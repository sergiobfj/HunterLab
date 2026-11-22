package dev.hunterlab.core.playground

/** Editor limitado e estritamente local: não recebe qualquer porta BLE. */
data class PreviewStage(val symbolId: String, val durationMs: Long = 2_000)
data class PreviewSequence(val stages: List<PreviewStage>) {
    val valid: Boolean get() = stages.isNotEmpty() && stages.size <= MAX_STAGES &&
        stages.all { PlaygroundCatalog.find(it.symbolId) != null && it.durationMs in 2_000..10_000 } &&
        stages.sumOf { it.durationMs } <= MAX_DURATION_MS
    val durationMs: Long get() = if (valid) stages.sumOf { it.durationMs } else 0
    fun indexAt(elapsedMs: Long): Int? {
        if (!valid || elapsedMs < 0 || elapsedMs >= durationMs) return null
        var cursor = 0L
        return stages.indexOfFirst { cursor += it.durationMs; elapsedMs < cursor }.takeIf { it >= 0 }
    }
    companion object {
        const val MAX_STAGES = 8
        const val MAX_DURATION_MS = 45_000L
        val presets = linkedMapOf(
            "Bússola girando" to PreviewSequence(listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW").map { PreviewStage("compass-$it") }),
            "Alternância de direções" to PreviewSequence(listOf("N", "E", "S", "W").map { PreviewStage("compass-$it") }),
            "Manobras" to PreviewSequence(listOf(9, 20, 21, 0).map { PreviewStage("icon-$it") }),
            "Símbolos conhecidos" to PreviewSequence(listOf(9, 28, 60, 0).map { PreviewStage("icon-$it") }),
        )
    }
}

enum class PlaybackPhase { STOPPED, PLAYING, PAUSED, FINISHED }
class PreviewPlayer(private val now: () -> Long) {
    var sequence = PreviewSequence(emptyList()); private set
    var phase = PlaybackPhase.STOPPED; private set
    private var offset = 0L
    private var startedAt = 0L
    val elapsedMs: Long get() = (offset + if (phase == PlaybackPhase.PLAYING) (now() - startedAt).coerceAtLeast(0) else 0).coerceAtMost(sequence.durationMs)
    val index: Int? get() = if (phase == PlaybackPhase.STOPPED) null else sequence.indexAt(elapsedMs)
    fun play(value: PreviewSequence = sequence): Boolean {
        if (!value.valid) return false
        if (value != sequence || phase != PlaybackPhase.PAUSED) offset = 0
        sequence = value; startedAt = now(); phase = PlaybackPhase.PLAYING
        return true
    }
    fun tick() { if (phase == PlaybackPhase.PLAYING && elapsedMs >= sequence.durationMs) { offset = sequence.durationMs; phase = PlaybackPhase.FINISHED } }
    fun pause() { if (phase == PlaybackPhase.PLAYING) { offset = elapsedMs; phase = PlaybackPhase.PAUSED } }
    fun stop() { offset = 0; phase = PlaybackPhase.STOPPED }
    fun restart(): Boolean { stop(); return play() }
}
