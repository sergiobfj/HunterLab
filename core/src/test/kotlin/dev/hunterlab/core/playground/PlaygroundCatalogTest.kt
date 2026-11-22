package dev.hunterlab.core.playground

import dev.hunterlab.protocol.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PlaygroundCatalogTest {
    @Test fun `catalogo preserva todos os codigos e identifica as oito bussolas`() {
        assertEquals(71, TripperIcons.all.size)
        assertEquals(79, PlaygroundCatalog.all.size)
        assertEquals(81, PlaygroundCatalog.entries.size)
        assertEquals(79, PlaygroundCatalog.all.map { it.id }.distinct().size)
        assertEquals(8, PlaygroundCatalog.physicalIds.size)
        assertEquals(TripperIcons.all.map { it.code }.toSet(), PlaygroundCatalog.all.mapNotNull { it.iconCode }.toSet())
        assertTrue(PlaygroundCatalog.all.filter { it.direction == null }.none { it.hardwareEligible })
    }
    @Test fun `evidencia na Hunter e especifica para direcao e modo`() {
        val n = requireNotNull(PlaygroundCatalog.find("compass-N"))
        val e = requireNotNull(PlaygroundCatalog.find("compass-E"))
        assertEquals(VisualEvidence.HUNTER, n.evidence(DayPeriod.NIGHT))
        assertEquals(VisualEvidence.REFERENCE, n.evidence(DayPeriod.DAY))
        assertEquals(VisualEvidence.HUNTER, e.evidence(DayPeriod.DAY))
        assertEquals(VisualEvidence.REFERENCE, e.evidence(DayPeriod.NIGHT))
        assertTrue(PlaygroundCatalog.all.filter { it.iconCode != null }.none { it.evidence(DayPeriod.DAY) == VisualEvidence.HUNTER })
    }
    @Test fun `previas para todos os simbolos nao dependem de radio e emitem matriz auditavel`() {
        PlaygroundCatalog.entries.forEach { s ->
            assertTrue(s.builder.startsWith("TripperCommands.") || s.special == SpecialPreview.CLOCK)
            DayPeriod.entries.forEach { assertNotNull(s.preview(it)); assertNotNull(s.preview(it, IconSlot.SMALL)) }
            println("MATRIX|${s.id}|${s.label}|${s.category.label}|${s.command}|${s.builder}|${s.confidence.label}|${s.evidence(DayPeriod.DAY).label}|${s.evidence(DayPeriod.NIGHT).label}|${s.hardwareEligible}")
        }
    }
    @Test fun `selecao nao aceita bytes desconhecidos ou efeito arbitrario`() {
        assertNull(PlaygroundCatalog.find("FF 01 02"))
        assertNull(PlaygroundCatalog.find("gif"))
        assertThrows(IllegalArgumentException::class.java) { PlaygroundSelection("icon-256") }
        val state = PlaygroundSelection().select("compass-E").copy(period = DayPeriod.DAY)
        assertEquals(VisualEvidence.HUNTER, state.symbol.evidence(state.period))
        assertTrue(state.preview.night.not())
    }
}
