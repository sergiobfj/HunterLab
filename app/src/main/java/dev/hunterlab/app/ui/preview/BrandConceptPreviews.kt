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

private data class BrandCandidate(val name: String, val foreground: Int, val monochrome: Int)
private val Horse = BrandCandidate("A · Cavalo de rua", R.drawable.ic_launcher_foreground, R.drawable.ic_launcher_monochrome)
private val Roadster = BrandCandidate("B · Roadster", R.drawable.ic_candidate_roadster, R.drawable.ic_candidate_roadster_monochrome)
private val Hybrid = BrandCandidate("C · Elo mecânico", R.drawable.ic_candidate_hybrid, R.drawable.ic_candidate_hybrid_monochrome)

/** Study only. The launcher's own mask/theme can differ from these approximations. */
@Composable
private fun CandidateIcon(candidate: BrandCandidate, size: Dp, round: Boolean = false, monochrome: Boolean = false, light: Boolean = false) {
    Box(Modifier.size(size).clip(if (round) CircleShape else RoundedCornerShape(size * .29f))
        .background(if (light) HunterColors.OnPrimaryContainer else HunterColors.Background)) {
        // Adaptive layers use 108 dp; their central viewport is 72 dp.
        val layer = Modifier.requiredSize(size * 1.5f).align(Alignment.Center)
        if (monochrome) Icon(painterResource(candidate.monochrome), candidate.name, layer,
            tint = if (light) HunterColors.Background else Color(0xFFB7CF62))
        else Image(painterResource(candidate.foreground), candidate.name, layer)
    }
}

@Preview(name = "Três marcas · recursos Android", widthDp = 360, heightDp = 900)
@Composable
fun BrandComparisonPreview() = HunterLabTheme {
    Column(Modifier.fillMaxSize().background(HunterColors.Surface).verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text("Estudos de marca", style = MaterialTheme.typography.headlineMedium, color = HunterColors.TextPrimary)
        Text("Aguardam aprovação. Máscaras aproximadas; sem teste no aparelho.", color = HunterColors.TextSecondary)
        for (candidate in listOf(Horse, Roadster, Hybrid)) {
            Text(candidate.name, style = MaterialTheme.typography.titleLarge, color = HunterColors.TextPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                CandidateIcon(candidate, 72.dp, round = true)
                CandidateIcon(candidate, 72.dp)
                CandidateIcon(candidate, 72.dp, monochrome = true, light = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                CandidateIcon(candidate, 48.dp)
                CandidateIcon(candidate, 32.dp, monochrome = true)
                Text("48 / 32 dp", style = MaterialTheme.typography.bodySmall, color = HunterColors.TextSecondary)
            }
        }
    }
}

@Preview(name = "320 dp · fonte 150%", widthDp = 320, heightDp = 1000, fontScale = 1.5f)
@Composable
fun BrandLargeFontPreview() = BrandComparisonPreview()

@Composable
private fun CandidateLauncher(candidate: BrandCandidate) = HunterLabTheme {
    Column(Modifier.fillMaxSize().background(HunterColors.Background).padding(28.dp)) {
        Text("Launcher Samsung · simulação", style = MaterialTheme.typography.bodySmall, color = HunterColors.TextSecondary)
        Spacer(Modifier.height(64.dp))
        Text("Hunter Lab", style = MaterialTheme.typography.headlineLarge, color = HunterColors.TextPrimary)
        Spacer(Modifier.height(12.dp))
        Text(candidate.name, color = HunterColors.TextSecondary)
        Text("Identidade pendente de aprovação", style = MaterialTheme.typography.bodySmall, color = HunterColors.TextSecondary)
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CandidateIcon(candidate, 64.dp)
                Spacer(Modifier.height(8.dp))
                Text("Hunter Lab", style = MaterialTheme.typography.bodySmall, color = HunterColors.TextPrimary)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CandidateIcon(candidate, 64.dp, monochrome = true, light = true)
                Spacer(Modifier.height(8.dp))
                Text("Temático", style = MaterialTheme.typography.bodySmall, color = HunterColors.TextPrimary)
            }
        }
        Spacer(Modifier.height(64.dp))
        Text("Recorte inspirado em One UI. Sem validação em aparelho; a máscara real depende do launcher.",
            style = MaterialTheme.typography.bodySmall, color = HunterColors.TextSecondary)
    }
}

@Preview(name = "A · launcher", widthDp = 360, heightDp = 800)
@Composable fun BrandHorseLauncherPreview() = CandidateLauncher(Horse)
@Preview(name = "B · launcher", widthDp = 360, heightDp = 800)
@Composable fun BrandRoadsterLauncherPreview() = CandidateLauncher(Roadster)
@Preview(name = "C · launcher", widthDp = 360, heightDp = 800)
@Composable fun BrandHybridLauncherPreview() = CandidateLauncher(Hybrid)
@Preview(name = "A · abertura ilustrativa", widthDp = 360, heightDp = 800)
@Composable fun BrandHorseSplashPreview() {
    Box(Modifier.fillMaxSize().background(HunterColors.Background), contentAlignment = Alignment.Center) {
        Image(painterResource(Horse.foreground), "Hunter Lab — proposta A", Modifier.size(240.dp))
    }
}
