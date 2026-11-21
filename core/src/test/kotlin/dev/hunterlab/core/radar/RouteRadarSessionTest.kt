package dev.hunterlab.core.radar

import dev.hunterlab.core.radar.RadarTestGeo.camera
import dev.hunterlab.core.radar.RadarTestGeo.route
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RouteRadarSessionTest {

    /** Reta de 3 km para leste com um radar (sentido leste, 60 km/h) a 1500 m. */
    private val routeA = route(0 to 0, 3_000 to 0)
    private val camX = camera(1, 1_500.0, 0.0, directionDeg = 90.0)

    private fun walk(s: RouteRadarSession, from: Int, to: Int, step: Int = 10): List<Pair<Int, RadarEvent>> =
        (from..to step step).flatMap { pos -> s.update(pos.toDouble()).map { pos to it } }

    private fun tag(e: RadarEvent): String = e::class.simpleName + ":" + when (e) {
        is RadarEvent.Detected -> e.radar.id
        is RadarEvent.AlertStarted -> e.radar.id
        is RadarEvent.Passed -> e.radar.id
        is RadarEvent.ReturnedToNormal -> e.radar?.id ?: "-"
    }

    private fun ceil10(v: Int): Int = (v + 9) / 10 * 10

    @Test
    fun `sequencia detectado, alerta, ultrapassado e encerrado sem duplicar`() {
        val s = RouteRadarSession()
        s.setRoute(routeA, listOf(camX), CoverageStatus.COMPLETE)
        assertEquals("Radares atualizados", s.statusText)
        assertTrue(s.alertsEnabled)
        val along = s.matches.single().alongM
        assertEquals(1_500.0, along.toDouble(), 1.0)

        val events = walk(s, 0, 3_000)
        assertEquals(
            listOf("Detected:osm:node/1", "AlertStarted:osm:node/1", "Passed:osm:node/1", "ReturnedToNormal:osm:node/1"),
            events.map { tag(it.second) },
        )
        assertEquals(ceil10(along - 700), events[0].first)
        assertEquals(ceil10(along - 300), events[1].first)
        assertTrue(events[2].first in along + 1..along + 10)
        assertEquals(events[2].first + 60, events[3].first)
        assertEquals(RadarPhase.Clear, s.phase)
        assertTrue((events[1].second as RadarEvent.AlertStarted).description.contains("limite 60 km/h"))
    }

    @Test
    fun `fase de alerta vai para o ScreenComposer e oscilacao na borda nao repete o aviso`() {
        val s = RouteRadarSession()
        s.setRoute(routeA, listOf(camX), CoverageStatus.COMPLETE)
        val along = s.matches.single().alongM
        val ev = mutableListOf<RadarEvent>()
        ev += s.update((along - 310).toDouble()) // detectado
        ev += s.update((along - 290).toDouble()) // alerta
        val alert = s.phase as RadarPhase.Alert
        assertEquals(290, alert.distanceM)
        assertFalse(alert.radar.isSimulated)
        ev += s.update((along - 345).toDouble()) // recuo do GPS: volta a "detectado"
        assertTrue(s.phase is RadarPhase.Detected)
        ev += s.update((along - 250).toDouble()) // alerta de novo, sem novo aviso
        assertTrue(s.phase is RadarPhase.Alert)
        assertEquals(1, ev.count { it is RadarEvent.AlertStarted })
        assertEquals(1, ev.count { it is RadarEvent.Detected })
    }

    @Test
    fun `recuo pequeno do GPS depois de ultrapassar nao repete o radar`() {
        val s = RouteRadarSession()
        s.setRoute(routeA, listOf(camX), CoverageStatus.COMPLETE)
        val along = s.matches.single().alongM
        val ev = walk(s, 0, along + 10).map { it.second }.toMutableList()
        assertTrue(s.phase is RadarPhase.Passed)
        ev += s.update((along - 5).toDouble()) // recuo de ~15 m: ruído
        assertTrue(s.phase is RadarPhase.Passed)
        ev += walk(s, along + 20, 3_000).map { it.second }
        assertEquals(listOf("Detected", "AlertStarted", "Passed", "ReturnedToNormal"), ev.map { it::class.simpleName })
    }

    @Test
    fun `sem dados nunca alerta e informa sem dados de radar`() {
        val s = RouteRadarSession()
        assertEquals("Sem dados de radar", s.statusText) // antes de qualquer rota
        s.setRoute(routeA, listOf(camX), CoverageStatus.NONE)
        assertFalse(s.alertsEnabled)
        assertEquals("Sem dados de radar", s.statusText)
        assertTrue(s.matches.isEmpty())
        val events = walk(s, 0, 3_000)
        assertTrue(events.isEmpty(), events.toString())
        assertEquals(RadarPhase.Clear, s.phase)
    }

    @Test
    fun `dados que chegam no meio da rota passam a alertar e a perda de dados encerra o alerta`() {
        val s = RouteRadarSession()
        s.setRoute(routeA, emptyList(), CoverageStatus.NONE)
        assertTrue(walk(s, 0, 500).isEmpty())
        s.updateData(listOf(camX), CoverageStatus.PARTIAL)
        assertEquals("Cobertura parcial de radares", s.statusText)
        val ev = walk(s, 510, 1_300)
        assertEquals(listOf("Detected:osm:node/1", "AlertStarted:osm:node/1"), ev.map { tag(it.second) })

        // Atualização dos mesmos dados durante o alerta: nada é repetido.
        s.updateData(listOf(camX), CoverageStatus.COMPLETE)
        assertTrue(s.update(1_310.0).isEmpty())
        assertTrue(s.phase is RadarPhase.Alert)

        // Cobertura cai para NONE: a fase some na hora e o encerramento sai no próximo passo.
        s.updateData(emptyList(), CoverageStatus.NONE)
        assertEquals(RadarPhase.Clear, s.phase)
        assertEquals(listOf("ReturnedToNormal:osm:node/1"), s.update(1_320.0).map(::tag))
        assertTrue(walk(s, 1_330, 3_000).isEmpty())
    }

    @Test
    fun `recalculo apos ultrapassar nao repete o mesmo radar`() {
        val s = RouteRadarSession()
        s.setRoute(routeA, listOf(camX), CoverageStatus.COMPLETE)
        val before = walk(s, 0, 1_540)
        assertTrue(s.phase is RadarPhase.Passed)

        // Rota nova começa 10 m ANTES do radar (efeito do ajuste do GPS) e tem outro radar Y adiante.
        val camY = camera(2, 2_500.0, 0.0, directionDeg = 90.0)
        val routeB = route(1_490 to 2, 4_000 to 2)
        s.setRoute(routeB, listOf(camX, camY), CoverageStatus.COMPLETE)
        assertEquals(2, s.matches.size) // X ainda casa com a rota nova (posição ~10 m)
        val after = walk(s, 0, 2_000)

        val all = (before + after).map { tag(it.second) }
        assertEquals(1, all.count { it == "AlertStarted:osm:node/1" }, all.toString())
        assertEquals(1, all.count { it == "Detected:osm:node/1" }, all.toString())
        assertEquals(
            listOf("ReturnedToNormal:osm:node/1", "Detected:osm:node/2", "AlertStarted:osm:node/2", "Passed:osm:node/2", "ReturnedToNormal:osm:node/2"),
            after.map { tag(it.second) },
        )
    }

    @Test
    fun `recalculo durante o alerta continua sem novo aviso`() {
        val s = RouteRadarSession()
        s.setRoute(routeA, listOf(camX), CoverageStatus.COMPLETE)
        walk(s, 0, 1_300)
        assertTrue(s.phase is RadarPhase.Alert)

        val routeB = route(1_300 to 3, 3_000 to 3)
        s.setRoute(routeB, listOf(camX), CoverageStatus.COMPLETE)
        assertTrue(s.update(0.0).isEmpty())
        assertEquals(200.0, (s.phase as RadarPhase.Alert).distanceM.toDouble(), 1.0)
        val after = walk(s, 10, 400)
        assertEquals(listOf("Passed:osm:node/1", "ReturnedToNormal:osm:node/1"), after.map { tag(it.second) })
    }

    @Test
    fun `recalculo que tira o radar da rota encerra o alerta`() {
        val s = RouteRadarSession()
        s.setRoute(routeA, listOf(camX), CoverageStatus.COMPLETE)
        walk(s, 0, 1_300)
        val routeNorth = route(1_300 to 0, 1_300 to 2_000)
        s.setRoute(routeNorth, listOf(camX), CoverageStatus.COMPLETE)
        assertTrue(s.matches.isEmpty())
        assertEquals(listOf("ReturnedToNormal:osm:node/1"), s.update(0.0).map(::tag))
        assertEquals(RadarPhase.Clear, s.phase)
        assertTrue(walk(s, 10, 2_000).isEmpty())
    }

    @Test
    fun `rota que passa duas vezes alerta em cada passagem`() {
        val loop = route(0 to 0, 2_000 to 0, 2_000 to 500, 0 to 500, 0 to 0, 2_000 to 0)
        val s = RouteRadarSession()
        s.setRoute(loop, listOf(camera(1, 800.0, 0.0, directionDeg = 90.0)), CoverageStatus.COMPLETE)
        val events = walk(s, 0, 7_000).map { tag(it.second) }
        assertEquals(
            listOf(
                "Detected:osm:node/1", "AlertStarted:osm:node/1", "Passed:osm:node/1", "ReturnedToNormal:osm:node/1",
                "Detected:osm:node/1#2", "AlertStarted:osm:node/1#2", "Passed:osm:node/1#2", "ReturnedToNormal:osm:node/1#2",
            ),
            events,
        )
    }

    @Test
    fun `limiares configuraveis e fim da navegacao`() {
        val s = RouteRadarSession(RadarConfig(searchRadiusM = 500, alertRadiusM = 200))
        s.setRoute(routeA, listOf(camX), CoverageStatus.STALE)
        assertEquals("Radares desatualizados", s.statusText)
        val along = s.matches.single().alongM
        val events = walk(s, 0, 1_400)
        assertEquals(ceil10(along - 500), events[0].first)
        assertEquals(ceil10(along - 200), events[1].first)
        assertTrue(s.phase is RadarPhase.Alert)

        assertEquals(listOf("ReturnedToNormal:osm:node/1"), s.end().map(::tag))
        assertEquals(RadarPhase.Clear, s.phase)
        assertFalse(s.alertsEnabled)
        assertTrue(s.update(1_450.0).isEmpty())
    }

    @Test
    fun `camadas de tiles sao combinadas sem repetir radar`() {
        val t1 = RadarTileData(TileKey.of(RadarTestGeo.ORIGIN), 1_000L, listOf(camX))
        val t2 = RadarTileData(TileKey.of(RadarTestGeo.ORIGIN), 2_000L, listOf(camX.copy(maxSpeedKmh = 50)))
        val s = RouteRadarSession()
        s.setRouteFromTiles(routeA, listOf(t1, t2), CoverageStatus.COMPLETE)
        assertEquals(50, s.matches.single().camera.maxSpeedKmh) // fica o tile mais recente
    }
}
