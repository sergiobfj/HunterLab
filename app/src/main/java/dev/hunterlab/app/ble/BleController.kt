package dev.hunterlab.app.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.text.format.DateFormat
import androidx.core.content.ContextCompat
import dev.hunterlab.app.ble.lab.AndroidTripperLink
import dev.hunterlab.app.ble.lab.HandlerLinkScheduler
import dev.hunterlab.core.link.InMemoryPairingRegistry
import dev.hunterlab.core.link.LinkTiming
import dev.hunterlab.core.link.TripperLink
import dev.hunterlab.protocol.ClockFormat
import java.time.LocalTime
import dev.hunterlab.core.ble.ReadinessIssue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object BlePermissions {
    /** minSdk 31: só as permissões de "Dispositivos por perto". Nada de localização. */
    val required = arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)

    fun granted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun hasScan(context: Context) = granted(context, Manifest.permission.BLUETOOTH_SCAN)
    fun hasConnect(context: Context) = granted(context, Manifest.permission.BLUETOOTH_CONNECT)
    fun allGranted(context: Context) = required.all { granted(context, it) }
}

data class BleReadiness(
    val hasAdapter: Boolean,
    val hasBle: Boolean,
    val permissionsGranted: Boolean,
    val bluetoothOn: Boolean,
) {
    val issue: ReadinessIssue?
        get() = when {
            !hasAdapter -> ReadinessIssue.NO_BLUETOOTH
            !hasBle -> ReadinessIssue.NO_BLE
            !permissionsGranted -> ReadinessIssue.PERMISSIONS_MISSING
            !bluetoothOn -> ReadinessIssue.BLUETOOTH_OFF
            else -> null
        }
    val ready: Boolean get() = issue == null
}

/**
 * Ponto único de acesso ao Bluetooth, com escopo de aplicação.
 * Busca e inspeção são somente leitura. A única via de escrita é o [lab], que nasce bloqueado.
 */
class BleController(private val context: Context) {
    val scanner = BleScanner(context)
    val inspector = GattInspector(context, devicesScanActive = { scanner.state.value.scanning })

    /** Registro de autenticações confirmadas — só em memória (nunca guarda PIN). */
    val pairings = InMemoryPairingRegistry()

    private val labAdapter = AndroidTripperLink(
        context,
        devicesScanActive = { scanner.state.value.scanning },
        environmentNotes = { inspectorNotes() },
    )

    private fun inspectorNotes(): List<String> = listOfNotNull(
        inspector.lastAppInitiatedDisconnectAt?.let { at ->
            val ago = (android.os.SystemClock.elapsedRealtime() - at) / 1000
            "A inspeção da aba Dispositivos foi encerrada PELO CELULAR há $ago s. Na Meteor, isso faz o Tripper parar de " +
                "anunciar até ciclar a ignição — se a ignição não foi ciclada desde então, a conexão tende a falhar (147)."
        },
    )

    /** Laboratório BLE (0.2.0): transmissão real só com autorização explícita por sessão. */
    val lab = TripperLink(
        client = labAdapter,
        server = labAdapter,
        scheduler = HandlerLinkScheduler(),
        pairings = pairings,
        timing = LinkTiming(),
        timeOfDay = { LocalTime.now() },
        clockFormat = { if (DateFormat.is24HourFormat(context)) ClockFormat.H24 else ClockFormat.H12 },
    ).also { labAdapter.events = it }

    /** Abre o laboratório para um dispositivo: para a busca e libera a inspeção somente leitura. */
    fun prepareLab() {
        scanner.stop()
        inspector.reset()
    }

    private val _readiness = MutableStateFlow(compute())
    val readiness: StateFlow<BleReadiness> = _readiness.asStateFlow()

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                if (state != BluetoothAdapter.STATE_ON) {
                    scanner.stop()
                    lab.interrupt("Bluetooth do celular desligado")
                }
                refresh()
            }
        }
    }

    init {
        ContextCompat.registerReceiver(
            context, stateReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    fun refresh() {
        _readiness.value = compute()
    }

    private fun compute(): BleReadiness {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        return BleReadiness(
            hasAdapter = adapter != null,
            hasBle = context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE),
            permissionsGranted = BlePermissions.allGranted(context),
            bluetoothOn = adapter?.isEnabled == true,
        )
    }
}
