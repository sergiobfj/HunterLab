package dev.hunterlab.core.link

import dev.hunterlab.protocol.BottomLine
import dev.hunterlab.protocol.CompassDirection
import dev.hunterlab.protocol.DayPeriod
import dev.hunterlab.protocol.Hex
import dev.hunterlab.protocol.NavigationFrame
import dev.hunterlab.protocol.TripperCommands
import dev.hunterlab.protocol.TripperPacket
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Fila integrada, timeouts, interrupção, limites de sessão, bússola e lista branca. */
class TripperLinkTransmissionTest {

    // ------------------------------------------------------------------ fila

    @Test
    fun `uma escrita por vez, a proxima so depois do callback`() {
        val rig = LinkRig()
        rig.pairings.markRecognized(LinkRig.ADDR, 0)
        rig.connectToReady()
        rig.link.resume()
        rig.scheduler.advanceBy(1_000) // sem confirmar nenhuma escrita
        assertEquals(1, rig.client.writes.size)
        assertEquals("RESUME", rig.snap.diagnostics.inFlight?.let { "RESUME" })
    }

    @Test
    fun `escrita sem conclusao expira, entra em quarentena e callback atrasado e ignorado`() {
        val rig = LinkRig()
        rig.pairAndAuthenticate()
        val base = rig.client.writes.size
        rig.scheduler.advanceBy(3_000) // keepalive PING sai e fica sem callback
        assertEquals(base + 1, rig.client.writes.size)
        rig.scheduler.advanceBy(1_000) // timeout de conclusão
        assertEquals(1, rig.snap.diagnostics.writeFailures)
        rig.link.onWriteCompleted(rig.gen, 0) // atrasado: ignorado
        assertEquals(1, rig.snap.diagnostics.writeFailures)
        assertEquals(LinkState.AUTHENTICATED, rig.state, "falha isolada de PING não derruba a sessão")
    }

    @Test
    fun `falhas consecutivas de escrita encerram a transmissao`() {
        val rig = LinkRig()
        rig.pairAndAuthenticate()
        rig.client.nextWrite = WriteStart.Refused(201)
        rig.runFor(15_000)
        assertEquals(LinkState.ERROR, rig.state)
        val n = rig.client.writes.size
        rig.scheduler.advanceBy(5_000)
        assertEquals(n, rig.client.writes.size)
    }

    @Test
    fun `atraso pos descoberta e intervalo minimo sao configuraveis`() {
        val rig = LinkRig(LinkTiming(postDiscoveryDelayMs = 500, minWriteGapMs = 120))
        rig.pairings.markRecognized(LinkRig.ADDR, 0)
        rig.connectToReady()
        rig.link.resume()
        rig.runFor(2_000, step = 10)
        val t = rig.client.writes.map { it.atMs }
        assertEquals(500L, t.first())
        t.zipWithNext().forEach { (a, b) -> assertTrue(b - a >= 120, "intervalo ${b - a}") }
    }

    @Test
    fun `pacote obsoleto na fila e descartado em vez de enviado atrasado`() {
        val rig = LinkRig(LinkTiming(writeCompletionTimeoutMs = 10_000))
        rig.pairings.markRecognized(LinkRig.ADDR, 0)
        rig.connectToReady()
        rig.link.resume()
        rig.scheduler.advanceBy(5_000) // RESUME preso em andamento; SET_TIME e PINGs envelhecem na fila
        rig.link.onWriteCompleted(rig.gen, 0)
        assertEquals(listOf("RESUME"), rig.writtenKinds())
        assertTrue(rig.snap.log.any { "obsoleto" in it.text })
    }

    // ------------------------------------------------------------------ interrupção

    @Test
    fun `interromper cancela fila e agendamentos e nada mais e escrito`() {
        val rig = LinkRig()
        rig.pairings.markRecognized(LinkRig.ADDR, 0)
        rig.connectToReady()
        rig.link.resume()
        rig.scheduler.advanceBy(250) // RESUME em andamento, resto agendado
        val n = rig.client.writes.size
        assertEquals(CommandResult.Accepted, rig.link.interrupt())
        assertEquals(LinkState.STOPPING, rig.state)
        assertEquals(0, rig.snap.diagnostics.queueSize)
        rig.link.onWriteCompleted(rig.gen, 0) // conclusão tardia
        rig.scheduler.advanceBy(3_000)
        assertEquals(n, rig.client.writes.size)
        assertFalse(rig.snap.transmissionAuthorized)
        rig.link.onDisconnected(rig.gen, 19) // o Tripper encerra sozinho (forma documentada)
        assertEquals(LinkState.DISCONNECTED, rig.state)
        assertTrue(rig.snap.message!!.expected)
        assertTrue(rig.client.disconnects.isEmpty(), "não precisou desconectar pelo celular")
        assertEquals(0, rig.scheduler.pending)
    }

    @Test
    fun `se o Tripper nao encerrar, o app desconecta depois da carencia e libera recursos`() {
        val rig = LinkRig()
        rig.pairAndAuthenticate()
        rig.link.interrupt()
        val n = rig.client.writes.size
        rig.scheduler.advanceBy(rig.link.timing.stopGraceMs - 1)
        assertTrue(rig.client.disconnects.isEmpty())
        rig.scheduler.advanceBy(1)
        assertEquals(1, rig.client.disconnects.size)
        rig.scheduler.advanceBy(rig.link.timing.forceCloseMs)
        assertEquals(LinkState.DISCONNECTED, rig.state)
        assertTrue(rig.client.closes.contains(rig.gen))
        assertEquals(1, rig.server.closes)
        assertEquals(n, rig.client.writes.size)
        assertEquals(0, rig.scheduler.pending)
    }

    @Test
    fun `desconectar agora so depois de interromper`() {
        val rig = LinkRig()
        rig.pairAndAuthenticate()
        assertTrue(rig.link.disconnectNow() is CommandResult.Rejected)
        rig.link.interrupt()
        assertEquals(CommandResult.Accepted, rig.link.disconnectNow())
        assertEquals(1, rig.client.disconnects.size)
    }

    @Test
    fun `interromper durante a conexao encerra na hora`() {
        val rig = LinkRig()
        rig.link.connect(LinkRig.ADDR, null)
        rig.link.interrupt()
        assertEquals(LinkState.DISCONNECTED, rig.state)
        assertEquals(listOf(1L), rig.client.closes)
        rig.link.onConnected(1) // tarde demais
        assertEquals(LinkState.DISCONNECTED, rig.state)
    }

    @Test
    fun `interromper sem sessao cancela a pre autorizacao`() {
        val rig = LinkRig()
        rig.link.authorizeTransmission(LinkRig.ACK)
        assertNotNull(rig.snap.preAuthorizedRemainingMs)
        rig.link.interrupt()
        rig.connectToReady(authorize = false)
        assertFalse(rig.snap.transmissionAuthorized)
    }

    @Test
    fun `limite de duracao da sessao autorizada interrompe sozinho`() {
        val rig = LinkRig()
        rig.pairAndAuthenticate()
        rig.runFor(rig.link.timing.sessionMaxMs, step = 500)
        assertTrue(rig.state == LinkState.STOPPING || rig.state == LinkState.DISCONNECTED)
        val n = rig.client.writes.size
        rig.runFor(20_000, step = 500)
        assertEquals(n, rig.client.writes.size)
    }

    // ------------------------------------------------------------------ bússola

    @Test
    fun `teste de bussola exige autenticacao e duracao conservadora`() {
        val rig = LinkRig()
        rig.connectToReady()
        assertTrue(rig.link.startCompassTest(CompassDirection.NE, DayPeriod.NIGHT) is CommandResult.Rejected)
        rig.link.startPairing()
        rig.runFor(300)
        rig.link.submitPin("4821")
        rig.runFor(100)
        assertTrue(rig.link.startCompassTest(CompassDirection.NE, DayPeriod.NIGHT) is CommandResult.Rejected, "autenticando")
        rig.link.confirmAuthenticated()
        assertTrue(rig.link.startCompassTest(CompassDirection.NE, DayPeriod.NIGHT, 4_000) is CommandResult.Rejected)
        assertTrue(rig.link.startCompassTest(CompassDirection.NE, DayPeriod.NIGHT, 61_000) is CommandResult.Rejected)
        assertEquals(CommandResult.Accepted, rig.link.startCompassTest(CompassDirection.NE, DayPeriod.NIGHT, 20_000))
    }

    @Test
    fun `bussola fixa reenviada a cada 2 s ate o limite e depois para`() {
        val rig = LinkRig()
        rig.pairAndAuthenticate()
        val before = rig.client.writes.size
        rig.link.startCompassTest(CompassDirection.NE, DayPeriod.NIGHT, 20_000)
        assertEquals(LinkState.DISPLAY_ACTIVE, rig.state)
        rig.runFor(20_000, step = 10)
        assertEquals(LinkState.AUTHENTICATED, rig.state)
        val compass = rig.client.writes.drop(before).filter { LinkRig.kindOf(it.bytes) == "COMPASS" }
        assertEquals(10, compass.size)
        compass.forEach { assertEquals(TripperCommands.compass(CompassDirection.NE, DayPeriod.NIGHT).toHex(), Hex.encode(it.bytes)) }
        compass.map { it.atMs }.zipWithNext().forEach { (a, b) -> assertTrue(b - a in 1_900..2_100) }
        assertFalse(rig.client.writes.drop(before).any { LinkRig.kindOf(it.bytes) == "PING" }, "sem PING durante a tela")
        rig.runFor(10_000)
        assertFalse(rig.client.writes.drop(before + 10).any { LinkRig.kindOf(it.bytes) == "COMPASS" })
        assertEquals(10, rig.snap.compass!!.framesDelivered)
    }

    @Test
    fun `parar a bussola manualmente descarta quadros pendentes`() {
        val rig = LinkRig()
        rig.pairAndAuthenticate()
        rig.link.startCompassTest(CompassDirection.S, DayPeriod.DAY, 30_000)
        rig.runFor(5_000)
        assertEquals(CommandResult.Accepted, rig.link.stopCompassTest())
        val n = rig.client.writes.count { LinkRig.kindOf(it.bytes) == "COMPASS" }
        rig.runFor(10_000)
        assertEquals(n, rig.client.writes.count { LinkRig.kindOf(it.bytes) == "COMPASS" })
    }

    @Test
    fun `queda do link durante a bussola encerra tudo`() {
        val rig = LinkRig()
        rig.pairAndAuthenticate()
        rig.link.startCompassTest(CompassDirection.W, DayPeriod.DAY, 30_000)
        rig.runFor(3_000)
        rig.link.onDisconnected(rig.gen, 19)
        assertEquals(LinkState.DISCONNECTED, rig.state)
        val n = rig.client.writes.size
        rig.scheduler.advanceBy(60_000)
        assertEquals(n, rig.client.writes.size)
        assertEquals(0, rig.scheduler.pending)
    }

    @Test
    fun `observacoes do teste sao registradas no relatorio`() {
        val rig = LinkRig()
        assertTrue(rig.link.recordObservation(CompassObservation.SHOWN) is CommandResult.Rejected)
        rig.pairAndAuthenticate()
        rig.link.startCompassTest(CompassDirection.N, DayPeriod.DAY, 10_000)
        rig.runFor(10_000)
        rig.link.recordObservation(CompassObservation.SHOWN)
        rig.link.recordObservation(AfterTestObservation.LINK_DROPPED)
        val report = rig.link.report("0.2.0", "agora")
        assertTrue("A bússola apareceu" in report)
        assertTrue("A conexão caiu" in report)
        assertFalse("AA:BB:CC" in report)
    }

    // ------------------------------------------------------------------ lista branca

    @Test
    fun `lista branca aceita so os comandos documentados permitidos`() {
        val allowed = listOf(
            TripperCommands.handshakeShowPin(), TripperCommands.handshakeResume(), TripperCommands.pingFirmware(),
            TripperCommands.pin("1234"), TripperCommands.setTime(9, 5, dev.hunterlab.protocol.ClockFormat.H24),
            TripperCommands.compass(CompassDirection.SE, DayPeriod.NIGHT),
            // 0.3.0: telas de navegação documentadas (só em sessões de navegação — ver TripperLinkNavigationStreamTest).
            NavigationFrame(0x14, 167, 1, bottomLine = BottomLine.TotalDistance(2542)).toPacket(),
            TripperCommands.recalculating(DayPeriod.DAY),
        )
        allowed.forEach { assertTrue(CommandWhitelist.isTransmittable(it), it.toHex()) }
        val blocked = listOf(
            TripperCommands.callIcon(),
            TripperCommands.idleScreen(DayPeriod.DAY),
            TripperCommands.routeStarted(),
            TripperPacket.fromPayload(ByteArray(18) { 0x7E }),
            TripperPacket.fromPayload(ByteArray(18).also { it[0] = 0x10; it[1] = 0x11; it[2] = 0x41; it[14] = 0x33 }), // direção inválida
        )
        blocked.forEach { assertFalse(CommandWhitelist.isTransmittable(it), it.toHex()) }
        assertFalse(CommandWhitelist.matches(CommandKind.PING, TripperCommands.handshakeShowPin()))
        assertFalse(CommandWhitelist.matches(CommandKind.SHOW_PIN, TripperCommands.handshakeResume()))
    }

    @Test
    fun `tudo que chega ao radio passa pela lista branca`() {
        val rig = LinkRig()
        rig.pairAndAuthenticate()
        rig.link.syncClock()
        rig.link.startCompassTest(CompassDirection.N, DayPeriod.DAY, 6_000)
        rig.runFor(10_000)
        rig.client.writes.forEach {
            val p = (TripperPacket.parse(it.bytes) as dev.hunterlab.protocol.PacketParseResult.Valid).packet
            assertTrue(CommandWhitelist.isTransmittable(p), Hex.encode(it.bytes))
            assertEquals(20, it.bytes.size)
        }
    }
}
