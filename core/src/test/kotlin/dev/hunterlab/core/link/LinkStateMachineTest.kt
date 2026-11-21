package dev.hunterlab.core.link

import dev.hunterlab.protocol.CompassDirection
import dev.hunterlab.protocol.DayPeriod
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class LinkStateMachineTest {

    @Test
    fun `tabela cobre todos os estados e respeita os caminhos obrigatorios`() {
        for (s in LinkState.entries) assertTrue(LinkTransitions.targets(s).isNotEmpty(), "$s sem saída")
        assertEquals(setOf(LinkState.CONNECTING), LinkTransitions.targets(LinkState.DISCONNECTED))
        assertEquals(setOf(LinkState.DISCONNECTED), LinkTransitions.targets(LinkState.STOPPING))
        // Autenticado só passando por "autenticando"; tela só depois de autenticado.
        assertFalse(LinkTransitions.allowed(LinkState.WAITING_FOR_PIN, LinkState.AUTHENTICATED))
        assertFalse(LinkTransitions.allowed(LinkState.READY_FOR_AUTH, LinkState.AUTHENTICATED))
        assertFalse(LinkTransitions.allowed(LinkState.READY_FOR_AUTH, LinkState.DISPLAY_ACTIVE))
        assertFalse(LinkTransitions.allowed(LinkState.AUTHENTICATING, LinkState.DISPLAY_ACTIVE))
        assertFalse(LinkTransitions.allowed(LinkState.ERROR, LinkState.READY_FOR_AUTH))
        assertFalse(LinkTransitions.allowed(LinkState.DISCONNECTED, LinkState.READY_FOR_AUTH))
        // Toda sessão ativa pode ser interrompida ou perder o link.
        for (s in LinkState.entries - LinkState.DISCONNECTED - LinkState.STOPPING) {
            assertTrue(LinkTransitions.allowed(s, LinkState.DISCONNECTED), "$s → DISCONNECTED")
            if (s != LinkState.ERROR) assertTrue(LinkTransitions.allowed(s, LinkState.ERROR), "$s → ERROR")
            assertTrue(LinkTransitions.allowed(s, LinkState.STOPPING), "$s → STOPPING")
        }
    }

    @Test
    fun `so quatro estados permitem escrita`() {
        assertEquals(
            setOf(LinkState.WAITING_FOR_PIN, LinkState.AUTHENTICATING, LinkState.AUTHENTICATED, LinkState.DISPLAY_ACTIVE),
            LinkState.entries.filter { it.allowsWrites }.toSet(),
        )
    }

    @Test
    fun `comandos em estado incompativel sao recusados sem escrever`() {
        val rig = LinkRig()
        val l = rig.link
        // Desconectado
        listOf(
            l.startPairing(), l.resume(), l.submitPin("1234"), l.confirmAuthenticated(), l.reportPinRequired(),
            l.syncClock(), l.startCompassTest(CompassDirection.N, DayPeriod.DAY), l.stopCompassTest(), l.disconnectNow(),
        ).forEach { assertTrue(it is CommandResult.Rejected, "$it") }
        // Pronto para autenticar
        rig.connectToReady()
        assertEquals(LinkState.READY_FOR_AUTH, rig.state)
        listOf(
            l.submitPin("1234"), l.confirmAuthenticated(), l.syncClock(),
            l.startCompassTest(CompassDirection.N, DayPeriod.DAY), l.connect(LinkRig.ADDR, null),
        ).forEach { assertTrue(it is CommandResult.Rejected, "$it") }
        rig.runFor(10_000)
        assertTrue(rig.client.writes.isEmpty())
    }

    @Test
    fun `autorizacao exige todos os itens e nao vale depois da autenticacao`() {
        val rig = LinkRig()
        val partial = TransmissionAck(true, true, true, false, true)
        assertTrue(rig.link.authorizeTransmission(partial) is CommandResult.Rejected)
        rig.pairAndAuthenticate()
        assertTrue(rig.link.authorizeTransmission(LinkRig.ACK) is CommandResult.Rejected)
    }

    @Test
    fun `configuracao de tempos invalida e recusada`() {
        assertThrows<IllegalArgumentException> { LinkTiming(screenResendMs = 5_000) }
        assertThrows<IllegalArgumentException> { LinkTiming(maxQueue = 0) }
        assertThrows<IllegalArgumentException> { LinkTiming(maxPinAttempts = 10) }
        assertThrows<IllegalArgumentException> { LinkTiming(compassMaxMs = 600_000) }
        assertThrows<IllegalArgumentException> { LinkTiming(postDiscoveryDelayMs = -1) }
    }
}
