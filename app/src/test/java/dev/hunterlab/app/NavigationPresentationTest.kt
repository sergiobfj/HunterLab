package dev.hunterlab.app

import dev.hunterlab.app.ui.contract.*
import dev.hunterlab.app.ui.navigation.showCurrentManeuver
import dev.hunterlab.app.ui.navigation.showJourneyMetrics
import org.junit.Assert.*
import org.junit.Test

class NavigationPresentationTest {
    private val state = NavUi(
        phase = NavPhaseUi.NAVIGATING, gps = GpsUi.OK,
        current = ManeuverUi(0x14, "Vire", "250 m"), remainingTimeText = "10 min",
    )

    @Test fun `recalculo erro e encerramento ocultam instrucoes e estimativas antigas`() {
        NavPhaseUi.entries.filter { it != NavPhaseUi.NAVIGATING }.forEach { phase ->
            assertFalse(phase.name, state.copy(phase = phase).showCurrentManeuver)
            assertFalse(phase.name, state.copy(phase = phase).showJourneyMetrics)
        }
        assertTrue(state.showCurrentManeuver)
        assertTrue(state.showJourneyMetrics)
    }

    @Test fun `GPS sem posicao confiavel oculta instrucoes antigas`() {
        GpsUi.entries.filter { it != GpsUi.OK }.forEach { gps ->
            assertFalse(gps.name, state.copy(gps = gps).showCurrentManeuver)
            assertFalse(gps.name, state.copy(gps = gps).showJourneyMetrics)
        }
        assertFalse(state.copy(current = null).showCurrentManeuver)
    }
}
