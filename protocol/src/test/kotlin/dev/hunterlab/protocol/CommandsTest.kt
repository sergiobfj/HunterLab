package dev.hunterlab.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class CommandsTest {

    @Test
    fun `REAL pacotes fixos`() {
        assertEquals("03 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 45 D9", TripperCommands.pingFirmware().toHex())
        assertEquals("21 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 40 45", TripperCommands.handshakeResume().toHex())
        assertEquals("21 01 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 50 A7", TripperCommands.handshakeShowPin().toHex())
    }

    @Test
    fun `PIN 1234 vira comando 0x20 mais ASCII`() {
        assertEquals("20 31 32 33 34 00 00", TripperCommands.pin("1234").toHex().substring(0, 20))
        assertEquals("20 31 32 33 34 35 36 00", TripperCommands.pin("123456").toHex().substring(0, 23))
    }

    @Test
    fun `PIN invalido e rejeitado em vez de truncado`() {
        assertThrows<IllegalArgumentException> { TripperCommands.pin("") }
        assertThrows<IllegalArgumentException> { TripperCommands.pin("1234567") }
        assertThrows<IllegalArgumentException> { TripperCommands.pin("12 4") }
        assertThrows<IllegalArgumentException> { TripperCommands.pin("12é4") }
    }

    @Test
    fun `REAL SET_TIME em 12h`() {
        assertEquals(
            "50 01 37 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 51 A8",
            TripperCommands.setTime(13, 55, ClockFormat.H12).toHex(),
        )
        assertEquals(
            "50 01 36 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 F9 8C",
            TripperCommands.setTime(13, 54, ClockFormat.H12).toHex(),
        )
    }

    @ParameterizedTest(name = "{0}:{1} {2} -> byte[1]=0x{3}")
    @CsvSource(
        "13, 55, H24, 0D", // 24h: hora crua
        "9, 5, H12, 49",   // AM soma 0x40
        "0, 0, H12, 4C",   // meia-noite = 12 AM
        "12, 0, H12, 0C",  // meio-dia = 12 PM, sem bit
        "23, 59, H12, 0B",
        "0, 0, H24, 00",
    )
    fun `SET_TIME codifica a hora`(hour: Int, minute: Int, format: ClockFormat, expectedHex: String) {
        val p = TripperCommands.setTime(hour, minute, format)
        assertEquals(expectedHex.toInt(16), p[1])
        assertEquals(minute, p[2])
    }

    @Test
    fun `SET_TIME rejeita hora ou minuto invalidos`() {
        assertThrows<IllegalArgumentException> { TripperCommands.setTime(24, 0, ClockFormat.H24) }
        assertThrows<IllegalArgumentException> { TripperCommands.setTime(-1, 0, ClockFormat.H24) }
        assertThrows<IllegalArgumentException> { TripperCommands.setTime(10, 60, ClockFormat.H12) }
    }

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource(
        "SE, NIGHT, 10 11 41 00 00 00 01 FF 00 00 00 FF FF FF 70 00 00 00 CE D1", // REAL
        "SW, NIGHT, 10 11 41 00 00 00 01 FF 00 00 00 FF FF FF 80 00 00 00 51 9C", // REAL
        "NE, NIGHT, 10 11 41 00 00 00 01 FF 00 00 00 FF FF FF 50 00 00 00 F9 9F", // REAL
        "N, NIGHT, 10 11 41 00 00 00 01 FF 00 00 00 FF FF FF 10 00 00 00 97 03",  // REAL
        "N, DAY, 10 11 41 00 00 00 00 FF 00 00 00 FF FF FF 10 00 00 00 94 76",    // LOCK
    )
    fun `bussola`(direction: CompassDirection, period: DayPeriod, expected: String) {
        assertEquals(expected, TripperCommands.compass(direction, period).toHex())
    }

    @ParameterizedTest(name = "rumo {0} -> {1}")
    @CsvSource(
        "141, SE", "235, SW", "51, NE", "10, N", // REAL (rumos do log)
        "22.4, N", "22.6, NE", "359.9, N", "90, E", "180, S", "270, W", "315, NW",
        "-10, N", "370, N", "-90, W", "720, N",
    )
    fun `rumo para setor da bussola`(bearing: Double, expected: CompassDirection) {
        assertEquals(expected, CompassDirection.fromBearing(bearing))
    }

    @Test
    fun `rumo invalido e rejeitado`() {
        assertThrows<IllegalArgumentException> { CompassDirection.fromBearing(Double.NaN) }
        assertThrows<IllegalArgumentException> { CompassDirection.fromBearing(Double.POSITIVE_INFINITY) }
    }

    @Test
    fun `LOCK telas especiais`() {
        assertEquals(
            "10 11 45 00 00 01 00 FF FF FF 41 FF FF FF 00 00 00 00 1A 5A",
            TripperCommands.routeStarted(DayPeriod.DAY).toHex(),
        )
        assertEquals(
            "10 11 1C 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 0B EA",
            TripperCommands.recalculating(DayPeriod.DAY).toHex(),
        )
        assertEquals(1, TripperCommands.recalculating(DayPeriod.NIGHT)[6])
        assertEquals(
            "40 05 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 ED 5E",
            TripperCommands.callIcon().toHex(),
        )
    }

    @Test
    fun `EXT tela ociosa tem CRC 10 50`() {
        assertEquals(
            "10 11 3C 00 00 04 40 15 00 00 41 00 04 03 00 00 00 00 10 50",
            TripperCommands.idleScreen(DayPeriod.DAY).toHex(),
        )
        assertEquals(0x41, TripperCommands.idleScreen(DayPeriod.NIGHT)[6])
    }

    @Test
    fun `catalogo de comandos tem pacotes validos e nenhum e transmitido`() {
        assertTrue(CommandCatalog.all.isNotEmpty())
        for (c in CommandCatalog.all) {
            assertTrue(TripperPacket.parse(c.example.toByteArray()) is PacketParseResult.Valid, c.name)
            assertFalse(c.transmittedInThisVersion, c.name)
        }
        assertEquals(CommandCatalog.all.size, CommandCatalog.all.map { it.name }.toSet().size)
    }
}
