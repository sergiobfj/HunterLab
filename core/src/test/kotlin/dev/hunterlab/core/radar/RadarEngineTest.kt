package dev.hunterlab.core.radar

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RadarEngineTest {

    private val r1 = SimulatedRadar("A", 1_000, RadarKind.FIXED, 60, "A")

    private fun walk(engine: RadarEngine, from: Int, to: Int, step: Int = 10): List<Pair<Int, RadarEvent>> =
        (from..to step step).flatMap { pos -> engine.update(pos.toDouble()).map { pos to it } }

    @Test
    fun `ciclo completo detectado, alerta, ultrapassado e retorno`() {
        val e = RadarEngine(listOf(r1))
        val events = walk(e, 0, 1_200)
        val kinds = events.map { it.second::class.simpleName }
        assertEquals(listOf("Detected", "AlertStarted", "Passed", "ReturnedToNormal"), kinds)
        assertEquals(300, events[0].first)   // 700 m antes
        assertEquals(700, events[1].first)   // 300 m antes
        assertEquals(1_010, events[2].first) // primeiro passo além do radar
        assertEquals(1_070, events[3].first) // após segurar 60 m
        assertEquals(RadarPhase.Clear, e.phase)
    }

    @Test
    fun `distancia aproximada diminui durante o alerta`() {
        val e = RadarEngine(listOf(r1))
        e.update(750.0)
        assertEquals(RadarPhase.Alert(r1, 250), e.phase)
        e.update(900.0)
        assertEquals(RadarPhase.Alert(r1, 100), e.phase)
        e.update(1_000.0)
        assertEquals(RadarPhase.Alert(r1, 0), e.phase)
    }

    @Test
    fun `histerese evita piscar na borda dos 300 m`() {
        val e = RadarEngine(listOf(r1))
        e.update(710.0) // 290 m: alerta
        assertTrue(e.phase is RadarPhase.Alert)
        e.update(685.0) // 315 m (recuou): continua em alerta pela histerese
        assertTrue(e.phase is RadarPhase.Alert)
        e.update(660.0) // 340 m: volta a "detectado"
        assertTrue(e.phase is RadarPhase.Detected)
    }

    @Test
    fun `radares consecutivos pulam o retorno ao normal`() {
        val a = SimulatedRadar("A", 1_000, RadarKind.FIXED, 60, "A")
        val b = SimulatedRadar("B", 1_200, RadarKind.FIXED, 60, "B")
        val e = RadarEngine(listOf(b, a)) // ordem de entrada não importa
        val events = walk(e, 0, 1_400)
        val seq = events.map { "${it.second::class.simpleName}:" + radarId(it.second) }
        assertEquals(
            listOf("Detected:A", "AlertStarted:A", "Passed:A", "Detected:B", "AlertStarted:B", "Passed:B", "ReturnedToNormal:B"),
            seq,
        )
        // A é ultrapassado em 1010 m; B entra em alerta já no passo seguinte (1020 m),
        // sem esperar os 60 m de "ultrapassado" nem emitir "retorno ao normal" entre os dois.
        assertEquals(1_010, events[2].first)
        assertEquals(1_020, events[3].first)
    }

    @Test
    fun `fora do raio de busca nada acontece`() {
        val e = RadarEngine(listOf(r1))
        assertTrue(walk(e, 0, 290).isEmpty())
        assertEquals(RadarPhase.Clear, e.phase)
    }

    @Test
    fun `sem radares sempre normal`() {
        val e = RadarEngine(emptyList())
        assertTrue(walk(e, 0, 5_000, 100).isEmpty())
    }

    @Test
    fun `configuracao e posicao invalidas sao rejeitadas`() {
        assertThrows<IllegalArgumentException> { RadarConfig(searchRadiusM = 200, alertRadiusM = 300) }
        assertThrows<IllegalArgumentException> { RadarConfig(alertRadiusM = 0) }
        assertThrows<IllegalArgumentException> { RadarConfig(passedHoldM = -1) }
        assertThrows<IllegalArgumentException> { RadarEngine(listOf(r1)).update(-1.0) }
        assertThrows<IllegalArgumentException> { RadarEngine(listOf(r1)).update(Double.NaN) }
        assertThrows<IllegalArgumentException> { SimulatedRadar("x", -1, RadarKind.FIXED, 60, "x") }
    }

    @Test
    fun `simulador avanca pela velocidade e escala de tempo`() {
        val sim = RadarSimulator(listOf(r1), routeLengthM = 2_000)
        sim.speedKmh = 72 // 20 m/s
        sim.step(1_000)
        assertEquals(20.0, sim.positionM, 1e-9)
        sim.step(1_000, timeScale = 5.0)
        assertEquals(120.0, sim.positionM, 1e-9)
        sim.step(1_000_000)
        assertEquals(2_000.0, sim.positionM, 1e-9)
        assertTrue(sim.finished)
        assertTrue(sim.step(1_000).isEmpty())
    }

    @Test
    fun `simulador percorre a rota ficticia padrao gerando todos os eventos`() {
        val sim = RadarSimulator()
        val events = mutableListOf<RadarEvent>()
        while (!sim.finished) events += sim.step(250, timeScale = 4.0)
        val alerts = events.filterIsInstance<RadarEvent.AlertStarted>().map { it.radar.id }
        assertEquals(listOf("SIM-01", "SIM-02", "SIM-03", "SIM-04"), alerts)
        assertTrue(FictitiousRadars.DEFAULT.all { it.isSimulated })
        assertTrue(events.last() is RadarEvent.ReturnedToNormal || sim.phase is RadarPhase.Passed)
    }

    @Test
    fun `seek e reset reiniciam o estado`() {
        val sim = RadarSimulator(listOf(r1), routeLengthM = 2_000)
        val ev = sim.seek(800.0)
        assertTrue(ev.any { it is RadarEvent.AlertStarted })
        sim.reset()
        assertEquals(0.0, sim.positionM)
        assertEquals(RadarPhase.Clear, sim.phase)
        assertThrows<IllegalArgumentException> { sim.seek(5_000.0) }
        assertThrows<IllegalArgumentException> { sim.speedKmh = 0 }
        assertThrows<IllegalArgumentException> { sim.step(-1) }
        assertThrows<IllegalArgumentException> { sim.step(1, timeScale = 0.0) }
    }

    private fun radarId(e: RadarEvent): String = when (e) {
        is RadarEvent.Detected -> e.radar.id
        is RadarEvent.AlertStarted -> e.radar.id
        is RadarEvent.Passed -> e.radar.id
        is RadarEvent.ReturnedToNormal -> e.radar?.id ?: "-"
    }
}
