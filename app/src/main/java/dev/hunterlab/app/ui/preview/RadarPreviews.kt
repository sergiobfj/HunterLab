package dev.hunterlab.app.ui.preview

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.hunterlab.app.ui.contract.*
import dev.hunterlab.app.ui.navigation.RadarAlert
import dev.hunterlab.app.ui.navigation.NavigationScreen
import dev.hunterlab.app.ui.theme.HunterLabTheme

/** Synthetic review states; never a claim that these values were seen in hardware. */
object RadarReviewStates {
    val known = RadarUi(true, "Cobertura parcial de radares", "Radar fixo a 280 m", 280,
        limitKmh = 50, limitStatus = "Limite cadastrado", kindText = "Radar fixo",
        sourceText = "OpenStreetMap · Pardal / DER-PE · 19/11/2026")
    val missing = known.copy(limitKmh = null, limitStatus = "Limite indisponível")
    val conflict = known.copy(limitKmh = null, limitStatus = "Limites divergentes")
    val conditional = known.copy(limitKmh = null, limitStatus = "Limite condicionado")
}

@Composable
fun RadarReview(state: RadarUi) = HunterLabTheme {
    Surface {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("Radares", style = MaterialTheme.typography.headlineMedium)
            RadarAlert(state)
            Text("Prévia com dados sintéticos. A sinalização da via prevalece.", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Preview(name = "Radar · limite cadastrado", widthDp = 360, locale = "pt-rBR")
@Composable fun RadarKnownPreview() = RadarReview(RadarReviewStates.known)
@Preview(name = "Radar · sem limite", widthDp = 360, locale = "pt-rBR")
@Composable fun RadarMissingPreview() = RadarReview(RadarReviewStates.missing)
@Preview(name = "Radar · divergência", widthDp = 360, locale = "pt-rBR")
@Composable fun RadarConflictPreview() = RadarReview(RadarReviewStates.conflict)
@Preview(name = "Radar · condição", widthDp = 360, locale = "pt-rBR")
@Composable fun RadarConditionalPreview() = RadarReview(RadarReviewStates.conditional)
@Preview(name = "Radar · 320 dp / fonte 150%", widthDp = 320, fontScale = 1.5f, locale = "pt-rBR")
@Composable fun RadarLargeTypePreview() = RadarReview(RadarReviewStates.known.copy(limitKmh = 100))
@Preview(name = "Navegação · radar e limite do trecho separados", widthDp = 360, heightDp = 900, locale = "pt-rBR")
@Composable fun RadarNavigationPreview() = HunterLabTheme {
    Surface { NavigationScreen(SampleStates.navigating.copy(
        simulated = true, radar = RadarReviewStates.known, roadLimitKmh = 60,
        tripper = TripperUi(), tripperPreview = null,
    ), EmptyNavActions, {}) }
}
