package dev.hunterlab.protocol

/** Unidade do byte `[5]` (e do terceiro byte do rodapé quando ele é uma distância). */
public enum class DistanceUnit(public val code: Int, public val label: String) {
    /** Valor em metros (0–999). */
    METERS(1, "m"),

    /** Valor em décimos de quilômetro ("km × 10"), usado acima de 999 m. */
    KILOMETER_TENTHS(2, "km");

    public companion object {
        public fun fromCode(code: Int): DistanceUnit? = entries.firstOrNull { it.code == code }
    }
}

/** Distância como o Tripper a recebe: 16 bits big-endian + unidade. */
public data class EncodedDistance(val value: Int, val unit: DistanceUnit) {
    init {
        require(value in 0..0xFFFF) { "Valor de distância fora de 16 bits: $value" }
    }

    val high: Int get() = (value shr 8) and 0xFF
    val low: Int get() = value and 0xFF

    /** Metros representados (com a perda de precisão da codificação). */
    val representedMeters: Int get() = if (unit == DistanceUnit.METERS) value else value * 100

    /** Texto em pt-BR: `"167 m"`, `"2,9 km"`. */
    public fun format(): String = when (unit) {
        DistanceUnit.METERS -> "$value m"
        DistanceUnit.KILOMETER_TENTHS -> "${value / 10},${value % 10} km"
    }
}

public object DistanceEncoding {
    /** Até este valor (inclusive) a distância vai em metros. */
    public const val MAX_METERS: Int = 999

    /**
     * Até 999 m: metros crus (unidade 1). Acima: `metros / 100` truncado (unidade 2),
     * então 2942 m vira 29 ("2,9 km"). Limitado a 0xFFFF.
     *
     * @throws IllegalArgumentException para distância negativa (dado ausente deve ser tratado por quem chama).
     */
    public fun encode(meters: Int): EncodedDistance {
        require(meters >= 0) { "Distância negativa: $meters m" }
        return if (meters <= MAX_METERS) {
            EncodedDistance(meters, DistanceUnit.METERS)
        } else {
            EncodedDistance(minOf(meters / 100, 0xFFFF), DistanceUnit.KILOMETER_TENTHS)
        }
    }

    public fun decode(high: Int, low: Int, unitCode: Int): EncodedDistance? {
        val unit = DistanceUnit.fromCode(unitCode) ?: return null
        return EncodedDistance(((high and 0xFF) shl 8) or (low and 0xFF), unit)
    }
}
