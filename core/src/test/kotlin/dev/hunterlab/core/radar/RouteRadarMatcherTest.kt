package dev.hunterlab.core.radar

import dev.hunterlab.core.radar.RadarTestGeo.camera
import dev.hunterlab.core.radar.RadarTestGeo.route
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RouteRadarMatcherTest {

    private val matcher = RouteRadarMatcher()

    /** Reta de 2 km para leste (rumo 90°). */
    private val east = route(0 to 0, 2_000 to 0)

    @Test
    fun `radar na via e no sentido da rota e casado com a posicao ao longo da rota`() {
        val m = matcher.match(east, listOf(camera(1, 800.0, 5.0, directionDeg = 90.0))).single()
        assertEquals(800.0, m.alongM.toDouble(), 1.0)
        assertEquals(5.0, m.offsetM, 0.5)
        assertEquals(90.0, m.routeBearingDeg, 0.5)
        assertTrue(m.directionConfirmed)
        assertEquals(0, m.passIndex)
        assertEquals("osm:node/1", m.radarId)

        val r = m.toRouteRadar()
        assertFalse(r.isSimulated)
        assertEquals(60, r.speedLimitKmh)
        assertEquals(m.alongM, r.positionM)
        assertEquals("Radar fixo 60 km/h", r.label)
    }

    @Test
    fun `via paralela a 40 m e descartada`() {
        assertTrue(matcher.match(east, listOf(camera(1, 800.0, 40.0))).isEmpty())
        assertTrue(matcher.match(east, listOf(camera(1, 800.0, -40.0, directionDeg = 90.0))).isEmpty())
        // Controle: a 20 m ainda é a mesma via (poste no acostamento).
        assertEquals(1, matcher.match(east, listOf(camera(1, 800.0, 20.0))).size)
        // Limite configurável.
        val wide = RouteRadarMatcher(RouteRadarMatcher.Config(maxOffsetM = 50.0))
        assertEquals(1, wide.match(east, listOf(camera(1, 800.0, 40.0))).size)
    }

    @Test
    fun `sentido oposto e descartado`() {
        assertTrue(matcher.match(east, listOf(camera(1, 800.0, 3.0, directionDeg = 270.0))).isEmpty())
        assertTrue(matcher.match(east, listOf(camera(1, 800.0, 3.0, directionDeg = 140.0))).isEmpty()) // 50°
        assertEquals(1, matcher.match(east, listOf(camera(1, 800.0, 3.0, directionDeg = 120.0))).size) // 30°
        assertEquals(1, matcher.match(east, listOf(camera(1, 800.0, 3.0, directionDeg = 50.0))).size) // 40°
        val tolerant = RouteRadarMatcher(RouteRadarMatcher.Config(maxHeadingDiffDeg = 60.0))
        assertEquals(1, tolerant.match(east, listOf(camera(1, 800.0, 3.0, directionDeg = 140.0))).size)
    }

    @Test
    fun `sem direcao e aceito e marcado como nao confirmado`() {
        val m = matcher.match(east, listOf(camera(1, 800.0, 0.0, speed = null))).single()
        assertFalse(m.directionConfirmed)
        assertTrue("sentido não confirmado" in m.label, m.label)
        val r = m.toRouteRadar()
        assertFalse(r.directionConfirmed)
        assertEquals(null, r.speedLimitKmh)
    }

    @Test
    fun `rota que passa duas vezes conta cada passagem compativel uma vez`() {
        // Ida e volta numa pista dupla: leste em y=0, retorno para oeste em y=8 m.
        val outAndBack = route(0 to 0, 2_000 to 0, 2_000 to 8, 0 to 8)
        val unknown = matcher.match(outAndBack, listOf(camera(1, 800.0, 4.0)))
        assertEquals(2, unknown.size)
        assertEquals(800.0, unknown[0].alongM.toDouble(), 1.0)
        assertEquals(3_208.0, unknown[1].alongM.toDouble(), 1.0)
        assertEquals(listOf("osm:node/1", "osm:node/1#2"), unknown.map { it.radarId })

        val eastOnly = matcher.match(outAndBack, listOf(camera(1, 800.0, 4.0, directionDeg = 90.0))).single()
        assertEquals(800.0, eastOnly.alongM.toDouble(), 1.0)
        val westOnly = matcher.match(outAndBack, listOf(camera(1, 800.0, 4.0, directionDeg = 270.0))).single()
        assertEquals(3_208.0, westOnly.alongM.toDouble(), 1.0)
        assertEquals(0, westOnly.passIndex)

        // Circuito: passa pelo mesmo radar duas vezes no mesmo sentido.
        val loop = route(0 to 0, 2_000 to 0, 2_000 to 500, 0 to 500, 0 to 0, 2_000 to 0)
        val twice = matcher.match(loop, listOf(camera(1, 800.0, 0.0, directionDeg = 90.0)))
        assertEquals(listOf(800.0, 5_800.0), twice.map { it.alongM.toDouble() }.map { Math.round(it / 10.0) * 10.0 })
        assertEquals(listOf(0, 1), twice.map { it.passIndex })
    }

    @Test
    fun `mesma passagem numa curva nao duplica`() {
        // Curva à esquerda em x=1000; a câmera fica a ~10 m dos dois segmentos.
        val turn = route(0 to 0, 1_000 to 0, 1_000 to 1_000)
        val any = matcher.match(turn, listOf(camera(1, 990.0, 10.0))).single()
        assertTrue(any.alongM in 985..1_015, "${any.alongM}")
        // Com sentido conhecido, escolhe o segmento compatível.
        val north = matcher.match(turn, listOf(camera(1, 990.0, 10.0, directionDeg = 0.0))).single()
        assertEquals(1_010.0, north.alongM.toDouble(), 1.5)
        val eastward = matcher.match(turn, listOf(camera(1, 990.0, 10.0, directionDeg = 90.0))).single()
        assertEquals(990.0, eastward.alongM.toDouble(), 1.5)
    }

    @Test
    fun `camera antes do inicio ou depois do fim nao entra`() {
        val line = route(0 to 0, 1_000 to 0)
        assertTrue(matcher.match(line, listOf(camera(1, -15.0, 0.0))).isEmpty())
        assertTrue(matcher.match(line, listOf(camera(1, 1_015.0, 0.0))).isEmpty())
        // Ao lado do ponto inicial (dentro da tolerância): conta, na posição 0.
        assertEquals(0, matcher.match(line, listOf(camera(1, -3.0, 10.0))).single().alongM)
    }

    @Test
    fun `resultado ordenado pela posicao e sem ids repetidos`() {
        val cams = listOf(camera(3, 1_500.0, 2.0), camera(1, 300.0, -2.0), camera(2, 900.0, 0.0), camera(2, 900.0, 0.0))
        val ms = matcher.match(east, cams)
        assertEquals(listOf("osm:node/1", "osm:node/2", "osm:node/3"), ms.map { it.camera.id })
        assertEquals(ms.map { it.alongM }.sorted(), ms.map { it.alongM })
        assertTrue(matcher.match(east, emptyList()).isEmpty())
    }

    @Test
    fun `configuracao invalida e rejeitada`() {
        assertThrows<IllegalArgumentException> { RouteRadarMatcher.Config(maxOffsetM = 0.0) }
        assertThrows<IllegalArgumentException> { RouteRadarMatcher.Config(maxHeadingDiffDeg = 200.0) }
        assertThrows<IllegalArgumentException> { RadarCamera("x", RadarTestGeo.ORIGIN, RadarKind.FIXED, 60, null, directionKnown = true) }
        assertThrows<IllegalArgumentException> { RadarCamera("x", RadarTestGeo.ORIGIN, RadarKind.FIXED, 0, null) }
        assertThrows<IllegalArgumentException> { RadarCamera("x", RadarTestGeo.ORIGIN, RadarKind.FIXED, 60, 360.0) }
    }
}
