package dev.hunterlab.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.platform.LocalDensity
import dev.hunterlab.app.ui.theme.HunterShapes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.hunterlab.app.ui.contract.NavActions
import dev.hunterlab.app.ui.theme.HunterColors
import dev.hunterlab.app.ui.theme.HunterIcons

/** Conteúdo rolável, também em 360 × 640 dp e com o teclado aberto. */
@Composable
fun DailyScreen(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

@Composable
fun DailyHeading(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = HunterColors.TextSecondary) }
    }
}

@Composable
fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
}

@Composable
fun SupportingText(text: String, error: Boolean = false) {
    Text(
        text, style = MaterialTheme.typography.bodySmall,
        color = if (error) HunterColors.ErrorInk else HunterColors.TextSecondary,
        modifier = Modifier.semantics { if (error) liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
fun PrimaryAction(text: String, onClick: () -> Unit, enabled: Boolean = true, icon: ImageVector? = null) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = HunterShapes.Medium, interactionSource = interaction,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (pressed) HunterColors.PrimaryPressed else HunterColors.Primary,
            contentColor = if (pressed) HunterColors.OnPrimaryPressed else HunterColors.OnPrimary,
        ),
    ) {
        icon?.let { Icon(it, contentDescription = null); Spacer(Modifier.width(10.dp)) }
        Text(text)
    }
}

@Composable
fun ActionRow(title: String, onClick: () -> Unit, detail: String? = null, icon: ImageVector? = null) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        icon?.let { Icon(it, null, tint = HunterColors.TextSecondary); Spacer(Modifier.width(12.dp)) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = HunterColors.TextPrimary)
            detail?.let { SupportingText(it) }
        }
        Icon(HunterIcons.ChevronRight, contentDescription = null, tint = HunterColors.TextSecondary)
    }
}

@Composable
fun DailySwitch(title: String, detail: String? = null, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            detail?.let { SupportingText(it) }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked, onChecked, modifier = Modifier.heightIn(min = 48.dp).semantics {
            // O texto ao lado também é anunciado como parte da opção.
            contentDescription = title
        })
    }
}

/** A mesma confirmação única no painel e na barra persistente da raiz. */
@Composable
fun SessionSafetyActions(active: Boolean, canInterrupt: Boolean, actions: NavActions, modifier: Modifier = Modifier) {
    var confirmStop by remember(active) { mutableStateOf(false) }
    if (!active && !canInterrupt) return
    BoxWithConstraints(modifier.fillMaxWidth().background(HunterColors.Background)) {
        val stacked = maxWidth < 360.dp || LocalDensity.current.fontScale > 1.2f
        val stopRadio: @Composable (Modifier) -> Unit = { buttonModifier ->
            OutlinedButton(onClick = actions::interruptTripper, modifier = buttonModifier.heightIn(min = 48.dp),
                shape = HunterShapes.Small, colors = ButtonDefaults.outlinedButtonColors(contentColor = HunterColors.ErrorInk),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            ) { Text("Interromper") }
        }
        val stopJourney: @Composable (Modifier) -> Unit = { buttonModifier ->
            OutlinedButton(onClick = { confirmStop = true }, modifier = buttonModifier.heightIn(min = 48.dp),
                shape = HunterShapes.Small, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            ) { Text("Encerrar navegação", color = HunterColors.TextPrimary) }
        }
        if (stacked) Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (canInterrupt) stopRadio(Modifier.fillMaxWidth())
            if (active) stopJourney(Modifier.fillMaxWidth())
        } else Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (canInterrupt) stopRadio(Modifier.weight(1f))
            if (active) stopJourney(Modifier.weight(1.25f))
        }
    }
    if (confirmStop && active) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text("Encerrar navegação?") },
            text = { Text("O trajeto atual será encerrado.") },
            confirmButton = { TextButton(onClick = { confirmStop = false; actions.stopNavigation() }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Encerrar") } },
            dismissButton = { TextButton(onClick = { confirmStop = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Continuar") } },
        )
    }
}

/** Isola a sobrecarga obsoleta, sem introduzir dependências. */
@Suppress("DEPRECATION")
@Composable
fun HunterSlider(value: Float, onValueChange: (Float) -> Unit, modifier: Modifier = Modifier, steps: Int = 0) {
    Slider(value = value.coerceIn(0f, 1f), onValueChange = onValueChange, steps = steps, modifier = modifier.heightIn(min = 48.dp))
}
