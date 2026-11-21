package dev.hunterlab.app.ui.navigation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import dev.hunterlab.app.ui.components.*
import dev.hunterlab.app.ui.contract.*
import dev.hunterlab.app.ui.theme.HunterColors
import dev.hunterlab.app.ui.theme.HunterIcons
import dev.hunterlab.app.ui.tripper.drawDepiction
import dev.hunterlab.protocol.TripperIcons

@Composable
fun NavigationScreen(
    state: NavUi,
    actions: NavActions,
    padding: PaddingValues = PaddingValues(),
    engineAvailable: Boolean = true,
    onHome: () -> Unit = {},
) {
    var ending by remember { mutableStateOf(false) }
    val pairing = state.tripper.status in setOf(TripperUiStatus.CONNECTING, TripperUiStatus.READY_TO_PAIR, TripperUiStatus.WAITING_PIN, TripperUiStatus.CONFIRM_PIN)
    Column(Modifier.fillMaxSize().padding(padding)) {
        ProductPage(modifier = Modifier.weight(1f)) {
            if (state.simulated) StatusLine("Trajeto simulado" to HunterColors.Amber)
            if (!state.journeyOpen && engineAvailable && pairing) {
                PageHeading("SEU DISPLAY", "Conectar ao Tripper")
            } else if (!state.journeyOpen) {
                PageHeading("SEU PRÓXIMO CAMINHO", "O trajeto começa aqui.", "Escolha um destino para acompanhar cada curva.")
                PrimaryAction("Escolher destino", onHome, icon = HunterIcons.Search)
                if (!engineAvailable) InlineNotice("A navegação real estará disponível na próxima versão. Os simuladores estão em Configurações → Avançado.")
            } else Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                state.destination?.let { Text(it.label, style = MaterialTheme.typography.titleMedium, color = HunterColors.TextSecondary) }
                when {
                    state.showCurrentManeuver -> {
                        val maneuver = state.current!!
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            ManeuverGlyph(maneuver, Modifier.size(56.dp))
                            Text(maneuver.distanceText, style = MaterialTheme.typography.displayLarge)
                            Text(maneuver.instruction, style = MaterialTheme.typography.headlineMedium)
                            maneuver.streetName?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = HunterColors.TextSecondary) }
                        }
                    }
                    state.calculating -> {
                        CircularProgressIndicator(Modifier.size(40.dp), strokeWidth = 3.dp)
                        Text(state.phase.label, style = MaterialTheme.typography.headlineMedium)
                    }
                    state.phase == NavPhaseUi.ARRIVED -> {
                        Icon(HunterIcons.Location, null, Modifier.size(64.dp), tint = HunterColors.Amber)
                        Text("Você chegou.", style = MaterialTheme.typography.headlineLarge)
                    }
                    else -> {
                        Icon(HunterIcons.Warning, null, Modifier.size(40.dp), tint = HunterColors.Amber)
                        Text(state.phase.label, style = MaterialTheme.typography.headlineMedium)
                    }
                }
                state.message?.let { InlineNotice(it, warning = state.phase in setOf(NavPhaseUi.FAILED, NavPhaseUi.OFF_ROUTE)) }
                state.radar?.alertText?.let { InlineNotice(it, warning = true) }
                if (state.showJourneyMetrics) {
                    HorizontalDivider(color = HunterColors.Outline)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        state.remainingTimeText?.let { Metric("Restantes", it, Modifier.weight(1f)) }
                        state.remainingDistanceText?.let { Metric("Até o destino", it, Modifier.weight(1f)) }
                        state.arrivalText?.let { Metric("Chegada", it, Modifier.weight(1f)) }
                    }
                }
                StatusLine(
                    (if (state.simulated) "Posição simulada" else state.gps.label) to if (!state.simulated && state.gps == GpsUi.OK) HunterColors.Confirmed else HunterColors.TextSecondary,
                    state.tripper.label to state.tripper.statusColor(),
                )
                if (!state.simulated) state.gpsAccuracyText?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = HunterColors.TextSecondary) }
                if (state.showCurrentManeuver) state.next?.let { next ->
                    ProductPanel {
                        SectionLabel("EM SEGUIDA")
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            ManeuverGlyph(next, Modifier.size(32.dp))
                            Column(Modifier.weight(1f)) {
                                Text(next.instruction, style = MaterialTheme.typography.titleSmall)
                                Text(next.distanceText, style = MaterialTheme.typography.bodyMedium, color = HunterColors.TextSecondary)
                            }
                        }
                    }
                }
                state.radar?.let { radar ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(radar.coverageText, style = MaterialTheme.typography.bodySmall, color = HunterColors.TextSecondary)
                        Text(radar.disclaimer, style = MaterialTheme.typography.bodySmall, color = HunterColors.TextMuted)
                        Text(radar.attribution, style = MaterialTheme.typography.labelSmall, color = HunterColors.TextMuted)
                    }
                }
            }
            if (engineAvailable && state.tripper.status !in setOf(TripperUiStatus.AUTHENTICATED, TripperUiStatus.SHOWING)) {
                // A raiz mantém Interromper visível acima de qualquer aba.
                TripperControls(state.tripper, actions, showInterrupt = false)
            }
        }
        if (state.journeyOpen) {
            Surface(color = HunterColors.Background) {
                OutlinedButton(
                    { ending = true }, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).heightIn(min = 52.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = HunterColors.Error),
                ) {
                    Icon(HunterIcons.Stop, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Encerrar navegação")
                }
            }
        }
    }
    if (ending) AlertDialog(
        onDismissRequest = { ending = false },
        title = { Text("Encerrar este trajeto?") },
        text = { Text("A navegação será encerrada.") },
        confirmButton = { Button({ ending = false; actions.stopNavigation() }) { Text("Encerrar") } },
        dismissButton = { TextButton({ ending = false }) { Text("Continuar trajeto") } },
    )
}

@Composable
private fun ManeuverGlyph(maneuver: ManeuverUi, modifier: Modifier) {
    val textMeasurer = rememberTextMeasurer()
    val depiction = TripperIcons.describe(maneuver.iconCode).depiction
    Canvas(modifier) {
        drawDepiction(depiction, HunterColors.Amber, textMeasurer)
    }
}
