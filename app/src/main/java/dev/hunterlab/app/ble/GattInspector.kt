package dev.hunterlab.app.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import dev.hunterlab.core.ble.BleErrorTranslator
import dev.hunterlab.core.ble.ConnectionPhase
import dev.hunterlab.core.ble.GattReportFormatter
import dev.hunterlab.core.ble.KnownUuids
import dev.hunterlab.core.ble.ReadinessIssue
import dev.hunterlab.core.ble.ScannedDevice
import dev.hunterlab.core.ble.Severity
import dev.hunterlab.core.ble.UserMessage
import dev.hunterlab.core.ble.UuidCategory
import dev.hunterlab.protocol.Hex
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID

data class ReadValue(val hex: String?, val text: String?, val error: UserMessage?)

data class InspectorState(
    val phase: ConnectionPhase = ConnectionPhase.IDLE,
    val address: String? = null,
    val name: String? = null,
    val snapshot: dev.hunterlab.core.ble.GattSnapshot? = null,
    val message: UserMessage? = null,
    val reads: Map<String, ReadValue> = emptyMap(),
    val pendingRead: String? = null,
    val log: List<String> = emptyList(),
) {
    val connected: Boolean
        get() = phase == ConnectionPhase.CONNECTED || phase == ConnectionPhase.DISCOVERING || phase == ConnectionPhase.READY
}

/**
 * Conecta, descobre serviços, fotografa a tabela GATT e permite **leituras** opcionais.
 * Não há nenhuma operação de escrita, notificação, pareamento nem troca de MTU nesta classe.
 */
class GattInspector(
    private val context: Context,
    private val devicesScanActive: () -> Boolean = { false },
) {
    private val _state = MutableStateFlow(InspectorState())
    val state: StateFlow<InspectorState> = _state.asStateFlow()

    private val main = Handler(Looper.getMainLooper())
    private var gatt: BluetoothGatt? = null

    /** Último encerramento de conexão viva iniciado pelo celular (só para diagnóstico; elapsedRealtime). */
    var lastAppInitiatedDisconnectAt: Long? = null
        private set
    private var disconnectRequestedByUser = false

    private val connectTimeout = Runnable {
        if (_state.value.phase == ConnectionPhase.CONNECTING) {
            closeGatt()
            _state.update { it.copy(phase = ConnectionPhase.FAILED, message = BleErrorTranslator.connectionTimeout(CONNECT_TIMEOUT_MS)) }
            log("Tempo esgotado sem conexão.")
        }
    }

    /** Se o dispositivo não encerrar o link sozinho (o Tripper encerra em ~5 s), o app libera. */
    private val autoRelease = Runnable {
        if (_state.value.connected) {
            log("Liberando a conexão após ${AUTO_RELEASE_MS / 1000} s de inspeção.")
            disconnect()
        }
    }

    private val forceClose = Runnable {
        if (gatt != null) {
            closeGatt()
            _state.update { it.copy(phase = ConnectionPhase.DISCONNECTED) }
        }
    }

    @SuppressLint("MissingPermission") // verificada explicitamente
    fun connect(device: ScannedDevice): UserMessage? {
        if (!BlePermissions.hasConnect(context)) return BleErrorTranslator.readiness(ReadinessIssue.PERMISSIONS_MISSING)
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            ?: return BleErrorTranslator.readiness(ReadinessIssue.NO_BLUETOOTH)
        if (!adapter.isEnabled) return BleErrorTranslator.readiness(ReadinessIssue.BLUETOOTH_OFF)
        closeGatt()
        val remote = try {
            adapter.getRemoteDevice(device.address)
        } catch (e: IllegalArgumentException) {
            return UserMessage("Endereço inválido", device.address)
        }
        disconnectRequestedByUser = false
        _state.value = InspectorState(phase = ConnectionPhase.CONNECTING, address = device.address, name = device.name)
        log("Conectando a ${device.address} (TRANSPORT_LE, autoConnect=false)…")
        // Diagnóstico (somente registro; mesmo conjunto de medidas do Laboratório BLE)
        log(
            "diag: último anúncio ${BleDiagnostics.ageText(device.lastSeenMs)} · tipo no anúncio: " +
                "${BleDiagnostics.scanAddressType(device.addressType)} · objeto getRemoteDevice: tipo ${BleDiagnostics.addressType(remote)}",
        )
        BleDiagnostics.environment(context, device.address, devicesScanActive()).forEach { log("diag: $it") }
        val calledAt = SystemClock.elapsedRealtime()
        connectCalledAt = calledAt
        val g = try {
            // A sobrecarga com BluetoothGattConnectionSettings só existe a partir da API 37 (minSdk é 31).
            // Esta forma clássica não negocia MTU automaticamente — coerente com o "somente leitura".
            @Suppress("DEPRECATION")
            remote.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        } catch (e: SecurityException) {
            return fail(BleErrorTranslator.permissionRevoked())
        }
        log("diag: connectGatt retornou ${if (g == null) "nulo" else "objeto"} em ${SystemClock.elapsedRealtime() - calledAt} ms.")
        if (g == null) return fail(UserMessage("Falha ao conectar", "O Android não abriu a conexão GATT."))
        gatt = g
        main.postDelayed(connectTimeout, CONNECT_TIMEOUT_MS)
        return null
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        val g = gatt ?: return
        disconnectRequestedByUser = true
        if (_state.value.connected) lastAppInitiatedDisconnectAt = SystemClock.elapsedRealtime()
        main.removeCallbacks(autoRelease)
        _state.update { it.copy(phase = ConnectionPhase.DISCONNECTING) }
        log("Desconexão solicitada pelo app.")
        try {
            g.disconnect()
        } catch (_: SecurityException) {
            closeGatt()
        }
        main.postDelayed(forceClose, 2_000)
    }

    /** Volta ao estado inicial, descartando a inspeção guardada. */
    fun reset() {
        if (_state.value.connected) lastAppInitiatedDisconnectAt = SystemClock.elapsedRealtime()
        closeGatt()
        _state.value = InspectorState()
    }

    /** Leitura opcional de uma característica com propriedade READ. Bloqueada em serviços sensíveis. */
    @SuppressLint("MissingPermission")
    fun read(serviceUuid: UUID, characteristicUuid: UUID): UserMessage? {
        val g = gatt
        val s = _state.value
        if (g == null || !s.connected) return UserMessage("Sem conexão", "Conecte novamente para ler.", severity = Severity.WARNING)
        if (s.pendingRead != null) return UserMessage("Aguarde", "Uma leitura já está em andamento.", severity = Severity.INFO)
        if (KnownUuids.lookup(serviceUuid)?.category == UuidCategory.SENSITIVE) {
            return UserMessage("Bloqueado", "Serviço sensível (provável atualização de firmware): o app não interage com ele.")
        }
        val ch = g.getService(serviceUuid)?.getCharacteristic(characteristicUuid)
            ?: return UserMessage("Característica não encontrada", characteristicUuid.toString())
        if (ch.properties and BluetoothGattCharacteristic.PROPERTY_READ == 0) {
            return UserMessage("Sem permissão de leitura", "A característica não anuncia READ.", severity = Severity.WARNING)
        }
        val key = "$serviceUuid/$characteristicUuid"
        val started = try {
            g.readCharacteristic(ch)
        } catch (_: SecurityException) {
            false
        }
        if (!started) return UserMessage("Leitura não iniciada", "O Android recusou a leitura agora. Tente de novo.", severity = Severity.WARNING)
        _state.update { it.copy(pendingRead = key) }
        log("Lendo ${KnownUuids.display(characteristicUuid)}…")
        return null
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            main.post { onConnectionState(g, status, newState) }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            main.post { onDiscovered(g, status) }
        }

        // API 33+
        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            val service = c.service.uuid
            main.post { onRead(service, c.uuid, value, status) }
        }

        // API 31–32
        @Deprecated("Substituído no Android 13")
        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
            val value = c.value ?: ByteArray(0)
            val service = c.service.uuid
            main.post { onRead(service, c.uuid, value, status) }
        }
    }

    @SuppressLint("MissingPermission")
    private fun onConnectionState(g: BluetoothGatt, status: Int, newState: Int) {
        if (g != gatt) {
            g.close() // callback de uma conexão antiga
            return
        }
        val since = connectCalledAt?.let { " após ${SystemClock.elapsedRealtime() - it} ms do connectGatt" } ?: ""
        log("diag: onConnectionStateChange status=$status newState=$newState$since.")
        when (newState) {
            BluetoothProfile.STATE_CONNECTED -> {
                main.removeCallbacks(connectTimeout)
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    closeGatt()
                    fail(BleErrorTranslator.connectionClosed(status, false))
                    return
                }
                connectedAt = SystemClock.elapsedRealtime()
                log("Conectado. Descobrindo serviços…")
                _state.update { it.copy(phase = ConnectionPhase.DISCOVERING, message = null) }
                main.postDelayed({
                    val ok = try {
                        gatt?.discoverServices() == true
                    } catch (_: SecurityException) {
                        false
                    }
                    if (!ok && _state.value.phase == ConnectionPhase.DISCOVERING) {
                        fail(UserMessage("Descoberta não iniciada", "O Android recusou discoverServices()."))
                        releaseLink()
                    }
                }, 150)
            }
            BluetoothProfile.STATE_DISCONNECTED -> {
                main.removeCallbacks(connectTimeout)
                main.removeCallbacks(autoRelease)
                main.removeCallbacks(forceClose)
                val after = connectedAt?.let { " após ${(SystemClock.elapsedRealtime() - it) / 100 / 10.0} s" } ?: ""
                log("Desconectado (status $status)$after.")
                closeGatt()
                if (_state.value.phase == ConnectionPhase.FAILED) return // preserva a mensagem da falha original
                val msg = BleErrorTranslator.connectionClosed(status, disconnectRequestedByUser)
                _state.update {
                    it.copy(
                        phase = if (msg.expected || it.snapshot != null) ConnectionPhase.DISCONNECTED else ConnectionPhase.FAILED,
                        message = msg,
                        pendingRead = null,
                    )
                }
            }
        }
    }

    private var connectedAt: Long? = null
    private var connectCalledAt: Long? = null

    private fun onDiscovered(g: BluetoothGatt, status: Int) {
        if (g != gatt) return
        if (status != BluetoothGatt.GATT_SUCCESS) {
            fail(BleErrorTranslator.discoveryFailed(status))
            releaseLink()
            return
        }
        val snapshot = GattSnapshots.from(g, _state.value.address ?: g.device.address, _state.value.name)
        val services = snapshot.services
        log("${services.size} serviço(s), ${services.sumOf { it.characteristics.size }} característica(s). Inspeção guardada.")
        _state.update { it.copy(phase = ConnectionPhase.READY, snapshot = snapshot) }
        main.postDelayed(autoRelease, AUTO_RELEASE_MS)
    }

    private fun onRead(service: UUID, characteristic: UUID, value: ByteArray, status: Int) {
        val key = "$service/$characteristic"
        val result = if (status == BluetoothGatt.GATT_SUCCESS) {
            val printable = value.isNotEmpty() && value.all { it in 0x20..0x7E }
            ReadValue(Hex.encode(value).ifEmpty { "(vazio)" }, if (printable) String(value, Charsets.US_ASCII) else null, null)
        } else {
            ReadValue(null, null, BleErrorTranslator.readFailed(status))
        }
        log("Leitura ${KnownUuids.display(characteristic)}: ${result.text ?: result.hex ?: "status $status"}")
        _state.update { it.copy(reads = it.reads + (key to result), pendingRead = null) }
    }

    /** Encerra o link após uma falha, sem trocar o estado/mensagem já registrados. */
    @SuppressLint("MissingPermission")
    private fun releaseLink() {
        val g = gatt ?: return
        try {
            g.disconnect()
        } catch (_: SecurityException) {
            closeGatt()
            return
        }
        main.postDelayed({ if (gatt === g) closeGatt() }, 2_000)
    }

    private fun fail(message: UserMessage): UserMessage {
        _state.update { it.copy(phase = ConnectionPhase.FAILED, message = message) }
        log("Falha: ${message.title}")
        return message
    }

    @SuppressLint("MissingPermission")
    private fun closeGatt() {
        main.removeCallbacks(connectTimeout)
        main.removeCallbacks(autoRelease)
        main.removeCallbacks(forceClose)
        try {
            gatt?.close()
        } catch (_: SecurityException) {
        }
        gatt = null
        connectedAt = null
    }

    private fun log(line: String) {
        val t = LocalTime.now().format(TIME)
        _state.update { it.copy(log = (it.log + "$t  $line").takeLast(60)) }
    }

    fun report(device: ScannedDevice?, appVersion: String): String? {
        val s = _state.value
        val snap = s.snapshot ?: return null
        val reads = s.reads.mapNotNull { (k, v) -> (v.text?.let { "\"$it\" (${v.hex})" } ?: v.hex)?.let { k to it } }.toMap()
        return GattReportFormatter.format(snap, device, reads, appVersion, java.time.LocalDateTime.now().format(STAMP))
    }

    companion object {
        const val CONNECT_TIMEOUT_MS = 15_000L
        const val AUTO_RELEASE_MS = 20_000L
        private val TIME = DateTimeFormatter.ofPattern("HH:mm:ss")
        private val STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}
