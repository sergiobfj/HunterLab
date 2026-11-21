package dev.hunterlab.core.display

import dev.hunterlab.protocol.DayPeriod
import dev.hunterlab.protocol.RadarDisplayMode
import dev.hunterlab.protocol.TrafficSide
import java.time.LocalTime

enum class DayNightMode(val label: String) {
    DAY("Dia"),
    NIGHT("Noite"),
    AUTO("Automático"),
}

/**
 * Horário noturno para o modo automático. Usa só o relógio do celular — sem GPS nem
 * cálculo de nascer/pôr do sol nesta versão.
 */
data class DayNightSchedule(
    val nightStarts: LocalTime = LocalTime.of(18, 0),
    val dayStarts: LocalTime = LocalTime.of(6, 0),
) {
    init {
        require(nightStarts != dayStarts) { "Início da noite e do dia não podem coincidir" }
    }

    fun periodAt(time: LocalTime): DayPeriod {
        val night = if (nightStarts > dayStarts) {
            time >= nightStarts || time < dayStarts // atravessa a meia-noite (caso comum)
        } else {
            time >= nightStarts && time < dayStarts
        }
        return if (night) DayPeriod.NIGHT else DayPeriod.DAY
    }
}

fun DayNightMode.resolve(now: LocalTime, schedule: DayNightSchedule): DayPeriod = when (this) {
    DayNightMode.DAY -> DayPeriod.DAY
    DayNightMode.NIGHT -> DayPeriod.NIGHT
    DayNightMode.AUTO -> schedule.periodAt(now)
}

enum class BottomLineMode(val label: String) {
    TOTAL_DISTANCE("Distância total"),
    TIME_REMAINING("Tempo restante"),
    ARRIVAL_24H("Chegada (24h)"),
    ARRIVAL_12H("Chegada (12h)"),
}

data class DisplaySettings(
    val dayNightMode: DayNightMode = DayNightMode.AUTO,
    val schedule: DayNightSchedule = DayNightSchedule(),
    val bottomLineMode: BottomLineMode = BottomLineMode.TOTAL_DISTANCE,
    /** Nibble alto do byte [6] por distância; efeito visual não documentado. */
    val intensityByDistance: Boolean = false,
    val radarOnTripper: Boolean = true,
    val radarMode: RadarDisplayMode = RadarDisplayMode.COMPATIBLE,
    val trafficSide: TrafficSide = TrafficSide.RIGHT_HAND,
)
