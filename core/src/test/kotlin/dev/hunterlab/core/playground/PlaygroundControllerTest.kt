package dev.hunterlab.core.playground

import dev.hunterlab.core.ble.ScannedDevice
import dev.hunterlab.core.link.*
import dev.hunterlab.protocol.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PlaygroundControllerTest {
    private val options = ConnectOptions(purpose = SessionPurpose.PLAYGROUND)
    private class Setup {
        val rig = LinkRig()
        var nav = false
        val records = mutableListOf<PlaygroundExperiment>()
        val control = PlaygroundController(rig.link, rig.scheduler, { nav }, { 1_800_000_000_000 }, records::add)
        fun auth() { assertEquals(CommandResult.Accepted, control.arm(LinkRig.ACK)); rig.connectToReady(options = ConnectOptions(purpose = SessionPurpose.PLAYGROUND)); rig.pairAndAuthenticate() }
    }
    private fun rejected(result: CommandResult) { assertTrue(result is CommandResult.Rejected, result.toString()) }
    private fun candidate(address: String = LinkRig.ADDR, name: String = "RE_DISP", at: Long = 100_000) = ScannedDevice(address, name, -50, lastSeenMs = at)

    @Test fun `construcao catalogo e selecao nunca abrem radio e fisico nasce desativado`() {
        val s = Setup()
        PlaygroundSelection().select("compass-E").preview
        assertFalse(s.control.enabled); assertTrue(s.rig.client.connects.isEmpty()); assertTrue(s.rig.client.writes.isEmpty())
        rejected(s.control.show("compass-N", DayPeriod.NIGHT, 10_000))
        rejected(s.control.connect(listOf(candidate()), 100_000))
        assertTrue(s.rig.client.connects.isEmpty())
    }
    @Test fun `consentimento incompleto navegacao e sessao alheia impedem armar`() {
        val s = Setup()
        rejected(s.control.arm(LinkRig.ACK.copy(bikeStopped = false)))
        s.nav = true; rejected(s.control.arm(LinkRig.ACK)); s.nav = false
        s.rig.connectToReady(); rejected(s.control.arm(LinkRig.ACK))
        assertTrue(s.rig.client.writes.isEmpty())
    }
    @Test fun `identidade ambigua desconhecida antiga ou nao conectavel nunca conecta`() {
        val s = Setup(); s.control.arm(LinkRig.ACK)
        for (candidates in listOf(emptyList(), listOf(candidate(), candidate("00:60:37:00:00:02")), listOf(candidate(name = "RE_OTHER")), listOf(candidate(at = 90_000)), listOf(candidate().copy(connectable = false)))) {
            rejected(s.control.connect(candidates, 100_000))
        }
        rejected(s.control.connect(listOf(candidate(), candidate("00:60:37:00:00:03").copy(name = null, serviceUuids = listOf(TripperTransport.SERVICE_UUID))), 100_000))
        rejected(s.control.connect(listOf(candidate(address = "")), 100_000))
        assertTrue(s.rig.client.connects.isEmpty()); assertTrue(s.rig.client.writes.isEmpty())
    }
    @Test fun `conectar nao dispara handshake e autenticacao exige confirmacao visual`() {
        val s = Setup(); s.control.arm(LinkRig.ACK)
        assertEquals(CommandResult.Accepted, s.control.connect(listOf(candidate()), 100_000))
        val r = s.rig
        r.link.onServerReady(r.gen, true); r.link.onAdvertisementSeen(r.gen, -50, true)
        r.link.onConnected(r.gen); r.link.onServicesDiscovered(r.gen, 0, r.documentedSnapshot())
        assertTrue(r.client.writes.isEmpty())
        rejected(s.control.show("compass-N", DayPeriod.NIGHT, 5_000))
        assertEquals(CommandResult.Accepted, s.control.requestPin()); r.runFor(300)
        assertEquals(CommandResult.Accepted, s.control.submitPin("4821")); r.runFor(100)
        rejected(s.control.show("compass-N", DayPeriod.NIGHT, 5_000))
        assertEquals(CommandResult.Accepted, s.control.confirmPin())
        assertEquals(CommandResult.Accepted, s.control.show("compass-N", DayPeriod.NIGHT, 5_000))
    }
    @Test fun `bytes nomes arbitrarios simbolos de navegacao e duracoes invalidas sao recusados`() {
        val s = Setup(); s.auth()
        for (id in listOf("FF FF FF", "gif", "sequence", "icon-20", "icon-69", "compass-INVALID")) rejected(s.control.show(id, DayPeriod.DAY, 5_000))
        for (duration in listOf(-1L, 0L, 4_999L, 15_001L, Long.MAX_VALUE)) rejected(s.control.show("compass-N", DayPeriod.DAY, duration))
        assertFalse(s.rig.writtenKinds().contains("COMPASS"))
        assertFalse(s.rig.writtenKinds().contains("NAVIGATION"))
        rejected(s.rig.link.startScreenStream(TripperScreen.Navigation(NavigationFrame(TripperIcons.STRAIGHT, 250))))
        assertFalse(s.rig.writtenKinds().contains("NAVIGATION"))
        val fast = LinkRig(LinkTiming(screenResendMs = 1_000))
        fast.connectToReady(options = options); fast.pairAndAuthenticate()
        rejected(fast.link.startCompassTest(CompassDirection.N, DayPeriod.NIGHT, 5_000))
        assertFalse(fast.writtenKinds().contains("COMPASS"))
    }
    @Test fun `teste curto reutiliza compass cadencia dois segundos e termina sem reenviar`() {
        val s = Setup(); s.auth()
        assertEquals(CommandResult.Accepted, s.control.show("compass-E", DayPeriod.DAY, 5_000))
        s.rig.runFor(7_000)
        val frames = s.rig.client.writes.filter { LinkRig.kindOf(it.bytes) == "COMPASS" }
        assertEquals(3, frames.size)
        frames.zipWithNext().forEach { (a, b) -> assertTrue(b.atMs - a.atMs >= 2_000) }
        assertFalse(s.control.running); assertFalse(s.control.enabled)
        assertEquals(1, s.records.size); assertEquals(ExperimentEnd.DURATION, s.records.single().end)
        val count = s.rig.client.writes.size; s.rig.runFor(20_000)
        assertEquals(count, s.rig.client.writes.size)
        assertTrue(s.records.single().delivered!! > 0)
        assertEquals(VisualObservation.NOT_RECORDED, s.records.single().observation)
    }
    @Test fun `interromper limpa fila timers e callbacks tardios nao reativam teste`() {
        val s = Setup(); s.auth(); s.control.show("compass-N", DayPeriod.NIGHT, 15_000)
        s.rig.runFor(100); val gen = s.rig.gen
        s.control.stop(); val count = s.rig.client.writes.size
        s.rig.link.onWriteCompleted(gen, 0); s.rig.link.onConnected(gen); s.rig.runFor(30_000)
        s.control.onLinkChanged(s.rig.snap)
        assertEquals(count, s.rig.client.writes.size); assertEquals(1, s.records.size)
        assertFalse(s.control.running); assertFalse(s.rig.snap.transmissionAuthorized)
        assertEquals(0, s.rig.snap.diagnostics.queueSize)
    }
    @Test fun `navegacao revoga imediatamente a sessao e bloqueia envios diretos e novos connects`() {
        val s = Setup(); s.auth(); s.control.show("compass-N", DayPeriod.NIGHT, 15_000); s.rig.runFor(100)
        s.nav = true; s.rig.link.setNavigationPriority(true)
        s.control.onLinkChanged(s.rig.snap)
        val count = s.rig.client.writes.size
        rejected(s.rig.link.startCompassTest(CompassDirection.E, DayPeriod.DAY, 5_000))
        s.rig.runFor(30_000)
        rejected(s.rig.link.connect(LinkRig.ADDR, "RE_DISP", options))
        assertEquals(count, s.rig.client.writes.size)
        assertEquals(ExperimentEnd.NAVIGATION_PRIORITY, s.records.single().end)
    }
    @Test fun `desconexao inesperada preserva registro sem segredo e ignora geracao antiga`() {
        val s = Setup(); s.auth(); s.control.show("compass-N", DayPeriod.NIGHT, 10_000); s.rig.runFor(2_100)
        val gen = s.rig.gen; s.rig.link.onDisconnected(gen, 19); s.control.onLinkChanged(s.rig.snap)
        val count = s.rig.client.writes.size; s.rig.link.onWriteCompleted(gen, 0); s.rig.runFor(30_000)
        assertEquals(count, s.rig.client.writes.size); assertEquals(1, s.records.size)
        assertEquals(ExperimentEnd.DISCONNECTED, s.records.single().end)
        val report = ExperimentLog.report(s.records)
        assertFalse(report.contains("4821")); assertFalse(report.contains(LinkRig.ADDR))
        assertTrue(s.rig.snap.diagnostics.fromEndedSession)
    }
    @Test fun `expiracao e troca de dono requerem consentimento novo`() {
        val s = Setup(); s.control.arm(LinkRig.ACK); s.rig.scheduler.advanceBy(PlaygroundController.ARM_TTL_MS)
        assertFalse(s.control.enabled); rejected(s.control.connect(listOf(candidate()), 100_000))
        s.control.arm(LinkRig.ACK); s.rig.connectToReady(options = ConnectOptions(purpose = SessionPurpose.NAVIGATION)); s.rig.pairAndAuthenticate()
        rejected(s.control.show("compass-N", DayPeriod.DAY, 10_000))
        val state = s.rig.state; s.control.stop()
        assertEquals(state, s.rig.state); assertTrue(s.rig.snap.transmissionAuthorized)
    }
    @Test fun `falha de armazenamento nunca impede a interrupcao do radio`() {
        val rig = LinkRig()
        val control = PlaygroundController(rig.link, rig.scheduler, { false }, { 1_800_000_000_000 }) { error("armazenamento indisponível") }
        control.arm(LinkRig.ACK); rig.connectToReady(options = options); rig.pairAndAuthenticate()
        control.show("compass-N", DayPeriod.NIGHT, 15_000); rig.runFor(100)
        assertThrows(IllegalStateException::class.java) { control.stop() }
        assertFalse(control.running); assertFalse(rig.snap.transmissionAuthorized)
        val writes = rig.client.writes.size; rig.runFor(30_000)
        assertEquals(writes, rig.client.writes.size)
    }

}
