package dev.hunterlab.core.route

import dev.hunterlab.core.geo.Geo
import dev.hunterlab.core.geo.GeoPoint
import kotlin.math.abs

sealed interface RouteChoicesResult {
    data class Success(val routes: List<Route>, val notice: String? = null) : RouteChoicesResult
    data class Failure(val failure: RouteResult.Failure) : RouteChoicesResult
}

object RouteChoices {
    /** Remove only almost identical geometries, never manufacture alternative paths. */
    fun distinct(routes: List<Route>): List<Route> = routes.fold(emptyList<Route>()) { accepted, route ->
        if (accepted.any { almostSame(it, route) }) accepted else accepted + route
    }.take(3)

    private fun almostSame(a: Route, b: Route): Boolean {
        if (abs(a.distanceM - b.distanceM) > 30) return false
        // Compare at equal progress, so loops and reversed itineraries remain distinguishable.
        return (0..40).all { i -> Geo.distanceM(a.polyline.pointAt(a.polyline.lengthM * i / 40),
            b.polyline.pointAt(b.polyline.lengthM * i / 40)) <= 8.0 }
    }
}

/** Exact selected Route is retained, including instructions/speed details; no network on selection. */
class PreparedRoutePlan(val routes: List<Route>, val origin: GeoPoint, val createdAtMs: Long) {
    init { require(routes.isNotEmpty() && routes.size <= 3) }
    var selectedIndex: Int = 0
        private set
    val selected: Route get() = routes[selectedIndex]
    fun select(index: Int): Boolean {
        if (index !in routes.indices) return false
        selectedIndex = index
        return true
    }
    fun usableAt(position: GeoPoint?, nowMs: Long, simulated: Boolean): Boolean = simulated ||
        (position != null && nowMs - createdAtMs in 0..180_000 && Geo.distanceM(origin, position) < 150)
}

