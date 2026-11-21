package dev.hunterlab.core.navigation

import dev.hunterlab.core.geo.GeoPoint
import dev.hunterlab.core.nav.NavigationStatus
import dev.hunterlab.core.navigation.TestRoutes.fixAlong
import dev.hunterlab.core.navigation.TestRoutes.fixAt
import dev.hunterlab.core.route.Route
import dev.hunterlab.core.route.RouteFailureKind
import dev.hunterlab.core.route.RouteResult
import dev.hunterlab.protocol.Maneuver
import dev.hunterlab.protocol.Side
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NavigationEngineTest {

    private val route = TestRoutes.lRoute()
    private val destination = route.destination

    private fun failure(kind: RouteFailureKind) = RouteResult.Failure(kind, "falha de teste (${kind.name})")

    private fun List<NavigationEffect>.requests() = filterIsInstance<NavigationEffect.RequestRoute>()

    /** Rota de recálculo: do ponto do pedido até o destino, com uma curva. */
    private fun rerouteFrom(origin: GeoPoint): Route {
        val corner = GeoPoint(origin.lat, destination.lon)
        return Route(
            polyline = dev.hunterlab.core.geo.Polyline(listOf(origin, corner, destination)),
            steps = listOf(
                dev.hunterlab.core.route.RouteStep(Maneuver.Depart, 0.0, 0.0, 0.0, null, null),
            ),
            distanceM = 0.0,
            durationS = 60.0,
            providerId = "teste",
            attribution = "teste",
        )
    }

    /** Motor já navegando na [route], com fixes sobre ela até [along] (1 fix/s a 10 m/s). Devolve o tempo atual. */
    private fun navigating(engine: NavigationEngine, along: Double = 30.0): Long {
        val first = engine.onFix(fixAlong(route, 0.0, 0), 0)
        assertTrue(first.isEmpty())
        val req = engine.start(destination, 0).requests().single()
        engine.onRouteResult(req.requestId, RouteResult.Success(route), 0)
        var t = 0L
        var a = 10.0
        while (a <= along) {
            t += 1_000
            engine.onFix(fixAlong(route, a, t), t)
            a += 10.0
        }
        assertEquals(EngineStatus.NAVIGATING, engine.state.status)
        return t
    }

    /** Fix bem fora da rota (100 m ao norte da 1ª rua), parado. */
    private fun away(t: Long) = fixAt(40.0, 100.0, t, accuracy = 5f)

    @Test
    fun `sem fix valido nao pede rota`() {
        val engine = NavigationEngine()
        assertTrue(engine.start(destination, 0).isEmpty())
        assertEquals(EngineStatus.ROUTING, engine.state.status)
        assertEquals("Aguardando sinal de GPS…", engine.state.message)
        assertEquals(NavigationStatus.IDLE, engine.toSnapshot().status)
        // Fix impreciso também não serve.
        assertTrue(engine.onFix(fixAlong(route, 0.0, 1_000, accuracy = 120f), 1_000).isEmpty())
        // Fix bom: pede rota com origem nele e rumo (está em movimento).
        val req = engine.onFix(fixAlong(route, 0.0, 2_000, speed = 10f), 2_000).requests().single()
        assertEquals(route.polyline.points.first(), req.request.origin)
        assertEquals(destination, req.request.destination)
        assertEquals(90.0, req.request.originHeadingDeg!!, 1.0)
        assertEquals(req.requestId, engine.state.pendingRequestId)
        assertEquals("Calculando rota…", engine.state.message)
    }

    @Test
    fun `parado o pedido nao leva rumo`() {
        val engine = NavigationEngine()
        engine.onFix(fixAlong(route, 0.0, 0, speed = 0f), 0)
        val req = engine.start(destination, 0).requests().single()
        assertNull(req.request.originHeadingDeg)
    }

    @Test
    fun `fix antigo nao serve para o primeiro pedido`() {
        val engine = NavigationEngine()
        engine.onFix(fixAlong(route, 0.0, 0), 0)
        assertTrue(engine.start(destination, 15_000).isEmpty(), "fix de 15 s atrás está vencido")
        assertTrue(engine.state.gpsLost)
        assertEquals(1, engine.onFix(fixAlong(route, 0.0, 15_500), 15_500).requests().size)
    }

    @Test
    fun `rota recebida passa a navegar e gera snapshot`() {
        val engine = NavigationEngine()
        val t = navigating(engine, along = 200.0)
        assertEquals(0, engine.state.rerouteCount)
        val snap = engine.toSnapshot()
        assertEquals(NavigationStatus.NAVIGATING, snap.status)
        assertEquals(Maneuver.Turn(Side.RIGHT), snap.maneuver)
        assertEquals(300.0, snap.distanceToManeuverM!!.toDouble(), 2.0)
        assertEquals(Maneuver.Arrive(), snap.nextManeuver)
        assertEquals(route.polyline.lengthM - 200, snap.remainingDistanceM!!.toDouble(), 2.0)
        assertEquals(2, snap.remainingTimeMin) // ~70 s restantes → arredonda para cima
        assertEquals("teste", snap.sourceId)
        assertFalse(snap.simulated)
        assertTrue(t > 0)
    }

    @Test
    fun `resultado de pedido antigo e ignorado`() {
        val engine = NavigationEngine()
        engine.onFix(fixAlong(route, 0.0, 0), 0)
        val first = engine.start(destination, 0).requests().single()
        // Reinicia: o pedido 1 é cancelado e sai o 2.
        val restart = engine.start(destination, 500)
        assertTrue(restart.contains(NavigationEffect.CancelRequest(first.requestId)))
        val second = restart.requests().single()
        assertTrue(second.requestId > first.requestId)
        assertTrue(engine.onRouteResult(first.requestId, RouteResult.Success(rerouteFrom(fixAt(0.0, 50.0, 0).point)), 600).isEmpty())
        assertEquals(EngineStatus.ROUTING, engine.state.status)
        engine.onRouteResult(second.requestId, RouteResult.Success(route), 700)
        assertSame(route, engine.state.route)
        // O antigo chegando depois também não substitui a rota.
        engine.onRouteResult(first.requestId, RouteResult.Success(rerouteFrom(fixAt(0.0, 50.0, 0).point)), 800)
        assertSame(route, engine.state.route)
        // Id que nunca existiu.
        assertTrue(engine.onRouteResult(999, failure(RouteFailureKind.NETWORK), 900).isEmpty())
        assertNull(engine.state.lastFailure)
    }

    @Test
    fun `fix fora de ordem e ignorado`() {
        val engine = NavigationEngine()
        val t = navigating(engine, along = 100.0)
        val before = engine.state.track
        assertTrue(engine.onFix(away(t - 5_000), t + 100).isEmpty())
        assertTrue(engine.onFix(fixAlong(route, 100.0, t), t + 200).isEmpty()) // mesmo instante do último
        assertEquals(before, engine.state.track)
        assertEquals(2, engine.state.ignoredFixCount)
    }

    @Test
    fun `desvio confirmado pede um unico recalculo e nunca um por fix`() {
        val engine = NavigationEngine()
        var t = navigating(engine, along = 30.0) // t = 3 s
        t = 20_000 // passou o intervalo mínimo desde o 1º pedido
        engine.onFix(fixAlong(route, 40.0, t), t)
        assertTrue(engine.onFix(away(t + 1_000), t + 1_000).isEmpty())
        assertTrue(engine.onFix(away(t + 2_000), t + 2_000).isEmpty())
        assertEquals(EngineStatus.NAVIGATING, engine.state.status, "2 fixes ainda não confirmam")
        val effects = engine.onFix(away(t + 3_000), t + 3_000)
        val req = effects.requests().single()
        assertEquals(away(0).point, req.request.origin)
        assertEquals(EngineStatus.REROUTING, engine.state.status)
        assertEquals(NavigationStatus.RECALCULATING, engine.toSnapshot().status)
        // 20 fixes fora da rota enquanto o pedido está pendente: nenhum pedido novo.
        var total = 0
        for (i in 4..23) total += engine.onFix(away(t + i * 1_000L), t + i * 1_000L).requests().size
        assertEquals(0, total)
        // Resposta: nova rota, recálculo contado.
        engine.onRouteResult(req.requestId, RouteResult.Success(rerouteFrom(req.request.origin)), t + 24_000)
        assertEquals(1, engine.state.rerouteCount)
        assertEquals(EngineStatus.NAVIGATING, engine.state.status)
        assertEquals(0.0, engine.state.track!!.progressM, 1.0)
    }

    @Test
    fun `intervalo minimo entre pedidos`() {
        val engine = NavigationEngine()
        val t = navigating(engine, along = 30.0) // 1º pedido em t=0
        for (i in 1..3) engine.onFix(away(t + i * 1_000L), t + i * 1_000L) // confirma em 6 s
        assertEquals(EngineStatus.OFF_ROUTE, engine.state.status)
        assertNull(engine.state.pendingRequestId)
        assertEquals(10_000L, engine.state.nextRequestAtMs)
        assertEquals(NavigationStatus.RECALCULATING, engine.toSnapshot().status)
        assertTrue(engine.state.message.startsWith("Fora da rota"), engine.state.message)
        assertTrue(engine.onTick(9_999).isEmpty())
        assertEquals(1, engine.onTick(10_000).requests().size)
        assertEquals(EngineStatus.REROUTING, engine.state.status)
    }

    /** Mantém o motor fora da rota (1 fix/s) até [untilMs], falhando todo pedido com [kind]. Devolve os instantes dos pedidos. */
    private fun failEveryRequest(engine: NavigationEngine, fromMs: Long, untilMs: Long, kind: RouteFailureKind): List<Long> {
        val times = mutableListOf<Long>()
        var t = fromMs
        while (t <= untilMs) {
            for (req in engine.onFix(away(t), t).requests()) {
                times += t
                engine.onRouteResult(req.requestId, failure(kind), t)
            }
            t += 1_000
        }
        return times
    }

    @Test
    fun `backoff exponencial em falhas de recalculo`() {
        val engine = NavigationEngine(NavigationConfig(minRerouteIntervalMs = 0, maxRequestsPerWindow = 100))
        val t = navigating(engine, along = 30.0)
        val times = failEveryRequest(engine, t + 1_000, 260_000, RouteFailureKind.NETWORK)
        assertEquals(listOf(5_000L, 10_000L, 20_000L, 40_000L, 60_000L, 60_000L), times.zipWithNext { a, b -> b - a }.take(6))
        assertEquals(RouteFailureKind.NETWORK, engine.state.lastFailure!!.kind)
        assertSame(route, engine.state.route, "a rota antiga continua guardada")
        assertEquals(EngineStatus.OFF_ROUTE, engine.state.status)
        assertTrue(engine.state.message.contains("Nova tentativa em"), engine.state.message)
    }

    @Test
    fun `teto de pedidos por janela`() {
        val engine = NavigationEngine(NavigationConfig(backoffMs = listOf(0)))
        val t = navigating(engine, along = 30.0) // pedido inicial em t=0
        val times = failEveryRequest(engine, t + 1_000, 320_000, RouteFailureKind.SERVER)
        // 0 (inicial), 10, 20, 30, 40, 50 s = 6 na janela; o 7º só quando o de t=0 sai da janela.
        assertEquals(listOf(10_000L, 20_000L, 30_000L, 40_000L, 50_000L, 300_000L), times.take(6))
    }

    @Test
    fun `falha de rede mantem a rota antiga e volta a navegar nela`() {
        val engine = NavigationEngine()
        var t = navigating(engine, along = 30.0)
        t = 20_000
        for (i in 1..3) engine.onFix(away(t + i * 1_000L), t + i * 1_000L)
        val req = engine.state.pendingRequestId!!
        engine.onRouteResult(req, failure(RouteFailureKind.NETWORK), t + 4_000)
        assertSame(route, engine.state.route)
        assertEquals(EngineStatus.OFF_ROUTE, engine.state.status)
        // Volta para a rota (2 fixes bons): navega na rota antiga, sem novo pedido.
        engine.onFix(fixAlong(route, 60.0, t + 5_000), t + 5_000)
        val effects = engine.onFix(fixAlong(route, 70.0, t + 6_000), t + 6_000)
        assertTrue(effects.requests().isEmpty())
        assertEquals(EngineStatus.NAVIGATING, engine.state.status)
        assertEquals(NavigationStatus.NAVIGATING, engine.toSnapshot().status)
        assertEquals(Maneuver.Turn(Side.RIGHT), engine.toSnapshot().maneuver)
        assertEquals("Navegando (rota anterior mantida)", engine.state.message)
    }

    @Test
    fun `voltar a rota durante o recalculo cancela o pedido`() {
        val engine = NavigationEngine()
        var t = navigating(engine, along = 30.0)
        t = 20_000
        for (i in 1..3) engine.onFix(away(t + i * 1_000L), t + i * 1_000L)
        val req = engine.state.pendingRequestId!!
        engine.onFix(fixAlong(route, 60.0, t + 4_000), t + 4_000)
        val effects = engine.onFix(fixAlong(route, 70.0, t + 5_000), t + 5_000)
        assertTrue(effects.contains(NavigationEffect.CancelRequest(req)))
        assertEquals(EngineStatus.NAVIGATING, engine.state.status)
        // A resposta atrasada é ignorada.
        engine.onRouteResult(req, RouteResult.Success(rerouteFrom(away(0).point)), t + 6_000)
        assertSame(route, engine.state.route)
        assertEquals(0, engine.state.rerouteCount)
    }

    @Test
    fun `perda de GPS sinaliza e impede recalculo`() {
        val engine = NavigationEngine()
        var t = navigating(engine, along = 30.0)
        t = 20_000
        for (i in 1..3) engine.onFix(away(t + i * 1_000L), t + i * 1_000L)
        engine.onRouteResult(engine.state.pendingRequestId!!, failure(RouteFailureKind.TIMEOUT), t + 3_000)
        assertFalse(engine.state.gpsLost)
        // Sem fixes: depois de 10 s, gpsLost; nenhum pedido sai mesmo passado o intervalo/backoff.
        assertTrue(engine.onTick(t + 12_000).isEmpty())
        assertFalse(engine.state.gpsLost)
        for (s in 14..120) assertTrue(engine.onTick(t + s * 1_000L).isEmpty())
        assertTrue(engine.state.gpsLost)
        assertTrue(engine.state.message.contains("aguardando GPS"), engine.state.message)
        // Fixes imprecisos não contam como sinal.
        engine.onFix(away(t + 121_000).copy(accuracyM = 90f), t + 121_000)
        assertTrue(engine.state.gpsLost)
        // GPS volta (fora da rota): pede imediatamente.
        val effects = engine.onFix(away(t + 122_000), t + 122_000)
        assertFalse(engine.state.gpsLost)
        assertEquals(1, effects.requests().size)
    }

    @Test
    fun `perda de GPS navegando mantem a tela de navegacao`() {
        val engine = NavigationEngine()
        val t = navigating(engine, along = 100.0)
        engine.onTick(t + 10_000)
        assertFalse(engine.state.gpsLost, "exatamente 10 s ainda não é perda")
        engine.onTick(t + 10_001)
        assertTrue(engine.state.gpsLost)
        assertEquals("Sinal de GPS perdido", engine.state.message)
        assertEquals(NavigationStatus.NAVIGATING, engine.toSnapshot().status)
        engine.onFix(fixAlong(route, 110.0, t + 11_000), t + 11_000)
        assertFalse(engine.state.gpsLost)
    }

    @Test
    fun `GPS impreciso nao gera desvio nem pedido`() {
        val engine = NavigationEngine()
        val t = navigating(engine, along = 30.0)
        var requests = 0
        for (i in 1..30) requests += engine.onFix(away(t + i * 1_000L).copy(accuracyM = 75f), t + i * 1_000L).requests().size
        assertEquals(0, requests)
        assertEquals(EngineStatus.NAVIGATING, engine.state.status)
        assertTrue(engine.state.gpsLost, "30 s só com fixes imprecisos")
        assertTrue(engine.state.track!!.lastFixImprecise)
    }

    @Test
    fun `chave ausente ou invalida vai para FAILED sem repetir`() {
        for (kind in listOf(RouteFailureKind.NOT_CONFIGURED, RouteFailureKind.INVALID_KEY)) {
            val engine = NavigationEngine()
            engine.onFix(fixAlong(route, 0.0, 0), 0)
            val req = engine.start(destination, 0).requests().single()
            engine.onRouteResult(req.requestId, failure(kind), 100)
            assertEquals(EngineStatus.FAILED, engine.state.status)
            assertEquals(kind, engine.state.lastFailure!!.kind)
            assertEquals(engine.state.lastFailure!!.message, engine.state.message)
            assertEquals(NavigationStatus.IDLE, engine.toSnapshot().status)
            var effects = 0
            for (s in 1..600) {
                effects += engine.onFix(away(s * 1_000L), s * 1_000L).size
                effects += engine.onTick(s * 1_000L + 500).size
            }
            assertEquals(0, effects, kind.name)
        }
    }

    @Test
    fun `chave invalida durante recalculo tambem encerra`() {
        val engine = NavigationEngine()
        var t = navigating(engine, along = 30.0)
        t = 20_000
        for (i in 1..3) engine.onFix(away(t + i * 1_000L), t + i * 1_000L)
        engine.onRouteResult(engine.state.pendingRequestId!!, failure(RouteFailureKind.INVALID_KEY), t + 4_000)
        assertEquals(EngineStatus.FAILED, engine.state.status)
        assertTrue(engine.onTick(t + 600_000).isEmpty())
    }

    @Test
    fun `sem rota na rota inicial falha mas no recalculo tenta de novo`() {
        val initial = NavigationEngine()
        initial.onFix(fixAlong(route, 0.0, 0), 0)
        initial.onRouteResult(initial.start(destination, 0).requests().single().requestId, failure(RouteFailureKind.NO_ROUTE), 100)
        assertEquals(EngineStatus.FAILED, initial.state.status)

        val reroute = NavigationEngine()
        var t = navigating(reroute, along = 30.0)
        t = 20_000
        for (i in 1..3) reroute.onFix(away(t + i * 1_000L), t + i * 1_000L)
        reroute.onRouteResult(reroute.state.pendingRequestId!!, failure(RouteFailureKind.NO_ROUTE), t + 3_000)
        assertEquals(EngineStatus.OFF_ROUTE, reroute.state.status)
        val times = failEveryRequest(reroute, t + 4_000, t + 30_000, RouteFailureKind.NO_ROUTE)
        // Intervalo mínimo (10 s) e backoff (5 s, 10 s, 20 s...): continua tentando, sem martelar.
        assertEquals(listOf(t + 13_000, t + 23_000), times)
    }

    @Test
    fun `falha transitoria na rota inicial tenta de novo com backoff`() {
        val engine = NavigationEngine()
        engine.onFix(fixAlong(route, 0.0, 0, speed = 0f), 0)
        val req = engine.start(destination, 0).requests().single()
        engine.onRouteResult(req.requestId, failure(RouteFailureKind.NETWORK), 1_000)
        assertEquals(EngineStatus.ROUTING, engine.state.status)
        assertEquals(10_000L, engine.state.nextRequestAtMs) // max(0 + 10 s, 1 s + 5 s)
        assertTrue(engine.state.message.contains("Nova tentativa em 9 s"), engine.state.message)
        var t = 1_000L
        var retry: NavigationEffect.RequestRoute? = null
        while (retry == null && t < 20_000) {
            t += 1_000
            retry = engine.onFix(fixAlong(route, 0.0, t, speed = 0f), t).requests().singleOrNull()
        }
        assertEquals(10_000L, t)
        engine.onRouteResult(retry!!.requestId, RouteResult.Success(route), t)
        assertEquals(EngineStatus.NAVIGATING, engine.state.status)
        assertNull(engine.state.lastFailure)
        assertEquals(0, engine.state.rerouteCount, "rota inicial não é recálculo")
    }

    @Test
    fun `pedido sem resposta expira e conta como falha`() {
        val engine = NavigationEngine()
        engine.onFix(fixAlong(route, 0.0, 0, speed = 0f), 0)
        val req = engine.start(destination, 0).requests().single()
        var t = 0L
        var effects: List<NavigationEffect> = emptyList()
        while (effects.isEmpty()) {
            t += 1_000
            effects = engine.onFix(fixAlong(route, 0.0, t, speed = 0f), t)
        }
        assertEquals(30_000L, t)
        assertTrue(effects.contains(NavigationEffect.CancelRequest(req.requestId)))
        assertEquals(RouteFailureKind.TIMEOUT, engine.state.lastFailure!!.kind)
        // Resposta tardia ignorada.
        engine.onRouteResult(req.requestId, RouteResult.Success(route), t + 1)
        assertNull(engine.state.route)
    }

    @Test
    fun `chegada encerra recalculos`() {
        val engine = NavigationEngine()
        val t = navigating(engine, along = 870.0)
        engine.onFix(fixAlong(route, 880.0, t + 1_000), t + 1_000)
        assertEquals(EngineStatus.ARRIVED, engine.state.status)
        val snap = engine.toSnapshot()
        assertEquals(NavigationStatus.ARRIVED, snap.status)
        assertEquals(0, snap.distanceToManeuverM)
        assertEquals(0, snap.remainingDistanceM)
        assertEquals(Maneuver.Arrive(), snap.maneuver)
        var effects = 0
        for (s in 100..200) effects += engine.onFix(away(s * 1_000L), s * 1_000L).size
        assertEquals(0, effects)
        assertEquals(EngineStatus.ARRIVED, engine.state.status)
        assertEquals("Você chegou ao destino", engine.state.message)
    }

    @Test
    fun `stop cancela o pedido pendente e volta ao relogio`() {
        val engine = NavigationEngine()
        engine.onFix(fixAlong(route, 0.0, 0), 0)
        val req = engine.start(destination, 0).requests().single()
        assertEquals(listOf(NavigationEffect.CancelRequest(req.requestId)), engine.stop())
        assertEquals(EngineStatus.IDLE, engine.state.status)
        assertEquals(NavigationStatus.IDLE, engine.toSnapshot().status)
        assertTrue(engine.onRouteResult(req.requestId, RouteResult.Success(route), 10).isEmpty())
        assertNull(engine.state.route)
        // Novo start aproveita o último fix (ainda recente).
        assertEquals(1, engine.start(destination, 1_000).requests().size)
    }

    @Test
    fun `snapshot nunca tem distancias negativas`() {
        val r = TestRoutes.build(
            listOf(0.0 to 0.0, 500.0 to 0.0, 500.0 to -400.0),
            mapOf(1 to Maneuver.Turn(Side.RIGHT)),
            stepLengths = listOf(0.0, 0.0, 0.0),
            distanceM = 0.0,
            durations = listOf(0.0, 0.0, 0.0),
        )
        val engine = NavigationEngine()
        engine.onFix(fixAlong(r, 0.0, 0), 0)
        engine.onRouteResult(engine.start(r.destination, 0).requests().single().requestId, RouteResult.Success(r), 0)
        var t = 0L
        for (a in 10..600 step 10) {
            t += 1_000
            engine.onFix(fixAlong(r, a.toDouble(), t), t)
            val s = engine.toSnapshot()
            assertTrue(s.distanceToManeuverM!! >= 0 && s.remainingDistanceM!! >= 0 && s.remainingTimeMin!! >= 0)
        }
        assertEquals(0, engine.toSnapshot().remainingTimeMin) // sem durações: 0, não negativo
    }
}
