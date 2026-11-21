package dev.hunterlab.app.ui.playground

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.hunterlab.core.display.PreviewMapper
import dev.hunterlab.core.display.TripperPreview
import dev.hunterlab.core.playground.IconSlot
import dev.hunterlab.core.playground.SequenceIssue
import dev.hunterlab.core.playground.SequenceStep
import dev.hunterlab.core.playground.SymbolSequence
import dev.hunterlab.protocol.BottomLine
import dev.hunterlab.protocol.CompassDirection
import dev.hunterlab.protocol.Confidence
import dev.hunterlab.protocol.DayPeriod
import dev.hunterlab.protocol.DisplayIntensity
import dev.hunterlab.protocol.NavigationFrame
import dev.hunterlab.protocol.TripperCommands
import dev.hunterlab.protocol.TripperIcon
import dev.hunterlab.protocol.TripperIcons
import dev.hunterlab.protocol.TripperPacket
import dev.hunterlab.protocol.TripperScreen
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class SpecialScreen(val label: String) {
    NONE("Símbolo do catálogo"),
    COMPASS("Bússola"),
    ROUTE_STARTED("Rota iniciada"),
    RECALCULATING("Recalculando"),
    CALL_ICON("Ícone de ligação"),
    NATIVE_CLOCK("Relógio nativo"),
}

enum class ConfidenceFilter(val label: String, val confidence: Confidence?) {
    ALL("Todos", null),
    CONFIRMED("Confirmados", Confidence.CONFIRMED),
    LIKELY("Prováveis", Confidence.LIKELY),
    UNVERIFIED("Não verificados", Confidence.UNVERIFIED),
}

data class PlaygroundUiState(
    val filter: ConfidenceFilter,
    val icons: List<TripperIcon>,
    val counts: Map<ConfidenceFilter, Int>,
    val selected: TripperIcon,
    val slot: IconSlot,
    val period: DayPeriod,
    val special: SpecialScreen,
    val compass: CompassDirection,
    val steps: List<SequenceStep>,
    val issues: List<SequenceIssue>,
    val playing: Boolean,
    val playingIndex: Int?,
    val preview: TripperPreview,
    val packet: TripperPacket?,
)

private data class Raw(
    val filter: ConfidenceFilter = ConfidenceFilter.ALL,
    val selectedCode: Int = 0x14,
    val slot: IconSlot = IconSlot.LARGE,
    val period: DayPeriod = DayPeriod.NIGHT,
    val special: SpecialScreen = SpecialScreen.NONE,
    val compass: CompassDirection = CompassDirection.NE,
    val steps: List<SequenceStep> = emptyList(),
    val playingIndex: Int? = null,
)

/** Experimentos visuais **apenas no app**. Nada aqui é transmitido ao Tripper. */
class PlaygroundViewModel : ViewModel() {
    private val raw = MutableStateFlow(Raw())
    private var playback: Job? = null

    val ui: StateFlow<PlaygroundUiState> = raw.map(::build).stateIn(viewModelScope, SharingStarted.Eagerly, build(raw.value))

    private fun build(r: Raw): PlaygroundUiState {
        val playingStep = r.playingIndex?.let { r.steps.getOrNull(it) }
        val code = playingStep?.iconCode ?: r.selectedCode
        val slot = playingStep?.slot ?: r.slot
        val special = if (playingStep != null) SpecialScreen.NONE else r.special
        val (preview, packet) = previewFor(special, code, slot, r.period, r.compass)
        val sequence = SymbolSequence("Playground", r.steps)
        return PlaygroundUiState(
            filter = r.filter,
            icons = TripperIcons.all.filter { r.filter.confidence == null || it.confidence == r.filter.confidence },
            counts = ConfidenceFilter.entries.associateWith { f -> TripperIcons.all.count { f.confidence == null || it.confidence == f.confidence } },
            selected = TripperIcons.describe(r.selectedCode),
            slot = r.slot,
            period = r.period,
            special = r.special,
            compass = r.compass,
            steps = r.steps,
            issues = if (r.steps.isEmpty()) emptyList() else sequence.validate(),
            playing = r.playingIndex != null,
            playingIndex = r.playingIndex,
            preview = preview,
            packet = packet,
        )
    }

    private fun previewFor(special: SpecialScreen, code: Int, slot: IconSlot, period: DayPeriod, compass: CompassDirection): Pair<TripperPreview, TripperPacket?> {
        val night = DisplayIntensity.nightFlag(period)
        val example = NavigationFrame(
            icon = if (slot == IconSlot.LARGE) code else TripperIcons.STRAIGHT,
            distanceMeters = 250,
            byte6 = night,
            nextIcon = if (slot == IconSlot.SMALL) code else TripperIcons.NONE,
            bottomLine = BottomLine.TotalDistance(2_500),
        )
        val screen: TripperScreen = when (special) {
            SpecialScreen.NONE, SpecialScreen.CALL_ICON -> TripperScreen.Navigation(example)
            SpecialScreen.COMPASS -> TripperScreen.Compass(compass, period)
            SpecialScreen.ROUTE_STARTED -> TripperScreen.RouteStarted(period)
            SpecialScreen.RECALCULATING -> TripperScreen.Recalculating(period)
            SpecialScreen.NATIVE_CLOCK -> TripperScreen.NativeClock
        }
        var preview = PreviewMapper.from(screen, period)
        val packet = if (special == SpecialScreen.CALL_ICON) {
            preview = preview.copy(callOverlay = true)
            TripperCommands.callIcon()
        } else {
            screen.toPacket()
        }
        return preview to packet
    }

    fun setFilter(f: ConfidenceFilter) = raw.update { it.copy(filter = f) }
    fun select(code: Int) = raw.update { it.copy(selectedCode = code, special = SpecialScreen.NONE) }
    fun setSlot(slot: IconSlot) = raw.update { it.copy(slot = slot, special = SpecialScreen.NONE) }
    fun setPeriod(p: DayPeriod) = raw.update { it.copy(period = p) }
    fun setSpecial(s: SpecialScreen) = raw.update { it.copy(special = s) }
    fun setCompass(d: CompassDirection) = raw.update { it.copy(compass = d, special = SpecialScreen.COMPASS) }

    fun addStep() = raw.update {
        if (it.steps.size >= SymbolSequence.MAX_STEPS) it
        else it.copy(steps = it.steps + SequenceStep(it.selectedCode, 1_000, it.slot))
    }

    fun setStepDuration(index: Int, ms: Long) = raw.update { r ->
        r.copy(steps = r.steps.mapIndexed { i, s -> if (i == index) s.copy(durationMs = ms) else s })
    }

    fun removeStep(index: Int) {
        stop()
        raw.update { r -> r.copy(steps = r.steps.filterIndexed { i, _ -> i != index }) }
    }

    fun clearSteps() {
        stop()
        raw.update { it.copy(steps = emptyList()) }
    }

    /** Toca a sequência só na prévia do app, em loop. */
    fun play() {
        val seq = SymbolSequence("Playground", raw.value.steps)
        if (!seq.isValid) return
        playback?.cancel()
        playback = viewModelScope.launch {
            var elapsed = 0L
            while (isActive) {
                raw.update { it.copy(playingIndex = seq.stepIndexAt(elapsed, loop = true)) }
                delay(100)
                elapsed += 100
            }
        }
    }

    fun stop() {
        playback?.cancel()
        playback = null
        raw.update { it.copy(playingIndex = null) }
    }
}
