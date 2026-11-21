package dev.hunterlab.core.navigation

import dev.hunterlab.core.display.DisplaySettings
import dev.hunterlab.core.display.ScreenComposer
import dev.hunterlab.core.geo.GeoPoint
import dev.hunterlab.core.geo.Polyline
import dev.hunterlab.core.nav.NavigationSnapshot
import dev.hunterlab.core.nav.NavigationStatus
import dev.hunterlab.core.radar.RadarPhase
import dev.hunterlab.core.route.Fixtures
import dev.hunterlab.core.route.GraphHopperRouteProvider
import dev.hunterlab.core.route.HttpGet
import dev.hunterlab.core.route.HttpResult
import dev.hunterlab.core.route.PolylineCodec
import dev.hunterlab.protocol.Maneuver
import dev.hunterlab.protocol.Side
import dev.hunterlab.protocol.TripperIcons
import dev.hunterlab.protocol.TripperScreen
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalTime

/** Fixture GraphHopper → provedor → motor dirigido pelo simulador → snapshot → ScreenComposer → tela do Tripper. */
class NavigationEndToEndTest {

    private val route = Fixtures.route(Fixtures.TURNS)
    private val settings = DisplaySettings()
    private val noon = LocalTime.NOON

    private fun compose(s: NavigationSnapshot) = ScreenComposer.compose(s, RadarPhase.Clear, settings, noon)

    /** Corpo GraphHopper gerado na hora: origem → (desce até a latitude do destino) → destino, com curva à direita. */
    private fun rerouteBody(origin: GeoPoint, destination: GeoPoint): String {
        val points = listOf(origin, GeoPoint(destination.lat, origin.lon), destination)
        val c = Polyline(points).cumulative
        fun ins(sign: Int, from: Int, to: Int, text: String) = JsonObject(
            mapOf(
                "distance" to JsonPrimitive(c[to] - c[from]),
                "sign" to JsonPrimitive(sign),
                "interval" to JsonArray(listOf(JsonPrimitive(from), JsonPrimitive(to))),
                "text" to JsonPrimitive(text),
                "time" to JsonPrimitive(((c[to] - c[from]) / 10.0 * 1000).toLong()),
                "street_name" to JsonPrimitive(""),
            ),
        )
        val path = JsonObject(
            mapOf(
                "distance" to JsonPrimitive(c.last()),
                "time" to JsonPrimitive((c.last() / 10.0 * 1000).toLong()),
                "points_encoded" to JsonPrimitive(true),
                "points" to JsonPrimitive(PolylineCodec.encode(points)),
                "instructions" to JsonArray(
                    listOf(ins(0, 0, 1, "Siga"), ins(2, 1, 2, "Vire à direita"), ins(4, 2, 2, "Chegada ao destino")),
                ),
            ),
        )
        return JsonObject(mapOf("paths" to JsonArray(listOf(path)))).toString()
    }

    private class Rig(private val bodyFor: (call: Int, url: String) -> String) {
        val urls = mutableListOf<String>()
        val provider = GraphHopperRouteProvider(
            http = HttpGet { url, _ ->
                urls += url
                HttpResult.Ok(200, bodyFor(urls.size, url))
            },
            apiKey = { "chave-de-teste" },
        )
        val engine = NavigationEngine()

        /** Snapshot no instante de cada pedido (o que o Tripper mostra enquanto a rota não chega). */
        val atRequest = mutableListOf<NavigationSnapshot>()

        /** Executa os efeitos como o app faria (aqui, síncrono). */
        fun run(effects: List<NavigationEffect>, now: Long) {
            for (e in effects) if (e is NavigationEffect.RequestRoute) {
                atRequest += engine.toSnapshot()
                run(engine.onRouteResult(e.requestId, provider.route(e.request), now), now)
            }
        }
    }

    @Test
    fun `fixture dirigida pelo simulador vira telas de navegacao no Tripper`() {
        val rig = Rig { _, _ -> Fixtures.text(Fixtures.TURNS) }
        rig.run(rig.engine.start(route.destination, 0), 0)
        assertEquals(TripperScreen.NativeClock, compose(rig.engine.toSnapshot()).screen, "sem rota: relógio nativo")

        val snapshots = mutableListOf<NavigationSnapshot>()
        for (fix in RouteDriveSimulator(route, speedMps = 10.0, startTimeMs = 1_000, noiseM = 3.0, accuracyM = 6f).fixes()) {
            rig.run(rig.engine.onFix(fix, fix.timeMs), fix.timeMs)
            snapshots += rig.engine.toSnapshot()
        }
        assertEquals(1, rig.urls.size, "um único pedido de rota no trajeto inteiro")
        assertTrue(rig.urls.single().contains("key=chave-de-teste"))
        assertTrue(snapshots.none { it.status == NavigationStatus.RECALCULATING }, "seguindo a rota não pode recalcular")

        // ~100 m antes da curva à direita: ícone 0x15 e seta pequena da próxima (leve à esquerda, 0x18).
        val beforeTurn = snapshots.first { it.maneuver == Maneuver.Turn(Side.RIGHT) && it.distanceToManeuverM!! <= 110 }
        assertEquals(Maneuver.Turn(Side.LEFT, dev.hunterlab.protocol.TurnSharpness.SLIGHT), beforeTurn.nextManeuver)
        val frame = compose(beforeTurn)
        val screen = frame.screen as TripperScreen.Navigation
        assertEquals(0x15, screen.frame.icon)
        assertEquals(0x18, screen.frame.nextIcon)
        assertEquals(beforeTurn.distanceToManeuverM, screen.frame.distanceMeters)
        assertTrue(screen.frame.distanceMeters in 90..110)
        assertNotNull(frame.packet)

        // As distâncias até a manobra só diminuem dentro de cada manobra.
        snapshots.filter { it.status == NavigationStatus.NAVIGATING }.zipWithNext().forEach { (a, b) ->
            if (a.maneuver == b.maneuver) assertTrue(b.distanceToManeuverM!! <= a.distanceToManeuverM!!, "$a → $b")
            assertTrue(b.remainingDistanceM!! <= a.remainingDistanceM!!)
        }

        // Chegada: tela de destino a 0 m.
        val last = snapshots.last()
        assertEquals(NavigationStatus.ARRIVED, last.status)
        val arrived = compose(last).screen as TripperScreen.Navigation
        assertEquals(TripperIcons.DESTINATION, arrived.frame.icon)
        assertEquals(0, arrived.frame.distanceMeters)
        assertEquals(EngineStatus.ARRIVED, rig.engine.state.status)
    }

    @Test
    fun `curva perdida recalcula e volta a navegar na rota nova`() {
        val pointRegex = Regex("point=(-?[0-9.]+),(-?[0-9.]+)")
        val rig = Rig { call, url ->
            if (call == 1) {
                Fixtures.text(Fixtures.TURNS)
            } else {
                val (o, d) = pointRegex.findAll(url).map { GeoPoint(it.groupValues[1].toDouble(), it.groupValues[2].toDouble()) }.toList()
                rerouteBody(o, d)
            }
        }
        rig.run(rig.engine.start(route.destination, 0), 0)
        val turnAt = route.steps[2].atAlongM // curva à direita, ~400 m
        val sim = RouteDriveSimulator(route, speedMps = 10.0, startTimeMs = 1_000, deviation = SimDeviation.GoStraight(turnAt))
        var rerouted: NavigationSnapshot? = null
        for (fix in sim.fixes()) {
            rig.run(rig.engine.onFix(fix, fix.timeMs), fix.timeMs)
            if (rig.engine.state.rerouteCount == 1) {
                rerouted = rig.engine.toSnapshot()
                break
            }
        }
        assertEquals(2, rig.urls.size)
        // Pedido inicial com o Pod no relógio; recálculo com a tela "recalculando".
        assertEquals(TripperScreen.NativeClock, compose(rig.atRequest[0]).screen)
        assertEquals(NavigationStatus.RECALCULATING, rig.atRequest[1].status)
        assertTrue(compose(rig.atRequest[1]).screen is TripperScreen.Recalculating)
        assertNotNull(rerouted)
        val snap = rerouted!!
        assertEquals(NavigationStatus.NAVIGATING, snap.status)
        assertEquals(Maneuver.Turn(Side.RIGHT), snap.maneuver)
        assertEquals(0x15, (compose(snap).screen as TripperScreen.Navigation).frame.icon)
        assertTrue(rig.urls.all { GraphHopperRouteProvider.redact(it).contains("key=***") })
    }
}
