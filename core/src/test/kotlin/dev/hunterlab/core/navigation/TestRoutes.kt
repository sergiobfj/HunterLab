package dev.hunterlab.core.navigation

import dev.hunterlab.core.geo.Geo
import dev.hunterlab.core.geo.GeoPoint
import dev.hunterlab.core.geo.Polyline
import dev.hunterlab.core.route.Route
import dev.hunterlab.core.route.RouteStep
import dev.hunterlab.protocol.Maneuver
import dev.hunterlab.protocol.Side
import kotlin.math.PI
import kotlin.math.cos

/** Rotas sintéticas numa grade local fictícia (x = leste, y = norte, em metros). */
object TestRoutes {
    val ORIGIN = GeoPoint(-23.5, -46.6)
    private const val K = Geo.EARTH_RADIUS_M * PI / 180.0
    private val COS = cos(ORIGIN.lat * PI / 180.0)

    fun pt(x: Double, y: Double) = GeoPoint(ORIGIN.lat + y / K, ORIGIN.lon + x / (K * COS))

    /**
     * Rota com manobras nos vértices de [maneuvers]; o vértice 0 vira Partida e o último, Chegada.
     * Durações = comprimento / [speedMps], salvo [durations] (uma por passo).
     */
    fun build(
        vertices: List<Pair<Double, Double>>,
        maneuvers: Map<Int, Maneuver> = emptyMap(),
        speedMps: Double = 10.0,
        durations: List<Double>? = null,
        stepLengths: List<Double>? = null,
        distanceM: Double? = null,
        providerId: String = "teste",
    ): Route {
        val line = Polyline(vertices.map { (x, y) -> pt(x, y) })
        val last = vertices.lastIndex
        val idx = (listOf(0) + maneuvers.keys.filter { it in 1 until last }.sorted() + listOf(last)).distinct()
        val steps = idx.mapIndexed { k, v ->
            val m = when (v) {
                0 -> Maneuver.Depart
                last -> maneuvers[v] ?: Maneuver.Arrive()
                else -> maneuvers.getValue(v)
            }
            val at = line.cumulative[v]
            val end = line.cumulative[idx.getOrElse(k + 1) { last }]
            RouteStep(
                maneuver = m,
                atAlongM = at,
                lengthM = stepLengths?.get(k) ?: (end - at),
                durationS = durations?.get(k) ?: ((end - at) / speedMps),
                streetName = null,
                text = m.label,
            )
        }
        return Route(
            polyline = line,
            steps = steps,
            distanceM = distanceM ?: line.lengthM,
            durationS = steps.sumOf { it.durationS },
            providerId = providerId,
            attribution = "teste",
        )
    }

    /** Leste 500 m, vira à direita, sul 400 m, chegada. Comprimento ≈ 900 m. */
    fun lRoute(durations: List<Double>? = null) = build(
        listOf(0.0 to 0.0, 500.0 to 0.0, 500.0 to -400.0),
        mapOf(1 to Maneuver.Turn(Side.RIGHT)),
        durations = durations,
    )

    /** Fix sobre a rota a [along] metros, deslocado [rightM] para a direita da via. Rumo = o da via. */
    fun fixAlong(
        route: Route,
        along: Double,
        timeMs: Long,
        rightM: Double = 0.0,
        speed: Float? = 10f,
        accuracy: Float? = 5f,
        bearing: Float? = null,
    ): LocationFix {
        val line = route.polyline
        val roadBearing = line.bearingAt(along)
        val base = line.pointAt(along)
        val p = if (rightM == 0.0) {
            base
        } else {
            RouteDriveSimulator.destination(base, roadBearing + if (rightM > 0) 90.0 else -90.0, kotlin.math.abs(rightM))
        }
        return LocationFix(p, accuracy, bearing ?: roadBearing.toFloat(), speed, timeMs)
    }

    fun fixAt(x: Double, y: Double, timeMs: Long, bearing: Float? = null, speed: Float? = null, accuracy: Float? = 5f) =
        LocationFix(pt(x, y), accuracy, bearing, speed, timeMs)
}
