package dev.hunterlab.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
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
                state.radar?.takeIf { it.alertText != null }?.let { RadarAlert(it) }
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
            state.voiceStatusText?.let { SupportingText(it) }
            state.roadLimitKmh?.let { SupportingText("Trecho: $it km/h · GraphHopper / perfil car. Não é o limite do radar.") }
            state.radar?.let { radar ->
                if (!navigationActive) radar.takeIf { it.alertText != null }?.let { RadarAlert(it) }
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

/** A mapped value, never a tolerance or an inferred adjacent-road limit. */
@Composable
fun RadarAlert(state: RadarUi, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val width = with(density) { LocalWindowInfo.current.containerSize.width.toDp() }
    val reflowLimitLabel = width < 360.dp && density.fontScale > 1.2f
    Column(modifier.fillMaxWidth().background(HunterColors.SurfaceHigh, RoundedCornerShape(14.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SpeedLimitSign(state.limitKmh)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(state.kindText, style = MaterialTheme.typography.titleMedium, color = HunterColors.AmberSoft)
                Text(state.alertDistanceM?.let { "$it m" } ?: "À frente", style = MaterialTheme.typography.headlineMedium)
                if (!reflowLimitLabel) Text(state.limitStatus, style = MaterialTheme.typography.bodyMedium, color = HunterColors.TextSecondary)
            }
        }
        if (reflowLimitLabel) Text(state.limitStatus, style = MaterialTheme.typography.bodyMedium, color = HunterColors.TextSecondary)
        if (state.directionUnknown) SupportingText("Sentido de fiscalização não confirmado")
        SupportingText(state.sourceText)
    }
}

@Composable
fun SpeedLimitSign(kmh: Int?, modifier: Modifier = Modifier) {
    // Stable size at large font scales; surrounding labels remain scalable and may wrap.
    val label = kmh?.let { "Limite cadastrado: $it quilômetros por hora" } ?: "Limite não confirmado"
    Box(modifier.size(80.dp).semantics(mergeDescendants = true) { contentDescription = label }
        .background(androidx.compose.ui.graphics.Color.White, androidx.compose.foundation.shape.CircleShape)
        .then(if (kmh != null) Modifier.border(5.dp, androidx.compose.ui.graphics.Color(0xFFB63830), androidx.compose.foundation.shape.CircleShape)
              else Modifier.border(2.dp, HunterColors.TextSecondary, androidx.compose.foundation.shape.CircleShape)),
        contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(kmh?.toString() ?: "—", color = androidx.compose.ui.graphics.Color(0xFF101416),
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = (30f / androidx.compose.ui.platform.LocalDensity.current.fontScale.coerceAtLeast(1f)).sp, fontWeight = FontWeight.SemiBold),
                maxLines = 1)
            if (kmh != null) Text("km/h", color = androidx.compose.ui.graphics.Color(0xFF101416),
                style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        SupportingText(label)
    }
}
