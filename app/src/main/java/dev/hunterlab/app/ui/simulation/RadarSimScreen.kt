package dev.hunterlab.app.ui.simulation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hunterlab.app.ui.components.BulletText
import dev.hunterlab.app.ui.components.ChoiceRow
import dev.hunterlab.app.ui.components.CollapsibleCard
import dev.hunterlab.app.ui.components.ConfidenceBadge
import dev.hunterlab.app.ui.components.Dot
import dev.hunterlab.app.ui.components.HunterCard
import dev.hunterlab.app.ui.components.KeyValue
import dev.hunterlab.app.ui.components.LabeledSwitch
import dev.hunterlab.app.ui.components.PacketInspector
import dev.hunterlab.app.ui.components.Pill
import dev.hunterlab.app.ui.components.SimulationBanner
import dev.hunterlab.app.ui.theme.HunterColors
import dev.hunterlab.app.ui.theme.HunterIcons
import dev.hunterlab.app.ui.theme.MonoDigits
import dev.hunterlab.core.nav.NavigationStatus
import dev.hunterlab.core.radar.RadarPhase
import dev.hunterlab.core.radar.SimulatedRadar
import dev.hunterlab.protocol.RadarDisplayMode

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RadarSimScreen(vm: SimulationViewModel, contentPadding: PaddingValues) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val r = ui.radar

    SimulatorLayout(
        contentPadding = contentPadding,
        header = { compact ->
            FramePreviewHeader(ui.frame, ui.nav.status, ui.now, compact) { Pill(r.phase.label, phaseColor(r.phase)) }
        },
    ) {
        item { SimulationBanner("Radares fictícios (SIM-xx). Sem GPS, sem base de radares reais.") }

        if (ui.nav.status == NavigationStatus.IDLE) {
            item {
                HunterCard(title = "Navegação inativa", subtitle = "Sem rota, o alerta aparece só no app: não há tela documentada para sobrepor.") {
                    ActionButton("Iniciar navegação simulada", HunterIcons.Navigation, primary = true) { vm.ensureNavigating() }
                }
            }
        }

        item {
            HunterCard(title = "Percurso simulado", subtitle = "${formatKm(r.positionM)} de ${formatKm(r.routeLengthM.toDouble())} · toque na barra para posicionar") {
                RouteTrack(r.positionM, r.routeLengthM, r.radars, r.config.searchRadiusM, r.config.alertRadiusM) { vm.radarSeek(it) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (r.running) ActionButton("Pausar", HunterIcons.Pause, primary = true) { vm.radarPause() }
                    else ActionButton("Rodar", HunterIcons.Play, primary = true) { vm.radarPlay() }
                    ActionButton("Reiniciar", HunterIcons.Reset) { vm.radarReset() }
                }
                Text("Velocidade simulada", style = MaterialTheme.typography.bodySmall, color = HunterColors.TextSecondary)
                ChoiceRow(listOf(40, 60, 80, 100), r.speedKmh, { "$it km/h" }, vm::radarSpeed)
                Text("Aceleração do tempo", style = MaterialTheme.typography.bodySmall, color = HunterColors.TextSecondary)
                ChoiceRow(listOf(1.0, 2.0, 4.0, 8.0), r.timeScale, { "×${it.toInt()}" }, vm::radarTimeScale)
            }
        }

        item { RadarStateCard(r.phase, ui.frame.radarOnTripper) }

        item {
            HunterCard(title = "Exibição no Tripper") {
                LabeledSwitch("Mostrar alerta no Tripper", "Somente durante a navegação e dentro de ${r.config.alertRadiusM} m", ui.settings.radarOnTripper, vm::setRadarOnTripper)
                ChoiceRow(RadarDisplayMode.entries, ui.settings.radarMode, { it.label }, vm::setRadarMode)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ConfidenceBadge(ui.settings.radarMode.confidence)
                    Text("Seta pequena [7] → 0x3C", style = MonoDigits, color = HunterColors.TextSecondary)
                }
                Text(ui.settings.radarMode.detail, style = MaterialTheme.typography.bodySmall, color = HunterColors.TextSecondary)
                KeyValue("Busca", "${r.config.searchRadiusM} m (só no app)")
                KeyValue("Alerta", "≤ ${r.config.alertRadiusM} m (histerese ${r.config.alertHysteresisM} m)")
                KeyValue("\"Ultrapassado\" visível por", "${r.config.passedHoldM} m")
            }
        }

        item {
            CollapsibleCard(title = "Prioridade", summary = "O alerta nunca prejudica a navegação essencial") {
                BulletText("1. Recalculando, 2. Rota iniciada, 3. Chegada: o radar é suprimido.")
                BulletText("4. Navegando: o radar ocupa só a seta pequena [7] e o rodapé [11–13] (compatível) ou [8–9] (experimental).")
                BulletText("Ícone da manobra [2], distância [3–5] e período [6] nunca são alterados — verificado por teste automatizado.")
                BulletText("5. Sem rota: relógio nativo do Pod; o radar aparece só no app.")
            }
        }

        item {
            HunterCard(title = "Eventos", subtitle = "Mais recentes primeiro · tempo simulado") {
                if (r.log.isEmpty()) Text("Toque em Rodar para começar.", style = MaterialTheme.typography.bodySmall, color = HunterColors.TextMuted)
                r.log.take(20).forEach { e ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Dot(
                            when (e.kind) {
                                RadarLogKind.DETECTED -> HunterColors.Info
                                RadarLogKind.ALERT -> HunterColors.Amber
                                RadarLogKind.PASSED -> HunterColors.Confirmed
                                RadarLogKind.NORMAL -> HunterColors.TextMuted
                            },
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(clock(e.simTimeMs), style = MonoDigits, color = HunterColors.TextMuted)
                        Spacer(Modifier.width(10.dp))
                        Text(e.text, style = MaterialTheme.typography.bodySmall, color = HunterColors.TextPrimary)
                    }
                }
            }
        }

        item {
            CollapsibleCard(title = "Radares fictícios", summary = "${r.radars.size} radares inventados para simulação") {
                r.radars.forEach { radar ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${radar.id} · ${radar.kind.label}", style = MaterialTheme.typography.bodyMedium, color = HunterColors.TextPrimary)
                            Text("km ${formatKm(radar.positionM.toDouble()).removeSuffix(" km")} · limite fictício ${radar.speedLimitKmh} km/h", style = MaterialTheme.typography.bodySmall, color = HunterColors.TextSecondary)
                        }
                        Pill("Simulado", HunterColors.Amber)
                    }
                }
            }
        }
        item { PacketInspector(ui.frame.packet) }
    }
}

@Composable
private fun RadarStateCard(phase: RadarPhase, onTripper: Boolean) {
    HunterCard(title = "Estado do alerta") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(phase.label, phaseColor(phase))
            Pill(if (onTripper) "No Tripper: sim" else "No Tripper: não", if (onTripper) HunterColors.Amber else HunterColors.TextMuted)
        }
        val (radar, distance) = when (phase) {
            is RadarPhase.Detected -> phase.radar to phase.distanceM
            is RadarPhase.Alert -> phase.radar to phase.distanceM
            is RadarPhase.Passed -> phase.radar to null
            RadarPhase.Clear -> null to null
        }
        if (radar == null) {
            Text("Nenhum radar no raio de busca.", style = MaterialTheme.typography.bodySmall, color = HunterColors.TextSecondary)
        } else {
            KeyValue("Radar", "${radar.id} (fictício)")
            KeyValue("Tipo", radar.kind.label)
            KeyValue("Limite", "${radar.speedLimitKmh} km/h (fictício)")
            distance?.let { KeyValue("Distância aproximada", "$it m", HunterColors.Amber) }
        }
    }
}

@Composable
private fun RouteTrack(position: Double, length: Int, radars: List<SimulatedRadar>, search: Int, alert: Int, onSeek: (Double) -> Unit) {
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .semantics { contentDescription = "Percurso simulado: ${formatKm(position)} de ${formatKm(length.toDouble())}" }
            .pointerInput(length) { detectTapGestures { o -> onSeek(o.x.toDouble() / size.width * length) } },
    ) {
        val y = size.height * 0.55f
        val pad = 8.dp.toPx()
        val w = size.width - 2 * pad
        fun x(m: Double) = pad + (m / length * w).toFloat()
        drawRoundRect(HunterColors.SurfaceHighest, Offset(pad, y - 3.dp.toPx()), Size(w, 6.dp.toPx()), CornerRadius(3.dp.toPx()))
        radars.forEach { r ->
            val rx = x(r.positionM.toDouble())
            drawRect(HunterColors.Info.copy(alpha = 0.18f), Offset(x((r.positionM - search).toDouble().coerceAtLeast(0.0)), y - 9.dp.toPx()), Size(rx - x((r.positionM - search).toDouble().coerceAtLeast(0.0)), 18.dp.toPx()))
            drawRect(HunterColors.Amber.copy(alpha = 0.3f), Offset(x((r.positionM - alert).toDouble().coerceAtLeast(0.0)), y - 9.dp.toPx()), Size(rx - x((r.positionM - alert).toDouble().coerceAtLeast(0.0)), 18.dp.toPx()))
            val tri = Path().apply {
                moveTo(rx, y - 12.dp.toPx())
                lineTo(rx - 6.dp.toPx(), y - 22.dp.toPx())
                lineTo(rx + 6.dp.toPx(), y - 22.dp.toPx())
                close()
            }
            drawPath(tri, HunterColors.Amber)
        }
        drawRoundRect(HunterColors.Amber.copy(alpha = 0.7f), Offset(pad, y - 3.dp.toPx()), Size(x(position) - pad, 6.dp.toPx()), CornerRadius(3.dp.toPx()))
        drawCircle(Color.White, 7.dp.toPx(), Offset(x(position), y))
        drawCircle(HunterColors.Background, 3.dp.toPx(), Offset(x(position), y))
    }
}

private fun phaseColor(p: RadarPhase): Color = when (p) {
    RadarPhase.Clear -> HunterColors.TextSecondary
    is RadarPhase.Detected -> HunterColors.Info
    is RadarPhase.Alert -> HunterColors.Amber
    is RadarPhase.Passed -> HunterColors.Confirmed
}

private fun clock(ms: Long): String {
    val s = ms / 1000
    return "${(s / 60).toString().padStart(2, '0')}:${(s % 60).toString().padStart(2, '0')}"
}
