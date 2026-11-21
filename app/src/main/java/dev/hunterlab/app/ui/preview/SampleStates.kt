package dev.hunterlab.app.ui.preview

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.hunterlab.app.ui.HunterBrandBar
import dev.hunterlab.app.ui.HunterNavigationBar
import dev.hunterlab.app.ui.PrimaryArea
import dev.hunterlab.app.ui.contract.*
import dev.hunterlab.app.ui.components.ActiveTripperStrip
import dev.hunterlab.app.ui.home.HomeScreen
import dev.hunterlab.app.ui.home.LocalAppearanceActions
import dev.hunterlab.app.ui.navigation.NavigationScreen
import dev.hunterlab.app.ui.personalization.PersonalizationScreen
import dev.hunterlab.app.ui.settings.SettingsScreen
import dev.hunterlab.app.ui.theme.HunterColors
import dev.hunterlab.app.ui.theme.HunterLabTheme

/** Dados fictícios, exclusivos das prévias do Android Studio; não entram nos estados do app. */
object SampleStates {
    val actions: NavActions = LocalAppearanceActions { }
    val destination = DestinationUi("Parque Central", 0.0, 0.0, "Destino de exemplo")
    val connected = TripperUi(TripperUiStatus.SHOWING, "Tripper conectado", canInterrupt = true, needsTerms = false)
    val navigating = NavUi(
        phase = NavPhaseUi.NAVIGATING, destination = destination,
        current = ManeuverUi(0x14, "Vire à esquerda", "250 m", "Avenida das Palmeiras"),
        next = ManeuverUi(0x09, "Siga em frente", "1 km"),
        remainingDistanceText = "6,4 km", remainingTimeText = "12 min", arrivalText = "19:42",
        gps = GpsUi.OK, tripper = connected,
        radar = RadarUi(true, "Cobertura de radar disponível"),
        simulated = true,
    )
    val radar = navigating.copy(radar = navigating.radar?.copy(alertText = "Radar 60 km/h a 280 m", alertDistanceM = 280))
    val pin = NavUi(tripper = TripperUi(
        status = TripperUiStatus.WAITING_PIN, label = "Digite o PIN do Tripper",
        canInterrupt = true, needsTerms = false,
    ))
    val home = HomeUi(
        destination = destination, route = RouteSummaryUi("6,4 km", "12 min", "19:42", "Provedor de exemplo"),
        providerConfigured = true, canStart = true, tripper = connected, gps = GpsUi.OK,
    )
    val settings = SettingsUi(version = "0.2.1", permissions = PermissionsUi(bluetooth = true))
}

@Composable
private fun PreviewShell(area: PrimaryArea, tripper: TripperUi? = null, content: @Composable (PaddingValues) -> Unit) {
    HunterLabTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize(), containerColor = HunterColors.Background,
            topBar = { Column { HunterBrandBar(); if (tripper?.canInterrupt == true) ActiveTripperStrip(tripper.label) {} } }, bottomBar = { HunterNavigationBar(area) {} },
            content = content,
        )
    }
}

@Preview(name = "Início · 360 × 640", widthDp = 360, heightDp = 640, showBackground = true)
@Composable
fun HomeCompactPreview() = PreviewShell(PrimaryArea.HOME, SampleStates.home.tripper) { HomeScreen(SampleStates.home, SampleStates.actions, it) }

@Preview(name = "Navegação · 360 × 800", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
fun NavigationPreview() = PreviewShell(PrimaryArea.NAVIGATION, SampleStates.navigating.tripper) { NavigationScreen(SampleStates.navigating, SampleStates.actions, it) }

@Preview(name = "Radar · 360 × 800", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
fun RadarAlertPreview() = PreviewShell(PrimaryArea.NAVIGATION, SampleStates.radar.tripper) { NavigationScreen(SampleStates.radar, SampleStates.actions, it) }

@Preview(name = "PIN · 360 × 640", widthDp = 360, heightDp = 640, showBackground = true)
@Composable
fun PairingPreview() = PreviewShell(PrimaryArea.NAVIGATION, SampleStates.pin.tripper) { NavigationScreen(SampleStates.pin, SampleStates.actions, it) }

@Preview(name = "Sem provedor · 360 × 640", widthDp = 360, heightDp = 640, showBackground = true)
@Composable
fun NoProviderPreview() = PreviewShell(PrimaryArea.HOME) { HomeScreen(HomeUi(), SampleStates.actions, it) }

@Preview(name = "Base publicada · 360 × 640", widthDp = 360, heightDp = 640, showBackground = true)
@Composable
fun PublishedBasePreview() = PreviewShell(PrimaryArea.HOME) { HomeScreen(HomeUi(), SampleStates.actions, it, engineAvailable = false, legacyConnect = {}) }

@Preview(name = "Personalização · 360 × 800", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
fun PersonalizationPreview() = PreviewShell(PrimaryArea.APPEARANCE) { PersonalizationScreen(SampleStates.settings, SampleStates.actions, it) }

@Preview(name = "Configurações · 360 × 800", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
fun SettingsPreview() = PreviewShell(PrimaryArea.SETTINGS) { SettingsScreen(SampleStates.settings, SampleStates.actions, it) }

@Preview(name = "Fonte 150% · 320 × 640", widthDp = 320, heightDp = 640, fontScale = 1.5f, showBackground = true)
@Composable
fun LargeFontPreview() = PreviewShell(PrimaryArea.NAVIGATION, SampleStates.radar.tripper) { NavigationScreen(SampleStates.radar, SampleStates.actions, it) }

@Preview(name = "Tela ampla · 840 × 1000", widthDp = 840, heightDp = 1000, showBackground = true)
@Composable
fun WidePreview() = PreviewShell(PrimaryArea.HOME, SampleStates.home.tripper) { HomeScreen(SampleStates.home, SampleStates.actions, it) }

@Preview(name = "Calculando rota", widthDp = 360, heightDp = 640, showBackground = true)
@Composable
fun RoutingPreview() = PreviewShell(PrimaryArea.NAVIGATION) {
    NavigationScreen(SampleStates.navigating.copy(phase = NavPhaseUi.REROUTING, message = "Buscando um novo caminho…"), SampleStates.actions, it)
}

@Preview(name = "Erro de rota", widthDp = 360, heightDp = 640, showBackground = true)
@Composable
fun ErrorPreview() = PreviewShell(PrimaryArea.HOME) {
    HomeScreen(SampleStates.home.copy(route = null, canStart = false, routeError = "Não foi possível calcular a rota. Verifique sua conexão."), SampleStates.actions, it)
}
