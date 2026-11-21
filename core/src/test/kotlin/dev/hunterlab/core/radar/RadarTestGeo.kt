package dev.hunterlab.core.radar

import dev.hunterlab.core.geo.Geo
import dev.hunterlab.core.geo.GeoPoint
import dev.hunterlab.core.geo.Polyline
import kotlin.math.PI
import kotlin.math.cos

/** Geometria local em metros a partir de uma origem **fictícia** (nenhum radar real). */
internal object RadarTestGeo {
    val ORIGIN = GeoPoint(-10.5, -48.3)
    private const val M_PER_DEG = Geo.EARTH_RADIUS_M * PI / 180.0

    /** Ponto a [eastM] metros a leste e [northM] metros ao norte da origem. */
    fun at(eastM: Double, northM: Double): GeoPoint = GeoPoint(
        ORIGIN.lat + northM / M_PER_DEG,
        ORIGIN.lon + eastM / (M_PER_DEG * cos(ORIGIN.lat * PI / 180.0)),
    )

    fun route(vararg pts: Pair<Number, Number>): Polyline =
        Polyline(pts.map { at(it.first.toDouble(), it.second.toDouble()) })

    fun camera(
        n: Int,
        eastM: Double,
        northM: Double,
        directionDeg: Double? = null,
        speed: Int? = 60,
        kind: RadarKind = RadarKind.FIXED,
    ): RadarCamera = RadarCamera(RadarCamera.osmNodeId(n.toLong()), at(eastM, northM), kind, speed, directionDeg)
}
