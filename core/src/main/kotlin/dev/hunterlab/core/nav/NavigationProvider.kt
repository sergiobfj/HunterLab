package dev.hunterlab.core.nav

import dev.hunterlab.protocol.Maneuver
import kotlinx.coroutines.flow.StateFlow

enum class NavigationStatus(val label: String) {
    IDLE("Sem rota"),
    ROUTE_STARTED("Rota iniciada"),
    NAVIGATING("Navegando"),
    RECALCULATING("Recalculando"),
    ARRIVED("Chegada"),
}

/**
 * Estado de navegação neutro, independente de quem o produz.
 * Os campos de manobra podem estar preenchidos mesmo em [NavigationStatus.IDLE] (rascunho do simulador);
 * é o status que decide o que vai para a tela.
 */
data class NavigationSnapshot(
    val status: NavigationStatus = NavigationStatus.IDLE,
    val maneuver: Maneuver? = null,
    val distanceToManeuverM: Int? = null,
    val nextManeuver: Maneuver? = null,
    val remainingDistanceM: Int? = null,
    val remainingTimeMin: Int? = null,
    val sourceId: String,
    val simulated: Boolean,
) {
    init {
        require(distanceToManeuverM == null || distanceToManeuverM >= 0) { "Distância negativa" }
        require(remainingDistanceM == null || remainingDistanceM >= 0) { "Distância restante negativa" }
        require(remainingTimeMin == null || remainingTimeMin >= 0) { "Tempo restante negativo" }
    }
}

enum class ProviderKind { SIMULATED, EXTERNAL }

sealed interface ProviderAvailability {
    data object Available : ProviderAvailability
    data class Unavailable(val reason: String) : ProviderAvailability
}

data class ProviderInfo(
    val id: String,
    val displayName: String,
    val kind: ProviderKind,
    val availability: ProviderAvailability,
)

/**
 * Fonte de eventos de navegação. O app conversa só com esta interface — nunca com um app
 * específico (Waze, Maps...). Novas fontes entram como novas implementações.
 */
interface NavigationProvider {
    val info: ProviderInfo
    val snapshots: StateFlow<NavigationSnapshot>
    fun start()
    fun stop()
}
