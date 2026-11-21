package dev.hunterlab.protocol

import java.time.LocalTime

public enum class ClockFormat { H24, H12 }

/**
 * Linha inferior da tela de navegação, bytes `[11..13]`.
 * `FF FF FF` significa "sem dado".
 */
public sealed interface BottomLine {
    /** Os três bytes `[11]`, `[12]`, `[13]`. */
    public fun encode(): List<Int>

    public data object None : BottomLine {
        override fun encode(): List<Int> = listOf(0xFF, 0xFF, 0xFF)
    }

    /** Distância total restante, mesma codificação da distância da manobra. */
    public data class TotalDistance(val meters: Int) : BottomLine {
        init {
            require(meters >= 0) { "Distância total negativa: $meters m" }
        }

        val encoded: EncodedDistance get() = DistanceEncoding.encode(meters)
        override fun encode(): List<Int> = encoded.let { listOf(it.high, it.low, it.unit.code) }
    }

    /** Tempo restante: `[horas, minutos, 0]`; horas limitadas a 23 (como na referência). */
    public data class TimeRemaining(val minutes: Int) : BottomLine {
        init {
            require(minutes >= 0) { "Tempo restante negativo: $minutes min" }
        }

        val hours: Int get() = minOf(minutes / 60, 23)
        val remainderMinutes: Int get() = minOf(minutes % 60, 59)
        override fun encode(): List<Int> = listOf(hours, remainderMinutes, 0)
    }

    /**
     * Horário de chegada.
     * - 24h: `[hora 0–23, minuto, 0]`.
     * - 12h: `[hora 0–11 | 0x40 (AM) ou | 0x80 (PM), minuto, 0]` — esquema **diferente** do SET_TIME.
     */
    public data class ArrivalTime(val hour: Int, val minute: Int, val format: ClockFormat) : BottomLine {
        init {
            require(hour in 0..23) { "Hora inválida: $hour" }
            require(minute in 0..59) { "Minuto inválido: $minute" }
        }

        val isPm: Boolean get() = hour >= 12

        override fun encode(): List<Int> = when (format) {
            ClockFormat.H24 -> listOf(hour, minute, 0)
            ClockFormat.H12 -> listOf((hour % 12) or (if (isPm) 0x80 else 0x40), minute, 0)
        }
    }

    /**
     * Distância até um radar no rodapé — modo de radar **compatível** (o que outros apps fazem).
     * Substitui o rodapé da rota.
     */
    public data class RadarDistance(val meters: Int) : BottomLine {
        init {
            require(meters >= 0) { "Distância do radar negativa: $meters m" }
        }

        val encoded: EncodedDistance get() = DistanceEncoding.encode(meters)
        override fun encode(): List<Int> = encoded.let { listOf(it.high, it.low, it.unit.code) }
    }

    public companion object {
        /** Horário de chegada = agora + ETA, com virada de meia-noite. */
        public fun arrivalAfter(now: LocalTime, etaMinutes: Int, format: ClockFormat): ArrivalTime {
            require(etaMinutes >= 0) { "ETA negativo: $etaMinutes min" }
            val t = now.plusMinutes(etaMinutes.toLong())
            return ArrivalTime(t.hour, t.minute, format)
        }
    }
}
