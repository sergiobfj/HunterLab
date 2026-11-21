package dev.hunterlab.core.navigation

import dev.hunterlab.core.geo.Geo
import dev.hunterlab.core.geo.GeoPoint
import dev.hunterlab.core.geo.Projection
import dev.hunterlab.core.route.Route
import dev.hunterlab.protocol.Maneuver
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Limiares do acompanhamento de rota. Distâncias em metros, tempos em ms, velocidades em m/s. */
data class TrackerConfig(
    /** Fix com precisão pior que isto é ignorado (marcado como impreciso). */
    val maxAccuracyM: Double = 50.0,
    /** Janela de busca atrás do progresso (tremor do GPS); nunca faz o progresso voltar. */
    val searchBackM: Double = 50.0,
    /** Janela à frente = base + velocidade máxima plausível × tempo desde o último fix aceito. */
    val searchAheadBaseM: Double = 80.0,
    val maxPlausibleSpeedMps: Double = 45.0,
    /** Janela à frente no primeiro fix de uma rota (ex.: rota recalculada chega com atraso). */
    val initialSearchAheadM: Double = 1_000.0,
    /** Rumo do GPS só é usado acima desta velocidade. */
    val headingMinSpeedMps: Double = 3.0,
    /** Trechos com rumo diferente do GPS por mais que isto recebem [headingPenaltyM] na escolha. */
    val headingMismatchDeg: Double = 60.0,
    val headingPenaltyM: Double = 25.0,
    /** Desempate por continuidade: custo (m) por metro de distância do progresso esperado. */
    val continuityWeight: Double = 0.02,
    /** Histerese de manobra: só passa para a seguinte após andar isto além da manobra. */
    val maneuverPassedM: Double = 15.0,
    /** Desvio: offset > max([offRouteMinM], [offRouteAccuracyFactor] × precisão). */
    val offRouteMinM: Double = 35.0,
    val offRouteAccuracyFactor: Double = 1.5,
    /** Confirma desvio com [offRouteFixCount] fixes suspeitos seguidos, ou ≥ 2 seguidos durante [offRouteMinDurationMs]. */
    val offRouteFixCount: Int = 3,
    val offRouteMinDurationMs: Long = 5_000,
    /** Volta à rota: offset < [backOnRouteM] (sem contramão) em [backOnRouteFixCount] fixes seguidos. */
    val backOnRouteM: Double = 25.0,
    val backOnRouteFixCount: Int = 2,
    /** Contramão: rumo do GPS a mais que isto do rumo da via, acima de [wrongWayMinSpeedMps]. */
    val wrongWayAngleDeg: Double = 120.0,
    val wrongWayMinSpeedMps: Double = 3.0,
    /** Chegada: restante < [arrivalRadiusM], ou passou do fim / está a menos disso do destino. */
    val arrivalRadiusM: Double = 25.0,
    /** "Passou do fim"/"perto do destino" só valem quando o restante já era menor que isto. */
    val arrivalApproachM: Double = 150.0,
) {
    init {
        require(maxAccuracyM > 0 && offRouteMinM > 0 && backOnRouteM > 0 && arrivalRadiusM > 0) { "Limiares devem ser positivos" }
        require(backOnRouteM <= offRouteMinM) { "Volta à rota precisa de histerese (backOnRouteM ≤ offRouteMinM)" }
        require(offRouteFixCount >= 1 && backOnRouteFixCount >= 1) { "Contagens devem ser ≥ 1" }
    }
}

/** Progresso na rota num instante. Distâncias em metros, sempre ≥ 0. */
data class TrackState(
    /** Metros percorridos ao longo da geometria (monotônico). */
    val progressM: Double,
    /** Distância do último fix preciso até a rota (null antes do primeiro). */
    val offsetM: Double?,
    val snappedPoint: GeoPoint?,
    /** Passo em que se está (o trecho depois da manobra [currentStepIndex]). */
    val currentStepIndex: Int,
    /** Índice em [Route.steps] da próxima manobra; null = só resta chegar ao fim da linha. */
    val nextStepIndex: Int?,
    val nextManeuver: Maneuver,
    /** Manobra depois da próxima (seta pequena do Tripper). */
    val followingManeuver: Maneuver?,
    val distanceToNextManeuverM: Double,
    val remainingDistanceM: Double,
    val remainingTimeS: Double,
    /** Desvio confirmado (inclui contramão). */
    val offRoute: Boolean,
    /** Último fix preciso em contramão. */
    val wrongWay: Boolean,
    /** Fixes suspeitos seguidos (ainda não confirmados). */
    val suspectFixCount: Int,
    /** O último fix recebido foi ignorado por precisão ruim. */
    val lastFixImprecise: Boolean,
    val arrived: Boolean,
    /** Fixes que atualizaram o progresso. */
    val acceptedFixCount: Int,
    val lastFixTimeMs: Long?,
)

/**
 * Acompanha o progresso numa [Route] a partir de [LocationFix]es. Puro: sem relógio, sem threads.
 *
 * - Projeta só numa **janela** de segmentos ao redor do progresso (para trás [TrackerConfig.searchBackM];
 *   para frente proporcional ao tempo desde o último fix aceito), com penalidade de rumo e desempate por
 *   continuidade — retornos e laços que passam perto não fazem o progresso "pular".
 * - O progresso nunca diminui; só fixes "na rota" o atualizam.
 * - A manobra seguinte só passa a valer depois de andar [TrackerConfig.maneuverPassedM] além da atual
 *   (ou metade da distância até a seguinte, se for menor); nunca volta.
 */
class RouteTracker(val route: Route, val config: TrackerConfig = TrackerConfig()) {
    private val line = route.polyline
    private val steps = route.steps
    private val stepEnd = DoubleArray(steps.size) { j ->
        val end = if (j + 1 < steps.size) steps[j + 1].atAlongM else line.lengthM
        max(end, steps[j].atAlongM)
    }
    private val totalStepDurationS = steps.sumOf { it.durationS }
    private val arrival: Maneuver = steps.last().maneuver as? Maneuver.Arrive ?: Maneuver.Arrive()

    private var progress = 0.0
    private var nextIdx = 1
    private var lastFixTime: Long? = null
    private var lastAcceptedTime: Long? = null
    private var offRoute = false
    private var suspectCount = 0
    private var suspectSince: Long? = null
    private var backCount = 0
    private var arrived = false
    private var accepted = 0

    var state: TrackState = buildState(offset = null, snapped = null, wrongWay = false, imprecise = false)
        private set

    /** Processa um fix. Fixes com tempo anterior ou igual ao último são ignorados (estado inalterado). */
    fun update(fix: LocationFix): TrackState {
        val last = lastFixTime
        if (last != null && fix.timeMs <= last) return state
        lastFixTime = fix.timeMs
        if (arrived) {
            state = state.copy(lastFixTimeMs = fix.timeMs, lastFixImprecise = false)
            return state
        }
        if (!fix.isAccurate(config.maxAccuracyM)) {
            state = state.copy(lastFixImprecise = true, lastFixTimeMs = fix.timeMs)
            return state
        }

        val accuracy = fix.accuracyM?.toDouble() ?: 0.0
        val dtS = lastAcceptedTime?.let { (fix.timeMs - it).coerceAtLeast(0) / 1000.0 }
        val speed = fix.speedMps?.takeIf { it.isFinite() && it >= 0f }?.toDouble() ?: 0.0
        val best = bestCandidate(fix.point, fix.reliableBearing(config.headingMinSpeedMps), dtS, speed)
        val offset = best.offsetM
        val wrongBearing = fix.reliableBearing(config.wrongWayMinSpeedMps)
        val wrongWay = wrongBearing != null &&
            Geo.angleDiff(wrongBearing, best.segmentBearingDeg) > config.wrongWayAngleDeg
        val threshold = max(config.offRouteMinM, config.offRouteAccuracyFactor * accuracy)
        val suspect = offset > threshold || wrongWay

        if (line.lengthM - progress <= config.arrivalApproachM && reachedEnd(fix.point, best)) {
            arrive()
            state = buildState(offset, best.point, wrongWay, imprecise = false)
            return state
        }

        val onRoute: Boolean
        if (!offRoute) {
            if (suspect) {
                if (suspectCount == 0) suspectSince = fix.timeMs
                suspectCount++
                val duration = fix.timeMs - (suspectSince ?: fix.timeMs)
                if (suspectCount >= config.offRouteFixCount ||
                    (suspectCount >= 2 && duration >= config.offRouteMinDurationMs)
                ) {
                    offRoute = true
                    backCount = 0
                }
                onRoute = false
            } else {
                suspectCount = 0
                suspectSince = null
                onRoute = true
            }
        } else {
            if (offset < config.backOnRouteM && !wrongWay) {
                backCount++
                onRoute = backCount >= config.backOnRouteFixCount
                if (onRoute) {
                    offRoute = false
                    suspectCount = 0
                    suspectSince = null
                    backCount = 0
                }
            } else {
                backCount = 0
                onRoute = false
            }
        }

        if (onRoute) {
            if (best.alongM > progress) progress = min(best.alongM, line.lengthM)
            lastAcceptedTime = fix.timeMs
            accepted++
            advanceSteps()
            if (line.lengthM - progress < config.arrivalRadiusM) arrive()
        }
        state = buildState(offset, best.point, wrongWay, imprecise = false)
        return state
    }

    private fun arrive() {
        arrived = true
        offRoute = false
        suspectCount = 0
        progress = line.lengthM
        nextIdx = steps.size
    }

    /** Chegou ao destino sem passar pela regra do restante: perto do ponto final ou além do fim da linha. */
    private fun reachedEnd(p: GeoPoint, best: Projection): Boolean {
        val end = line.points.last()
        if (Geo.distanceM(p, end) < config.arrivalRadiusM) return true
        val lastSeg = line.points.size - 2
        if (best.segmentIndex != lastSeg || best.alongM < line.lengthM - Route.ALONG_EPSILON_M) return false
        // Projeção "presa" no fim: o ponto está além do fim se fica à frente no rumo do último segmento.
        val beyond = Geo.angleDiff(best.segmentBearingDeg, Geo.bearingDeg(end, p)) < 90.0
        return beyond && best.offsetM <= config.offRouteMinM * 3
    }

    private fun bestCandidate(p: GeoPoint, heading: Double?, dtS: Double?, speed: Double): Projection {
        val ahead = if (dtS == null) config.initialSearchAheadM else config.searchAheadBaseM + config.maxPlausibleSpeedMps * dtS
        val from = segmentAt(progress - config.searchBackM)
        val to = max(from, segmentAt(progress + ahead))
        val expected = progress + speed * (dtS ?: 0.0)
        var best: Projection? = null
        var bestScore = Double.POSITIVE_INFINITY
        for (i in from..to) {
            val proj = line.project(p, i, i)
            var score = proj.offsetM + config.continuityWeight * abs(proj.alongM - expected)
            val segLen = line.cumulative[i + 1] - line.cumulative[i]
            if (heading != null && segLen > 0 && Geo.angleDiff(heading, proj.segmentBearingDeg) > config.headingMismatchDeg) {
                score += config.headingPenaltyM
            }
            if (score < bestScore) {
                best = proj
                bestScore = score
            }
        }
        return best!!
    }

    private fun segmentAt(alongM: Double): Int {
        val a = alongM.coerceIn(0.0, line.lengthM)
        val i = line.cumulative.binarySearch(a).let { if (it >= 0) it else -it - 2 }
        return i.coerceIn(0, line.points.size - 2)
    }

    private fun advanceSteps() {
        while (nextIdx < steps.size) {
            val at = steps[nextIdx].atAlongM
            val gap = if (nextIdx + 1 < steps.size) steps[nextIdx + 1].atAlongM - at else Double.POSITIVE_INFINITY
            val pass = min(config.maneuverPassedM, gap / 2)
            if (progress >= at + pass && progress > at) nextIdx++ else break
        }
    }

    private fun remainingTimeS(): Double {
        if (arrived) return 0.0
        val remaining = max(0.0, line.lengthM - progress)
        if (totalStepDurationS <= 0.0) {
            return if (line.lengthM > 0) route.durationS * remaining / line.lengthM else 0.0
        }
        var t = 0.0
        for (j in steps.indices) {
            val a = steps[j].atAlongM
            val e = stepEnd[j]
            val len = e - a
            t += when {
                len <= 0.0 -> if (a >= progress) steps[j].durationS else 0.0
                progress <= a -> steps[j].durationS
                progress >= e -> 0.0
                else -> steps[j].durationS * (e - progress) / len
            }
        }
        return t
    }

    private fun buildState(offset: Double?, snapped: GeoPoint?, wrongWay: Boolean, imprecise: Boolean): TrackState {
        val nextStep = if (arrived) null else steps.getOrNull(nextIdx)
        val nextManeuver = nextStep?.maneuver ?: arrival
        val nextAlong = nextStep?.atAlongM ?: line.lengthM
        return TrackState(
            progressM = progress,
            offsetM = offset,
            snappedPoint = snapped,
            currentStepIndex = (nextIdx - 1).coerceIn(0, steps.lastIndex),
            nextStepIndex = nextStep?.let { nextIdx },
            nextManeuver = nextManeuver,
            followingManeuver = if (arrived) null else steps.getOrNull(nextIdx + 1)?.maneuver,
            distanceToNextManeuverM = if (arrived) 0.0 else max(0.0, nextAlong - progress),
            remainingDistanceM = if (arrived) 0.0 else max(0.0, line.lengthM - progress),
            remainingTimeS = remainingTimeS(),
            offRoute = offRoute,
            wrongWay = wrongWay,
            suspectFixCount = suspectCount,
            lastFixImprecise = imprecise,
            arrived = arrived,
            acceptedFixCount = accepted,
            lastFixTimeMs = lastFixTime,
        )
    }
}
