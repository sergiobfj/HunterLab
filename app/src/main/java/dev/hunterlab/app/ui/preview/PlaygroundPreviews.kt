package dev.hunterlab.app.ui.preview

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.hunterlab.app.ui.playground.*
import dev.hunterlab.app.ui.theme.HunterLabTheme
import dev.hunterlab.core.link.*
import dev.hunterlab.core.playground.*
import dev.hunterlab.protocol.DayPeriod

/** Ações vazias somente nestas prévias. Nunca usadas no fluxo real. */
private object PlaygroundPreviewActions : PlaygroundActions {
    override fun select(id: String) = Unit
    override fun category(value: PlaygroundCategory) = Unit
    override fun evidence(value: VisualEvidence?) = Unit
    override fun slot(value: IconSlot) = Unit
    override fun period(value: DayPeriod) = Unit
    override fun duration(value: Long) = Unit
    override fun arm(ack: TransmissionAck) = Unit
    override fun scan() = Unit
    override fun connect() = Unit
    override fun requestPin() = Unit
    override fun submitPin(value: String) = Unit
    override fun confirmPin() = Unit
    override fun rejectPin() = Unit
    override fun show() = Unit
    override fun stopPhysical() = Unit
    override fun leave() = Unit
    override fun addStage() = Unit
    override fun removeStage(index: Int) = Unit
    override fun moveStage(index: Int, offset: Int) = Unit
    override fun stageDuration(index: Int, ms: Long) = Unit
    override fun preset(name: String) = Unit
    override fun play() = Unit
    override fun pause() = Unit
    override fun restart() = Unit
    override fun stopPreview(reason: ExperimentEnd) = Unit
    override fun observe(index: Int, value: VisualObservation) = Unit
    override fun clock(index: Int, value: ClockRecovery) = Unit
    override fun clearHistory() = Unit
    override fun report(): String = "Prévia de relatório simulado"
}
private val physical = PlaygroundUiState(
    physicalEnabled = true,
    link = LinkSnapshot(state = LinkState.AUTHENTICATED, purpose = SessionPurpose.PLAYGROUND, transmissionAuthorized = true, authCommandDelivered = true),
)
@Composable private fun PlaygroundPreview(state: PlaygroundUiState, tab: PlaygroundTab = PlaygroundTab.CATALOG) {
    HunterLabTheme { Surface(Modifier.fillMaxSize()) { PlaygroundContent(state, PlaygroundPreviewActions, initialTab = tab) } }
}
@Preview(name = "Playground · catálogo", widthDp = 360, heightDp = 800, locale = "pt-rBR")
@Composable fun PlaygroundCatalogPreview() = PlaygroundPreview(PlaygroundUiState())
@Preview(name = "Playground · sessão sintética", widthDp = 360, heightDp = 800, locale = "pt-rBR")
@Composable fun PlaygroundPhysicalPreview() = PlaygroundPreview(physical)
@Preview(name = "Playground · PIN vazio", widthDp = 360, heightDp = 800, locale = "pt-rBR")
@Composable fun PlaygroundPinPreview() = PlaygroundPreview(physical.copy(link = physical.link.copy(state = LinkState.WAITING_FOR_PIN)))
@Preview(name = "Playground · efeitos simulados", widthDp = 360, heightDp = 800, locale = "pt-rBR")
@Composable fun PlaygroundEffectsPreview() = PlaygroundPreview(PlaygroundUiState(sequence = PreviewSequence.presets.values.first()), PlaygroundTab.EFFECTS)
@Preview(name = "Playground · histórico fictício", widthDp = 360, heightDp = 800, locale = "pt-rBR")
@Composable fun PlaygroundHistoryPreview() = PlaygroundPreview(PlaygroundUiState(records = listOf(
    PlaygroundExperiment(1_795_262_400_000, listOf("compass-N"), ExperimentKind.SIMULATED, DayPeriod.NIGHT, 5_000, null, null, ExperimentEnd.DURATION),
)), PlaygroundTab.HISTORY)
@Preview(name = "Playground · fonte 150%", widthDp = 320, heightDp = 640, fontScale = 1.5f, locale = "pt-rBR")
@Composable fun PlaygroundLargeFontPreview() = PlaygroundPreview(physical)
@Preview(name = "Playground · amplo", widthDp = 840, heightDp = 1000, locale = "pt-rBR")
@Composable fun PlaygroundWidePreview() = PlaygroundPreview(PlaygroundUiState())
