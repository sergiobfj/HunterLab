package dev.hunterlab.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hunterlab.app.ui.components.*
import dev.hunterlab.app.ui.contract.*
import dev.hunterlab.app.ui.theme.HunterColors
import dev.hunterlab.app.ui.tripper.IconGlyph
import dev.hunterlab.app.ui.tripper.TripperDisplay
import dev.hunterlab.protocol.TripperIcons
import java.time.LocalTime

fun NavUi.hasActiveNavigation(): Boolean = phase in setOf(
    NavPhaseUi.ROUTING, NavPhaseUi.NAVIGATING, NavPhaseUi.OFF_ROUTE, NavPhaseUi.REROUTING,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NavigationScreen(
    state: NavUi,
    actions: NavActions,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
    navigationActive: Boolean = state.hasActiveNavigation(),
    showSafetyActions: Boolean = true,
) {
    Column(modifier.fillMaxSize()) {
        DailyScreen(Modifier.weight(1f)) {
            if (state.simulated) Pill("Trajeto simulado", HunterColors.Amber)
            if (!navigationActive) {
                DailyHeading(if (state.phase == NavPhaseUi.ARRIVED) "Você chegou" else "Seu próximo caminho")
                state.destination?.let { Text(it.label, style = MaterialTheme.typography.titleLarge) }
                state.message?.let { SupportingText(it, error = state.phase == NavPhaseUi.FAILED) }
                SupportingText("Escolha um destino no Início para navegar.")
                PrimaryAction("Ir para Início", onHome)
            } else {
                SupportingText(state.phase.label)
                state.current?.takeIf { state.showCurrentManeuver }?.let { current ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        IconGlyph(
                            TripperIcons.describe(current.iconCode).depiction, HunterColors.TextPrimary,
                            Modifier.size(88.dp).semantics { contentDescription = current.instruction },
                        )
                        Text(
                            current.distanceText,
                            style = MaterialTheme.typography.displayLarge.copy(fontSize = 52.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Text(current.instruction, style = MaterialTheme.typography.headlineMedium)
                    current.streetName?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = HunterColors.TextSecondary) }
                } ?: Text(state.message ?: state.phase.label, style = MaterialTheme.typography.headlineMedium)
                state.radar?.alertText?.let { RadarAlert(it) }
                state.next?.takeIf { state.showCurrentManeuver }?.let { next ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconGlyph(
                            TripperIcons.describe(next.iconCode).depiction, HunterColors.TextSecondary,
                            Modifier.size(32.dp).semantics { contentDescription = next.instruction },
                        )
                        Text("Depois · ${next.distanceText} · ${next.instruction}", style = MaterialTheme.typography.bodyMedium, color = HunterColors.TextSecondary)
                    }
                }
                HorizontalDivider()
                if (state.showJourneyMetrics) FlowRow(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    state.remainingTimeText?.let { Metric("Restante", it) }
                    state.arrivalText?.let { Metric("Chegada", it) }
                    state.remainingDistanceText?.let { Metric("Distância", it) }
                }
                if (state.showCurrentManeuver) state.message?.let { SupportingText(it) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(state.gps.label, if (state.gps == GpsUi.OK) HunterColors.TextSecondary else HunterColors.AmberSoft)
                Pill(state.tripper.label, HunterColors.TextSecondary)
            }
            state.gpsAccuracyText?.let { SupportingText(it) }
            state.radar?.let { radar ->
                if (!navigationActive) radar.alertText?.let { RadarAlert(it) }
                SupportingText(if (radar.enabled) radar.coverageText else "Alertas de radar desativados")
                SupportingText(radar.disclaimer)
                SupportingText(radar.attribution)
            }
            HorizontalDivider()
            TripperConnection(state.tripper, actions)
            state.tripperPreview?.let {
                TripperDisplay(it, LocalTime.now(), Modifier.widthIn(max = 180.dp).align(Alignment.CenterHorizontally), showCaption = false)
                SupportingText("Prévia aproximada do Tripper")
            }
        }
        if (showSafetyActions) SessionSafetyActions(navigationActive, state.tripper.canInterrupt, actions)
    }
}

@Composable
private fun RadarAlert(text: String) {
    Text(
        text, style = MaterialTheme.typography.titleLarge, color = HunterColors.AmberSoft,
        modifier = Modifier.fillMaxWidth().background(HunterColors.AmberContainer, RoundedCornerShape(12.dp)).padding(16.dp),
    )
}

@Composable
private fun Metric(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        SupportingText(label)
    }
}
