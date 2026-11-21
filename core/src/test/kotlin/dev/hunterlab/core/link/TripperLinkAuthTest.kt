package dev.hunterlab.core.link

import dev.hunterlab.protocol.Hex
import dev.hunterlab.protocol.TripperCommands
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Autorização, handshake documentado, PIN manual, retomada e tempos. */
class TripperLinkAuthTest {

    @Test
    fun `sem autorizacao o handshake e recusado e nada e escrito`() {
        val rig = LinkRig()
        rig.connectToReady(authorize = false)
        assertTrue(rig.link.startPairing() is CommandResult.Rejected)
        assertTrue(rig.link.resume() is CommandResult.Rejected)
        rig.runFor(10_000)
        assertTrue(rig.client.writes.isEmpty())
    }

    @Test
    fun `autorizacao dada com o link pronto tambem vale`() {
        val rig = LinkRig()
        rig.connectToReady(authorize = false)
        assertEquals(CommandResult.Accepted, rig.link.authorizeTransmission(LinkRig.ACK))
        assertEquals(CommandResult.Accepted, rig.link.startPairing())
    }

    @Test
    fun `pre autorizacao expira e nao passa para a sessao seguinte`() {
        val rig = LinkRig()
        rig.link.authorizeTransmission(LinkRig.ACK)
        rig.scheduler.advanceBy(rig.link.timing.preAuthorizationTtlMs + 1)
        rig.connectToReady(authorize = false)
        assertFalse(rig.snap.transmissionAuthorized)

        // Autorizada, usada numa sessão, a próxima sessão nasce bloqueada.
        val rig2 = LinkRig()
        rig2.connectToReady(authorize = true)
        rig2.link.interrupt()
        rig2.link.onDisconnected(rig2.gen, 19)
        rig2.connectToReady(authorize = false)
        assertFalse(rig2.snap.transmissionAuthorized)
        assertTrue(rig2.link.startPairing() is CommandResult.Rejected)
    }

    @Test
    fun `SHOW_PIN respeita o atraso pos descoberta e usa escrita sem resposta`() {
        val rig = LinkRig()
        rig.connectToReady()
        rig.link.startPairing()
        assertEquals(LinkState.WAITING_FOR_PIN, rig.state)
        rig.scheduler.advanceBy(199)
        assertTrue(rig.client.writes.isEmpty())
        rig.scheduler.advanceBy(1)
        val w = rig.client.writes.single()
        assertEquals("SHOW_PIN", LinkRig.kindOf(w.bytes))
        assertEquals(200L, w.atMs)
        assertTrue(w.withoutResponse)
        assertEquals(TripperCommands.handshakeShowPin().toHex(), Hex.encode(w.bytes))
    }

    @Test
    fun `keepalive comeca so depois do handshake e segue o intervalo da referencia`() {
        val rig = LinkRig()
        rig.connectToReady()
        rig.runFor(5_000)
        assertTrue(rig.client.writes.isEmpty())
        rig.link.startPairing()
        rig.runFor(8_000)
        val kinds = rig.writtenKinds()
        assertEquals("SHOW_PIN", kinds.first())
        assertTrue(kinds.drop(1).all { it == "PING" } && kinds.size >= 3, kinds.toString())
        val times = rig.client.writes.map { it.atMs }
        times.zipWithNext().forEach { (a, b) -> assertTrue(b - a in 2_000..3_000, "intervalo ${b - a}") }
    }

    @Test
    fun `PIN e enviado so manualmente e o sucesso da escrita nao autentica`() {
        val rig = LinkRig()
        rig.connectToReady()
        rig.link.startPairing()
        rig.runFor(300)
        assertEquals(CommandResult.Accepted, rig.link.submitPin("4821"))
        rig.runFor(100)
        assertEquals("PIN", rig.writtenKinds().last())
        assertEquals(LinkState.AUTHENTICATING, rig.state)
        assertTrue(rig.snap.authCommandDelivered)
        rig.runFor(10_000)
        assertEquals(LinkState.AUTHENTICATING, rig.state, "sem confirmação do usuário não há autenticação")
        assertEquals(CommandResult.Accepted, rig.link.confirmAuthenticated())
        assertEquals(LinkState.AUTHENTICATED, rig.state)
        assertTrue(rig.pairings.isRecognized(LinkRig.ADDR))
    }

    @Test
    fun `confirmacao antes da entrega do PIN e recusada`() {
        val rig = LinkRig()
        rig.connectToReady()
        rig.link.startPairing()
        rig.runFor(300)
        rig.link.submitPin("4821")
        // escrita do PIN iniciada mas ainda sem callback de conclusão
        assertTrue(rig.link.confirmAuthenticated() is CommandResult.Rejected)
    }

    @Test
    fun `PIN invalido nao consome tentativa nem escreve`() {
        val rig = LinkRig()
        rig.connectToReady()
        rig.link.startPairing()
        rig.runFor(300)
        val before = rig.client.writes.size
        listOf("", "1234567", "12 4", "12-4", "ção1").forEach {
            assertTrue(rig.link.submitPin(it) is CommandResult.Rejected, it)
        }
        assertEquals(before, rig.client.writes.size)
        assertEquals(0, rig.snap.pinAttemptsUsed)
    }

    @Test
    fun `tentativas de PIN sao limitadas e nunca automaticas`() {
        val rig = LinkRig()
        rig.connectToReady()
        rig.link.startPairing()
        rig.runFor(300)
        repeat(3) { i ->
            assertEquals(CommandResult.Accepted, rig.link.submitPin("100$i"), "tentativa ${i + 1}")
            rig.runFor(100)
            if (i < 2) assertEquals(CommandResult.Accepted, rig.link.reportPinRequired())
        }
        assertTrue(rig.link.reportPinRequired() is CommandResult.Rejected)
        assertEquals(LinkState.ERROR, rig.state)
        assertEquals(3, rig.writtenKinds().count { it == "PIN" }, "um PIN por envio manual, nenhum extra")
        val after = rig.client.writes.size
        rig.runFor(5_000)
        assertEquals(after, rig.client.writes.size, "nada é escrito em erro")
    }

    @Test
    fun `tempo para digitar o PIN expira com seguranca`() {
        val rig = LinkRig()
        rig.connectToReady()
        rig.link.startPairing()
        rig.runFor(rig.link.timing.pinEntryTimeoutMs)
        assertEquals(LinkState.ERROR, rig.state)
        val after = rig.client.writes.size
        rig.runFor(rig.link.timing.stopGraceMs + rig.link.timing.forceCloseMs + 1_000)
        assertEquals(after, rig.client.writes.size)
        assertEquals(LinkState.DISCONNECTED, rig.state)
    }

    @Test
    fun `confirmacao de autenticacao tambem expira`() {
        val rig = LinkRig()
        rig.connectToReady()
        rig.link.startPairing()
        rig.runFor(300)
        rig.link.submitPin("4821")
        rig.runFor(rig.link.timing.authConfirmTimeoutMs + 100)
        assertEquals(LinkState.ERROR, rig.state)
        assertFalse(rig.pairings.isRecognized(LinkRig.ADDR))
    }

    @Test
    fun `PIN nunca aparece no historico, no snapshot nem no relatorio`() {
        val rig = LinkRig()
        rig.connectToReady()
        rig.link.startPairing()
        rig.runFor(300)
        rig.link.submitPin("4821")
        rig.runFor(100)
        val pinPacket = TripperCommands.pin("4821")
        val forbidden = listOf("4821", "34 38 32 31", pinPacket.toHex(), Hex.byte(pinPacket[18]) + " " + Hex.byte(pinPacket[19]))
        val everything = rig.snap.toString() + rig.link.report("0.2.0", "agora")
        forbidden.forEach { assertFalse(it in everything, "vazou: $it") }
        assertTrue(rig.snap.log.any { it.text == "PIN (conteúdo oculto)" })
    }

    @Test
    fun `relatorio declara evidencia visual e alerta quando a captura detalhada foi ligada`() {
        val rig = LinkRig()
        rig.pairAndAuthenticate()
        assertEquals("Autenticado (confirmação visual)", rig.state.label)
        val plain = rig.link.report("0.2.0", "agora")
        assertTrue("somente confirmação visual do usuário" in plain)
        assertFalse("ATENÇÃO: a captura detalhada" in plain)
        rig.link.setInboundCapture(true)
        rig.link.setInboundCapture(false)
        assertTrue("ATENÇÃO: a captura detalhada" in rig.link.report("0.2.0", "agora"), "o aviso persiste mesmo após desligar")
    }

    @Test
    fun `retomada so com sessao reconhecida e segue a sequencia da referencia`() {
        val rig = LinkRig()
        rig.connectToReady()
        assertTrue(rig.link.resume() is CommandResult.Rejected, "sem evidência de sessão reconhecida")
        rig.link.interrupt()
        rig.link.onDisconnected(rig.gen, 19)

        rig.pairings.markRecognized(LinkRig.ADDR, 0)
        rig.connectToReady()
        assertTrue(rig.snap.canResume)
        val t0 = rig.scheduler.time
        val before = rig.client.writes.size
        assertEquals(CommandResult.Accepted, rig.link.resume())
        rig.runFor(1_000, step = 10)
        val seq = rig.client.writes.drop(before)
        assertEquals(listOf("RESUME", "SET_TIME", "PING", "PING"), seq.take(4).map { LinkRig.kindOf(it.bytes) })
        assertTrue(seq[1].atMs - seq[0].atMs >= 200 - 10)
        assertTrue(seq[2].atMs - seq[1].atMs >= 150 - 10, "tempos: ${seq.map { it.atMs }}")
        assertTrue(seq[0].atMs - t0 >= 200, "atraso pós-descoberta")
        // SET_TIME 13:55 em 12h = captura REAL da referência
        assertEquals("50 01 37 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 51 A8", Hex.encode(seq[1].bytes))
        assertEquals(LinkState.AUTHENTICATING, rig.state)
        assertEquals(CommandResult.Accepted, rig.link.confirmAuthenticated())
    }

    @Test
    fun `se a retomada nao for reconhecida o usuario pode digitar o PIN`() {
        val rig = LinkRig()
        rig.pairings.markRecognized(LinkRig.ADDR, 0)
        rig.connectToReady()
        rig.link.resume()
        rig.runFor(1_000)
        assertEquals(CommandResult.Accepted, rig.link.reportPinRequired())
        assertEquals(LinkState.WAITING_FOR_PIN, rig.state)
    }

    @Test
    fun `falha ao entregar handshake vai para erro sem nova tentativa`() {
        val rig = LinkRig()
        rig.connectToReady()
        rig.client.nextWrite = WriteStart.Refused(201)
        rig.link.startPairing()
        rig.runFor(5_000)
        assertEquals(LinkState.ERROR, rig.state)
        assertEquals(1, rig.client.writes.size)
    }

    @Test
    fun `sincronizar relogio so depois de autenticado`() {
        val rig = LinkRig()
        rig.connectToReady()
        assertTrue(rig.link.syncClock() is CommandResult.Rejected)
        rig.pairAndAuthenticate()
        assertEquals(CommandResult.Accepted, rig.link.syncClock())
        rig.runFor(100)
        assertEquals("SET_TIME", rig.writtenKinds().last())
    }
}
