package dev.hunterlab.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.hunterlab.app.ui.theme.HunterColors
import dev.hunterlab.app.ui.theme.HunterIcons

/** Conteúdo rolável, com largura de leitura limitada e espaço para fontes ampliadas. */
@Composable
fun ProductPage(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(),
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 840.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            content = content,
        )
    }
}

@Composable
fun PageHeading(eyebrow: String, title: String, description: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(eyebrow, HunterColors.TextSecondary)
        Text(title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
        description?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = HunterColors.TextSecondary) }
    }
}

@Composable
fun ProductPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = HunterColors.Surface,
        border = BorderStroke(1.dp, HunterColors.Outline),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}

@Composable
fun PrimaryAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector = HunterIcons.Navigation) {
    Button(
        onClick, modifier.fillMaxWidth().heightIn(min = 56.dp), enabled = enabled,
        shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Icon(icon, null, Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun ActionRow(title: String, detail: String? = null, icon: ImageVector, onClick: () -> Unit, enabled: Boolean = true) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 64.dp).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, null, Modifier.size(24.dp), tint = HunterColors.Amber)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = HunterColors.TextSecondary) }
        }
        Icon(HunterIcons.ChevronRight, null, Modifier.size(18.dp), tint = HunterColors.TextSecondary)
    }
}

@Composable
fun InlineNotice(text: String, warning: Boolean = false, action: String? = null, onAction: () -> Unit = {}) {
    val tint = if (warning) HunterColors.Amber else HunterColors.TextSecondary
    Surface(color = if (warning) HunterColors.AmberContainer else HunterColors.SurfaceHigh, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(if (warning) HunterIcons.Warning else HunterIcons.Info, null, Modifier.size(20.dp), tint = tint)
                Text(text, style = MaterialTheme.typography.bodyMedium, color = if (warning) HunterColors.AmberSoft else HunterColors.TextSecondary)
            }
            if (action != null) TextButton(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) { Text(action) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatusLine(vararg statuses: Pair<String, Color>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        statuses.forEach { (text, color) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Dot(color, 6.dp)
                Text(text, color = color, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold))
        Text(label, style = MaterialTheme.typography.bodySmall, color = HunterColors.TextSecondary)
    }
}

@Composable
fun ActiveTripperStrip(label: String, onInterrupt: () -> Unit) {
    Surface(color = HunterColors.AmberContainer) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = HunterColors.AmberSoft)
            TextButton(onInterrupt, Modifier.heightIn(min = 48.dp), colors = ButtonDefaults.textButtonColors(contentColor = HunterColors.Error)) {
                Text("Interromper")
            }
        }
    }
}
