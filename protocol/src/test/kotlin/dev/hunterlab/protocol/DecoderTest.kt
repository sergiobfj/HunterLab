package dev.hunterlab.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Ida e volta: o que os builders produzem é interpretado de volta sem ambiguidade. */
class DecoderTest {

    private fun decode(p: TripperPacket) = PacketDecoder.decode(p)

    @Test
    fun `pacotes fixos`() {
        assertEquals(DecodedPacket.HandshakeShowPin, decode(TripperCommands.handshakeShowPin()))
        assertEquals(DecodedPacket.HandshakeResume, decode(TripperCommands.handshakeResume()))
        assertEquals(DecodedPacket.PingFirmware, decode(TripperCommands.pingFirmware()))
        assertEquals(DecodedPacket.CallIcon, decode(TripperCommands.callIcon()))
        assertEquals(DecodedPacket.Pin("4821"), decode(TripperCommands.pin("4821")))
        assertEquals(DecodedPacket.SetTime(0x41, 5), decode(TripperCommands.setTime(1, 5, ClockFormat.H12)))
    }

    @Test
    fun `bussola`() {
        for (d in CompassDirection.entries) {
            assertEquals(DecodedPacket.Compass(d, true), decode(TripperCommands.compass(d, DayPeriod.NIGHT)))
        }
    }

    @Test
    fun `navegacao com radar experimental`() {
        val frame = NavigationFrame(0x33, 1500, 0x01, nextIcon = 0x15, bottomLine = BottomLine.TimeRemaining(30))
            .withRadar(260, RadarDisplayMode.EXPERIMENTAL)
        val d = decode(frame.toPacket()) as DecodedPacket.Navigation
        assertEquals(0x33, d.icon)
        assertEquals(EncodedDistance(15, DistanceUnit.KILOMETER_TENTHS), d.distance)
        assertTrue(d.night)
        assertEquals(0x3C, d.nextIcon)
        assertEquals(260, d.auxDistanceMeters)
        assertEquals(listOf(0, 30, 0), d.bottomRaw)
    }

    @Test
    fun `navegacao sem dado auxiliar`() {
        val d = decode(NavigationFrame(0x09, 80).toPacket()) as DecodedPacket.Navigation
        assertNull(d.auxDistanceMeters)
        assertEquals(listOf(0xFF, 0xFF, 0xFF), d.bottomRaw)
    }

    @Test
    fun `telas especiais`() {
        assertEquals(DecodedPacket.Screen(0x1C, true), decode(TripperCommands.recalculating(DayPeriod.NIGHT)))
        val started = decode(TripperCommands.routeStarted()) as DecodedPacket.Navigation
        assertEquals(TripperIcons.ROUTE_STARTED, started.icon)
    }

    @Test
    fun `comando desconhecido`() {
        val p = TripperPacket.fromPayload(ByteArray(18).also { it[0] = 0x7E })
        assertEquals(DecodedPacket.Unknown(0x7E), decode(p))
    }
}
