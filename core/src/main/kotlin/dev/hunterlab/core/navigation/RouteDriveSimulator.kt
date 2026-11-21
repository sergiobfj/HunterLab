package dev.hunterlab.core.navigation

import dev.hunterlab.core.geo.Geo
import dev.hunterlab.core.geo.GeoPoint
import dev.hunterlab.core.route.Route
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Como o trajeto simulado sai da rota. */
sealed interface SimDeviation {
    /** A partir de [atAlongM], segue reto no rumo em que vinha (ex.: perdeu a conversão). */
    data class GoStraight(val atAlongM: Double) : SimDeviation

    /** Em [atAlongM], dá meia-volta e anda de volta sobre a rota (contramão em relação à rota). */
    data class TurnBack(val atAlongM: Double) : SimDeviation

    /** Entre [fromAlongM] e [toAlongM], anda deslocado [offsetM] metros à direita da via (negativo = esquerda). */
    data class Offset(val fromAlongM: Double, val toAlongM: Double, val offsetM: Double) : SimDeviation
}

/**
 * Gera [LocationFix]es "dirigindo" uma [Route] a velocidade constante. Puro e determinístico ([seed]):
 * serve para testes e para um futuro modo "simular trajeto" no app.
 *
 * O trajeto termina quando a distância percorrida chega ao comprimento da rota (o último fix cai no fim
 * da linha, se não houver desvio) ou após `maxFixes`.
 */
class RouteDriveSimulator(
    val route: Route,
    val speedMps: Double,
    val intervalMs: Long = 1_000,
    val startTimeMs: Long = 0,
    /** Precisão informada em cada fix (null = desconhecida). */
    val accuracyM: Float? = 5f,
    /** Desvio-padrão (m) do ruído de posição; 0 = sem ruído. */
    val noiseM: Double = 0.0,
    val seed: Long = 42,
    val deviation: SimDeviation? = null,
    val startAlongM: Double = 0.0,
) {
    init {
        require(speedMps > 0 && speedMps.isFinite()) { "Velocidade inválida: $speedMps" }
        require(intervalMs > 0) { "Intervalo inválido: $intervalMs" }
        require(noiseM >= 0 && noiseM.isFinite()) { "Ruído inválido: $noiseM" }
        require(startAlongM >= 0) { "Início inválido: $startAlongM" }
    }

    private val line = route.polyline

    fun fixes(maxFixes: Int = 100_000): Sequence<LocationFix> = sequence {
        val random = java.util.Random(seed)
        var i = 0
        while (i < maxFixes) {
            val traveled = (startAlongM + speedMps * i * intervalMs / 1000.0).coerceAtMost(line.lengthM)
            val (point, bearing) = positionAt(traveled)
            val noisy = if (noiseM > 0) jitter(point, random) else point
            yield(
                LocationFix(
                    point = noisy,
                    accuracyM = accuracyM,
                    bearingDeg = bearing.toFloat(),
                    speedMps = speedMps.toFloat(),
                    timeMs = startTimeMs + i * intervalMs,
                ),
            )
            if (traveled >= line.lengthM) break
            i++
        }
    }

    /** Posição ideal (sem ruído) e rumo depois de percorrer [traveledM] metros. */
    fun positionAt(traveledM: Double): Pair<GeoPoint, Double> {
        val t = traveledM.coerceAtLeast(0.0)
        return when (val d = deviation) {
            null -> onRoute(t)
            is SimDeviation.GoStraight -> if (t <= d.atAlongM) {
                onRoute(t)
            } else {
                val bearing = line.bearingAt((d.atAlongM - 0.5).coerceAtLeast(0.0))
                destination(line.pointAt(d.atAlongM), bearing, t - d.atAlongM) to bearing
            }
            is SimDeviation.TurnBack -> if (t <= d.atAlongM) {
                onRoute(t)
            } else {
                val along = (d.atAlongM - (t - d.atAlongM)).coerceAtLeast(0.0)
                line.pointAt(along) to Geo.normalize(line.bearingAt(along) + 180.0)
            }
            is SimDeviation.Offset -> if (t < d.fromAlongM || t > d.toAlongM || d.offsetM == 0.0) {
                onRoute(t)
            } else {
                val bearing = line.bearingAt(t)
                val side = if (d.offsetM > 0) 90.0 else -90.0
                destination(line.pointAt(t), bearing + side, abs(d.offsetM)) to bearing
            }
        }
    }

    private fun onRoute(t: Double) = line.pointAt(t) to line.bearingAt(t)

    private fun jitter(p: GeoPoint, random: java.util.Random): GeoPoint {
        val dist = abs(random.nextGaussian()) * noiseM
        val dir = random.nextDouble() * 360.0
        return destination(p, dir, dist)
    }

    companion object {
        /** Ponto a [distM] metros de [p] no rumo [bearingDeg] (grande círculo). */
        fun destination(p: GeoPoint, bearingDeg: Double, distM: Double): GeoPoint {
            val delta = distM / Geo.EARTH_RADIUS_M
            val theta = bearingDeg * PI / 180.0
            val phi1 = p.lat * PI / 180.0
            val lambda1 = p.lon * PI / 180.0
            val phi2 = asin((sin(phi1) * cos(delta) + cos(phi1) * sin(delta) * cos(theta)).coerceIn(-1.0, 1.0))
            val lambda2 = lambda1 + atan2(sin(theta) * sin(delta) * cos(phi1), cos(delta) - sin(phi1) * sin(phi2))
            val lon = ((lambda2 * 180.0 / PI + 540.0) % 360.0) - 180.0
            return GeoPoint(phi2 * 180.0 / PI, lon)
        }
    }
}
