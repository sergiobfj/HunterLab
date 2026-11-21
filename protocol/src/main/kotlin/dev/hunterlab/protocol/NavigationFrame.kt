package dev.hunterlab.protocol

/**
 * Como um radar é sobreposto à tela de navegação. Em ambos, a seta pequena `[7]` vira `0x3C`.
 */
public enum class RadarDisplayMode(public val label: String, public val confidence: Confidence, public val detail: String) {
    COMPATIBLE(
        label = "Compatível",
        confidence = Confidence.CONFIRMED,
        detail = "Distância do radar no rodapé [11–13], substituindo o total da rota. Captura real na referência.",
    ),
    EXPERIMENTAL(
        label = "Experimental",
        confidence = Confidence.UNVERIFIED,
        detail = "Distância nos bytes [8–9] (uint16, metros). Hipótese: se o Pod ignorar, só o ícone aparece.",
    ),
}

/**
 * Conteúdo da tela de navegação (`CMD_NAVIGATE` = `10 11`, com `[10] = 0x41`).
 *
 * ```
 * [0-1] 10 11   [2] ícone grande   [3-4] distância BE   [5] unidade   [6] noite/intensidade
 * [7] seta pequena (FF = nenhuma)  [8-9] FF FF ou distância do radar (experimental)
 * [10] 41   [11-13] rodapé   [14-17] 00   [18-19] CRC
 * ```
 */
public data class NavigationFrame(
    val icon: Int,
    val distanceMeters: Int,
    val byte6: Int = 0,
    val nextIcon: Int = TripperIcons.NONE,
    /** Bytes `[8-9]`: nulo → `FF FF`. Só usado pelo radar experimental. */
    val auxDistanceMeters: Int? = null,
    val bottomLine: BottomLine = BottomLine.None,
) {
    init {
        require(icon in 0..0xFF) { "Ícone fora de 1 byte: $icon" }
        require(distanceMeters >= 0) { "Distância negativa: $distanceMeters m" }
        require(byte6 in 0..0xFF) { "Byte [6] fora de 1 byte: $byte6" }
        require(nextIcon in 0..0xFF) { "Seta pequena fora de 1 byte: $nextIcon" }
        require(auxDistanceMeters == null || auxDistanceMeters >= 0) { "Distância auxiliar negativa: $auxDistanceMeters" }
    }

    public val distance: EncodedDistance get() = DistanceEncoding.encode(distanceMeters)

    /** Sobrepõe um radar **sem tocar** em ícone `[2]`, distância `[3-5]` e noite `[6]`. */
    public fun withRadar(radarDistanceMeters: Int, mode: RadarDisplayMode): NavigationFrame {
        require(radarDistanceMeters >= 0) { "Distância do radar negativa: $radarDistanceMeters m" }
        return when (mode) {
            RadarDisplayMode.COMPATIBLE -> copy(
                nextIcon = TripperIcons.RADAR_SMALL,
                bottomLine = BottomLine.RadarDistance(radarDistanceMeters),
            )
            RadarDisplayMode.EXPERIMENTAL -> copy(
                nextIcon = TripperIcons.RADAR_SMALL,
                auxDistanceMeters = radarDistanceMeters,
            )
        }
    }

    public fun toPacket(): TripperPacket {
        val d = distance
        val aux = auxDistanceMeters?.let { minOf(it, MAX_AUX_DISTANCE) } ?: 0xFFFF
        val bottom = bottomLine.encode()
        val p = ByteArray(TripperPacket.PAYLOAD_SIZE)
        p[0] = 0x10
        p[1] = 0x11
        p[2] = icon.toByte()
        p[3] = d.high.toByte()
        p[4] = d.low.toByte()
        p[5] = d.unit.code.toByte()
        p[6] = byte6.toByte()
        p[7] = nextIcon.toByte()
        p[8] = (aux shr 8).toByte()
        p[9] = aux.toByte()
        p[10] = NAVIGATION_MARKER.toByte()
        p[11] = bottom[0].toByte()
        p[12] = bottom[1].toByte()
        p[13] = bottom[2].toByte()
        return TripperPacket.fromPayload(p)
    }

    public companion object {
        public const val NAVIGATION_MARKER: Int = 0x41

        /** `0xFFFF` é reservado para "sem dado". */
        public const val MAX_AUX_DISTANCE: Int = 0xFFFE
    }
}
