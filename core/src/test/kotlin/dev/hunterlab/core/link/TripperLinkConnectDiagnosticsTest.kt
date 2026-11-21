package dev.hunterlab.core.link

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** Regressão do diagnóstico do status 147: opções de conexão, ordem do servidor e registros. Nada é escrito. */
class TripperLinkConnectDiagnosticsTest {

    @Test
    fun `padrao exige anuncio recente e mantem o servidor antes da conexao`() {
        val rig = LinkRig()
        rig.link.connect(LinkRig.ADDR, "RE_DISP")
        val opts = rig.client.connectOptions.single()
        assertTrue(opts.requireFreshAdvertisement)
        assertEquals(ServerMode.BEFORE_CONNECT, opts.serverMode)
        assertEquals(listOf("server.open", "client.connect"), rig.client.calls)
    }

    @Test
    fun `servidor depois da conexao abre so apos o link e antes da descoberta`() {
        val rig = LinkRig()
        val opts = ConnectOptions(serverMode = ServerMode.AFTER_CONNECT)
        rig.link.authorizeTransmission(LinkRig.ACK)
        rig.link.connect(LinkRig.ADDR, "RE_DISP", opts)
        assertEquals(listOf("client.connect"), rig.client.calls)
        assertEquals(0, rig.server.opens)
        rig.link.onConnected(rig.gen)
        assertEquals(listOf("client.connect", "server.open", "client.discover"), rig.client.calls)
        rig.link.onServicesDiscovered(rig.gen, 0, rig.documentedSnapshot())
        assertEquals(LinkState.READY_FOR_AUTH, rig.state)
        assertTrue(rig.link.startPairing() is CommandResult.Rejected, "servidor ainda não confirmou")
        rig.link.onServerReady(rig.gen, true)
        assertEquals(CommandResult.Accepted, rig.link.startPairing())
    }

    @Test
    fun `sem servidor local o handshake fica indisponivel e nada e escrito`() {
        val rig = LinkRig()
        rig.connectToReady(authorize = true, options = ConnectOptions(serverMode = ServerMode.DISABLED))
        assertEquals(LinkState.READY_FOR_AUTH, rig.state)
        assertEquals(0, rig.server.opens)
        assertTrue(rig.link.startPairing() is CommandResult.Rejected)
        rig.pairings.markRecognized(LinkRig.ADDR, 0)
        assertTrue(rig.link.resume() is CommandResult.Rejected)
        rig.runFor(30_000)
        assertTrue(rig.client.writes.isEmpty())
    }

    @Test
    fun `nenhuma variante de diagnostico escreve sem autorizacao`() {
        for (fresh in listOf(true, false)) for (mode in ServerMode.entries) {
            val rig = LinkRig()
            rig.connectToReady(authorize = false, options = ConnectOptions(requireFreshAdvertisement = fresh, serverMode = mode))
            rig.runFor(20_000)
            assertTrue(rig.client.writes.isEmpty(), "fresh=$fresh mode=$mode")
        }
    }

    @Test
    fun `falha antes de conectar encerra com mensagem e sem nova tentativa`() {
        val rig = LinkRig()
        rig.link.connect(LinkRig.ADDR, "RE_DISP")
        rig.link.onConnectFailed(rig.gen, "Nenhum anúncio de 00:60:37:AA:BB:CC em 12 s.")
        assertEquals(LinkState.DISCONNECTED, rig.state)
        val msg = rig.snap.message!!
        assertTrue("00:60:37:XX:XX:XX" in msg.detail)
        assertFalse("AA:BB:CC" in msg.detail)
        rig.scheduler.advanceBy(120_000)
        assertEquals(1, rig.client.connects.size)
        assertEquals(0, rig.scheduler.pending)
    }

    @Test
    fun `prazo de conexao cobre a espera pelo anuncio`() {
        val rig = LinkRig()
        rig.link.connect(LinkRig.ADDR, "RE_DISP")
        rig.scheduler.advanceBy(rig.link.timing.connectTimeoutMs + ConnectOptions().advertisementWaitMs - 1)
        assertEquals(LinkState.CONNECTING, rig.state)
        rig.scheduler.advanceBy(1)
        assertEquals(LinkState.DISCONNECTED, rig.state)

        val legacy = LinkRig()
        legacy.link.connect(LinkRig.ADDR, "RE_DISP", ConnectOptions(requireFreshAdvertisement = false))
        legacy.scheduler.advanceBy(legacy.link.timing.connectTimeoutMs)
        assertEquals(LinkState.DISCONNECTED, legacy.state)
    }

    @Test
    fun `status 147 fica registrado na tentativa com tempos e dica`() {
        val rig = LinkRig()
        rig.link.connect(LinkRig.ADDR, "RE_DISP", lastAdvertisementAgeMs = 42_000)
        rig.link.onServerReady(rig.gen, true)
        rig.scheduler.advanceBy(1_500)
        rig.link.onAdvertisementSeen(rig.gen, -71, true)
        rig.scheduler.advanceBy(8_000)
        rig.link.onDisconnected(rig.gen, 147)
        val a = rig.snap.attempts.single()
        assertEquals(42_000L, a.lastAdvertisementAgeMs)
        assertEquals(1_500L, a.advertisementSeenAfterMs)
        assertNull(a.connectedAfterMs)
        assertEquals(147, a.endStatus)
        assertEquals(9_500L, a.endedAfterMs)
        assertTrue(rig.snap.message!!.title.contains("147"))
        assertTrue(rig.snap.message!!.hint!!.contains("ciclo de ignição"))
        val report = rig.link.report("0.2.1", "agora")
        assertTrue("status 147" in report)
        assertTrue("último anúncio da busca: 42,0 s" in report)
    }

    @Test
    fun `tentativa bem sucedida registra cada etapa`() {
        val rig = LinkRig()
        rig.link.connect(LinkRig.ADDR, "RE_DISP")
        rig.scheduler.advanceBy(100)
        rig.link.onServerReady(rig.gen, true)
        rig.scheduler.advanceBy(900)
        rig.link.onAdvertisementSeen(rig.gen, -60, true)
        rig.scheduler.advanceBy(700)
        rig.link.onConnected(rig.gen)
        rig.scheduler.advanceBy(400)
        rig.link.onServicesDiscovered(rig.gen, 0, rig.documentedSnapshot())
        rig.link.interrupt()
        rig.link.onDisconnected(rig.gen, 19)
        val a = rig.snap.attempts.single()
        assertEquals(100L, a.serverReadyAfterMs)
        assertEquals(1_000L, a.advertisementSeenAfterMs)
        assertEquals(1_700L, a.connectedAfterMs)
        assertEquals(2_100L, a.discoveredAfterMs)
        assertTrue(a.outcome.startsWith("Chegou a"))
    }

    @Test
    fun `diagnosticos sao registrados com endereco mascarado e eventos antigos ignorados`() {
        val rig = LinkRig()
        rig.link.connect(LinkRig.ADDR, "RE_DISP")
        rig.link.onDiagnostic(rig.gen, "connectGatt(00:60:37:AA:BB:CC) retornou objeto; outras conexões GATT: 11:22:33:44:55:66")
        val line = rig.snap.log.last().text
        assertTrue(line.startsWith("diag: "))
        assertTrue("00:60:37:XX:XX:XX" in line && "11:22:33:XX:XX:XX" in line)
        assertFalse("AA:BB:CC" in line || "44:55:66" in line)
        val before = rig.snap.log.size
        rig.link.onDiagnostic(rig.gen + 5, "de outra sessão")
        assertFalse(rig.snap.log.drop(before).any { "de outra sessão" in it.text })
    }

    @Test
    fun `anuncio fora da fase de conexao e ignorado`() {
        val rig = LinkRig()
        rig.connectToReady()
        rig.link.onAdvertisementSeen(rig.gen, -50, true)
        rig.link.onConnectFailed(rig.gen, "tarde demais")
        assertEquals(LinkState.READY_FOR_AUTH, rig.state)
    }

    @Test
    fun `opcoes invalidas sao recusadas`() {
        assertThrows<IllegalArgumentException> { ConnectOptions(advertisementWaitMs = 500) }
        assertThrows<IllegalArgumentException> { ConnectOptions(advertisementWaitMs = 60_000) }
    }

    @Test
    fun `mascara de enderecos em texto livre`() {
        assertEquals("a 00:60:37:XX:XX:XX b", LinkFormat.maskMacs("a 00:60:37:ab:cd:ef b"))
        assertEquals("sem endereço", LinkFormat.maskMacs("sem endereço"))
    }
}
