package dev.hunterlab.app.ui.playground

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hunterlab.app.ui.components.BulletText
import dev.hunterlab.app.ui.components.ChoiceRow
import dev.hunterlab.app.ui.components.CollapsibleCard
import dev.hunterlab.app.ui.components.ConfidenceBadge
import dev.hunterlab.app.ui.components.Dot
import dev.hunterlab.app.ui.components.HunterCard
import dev.hunterlab.app.ui.components.MiniHex
import dev.hunterlab.app.ui.components.PacketInspector
import dev.hunterlab.app.ui.components.Pill
import dev.hunterlab.app.ui.simulation.ActionButton
import dev.hunterlab.app.ui.simulation.SimulatorLayout
import dev.hunterlab.app.ui.theme.HunterColors
import dev.hunterlab.app.ui.theme.HunterIcons
import dev.hunterlab.app.ui.theme.MonoDigits
import dev.hunterlab.app.ui.theme.color
import dev.hunterlab.app.ui.tripper.IconGlyph
import dev.hunterlab.app.ui.tripper.TripperDisplay
import dev.hunterlab.core.playground.HardwareExecutionPolicy
import dev.hunterlab.core.playground.IconSlot
import dev.hunterlab.protocol.CommandCatalog
import dev.hunterlab.protocol.CompassDirection
import dev.hunterlab.protocol.DayPeriod
import dev.hunterlab.protocol.TripperIcon
import dev.hunterlab.protocol.TripperIcons
import java.time.LocalTime

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlaygroundScreen(vm: PlaygroundViewModel, contentPadding: PaddingValues) {
    val ui by vm.ui.collectAsStateWithLifecycle()

    SimulatorLayout(
        contentPadding = contentPadding,
        header = { compact ->
            if (compact) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TripperDisplay(ui.preview, LocalTime.now(), Modifier.width(158.dp), showCaption = false)
                    Column(Modifier.padding(start = 14.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SelectedSummary(ui)
                    }
                }
            } else {
                HunterCard {
                    TripperDisplay(ui.preview, LocalTime.now())
                    SelectedSummary(ui)
                }
            }
        },
    ) {
        item {
            HunterCard(title = "Prévia", subtitle = "Somente no app. O catálogo vem da referência (Meteor 350).") {
                ChoiceRow(IconSlot.entries, ui.slot.takeIf { ui.special == SpecialScreen.NONE }, { it.label }, vm::setSlot)
                ChoiceRow(DayPeriod.entries, ui.period, { it.label }, vm::setPeriod, leading = { p: DayPeriod -> if (p == DayPeriod.NIGHT) HunterIcons.Moon else HunterIcons.Sun })
                Text("Telas especiais", style = MaterialTheme.typography.bodySmall, color = HunterColors.TextSecondary)
                ChoiceRow(SpecialScreen.entries.drop(1), ui.special.takeIf { it != SpecialScreen.NONE }, { it.label }, vm::setSpecial)
                if (ui.special == SpecialScreen.COMPASS) {
                    ChoiceRow(CompassDirection.entries, ui.compass, { it.label }, vm::setCompass)
                }
                if (ui.special == SpecialScreen.CALL_ICON) {
                    Text("O Pod desenha o ícone de ligação por conta própria; posição e tamanho reais não são documentados.", style = MaterialTheme.typography.bodySmall, color = HunterColors.TextMuted)
                }
            }
        }

        item {
            HunterCard(title = "Catálogo de símbolos", subtitle = "Toque para pré-visualizar · cores indicam o nível de confiança") {
                ChoiceRow(ConfidenceFilter.entries, ui.filter, { "${it.label} (${ui.counts[it]})" }, vm::setFilter)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ui.icons.forEach { icon ->
                        SymbolCell(icon, selected = icon.code == ui.selected.code && ui.special == SpecialScreen.NONE) { vm.select(icon.code) }
                    }
                }
            }
        }

        item {
            HunterCard(
                title = "Sequência de símbolos (experimental)",
                subtitle = "Arquitetura para etapas futuras. Toca apenas na prévia, em loop.",
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(HunterIcons.Lock, null, tint = HunterColors.Amber, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(HardwareExecutionPolicy.REASON, style = MaterialTheme.typography.bodySmall, color = HunterColors.AmberSoft)
                }
                if (ui.steps.isEmpty()) {
                    Text("Selecione um símbolo e toque em \"Adicionar\".", style = MaterialTheme.typography.bodySmall, color = HunterColors.TextMuted)
                }
                ui.steps.forEachIndexed { i, step ->
                    val icon = TripperIcons.describe(step.iconCode)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${i + 1}.", style = MonoDigits, color = if (ui.playingIndex == i) HunterColors.Amber else HunterColors.TextMuted)
                            Spacer(Modifier.width(8.dp))
                            IconGlyph(icon.depiction, HunterColors.TextPrimary, Modifier.size(28.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text("${icon.hex} ${icon.name}", style = MaterialTheme.typography.bodySmall, color = HunterColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(step.slot.label, style = MaterialTheme.typography.labelSmall, color = HunterColors.TextMuted)
                            }
                            IconButton(onClick = { vm.removeStep(i) }) { Icon(HunterIcons.Close, "Remover passo", tint = HunterColors.TextMuted) }
                        }
                        ChoiceRow(listOf(500L, 1_000L, 2_000L), step.durationMs, { "${it / 1000.0}s".replace(".0s", "s") }, { vm.setStepDuration(i, it) })
                    }
                }
                ui.issues.forEach { BulletText(it.message, if (it.blocking) HunterColors.Error else HunterColors.Amber) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton("Adicionar ${ui.selected.hex}", HunterIcons.Plus) { vm.addStep() }
                    if (ui.playing) ActionButton("Parar", HunterIcons.Stop, primary = true) { vm.stop() }
                    else ActionButton("Tocar na prévia", HunterIcons.Play, primary = true, enabled = ui.steps.isNotEmpty() && ui.issues.none { it.blocking }) { vm.play() }
                    if (ui.steps.isNotEmpty()) TextButton(onClick = vm::clearSteps) { Text("Limpar") }
                }
            }
        }

        item { PacketInspector(ui.packet) }

        item {
            CollapsibleCard(title = "Comandos documentados", summary = "Catálogo em memória · a aba Playground nunca transmite") {
                CommandCatalog.all.forEach { c ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(c.name, style = MonoDigits.copy(fontSize = 12.sp), color = HunterColors.TextPrimary, modifier = Modifier.weight(1f))
                            ConfidenceBadge(c.confidence)
                        }
                        Text(c.description, style = MaterialTheme.typography.bodySmall, color = HunterColors.TextSecondary)
                        MiniHex(c.example.toHex())
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectedSummary(ui: PlaygroundUiState) {
    if (ui.special != SpecialScreen.NONE && !ui.playing) {
        Text(ui.special.label, style = MaterialTheme.typography.titleSmall, color = HunterColors.TextPrimary)
        Pill("Não testado na Hunter 350", HunterColors.TextSecondary)
        return
    }
    val icon = ui.playingIndex?.let { TripperIcons.describe(ui.steps[it].iconCode) } ?: ui.selected
    Text("${icon.hex} · ${icon.name}", style = MaterialTheme.typography.titleSmall, color = HunterColors.TextPrimary)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ConfidenceBadge(icon.confidence)
        if (ui.playing) Pill("Sequência", HunterColors.Amber, icon = HunterIcons.Play)
        Pill(icon.hunter.label, HunterColors.TextSecondary)
    }
    icon.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = HunterColors.TextMuted) }
}

@Composable
private fun SymbolCell(icon: TripperIcon, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) HunterColors.AmberContainer else HunterColors.SurfaceHigh,
        border = BorderStroke(1.dp, if (selected) HunterColors.Amber else HunterColors.Outline),
        modifier = Modifier.width(74.dp).semantics { contentDescription = "${icon.hex} ${icon.name}, ${icon.confidence.label}" },
    ) {
        Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(38.dp)) { IconGlyph(icon.depiction, HunterColors.TextPrimary, Modifier.size(38.dp)) }
            Spacer(Modifier.size(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(icon.confidence.color(), 6.dp)
                Spacer(Modifier.width(4.dp))
                Text(icon.hex, style = MonoDigits.copy(fontSize = 11.sp), color = HunterColors.TextSecondary)
            }
        }
    }
}
