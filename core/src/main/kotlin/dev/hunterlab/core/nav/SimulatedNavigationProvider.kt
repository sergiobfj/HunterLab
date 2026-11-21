package dev.hunterlab.core.nav

import dev.hunterlab.protocol.Maneuver
import dev.hunterlab.protocol.Side
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Provedor 100% simulado, controlado pela UI. Determinístico: o tempo vem de [clock] e as
 * telas transitórias (rota iniciada, recalculando, chegada) expiram em [tick].
 */
class SimulatedNavigationProvider(private val clock: () -> Long) : NavigationProvider {

    override val info = ProviderInfo(ID, "Simulador", ProviderKind.SIMULATED, ProviderAvailability.Available)

    private val state = MutableStateFlow(
        NavigationSnapshot(
            status = NavigationStatus.IDLE,
            maneuver = Maneuver.Turn(Side.RIGHT),
            distanceToManeuverM = 250,
            nextManeuver = null,
            remainingDistanceM = 4_200,
            remainingTimeMin = 12,
            sourceId = ID,
            simulated = true,
        ),
    )
    override val snapshots: StateFlow<NavigationSnapshot> = state.asStateFlow()

    private var transientUntilMs: Long? = null
    private var demo: DemoPlayback? = null

    val isDemoRunning: Boolean get() = demo != null
    val demoProgress: Double get() = demo?.let { it.traveledM / it.route.totalLengthM } ?: 0.0

    override fun start() = startRoute()
    override fun stop() = stopRoute()

    /** Mostra "rota iniciada" por [ROUTE_STARTED_MS] e depois navega com os valores atuais. */
    fun startRoute() {
        demo = null
        setTransient(NavigationStatus.ROUTE_STARTED, ROUTE_STARTED_MS)
    }

    /** Só faz sentido com rota ativa; devolve `false` (sem efeito) em [NavigationStatus.IDLE]. */
    fun recalculate(): Boolean {
        if (state.value.status == NavigationStatus.IDLE) return false
        setTransient(NavigationStatus.RECALCULATING, RECALCULATING_MS)
        return true
    }

    /** Chegada: ícone de destino a 0 m por [ARRIVED_HOLD_MS], depois volta ao relógio nativo. */
    fun arrive() {
        demo = null
        state.update {
            it.copy(
                status = NavigationStatus.ARRIVED,
                maneuver = Maneuver.Arrive(),
                distanceToManeuverM = 0,
                nextManeuver = null,
                remainingDistanceM = 0,
                remainingTimeMin = 0,
            )
        }
        transientUntilMs = clock() + ARRIVED_HOLD_MS
    }

    fun stopRoute() {
        demo = null
        transientUntilMs = null
        state.update { it.copy(status = NavigationStatus.IDLE) }
    }

    /** Escolher uma manobra sem rota ativa já inicia a navegação simulada (sem tela de início). */
    fun setManeuver(maneuver: Maneuver) {
        demo = null
        state.update { it.copy(maneuver = maneuver, status = activeStatus(it.status)) }
    }

    fun setDistanceToManeuver(meters: Int) {
        require(meters >= 0) { "Distância negativa: $meters" }
        demo = null
        state.update { it.copy(distanceToManeuverM = meters, status = activeStatus(it.status)) }
    }

    fun setNextManeuver(maneuver: Maneuver?) {
        demo = null
        state.update { it.copy(nextManeuver = maneuver) }
    }

    fun setRemaining(distanceM: Int?, timeMin: Int?) {
        require(distanceM == null || distanceM >= 0) { "Distância restante negativa" }
        require(timeMin == null || timeMin >= 0) { "Tempo restante negativo" }
        state.update { it.copy(remainingDistanceM = distanceM, remainingTimeMin = timeMin) }
    }

    /** Expira telas transitórias. Devolve `true` se o estado mudou. */
    fun tick(): Boolean {
        val until = transientUntilMs ?: return false
        if (clock() < until) return false
        transientUntilMs = null
        val before = state.value.status
        state.update {
            it.copy(status = if (it.status == NavigationStatus.ARRIVED) NavigationStatus.IDLE else NavigationStatus.NAVIGATING)
        }
        return state.value.status != before
    }

    // ---------- Rota demonstrativa ----------

    fun startDemo(route: SimulatedRoute, speedKmh: Int) {
        require(speedKmh > 0) { "Velocidade deve ser positiva" }
        demo = DemoPlayback(route, speedKmh, 0.0)
        applyDemo()
        setTransient(NavigationStatus.ROUTE_STARTED, ROUTE_STARTED_MS)
    }

    /** Avança a rota demonstrativa. Ao terminar, chama [arrive]. */
    fun advanceDemo(meters: Double) {
        val d = demo ?: return
        if (state.value.status == NavigationStatus.ROUTE_STARTED || state.value.status == NavigationStatus.RECALCULATING) return
        d.traveledM = (d.traveledM + meters).coerceAtMost(d.route.totalLengthM.toDouble())
        if (d.traveledM >= d.route.totalLengthM) {
            arrive()
            return
        }
        applyDemo()
    }

    private fun applyDemo() {
        val d = demo ?: return
        val pos = d.route.positionAt(d.traveledM)
        val remaining = d.route.totalLengthM - d.traveledM
        state.update {
            it.copy(
                maneuver = pos.maneuver,
                distanceToManeuverM = pos.distanceToManeuverM,
                nextManeuver = pos.nextManeuver,
                remainingDistanceM = remaining.roundToInt(),
                remainingTimeMin = ceil(remaining / (d.speedKmh / 3.6) / 60.0).toInt(),
            )
        }
    }

    private fun setTransient(status: NavigationStatus, durationMs: Long) {
        transientUntilMs = clock() + durationMs
        state.update { it.copy(status = status) }
    }

    private fun activeStatus(current: NavigationStatus): NavigationStatus = when (current) {
        NavigationStatus.IDLE, NavigationStatus.ARRIVED -> {
            transientUntilMs = null
            NavigationStatus.NAVIGATING
        }
        else -> current
    }

    private class DemoPlayback(val route: SimulatedRoute, val speedKmh: Int, var traveledM: Double)

    companion object {
        const val ID = "simulated"
        const val ROUTE_STARTED_MS = 2_000L
        const val RECALCULATING_MS = 3_000L
        const val ARRIVED_HOLD_MS = 5_000L
    }
}

/** Futuras integrações (notificações de navegação, Android Auto, etc.). Indisponível na 0.1.0. */
class FutureNavigationProvider : NavigationProvider {
    override val info = ProviderInfo(
        id = "future",
        displayName = "Provedor futuro",
        kind = ProviderKind.EXTERNAL,
        availability = ProviderAvailability.Unavailable(
            "Nenhuma integração externa nesta versão. Waze e outros apps estão fora do escopo da 0.1.0.",
        ),
    )
    override val snapshots: StateFlow<NavigationSnapshot> =
        MutableStateFlow(NavigationSnapshot(sourceId = info.id, simulated = false)).asStateFlow()

    override fun start() = Unit // intencionalmente sem efeito
    override fun stop() = Unit
}
