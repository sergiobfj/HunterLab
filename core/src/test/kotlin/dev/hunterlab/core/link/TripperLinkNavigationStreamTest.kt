package dev.hunterlab.core.link

import dev.hunterlab.protocol.BottomLine
import dev.hunterlab.protocol.CompassDirection
import dev.hunterlab.protocol.DayPeriod
import dev.hunterlab.protocol.Hex
import dev.hunterlab.protocol.NavigationFrame
import dev.hunterlab.protocol.PacketParseResult
import dev.hunterlab.protocol.TripperIcons
import dev.hunterlab.protocol.TripperPacket
import dev.hunterlab.protocol.TripperScreen
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** 0.2.2 (contadores após a desconexão) e 0.3.0 (transmissão contínua de telas de navegação). */
class TripperLinkNavigationStreamTest {

    private val nav = ConnectOptions(purpose = SessionPurpose.NAVIGATION)
    private fun frame(icon: Int = 0x14, meters: Int = 250) =
        TripperScreen.Navigation(NavigationFrame(icon, meters, bottomLine = BottomLine.TotalDistance(4_200)))

    private fun navRig(): LinkRig = LinkRig().apply {
        connectToReady(options = nav)
        pairAndAuthenticate()
    }

    // ------------------------------------------------------------------ 0.2.2: contadores do relatório

    @Test
    fun `contadores de escrita e respostas sobrevivem a desconexao`() {
        val rig = LinkRig()
        rig.pairAndAuthenticate()
        rig.link.startCompassTest(CompassDirection.E, DayPeriod.DAY, 6_000)
        rig.link.onInbound(rig.gen, LinkRig.ADDR, dev.hunterlab.protocol.TripperTransport.CHARACTERISTIC_UUID, byteArrayOf(1, 2, 3))
        rig.runFor(8_000)
        val delivered = rig.snap.diagnostics.writesDelivered
        assertTrue(delivered >= 4, "handshake + PIN + quadros: $delivered")
        rig.link.onDisconnected(rig.gen, 19)

        val after = rig.snap
        assertEquals(LinkState.DISCONNECTED, after.state)
        assertEquals(delivered, after.diagnostics.writesDelivered)
        assertEquals(1, after.diagnostics.inboundAccepted)
        assertTrue(after.diagnostics.fromEndedSession)
        assertFalse(after.diagnostics.keepaliveActive)
        assertEquals("RE_DISP", after.deviceName)
        assertEquals(AuthPath.PAIRING, after.authPath)
        assertEquals(1, after.pinAttemptsUsed)
        assertFalse(after.transmissionAuthorized)

        val report = rig.link.report("0.2.2", "agora")
        assertTrue("Escritas entregues ao controlador: $delivered" in report, report)
        assertTrue("respostas recebidas: 1" in report, report)
        assertTrue("sessão encerrada" in report, report)
    }

    @Test
    fun `nova conexao zera os contadores da sessao anterior`() {
        val rig = LinkRig()
        rig.pairAndAuthenticate()
        rig.runFor(3_000)
        rig.link.onDisconnected(rig.gen, 19)
        assertTrue(rig.snap.diagnostics.writesDelivered > 0)
        rig.connectToReady(authorize = false)
        assertEquals(0, rig.snap.diagnostics.writesDelivered)
        assertFalse(rig.snap.diagnostics.fromEndedSession)
        assertEquals(0, rig.snap.pinAttemptsUsed)
    }

    @Test
    fun `envios periodicos repetidos nao inundam o historico`() {
        val rig = LinkRig()
        rig.pairAndAuthenticate()
        rig.runFor(60_000) // ~30 PING_FW de keepalive
        val pingLines = rig.snap.log.count { it.level == LogLevel.TX && it.text.startsWith("PING_FW") }
        assertEquals(1, pingLines)
        assertTrue(rig.client.writes.count { LinkRig.kindOf(it.bytes) == "PING" } > 20)
        rig.link.onDisconnected(rig.gen, 19)
        assertTrue(rig.snap.log.any { "PING_FW ×" in it.text })
        assertTrue(rig.snap.log.any { "HANDSHAKE_SHOW_PIN" in it.text && it.level == LogLevel.TX })
    }

    // ------------------------------------------------------------------ 0.3.0: telas de navegação

    @Test
    fun `sessao do laboratorio nao transmite navegacao`() {
        val rig = LinkRig()
        rig.pairAndAuthenticate()
        assertEquals(StreamAvailability.UNAVAILABLE, rig.link.streamAvailability)
        assertTrue(rig.link.startScreenStream(frame()) is CommandResult.Rejected)
        assertFalse(rig.writtenKinds().contains("NAVIGATION"))
    }

    @Test
    fun `navegacao exige autenticacao confirmada`() {
        val rig = LinkRig()
        rig.connectToReady(options = nav)
        assertEquals(StreamAvailability.UNAVAILABLE, rig.link.streamAvailability)
        assertTrue(rig.link.startScreenStream(frame()) is CommandResult.Rejected)
        assertTrue(rig.client.writes.isEmpty())
    }

    @Test
    fun `tela de navegacao e reenviada a cada 2 s sem keepalive`() {
        val rig = navRig()
        assertEquals(StreamAvailability.READY, rig.link.streamAvailability)
        val before = rig.client.writes.size
        assertEquals(CommandResult.Accepted, rig.link.startScreenStream(frame()))
        assertEquals(StreamAvailability.STREAMING, rig.link.streamAvailability)
        assertEquals(LinkState.DISPLAY_ACTIVE, rig.link.currentState)
        rig.runFor(10_100)
        val kinds = rig.writtenKinds().drop(before)
        assertEquals(6, kinds.count { it == "NAVIGATION" }, kinds.toString()) // t = 0, 2, 4, 6, 8, 10 s
        assertFalse(kinds.contains("PING"), "o reenvio de tela já mantém o link")
        val stream = rig.snap.screenStream!!
        assertEquals(stream.framesQueued, stream.framesDelivered)
    }

    @Test
    fun `troca de tela envia na hora e tela igual nao gera envio extra`() {
        val rig = navRig()
        rig.link.startScreenStream(frame(meters = 300))
        rig.runFor(500)
        val n0 = rig.writtenKinds().count { it == "NAVIGATION" }
        assertEquals(CommandResult.Accepted, rig.link.updateScreen(frame(meters = 300)))
        rig.runFor(100)
        assertEquals(n0, rig.writtenKinds().count { it == "NAVIGATION" })
        rig.link.updateScreen(frame(meters = 200))
        rig.runFor(100)
        assertEquals(n0 + 1, rig.writtenKinds().count { it == "NAVIGATION" })
        val last = rig.client.writes.last().bytes
        assertEquals(0x14, last[2].toInt() and 0xFF)
        assertEquals(200, last[4].toInt() and 0xFF) // distância [3-4], unidade [5]
        assertEquals(1, last[5].toInt() and 0xFF)
        rig.link.updateScreen(TripperScreen.Recalculating(DayPeriod.DAY))
        rig.runFor(100)
        assertEquals(TripperIcons.RECALCULATING, rig.client.writes.last().bytes[2].toInt() and 0xFF)
    }

    @Test
    fun `parar a navegacao deixa de enviar telas`() {
        val rig = navRig()
        rig.link.startScreenStream(frame())
        rig.runFor(3_000)
        assertEquals(CommandResult.Accepted, rig.link.stopScreenStream("teste"))
        assertEquals(LinkState.AUTHENTICATED, rig.link.currentState)
        val n = rig.writtenKinds().count { it == "NAVIGATION" }
        rig.runFor(10_000)
        assertEquals(n, rig.writtenKinds().count { it == "NAVIGATION" })
    }

    @Test
    fun `telas nao documentadas sao recusadas`() {
        val rig = navRig()
        assertTrue(rig.link.startScreenStream(TripperScreen.NativeClock) is CommandResult.Rejected)
        assertTrue(rig.link.startScreenStream(TripperScreen.RouteStarted(DayPeriod.DAY)) is CommandResult.Rejected)
        assertTrue(rig.link.startScreenStream(TripperScreen.Compass(CompassDirection.N, DayPeriod.DAY)) is CommandResult.Rejected)
        rig.link.startScreenStream(frame())
        assertTrue(rig.link.updateScreen(TripperScreen.NativeClock) is CommandResult.Rejected)
    }

    @Test
    fun `interromper encerra a navegacao na hora`() {
        val rig = navRig()
        rig.link.startScreenStream(frame())
        rig.runFor(1_000)
        rig.link.interrupt()
        val n = rig.client.writes.size
        rig.runFor(6_000)
        assertEquals(n, rig.client.writes.size)
        assertEquals(StreamAvailability.UNAVAILABLE, rig.link.streamAvailability)
    }

    @Test
    fun `sair da tela interrompe o laboratorio mas nao a navegacao`() {
        val lab = LinkRig()
        lab.pairAndAuthenticate()
        lab.link.interruptIfLab("app saiu da tela")
        assertEquals(LinkState.STOPPING, lab.link.currentState)

        val rig = navRig()
        rig.link.startScreenStream(frame())
        rig.link.interruptIfLab("app saiu da tela")
        assertEquals(LinkState.DISPLAY_ACTIVE, rig.link.currentState)
    }

    @Test
    fun `navegacao nao e cortada pelo limite de 5 min do laboratorio`() {
        val rig = navRig()
        rig.link.startScreenStream(frame())
        rig.runFor(LinkTiming().sessionMaxMs + 60_000, step = 500)
        assertEquals(LinkState.DISPLAY_ACTIVE, rig.link.currentState)

        val lab = LinkRig()
        lab.pairAndAuthenticate()
        lab.runFor(LinkTiming().sessionMaxMs + 2_000, step = 500)
        assertTrue(lab.link.currentState == LinkState.STOPPING || lab.link.currentState == LinkState.DISCONNECTED)
    }

    @Test
    fun `tudo que a navegacao envia passa pela lista branca`() {
        val rig = navRig()
        rig.link.startScreenStream(frame())
        for (m in 500 downTo 0 step 50) {
            rig.link.updateScreen(frame(meters = m))
            rig.runFor(700)
        }
        rig.link.updateScreen(TripperScreen.Recalculating(DayPeriod.NIGHT))
        rig.runFor(3_000)
        rig.client.writes.forEach {
            val p = (TripperPacket.parse(it.bytes) as PacketParseResult.Valid).packet
            assertTrue(CommandWhitelist.isTransmittable(p), Hex.encode(it.bytes))
        }
        // Histórico: troca de distância não gera uma linha por quadro.
        assertTrue(rig.snap.log.count { it.level == LogLevel.TX && it.text.startsWith("Tela de navegação") } <= 2)
    }
}
