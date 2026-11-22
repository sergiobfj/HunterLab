package dev.hunterlab.app.ui.preview

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.hunterlab.app.R
import dev.hunterlab.app.ui.theme.HunterColors
import dev.hunterlab.app.ui.theme.HunterLabTheme

private enum class HorsePaper { DARK, LIGHT, MONO }

/** Editorial comparison only: no state/controller or connection to device features. */
@Composable
private fun HorseIcon(version: Int, size: Dp, paper: HorsePaper = HorsePaper.DARK, circle: Boolean = false) {
    val foreground = if (version == 1) R.drawable.ic_launcher_foreground else R.drawable.ic_horse_refined
    val day = if (version == 1) R.drawable.ic_horse_original_light else R.drawable.ic_horse_refined_light
    val mono = if (version == 1) R.drawable.ic_launcher_monochrome else R.drawable.ic_horse_refined_monochrome
    val background = when (paper) {
        HorsePaper.DARK -> HunterColors.Background
        HorsePaper.LIGHT -> Color(0xFFDCE3E6)
        HorsePaper.MONO -> HunterColors.OnPrimaryContainer
    }
    Box(Modifier.size(size).clip(if (circle) CircleShape else RoundedCornerShape(size * .29f)).background(background)) {
        val layer = Modifier.requiredSize(size * 1.5f).align(Alignment.Center)
        if (paper == HorsePaper.MONO) Icon(painterResource(mono), "A$version monocromática", layer, tint = HunterColors.Background)
        else Image(painterResource(if (paper == HorsePaper.LIGHT) day else foreground), "Hunter Lab A$version", layer)
    }
}

@Preview(name = "A1 e A2 · comparação", widthDp = 360, heightDp = 1100)
@Composable
fun HorseRefinementComparisonPreview() = HunterLabTheme {
    Column(Modifier.fillMaxSize().background(HunterColors.Surface).verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text("Cavalo de rua", style = MaterialTheme.typography.headlineMedium, color = HunterColors.TextPrimary)
        Text("Antes e depois. A2 aguarda aprovação visual.", color = HunterColors.TextSecondary)
        for (paper in HorsePaper.entries) {
            Text(when (paper) { HorsePaper.DARK -> "Fundo escuro"; HorsePaper.LIGHT -> "Fundo claro"; HorsePaper.MONO -> "Monocromática" },
                style = MaterialTheme.typography.titleMedium, color = HunterColors.TextPrimary)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                for (version in 1..2) Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HorseIcon(version, 112.dp, paper, circle = true)
                    Text(if (version == 1) "A1 · anterior" else "A2 · refinada", style = MaterialTheme.typography.bodySmall, color = HunterColors.TextPrimary)
                    HorseIcon(version, 48.dp, paper)
                }
            }
        }
        Text("Recortes aproximados. Sem validação em aparelho.", style = MaterialTheme.typography.bodySmall, color = HunterColors.TextSecondary)
    }
}

@Preview(name = "A1 e A2 · 320 dp / 150%", widthDp = 320, heightDp = 1100, fontScale = 1.5f)
@Composable fun HorseRefinementLargeFontPreview() = HorseRefinementComparisonPreview()

@Composable
private fun HorseLauncherComparison(paper: HorsePaper) = HunterLabTheme {
    val light = paper == HorsePaper.LIGHT
    val ink = if (light) HunterColors.Background else HunterColors.TextPrimary
    val secondary = if (light) Color(0xFF4D5A61) else HunterColors.TextSecondary
    Column(Modifier.fillMaxSize().background(if (light) Color(0xFFF2F4F5) else HunterColors.Background).padding(28.dp)) {
        Text("Samsung · launcher ilustrativo", style = MaterialTheme.typography.bodySmall, color = secondary)
        Spacer(Modifier.height(64.dp))
        Text("Hunter Lab", style = MaterialTheme.typography.headlineLarge, color = ink)
        Spacer(Modifier.height(12.dp))
        Text("A1 anterior / A2 refinada", color = secondary)
        Text("Mesma escala. Aprovação pendente.", style = MaterialTheme.typography.bodySmall, color = secondary)
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            for (version in 1..2) Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HorseIcon(version, 64.dp, paper)
                Text("Hunter Lab · A$version", style = MaterialTheme.typography.bodySmall, color = ink)
            }
        }
        Spacer(Modifier.height(64.dp))
        Text("Simulação antes/depois. Máscara e cores temáticas reais dependem do launcher. Nenhuma instalação no aparelho.",
            style = MaterialTheme.typography.bodySmall, color = secondary)
    }
}
@Preview(name = "Launcher · escuro", widthDp = 360, heightDp = 800)
@Composable fun HorseLauncherDarkPreview() = HorseLauncherComparison(HorsePaper.DARK)
@Preview(name = "Launcher · claro", widthDp = 360, heightDp = 800)
@Composable fun HorseLauncherLightPreview() = HorseLauncherComparison(HorsePaper.LIGHT)
@Preview(name = "Launcher · temático", widthDp = 360, heightDp = 800)
@Composable fun HorseLauncherMonoPreview() = HorseLauncherComparison(HorsePaper.MONO)
