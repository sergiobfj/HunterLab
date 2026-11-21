package dev.hunterlab.protocol

/**
 * Direção da tela de bússola (byte `[14]`). A tabela não é linear — valores capturados do Pod.
 * Rótulos em pt-BR (L = leste, O = oeste).
 */
public enum class CompassDirection(public val code: Int, public val label: String, public val degrees: Int) {
    N(0x10, "N", 0),
    NE(0x50, "NE", 45),
    E(0x20, "L", 90),
    SE(0x70, "SE", 135),
    S(0x40, "S", 180),
    SW(0x80, "SO", 225),
    W(0x30, "O", 270),
    NW(0x60, "NO", 315);

    public companion object {
        /** Setores de 45° com deslocamento de 22,5° (0° = norte). Aceita qualquer ângulo real. */
        public fun fromBearing(bearing: Double): CompassDirection {
            require(bearing.isFinite()) { "Rumo inválido: $bearing" }
            val normalized = ((bearing % 360.0) + 360.0) % 360.0
            val sector = (((normalized + 22.5) % 360.0) / 45.0).toInt().coerceIn(0, 7)
            return entries.first { it.degrees == sector * 45 }
        }

        public fun fromCode(code: Int): CompassDirection? = entries.firstOrNull { it.code == code }
    }
}
