package dev.hunterlab.core.display

import dev.hunterlab.core.nav.NavigationSnapshot
import dev.hunterlab.core.nav.NavigationStatus
import dev.hunterlab.core.radar.RadarPhase
import dev.hunterlab.protocol.BottomLine
import dev.hunterlab.protocol.ClockFormat
import dev.hunterlab.protocol.Confidence
import dev.hunterlab.protocol.DayPeriod
import dev.hunterlab.protocol.DisplayIntensity
import dev.hunterlab.protocol.Maneuver
import dev.hunterlab.protocol.ManeuverMapper
import dev.hunterlab.protocol.NavigationFrame
import dev.hunterlab.protocol.TripperIcons
import dev.hunterlab.protocol.TripperPacket
import dev.hunterlab.protocol.TripperScreen
import java.time.LocalTime

/** Resultado da arbitragem: a tela tipada, o pacote em memória e a prévia — sempre coerentes. */
data class ComposedFrame(
    val screen: TripperScreen,
    val packet: TripperPacket?,
    val preview: TripperPreview,
    val period: DayPeriod,
    val radarOnTripper: Boolean,
    val confidence: Confidence,
    val notes: List<String>,
)

/**
 * Árbitro de tela. Prioridades (da maior para a menor):
 *
 * 1. **Recalculando** — tela `0x1C`; radar suprimido.
 * 2. **Rota iniciada** — tela `0x45`; radar suprimido.
 * 3. **Chegada** — destino a 0 m; radar suprimido.
 * 4. **Navegando** — tela de navegação; o radar em alerta pode **apenas** ocupar a seta pequena `[7]`
 *    e o rodapé `[11-13]` (compatível) ou `[8-9]` (experimental). Ícone `[2]`, distância `[3-5]`
 *    e noite `[6]` nunca são tocados.
 * 5. **Sem rota** — relógio nativo do Pod (nenhum pacote de tela); radar só no app.
 */
object ScreenComposer {

    fun compose(
        nav: NavigationSnapshot,
        radar: RadarPhase,
        settings: DisplaySettings,
        now: LocalTime,
    ): ComposedFrame {
        val period = settings.dayNightMode.resolve(now, settings.schedule)
        val notes = mutableListOf<String>()
        var confidence = Confidence.CONFIRMED
        var radarShown = false
        val alert = radar as? RadarPhase.Alert
        val radarSuppressedNote = "Alerta de radar ativo, mas suprimido no Tripper: ${nav.status.label.lowercase()} tem prioridade."

        val screen: TripperScreen = when (nav.status) {
            NavigationStatus.IDLE -> {
                notes += "Sem rota: o Pod mostraria o relógio nativo (nenhum pacote de tela é gerado)."
                if (alert != null) notes += "Radar em alerta exibido só no app: sem rota não há tela documentada para sobrepor."
                TripperScreen.NativeClock
            }
            NavigationStatus.RECALCULATING -> {
                if (alert != null) notes += radarSuppressedNote
                TripperScreen.Recalculating(period)
            }
            NavigationStatus.ROUTE_STARTED -> {
                confidence = Confidence.LIKELY
                notes += "Tela \"rota iniciada\" (0x45): layout travado por regressão, ainda sem captura real publicada."
                if (alert != null) notes += radarSuppressedNote
                TripperScreen.RouteStarted(period)
            }
            NavigationStatus.ARRIVED -> {
                if (alert != null) notes += radarSuppressedNote
                TripperScreen.Navigation(
                    NavigationFrame(
                        icon = TripperIcons.DESTINATION,
                        distanceMeters = 0,
                        byte6 = DisplayIntensity.byte6(0, period, settings.intensityByDistance),
                    ),
                )
            }
            NavigationStatus.NAVIGATING -> {
                val maneuver = nav.maneuver ?: Maneuver.Straight
                val current = ManeuverMapper.map(maneuver, settings.trafficSide)
                confidence = confidence.weakest(current.confidence)
                val nextIcon = nav.nextManeuver?.let { next ->
                    val m = ManeuverMapper.map(next, settings.trafficSide)
                    confidence = confidence.weakest(m.confidence)
                    m.small
                } ?: TripperIcons.NONE
                val distance = nav.distanceToManeuverM ?: 0
                if (nav.distanceToManeuverM == null) notes += "Distância até a manobra desconhecida: enviada como 0 m."
                if (settings.intensityByDistance) {
                    confidence = confidence.weakest(Confidence.LIKELY)
                    notes += "Intensidade por distância no byte [6]: copiada de outro app; efeito visual não documentado."
                }
                var frame = NavigationFrame(
                    icon = current.large,
                    distanceMeters = distance,
                    byte6 = DisplayIntensity.byte6(distance, period, settings.intensityByDistance),
                    nextIcon = nextIcon,
                    bottomLine = bottomLine(nav, settings.bottomLineMode, now),
                )
                if (alert != null) {
                    if (settings.radarOnTripper) {
                        frame = frame.withRadar(alert.distanceM, settings.radarMode)
                        radarShown = true
                        confidence = confidence.weakest(settings.radarMode.confidence)
                        notes += "Radar no Tripper (modo ${settings.radarMode.label.lowercase()}): seta pequena → 0x3C; " +
                            "ícone, distância e período da manobra preservados."
                    } else {
                        notes += "Radar em alerta, mas a exibição no Tripper está desligada nas configurações."
                    }
                }
                TripperScreen.Navigation(frame)
            }
        }

        return ComposedFrame(
            screen = screen,
            packet = screen.toPacket(),
            preview = PreviewMapper.from(screen, period),
            period = period,
            radarOnTripper = radarShown,
            confidence = confidence,
            notes = notes,
        )
    }

    fun bottomLine(nav: NavigationSnapshot, mode: BottomLineMode, now: LocalTime): BottomLine = when (mode) {
        BottomLineMode.TOTAL_DISTANCE -> nav.remainingDistanceM?.let { BottomLine.TotalDistance(it) }
        BottomLineMode.TIME_REMAINING -> nav.remainingTimeMin?.let { BottomLine.TimeRemaining(it) }
        BottomLineMode.ARRIVAL_24H -> nav.remainingTimeMin?.let { BottomLine.arrivalAfter(now, it, ClockFormat.H24) }
        BottomLineMode.ARRIVAL_12H -> nav.remainingTimeMin?.let { BottomLine.arrivalAfter(now, it, ClockFormat.H12) }
    } ?: BottomLine.None
}
