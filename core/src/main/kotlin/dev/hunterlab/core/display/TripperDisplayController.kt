package dev.hunterlab.core.display

import dev.hunterlab.core.link.ScreenStreamPort
import dev.hunterlab.core.link.StreamAvailability
import dev.hunterlab.core.nav.NavigationSnapshot
import dev.hunterlab.core.nav.NavigationStatus
import dev.hunterlab.core.radar.RadarPhase
import dev.hunterlab.protocol.TripperScreen
import java.time.LocalTime

enum class TripperDisplayStatus(val label: String) {
    NOT_CONNECTED("Tripper não conectado — a navegação continua no celular"),
    READY("Tripper autenticado, aguardando a rota"),
    SHOWING("Navegação no Tripper"),
    ENDED("Navegação encerrada no Tripper; o relógio volta quando ele fechar o link"),
}

data class DisplayUpdate(
    val status: TripperDisplayStatus,
    /** Tela composta (prévia no app), mesmo sem Tripper conectado. */
    val composed: ComposedFrame,
)

/**
 * Liga navegação + radar ao Tripper. Puro e testável: decide quando iniciar, atualizar e encerrar a transmissão.
 *
 * - Compõe a tela com o [ScreenComposer] (prioridade: manobra > recalculando > radar na seta pequena/rodapé).
 * - Só transmite telas de navegação/recalculando; "sem rota" nunca inicia transmissão.
 * - Na chegada mostra o destino por [arrivalHoldMs] e encerra: sem novas telas o Tripper fecha o link e volta ao relógio
 *   (observado na Hunter após o teste de bússola, status 19).
 * - Sem Tripper autenticado, apenas devolve a prévia: a navegação no celular segue normalmente.
 */
class TripperDisplayController(
    private val port: ScreenStreamPort,
    private val arrivalHoldMs: Long = 15_000,
) {
    private var arrivedAtMs: Long? = null
    private var ended = false

    /** Nova navegação (novo destino): esquece a chegada anterior. */
    fun reset() {
        arrivedAtMs = null
        ended = false
    }

    fun update(
        nav: NavigationSnapshot,
        radar: RadarPhase,
        settings: DisplaySettings,
        now: LocalTime,
        nowMs: Long,
    ): DisplayUpdate {
        val composed = ScreenComposer.compose(nav, radar, settings, now)
        val screen = composed.screen
        // "Encerrado" (chegada ou pedido do usuário) só é desfeito por reset(), numa nova navegação.
        if (nav.status == NavigationStatus.ARRIVED) {
            if (arrivedAtMs == null) arrivedAtMs = nowMs
        } else if (nav.status != NavigationStatus.IDLE) {
            arrivedAtMs = null
        }
        val arrivalOver = arrivedAtMs?.let { nowMs - it >= arrivalHoldMs } == true
        val transmittable = screen is TripperScreen.Navigation || screen is TripperScreen.Recalculating

        val status = when (port.streamAvailability) {
            StreamAvailability.UNAVAILABLE -> if (ended) TripperDisplayStatus.ENDED else TripperDisplayStatus.NOT_CONNECTED
            StreamAvailability.READY -> when {
                ended || arrivalOver -> TripperDisplayStatus.ENDED.also { ended = true }
                transmittable -> if (port.startScreenStream(screen) is dev.hunterlab.core.link.CommandResult.Accepted) {
                    TripperDisplayStatus.SHOWING
                } else {
                    TripperDisplayStatus.READY
                }
                else -> TripperDisplayStatus.READY
            }
            StreamAvailability.STREAMING -> when {
                ended || arrivalOver -> {
                    port.stopScreenStream("chegada ao destino")
                    ended = true
                    TripperDisplayStatus.ENDED
                }
                !transmittable -> {
                    port.stopScreenStream("navegação sem rota")
                    ended = true
                    TripperDisplayStatus.ENDED
                }
                else -> {
                    port.updateScreen(screen)
                    TripperDisplayStatus.SHOWING
                }
            }
        }
        return DisplayUpdate(status, composed)
    }

    /** Encerramento pedido pelo usuário: para de transmitir (o Tripper fecha o link sozinho). */
    fun stop(reason: String = "navegação encerrada") {
        if (port.streamAvailability == StreamAvailability.STREAMING) port.stopScreenStream(reason)
        ended = true
    }
}
