package dev.hunterlab.core.display

import dev.hunterlab.protocol.BottomLine
import dev.hunterlab.protocol.ClockFormat
import dev.hunterlab.protocol.CompassDirection
import dev.hunterlab.protocol.DayPeriod
import dev.hunterlab.protocol.DisplayIntensity
import dev.hunterlab.protocol.DistanceEncoding
import dev.hunterlab.protocol.TripperIcon
import dev.hunterlab.protocol.TripperIcons
import dev.hunterlab.protocol.TripperScreen

enum class PreviewKind { NATIVE_CLOCK, NAVIGATION, COMPASS, ROUTE_STARTED, RECALCULATING }

/**
 * O que a prévia circular do app deve desenhar. Derivado da [TripperScreen] tipada — a mesma que
 * gera o pacote — para que prévia e bytes nunca divirjam. **Não** reproduz os pixels do display real.
 */
data class TripperPreview(
    val kind: PreviewKind,
    val night: Boolean,
    val largeIcon: TripperIcon? = null,
    val distanceText: String? = null,
    val smallIcon: TripperIcon? = null,
    /** Distância nos bytes [8-9] (radar experimental). */
    val auxDistanceText: String? = null,
    val bottomText: String? = null,
    val bottomIsRadar: Boolean = false,
    val radarActive: Boolean = false,
    val compass: CompassDirection? = null,
    val callOverlay: Boolean = false,
)

object PreviewMapper {
    fun from(screen: TripperScreen, periodWhenNoPacket: DayPeriod = DayPeriod.DAY): TripperPreview = when (screen) {
        TripperScreen.NativeClock -> TripperPreview(PreviewKind.NATIVE_CLOCK, night = periodWhenNoPacket == DayPeriod.NIGHT)
        is TripperScreen.Compass -> TripperPreview(
            kind = PreviewKind.COMPASS,
            night = screen.period == DayPeriod.NIGHT,
            compass = screen.direction,
        )
        is TripperScreen.RouteStarted -> TripperPreview(
            kind = PreviewKind.ROUTE_STARTED,
            night = screen.period == DayPeriod.NIGHT,
            largeIcon = TripperIcons.describe(TripperIcons.ROUTE_STARTED),
        )
        is TripperScreen.Recalculating -> TripperPreview(
            kind = PreviewKind.RECALCULATING,
            night = screen.period == DayPeriod.NIGHT,
            largeIcon = TripperIcons.describe(TripperIcons.RECALCULATING),
        )
        is TripperScreen.Navigation -> {
            val f = screen.frame
            val radar = f.nextIcon == TripperIcons.RADAR_SMALL &&
                (f.bottomLine is BottomLine.RadarDistance || f.auxDistanceMeters != null)
            TripperPreview(
                kind = PreviewKind.NAVIGATION,
                night = DisplayIntensity.isNight(f.byte6),
                largeIcon = TripperIcons.describe(f.icon),
                distanceText = f.distance.format(),
                smallIcon = if (f.nextIcon == TripperIcons.NONE) null else TripperIcons.describe(f.nextIcon),
                auxDistanceText = f.auxDistanceMeters?.let { DistanceEncoding.encode(it).format() },
                bottomText = formatBottomLine(f.bottomLine),
                bottomIsRadar = f.bottomLine is BottomLine.RadarDistance,
                radarActive = radar,
            )
        }
    }

    fun formatBottomLine(line: BottomLine): String? = when (line) {
        BottomLine.None -> null
        is BottomLine.TotalDistance -> line.encoded.format()
        is BottomLine.RadarDistance -> line.encoded.format()
        is BottomLine.TimeRemaining -> "${line.hours}h${line.remainderMinutes.toString().padStart(2, '0')}"
        is BottomLine.ArrivalTime -> when (line.format) {
            ClockFormat.H24 -> "${line.hour.toString().padStart(2, '0')}:${line.minute.toString().padStart(2, '0')}"
            ClockFormat.H12 -> {
                val h = (line.hour % 12).let { if (it == 0) 12 else it }
                "$h:${line.minute.toString().padStart(2, '0')} ${if (line.isPm) "PM" else "AM"}"
            }
        }
    }
}
