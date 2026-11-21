package dev.hunterlab.app.ui.personalization

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import dev.hunterlab.app.ui.components.*
import dev.hunterlab.app.ui.contract.NavActions
import dev.hunterlab.app.ui.contract.SettingsUi
import dev.hunterlab.app.ui.theme.HunterColors
import dev.hunterlab.app.ui.theme.HunterIcons
import dev.hunterlab.app.ui.tripper.TripperDisplay
import dev.hunterlab.core.display.*
import dev.hunterlab.protocol.DayPeriod
import java.time.LocalTime

@Composable
fun PersonalizationScreen(
    settings: SettingsUi,
    actions: NavActions,
    padding: PaddingValues = PaddingValues(),
    preview: TripperPreview? = null,
    engineAvailable: Boolean = true,
) {
    val clock = LocalTime.now()
    val night = settings.dayNight.resolve(clock, DayNightSchedule()) == DayPeriod.NIGHT
    ProductPage(padding = padding) {
        PageHeading("DO SEU JEITO", "Personalização", "O essencial, no seu ritmo.")
        TripperDisplay(
            preview ?: TripperPreview(PreviewKind.NATIVE_CLOCK, night = night), clock,
            Modifier.widthIn(max = 240.dp).fillMaxWidth().align(Alignment.CenterHorizontally), showCaption = true,
        )
        if (!engineAvailable) InlineNotice("Prévia local. Estes ajustes não alteram o Tripper nesta versão.")
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionLabel("LUZ DO DISPLAY")
            ChoiceRow(DayNightMode.entries, settings.dayNight, { it.label }, actions::setDayNight,
                leading = { when (it) { DayNightMode.DAY -> HunterIcons.Sun; DayNightMode.NIGHT -> HunterIcons.Moon; DayNightMode.AUTO -> HunterIcons.Auto } })
            Text("Automático acompanha o horário do celular.", style = MaterialTheme.typography.bodySmall, color = HunterColors.TextSecondary)
        }
        ProductPanel {
            SectionLabel("NA LINHA INFERIOR")
            ChoiceRow(BottomLineMode.entries, settings.bottomLine, { it.label }, actions::setBottomLine)
            Text("Exibido durante o trajeto.", style = MaterialTheme.typography.bodySmall, color = HunterColors.TextSecondary)
        }
        if (engineAvailable) ProductPanel {
            LabeledSwitch("Radar no Tripper", "Mostrar alertas junto às instruções do trajeto.", settings.radarOnTripper, actions::setRadarOnTripper)
        }
        Text("Símbolos e testes experimentais ficam em Configurações → Avançado → Playground.", style = MaterialTheme.typography.bodySmall, color = HunterColors.TextSecondary)
    }
}
