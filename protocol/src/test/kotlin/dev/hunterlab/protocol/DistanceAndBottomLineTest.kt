package dev.hunterlab.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.LocalTime

class DistanceAndBottomLineTest {

    private fun EncodedDistance.hex() = Hex.encode(byteArrayOf(high.toByte(), low.toByte(), unit.code.toByte()))

    @ParameterizedTest(name = "{0} m -> {1} ({2})")
    @CsvSource(
        "0, 00 00 01, 0 m",
        "68, 00 44 01, 68 m",
        "260, 01 04 01, 260 m",
        "999, 03 E7 01, 999 m",       // ainda em metros
        "1000, 00 0A 02, '1,0 km'",   // vira km x 10
        "1099, 00 0A 02, '1,0 km'",   // truncado, não arredondado
        "2942, 00 1D 02, '2,9 km'",
        "22900, 00 E5 02, '22,9 km'",
        "99999999, FF FF 02, '6553,5 km'", // limitado a 0xFFFF
    )
    fun `codificacao e formatacao de distancia`(meters: Int, hex: String, text: String) {
        val e = DistanceEncoding.encode(meters)
        assertEquals(hex, e.hex())
        assertEquals(text, e.format())
    }

    @Test
    fun `distancia representada mostra a perda de precisao`() {
        assertEquals(2900, DistanceEncoding.encode(2942).representedMeters)
        assertEquals(999, DistanceEncoding.encode(999).representedMeters)
    }

    @Test
    fun `distancia negativa e erro explicito`() {
        assertThrows<IllegalArgumentException> { DistanceEncoding.encode(-1) }
        assertThrows<IllegalArgumentException> { BottomLine.TotalDistance(-10) }
        assertThrows<IllegalArgumentException> { BottomLine.RadarDistance(-10) }
    }

    @Test
    fun `decodificacao de distancia e unidades`() {
        assertEquals(EncodedDistance(167, DistanceUnit.METERS), DistanceEncoding.decode(0x00, 0xA7, 1))
        assertEquals(EncodedDistance(229, DistanceUnit.KILOMETER_TENTHS), DistanceEncoding.decode(0x00, 0xE5, 2))
        assertNull(DistanceEncoding.decode(0, 0, 0))
        assertNull(DistanceEncoding.decode(0, 0, 3))
        assertThrows<IllegalArgumentException> { EncodedDistance(0x10000, DistanceUnit.METERS) }
    }

    @Test
    fun `rodape sem dado e FF FF FF`() {
        assertEquals(listOf(0xFF, 0xFF, 0xFF), BottomLine.None.encode())
    }

    @Test
    fun `REAL rodape de distancia total e tempo restante`() {
        assertEquals(listOf(0x00, 0x1D, 0x02), BottomLine.TotalDistance(2942).encode())
        assertEquals(listOf(0x00, 0x08, 0x00), BottomLine.TimeRemaining(8).encode())
        assertEquals(listOf(0x02, 0x05, 0x00), BottomLine.TimeRemaining(125).encode())
        assertEquals(listOf(23, 20, 0), BottomLine.TimeRemaining(2000).encode()) // horas limitadas a 23
        assertThrows<IllegalArgumentException> { BottomLine.TimeRemaining(-1) }
    }

    @ParameterizedTest(name = "{0} + {1} min, {2} -> {3}")
    @CsvSource(
        "17:00, 28, H24, 11 1C 00", // REAL
        "17:00, 28, H12, 85 1C 00", // REAL (5:28 PM)
        "09:05, 10, H12, 49 0F 00", // 9:15 AM
        "23:50, 20, H24, 00 0A 00", // vira a meia-noite
        "23:50, 20, H12, 40 0A 00", // 0:10 AM
        "11:30, 45, H12, 80 0F 00", // 12:15 PM -> hora 0 | 0x80
    )
    fun `horario de chegada`(now: String, eta: Int, format: ClockFormat, expected: String) {
        val line = BottomLine.arrivalAfter(LocalTime.parse(now), eta, format)
        assertEquals(expected, Hex.encode(line.encode().map { it.toByte() }.toByteArray()))
    }

    @Test
    fun `horario de chegada invalido e rejeitado`() {
        assertThrows<IllegalArgumentException> { BottomLine.ArrivalTime(24, 0, ClockFormat.H24) }
        assertThrows<IllegalArgumentException> { BottomLine.ArrivalTime(10, 60, ClockFormat.H24) }
        assertThrows<IllegalArgumentException> { BottomLine.arrivalAfter(LocalTime.NOON, -5, ClockFormat.H24) }
    }

    @ParameterizedTest(name = "{0} m -> 0x{1}")
    @CsvSource("0, 50", "10, 50", "11, 40", "20, 40", "21, 30", "45, 30", "46, 20", "70, 20", "71, 10", "95, 10", "96, 00", "500, 00", "2147483647, 00")
    fun `intensidade por faixa de distancia`(meters: Int, expectedHex: String) {
        assertEquals(expectedHex.toInt(16), DisplayIntensity.forDistance(meters))
    }

    @Test
    fun `alternancia dia e noite no byte 6`() {
        assertEquals(0x21, DisplayIntensity.byte6(68, DayPeriod.NIGHT, true))   // REAL
        assertEquals(0x01, DisplayIntensity.byte6(166, DayPeriod.NIGHT, true))  // REAL
        assertEquals(0x20, DisplayIntensity.byte6(68, DayPeriod.DAY, true))
        assertEquals(0x01, DisplayIntensity.byte6(5, DayPeriod.NIGHT, false))   // só o flag
        assertEquals(0x00, DisplayIntensity.byte6(5, DayPeriod.DAY, false))
        assertTrue(DisplayIntensity.isNight(0x21))
        assertFalse(DisplayIntensity.isNight(0x50))
    }
}
