package dev.hunterlab.app

import dev.hunterlab.app.ui.contract.ManeuverUi
import dev.hunterlab.app.ui.contract.NavPhaseUi
import dev.hunterlab.app.ui.contract.NavUi
import dev.hunterlab.app.ui.navigation.showCurrentManeuver
import dev.hunterlab.app.ui.navigation.showJourneyMetrics
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiPresentationTest {
    private val instruction = ManeuverUi(0x14, "Vire à esquerda", "250 m")

    @Test
    fun `manobra aparece durante navegacao com instrucao disponivel`() {
        assertTrue(NavUi(phase = NavPhaseUi.NAVIGATING, current = instruction).showCurrentManeuver)
    }

    @Test
    fun `instrucao antiga fica oculta fora da navegacao valida`() {
        NavPhaseUi.entries.filter { it != NavPhaseUi.NAVIGATING }.forEach { phase ->
            val stale = NavUi(phase = phase, current = instruction, remainingDistanceText = "6 km", remainingTimeText = "12 min", arrivalText = "19:42")
            assertFalse("Manobra antiga exibida em $phase", stale.showCurrentManeuver)
            assertFalse("ETA antigo exibido em $phase", stale.showJourneyMetrics)
        }
    }

    @Test
    fun `estado sem instrucao nao inventa uma manobra`() {
        assertFalse(NavUi(phase = NavPhaseUi.NAVIGATING, current = null).showCurrentManeuver)
    }
}
