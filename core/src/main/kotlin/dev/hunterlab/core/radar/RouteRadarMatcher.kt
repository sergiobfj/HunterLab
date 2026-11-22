package dev.hunterlab.core.radar

import dev.hunterlab.core.geo.Geo
import dev.hunterlab.core.geo.Polyline
import dev.hunterlab.core.geo.Projection
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Um radar real casado com a rota: [alongM] metros do início da rota, numa passagem ([passIndex], 0 = primeira)
 * por aquele dispositivo. [directionConfirmed] = a câmera tem sentido conhecido e ele bate com o rumo da via.
 */
data class RouteRadarMatch(
    val camera: RadarCamera,
    val alongM: Int,
    val offsetM: Double,
    val routeBearingDeg: Double,
    val directionConfirmed: Boolean,
    val passIndex: Int,
) {
    /** Id do radar na rota: o da câmera; a partir da 2ª passagem, com sufixo `#n`. */
    val radarId: String get() = if (passIndex == 0) camera.id else "${camera.id}#${passIndex + 1}"

    val label: String
        get() = buildString {
            append(camera.kind.label)
            camera.maxSpeedKmh?.let { append(' ').append(it).append(" km/h") }
            if (!directionConfirmed) append(" (sentido não confirmado)")
        }

    /** Radar real para o [RadarEngine]. */
    fun toRouteRadar(): RouteRadar = RouteRadar(
        id = radarId,
        positionM = alongM.coerceAtLeast(0),
        kind = camera.kind,
        speedLimitKmh = camera.maxSpeedKmh,
        label = label,
        simulated = false,
        directionConfirmed = directionConfirmed,
        limitInfo = camera.speedLimit,
        sourceLabel = camera.source,
    )
}

/**
 * Casa câmeras com uma rota (puro, sem rede).
 *
 * - aceita só câmeras a até [Config.maxOffsetM] da linha (descarta vias paralelas mais distantes);
 * - com sentido conhecido, exige |sentido − rumo do segmento| ≤ [Config.maxHeadingDiffDeg] (descarta o sentido
 *   oposto); sem sentido, aceita marcando "sentido não confirmado";
 * - cada passagem da rota perto da câmera conta uma vez: projeções em segmentos vizinhos (curvas, rotatórias)
 *   separadas por até [Config.passageGapM] ao longo da rota formam a mesma passagem;
 * - descarta câmeras antes do início ou depois do fim da rota (mais que [Config.endpointToleranceM]);
 * - resultado ordenado pela posição na rota.
 */
class RouteRadarMatcher(val config: Config = Config()) {

    data class Config(
        val maxOffsetM: Double = 25.0,
        val maxHeadingDiffDeg: Double = 45.0,
        val passageGapM: Double = 50.0,
        val endpointToleranceM: Double = 5.0,
    ) {
        init {
            require(maxOffsetM > 0 && maxOffsetM.isFinite()) { "Distância máxima inválida" }
            require(maxHeadingDiffDeg in 0.0..180.0) { "Diferença de rumo deve estar entre 0 e 180" }
            require(passageGapM >= 0 && endpointToleranceM >= 0) { "Valores negativos não são permitidos" }
        }
    }

    fun match(route: Polyline, cameras: Collection<RadarCamera>): List<RouteRadarMatch> {
        if (cameras.isEmpty()) return emptyList()
        val sorted = cameras.distinctBy { it.id }.sortedBy { it.point.lat }
        val lats = DoubleArray(sorted.size) { sorted[it].point.lat }
        val candidates = HashMap<Int, MutableList<Projection>>()
        // Margem em graus com folga de 50% (o filtro fino é a projeção).
        val marginLat = config.maxOffsetM * 1.5 / M_PER_DEG_LAT
        val pts = route.points
        val lastSeg = pts.size - 2
        val firstReal = (0..lastSeg).firstOrNull { route.cumulative[it + 1] - route.cumulative[it] >= MIN_SEGMENT_M } ?: return emptyList()
        val lastReal = (0..lastSeg).last { route.cumulative[it + 1] - route.cumulative[it] >= MIN_SEGMENT_M }

        for (i in 0..lastSeg) {
            if (route.cumulative[i + 1] - route.cumulative[i] < MIN_SEGMENT_M) continue // segmento degenerado
            val a = pts[i]
            val b = pts[i + 1]
            val minLat = min(a.lat, b.lat) - marginLat
            val maxLat = max(a.lat, b.lat) + marginLat
            val cosLat = max(cos(max(abs(minLat), abs(maxLat)).coerceAtMost(89.0) * PI / 180.0), 0.01)
            val marginLon = config.maxOffsetM * 1.5 / (M_PER_DEG_LAT * cosLat)
            val minLon = min(a.lon, b.lon) - marginLon
            val maxLon = max(a.lon, b.lon) + marginLon
            var k = lowerBound(lats, minLat)
            while (k < sorted.size && lats[k] <= maxLat) {
                val cam = sorted[k]
                if (cam.point.lon in minLon..maxLon) {
                    val proj = route.project(cam.point, i, i)
                    if (proj.offsetM <= config.maxOffsetM && !beyondEnds(route, i, firstReal, lastReal, proj, cam)) {
                        candidates.getOrPut(k) { mutableListOf() } += proj
                    }
                }
                k++
            }
        }

        val result = mutableListOf<RouteRadarMatch>()
        for ((k, projs) in candidates) {
            val cam = sorted[k]
            var pass = 0
            for (group in passages(projs)) {
                val chosen = choose(cam, group) ?: continue // sentido oposto nesta passagem
                result += RouteRadarMatch(
                    camera = cam,
                    alongM = chosen.first.alongM.roundToInt(),
                    offsetM = chosen.first.offsetM,
                    routeBearingDeg = chosen.first.segmentBearingDeg,
                    directionConfirmed = chosen.second,
                    passIndex = pass++,
                )
            }
        }
        return result.sortedWith(compareBy({ it.alongM }, { it.camera.id }))
    }

    /** Agrupa projeções (ordenadas ao longo da rota) em passagens. */
    private fun passages(projs: List<Projection>): List<List<Projection>> {
        val ordered = projs.sortedBy { it.alongM }
        val groups = mutableListOf(mutableListOf(ordered.first()))
        for (p in ordered.drop(1)) {
            val current = groups.last()
            if (p.alongM - current.last().alongM <= config.passageGapM) current += p else groups += mutableListOf(p)
        }
        return groups
    }

    /** Melhor projeção da passagem e se o sentido foi confirmado; `null` = sentido incompatível. */
    private fun choose(cam: RadarCamera, group: List<Projection>): Pair<Projection, Boolean>? {
        val dir = cam.directionDeg
        if (!cam.directionKnown || dir == null) return group.minBy { it.offsetM } to false
        val compatible = group.filter { Geo.angleDiff(dir, it.segmentBearingDeg) <= config.maxHeadingDiffDeg }
        return compatible.minByOrNull { it.offsetM }?.let { it to true }
    }

    /** A projeção foi "presa" na ponta da rota e a câmera fica antes do início / depois do fim. */
    private fun beyondEnds(route: Polyline, seg: Int, firstSeg: Int, lastSeg: Int, proj: Projection, cam: RadarCamera): Boolean {
        val pts = route.points
        if (seg == firstSeg && proj.alongM <= route.cumulative[firstSeg] + END_EPS_M) {
            val d = Geo.distanceM(pts.first(), cam.point)
            val ang = Geo.angleDiff(Geo.bearingDeg(pts.first(), cam.point), proj.segmentBearingDeg)
            if (-d * cos(ang * PI / 180.0) > config.endpointToleranceM) return true
        }
        if (seg == lastSeg && proj.alongM >= route.cumulative[lastSeg + 1] - END_EPS_M) {
            val d = Geo.distanceM(pts.last(), cam.point)
            val ang = Geo.angleDiff(Geo.bearingDeg(pts.last(), cam.point), proj.segmentBearingDeg)
            if (d * cos(ang * PI / 180.0) > config.endpointToleranceM) return true
        }
        return false
    }

    private fun lowerBound(a: DoubleArray, v: Double): Int {
        var lo = 0
        var hi = a.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (a[mid] < v) lo = mid + 1 else hi = mid
        }
        return lo
    }

    private companion object {
        const val M_PER_DEG_LAT = Geo.EARTH_RADIUS_M * PI / 180.0
        const val MIN_SEGMENT_M = 0.01
        const val END_EPS_M = 1e-6
    }
}
