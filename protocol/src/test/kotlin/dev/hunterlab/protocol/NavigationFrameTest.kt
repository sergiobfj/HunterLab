package dev.hunterlab.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalTime

/** Todos os vetores "REAL" vêm do log de um trajeto num Tripper real (referência). */
class NavigationFrameTest {

    private val total2542 = BottomLine.TotalDistance(2542) // -> 00 19 02

    @Test
    fun `REAL virar a esquerda 167 m total 2,5 km noite`() {
        val f = NavigationFrame(0x14, 167, byte6 = 0x01, bottomLine = total2542)
        assertEquals("10 11 14 00 A7 01 01 FF FF FF 41 00 19 02 00 00 00 00 19 1A", f.toPacket().toHex())
    }

    @Test
    fun `REAL distancia zero`() {
        val f = NavigationFrame(0x14, 0, byte6 = 0x01, bottomLine = total2542)
        assertEquals("10 11 14 00 00 01 01 FF FF FF 41 00 19 02 00 00 00 00 A3 AD", f.toPacket().toHex())
    }

    @Test
    fun `REAL rodape em tempo restante e em distancia total`() {
        assertEquals(
            "10 11 14 00 A6 01 01 FF FF FF 41 00 08 00 00 00 00 00 79 DC",
            NavigationFrame(0x14, 166, 0x01, bottomLine = BottomLine.TimeRemaining(8)).toPacket().toHex(),
        )
        assertEquals(
            "10 11 14 00 A6 01 01 FF FF FF 41 00 1D 02 00 00 00 00 64 DA",
            NavigationFrame(0x14, 166, 0x01, bottomLine = BottomLine.TotalDistance(2942)).toPacket().toHex(),
        )
    }

    @Test
    fun `REAL chegando ao destino a 68 m com intensidade calculada`() {
        val byte6 = DisplayIntensity.byte6(68, DayPeriod.NIGHT, intensityByDistance = true)
        assertEquals(0x21, byte6)
        val f = NavigationFrame(TripperIcons.DESTINATION, 68, byte6, bottomLine = BottomLine.TotalDistance(68))
        assertEquals("10 11 00 00 44 01 21 FF FF FF 41 00 44 01 00 00 00 00 68 CA", f.toPacket().toHex())
    }

    @Test
    fun `REAL horario de chegada 24h e 12h`() {
        val at1700 = LocalTime.of(17, 0)
        val h24 = BottomLine.arrivalAfter(at1700, 28, ClockFormat.H24)
        val h12 = BottomLine.arrivalAfter(at1700, 28, ClockFormat.H12)
        assertEquals(
            "10 11 00 00 44 01 21 FF FF FF 41 11 1C 00 00 00 00 00 30 57",
            NavigationFrame(0x00, 68, 0x21, bottomLine = h24).toPacket().toHex(),
        )
        assertEquals(
            "10 11 00 00 44 01 21 FF FF FF 41 85 1C 00 00 00 00 00 6D 13",
            NavigationFrame(0x00, 68, 0x21, bottomLine = h12).toPacket().toHex(),
        )
    }

    @Test
    fun `REAL radar modo compativel`() {
        val base = NavigationFrame(0x14, 174, 0x01, bottomLine = total2542)
        val withRadar = base.withRadar(260, RadarDisplayMode.COMPATIBLE)
        assertEquals("10 11 14 00 AE 01 01 3C FF FF 41 01 04 01 00 00 00 00 3E E1", withRadar.toPacket().toHex())
    }

    @Test
    fun `REAL layout antigo de radar continua reproduzivel byte a byte`() {
        val f = NavigationFrame(0x14, 167, 0x01, nextIcon = 0x45, bottomLine = BottomLine.RadarDistance(250))
        assertEquals("10 11 14 00 A7 01 01 45 FF FF 41 00 FA 01 00 00 00 00 C6 C0", f.toPacket().toHex())
    }

    @Test
    fun `LOCK radar experimental usa bytes 8 e 9`() {
        val base = NavigationFrame(0x14, 174, 0x01, bottomLine = total2542)
        val exp = base.withRadar(260, RadarDisplayMode.EXPERIMENTAL).toPacket()
        assertEquals(0x3C, exp[7])
        assertEquals(0x01, exp[8])
        assertEquals(0x04, exp[9])
        assertEquals(listOf(0x00, 0x19, 0x02), listOf(exp[11], exp[12], exp[13])) // rodapé preservado

        val none = base.copy(nextIcon = 0x3C).toPacket()
        assertEquals(0xFF, none[8])
        assertEquals(0xFF, none[9])

        val big = base.withRadar(70_000, RadarDisplayMode.EXPERIMENTAL).toPacket()
        assertEquals(0xFF, big[8])
        assertEquals(0xFE, big[9]) // FFFF é reservado para "sem dado"
    }

    @Test
    fun `radar nunca altera icone, distancia e byte de noite`() {
        val frames = listOf(
            NavigationFrame(0x14, 50, 0x21, bottomLine = total2542),
            NavigationFrame(0x32, 1500, 0x01, nextIcon = 0x15, bottomLine = BottomLine.TimeRemaining(30)),
            NavigationFrame(0x00, 0, 0x50, bottomLine = BottomLine.None),
        )
        for (f in frames) {
            for (mode in RadarDisplayMode.entries) {
                for (radarM in listOf(0, 120, 299, 999, 1000)) {
                    val a = f.toPacket()
                    val b = f.withRadar(radarM, mode).toPacket()
                    for (i in 0..6) assertEquals(a[i], b[i], "byte [$i] mudou com radar ($mode, $radarM m)")
                    assertEquals(0x41, b[10])
                }
            }
        }
    }

    @Test
    fun `bytes fixos do layout de navegacao`() {
        val p = NavigationFrame(0x09, 999, 0).toPacket()
        assertEquals(0x10, p[0])
        assertEquals(0x11, p[1])
        assertEquals(0x41, p[10])
        for (i in 14..17) assertEquals(0, p[i])
        assertEquals(listOf(0xFF, 0xFF, 0xFF), listOf(p[11], p[12], p[13])) // sem rodapé
    }

    @Test
    fun `entradas invalidas sao rejeitadas`() {
        assertThrows<IllegalArgumentException> { NavigationFrame(0x100, 0) }
        assertThrows<IllegalArgumentException> { NavigationFrame(-1, 0) }
        assertThrows<IllegalArgumentException> { NavigationFrame(0x14, -1) }
        assertThrows<IllegalArgumentException> { NavigationFrame(0x14, 0, byte6 = 256) }
        assertThrows<IllegalArgumentException> { NavigationFrame(0x14, 0, nextIcon = 300) }
        assertThrows<IllegalArgumentException> { NavigationFrame(0x14, 0, auxDistanceMeters = -5) }
        assertThrows<IllegalArgumentException> { NavigationFrame(0x14, 0).withRadar(-1, RadarDisplayMode.COMPATIBLE) }
    }
}
