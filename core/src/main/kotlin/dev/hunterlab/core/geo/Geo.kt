package dev.hunterlab.core.geo

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Contrato geográfico compartilhado por navegação e radares (MVP).
 * Coordenadas WGS84 em graus. Distâncias em metros. Rumos em graus, 0 = norte, sentido horário.
 */
data class GeoPoint(val lat: Double, val lon: Double) {
    init {
        require(lat in -90.0..90.0 && !lat.isNaN()) { "Latitude inválida: $lat" }
        require(lon in -180.0..180.0 && !lon.isNaN()) { "Longitude inválida: $lon" }
    }

    override fun toString(): String = "%.6f,%.6f".format(java.util.Locale.ROOT, lat, lon)
}

object Geo {
    const val EARTH_RADIUS_M = 6_371_008.8

    private fun rad(d: Double) = d * PI / 180.0
    private fun deg(r: Double) = r * 180.0 / PI

    /** Distância de grande círculo (haversine). */
    fun distanceM(a: GeoPoint, b: GeoPoint): Double {
        val dLat = rad(b.lat - a.lat)
        val dLon = rad(b.lon - a.lon)
        val h = sin(dLat / 2).let { it * it } + cos(rad(a.lat)) * cos(rad(b.lat)) * sin(dLon / 2).let { it * it }
        return 2 * EARTH_RADIUS_M * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    /** Rumo inicial de [a] para [b], 0..360. */
    fun bearingDeg(a: GeoPoint, b: GeoPoint): Double {
        val y = sin(rad(b.lon - a.lon)) * cos(rad(b.lat))
        val x = cos(rad(a.lat)) * sin(rad(b.lat)) - sin(rad(a.lat)) * cos(rad(b.lat)) * cos(rad(b.lon - a.lon))
        return normalize(deg(atan2(y, x)))
    }

    fun normalize(deg: Double): Double = ((deg % 360.0) + 360.0) % 360.0

    /** Menor diferença angular entre dois rumos, 0..180. */
    fun angleDiff(a: Double, b: Double): Double {
        val d = abs(normalize(a) - normalize(b))
        return if (d > 180.0) 360.0 - d else d
    }
}

/** Projeção de um ponto sobre uma [Polyline]. */
data class Projection(
    /** Distância percorrida ao longo da linha até o ponto projetado. */
    val alongM: Double,
    /** Distância perpendicular (aproximada) do ponto até a linha. */
    val offsetM: Double,
    /** Índice do segmento (ponto inicial) onde caiu a projeção. */
    val segmentIndex: Int,
    /** Rumo do segmento onde caiu a projeção. */
    val segmentBearingDeg: Double,
    val point: GeoPoint,
)

/**
 * Linha com distâncias acumuladas. A projeção usa um plano local equirretangular por segmento —
 * precisão de centímetros para segmentos de rota (dezenas/centenas de metros).
 */
class Polyline(val points: List<GeoPoint>) {
    init {
        require(points.size >= 2) { "A linha precisa de pelo menos 2 pontos" }
    }

    /** cumulative[i] = distância do primeiro ponto até points[i]. */
    val cumulative: DoubleArray = DoubleArray(points.size).also { c ->
        for (i in 1 until points.size) c[i] = c[i - 1] + Geo.distanceM(points[i - 1], points[i])
    }

    val lengthM: Double get() = cumulative.last()

    /** Ponto a [alongM] metros do início (limitado às pontas). */
    fun pointAt(alongM: Double): GeoPoint {
        if (alongM <= 0) return points.first()
        if (alongM >= lengthM) return points.last()
        var i = cumulative.binarySearch(alongM).let { if (it >= 0) it else -it - 2 }
        i = i.coerceIn(0, points.size - 2)
        val segLen = cumulative[i + 1] - cumulative[i]
        val t = if (segLen <= 0) 0.0 else (alongM - cumulative[i]) / segLen
        val a = points[i]
        val b = points[i + 1]
        return GeoPoint(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t)
    }

    /** Rumo do segmento que contém [alongM]. */
    fun bearingAt(alongM: Double): Double {
        var i = cumulative.binarySearch(alongM.coerceIn(0.0, lengthM)).let { if (it >= 0) it else -it - 2 }
        i = i.coerceIn(0, points.size - 2)
        return Geo.bearingDeg(points[i], points[i + 1])
    }

    /**
     * Projeção mais próxima de [p]. Se [fromSegment]/[toSegment] forem informados, procura só nesse intervalo
     * (útil para não "pular" para um trecho da rota que passa perto mais à frente).
     */
    fun project(p: GeoPoint, fromSegment: Int = 0, toSegment: Int = points.size - 2): Projection {
        val from = fromSegment.coerceIn(0, points.size - 2)
        val to = toSegment.coerceIn(from, points.size - 2)
        var best: Projection? = null
        for (i in from..to) {
            val proj = projectOnSegment(p, i)
            if (best == null || proj.offsetM < best.offsetM) best = proj
        }
        return best!!
    }

    private fun projectOnSegment(p: GeoPoint, i: Int): Projection {
        val a = points[i]
        val b = points[i + 1]
        val cosLat = cos(a.lat * PI / 180.0)
        // Plano local em metros com origem em a.
        val k = Geo.EARTH_RADIUS_M * PI / 180.0
        val bx = (b.lon - a.lon) * cosLat * k
        val by = (b.lat - a.lat) * k
        val px = (p.lon - a.lon) * cosLat * k
        val py = (p.lat - a.lat) * k
        val len2 = bx * bx + by * by
        val t = if (len2 <= 0) 0.0 else ((px * bx + py * by) / len2).coerceIn(0.0, 1.0)
        val q = GeoPoint(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t)
        val segLen = cumulative[i + 1] - cumulative[i]
        return Projection(
            alongM = cumulative[i] + segLen * t,
            offsetM = Geo.distanceM(p, q),
            segmentIndex = i,
            segmentBearingDeg = Geo.bearingDeg(a, b),
            point = q,
        )
    }
}
