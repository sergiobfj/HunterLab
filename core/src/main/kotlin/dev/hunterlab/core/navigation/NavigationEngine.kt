package dev.hunterlab.core.navigation

import dev.hunterlab.core.geo.GeoPoint
import dev.hunterlab.core.route.Route
import dev.hunterlab.core.route.RouteFailureKind
import dev.hunterlab.core.route.RouteRequest
import dev.hunterlab.core.route.RouteResult
import kotlin.math.ceil
import kotlin.math.max

enum class EngineStatus(val label: String) {
    IDLE("Sem rota"),
    ROUTING("Calculando rota"),
    NAVIGATING("Navegando"),
    OFF_ROUTE("Fora da rota"),
    REROUTING("Recalculando"),
    ARRIVED("Chegada"),
    FAILED("Falha"),
}

/** Limiares do motor. Tempos em ms (relógio injetado `nowMs`). */
data class NavigationConfig(
    val tracker: TrackerConfig = TrackerConfig(),
    /** Sem fix preciso há mais que isto → `gpsLost` (e nenhum pedido de rota é feito). */
    val gpsLostAfterMs: Long = 10_000,
    /** Intervalo mínimo entre dois pedidos de rota (inclusive novas tentativas). */
    val minRerouteIntervalMs: Long = 10_000,
    /** Espera após a n-ésima falha seguida (o último valor se repete). */
    val backoffMs: List<Long> = listOf(5_000, 10_000, 20_000, 40_000, 60_000),
    /** Teto de pedidos numa janela deslizante (protege a cota da API). */
    val maxRequestsPerWindow: Int = 6,
    val requestWindowMs: Long = 5 * 60_000,
    /** Pedido sem resposta após isto conta como falha TIMEOUT (o app deve cancelar o pedido). */
    val requestTimeoutMs: Long = 30_000,
    /** `sourceId` do snapshot quando ainda não há rota (com rota, usa o `providerId`). */
    val sourceId: String = "hunterlab-route",
) {
    init {
        require(backoffMs.isNotEmpty() && backoffMs.all { it >= 0 }) { "Backoff inválido" }
        require(maxRequestsPerWindow >= 1 && requestWindowMs > 0) { "Janela de pedidos inválida" }
        require(gpsLostAfterMs > 0 && requestTimeoutMs > 0 && minRerouteIntervalMs >= 0) { "Tempos inválidos" }
    }
}

/** O que o app deve fazer após cada entrada. */
sealed interface NavigationEffect {
    /** Chamar o provedor (em IO) e devolver o resultado com [requestId] em [NavigationEngine.onRouteResult]. */
    data class RequestRoute(val requestId: Long, val request: RouteRequest) : NavigationEffect

    /** O resultado de [requestId] não interessa mais (pode cancelar a chamada; se chegar, será ignorado). */
    data class CancelRequest(val requestId: Long) : NavigationEffect
}

/** Estado observável do motor (imutável). */
data class NavigationState(
    val status: EngineStatus = EngineStatus.IDLE,
    val destination: GeoPoint? = null,
    val route: Route? = null,
    val track: TrackState? = null,
    /** Recálculos bem-sucedidos desde o [NavigationEngine.start]. */
    val rerouteCount: Int = 0,
    val lastFailure: RouteResult.Failure? = null,
    /** Sem fix preciso há mais que [NavigationConfig.gpsLostAfterMs]. */
    val gpsLost: Boolean = false,
    val pendingRequestId: Long? = null,
    /** Quando o próximo pedido poderá sair (só enquanto se espera para pedir). */
    val nextRequestAtMs: Long? = null,
    /** Fixes ignorados por estarem fora de ordem/duplicados. */
    val ignoredFixCount: Int = 0,
    /** Texto curto em pt-BR para a UI. */
    val message: String = EngineStatus.IDLE.label,
    val sourceId: String = "hunterlab-route",
)

/**
 * Máquina de estados de navegação curva a curva. Pura: tempo injetado, sem corrotinas nem threads, sem rede
 * (pede rotas devolvendo [NavigationEffect.RequestRoute]). Não é thread-safe: chame de um único executor.
 *
 * Regras:
 * - O 1º pedido só sai com um fix preciso recente.
 * - Recálculo só com desvio **confirmado** pelo [RouteTracker] (nunca por fix), respeitando intervalo mínimo,
 *   backoff exponencial após falhas e teto por janela. Volta à rota antes da resposta cancela o pedido.
 * - Falha não fatal mantém a rota antiga; NOT_CONFIGURED/INVALID_KEY → FAILED sem novas tentativas;
 *   na rota inicial, NO_ROUTE/BAD_REQUEST também → FAILED (repetir o mesmo pedido não ajuda).
 * - Resultados de pedidos antigos e fixes com tempo ≤ ao último processado são ignorados.
 * - ARRIVED encerra os recálculos.
 */
class NavigationEngine(val config: NavigationConfig = NavigationConfig()) {

    private data class Pending(val id: Long, val sentAtMs: Long, val reroute: Boolean)

    private var status = EngineStatus.IDLE
    private var destination: GeoPoint? = null
    private var route: Route? = null
    private var tracker: RouteTracker? = null
    private var rerouteCount = 0
    private var lastFailure: RouteResult.Failure? = null
    private var consecutiveFailures = 0
    private var lastFailureAtMs: Long? = null
    private var lastRequestAtMs: Long? = null
    private val requestTimes = ArrayDeque<Long>()
    private var pending: Pending? = null
    private var nextRequestId = 1L
    private var lastValidFix: LocationFix? = null
    private var lastValidFixAtMs: Long? = null
    private var lastFixTimeMs: Long? = null
    private var sessionStartMs: Long? = null
    private var gpsLost = false
    private var ignoredFixes = 0
    private var clockMs: Long? = null

    var state: NavigationState = NavigationState(sourceId = config.sourceId)
        private set

    /** Inicia (ou reinicia) a navegação até [destination]. Cancela qualquer pedido anterior. */
    fun start(destination: GeoPoint, nowMs: Long): List<NavigationEffect> {
        val now = clock(nowMs)
        val effects = mutableListOf<NavigationEffect>()
        cancelPending(effects)
        this.destination = destination
        route = null
        tracker = null
        rerouteCount = 0
        lastFailure = null
        consecutiveFailures = 0
        lastFailureAtMs = null
        lastRequestAtMs = null
        requestTimes.clear()
        sessionStartMs = now
        status = EngineStatus.ROUTING
        evaluate(now, effects)
        publish(now)
        return effects
    }

    /** Encerra a navegação. Mantém só o último fix (para um próximo [start] imediato). */
    fun stop(): List<NavigationEffect> {
        val effects = mutableListOf<NavigationEffect>()
        cancelPending(effects)
        status = EngineStatus.IDLE
        destination = null
        route = null
        tracker = null
        lastFailure = null
        consecutiveFailures = 0
        sessionStartMs = null
        publish(clockMs ?: 0L)
        return effects
    }

    fun onFix(fix: LocationFix, nowMs: Long): List<NavigationEffect> {
        val now = clock(nowMs)
        val previous = lastFixTimeMs
        if (previous != null && fix.timeMs <= previous) {
            ignoredFixes++
            state = state.copy(ignoredFixCount = ignoredFixes)
            return emptyList()
        }
        lastFixTimeMs = fix.timeMs
        if (fix.isAccurate(config.tracker.maxAccuracyM)) {
            lastValidFix = fix
            lastValidFixAtMs = now
        }
        if (status in TRACKING) tracker?.update(fix)
        val effects = mutableListOf<NavigationEffect>()
        evaluate(now, effects)
        publish(now)
        return effects
    }

    fun onRouteResult(requestId: Long, result: RouteResult, nowMs: Long): List<NavigationEffect> {
        val now = clock(nowMs)
        val p = pending
        if (p == null || p.id != requestId) return emptyList() // obsoleto, cancelado ou fora de ordem
        pending = null
        when (result) {
            is RouteResult.Success -> {
                val newTracker = RouteTracker(result.route, config.tracker)
                route = result.route
                tracker = newTracker
                if (p.reroute) rerouteCount++
                consecutiveFailures = 0
                lastFailure = null
                lastFailureAtMs = null
                if (!isGpsLost(now)) lastValidFix?.let { newTracker.update(it) }
                status = EngineStatus.NAVIGATING
            }
            is RouteResult.Failure -> registerFailure(result, now)
        }
        val effects = mutableListOf<NavigationEffect>()
        evaluate(now, effects)
        publish(now)
        return effects
    }

    /** Chamar periodicamente (ex.: 1 Hz): perda de GPS, timeout de pedido e novas tentativas dependem do tempo. */
    fun onTick(nowMs: Long): List<NavigationEffect> {
        val now = clock(nowMs)
        val effects = mutableListOf<NavigationEffect>()
        evaluate(now, effects)
        publish(now)
        return effects
    }

    fun toSnapshot() = state.toSnapshot()

    // ---------------------------------------------------------------------------------------------

    private fun clock(nowMs: Long): Long {
        val c = clockMs
        val now = if (c == null) nowMs else max(c, nowMs) // relógio nunca volta
        clockMs = now
        return now
    }

    private fun isGpsLost(now: Long): Boolean {
        val reference = lastValidFixAtMs ?: sessionStartMs ?: return false
        return now - reference > config.gpsLostAfterMs
    }

    private fun evaluate(now: Long, effects: MutableList<NavigationEffect>) {
        gpsLost = isGpsLost(now)
        pending?.let {
            if (now - it.sentAtMs >= config.requestTimeoutMs) {
                effects += NavigationEffect.CancelRequest(it.id)
                pending = null
                registerFailure(
                    RouteResult.Failure(RouteFailureKind.TIMEOUT, "O serviço de rotas não respondeu a tempo."),
                    now,
                )
            }
        }
        when (status) {
            EngineStatus.ROUTING -> if (pending == null && canRequest(now)) effects += request(now)
            EngineStatus.NAVIGATING, EngineStatus.OFF_ROUTE, EngineStatus.REROUTING -> {
                val track = tracker?.state
                when {
                    track == null -> status = EngineStatus.ROUTING
                    track.arrived -> {
                        cancelPending(effects)
                        status = EngineStatus.ARRIVED
                    }
                    track.offRoute -> if (pending != null) {
                        status = EngineStatus.REROUTING
                    } else {
                        status = EngineStatus.OFF_ROUTE
                        if (canRequest(now)) {
                            effects += request(now)
                            status = EngineStatus.REROUTING
                        }
                    }
                    else -> {
                        cancelPending(effects) // voltou à rota antes da resposta
                        status = EngineStatus.NAVIGATING
                    }
                }
            }
            EngineStatus.IDLE, EngineStatus.ARRIVED, EngineStatus.FAILED -> Unit
        }
    }

    private fun registerFailure(failure: RouteResult.Failure, now: Long) {
        lastFailure = failure
        consecutiveFailures++
        lastFailureAtMs = now
        status = when {
            !failure.kind.retryable -> {
                route = null
                tracker = null
                EngineStatus.FAILED
            }
            route == null && failure.kind in PERMANENT_ON_FIRST_ROUTE -> EngineStatus.FAILED
            route == null -> EngineStatus.ROUTING
            tracker?.state?.offRoute == true -> EngineStatus.OFF_ROUTE
            else -> EngineStatus.NAVIGATING
        }
    }

    private fun canRequest(now: Long): Boolean =
        destination != null && lastValidFix != null && !gpsLost && now >= nextAllowedAt(now)

    private fun nextAllowedAt(now: Long): Long {
        var at = Long.MIN_VALUE
        lastRequestAtMs?.let { at = max(at, it + config.minRerouteIntervalMs) }
        val failedAt = lastFailureAtMs
        if (consecutiveFailures > 0 && failedAt != null) {
            val wait = config.backoffMs[(consecutiveFailures - 1).coerceAtMost(config.backoffMs.size - 1)]
            at = max(at, failedAt + wait)
        }
        while (requestTimes.isNotEmpty() && requestTimes.first() <= now - config.requestWindowMs) requestTimes.removeFirst()
        if (requestTimes.size >= config.maxRequestsPerWindow) at = max(at, requestTimes.first() + config.requestWindowMs)
        return at
    }

    private fun request(now: Long): NavigationEffect.RequestRoute {
        val fix = lastValidFix!!
        val id = nextRequestId++
        pending = Pending(id, now, reroute = route != null)
        lastRequestAtMs = now
        requestTimes.addLast(now)
        val heading = fix.reliableBearing(config.tracker.headingMinSpeedMps)
        return NavigationEffect.RequestRoute(id, RouteRequest(fix.point, destination!!, heading))
    }

    private fun cancelPending(effects: MutableList<NavigationEffect>) {
        pending?.let { effects += NavigationEffect.CancelRequest(it.id) }
        pending = null
    }

    private fun publish(now: Long) {
        val waiting = pending == null && destination != null &&
            (status == EngineStatus.ROUTING || status == EngineStatus.OFF_ROUTE)
        val nextAt = if (waiting) nextAllowedAt(now).takeIf { it > now } else null
        val track = tracker?.state
        state = NavigationState(
            status = status,
            destination = destination,
            route = route,
            track = track,
            rerouteCount = rerouteCount,
            lastFailure = lastFailure,
            gpsLost = gpsLost,
            pendingRequestId = pending?.id,
            nextRequestAtMs = nextAt,
            ignoredFixCount = ignoredFixes,
            message = message(now, track, nextAt),
            sourceId = route?.providerId ?: config.sourceId,
        )
    }

    private fun message(now: Long, track: TrackState?, nextAt: Long?): String {
        val retryIn = nextAt?.let { " Nova tentativa em ${ceil((it - now) / 1000.0).toLong()} s." } ?: ""
        return when (status) {
            EngineStatus.IDLE -> "Sem rota"
            EngineStatus.ROUTING -> when {
                pending != null -> "Calculando rota…"
                lastValidFix == null || gpsLost -> "Aguardando sinal de GPS…"
                lastFailure != null -> lastFailure!!.message + retryIn
                else -> "Calculando rota…"
            }
            EngineStatus.NAVIGATING -> when {
                gpsLost -> "Sinal de GPS perdido"
                track?.lastFixImprecise == true -> "GPS impreciso"
                lastFailure != null -> "Navegando (rota anterior mantida)"
                else -> "Navegando"
            }
            EngineStatus.OFF_ROUTE -> buildString {
                append(if (track?.wrongWay == true) "Contramão" else "Fora da rota")
                when {
                    gpsLost -> append(": aguardando GPS")
                    lastFailure != null -> append(": ").append(lastFailure!!.message).append(retryIn)
                    nextAt != null -> append(".").append(retryIn.replace("Nova tentativa", "Recálculo"))
                }
            }
            EngineStatus.REROUTING -> "Recalculando rota…"
            EngineStatus.ARRIVED -> "Você chegou ao destino"
            EngineStatus.FAILED -> lastFailure?.message ?: "Falha na navegação"
        }
    }

    companion object {
        private val TRACKING = setOf(EngineStatus.NAVIGATING, EngineStatus.OFF_ROUTE, EngineStatus.REROUTING)

        /** Na rota inicial, repetir o mesmo pedido não muda o resultado. */
        val PERMANENT_ON_FIRST_ROUTE = setOf(RouteFailureKind.NO_ROUTE, RouteFailureKind.BAD_REQUEST)
    }
}
