package dev.hunterlab.core.radar

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** O motor generalizado aceita radares reais sem mudar o contrato do radar fictício. */
class RouteRadarEngineTest {

    @Test
    fun `apelido SimulatedRadar continua ficticio e com limite obrigatorio`() {
        val sim: SimulatedRadar = SimulatedRadar("A", 1_000, RadarKind.FIXED, 60, "A")
        assertTrue(sim.isSimulated)
        assertTrue(sim.directionConfirmed)
        assertThrows<IllegalArgumentException> { RouteRadar("x", 10, RadarKind.FIXED, null, "x") }
        assertThrows<IllegalArgumentException> { RouteRadar("x", 10, RadarKind.FIXED, 0, "x", simulated = false) }
        val ev = RadarEngine(listOf(sim)).update(700.0)
        assertEquals("Alerta ativo: Radar fixo, limite 60 km/h, a 300 m", ev.filterIsInstance<RadarEvent.AlertStarted>().single().description)
    }

    @Test
    fun `radar real sem limite percorre o mesmo ciclo do motor`() {
        val real = RouteRadar("osm:node/7", 1_000, RadarKind.TRAFFIC_LIGHT, null, "Avanço de sinal", simulated = false, directionConfirmed = false)
        assertFalse(real.isSimulated)
        assertNull(real.speedLimitKmh)
        val e = RadarEngine(listOf(real))
        val events = (0..1_200 step 10).flatMap { e.update(it.toDouble()) }
        assertEquals(listOf("Detected", "AlertStarted", "Passed", "ReturnedToNormal"), events.map { it::class.simpleName })
        val alert = events.filterIsInstance<RadarEvent.AlertStarted>().single()
        assertEquals("Alerta ativo: Avanço de sinal, limite desconhecido, a 300 m", alert.description)
    }
}
