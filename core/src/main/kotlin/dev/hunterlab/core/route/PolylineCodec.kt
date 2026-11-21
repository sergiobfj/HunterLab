package dev.hunterlab.core.route

import dev.hunterlab.core.geo.GeoPoint
import kotlin.math.roundToLong

/**
 * Algoritmo "encoded polyline" do Google (também usado pelo GraphHopper com `points_encoded=true`).
 * Cada coordenada é a diferença para a anterior, multiplicada por [multiplier] (1e5 padrão; 1e6 em
 * algumas APIs), em blocos de 5 bits com deslocamento ASCII 63. Só 2D (sem elevação).
 */
object PolylineCodec {

    /** Decodifica. Lança [IllegalArgumentException] para texto malformado ou coordenadas inválidas. */
    fun decode(encoded: String, multiplier: Double = 1e5): List<GeoPoint> {
        require(multiplier.isFinite() && multiplier > 0) { "Multiplicador inválido: $multiplier" }
        val out = ArrayList<GeoPoint>(encoded.length / 4)
        var index = 0
        var lat = 0L
        var lon = 0L
        while (index < encoded.length) {
            val dLat = readValue(encoded, index).also { index = it.second }.first
            require(index < encoded.length) { "Polyline truncada: latitude sem longitude na posição $index" }
            val dLon = readValue(encoded, index).also { index = it.second }.first
            lat += dLat
            lon += dLon
            out += GeoPoint(lat / multiplier, lon / multiplier)
        }
        return out
    }

    fun encode(points: List<GeoPoint>, multiplier: Double = 1e5): String {
        require(multiplier.isFinite() && multiplier > 0) { "Multiplicador inválido: $multiplier" }
        val sb = StringBuilder()
        var prevLat = 0L
        var prevLon = 0L
        for (p in points) {
            val lat = (p.lat * multiplier).roundToLong()
            val lon = (p.lon * multiplier).roundToLong()
            writeValue(lat - prevLat, sb)
            writeValue(lon - prevLon, sb)
            prevLat = lat
            prevLon = lon
        }
        return sb.toString()
    }

    private fun readValue(s: String, start: Int): Pair<Long, Int> {
        var result = 0L
        var shift = 0
        var i = start
        while (true) {
            require(i < s.length) { "Polyline truncada na posição $i" }
            val c = s[i++].code - 63
            require(c in 0..63) { "Caractere inválido na polyline: '${s[i - 1]}' (posição ${i - 1})" }
            require(shift <= 60) { "Valor longo demais na polyline (posição $start)" }
            result = result or ((c and 0x1F).toLong() shl shift)
            shift += 5
            if (c < 0x20) break
        }
        val value = if ((result and 1L) != 0L) (result shr 1).inv() else result shr 1
        return value to i
    }

    private fun writeValue(value: Long, sb: StringBuilder) {
        var v = if (value < 0) (value shl 1).inv() else value shl 1
        while (v >= 0x20) {
            sb.append(((0x20 or (v and 0x1F).toInt()) + 63).toChar())
            v = v shr 5
        }
        sb.append((v.toInt() + 63).toChar())
    }
}
