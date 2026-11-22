package dev.hunterlab.core.route

/** Map information for a route segment. Never evidence of a camera's enforcement limit. */
data class RoadSpeedLimitSpan(val fromM: Double, val toM: Double, val kmh: Int?) {
    init {
        require(fromM.isFinite() && toM.isFinite() && fromM >= 0 && toM > fromM)
        require(kmh == null || kmh in 1..250)
    }
}
