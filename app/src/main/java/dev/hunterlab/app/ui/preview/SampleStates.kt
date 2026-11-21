package dev.hunterlab.app.ui.preview

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.hunterlab.app.ui.contract.*
import dev.hunterlab.app.ui.home.HomeScreen
import dev.hunterlab.app.ui.navigation.NavigationScreen
import dev.hunterlab.app.ui.personalization.PersonalizationScreen
import dev.hunterlab.app.ui.settings.SettingsScreen
import dev.hunterlab.app.ui.theme.HunterLabTheme
import dev.hunterlab.core.display.*
import dev.hunterlab.protocol.TripperIcons

/** Exemplos independentes da lógica, sem rede, GPS, BLE ou armazenamento. */
object SampleStates {
    val destination = DestinationUi("Parque Ibirapuera", -23.5874, -46.6576, "Av. Pedro Álvares Cabral · São Paulo")
    val tripper = TripperUi(
        status = TripperUiStatus.SHOWING, label = "Tripper conectado", canInterrupt = true, needsTerms = false,
    )
    val preview = TripperPreview(
        kind = PreviewKind.NAVIGATION, night = true, largeIcon = TripperIcons.describe(0x15),
        distanceText = "300 m", smallIcon = TripperIcons.describe(TripperIcons.STRAIGHT), bottomText = "12,4 km",
    )
    val home = HomeUi(
        destination = destination, route = RouteSummaryUi("12,4 km", "18 min", "14:32", "© OpenStreetMap · Provedor de rotas"),
        providerConfigured = true, canStart = true, gps = GpsUi.OK,
        tripper = TripperUi(canConnect = true),
    )
    val noKey = HomeUi(providerConfigured = false, tripper = TripperUi(canConnect = true), recent = listOf(destination))
    val navigating = NavUi(
        phase = NavPhaseUi.NAVIGATING, destination = destination,
        current = ManeuverUi(0x15, "Vire à direita", "300 m", "Av. Brasil"),
        next = ManeuverUi(TripperIcons.STRAIGHT, "Siga em frente", "1,2 km"),
        remainingDistanceText = "12,4 km", remainingTimeText = "18 min", arrivalText = "14:32",
        gps = GpsUi.OK, tripper = tripper, tripperPreview = preview,
        radar = RadarUi(true, "Dados de radar atualizados"),
    )
    val pin = navigating.copy(
        tripper = TripperUi(
            status = TripperUiStatus.WAITING_PIN, label = "Digite o PIN mostrado no Tripper",
            pinAttempt = 1, canInterrupt = true, needsTerms = false,
        ),
    )
    val radarAlert = navigating.copy(radar = RadarUi(true, "Dados de radar atualizados", alertText = "Radar 60 km/h a 280 m", alertDistanceM = 280))
    val settings = SettingsUi(
        apiKeyConfigured = true, apiKeyMasked = "••••••3f2a", version = "0.2.2",
        permissions = PermissionsUi(location = true, notifications = false, bluetooth = true),
        routingAttribution = "© colaboradores do OpenStreetMap · Provedor de rotas",
    )
}

/** Ações vazias exclusivamente para previews. */
object EmptyNavActions : NavActions {
    override fun search(query: String) = Unit
    override fun selectDestination(destination: DestinationUi) = Unit
    override fun setDestinationFromText(text: String): String? = null
    override fun clearDestination() = Unit
    override fun previewRoute() = Unit
    override fun startNavigation(simulated: Boolean) = Unit
    override fun stopNavigation() = Unit
    override fun acceptTerms() = Unit
    override fun connectTripper() = Unit
    override fun requestPin() = Unit
    override fun submitPin(pin: String) = Unit
    override fun confirmPinAccepted() = Unit
    override fun reportPinRejected() = Unit
    override fun interruptTripper() = Unit
    override fun setApiKey(key: String) = Unit
    override fun clearApiKey() = Unit
    override fun setDayNight(mode: DayNightMode) = Unit
    override fun setBottomLine(mode: BottomLineMode) = Unit
    override fun setRadarEnabled(enabled: Boolean) = Unit
    override fun setRadarOnTripper(enabled: Boolean) = Unit
    override fun setRadarThresholds(searchM: Int, alertM: Int) = Unit
    override fun refreshPermissions() = Unit
}

@Composable
private fun PreviewSurface(content: @Composable () -> Unit) {
    HunterLabTheme { Surface(Modifier.fillMaxSize(), content = content) }
}

@Preview(name = "Início · rota pronta", locale = "pt-rBR", widthDp = 360, heightDp = 640, showBackground = true)
@Composable
private fun HomePreview() = PreviewSurface { HomeScreen(SampleStates.home, EmptyNavActions, {}, {}) }

@Preview(name = "Início · sem chave", locale = "pt-rBR", widthDp = 360, heightDp = 640)
@Composable
private fun NoKeyPreview() = PreviewSurface { HomeScreen(SampleStates.noKey, EmptyNavActions, {}, {}) }

@Preview(name = "Navegação", locale = "pt-rBR", widthDp = 360, heightDp = 640)
@Composable
private fun NavigationPreview() = PreviewSurface { NavigationScreen(SampleStates.navigating, EmptyNavActions, {}) }

@Preview(name = "Navegação · PIN", locale = "pt-rBR", widthDp = 360, heightDp = 640)
@Composable
private fun PinPreview() = PreviewSurface { NavigationScreen(SampleStates.pin, EmptyNavActions, {}) }

@Preview(name = "Navegação · radar em alerta", locale = "pt-rBR", widthDp = 360, heightDp = 640)
@Composable
private fun RadarPreview() = PreviewSurface { NavigationScreen(SampleStates.radarAlert, EmptyNavActions, {}) }

@Preview(name = "Navegação · sem trajeto", locale = "pt-rBR", widthDp = 360, heightDp = 640)
@Composable
private fun EmptyNavigationPreview() = PreviewSurface { NavigationScreen(NavUi(), EmptyNavActions, {}) }

@Preview(name = "Personalização", locale = "pt-rBR", widthDp = 360, heightDp = 640)
@Composable
private fun PersonalizationPreview() = PreviewSurface { PersonalizationScreen(SampleStates.settings, EmptyNavActions, preview = SampleStates.preview) }

@Preview(name = "Configurações", locale = "pt-rBR", widthDp = 360, heightDp = 640)
@Composable
private fun SettingsPreview() = PreviewSurface { SettingsScreen(SampleStates.settings, EmptyNavActions, {}) }

@Preview(name = "Navegação · fonte ampliada", locale = "pt-rBR", widthDp = 360, heightDp = 640, fontScale = 1.5f)
@Composable
private fun LargeTypePreview() = PreviewSurface { NavigationScreen(SampleStates.navigating, EmptyNavActions, {}) }
