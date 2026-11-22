package dev.hunterlab.app.ui.playground

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.hunterlab.app.ble.BleController
import dev.hunterlab.app.ble.lab.HandlerLinkScheduler
import dev.hunterlab.app.settings.AppPreferences
import dev.hunterlab.app.ui.contract.NavBindings
import dev.hunterlab.core.ble.ConnectionPhase
import dev.hunterlab.core.link.*
import dev.hunterlab.core.playground.*
import dev.hunterlab.protocol.DayPeriod
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlaygroundUiState(
    val selection: PlaygroundSelection = PlaygroundSelection(),
    val category: PlaygroundCategory = PlaygroundCategory.COMPASS,
    val evidence: VisualEvidence? = null,
    val symbols: List<PlaygroundSymbol> = PlaygroundCatalog.entries.filter { it.category == PlaygroundCategory.COMPASS },
    val sequence: PreviewSequence = PreviewSequence(emptyList()),
    val playback: PlaybackPhase = PlaybackPhase.STOPPED,
    val playingIndex: Int? = null,
    val preview: dev.hunterlab.core.display.TripperPreview = PlaygroundSelection().preview,
    val records: List<PlaygroundExperiment> = emptyList(),
    val physicalEnabled: Boolean = false, val physicalRunning: Boolean = false,
    val durationMs: Long = 10_000, val link: LinkSnapshot = LinkSnapshot(),
    val navigationActive: Boolean = false, val scanning: Boolean = false, val candidates: Int = 0,
    val message: String? = null,
)

/** Porta única da tela: sem pacotes ou transporte novo. Construção e seleção nunca transmitem. */
class PlaygroundViewModel(private val ble: BleController, private val nav: NavBindings, context: Context) : ViewModel(), PlaygroundActions {
    private val prefs = AppPreferences(context)
    private val raw = MutableStateFlow(PlaygroundUiState(records = ExperimentLog.decode(prefs.playgroundHistory)))
    val ui: StateFlow<PlaygroundUiState> = raw.asStateFlow()
    private val player = PreviewPlayer(SystemClock::elapsedRealtime)
    private var playbackJob: Job? = null
    private var scanToken: Long? = null
    private var simulationAt: Long? = null
    private var simulationPeriod = DayPeriod.NIGHT
    private val hardware = PlaygroundController(ble.lab, HandlerLinkScheduler(), { nav.home.value.navigationActive }, System::currentTimeMillis, ::append)

    init {
        viewModelScope.launch { ble.lab.snapshot.collect { hardware.onLinkChanged(it); refresh() } }
        viewModelScope.launch { ble.scanner.state.collect { refresh() } }
        viewModelScope.launch { nav.home.collect { state ->
            if (state.navigationActive) { stopOwnedScan(); hardware.stop(ExperimentEnd.NAVIGATION_PRIORITY); stopPreview(ExperimentEnd.NAVIGATION_PRIORITY) }
            refresh()
        } }
    }

    private fun append(record: PlaygroundExperiment) {
        val records = (raw.value.records + record).takeLast(ExperimentLog.MAX_RECORDS)
        raw.update { it.copy(records = records) }
        runCatching { prefs.playgroundHistory = ExperimentLog.encode(records) }.onFailure {
            raw.update { it.copy(message = "Experimento encerrado. Não foi possível salvar o histórico; resultado mantido nesta sessão.") }
        }
    }
    private fun refresh() {
        val r = raw.value
        val playing = player.index?.let { player.sequence.stages.getOrNull(it) }?.symbolId?.let(PlaygroundCatalog::find)
        val symbols = PlaygroundCatalog.entries.filter { it.category == r.category && (r.evidence == null || it.evidence(r.selection.period) == r.evidence) }
        raw.value = r.copy(
            symbols = symbols, preview = playing?.preview(r.selection.period) ?: r.selection.preview,
            playback = player.phase, playingIndex = player.index,
            physicalEnabled = hardware.enabled, physicalRunning = hardware.running, link = ble.lab.snapshot.value,
            navigationActive = nav.home.value.navigationActive, scanning = ble.scanner.state.value.scanning,
            candidates = PlaygroundController.strongCandidates(ble.scanner.state.value.devices.values).size,
        )
    }
    private fun handle(result: CommandResult) { raw.update { it.copy(message = (result as? CommandResult.Rejected)?.reason) }; refresh() }
    override fun select(id: String) { if (PlaygroundCatalog.find(id) == null) return; stopPreview(); raw.update { it.copy(selection = it.selection.select(id)) }; refresh() }
    override fun category(value: PlaygroundCategory) { raw.update { it.copy(category = value) }; refresh() }
    override fun evidence(value: VisualEvidence?) { raw.update { it.copy(evidence = value) }; refresh() }
    override fun slot(value: IconSlot) { stopPreview(); raw.update { it.copy(selection = it.selection.copy(slot = value)) }; refresh() }
    override fun period(value: DayPeriod) { stopPreview(); raw.update { it.copy(selection = it.selection.copy(period = value)) }; refresh() }
    override fun duration(value: Long) { if (value in PlaygroundController.MIN_TEST_MS..PlaygroundController.MAX_TEST_MS) raw.update { it.copy(durationMs = value) } }
    override fun arm(ack: TransmissionAck) = handle(hardware.arm(ack))

    override fun scan() {
        if (!hardware.enabled || nav.home.value.navigationActive || ble.lab.snapshot.value.active || ble.scanner.state.value.scanning ||
            ble.inspector.state.value.phase !in setOf(ConnectionPhase.IDLE, ConnectionPhase.DISCONNECTED, ConnectionPhase.FAILED)) {
            handle(CommandResult.Rejected("Bluetooth ocupado ou modo físico bloqueado. Encerre a outra ferramenta primeiro.")); return
        }
        ble.refresh()
        if (!ble.readiness.value.ready) { handle(CommandResult.Rejected("Permita dispositivos por perto e ligue o Bluetooth em Configurações.")); return }
        ble.scanner.clear()
        val error = ble.scanner.start(10_000)
        scanToken = if (error == null) ble.scanner.state.value.startedAtMs else null
        raw.update { it.copy(message = error?.detail) }; refresh()
    }
    private fun stopOwnedScan() {
        val token = scanToken
        scanToken = null
        if (token != null && ble.scanner.state.value.startedAtMs == token && ble.scanner.state.value.scanning) ble.scanner.stop()
    }
    override fun connect() {
        if (scanToken == null || scanToken != ble.scanner.state.value.startedAtMs || ble.inspector.state.value.phase !in setOf(ConnectionPhase.IDLE, ConnectionPhase.DISCONNECTED, ConnectionPhase.FAILED)) {
            handle(CommandResult.Rejected("Faça uma busca própria do Playground e feche a inspeção BLE.")); return
        }
        if (ble.scanner.state.value.scanning) { handle(CommandResult.Rejected("Aguarde o fim da busca.")); return }
        if (!ble.readiness.value.ready || nav.home.value.navigationActive || ble.lab.snapshot.value.active) { handle(CommandResult.Rejected("Conexão indisponível.")); return }
        val candidates = ble.scanner.state.value.devices.values.toList()
        handle(hardware.connect(candidates, System.currentTimeMillis()))
    }
    override fun requestPin() = handle(hardware.requestPin())
    override fun submitPin(value: String) = handle(hardware.submitPin(value))
    override fun confirmPin() = handle(hardware.confirmPin())
    override fun rejectPin() = handle(hardware.rejectPin())
    override fun show() {
        if (player.phase == PlaybackPhase.PLAYING || player.phase == PlaybackPhase.PAUSED) { handle(CommandResult.Rejected("Sequências são somente simuladas. Interrompa a prévia primeiro.")); return }
        val s = raw.value
        handle(hardware.show(s.selection.symbolId, s.selection.period, s.durationMs))
    }
    override fun stopPhysical() { hardware.stop(); refresh() }
    override fun leave() { stopOwnedScan(); stopPreview(ExperimentEnd.LEFT_SCREEN); hardware.stop(ExperimentEnd.LEFT_SCREEN); refresh() }

    private fun changeSequence(stages: List<PreviewStage>) {
        stopPreview()
        if (stages.isNotEmpty() && !PreviewSequence(stages).valid) { raw.update { it.copy(message = "Até 8 etapas, de 2 a 10 segundos; máximo total de 45 segundos.") }; return }
        raw.update { it.copy(sequence = PreviewSequence(stages), message = null) }; refresh()
    }
    override fun addStage() = changeSequence(raw.value.sequence.stages + PreviewStage(raw.value.selection.symbolId))
    override fun removeStage(index: Int) = changeSequence(raw.value.sequence.stages.filterIndexed { i, _ -> i != index })
    override fun moveStage(index: Int, offset: Int) {
        val stages = raw.value.sequence.stages.toMutableList(); val target = index + offset
        if (index !in stages.indices || target !in stages.indices) return
        java.util.Collections.swap(stages, index, target); changeSequence(stages)
    }
    override fun stageDuration(index: Int, ms: Long) = changeSequence(raw.value.sequence.stages.mapIndexed { i, s -> if (i == index) s.copy(durationMs = ms) else s })
    override fun preset(name: String) { PreviewSequence.presets[name]?.let { changeSequence(it.stages) } }
    override fun play() {
        if (hardware.running || !raw.value.sequence.valid) return
        if (!player.play(raw.value.sequence)) return
        if (simulationAt == null) { simulationAt = System.currentTimeMillis(); simulationPeriod = raw.value.selection.period }
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            while (isActive && player.phase == PlaybackPhase.PLAYING) {
                player.tick(); refresh()
                if (player.phase == PlaybackPhase.FINISHED) { saveSimulation(ExperimentEnd.DURATION); break }
                delay(100)
            }
        }
        refresh()
    }
    override fun pause() { player.pause(); playbackJob?.cancel(); refresh() }
    override fun restart() { stopPreview(); play() }
    override fun stopPreview(reason: ExperimentEnd) { saveSimulation(reason); playbackJob?.cancel(); playbackJob = null; player.stop(); refresh() }
    private fun saveSimulation(reason: ExperimentEnd) {
        val at = simulationAt ?: return
        simulationAt = null
        append(PlaygroundExperiment(at, player.sequence.stages.map { it.symbolId }, ExperimentKind.SIMULATED, simulationPeriod, player.elapsedMs, null, null, reason))
    }
    override fun observe(index: Int, value: VisualObservation) { updateRecord(index) { it.copy(observation = value) } }
    override fun clock(index: Int, value: ClockRecovery) { updateRecord(index) { it.copy(clockRecovery = value) } }
    private fun updateRecord(index: Int, update: (PlaygroundExperiment) -> PlaygroundExperiment) {
        val records = raw.value.records.mapIndexed { i, r -> if (i == index) update(r) else r }
        prefs.playgroundHistory = ExperimentLog.encode(records); raw.update { it.copy(records = records) }
    }
    override fun clearHistory() { prefs.playgroundHistory = "[]"; raw.update { it.copy(records = emptyList()) } }
    override fun report(): String = ExperimentLog.report(raw.value.records)
    override fun onCleared() { leave() }
}
