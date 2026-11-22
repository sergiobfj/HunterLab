package dev.hunterlab.app.ui.personalization

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.hunterlab.app.ui.components.*
import dev.hunterlab.app.ui.contract.NavActions
import dev.hunterlab.app.ui.contract.SettingsUi
import dev.hunterlab.app.ui.tripper.TripperDisplay
import dev.hunterlab.core.display.*
import dev.hunterlab.protocol.TripperIcons
import java.time.LocalTime

@Composable
fun PersonalizationScreen(state: SettingsUi, actions: NavActions, modifier: Modifier = Modifier, preview: TripperPreview? = null, onPlayground: () -> Unit = {}) {
    val example = preview ?: TripperPreview(
        kind = PreviewKind.NAVIGATION, night = false,
        largeIcon = TripperIcons.describe(TripperIcons.STRAIGHT), distanceText = "300 m",
    )
    val night = when (state.dayNight) {
        DayNightMode.DAY -> false
        DayNightMode.NIGHT -> true
        DayNightMode.AUTO -> example.night
    }
    val bottom = when (state.bottomLine) {
        BottomLineMode.TOTAL_DISTANCE -> "12,4 km"
        BottomLineMode.TIME_REMAINING -> "0h18"
        BottomLineMode.ARRIVAL_24H -> "14:32"
        BottomLineMode.ARRIVAL_12H -> "2:32 PM"
    }
    DailyScreen(modifier) {
        DailyHeading("Do seu jeito", "Ajuste o que aparece no Tripper.")
        ActionRow("Tripper Playground", onPlayground, "Símbolos, prévias e testes estacionários", dev.hunterlab.app.ui.theme.HunterIcons.Playground)
        TripperDisplay(
            example.copy(
                night = night, bottomText = bottom, bottomIsRadar = false,
                radarActive = state.radarOnTripper && example.radarActive,
                smallIcon = if (!state.radarOnTripper && example.radarActive) null else example.smallIcon,
                auxDistanceText = null,
            ),
            LocalTime.of(14, 32), Modifier.widthIn(max = 220.dp).align(Alignment.CenterHorizontally), showCaption = false,
        )
        SupportingText("Prévia aproximada, com valores de exemplo no rodapé.")
        SectionTitle("Aparência do Tripper")
        RadioOptions(DayNightMode.entries, state.dayNight, { it.label }, actions::setDayNight)
        if (state.dayNight == DayNightMode.AUTO) SupportingText("O modo automático acompanha o horário do celular.")
        HorizontalDivider()
        SectionTitle("Rodapé do Tripper")
        RadioOptions(BottomLineMode.entries, state.bottomLine, { it.label }, actions::setBottomLine)
        HorizontalDivider()
        DailySwitch(
            "Radar no Tripper", "Exibir os alertas também no visor da moto.",
            state.radarOnTripper, actions::setRadarOnTripper,
        )
    }
}

@Composable
private fun <T> RadioOptions(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Column(Modifier.fillMaxWidth().selectableGroup()) {
        options.forEach { option ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    .selectable(option == selected, role = Role.RadioButton, onClick = { onSelect(option) })
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(option == selected, onClick = null)
                Spacer(Modifier.width(12.dp))
                Text(label(option), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
