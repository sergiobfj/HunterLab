package dev.hunterlab.core.navigation

import dev.hunterlab.core.geo.Geo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RouteDriveSimulatorTest {

    private val route = TestRoutes.lRoute()

    @Test
    fun `gera fixes ao longo da rota na velocidade dada`() {
        val fixes = RouteDriveSimulator(route, speedMps = 10.0, startTimeMs = 5_000).fixes().toList()
        assertEquals(91, fixes.size) // 0..900 m a cada 10 m
        assertEquals(5_000L, fixes.first().timeMs)
        assertEquals(95_000L, fixes.last().timeMs)
        assertEquals(route.polyline.points.first(), fixes.first().point)
        assertEquals(0.0, Geo.distanceM(route.destination, fixes.last().point), 0.01)
        assertEquals(90.0, fixes[10].bearingDeg!!.toDouble(), 0.5)
        assertEquals(180.0, fixes[60].bearingDeg!!.toDouble(), 0.5)
        assertTrue(fixes.all { it.speedMps == 10f && it.accuracyM == 5f })
        fixes.zipWithNext().forEach { (a, b) -> assertTrue(Geo.distanceM(a.point, b.point) <= 10.01) }
    }

    @Test
    fun `ruido e deterministico pela semente e limitado`() {
        val a = RouteDriveSimulator(route, 10.0, noiseM = 6.0, seed = 1).fixes().toList()
        val b = RouteDriveSimulator(route, 10.0, noiseM = 6.0, seed = 1).fixes().toList()
        val c = RouteDriveSimulator(route, 10.0, noiseM = 6.0, seed = 2).fixes().toList()
        assertEquals(a, b)
        assertNotEquals(a, c)
        val clean = RouteDriveSimulator(route, 10.0).fixes().toList()
        val offsets = a.zip(clean).map { (n, p) -> Geo.distanceM(n.point, p.point) }
        assertTrue(offsets.max() < 6.0 * 5, "ruído fora do esperado: ${offsets.max()}")
        assertTrue(offsets.average() > 1.0)
    }

    @Test
    fun `desvio reto sai da rota depois da curva perdida`() {
        val sim = RouteDriveSimulator(route, 10.0, deviation = SimDeviation.GoStraight(500.0))
        val (p, bearing) = sim.positionAt(600.0)
        assertEquals(90.0, bearing, 0.5)
        assertEquals(0.0, Geo.distanceM(p, TestRoutes.pt(600.0, 0.0)), 0.5)
        assertEquals(100.0, route.polyline.project(p).offsetM, 0.5)
    }

    @Test
    fun `meia volta anda para tras com rumo invertido`() {
        val sim = RouteDriveSimulator(route, 10.0, deviation = SimDeviation.TurnBack(300.0))
        val (p, bearing) = sim.positionAt(350.0)
        assertEquals(270.0, bearing, 0.5)
        assertEquals(250.0, route.polyline.project(p).alongM, 0.5)
    }

    @Test
    fun `deslocamento lateral fica so no trecho pedido`() {
        val sim = RouteDriveSimulator(route, 10.0, deviation = SimDeviation.Offset(100.0, 200.0, 50.0))
        assertEquals(0.0, Geo.distanceM(sim.positionAt(50.0).first, TestRoutes.pt(50.0, 0.0)), 0.5)
        assertEquals(0.0, Geo.distanceM(sim.positionAt(150.0).first, TestRoutes.pt(150.0, -50.0)), 0.5) // direita de quem vai a leste = sul
        assertEquals(0.0, Geo.distanceM(sim.positionAt(250.0).first, TestRoutes.pt(250.0, 0.0)), 0.5)
    }

    @Test
    fun `parametros invalidos sao rejeitados`() {
        assertThrows<IllegalArgumentException> { RouteDriveSimulator(route, 0.0) }
        assertThrows<IllegalArgumentException> { RouteDriveSimulator(route, 10.0, intervalMs = 0) }
        assertThrows<IllegalArgumentException> { RouteDriveSimulator(route, 10.0, noiseM = -1.0) }
    }
}
