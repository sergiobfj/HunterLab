package dev.hunterlab.protocol

/** Interpretação de um pacote válido. Útil para inspeção, testes de ida-e-volta e logs futuros. */
public sealed interface DecodedPacket {
    public data object HandshakeShowPin : DecodedPacket
    public data object HandshakeResume : DecodedPacket
    public data object PingFirmware : DecodedPacket
    public data object CallIcon : DecodedPacket
    public data class Pin(val code: String) : DecodedPacket

    /** A hora crua não diz se o formato é 12h ou 24h; [rawHour] preserva o byte. */
    public data class SetTime(val rawHour: Int, val minute: Int) : DecodedPacket
    public data class Compass(val direction: CompassDirection?, val night: Boolean) : DecodedPacket
    public data class Navigation(
        val icon: Int,
        val distance: EncodedDistance?,
        val byte6: Int,
        val nextIcon: Int,
        /** Bytes `[8-9]`; nulo quando `FF FF`. */
        val auxDistanceMeters: Int?,
        /** `[11..13]` crus: o significado depende do modo de rodapé escolhido por quem enviou. */
        val bottomRaw: List<Int>,
    ) : DecodedPacket {
        val night: Boolean get() = DisplayIntensity.isNight(byte6)
    }

    /** Tela `10 11` sem o marcador `[10]=0x41` (ex.: recalculando `0x1C`). */
    public data class Screen(val icon: Int, val night: Boolean) : DecodedPacket
    public data class Unknown(val command: Int) : DecodedPacket
}

public object PacketDecoder {
    public fun decode(packet: TripperPacket): DecodedPacket {
        val b = (0 until TripperPacket.PAYLOAD_SIZE).map { packet[it] }
        val restZero = { from: Int -> (from until TripperPacket.PAYLOAD_SIZE).all { b[it] == 0 } }
        return when {
            b[0] == TripperCommands.CMD_HANDSHAKE && b[1] == 0x01 && restZero(2) -> DecodedPacket.HandshakeShowPin
            b[0] == TripperCommands.CMD_HANDSHAKE && b[1] == 0x00 && restZero(2) -> DecodedPacket.HandshakeResume
            b[0] == TripperCommands.CMD_PING && restZero(1) -> DecodedPacket.PingFirmware
            b[0] == TripperCommands.CMD_KEEPALIVE && b[1] == TripperCommands.KEEPALIVE_SUB_CALL -> DecodedPacket.CallIcon
            b[0] == TripperCommands.CMD_PIN -> DecodedPacket.Pin(
                b.subList(1, 1 + TripperCommands.PIN_MAX_LENGTH).takeWhile { it != 0 }
                    .joinToString("") { it.toChar().toString() },
            )
            b[0] == TripperCommands.CMD_SET_TIME -> DecodedPacket.SetTime(b[1], b[2])
            b[0] == TripperCommands.CMD_NAVIGATE && b[1] == TripperCommands.CMD_NAVIGATE_SUB -> decodeNavigate(b)
            else -> DecodedPacket.Unknown(b[0])
        }
    }

    private fun decodeNavigate(b: List<Int>): DecodedPacket = when {
        b[2] == TripperCommands.SCREEN_COMPASS && b[10] != NavigationFrame.NAVIGATION_MARKER ->
            DecodedPacket.Compass(CompassDirection.fromCode(b[14]), DisplayIntensity.isNight(b[6]))
        b[10] == NavigationFrame.NAVIGATION_MARKER -> {
            val aux = (b[8] shl 8) or b[9]
            DecodedPacket.Navigation(
                icon = b[2],
                distance = DistanceEncoding.decode(b[3], b[4], b[5]),
                byte6 = b[6],
                nextIcon = b[7],
                auxDistanceMeters = if (aux == 0xFFFF) null else aux,
                bottomRaw = listOf(b[11], b[12], b[13]),
            )
        }
        else -> DecodedPacket.Screen(b[2], DisplayIntensity.isNight(b[6]))
    }
}
