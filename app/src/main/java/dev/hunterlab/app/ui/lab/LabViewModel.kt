package dev.hunterlab.app.ui.lab

import androidx.lifecycle.ViewModel
import dev.hunterlab.app.BuildConfigInfo
import dev.hunterlab.app.ble.BleController
import dev.hunterlab.core.ble.ScannedDevice
import dev.hunterlab.core.link.AfterTestObservation
import dev.hunterlab.core.link.CommandResult
import dev.hunterlab.core.link.CompassObservation
import dev.hunterlab.core.link.ConnectOptions
import dev.hunterlab.core.link.ServerMode
import dev.hunterlab.core.link.LinkSnapshot
import dev.hunterlab.core.link.LinkTiming
import dev.hunterlab.core.link.TransmissionAck
import dev.hunterlab.protocol.CompassDirection
import dev.hunterlab.protocol.DayPeriod
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Fina camada entre a UI e o [dev.hunterlab.core.link.TripperLink]. Não guarda PIN: ele passa direto
 * para o link, que monta o pacote e descarta o texto.
 */
class LabViewModel(private val ble: BleController) : ViewModel() {
    val link: StateFlow<LinkSnapshot> = ble.lab.snapshot
    val timing: LinkTiming = ble.lab.timing

    private val _options = MutableStateFlow(ConnectOptions())

    /** Opções da próxima conexão. Padrão: anúncio recente obrigatório, servidor antes da conexão. */
    val options: StateFlow<ConnectOptions> = _options.asStateFlow()

    fun setRequireFreshAdvertisement(required: Boolean) {
        _options.value = _options.value.copy(requireFreshAdvertisement = required)
    }

    fun setServerMode(mode: ServerMode) {
        _options.value = _options.value.copy(serverMode = mode)
    }

    private val _feedback = MutableStateFlow<String?>(null)
    val feedback: StateFlow<String?> = _feedback.asStateFlow()

    private fun handle(result: CommandResult) {
        _feedback.value = (result as? CommandResult.Rejected)?.reason
    }

    fun dismissFeedback() {
        _feedback.value = null
    }

    fun authorize(ack: TransmissionAck) = handle(ble.lab.authorizeTransmission(ack))
    fun cancelPreAuthorization() = ble.lab.cancelPreAuthorization()

    fun connect(device: ScannedDevice) {
        ble.prepareLab()
        val age = (System.currentTimeMillis() - device.lastSeenMs).coerceAtLeast(0)
        handle(ble.lab.connect(device.address, device.name, _options.value, lastAdvertisementAgeMs = age))
    }

    fun startPairing() = handle(ble.lab.startPairing())
    fun resume() = handle(ble.lab.resume())
    fun submitPin(pin: String) = handle(ble.lab.submitPin(pin))
    fun confirmAuthenticated() = handle(ble.lab.confirmAuthenticated())
    fun reportPinRequired() = handle(ble.lab.reportPinRequired())
    fun syncClock() = handle(ble.lab.syncClock())
    fun startCompass(direction: CompassDirection, period: DayPeriod, durationMs: Long) =
        handle(ble.lab.startCompassTest(direction, period, durationMs))

    fun stopCompass() = handle(ble.lab.stopCompassTest())
    fun observe(o: CompassObservation) = handle(ble.lab.recordObservation(o))
    fun observe(o: AfterTestObservation) = handle(ble.lab.recordObservation(o))
    fun interrupt() = handle(ble.lab.interrupt())
    fun disconnectNow() = handle(ble.lab.disconnectNow())
    fun setInboundCapture(enabled: Boolean) = ble.lab.setInboundCapture(enabled)

    fun report(): String =
        "Aparelho: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} · Android ${android.os.Build.VERSION.RELEASE} " +
            "(API ${android.os.Build.VERSION.SDK_INT})\n" + ble.lab.report(BuildConfigInfo.VERSION, LocalDateTime.now().format(STAMP))

    private companion object {
        val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}
