package dev.hunterlab.protocol

public enum class DayPeriod(public val label: String) {
    DAY("Diurno"),
    NIGHT("Noturno"),
}

/**
 * Byte `[6]` das telas `10 11`.
 *
 * - Bit 0: flag noturno (`01` noite / `00` dia) — **confirmado** (capturas reais à noite).
 * - Nibble alto: "intensidade" por distância até a manobra, copiada de outro app do Tripper.
 *   O efeito visual no Pod **não é documentado**.
 */
public object DisplayIntensity {
    public const val NIGHT_BIT: Int = 0x01

    /** Tabela: ≤10 m 0x50, ≤20 m 0x40, ≤45 m 0x30, ≤70 m 0x20, ≤95 m 0x10, acima 0x00. */
    public fun forDistance(meters: Int): Int = when {
        meters <= 10 -> 0x50
        meters <= 20 -> 0x40
        meters <= 45 -> 0x30
        meters <= 70 -> 0x20
        meters <= 95 -> 0x10
        else -> 0x00
    }

    public fun nightFlag(period: DayPeriod): Int = if (period == DayPeriod.NIGHT) NIGHT_BIT else 0

    /** Byte `[6]` completo. Sem intensidade, é só o flag noturno (aceito pelo Pod). */
    public fun byte6(distanceMeters: Int, period: DayPeriod, intensityByDistance: Boolean): Int {
        val base = if (intensityByDistance) forDistance(distanceMeters) else 0
        return base + nightFlag(period)
    }

    public fun isNight(byte6: Int): Boolean = byte6 and NIGHT_BIT != 0
}
