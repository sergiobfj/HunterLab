package dev.hunterlab.app.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.util.forEach
import dev.hunterlab.core.ble.BleErrorTranslator
import dev.hunterlab.core.ble.ReadinessIssue
import dev.hunterlab.core.ble.ScanThrottle
import dev.hunterlab.core.ble.ScannedDevice
import dev.hunterlab.core.ble.UserMessage
import dev.hunterlab.protocol.Hex
import dev.hunterlab.protocol.TripperTransport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ScanState(
    val scanning: Boolean = false,
    val startedAtMs: Long? = null,
    val durationMs: Long = 0,
    val devices: Map<String, ScannedDevice> = emptyMap(),
    val error: UserMessage? = null,
)

/**
 * Busca BLE sem filtro (para enxergar também um Tripper com nome diferente do documentado).
 * Dura por padrão a janela de anúncio do Pod (~30 s). Resultados são agregados a cada 400 ms
 * para não recompor a UI a cada pacote de anúncio.
 */
class BleScanner(private val context: Context) {
    private val _state = MutableStateFlow(ScanState())
    val state: StateFlow<ScanState> = _state.asStateFlow()

    private val main = Handler(Looper.getMainLooper())
    private val throttle = ScanThrottle({ SystemClock.elapsedRealtime() })
    private val pending = LinkedHashMap<String, ScannedDevice>()
    private var flushScheduled = false

    private val timeout = Runnable { stop() }
    private val flush = Runnable {
        flushScheduled = false
        if (pending.isEmpty()) return@Runnable
        val batch = pending.toMap()
        pending.clear()
        _state.update { s ->
            val merged = s.devices.toMutableMap()
            batch.forEach { (addr, dev) -> merged[addr] = merge(merged[addr], dev) }
            s.copy(devices = merged)
        }
    }

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) = onResult(result)
        override fun onBatchScanResults(results: MutableList<ScanResult>) = results.forEach(::onResult)
        override fun onScanFailed(errorCode: Int) {
            main.removeCallbacks(timeout)
            _state.update { it.copy(scanning = false, error = BleErrorTranslator.scanFailed(errorCode)) }
        }
    }

    /** Devolve uma mensagem se não foi possível iniciar. */
    @SuppressLint("MissingPermission") // verificada explicitamente abaixo
    fun start(durationMs: Long = TripperTransport.ADVERTISING_WINDOW_MS): UserMessage? {
        if (_state.value.scanning) return null
        if (!BlePermissions.hasScan(context)) return BleErrorTranslator.readiness(ReadinessIssue.PERMISSIONS_MISSING)
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            ?: return BleErrorTranslator.readiness(ReadinessIssue.NO_BLUETOOTH)
        if (!adapter.isEnabled) return BleErrorTranslator.readiness(ReadinessIssue.BLUETOOTH_OFF)
        val scanner = adapter.bluetoothLeScanner ?: return BleErrorTranslator.readiness(ReadinessIssue.BLUETOOTH_OFF)
        throttle.tryAcquire()?.let { wait ->
            return BleErrorTranslator.scanThrottled(wait).also { m -> _state.update { it.copy(error = m) } }
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .build()
        try {
            scanner.startScan(null, settings, callback)
        } catch (e: SecurityException) {
            return BleErrorTranslator.permissionRevoked().also { m -> _state.update { it.copy(error = m) } }
        }
        _state.update { it.copy(scanning = true, startedAtMs = SystemClock.elapsedRealtime(), durationMs = durationMs, error = null) }
        main.postDelayed(timeout, durationMs)
        return null
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        main.removeCallbacks(timeout)
        if (!_state.value.scanning) return
        try {
            if (BlePermissions.hasScan(context)) {
                context.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner?.stopScan(callback)
            }
        } catch (_: SecurityException) {
            // Permissão revogada durante a busca: o sistema já interrompeu.
        } catch (_: IllegalStateException) {
            // Bluetooth desligado durante a busca.
        }
        flush.run()
        _state.update { it.copy(scanning = false) }
    }

    fun clear() {
        pending.clear()
        _state.update { it.copy(devices = emptyMap(), error = null) }
    }

    private fun onResult(r: ScanResult) {
        val record = r.scanRecord
        val manufacturer = buildMap {
            record?.manufacturerSpecificData?.forEach { companyId, bytes -> put(companyId, Hex.encode(bytes)) }
        }
        val device = ScannedDevice(
            address = r.device.address,
            // Nome do registro de anúncio: não exige BLUETOOTH_CONNECT (ao contrário de device.name).
            name = record?.deviceName,
            rssi = r.rssi,
            serviceUuids = record?.serviceUuids?.map { it.uuid }.orEmpty(),
            manufacturerData = manufacturer,
            connectable = r.isConnectable,
            txPower = r.txPower.takeIf { it != ScanResult.TX_POWER_NOT_PRESENT },
            lastSeenMs = System.currentTimeMillis(),
            addressType = if (Build.VERSION.SDK_INT >= 35) r.device.addressType else null,
        )
        pending[device.address] = merge(pending[device.address], device)
        if (!flushScheduled) {
            flushScheduled = true
            main.postDelayed(flush, 400)
        }
    }

    /** Anúncio e resposta de scan chegam separados: preserva o que já se sabia. */
    private fun merge(old: ScannedDevice?, new: ScannedDevice): ScannedDevice = if (old == null) new else new.copy(
        name = new.name ?: old.name,
        serviceUuids = (old.serviceUuids + new.serviceUuids).distinct(),
        manufacturerData = old.manufacturerData + new.manufacturerData,
        txPower = new.txPower ?: old.txPower,
        addressType = new.addressType ?: old.addressType,
    )
}
