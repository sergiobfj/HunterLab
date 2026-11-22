package dev.hunterlab.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hunterlab.app.ui.theme.HunterShapes
import dev.hunterlab.app.ui.theme.HunterColors
import dev.hunterlab.app.ui.theme.HunterIcons
import dev.hunterlab.app.ui.theme.color
import dev.hunterlab.core.ble.Severity
import dev.hunterlab.core.ble.UserMessage
import dev.hunterlab.protocol.Confidence

@Composable
fun HunterCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = HunterShapes.Medium,
        color = HunterColors.Surface,
        border = BorderStroke(1.dp, HunterColors.Outline),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (title != null || trailing != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        title?.let { SectionLabel(it) }
                        subtitle?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = HunterColors.TextSecondary, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                    trailing?.invoke()
                }
            }
            content()
        }
    }
}

@Composable
fun SectionLabel(text: String, color: Color = HunterColors.TextSecondary) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = color)
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(5.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1)
    }
}

@Composable
fun ConfidenceBadge(confidence: Confidence, modifier: Modifier = Modifier) {
    Pill(confidence.label, confidence.color(), modifier)
}

@Composable
fun Dot(color: Color, size: Dp = 8.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

/** Faixa que identifica conteúdo simulado/fictício. */
@Composable
fun SimulationBanner(text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(HunterColors.AmberContainer)
            .border(1.dp, HunterColors.Amber.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("SIMULAÇÃO", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.6.sp, fontWeight = FontWeight.Bold), color = HunterColors.Amber)
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = HunterColors.AmberSoft)
    }
}

@Composable
fun MessageCard(message: UserMessage, modifier: Modifier = Modifier, actions: (@Composable () -> Unit)? = null) {
    val color = when {
        message.expected -> HunterColors.InfoInk
        message.severity == Severity.ERROR -> HunterColors.ErrorInk
        message.severity == Severity.WARNING -> HunterColors.Amber
        else -> HunterColors.InfoInk
    }
    val icon = if (message.severity == Severity.INFO || message.expected) HunterIcons.Info else HunterIcons.Warning
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.08f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(message.title, style = MaterialTheme.typography.titleSmall, color = HunterColors.TextPrimary)
            Text(message.detail, style = MaterialTheme.typography.bodySmall, color = HunterColors.TextSecondary)
            message.hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = color) }
            actions?.let {
                Spacer(Modifier.size(4.dp))
                it()
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceRow(
    options: List<T>,
    selected: T?,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: (T) -> Boolean = { true },
    leading: ((T) -> ImageVector?)? = null,
) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val icon = leading?.invoke(option)
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                enabled = enabled(option),
                label = { Text(label(option)) },
                leadingIcon = icon?.let { { Icon(it, null, Modifier.size(16.dp)) } },
                shape = RoundedCornerShape(10.dp),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = HunterColors.SurfaceHigh,
                    labelColor = HunterColors.TextSecondary,
                    iconColor = HunterColors.TextSecondary,
                    selectedContainerColor = HunterColors.PrimaryContainer,
                    selectedLabelColor = HunterColors.OnPrimaryContainer,
                    selectedLeadingIconColor = HunterColors.OnPrimaryContainer,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = enabled(option),
                    selected = option == selected,
                    borderColor = HunterColors.Outline,
                    selectedBorderColor = HunterColors.Primary.copy(alpha = 0.6f),
                ),
            )
        }
    }
}

@Composable
fun LabeledSwitch(title: String, subtitle: String?, checked: Boolean, onChecked: (Boolean) -> Unit, badge: (@Composable () -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, color = HunterColors.TextPrimary)
                badge?.invoke()
            }
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = HunterColors.TextSecondary) }
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(
                checkedThumbColor = HunterColors.Background,
                checkedTrackColor = HunterColors.Primary,
                uncheckedThumbColor = HunterColors.TextSecondary,
                uncheckedTrackColor = HunterColors.SurfaceHighest,
                uncheckedBorderColor = HunterColors.OutlineStrong,
            ),
        )
    }
}

@Composable
fun KeyValue(key: String, value: String, valueColor: Color = HunterColors.TextPrimary) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(key, style = MaterialTheme.typography.bodySmall, color = HunterColors.TextSecondary)
        Spacer(Modifier.width(12.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, color = valueColor)
    }
}

@Composable
fun BulletText(text: String, color: Color = HunterColors.TextSecondary) {
    Row {
        Text("•", color = HunterColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.width(8.dp))
        Text(text, color = color, style = MaterialTheme.typography.bodySmall)
    }
}

/** Uma coluna em telas estreitas; duas colunas lado a lado a partir de 720 dp (tablets/paisagem). */
@Composable
fun AdaptivePanes(
    modifier: Modifier = Modifier,
    first: @Composable ColumnScope.() -> Unit,
    second: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier) {
        if (maxWidth >= 720.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(0.45f), verticalArrangement = Arrangement.spacedBy(16.dp), content = first)
                Column(Modifier.weight(0.55f), verticalArrangement = Arrangement.spacedBy(16.dp), content = second)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                first()
                second()
            }
        }
    }
}

/** Cartão com cabeçalho clicável; recolhido por padrão para reduzir informação na tela. */
@Composable
fun CollapsibleCard(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(initiallyExpanded) }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = HunterShapes.Medium,
        color = HunterColors.Surface,
        border = BorderStroke(1.dp, HunterColors.Outline),
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(HunterShapes.Medium)
                    .clickable { expanded = !expanded }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    SectionLabel(title)
                    summary?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = HunterColors.TextSecondary, modifier = Modifier.padding(top = 2.dp))
                    }
                }
                Icon(
                    if (expanded) HunterIcons.ChevronDown else HunterIcons.ChevronRight,
                    contentDescription = if (expanded) "Recolher" else "Expandir",
                    tint = HunterColors.TextMuted,
                )
            }
            if (expanded) {
                Column(
                    Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content,
                )
            }
        }
    }
}
