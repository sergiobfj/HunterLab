package dev.hunterlab.core.radar

import dev.hunterlab.core.geo.Geo

/** Conservative reconciliation, retaining ambiguous neighbours instead of inventing one physical device. */
object RadarFusion {
    const val IDENTITY_DISTANCE_M = 5.0
    const val AMBIGUITY_DISTANCE_M = 25.0

    private fun compatible(a: RadarCamera, b: RadarCamera): Boolean = a.kind == b.kind &&
        !(a.directionKnown && b.directionKnown && Geo.angleDiff(a.directionDeg!!, b.directionDeg!!) > 45.0)

    fun merge(osm: Collection<RadarCamera>, pardal: Collection<RadarCamera>): List<RadarCamera> {
        val left = collapseIds(osm)
        val right = collapseIds(pardal)
        fun neighbours(a: RadarCamera, all: List<RadarCamera>, radius: Double) = all.filter { b ->
            kotlin.math.abs(a.point.lat - b.point.lat) < radius / 110_000 &&
                compatible(a, b) && Geo.distanceM(a.point, b.point) <= radius
        }
        val consumed = mutableSetOf<String>()
        val merged = left.map { a ->
            val candidates = neighbours(a, right, IDENTITY_DISTANCE_M)
            val b = candidates.singleOrNull()?.takeIf { neighbours(it, left, IDENTITY_DISTANCE_M).size == 1 }
            if (b == null) a else {
                consumed += b.id
                a.copy(
                    speedLimit = RadarSpeedLimit.resolve(a.speedLimit.observations + b.speedLimit.observations),
                    directionDeg = a.directionDeg ?: b.directionDeg,
                    directionKnown = a.directionKnown || b.directionKnown,
                    source = listOf(a.source, b.source).distinct().joinToString(" · "),
                )
            }
        } + right.filter { it.id !in consumed }
        return merged.map { camera ->
            val nearby = neighbours(camera, merged, AMBIGUITY_DISTANCE_M)
            val values = nearby.mapNotNull { it.maxSpeedKmh }.toSet()
            if (values.size <= 1) camera else camera.copy(
                // Identity remains separate; the applicable numeric limit is unresolved here.
                speedLimit = RadarSpeedLimit.resolve(nearby.flatMap { it.speedLimit.observations }),
            )
        }.sortedBy { it.id }
    }

    private fun collapseIds(cameras: Collection<RadarCamera>): List<RadarCamera> = cameras.groupBy { it.id }.map { (_, group) ->
        val first = group.first()
        val degrees = group.filter { it.directionKnown }.mapNotNull { it.directionDeg }.distinct()
        first.copy(
            speedLimit = RadarSpeedLimit.resolve(group.flatMap { it.speedLimit.observations }),
            directionDeg = degrees.singleOrNull(), directionKnown = degrees.size == 1,
        )
    }
}
