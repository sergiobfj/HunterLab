package dev.hunterlab.core.display

import dev.hunterlab.core.link.CommandResult
import dev.hunterlab.core.link.ScreenStreamPort
import dev.hunterlab.core.link.StreamAvailability
import dev.hunterlab.core.nav.NavigationSnapshot
import dev.hunterlab.core.nav.NavigationStatus
import dev.hunterlab.core.radar.RadarKind
import dev.hunterlab.core.radar.RadarPhase
import dev.hunterlab.core.radar.SimulatedRadar
import dev.hunterlab.protocol.Maneuver
import dev.hunterlab.protocol.Side
import dev.hunterlab.protocol.TripperIcons
import dev.hunterlab.protocol.TripperScreen
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalTime

class TripperDisplayControllerTest {

    private class FakePort : ScreenStreamPort {
        override var streamAvailability = StreamAvailability.READY
        val calls = mutableListOf<String>()
        val screens = mutableListOf<TripperScreen>()
        override fun startScreenStream(screen: TripperScreen): CommandResult {
            calls += "start"; screens += screen; streamAvailability = StreamAvailability.STREAMING
            return CommandResult.Accepted
        }
        override fun updateScreen(screen: TripperScreen): CommandResult {
            calls += "update"; screens += screen
            return CommandResult.Accepted
        }
        override fun stopScreenStream(reason: String): CommandResult {
            calls += "stop"; streamAvailability = StreamAvailability.READY
            return CommandResult.Accepted
        }
    }

    private val noon = LocalTime.NOON
    private val settings = DisplaySettings()
    private fun nav(status: NavigationStatus, m: Int = 300) = NavigationSnapshot(
        status = status,
        maneuver = Maneuver.Turn(Side.RIGHT),
        distanceToManeuverM = m,
        remainingDistanceM = 4_000,
        remainingTimeMin = 8,
        sourceId = "teste",
        simulated = false,
    )

    @Test
    fun `sem rota nunca inicia transmissao`() {
        val port = FakePort()
        val c = TripperDisplayController(port)
        val u = c.update(nav(NavigationStatus.IDLE), RadarPhase.Clear, settings, noon, 0)
        assertEquals(TripperDisplayStatus.READY, u.status)
        assertTrue(port.calls.isEmpty())
        assertEquals(TripperScreen.NativeClock, u.composed.screen)
    }

    @Test
    fun `navegando inicia e depois so atualiza`() {
        val port = FakePort()
        val c = TripperDisplayController(port)
        assertEquals(TripperDisplayStatus.SHOWING, c.update(nav(NavigationStatus.NAVIGATING, 300), RadarPhase.Clear, settings, noon, 0).status)
        c.update(nav(NavigationStatus.NAVIGATING, 250), RadarPhase.Clear, settings, noon, 1_000)
        c.update(nav(NavigationStatus.RECALCULATING), RadarPhase.Clear, settings, noon, 2_000)
        assertEquals(listOf("start", "update", "update"), port.calls)
        assertTrue(port.screens.last() is TripperScreen.Recalculating)
    }

    @Test
    fun `sem tripper a navegacao segue so com previa`() {
        val port = FakePort().apply { streamAvailability = StreamAvailability.UNAVAILABLE }
        val c = TripperDisplayController(port)
        val u = c.update(nav(NavigationStatus.NAVIGATING), RadarPhase.Clear, settings, noon, 0)
        assertEquals(TripperDisplayStatus.NOT_CONNECTED, u.status)
        assertTrue(u.composed.screen is TripperScreen.Navigation)
        assertTrue(port.calls.isEmpty())
    }

    @Test
    fun `chegada fica na tela pelo tempo definido e encerra`() {
        val port = FakePort()
        val c = TripperDisplayController(port, arrivalHoldMs = 10_000)
        c.update(nav(NavigationStatus.NAVIGATING, 20), RadarPhase.Clear, settings, noon, 0)
        c.update(nav(NavigationStatus.ARRIVED, 0), RadarPhase.Clear, settings, noon, 1_000)
        val arrival = port.screens.last() as TripperScreen.Navigation
        assertEquals(TripperIcons.DESTINATION, arrival.frame.icon)
        c.update(nav(NavigationStatus.ARRIVED, 0), RadarPhase.Clear, settings, noon, 9_000)
        assertEquals("update", port.calls.last())
        val end = c.update(nav(NavigationStatus.ARRIVED, 0), RadarPhase.Clear, settings, noon, 11_500)
        assertEquals(TripperDisplayStatus.ENDED, end.status)
        assertEquals("stop", port.calls.last())
        // Continua chegando atualizações: não reinicia a transmissão.
        c.update(nav(NavigationStatus.ARRIVED, 0), RadarPhase.Clear, settings, noon, 12_000)
        assertEquals("stop", port.calls.last())
    }

    @Test
    fun `radar nunca esconde a manobra`() {
        val port = FakePort()
        val c = TripperDisplayController(port)
        val radar = RadarPhase.Alert(SimulatedRadar("R1", 500, RadarKind.FIXED, 60, "R1"), 180)
        c.update(nav(NavigationStatus.NAVIGATING, 120), radar, settings, noon, 0)
        val frame = (port.screens.last() as TripperScreen.Navigation).frame
        val plain = (c.update(nav(NavigationStatus.NAVIGATING, 120), RadarPhase.Clear, settings, noon, 0).composed.screen as TripperScreen.Navigation).frame
        assertEquals(plain.icon, frame.icon)
        assertEquals(plain.distanceMeters, frame.distanceMeters)
    }

    @Test
    fun `parar pelo usuario encerra a transmissao`() {
        val port = FakePort()
        val c = TripperDisplayController(port)
        c.update(nav(NavigationStatus.NAVIGATING), RadarPhase.Clear, settings, noon, 0)
        c.stop()
        assertEquals("stop", port.calls.last())
        c.update(nav(NavigationStatus.NAVIGATING), RadarPhase.Clear, settings, noon, 1_000)
        assertEquals("stop", port.calls.last())
    }
}
