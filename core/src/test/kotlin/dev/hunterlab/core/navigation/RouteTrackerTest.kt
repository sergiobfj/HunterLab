package dev.hunterlab.core.navigation

import dev.hunterlab.core.navigation.TestRoutes.fixAlong
import dev.hunterlab.core.navigation.TestRoutes.fixAt
import dev.hunterlab.core.route.Fixtures
import dev.hunterlab.core.route.Route
import dev.hunterlab.protocol.Maneuver
import dev.hunterlab.protocol.Side
import dev.hunterlab.protocol.TurnSharpness
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RouteTrackerTest {

    private val route = TestRoutes.lRoute() // leste 500 m → direita → sul 400 m
    private val length = route.polyline.lengthM

    /** Dirige até [along] a 10 m/s (1 fix/s) e devolve o tempo do último fix. */
    private fun RouteTracker.driveTo(along: Double, fromAlong: Double = 0.0, startMs: Long = 0): Long {
        var a = fromAlong
        var t = startMs
        while (a < along) {
            update(fixAlong(route, a, t))
            a += 10.0
            t += 1_000
        }
        update(fixAlong(route, along, t))
        return t
    }

    /** Manobras "próximas" observadas em ordem, sem repetições, dirigindo com o simulador. */
    private fun maneuverSequence(r: Route, speed: Double = 8.0): List<Maneuver> {
        val tracker = RouteTracker(r)
        val seen = mutableListOf<Maneuver>()
        var lastProgress = 0.0
        for (fix in RouteDriveSimulator(r, speed).fixes()) {
            val s = tracker.update(fix)
            assertTrue(s.progressM >= lastProgress, "progresso voltou: ${s.progressM} < $lastProgress")
            assertFalse(s.offRoute, "não deveria desviar seguindo a rota (progresso ${s.progressM})")
            lastProgress = s.progressM
            if (seen.lastOrNull() != s.nextManeuver) seen += s.nextManeuver
        }
        assertTrue(tracker.state.arrived, "deveria ter chegado")
        return seen
    }

    @Test
    fun `seguir em frente reduz a distancia ate a manobra`() {
        val tracker = RouteTracker(route)
        var previous = Double.MAX_VALUE
        var t = 0L
        for (a in 0..400 step 20) {
            val s = tracker.update(fixAlong(route, a.toDouble(), t))
            t += 2_000
            assertEquals(Maneuver.Turn(Side.RIGHT), s.nextManeuver)
            assertEquals(Maneuver.Arrive(), s.followingManeuver)
            assertEquals(500.0 - a, s.distanceToNextManeuverM, 1.0)
            assertTrue(s.distanceToNextManeuverM < previous)
            assertEquals(length - a, s.remainingDistanceM, 1.0)
            assertEquals(0, s.currentStepIndex)
            previous = s.distanceToNextManeuverM
        }
    }

    @Test
    fun `curva so avanca para a manobra seguinte depois de passar 15 m`() {
        val tracker = RouteTracker(route)
        val t = tracker.driveTo(495.0)
        var s = tracker.update(fixAlong(route, 505.0, t + 1_000))
        assertEquals(Maneuver.Turn(Side.RIGHT), s.nextManeuver, "ainda dentro da histerese")
        assertEquals(0.0, s.distanceToNextManeuverM, 1e-9)
        s = tracker.update(fixAlong(route, 516.0, t + 2_000))
        assertEquals(Maneuver.Arrive(), s.nextManeuver)
        assertNull(s.followingManeuver)
        assertEquals(1, s.currentStepIndex)
        assertEquals(length - 516.0, s.distanceToNextManeuverM, 1.0)
    }

    @Test
    fun `progresso nunca volta e a manobra anterior nao volta`() {
        val tracker = RouteTracker(route)
        var t = tracker.driveTo(300.0)
        // Tremor para trás (20 m): continua na rota, progresso mantido.
        var s = tracker.update(fixAlong(route, 280.0, t + 1_000))
        assertFalse(s.offRoute)
        assertEquals(300.0, s.progressM, 1.0)
        t = tracker.driveTo(530.0, fromAlong = 310.0, startMs = t + 2_000)
        assertEquals(Maneuver.Arrive(), tracker.state.nextManeuver)
        // Fix perto da curva, ainda na 1ª rua: não volta para "virar à direita".
        s = tracker.update(fixAt(490.0, 0.0, t + 1_000))
        assertEquals(Maneuver.Arrive(), s.nextManeuver)
        assertEquals(530.0, s.progressM, 1.0)
    }

    @Test
    fun `fixes fora de ordem sao ignorados`() {
        val tracker = RouteTracker(route)
        tracker.update(fixAlong(route, 50.0, 5_000))
        val before = tracker.state
        val s = tracker.update(fixAlong(route, 150.0, 3_000))
        assertEquals(before, s)
        assertEquals(before, tracker.update(fixAlong(route, 150.0, 5_000))) // mesmo instante: duplicado
    }

    @Test
    fun `GPS impreciso e ignorado e marcado`() {
        val tracker = RouteTracker(route)
        val t = tracker.driveTo(200.0)
        repeat(5) { i ->
            val s = tracker.update(fixAt(200.0, 300.0, t + 1_000L * (i + 1), accuracy = 80f))
            assertTrue(s.lastFixImprecise)
            assertFalse(s.offRoute)
            assertEquals(0, s.suspectFixCount)
            assertEquals(200.0, s.progressM, 1.0)
        }
        val s = tracker.update(fixAlong(route, 260.0, t + 6_000))
        assertFalse(s.lastFixImprecise)
        assertEquals(260.0, s.progressM, 1.0)
    }

    @Test
    fun `desvio exige 3 fixes seguidos e um ponto fora isolado nao conta`() {
        val tracker = RouteTracker(route)
        var t = tracker.driveTo(200.0)
        // Um ponto fora isolado.
        tracker.update(fixAlong(route, 210.0, t + 1_000, rightM = -60.0))
        assertEquals(1, tracker.state.suspectFixCount)
        tracker.update(fixAlong(route, 220.0, t + 2_000))
        assertEquals(0, tracker.state.suspectFixCount)
        assertFalse(tracker.state.offRoute)
        t += 2_000
        // Três seguidos.
        tracker.update(fixAlong(route, 230.0, t + 1_000, rightM = -60.0))
        tracker.update(fixAlong(route, 240.0, t + 2_000, rightM = -60.0))
        assertFalse(tracker.state.offRoute)
        val s = tracker.update(fixAlong(route, 250.0, t + 3_000, rightM = -60.0))
        assertTrue(s.offRoute)
        assertEquals(60.0, s.offsetM!!, 1.0)
        assertEquals(220.0, s.progressM, 1.0, "fora da rota o progresso fica congelado")
    }

    @Test
    fun `desvio confirmado por tempo com fixes esparsos`() {
        val tracker = RouteTracker(route)
        val t = tracker.driveTo(100.0)
        tracker.update(fixAlong(route, 110.0, t + 1_000, rightM = 80.0))
        assertFalse(tracker.state.offRoute)
        val s = tracker.update(fixAlong(route, 160.0, t + 6_000, rightM = 80.0))
        assertTrue(s.offRoute, "2 fixes suspeitos em ≥ 5 s confirmam")
    }

    @Test
    fun `limiar de desvio cresce com a impressao do GPS`() {
        val tracker = RouteTracker(route)
        val t = tracker.driveTo(100.0)
        // 40 m fora com precisão de 30 m → limiar 45 m: não é suspeito.
        repeat(4) { i -> tracker.update(fixAlong(route, 110.0 + 10 * i, t + 1_000L * (i + 1), rightM = 40.0, accuracy = 30f)) }
        assertFalse(tracker.state.offRoute)
        assertEquals(0, tracker.state.suspectFixCount)
        // Mesmo deslocamento com precisão de 5 m → limiar 35 m: suspeito.
        tracker.update(fixAlong(route, 150.0, t + 5_000, rightM = 40.0, accuracy = 5f))
        assertEquals(1, tracker.state.suspectFixCount)
    }

    @Test
    fun `volta a rota com histerese`() {
        val tracker = RouteTracker(route)
        var t = tracker.driveTo(200.0)
        repeat(3) { tracker.update(fixAlong(route, 210.0 + 10 * it, t + 1_000L * (it + 1), rightM = 60.0)) }
        assertTrue(tracker.state.offRoute)
        t += 3_000
        // 30 m: abaixo do limiar de desvio (35) mas acima do de volta (25) → continua fora.
        tracker.update(fixAlong(route, 250.0, t + 1_000, rightM = 30.0))
        tracker.update(fixAlong(route, 260.0, t + 2_000, rightM = 30.0))
        assertTrue(tracker.state.offRoute)
        tracker.update(fixAlong(route, 270.0, t + 3_000, rightM = 5.0))
        assertTrue(tracker.state.offRoute, "1 fix bom ainda não basta")
        val s = tracker.update(fixAlong(route, 280.0, t + 4_000, rightM = 5.0))
        assertFalse(s.offRoute)
        assertEquals(280.0, s.progressM, 1.0)
    }

    @Test
    fun `contramao com velocidade conta como desvio`() {
        val tracker = RouteTracker(route)
        val t = tracker.driveTo(300.0)
        // Parado/lento com rumo invertido: não conta.
        tracker.update(fixAlong(route, 300.0, t + 1_000, speed = 2f, bearing = 270f))
        assertEquals(0, tracker.state.suspectFixCount)
        repeat(3) { i -> tracker.update(fixAlong(route, 290.0 - 10 * i, t + 1_000L * (i + 2), bearing = 270f)) }
        assertTrue(tracker.state.offRoute)
        assertTrue(tracker.state.wrongWay)
        assertEquals(0.0, tracker.state.offsetM!!, 1.0)
    }

    @Test
    fun `retorno sobre a mesma via nao pula para a pista de volta`() {
        // Vai 300 m ao norte e volta pela mesma geometria (pior caso: offsets iguais nas duas "pistas").
        val r = TestRoutes.build(
            listOf(0.0 to 0.0, 0.0 to 300.0, 0.0 to 0.0),
            mapOf(1 to Maneuver.UTurn),
        )
        val tracker = RouteTracker(r)
        var t = 0L
        for (y in 0..280 step 10) {
            tracker.update(fixAt(0.0, y.toDouble(), t, bearing = 0f, speed = 10f))
            t += 1_000
        }
        assertEquals(280.0, tracker.state.progressM, 1.0)
        assertEquals(Maneuver.UTurn, tracker.state.nextManeuver)
        // Parado (sem rumo) um pouco à frente: continuidade decide pela ida.
        tracker.update(fixAt(0.0, 285.0, t, bearing = null, speed = 0f))
        assertEquals(285.0, tracker.state.progressM, 1.0)
        t += 1_000
        tracker.update(fixAt(0.0, 300.0, t, bearing = 0f, speed = 5f))
        t += 1_000
        // Depois do retorno, rumo sul: agora sim a pista de volta.
        tracker.update(fixAt(0.0, 290.0, t, bearing = 180f, speed = 8f))
        assertEquals(310.0, tracker.state.progressM, 1.0)
        assertFalse(tracker.state.wrongWay)
        t += 1_000
        val s = tracker.update(fixAt(0.0, 280.0, t, bearing = 180f, speed = 8f))
        assertEquals(320.0, s.progressM, 1.0)
        assertEquals(Maneuver.Arrive(), s.nextManeuver)
    }

    @Test
    fun `laco que passa perto nao faz o progresso pular`() {
        // Norte 500, leste 300, sul 400, oeste 500: o último trecho cruza o primeiro em (0,100), ~1500 m depois.
        val r = TestRoutes.build(listOf(0.0 to 0.0, 0.0 to 500.0, 300.0 to 500.0, 300.0 to 100.0, -200.0 to 100.0))
        val tracker = RouteTracker(r)
        var t = 0L
        for (y in 0..90 step 10) {
            tracker.update(fixAt(0.0, y.toDouble(), t, bearing = 0f, speed = 10f))
            t += 1_000
        }
        val trap = fixAt(-1.0, 100.5, t) // mais perto do trecho final que do inicial, sem rumo
        assertTrue(r.polyline.project(trap.point).alongM > 1_400, "a projeção global cairia no fim do laço")
        val s = tracker.update(trap)
        assertTrue(s.progressM in 95.0..110.0, "progresso pulou para ${s.progressM}")
        assertFalse(s.offRoute)
    }

    @Test
    fun `chegada quando o restante fica abaixo de 25 m`() {
        val tracker = RouteTracker(route)
        tracker.driveTo(870.0)
        assertFalse(tracker.state.arrived)
        val s = tracker.update(fixAlong(route, 880.0, 1_000_000))
        assertTrue(s.arrived)
        assertEquals(0.0, s.remainingDistanceM)
        assertEquals(0.0, s.distanceToNextManeuverM)
        assertEquals(0.0, s.remainingTimeS)
        assertEquals(Maneuver.Arrive(), s.nextManeuver)
        // Chegada é definitiva: fixes depois disso não reabrem a navegação.
        val after = tracker.update(fixAt(0.0, 300.0, 1_001_000))
        assertTrue(after.arrived)
        assertFalse(after.offRoute)
    }

    @Test
    fun `chegada quando passa do fim entre dois fixes`() {
        val tracker = RouteTracker(route)
        val t = tracker.driveTo(800.0)
        // 60 m além do fim, na continuação da última rua (offset 60 > 35, mas passou do destino).
        val s = tracker.update(fixAt(500.0, -460.0, t + 5_000, bearing = 180f, speed = 12f))
        assertTrue(s.arrived)
    }

    @Test
    fun `tempo restante proporcional as duracoes da rota`() {
        val r = TestRoutes.lRoute(durations = listOf(100.0, 20.0, 0.0))
        val tracker = RouteTracker(r)
        assertEquals(120.0, tracker.state.remainingTimeS, 0.5)
        var t = 0L
        for (a in 0..250 step 10) {
            tracker.update(fixAlong(r, a.toDouble(), t))
            t += 1_000
        }
        assertEquals(70.0, tracker.state.remainingTimeS, 0.5) // 50 s do 1º trecho + 20 s do 2º
        for (a in 260..700 step 10) {
            tracker.update(fixAlong(r, a.toDouble(), t))
            t += 1_000
        }
        assertEquals(10.0, tracker.state.remainingTimeS, 0.5)
    }

    @Test
    fun `distancias inconsistentes do provedor nao afetam a geometria`() {
        val r = TestRoutes.build(
            listOf(0.0 to 0.0, 500.0 to 0.0, 500.0 to -400.0),
            mapOf(1 to Maneuver.Turn(Side.RIGHT)),
            stepLengths = listOf(9_999.0, 1.0, 0.0),
            distanceM = 1_500.0,
        )
        val tracker = RouteTracker(r)
        var t = 0L
        for (a in 0..250 step 10) {
            tracker.update(fixAlong(r, a.toDouble(), t))
            t += 1_000
        }
        val s = tracker.state
        assertEquals(250.0, s.distanceToNextManeuverM, 1.0)
        assertEquals(r.polyline.lengthM - 250.0, s.remainingDistanceM, 1.0)
        assertTrue(s.remainingTimeS > 0)
    }

    @Test
    fun `curvas da fixture aparecem em ordem`() {
        assertEquals(
            listOf(
                Maneuver.Straight,
                Maneuver.Turn(Side.RIGHT),
                Maneuver.Turn(Side.LEFT, TurnSharpness.SLIGHT),
                Maneuver.Turn(Side.RIGHT, TurnSharpness.SHARP),
                Maneuver.Arrive(),
            ),
            maneuverSequence(Fixtures.route(Fixtures.TURNS)),
        )
    }

    @Test
    fun `rotatoria da fixture mostra a saida e depois a curva`() {
        assertEquals(
            listOf(Maneuver.Roundabout(2), Maneuver.Turn(Side.LEFT), Maneuver.Arrive()),
            maneuverSequence(Fixtures.route(Fixtures.ROUNDABOUT), speed = 6.0),
        )
    }

    @Test
    fun `retorno da fixture em pista dupla segue em ordem`() {
        assertEquals(
            listOf(Maneuver.UTurn, Maneuver.Turn(Side.RIGHT), Maneuver.Arrive()),
            maneuverSequence(Fixtures.route(Fixtures.UTURN), speed = 6.0),
        )
    }

    @Test
    fun `segue a rota com ruido de GPS sem falso desvio`() {
        val r = Fixtures.route(Fixtures.TURNS)
        val tracker = RouteTracker(r)
        var maxSuspect = 0
        for (fix in RouteDriveSimulator(r, 11.0, noiseM = 8.0, accuracyM = 10f, seed = 7).fixes()) {
            val s = tracker.update(fix)
            assertFalse(s.offRoute)
            maxSuspect = maxOf(maxSuspect, s.suspectFixCount)
        }
        assertTrue(tracker.state.arrived)
        assertTrue(maxSuspect <= 1)
    }
}
