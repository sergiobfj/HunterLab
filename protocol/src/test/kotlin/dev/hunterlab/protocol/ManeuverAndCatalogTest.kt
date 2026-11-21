package dev.hunterlab.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ManeuverAndCatalogTest {

    private fun large(m: Maneuver) = ManeuverMapper.map(m).large
    private fun small(m: Maneuver) = ManeuverMapper.map(m).small

    @Test
    fun `traducao das manobras basicas (mao direita)`() {
        assertEquals(0x09, large(Maneuver.Straight))
        assertEquals(0x14, large(Maneuver.Turn(Side.LEFT)))
        assertEquals(0x15, large(Maneuver.Turn(Side.RIGHT)))
        assertEquals(0x16, large(Maneuver.Turn(Side.LEFT, TurnSharpness.SHARP)))
        assertEquals(0x17, large(Maneuver.Turn(Side.RIGHT, TurnSharpness.SHARP)))
        assertEquals(0x18, large(Maneuver.Turn(Side.LEFT, TurnSharpness.SLIGHT)))
        assertEquals(0x19, large(Maneuver.Turn(Side.RIGHT, TurnSharpness.SLIGHT)))
        assertEquals(0x3D, large(Maneuver.UTurn))
        assertEquals(0x00, large(Maneuver.Arrive()))
        assertEquals(0x01, large(Maneuver.Arrive(Side.LEFT)))
        assertEquals(0x02, large(Maneuver.Arrive(Side.RIGHT)))
    }

    @Test
    fun `manter e sair usam seta pequena generica de faixa`() {
        assertEquals(0x28, large(Maneuver.Keep(Side.LEFT)))
        assertEquals(0x27, large(Maneuver.Keep(Side.RIGHT)))
        assertEquals(0x2E, large(Maneuver.Exit(Side.LEFT)))
        assertEquals(0x2D, large(Maneuver.Exit(Side.RIGHT)))
        assertEquals(0x2B, small(Maneuver.Keep(Side.LEFT)))
        assertEquals(0x2B, small(Maneuver.Exit(Side.LEFT)))
        assertEquals(0x2C, small(Maneuver.Keep(Side.RIGHT)))
        assertEquals(0x2C, small(Maneuver.Exit(Side.RIGHT)))
        assertEquals(0x14, small(Maneuver.Turn(Side.LEFT)))
    }

    @Test
    fun `rotatoria mao direita usa 0x32 mais saida e limita em 9`() {
        assertEquals(0x32, large(Maneuver.Roundabout(1)))
        assertEquals(0x33, large(Maneuver.Roundabout(2)))
        assertEquals(0x34, small(Maneuver.Roundabout(3)))
        assertEquals(0x3A, large(Maneuver.Roundabout(9)))
        assertEquals(0x3A, large(Maneuver.Roundabout(12)))
        assertThrows<IllegalArgumentException> { Maneuver.Roundabout(0) }
    }

    @Test
    fun `manobras sem lado sao rejeitadas`() {
        assertThrows<IllegalArgumentException> { Maneuver.Turn(Side.NONE) }
        assertThrows<IllegalArgumentException> { Maneuver.Keep(Side.NONE) }
        assertThrows<IllegalArgumentException> { Maneuver.Exit(Side.NONE) }
        assertThrows<IllegalArgumentException> { Maneuver.Fork(Side.NONE) }
    }

    @Test
    fun `confianca da traducao segue a referencia`() {
        assertEquals(Confidence.CONFIRMED, ManeuverMapper.map(Maneuver.Straight).confidence)
        assertEquals(Confidence.CONFIRMED, ManeuverMapper.map(Maneuver.Keep(Side.LEFT)).confidence)
        assertEquals(Confidence.CONFIRMED, ManeuverMapper.map(Maneuver.Exit(Side.RIGHT)).confidence)
        assertEquals(Confidence.LIKELY, ManeuverMapper.map(Maneuver.Turn(Side.LEFT)).confidence)
        assertEquals(Confidence.LIKELY, ManeuverMapper.map(Maneuver.UTurn).confidence)
        assertEquals(Confidence.LIKELY, ManeuverMapper.map(Maneuver.Roundabout(2)).confidence)
    }

    @Test
    fun `mao inglesa e explicitamente nao verificada`() {
        val r = ManeuverMapper.map(Maneuver.Roundabout(1), TrafficSide.LEFT_HAND)
        assertEquals(0x0B, r.large)
        assertEquals(Confidence.UNVERIFIED, r.confidence)
        assertEquals(0x1A, ManeuverMapper.map(Maneuver.UTurn, TrafficSide.LEFT_HAND).large)
    }

    @Test
    fun `toda manobra traduz para codigos presentes no catalogo`() {
        val all = buildList {
            add(Maneuver.Straight); add(Maneuver.UTurn); add(Maneuver.Depart)
            for (s in listOf(Side.LEFT, Side.RIGHT)) {
                TurnSharpness.entries.forEach { add(Maneuver.Turn(s, it)) }
                add(Maneuver.Keep(s)); add(Maneuver.Exit(s)); add(Maneuver.Fork(s))
            }
            Side.entries.forEach { add(Maneuver.Arrive(it)); add(Maneuver.Merge(it)) }
            (1..12).forEach { add(Maneuver.Roundabout(it)) }
        }
        for (m in all) {
            for (traffic in TrafficSide.entries) {
                val icons = ManeuverMapper.map(m, traffic)
                assertNotNull(TripperIcons.byCode(icons.large), "$m grande 0x${Hex.byte(icons.large)}")
                assertNotNull(TripperIcons.byCode(icons.small), "$m pequena 0x${Hex.byte(icons.small)}")
            }
        }
    }

    @Test
    fun `catalogo tem codigos unicos de 1 byte`() {
        val codes = TripperIcons.all.map { it.code }
        assertEquals(71, codes.size) // documentado em docs/PROTOCOLO.md §6
        assertEquals(codes.size, codes.toSet().size)
        assertTrue(codes.all { it in 0..0xFF })
    }

    @Test
    fun `catalogo contem todos os codigos da tabela documentada`() {
        val documented = listOf(
            0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x14, 0x15, 0x16, 0x17, 0x18,
            0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E, 0x1F, 0x20, 0x21, 0x22, 0x27, 0x28, 0x2B, 0x2C, 0x2D, 0x2E,
            0x2F, 0x30, 0x31, 0x3C, 0x3D, 0x3E, 0x3F, 0x42, 0x44, 0xFF,
        )
        for (code in documented) {
            val icon = TripperIcons.byCode(code)
            assertNotNull(icon, "0x${Hex.byte(code)}")
            assertTrue(icon!!.confidence != Confidence.UNVERIFIED, "0x${Hex.byte(code)} deveria ser documentado")
        }
    }

    @Test
    fun `faixas nao testadas sao marcadas como nao verificadas`() {
        val untested = (0x0B..0x13) + listOf(0x23, 0x24, 0x25, 0x26, 0x29, 0x2A, 0x3B, 0x40, 0x43)
        for (code in untested) assertEquals(Confidence.UNVERIFIED, TripperIcons.describe(code).confidence, "0x${Hex.byte(code)}")
        assertEquals(Confidence.UNVERIFIED, TripperIcons.describe(0x99).confidence)
        assertEquals(IconDepiction.Unknown, TripperIcons.describe(0x99).depiction)
    }

    @Test
    fun `codigos com fontes divergentes sao provaveis`() {
        assertEquals(Confidence.LIKELY, TripperIcons.describe(0x1E).confidence)
        assertEquals(Confidence.LIKELY, TripperIcons.describe(0x1F).confidence)
        assertEquals(Confidence.LIKELY, TripperIcons.describe(0x45).confidence)
    }

    @Test
    fun `nada foi validado na Hunter 350 nesta versao`() {
        assertTrue(TripperIcons.all.all { it.hunter == HunterValidation.NOT_TESTED })
    }

    @Test
    fun `confianca mais fraca prevalece`() {
        assertEquals(Confidence.LIKELY, Confidence.CONFIRMED.weakest(Confidence.LIKELY))
        assertEquals(Confidence.UNVERIFIED, Confidence.UNVERIFIED.weakest(Confidence.CONFIRMED))
        assertEquals(Confidence.CONFIRMED, Confidence.CONFIRMED.weakest(Confidence.CONFIRMED))
    }
}
