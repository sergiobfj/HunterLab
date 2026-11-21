package dev.hunterlab.app.ui.simulation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hunterlab.app.ui.components.ChoiceRow
import dev.hunterlab.app.ui.components.CollapsibleCard
import dev.hunterlab.app.ui.components.ConfidenceBadge
import dev.hunterlab.app.ui.components.HunterCard
import dev.hunterlab.app.ui.components.KeyValue
import dev.hunterlab.app.ui.components.LabeledSwitch
import dev.hunterlab.app.ui.components.PacketInspector
import dev.hunterlab.app.ui.components.Pill
import dev.hunterlab.app.ui.components.SimulationBanner
import dev.hunterlab.app.ui.theme.HunterColors
import dev.hunterlab.app.ui.theme.HunterIcons
import dev.hunterlab.app.ui.theme.MonoDigits
import dev.hunterlab.core.display.BottomLineMode
import dev.hunterlab.core.display.DayNightMode
import dev.hunterlab.core.nav.NavigationStatus
import dev.hunterlab.core.nav.ProviderAvailability
import dev.hunterlab.protocol.Confidence
import dev.hunterlab.protocol.DistanceEncoding
import dev.hunterlab.protocol.DistanceUnit
import dev.hunterlab.protocol.Hex
import dev.hunterlab.protocol.Maneuver
import dev.hunterlab.protocol.ManeuverMapper
import dev.hunterlab.protocol.Side
import dev.hunterlab.protocol.TurnSharpness
import java.time.format.DateTimeFormatter
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

private val PRIMARY: List<Pair<String, Maneuver>> = listOf(
    "Em frente" to Maneuver.Straight,
    "Esquerda" to Maneuver.Turn(Side.LEFT),
    "Direita" to Maneuver.Turn(Side.RIGHT),
    "Retorno" to Maneuver.UTurn,
    "Chegada" to Maneuver.Arrive(),
)

private val MORE: List<Pair<String, Maneuver>> = listOf(
    "Leve à esq." to Maneuver.Turn(Side.LEFT, TurnSharpness.SLIGHT),
    "Leve à dir." to Maneuver.Turn(Side.RIGHT, TurnSharpness.SLIGHT),
    "Acentuada à esq." to Maneuver.Turn(Side.LEFT, TurnSharpness.SHARP),
    "Acentuada à dir." to Maneuver.Turn(Side.RIGHT, TurnSharpness.SHARP),
    "Manter à esq." to Maneuver.Keep(Side.LEFT),
    "Manter à dir." to Maneuver.Keep(Side.RIGHT),
    "Saída à esq." to Maneuver.Exit(Side.LEFT),
    "Saída à dir." to Maneuver.Exit(Side.RIGHT),
    "Destino à esq." to Maneuver.Arrive(Side.LEFT),
    "Destino à dir." to Maneuver.Arrive(Side.RIGHT),
    "Convergir" to Maneuver.Merge(),
    "Bifurcação esq." to Maneuver.Fork(Side.LEFT),
    "Bifurcação dir." to Maneuver.Fork(Side.RIGHT),
)

private val NEXT: List<Maneuver?> = listOf(
    null, Maneuver.Straight, Maneuver.Turn(Side.LEFT), Maneuver.Turn(Side.RIGHT), Maneuver.UTurn,
    Maneuver.Roundabout(2), Maneuver.Keep(Side.LEFT), Maneuver.Keep(Side.RIGHT), Maneuver.Arrive(),
)

private val QUICK_DISTANCES = listOf(30, 150, 500, 999, 1_000, 2_500, 12_000)
private val HHMM = DateTimeFormatter.ofPattern("HH:mm")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NavigationSimScreen(vm: SimulationViewModel, contentPadding: PaddingValues) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val nav = ui.nav
    var showMore by rememberSaveable { mutableStateOf(false) }

    SimulatorLayout(
        contentPadding = contentPadding,
        header = { compact -> FramePreviewHeader(ui.frame, nav.status, ui.now, compact) },
    ) {
        item { SimulationBanner("Nenhum dado é enviado ao Tripper. Sem GPS e sem Waze.") }

        item {
            HunterCard(title = "Rota", subtitle = "Telas transitórias: rota iniciada ~2 s, recalculando ~3 s, chegada ~5 s") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton("Iniciar rota", HunterIcons.Route, primary = true) { vm.startRoute() }
                    ActionButton("Recalcular", HunterIcons.Reset, enabled = nav.status != NavigationStatus.IDLE) { vm.recalculate() }
                    ActionButton("Concluir chegada", HunterIcons.Check, enabled = nav.status != NavigationStatus.IDLE) { vm.arrive() }
                    ActionButton("Encerrar", HunterIcons.Stop, enabled = nav.status != NavigationStatus.IDLE) { vm.stopRoute() }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Rota demonstrativa (fictícia)", style = MaterialTheme.typography.bodyMedium, color = HunterColors.TextPrimary, modifier = Modifier.weight(1f))
                    if (ui.demo.running) {
                        TextButton(onClick = vm::stopRoute) { Text("Parar") }
                    } else {
                        TextButton(onClick = vm::startDemo) { Text("Tocar") }
                    }
                }
                ChoiceRow(listOf(1.0, 5.0, 20.0), ui.demo.timeScale, { "×${it.toInt()}" }, vm::setDemoTimeScale)
                if (ui.demo.running) {
                    LinearProgressIndicator(
                        progress = { ui.demo.progress.toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                        color = HunterColors.Amber,
                        trackColor = HunterColors.SurfaceHighest,
                    )
                }
            }
        }

        item {
            val current = nav.maneuver ?: Maneuver.Straight
            val icons = ManeuverMapper.map(current, ui.settings.trafficSide)
            HunterCard(title = "Manobra atual", subtitle = current.label) {
                ChoiceRow(PRIMARY.map { it.second }, current, { m -> PRIMARY.first { it.second == m }.first }, vm::setManeuver)
                TextButton(onClick = { showMore = !showMore }) { Text(if (showMore) "Menos manobras" else "Mais manobras") }
                if (showMore) {
                    ChoiceRow(MORE.map { it.second }, current, { m -> MORE.first { it.second == m }.first }, vm::setManeuver)
                    Text("Rotatória — saída", style = MaterialTheme.typography.bodySmall, color = HunterColors.TextSecondary)
                    ChoiceRow((1..9).toList(), (current as? Maneuver.Roundabout)?.exit, { "${it}ª" }, { vm.setManeuver(Maneuver.Roundabout(it)) })
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill("Ícone [2] 0x${Hex.byte(icons.large)}", HunterColors.Amber)
                    Pill("Seta [7] 0x${Hex.byte(icons.small)}", HunterColors.AmberSoft)
                    ConfidenceBadge(icons.confidence)
                }
            }
        }

        item {
            val meters = nav.distanceToManeuverM ?: 0
            val enc = DistanceEncoding.encode(meters)
            HunterCard(title = "Distância até a manobra", subtitle = enc.format()) {
                HunterSlider(metersToSlider(meters)) { vm.setDistance(sliderToMeters(it)) }
                ChoiceRow(QUICK_DISTANCES, meters, { DistanceEncoding.encode(it).format().replace(",0 km", " km") }, vm::setDistance)
                Text(
                    "Bytes [3–5] = ${Hex.byte(enc.high)} ${Hex.byte(enc.low)} ${Hex.byte(enc.unit.code)} · " +
                        if (enc.unit == DistanceUnit.METERS) "metros (até 999 m)" else "km × 10, truncado (${meters} m → ${enc.representedMeters} m)",
                    style = MonoDigits,
                    color = HunterColors.TextSecondary,
                )
            }
        }

        item {
            HunterCard(title = "Próxima manobra", subtitle = "Seta pequena, byte [7]") {
                ChoiceRow(NEXT, nav.nextManeuver, { it?.label ?: "Nenhuma" }, vm::setNextManeuver)
            }
        }

        item {
            HunterCard(title = "Rodapé", subtitle = "Bytes [11–13]") {
                ChoiceRow(BottomLineMode.entries, ui.settings.bottomLineMode, { it.label }, vm::setBottomLine)
                val total = nav.remainingDistanceM ?: 0
                Text("Distância total restante: ${DistanceEncoding.encode(total).format()}", style = MaterialTheme.typography.bodySmall, color = HunterColors.TextSecondary)
                HunterSlider(total / 50_000f) { vm.setRemainingDistance(((it * 50_000) / 100).roundToInt() * 100) }
                val minutes = nav.remainingTimeMin ?: 0
                Text("Tempo restante: ${minutes / 60}h${(minutes % 60).toString().padStart(2, '0')}", style = MaterialTheme.typography.bodySmall, color = HunterColors.TextSecondary)
                HunterSlider(minutes / 300f) { vm.setRemainingTime((it * 300).roundToInt()) }
            }
        }

        item {
            HunterCard(title = "Período diurno/noturno", subtitle = "Bit 0 do byte [6] · confirmado") {
                ChoiceRow(
                    DayNightMode.entries, ui.settings.dayNightMode, { it.label }, vm::setDayNight,
                    leading = { mode: DayNightMode -> dayNightIcon(mode) },
                )
                val sched = ui.settings.schedule
                KeyValue("Relógio do celular", "${ui.now.format(HHMM)} → ${ui.frame.period.label}")
                KeyValue("Automático", "noite das ${sched.nightStarts.format(HHMM)} às ${sched.dayStarts.format(HHMM)}")
                LabeledSwitch(
                    "Intensidade por distância",
                    "Nibble alto do byte [6] (0x50 a ≤10 m … 0x00 acima de 95 m). Efeito visual não documentado.",
                    ui.settings.intensityByDistance,
                    vm::setIntensity,
                    badge = { ConfidenceBadge(Confidence.LIKELY) },
                )
            }
        }

        item {
            CollapsibleCard(title = "Fonte de navegação", summary = "Simulador ativo · provedor futuro indisponível") {
                ui.providers.forEach { p ->
                    val available = p.availability is ProviderAvailability.Available
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p.displayName, style = MaterialTheme.typography.bodyMedium, color = if (available) HunterColors.TextPrimary else HunterColors.TextMuted, modifier = Modifier.weight(1f))
                        Pill(if (available) "Ativo" else "Indisponível", if (available) HunterColors.Confirmed else HunterColors.TextMuted)
                    }
                    (p.availability as? ProviderAvailability.Unavailable)?.let {
                        Text(it.reason, style = MaterialTheme.typography.bodySmall, color = HunterColors.TextMuted)
                    }
                }
            }
        }

        item { CollapsibleCard(title = "Observações", summary = "${ui.frame.notes.size} nota(s) do árbitro de tela") { FrameNotes(ui.frame); if (ui.frame.notes.isEmpty()) Text("Nenhuma.", style = MaterialTheme.typography.bodySmall, color = HunterColors.TextMuted) } }
        item { PacketInspector(ui.frame.packet) }
    }
}

@Composable
fun ActionButton(label: String, icon: ImageVector, primary: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val content: @Composable () -> Unit = {
        Icon(icon, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
    if (primary) Button(onClick = onClick, enabled = enabled) { content() }
    else OutlinedButton(onClick = onClick, enabled = enabled) { content() }
}

fun dayNightIcon(mode: DayNightMode): ImageVector = when (mode) {
    DayNightMode.DAY -> HunterIcons.Sun
    DayNightMode.NIGHT -> HunterIcons.Moon
    DayNightMode.AUTO -> HunterIcons.Auto
}

@Composable
fun sliderColors() = SliderDefaults.colors(
    thumbColor = HunterColors.Amber,
    activeTrackColor = HunterColors.Amber,
    inactiveTrackColor = HunterColors.SurfaceHighest,
)

/**
 * Slider controlado pelo estado externo (o valor também muda por atalhos e pela rota demo).
 * O Material3 1.5 marca esta sobrecarga como obsoleta em favor de SliderState; ela segue funcional
 * e fica isolada aqui para migrar num ponto só.
 */
@Suppress("DEPRECATION")
@Composable
fun HunterSlider(value: Float, onValueChange: (Float) -> Unit) {
    Slider(value = value.coerceIn(0f, 1f), onValueChange = onValueChange, colors = sliderColors())
}

/** Escala logarítmica: 10 m a 20 km no mesmo controle. */
private fun sliderToMeters(v: Float): Int {
    if (v <= 0.001f) return 0
    val m = 10.0 * 2_000.0.pow(v.toDouble())
    return if (m < 1_000) ((m / 5).roundToInt() * 5) else ((m / 100).roundToInt() * 100)
}

private fun metersToSlider(m: Int): Float = if (m <= 10) 0f else (ln(m / 10.0) / ln(2_000.0)).toFloat().coerceIn(0f, 1f)
