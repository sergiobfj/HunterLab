package dev.hunterlab.core.playground

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PreviewSequenceTest {
    @Test fun `presets sao limitados catalogados e exclusivamente simulados`() {
        assertEquals(4, PreviewSequence.presets.size)
        PreviewSequence.presets.values.forEach { assertTrue(it.valid); assertTrue(it.durationMs <= 45_000) }
        assertFalse(HardwareExecutionPolicy.ALLOWED)
    }
    @Test fun `sequencias invalidas nunca iniciam`() {
        val invalid = listOf(
            PreviewSequence(emptyList()), PreviewSequence(List(9) { PreviewStage("compass-N") }),
            PreviewSequence(listOf(PreviewStage("arbitrary"))), PreviewSequence(listOf(PreviewStage("compass-N", Long.MAX_VALUE))),
            PreviewSequence(listOf(PreviewStage("compass-N", 1_999))), PreviewSequence(List(8) { PreviewStage("compass-N", 10_000) }),
        )
        invalid.forEach { seq ->
            assertFalse(seq.valid); assertNull(seq.indexAt(0)); assertFalse(PreviewPlayer { 0 }.play(seq))
        }
    }
    @Test fun `ordem limite e fim nao entram em loop`() {
        val seq = PreviewSequence(listOf(PreviewStage("compass-N"), PreviewStage("compass-E")))
        assertNull(seq.indexAt(-1)); assertEquals(0, seq.indexAt(0)); assertEquals(0, seq.indexAt(1_999))
        assertEquals(1, seq.indexAt(2_000)); assertNull(seq.indexAt(4_000))
        var now = 0L; val player = PreviewPlayer { now }
        assertTrue(player.play(seq)); now = 4_000; player.tick()
        assertEquals(PlaybackPhase.FINISHED, player.phase); assertNull(player.index)
    }
    @Test fun `pausa interromper e reiniciar invalidam a reproducao anterior`() {
        var now = 0L; val player = PreviewPlayer { now }
        val seq = PreviewSequence(listOf(PreviewStage("compass-N"), PreviewStage("compass-E")))
        player.play(seq); now = 1_500; player.pause(); now = 20_000
        assertEquals(1_500L, player.elapsedMs); assertEquals(0, player.index)
        player.play(); now += 600; assertEquals(1, player.index)
        player.stop(); assertNull(player.index); assertEquals(0L, player.elapsedMs)
        assertTrue(player.restart()); assertEquals(0, player.index)
        player.stop(); now += 100_000; player.tick(); assertEquals(PlaybackPhase.STOPPED, player.phase)
    }
}
