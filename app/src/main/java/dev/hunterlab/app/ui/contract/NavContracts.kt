package dev.hunterlab.app.ui.contract

import dev.hunterlab.core.display.BottomLineMode
import dev.hunterlab.core.display.DayNightMode
import dev.hunterlab.core.display.TripperPreview
import kotlinx.coroutines.flow.StateFlow

/*
 * Contratos ESTÁVEIS entre a lógica (NavigationController, Agente A) e a interface (Agente B / Codex).
 * Regras: telas recebem estes estados e chamam [NavActions]; não acessam BLE, rede, GPS ou preferências diretamente.
 * Textos já chegam formatados em pt-BR. Mudanças aqui exigem alinhamento entre os dois lados.
 */

/** Destino escolhido (busca, coordenadas ou link geo:). */
data class DestinationUi(val label: String, val lat: Double, val lon: Double, val detail: String? = null, val straightLineDistanceText: String? = null)

data class SearchUi(
    val query: String = "",
    val searching: Boolean = false,
    val results: List<DestinationUi> = emptyList(),
    val error: String? = null,
)

data class RouteSummaryUi(
    val distanceText: String,
    val durationText: String,
    val arrivalText: String,
    val attribution: String,
)

enum class GpsUi(val label: String) {
    NO_PERMISSION("Sem permissão de localização"),
    OFF("GPS desligado"),
    SEARCHING("Procurando GPS…"),
    OK("GPS ok"),
    IMPRECISE("GPS impreciso"),
    LOST("Sinal de GPS perdido"),
}

enum class TripperUiStatus {
    /** Sem sessão. [TripperUi.canConnect] diz se dá para conectar. */
    DISCONNECTED,
    CONNECTING,

    /** Conectado e com transmissão autorizada: tocar em "Mostrar PIN no Tripper". */
    READY_TO_PAIR,

    /** O Tripper deve estar mostrando um PIN: digitar e enviar. */
    WAITING_PIN,

    /** PIN enviado: o usuário confirma pelo display se foi aceito. */
    CONFIRM_PIN,

    /** Autenticado (confirmação visual); a navegação aparece quando houver rota. */
    AUTHENTICATED,

    /** Telas de navegação sendo transmitidas. */
    SHOWING,

    /** Interrompendo/encerrando o link. */
    STOPPING,
    ERROR,
}

data class TripperUi(
    val status: TripperUiStatus = TripperUiStatus.DISCONNECTED,
    /** Uma linha curta: "Tripper conectado", "Digite o PIN mostrado no Tripper"… */
    val label: String = "Tripper desconectado",
    val detail: String? = null,
    val canConnect: Boolean = false,
    val canInterrupt: Boolean = false,
    val pinAttempt: Int = 0,
    val pinAttemptsMax: Int = 3,
    /** O botão "Sim, aceitou" só fica ativo depois que o Android entregou o PIN. */
    val pinDelivered: Boolean = false,
    /** Primeiro uso: exibir os termos (checklist de 5 itens) antes de conectar. */
    val needsTerms: Boolean = true,
)

data class ManeuverUi(
    /** Código do ícone do Tripper (desenhado por ui/tripper/IconGlyphs). */
    val iconCode: Int,
    val instruction: String,
    val distanceText: String,
    val streetName: String? = null,
)

data class RadarUi(
    val enabled: Boolean,
    /** Cobertura dos dados ("Dados de radar atualizados", "Sem dados de radar para esta rota"…). */
    val coverageText: String,
    /** Alerta ativo, ex.: "Radar 60 km/h a 280 m". Nulo sem alerta. */
    val alertText: String? = null,
    val alertDistanceM: Int? = null,
    val kindText: String = "Radar",
    val limitKmh: Int? = null,
    val limitStatus: String = "Limite indisponível",
    val sourceText: String = "OpenStreetMap",
    val directionUnknown: Boolean = true,
    val attribution: String = "© colaboradores do OpenStreetMap (ODbL)",
    /** Aviso fixo de cobertura limitada. */
    val disclaimer: String = "Cobertura limitada: a ausência de alerta não significa ausência de fiscalização.",
)

enum class NavPhaseUi(val label: String) {
    IDLE("Sem navegação"),
    ROUTING("Calculando rota…"),
    NAVIGATING("Navegando"),
    OFF_ROUTE("Fora da rota"),
    REROUTING("Recalculando…"),
    ARRIVED("Você chegou"),
    FAILED("Falha na navegação"),
}

data class NavUi(
    val phase: NavPhaseUi = NavPhaseUi.IDLE,
    val destination: DestinationUi? = null,
    val current: ManeuverUi? = null,
    val next: ManeuverUi? = null,
    val remainingDistanceText: String? = null,
    val remainingTimeText: String? = null,
    val arrivalText: String? = null,
    val gps: GpsUi = GpsUi.SEARCHING,
    val gpsAccuracyText: String? = null,
    /** Limite do trecho, independente do equipamento de fiscalização. */
    val roadLimitKmh: Int? = null,
    val voiceStatusText: String? = null,
    val tripper: TripperUi = TripperUi(),
    val radar: RadarUi? = null,
    /** Mensagem curta de estado/erro (sem internet, recalculando…). */
    val message: String? = null,
    /** Trajeto simulado (teste em casa): a posição não vem do GPS. */
    val simulated: Boolean = false,
    /** Prévia do que o Tripper mostra (ui/tripper/TripperDisplay). */
    val tripperPreview: TripperPreview? = null,
)

data class HomeUi(
    val destination: DestinationUi? = null,
    val search: SearchUi = SearchUi(),
    val route: RouteSummaryUi? = null,
    val routing: Boolean = false,
    val routeError: String? = null,
    /** Há chave do provedor de rotas configurada? Sem ela, mostrar atalho para Configurações. */
    val providerConfigured: Boolean = false,
    val canStart: Boolean = false,
    val navigationActive: Boolean = false,
    val recent: List<DestinationUi> = emptyList(),
    val tripper: TripperUi = TripperUi(),
    val gps: GpsUi = GpsUi.SEARCHING,
    val planner: PlannerUi = PlannerUi(),
)

data class RouteOptionUi(
    val index: Int,
    val points: List<dev.hunterlab.core.geo.GeoPoint>,
    val summary: RouteSummaryUi,
    val roads: String? = null,
)

data class MapRadarUi(
    val id: String, val point: dev.hunterlab.core.geo.GeoPoint,
    val limitKmh: Int?, val limitStatus: String, val source: String, val directionUnknown: Boolean,
)

data class PlannerUi(
    val mapConfigured: Boolean = false,
    val mapRevision: Int = 0,
    val currentPosition: dev.hunterlab.core.geo.GeoPoint? = null,
    val alternatives: List<RouteOptionUi> = emptyList(),
    val selectedIndex: Int = 0,
    val radars: List<MapRadarUi> = emptyList(),
    val radarStatus: String = "Selecione uma rota para consultar radares.",
    val notice: String? = null,
    val favorites: List<DestinationUi> = emptyList(),
)

data class PermissionsUi(
    val location: Boolean = false,
    val notifications: Boolean = false,
    val bluetooth: Boolean = false,
)

data class SettingsUi(
    val apiKeyConfigured: Boolean = false,
    val mapKeyConfigured: Boolean = false,
    /** Ex.: "••••••3f2a". Nunca a chave inteira. */
    val apiKeyMasked: String? = null,
    val dayNight: DayNightMode = DayNightMode.AUTO,
    val bottomLine: BottomLineMode = BottomLineMode.TOTAL_DISTANCE,
    val radarEnabled: Boolean = true,
    val radarOnTripper: Boolean = true,
    val radarVoiceEnabled: Boolean = false,
    val radarSearchM: Int = 700,
    val radarAlertM: Int = 300,
    val permissions: PermissionsUi = PermissionsUi(),
    val version: String = "",
    val routingAttribution: String = "",
)

/** Ações disparadas pela interface. Implementadas pelo NavigationController (via ViewModel). */
interface NavActions {
    // Destino e rota
    fun search(query: String)
    fun selectDestination(destination: DestinationUi)
    /** Texto "lat, lon" digitado ou colado. Devolve erro legível ou null se aceito. */
    fun setDestinationFromText(text: String): String?
    fun clearDestination()
    fun previewRoute()
    fun selectRoute(index: Int) = Unit
    fun toggleFavorite(destination: DestinationUi) = Unit
    fun openPlanner() = Unit
    fun closePlanner() = Unit
    /** Ephemeral SDK configuration; never rendered as text or saved in UI state. */
    fun mapStyleJson(): String? = null
    fun setMapKey(key: String) = Unit
    fun clearMapKey() = Unit

    // Navegação
    fun startNavigation(simulated: Boolean = false)
    fun stopNavigation()

    // Tripper (cada passo exige toque do usuário; PIN nunca é tentado automaticamente)
    fun acceptTerms()
    fun connectTripper()
    fun requestPin()
    fun submitPin(pin: String)
    fun confirmPinAccepted()
    fun reportPinRejected()
    fun interruptTripper()

    // Configurações
    fun setApiKey(key: String)
    fun clearApiKey()
    fun setDayNight(mode: DayNightMode)
    fun setBottomLine(mode: BottomLineMode)
    fun setRadarEnabled(enabled: Boolean)
    fun setRadarVoiceEnabled(enabled: Boolean)
    fun setRadarOnTripper(enabled: Boolean)
    fun setRadarThresholds(searchM: Int, alertM: Int)
    fun refreshPermissions()
}

/** O que a raiz da interface recebe. */
class NavBindings(
    val home: StateFlow<HomeUi>,
    val nav: StateFlow<NavUi>,
    val settings: StateFlow<SettingsUi>,
    val actions: NavActions,
)
