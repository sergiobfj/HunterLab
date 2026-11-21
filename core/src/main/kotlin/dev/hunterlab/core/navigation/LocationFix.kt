package dev.hunterlab.core.navigation

import dev.hunterlab.core.geo.GeoPoint

/**
 * Leitura de GPS neutra (o app converte de `android.location.Location`).
 * [accuracyM] = raio horizontal (m); [bearingDeg] 0..360; [timeMs] = relógio do fix (só para ordem e durações).
 * Campos nulos = desconhecidos.
 */
data class LocationFix(
    val point: GeoPoint,
    val accuracyM: Float?,
    val bearingDeg: Float?,
    val speedMps: Float?,
    val timeMs: Long,
) {
    /** Precisão aceitável para navegar: desconhecida conta como aceitável; NaN/negativa, não. */
    fun isAccurate(maxAccuracyM: Double): Boolean {
        val a = accuracyM ?: return true
        return a.isFinite() && a >= 0f && a <= maxAccuracyM
    }

    /** Rumo confiável só acima de [minSpeedMps] (estritamente): parado, o rumo do GPS é ruído. */
    fun reliableBearing(minSpeedMps: Double): Double? {
        val b = bearingDeg ?: return null
        val s = speedMps ?: return null
        if (!b.isFinite() || !s.isFinite() || s <= minSpeedMps) return null
        return b.toDouble()
    }
}
