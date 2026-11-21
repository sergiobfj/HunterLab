package dev.hunterlab.core.playground

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SymbolSequenceTest {

    private val seq = SymbolSequence("teste", listOf(SequenceStep(0x14, 1_000), SequenceStep(0x15, 500), SequenceStep(0x09, 2_000)))

    @Test
    fun `sequencia valida`() {
        assertTrue(seq.isValid)
        assertEquals(3_500, seq.totalDurationMs)
    }

    @Test
    fun `passo ativo no tempo`() {
        assertEquals(0, seq.stepIndexAt(0, loop = false))
        assertEquals(0, seq.stepIndexAt(999, loop = false))
        assertEquals(1, seq.stepIndexAt(1_000, loop = false))
        assertEquals(2, seq.stepIndexAt(3_499, loop = false))
        assertNull(seq.stepIndexAt(3_500, loop = false))
        assertEquals(0, seq.stepIndexAt(3_500, loop = true))
        assertEquals(1, seq.stepIndexAt(4_600, loop = true))
        assertThrows<IllegalArgumentException> { seq.stepIndexAt(-1, loop = true) }
    }

    @Test
    fun `erros bloqueantes`() {
        assertFalse(SymbolSequence("vazia", emptyList()).isValid)
        assertFalse(SymbolSequence("curta", listOf(SequenceStep(0x14, 100))).isValid)
        assertFalse(SymbolSequence("longa", listOf(SequenceStep(0x14, 60_000))).isValid)
        assertFalse(SymbolSequence("fora", listOf(SequenceStep(0x99, 1_000))).isValid)
        assertFalse(SymbolSequence("muitos", List(13) { SequenceStep(0x09, 1_000) }).isValid)
        assertFalse(SymbolSequence("byte", listOf(SequenceStep(0x1FF, 1_000))).isValid)
    }

    @Test
    fun `avisos nao bloqueiam`() {
        val s = SymbolSequence("aviso", listOf(SequenceStep(0x23, 1_000), SequenceStep(0x45, 1_000, IconSlot.SMALL)))
        val issues = s.validate()
        assertEquals(2, issues.size)
        assertTrue(issues.none { it.blocking })
        assertTrue(s.isValid)
    }

    @Test
    fun `execucao em hardware bloqueada`() {
        assertFalse(HardwareExecutionPolicy.ALLOWED)
        assertTrue(HardwareExecutionPolicy.REASON.isNotBlank())
    }
}
