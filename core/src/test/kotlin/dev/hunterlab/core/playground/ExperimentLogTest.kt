package dev.hunterlab.core.playground

import dev.hunterlab.protocol.DayPeriod
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ExperimentLogTest {
    private val record = PlaygroundExperiment(1_800_000_000_000, listOf("compass-N"), ExperimentKind.PHYSICAL, DayPeriod.NIGHT, 10_000, 4, 0, ExperimentEnd.DURATION)
    @Test fun `historia e observacoes sobrevivem ao roundtrip sem elevar evidencia`() {
        val changed = record.copy(observation = VisualObservation.MATCHED, clockRecovery = ClockRecovery.WATCHDOG)
        assertEquals(listOf(changed), ExperimentLog.decode(ExperimentLog.encode(listOf(changed))))
        assertEquals(VisualEvidence.REFERENCE, PlaygroundCatalog.find("compass-N")!!.evidence(DayPeriod.DAY))
    }
    @Test fun `historico e limitado e JSON invalido ou com IDs arbitrarios e descartado`() {
        assertEquals(50, ExperimentLog.decode(ExperimentLog.encode(List(60) { record.copy(atEpochMs = it.toLong()) })).size)
        assertEquals(emptyList<PlaygroundExperiment>(), ExperimentLog.decode("{invalido"))
        val invalid = ExperimentLog.encode(listOf(record)).replace("compass-N", "PIN=1234")
        assertTrue(ExperimentLog.decode(invalid).isEmpty())
        assertTrue(ExperimentLog.decode(" ".repeat(100_001)).isEmpty())
    }
    @Test fun `campos estranhos nao sobrevivem ao armazenamento ou exportacao`() {
        val dirty = ExperimentLog.encode(listOf(record)).replace("{", "{\"pin\":\"1234\",\"mac\":\"AA:BB:CC:DD:EE:FF\",\"token\":\"segredo\",")
        val decoded = ExperimentLog.decode(dirty)
        val json = ExperimentLog.encode(decoded); val report = ExperimentLog.report(decoded)
        for (value in listOf("1234", "AA:BB:CC:DD:EE:FF", "segredo", "token")) {
            assertFalse(json.contains(value)); assertFalse(report.contains(value))
        }
        assertTrue(report.contains("não comprova"))
    }
    @Test fun `simulacao nao inventa contadores fisicos e registros invalidos sao recusados`() {
        val sim = record.copy(kind = ExperimentKind.SIMULATED, delivered = null, responses = null)
        assertTrue(ExperimentLog.report(listOf(sim)).contains("não se aplica"))
        assertThrows(IllegalArgumentException::class.java) { sim.copy(delivered = 1) }
        assertThrows(IllegalArgumentException::class.java) { record.copy(symbols = listOf("icon-20")) }
        assertThrows(IllegalArgumentException::class.java) { record.copy(delivered = -1) }
        assertThrows(IllegalArgumentException::class.java) { record.copy(durationMs = 50_000) }
    }
}
