package dev.hunterlab.core.geo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class GeoTest {
    // Pontos fictícios em São Paulo (Av. Paulista aproximada).
    private val a = GeoPoint(-23.5614, -46.6559)
    private val b = GeoPoint(-23.5614, -46.6459)

    @Test
    fun `distancia haversine conhecida`() {
        // 1 grau de latitude ≈ 111,195 km.
        assertEquals(111_195.0, Geo.distanceM(GeoPoint(0.0, 0.0), GeoPoint(1.0, 0.0)), 5.0)
        // 0,01° de longitude a -23,56° ≈ 1019 m.
        assertEquals(1019.4, Geo.distanceM(a, b), 1.0)
    }

    @Test
    fun `rumos cardeais e diferenca angular`() {
        assertEquals(0.0, Geo.bearingDeg(GeoPoint(0.0, 0.0), GeoPoint(1.0, 0.0)), 1e-6)
        assertEquals(90.0, Geo.bearingDeg(GeoPoint(0.0, 0.0), GeoPoint(0.0, 1.0)), 1e-6)
        assertEquals(180.0, Geo.bearingDeg(GeoPoint(1.0, 0.0), GeoPoint(0.0, 0.0)), 1e-6)
        assertEquals(20.0, Geo.angleDiff(350.0, 10.0), 1e-9)
        assertEquals(180.0, Geo.angleDiff(90.0, 270.0), 1e-9)
    }

    @Test
    fun `coordenadas invalidas sao rejeitadas`() {
        assertThrows<IllegalArgumentException> { GeoPoint(91.0, 0.0) }
        assertThrows<IllegalArgumentException> { GeoPoint(0.0, 181.0) }
        assertThrows<IllegalArgumentException> { GeoPoint(Double.NaN, 0.0) }
    }

    @Test
    fun `polyline acumula distancias e interpola`() {
        val c = GeoPoint(-23.5514, -46.6459) // ~1112 m ao norte de b
        val line = Polyline(listOf(a, b, c))
        assertEquals(Geo.distanceM(a, b) + Geo.distanceM(b, c), line.lengthM, 1e-6)
        val mid = line.pointAt(Geo.distanceM(a, b) / 2)
        assertEquals(-23.5614, mid.lat, 1e-6)
        assertEquals(-46.6509, mid.lon, 1e-4)
        assertEquals(a, line.pointAt(-10.0))
        assertEquals(c, line.pointAt(1e9))
        assertEquals(0.0, line.bearingAt(line.lengthM - 1), 0.5)
    }

    @Test
    fun `projecao perpendicular no segmento`() {
        val line = Polyline(listOf(a, b))
        // 20 m ao norte do meio do segmento.
        val p = GeoPoint(-23.5614 + 20.0 / 111_195.0, -46.6509)
        val proj = line.project(p)
        assertEquals(20.0, proj.offsetM, 0.5)
        assertEquals(line.lengthM / 2, proj.alongM, 1.0)
        assertEquals(0, proj.segmentIndex)
        assertEquals(90.0, proj.segmentBearingDeg, 0.5)
    }

    @Test
    fun `projecao antes do inicio fica presa na ponta`() {
        val line = Polyline(listOf(a, b))
        val before = GeoPoint(-23.5614, -46.6600)
        val proj = line.project(before)
        assertEquals(0.0, proj.alongM, 1e-9)
        assertTrue(proj.offsetM > 400)
    }

    @Test
    fun `projecao restrita a uma janela de segmentos`() {
        // Ida e volta pelo mesmo eixo: sem janela, a projeção poderia cair no trecho de volta.
        val c = GeoPoint(-23.5612, -46.6559)
        val line = Polyline(listOf(a, b, c))
        val p = GeoPoint(-23.5613, -46.6550)
        val onlyFirst = line.project(p, fromSegment = 0, toSegment = 0)
        assertEquals(0, onlyFirst.segmentIndex)
        val onlySecond = line.project(p, fromSegment = 1, toSegment = 1)
        assertEquals(1, onlySecond.segmentIndex)
        assertTrue(onlySecond.alongM > line.cumulative[1])
    }

    @Test
    fun `linha precisa de dois pontos`() {
        assertThrows<IllegalArgumentException> { Polyline(listOf(a)) }
    }
}
