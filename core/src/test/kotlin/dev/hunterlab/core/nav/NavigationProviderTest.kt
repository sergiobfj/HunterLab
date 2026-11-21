package dev.hunterlab.core.nav

import dev.hunterlab.protocol.Maneuver
import dev.hunterlab.protocol.Side
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class NavigationProviderTest {

    private var now = 1_000L
    private val sim = SimulatedNavigationProvider { now }
    private val status get() = sim.snapshots.value.status

    @Test
    fun `comeca sem rota e marcado como simulado`() {
        assertEquals(NavigationStatus.IDLE, status)
        assertTrue(sim.snapshots.value.simulated)
        assertEquals(ProviderKind.SIMULATED, sim.info.kind)
    }

    @Test
    fun `escolher manobra sem rota inicia a navegacao`() {
        sim.setManeuver(Maneuver.Turn(Side.LEFT))
        assertEquals(NavigationStatus.NAVIGATING, status)
        assertEquals(Maneuver.Turn(Side.LEFT), sim.snapshots.value.maneuver)
    }

    @Test
    fun `rota iniciada dura 2 s e vira navegacao`() {
        sim.startRoute()
        assertEquals(NavigationStatus.ROUTE_STARTED, status)
        now += 1_999
        assertFalse(sim.tick())
        assertEquals(NavigationStatus.ROUTE_STARTED, status)
        now += 1
        assertTrue(sim.tick())
        assertEquals(NavigationStatus.NAVIGATING, status)
    }

    @Test
    fun `recalculo so com rota ativa e dura 3 s`() {
        assertFalse(sim.recalculate())
        assertEquals(NavigationStatus.IDLE, status)
        sim.setManeuver(Maneuver.Straight)
        assertTrue(sim.recalculate())
        assertEquals(NavigationStatus.RECALCULATING, status)
        sim.setManeuver(Maneuver.UTurn) // muda o rascunho, mas não interrompe o recálculo
        assertEquals(NavigationStatus.RECALCULATING, status)
        now += SimulatedNavigationProvider.RECALCULATING_MS
        sim.tick()
        assertEquals(NavigationStatus.NAVIGATING, status)
        assertEquals(Maneuver.UTurn, sim.snapshots.value.maneuver)
    }

    @Test
    fun `chegada mostra destino e depois volta ao relogio`() {
        sim.setManeuver(Maneuver.Turn(Side.RIGHT))
        sim.arrive()
        val s = sim.snapshots.value
        assertEquals(NavigationStatus.ARRIVED, s.status)
        assertEquals(Maneuver.Arrive(), s.maneuver)
        assertEquals(0, s.distanceToManeuverM)
        assertNull(s.nextManeuver)
        now += SimulatedNavigationProvider.ARRIVED_HOLD_MS
        sim.tick()
        assertEquals(NavigationStatus.IDLE, status)
    }

    @Test
    fun `encerrar rota volta ao relogio imediatamente`() {
        sim.startRoute()
        sim.stopRoute()
        assertEquals(NavigationStatus.IDLE, status)
        now += 10_000
        assertFalse(sim.tick())
    }

    @Test
    fun `valores invalidos sao rejeitados`() {
        assertThrows<IllegalArgumentException> { sim.setDistanceToManeuver(-1) }
        assertThrows<IllegalArgumentException> { sim.setRemaining(-5, 10) }
        assertThrows<IllegalArgumentException> { sim.setRemaining(10, -1) }
        assertThrows<IllegalArgumentException> { sim.startDemo(SimulatedRoute.DEMO, 0) }
    }

    @Test
    fun `rota demonstrativa avanca manobras e termina em chegada`() {
        sim.startDemo(SimulatedRoute.DEMO, speedKmh = 60)
        assertTrue(sim.isDemoRunning)
        assertEquals(NavigationStatus.ROUTE_STARTED, status)
        sim.advanceDemo(500.0) // ignorado durante a tela de início
        assertEquals(600, sim.snapshots.value.distanceToManeuverM)
        now += SimulatedNavigationProvider.ROUTE_STARTED_MS
        sim.tick()

        sim.advanceDemo(500.0)
        var s = sim.snapshots.value
        assertEquals(Maneuver.Turn(Side.RIGHT), s.maneuver)
        assertEquals(100, s.distanceToManeuverM)
        assertEquals(Maneuver.Roundabout(2), s.nextManeuver)
        assertEquals(SimulatedRoute.DEMO.totalLengthM - 500, s.remainingDistanceM)

        sim.advanceDemo(150.0)
        s = sim.snapshots.value
        assertEquals(Maneuver.Roundabout(2), s.maneuver)
        assertEquals(1_450, s.distanceToManeuverM)

        sim.advanceDemo(1_000_000.0)
        assertEquals(NavigationStatus.ARRIVED, status)
        assertFalse(sim.isDemoRunning)
    }

    @Test
    fun `controle manual interrompe a demonstracao`() {
        sim.startDemo(SimulatedRoute.DEMO, 60)
        sim.setManeuver(Maneuver.Straight)
        assertFalse(sim.isDemoRunning)
    }

    @Test
    fun `posicao na rota`() {
        val r = SimulatedRoute.DEMO
        assertEquals(RoutePosition(Maneuver.Turn(Side.RIGHT), 600, Maneuver.Roundabout(2)), r.positionAt(0.0))
        assertEquals(Maneuver.Roundabout(2), r.positionAt(600.0).maneuver)
        assertEquals(RoutePosition(Maneuver.Arrive(Side.RIGHT), 0, null), r.positionAt(r.totalLengthM.toDouble()))
        assertThrows<IllegalArgumentException> { r.positionAt(-1.0) }
        assertThrows<IllegalArgumentException> { SimulatedRoute("vazia", emptyList()) }
    }

    @Test
    fun `provedor futuro existe mas esta indisponivel`() {
        val future = FutureNavigationProvider()
        assertTrue(future.info.availability is ProviderAvailability.Unavailable)
        future.start()
        assertEquals(NavigationStatus.IDLE, future.snapshots.value.status)
        assertFalse(future.snapshots.value.simulated)
    }

    @Test
    fun `os dois provedores implementam a mesma interface`() {
        val providers: List<NavigationProvider> = listOf(sim, FutureNavigationProvider())
        assertEquals(setOf("simulated", "future"), providers.map { it.info.id }.toSet())
    }
}
