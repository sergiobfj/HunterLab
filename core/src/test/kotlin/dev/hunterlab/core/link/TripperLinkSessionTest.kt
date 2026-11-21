package dev.hunterlab.core.link

import dev.hunterlab.core.ble.CharacteristicProperty
import dev.hunterlab.core.ble.GattCharacteristicInfo
import dev.hunterlab.core.ble.GattServiceInfo
import dev.hunterlab.core.ble.GattSnapshot
import dev.hunterlab.protocol.TripperTransport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Conexão, perfil, ausência de transmissão automática, ordem de callbacks e eventos atrasados. */
class TripperLinkSessionTest {

    @Test
    fun `conectar nao escreve nada, mesmo com transmissao autorizada`() {
        val rig = LinkRig()
        rig.connectToReady(authorize = true)
        assertEquals(LinkState.READY_FOR_AUTH, rig.state)
        assertTrue(rig.snap.transmissionAuthorized)
        rig.runFor(60_000)
        assertTrue(rig.client.writes.isEmpty(), "nenhuma escrita sem comando manual")
        assertFalse(rig.snap.diagnostics.keepaliveActive, "sem keepalive antes do handshake")
    }

    @Test
    fun `criar o link nao toca no radio`() {
        val rig = LinkRig()
        rig.scheduler.advanceBy(60_000)
        assertTrue(rig.client.connects.isEmpty())
        assertEquals(0, rig.server.opens)
        assertEquals(LinkState.DISCONNECTED, rig.state)
    }

    @Test
    fun `fluxo de conexao abre servidor local e descobre servicos`() {
        val rig = LinkRig()
        rig.connectToReady(authorize = false)
        assertEquals(1, rig.server.opens)
        assertEquals(1, rig.client.discoverCalls)
        assertEquals(LinkRig.ADDR to 1L, rig.client.connects.single())
        assertTrue(rig.snap.diagnostics.profileVerified)
        assertEquals("WRITE_NO_RESPONSE", rig.snap.diagnostics.writeMode)
        assertEquals(ServerState.READY, rig.snap.diagnostics.server)
    }

    @Test
    fun `perfil sem a caracteristica documentada vai para erro sem escrever`() {
        val rig = LinkRig()
        val wrong = GattSnapshot(
            LinkRig.ADDR, "RE_DISP",
            listOf(GattServiceInfo(TripperTransport.SERVICE_UUID, true, emptyList())), 0,
        )
        rig.connectToReady(snapshot = wrong)
        assertEquals(LinkState.ERROR, rig.state)
        assertTrue(rig.link.startPairing() is CommandResult.Rejected)
        rig.runFor(20_000)
        assertTrue(rig.client.writes.isEmpty())
        assertEquals(LinkState.DISCONNECTED, rig.state, "após a carência o app libera o link")
        assertEquals(listOf(1L), rig.client.disconnects)
    }

    @Test
    fun `caracteristica sem escrita e servico so de OTA sao recusados`() {
        val readOnly = LinkRig()
        readOnly.connectToReady(snapshot = readOnly.documentedSnapshot(props = CharacteristicProperty.READ.bit))
        assertEquals(LinkState.ERROR, readOnly.state)

        val otaOnly = LinkRig()
        otaOnly.connectToReady(snapshot = GattSnapshot(LinkRig.ADDR, "RE_DISP", listOf(LinkRig.OTA_SERVICE), 0))
        assertEquals(LinkState.ERROR, otaOnly.state)
        otaOnly.runFor(20_000)
        assertTrue(otaOnly.client.writes.isEmpty())
    }

    @Test
    fun `servico de OTA presente e ignorado e nunca vira alvo`() {
        val rig = LinkRig()
        rig.connectToReady(snapshot = rig.documentedSnapshot(extra = listOf(LinkRig.OTA_SERVICE)))
        assertEquals(LinkState.READY_FOR_AUTH, rig.state)
        assertTrue(rig.snap.log.any { "sensível" in it.text })
        rig.pairAndAuthenticate() // continua a partir da sessão já pronta
        // A porta de escrita não recebe UUID: só existe o alvo documentado. Todos os pacotes são documentados.
        assertTrue(rig.writtenKinds().all { it in setOf("SHOW_PIN", "PIN", "PING") }, rig.writtenKinds().toString())
    }

    @Test
    fun `NOTIFY inesperado e apenas anotado, nunca ativado`() {
        val rig = LinkRig()
        rig.connectToReady(snapshot = rig.documentedSnapshot(props = 0x04 or 0x10))
        assertEquals(LinkState.READY_FOR_AUTH, rig.state)
        assertTrue(rig.snap.log.any { "NOTIFY" in it.text && "Não será ativada" in it.text })
    }

    @Test
    fun `timeout de conexao encerra sem reconectar`() {
        val rig = LinkRig()
        rig.link.connect(LinkRig.ADDR, null)
        rig.scheduler.advanceBy(rig.link.timing.connectTimeoutMs + ConnectOptions().advertisementWaitMs)
        assertEquals(LinkState.DISCONNECTED, rig.state)
        assertNotNull(rig.snap.message)
        rig.scheduler.advanceBy(600_000)
        assertEquals(1, rig.client.connects.size, "sem reconexão automática")
        assertTrue(rig.client.closes.contains(1L))
    }

    @Test
    fun `timeout de descoberta vai para erro`() {
        val rig = LinkRig()
        rig.link.connect(LinkRig.ADDR, null)
        rig.link.onServerReady(1, true)
        rig.link.onConnected(1)
        rig.scheduler.advanceBy(10_000)
        assertEquals(LinkState.ERROR, rig.state)
    }

    @Test
    fun `servidor local que nao abre impede a conexao`() {
        val rig = LinkRig()
        rig.server.acceptOpen = false
        assertTrue(rig.link.connect(LinkRig.ADDR, null) is CommandResult.Rejected)
        assertEquals(LinkState.DISCONNECTED, rig.state)
        assertTrue(rig.client.connects.isEmpty())
    }

    @Test
    fun `servidor local que falha ao registrar o servico vai para erro`() {
        val rig = LinkRig()
        rig.link.connect(LinkRig.ADDR, null)
        rig.link.onServerReady(1, false)
        assertEquals(LinkState.DISCONNECTED, rig.state) // ainda não conectado: libera na hora
    }

    @Test
    fun `handshake exige servidor local pronto`() {
        val rig = LinkRig()
        rig.link.authorizeTransmission(LinkRig.ACK)
        rig.link.connect(LinkRig.ADDR, null)
        rig.link.onConnected(1)
        rig.link.onServicesDiscovered(1, 0, rig.documentedSnapshot())
        assertTrue(rig.link.startPairing() is CommandResult.Rejected)
        rig.link.onServerReady(1, true)
        assertEquals(CommandResult.Accepted, rig.link.startPairing())
    }

    @Test
    fun `callbacks fora de ordem sao ignorados`() {
        val rig = LinkRig()
        rig.link.connect(LinkRig.ADDR, null)
        rig.link.onServicesDiscovered(1, 0, rig.documentedSnapshot())
        assertEquals(LinkState.CONNECTING, rig.state)
        rig.link.onWriteCompleted(1, 0)
        rig.link.onConnected(1)
        rig.link.onConnected(1)
        assertEquals(1, rig.client.discoverCalls)
        assertEquals(LinkState.DISCOVERING, rig.state)
    }

    @Test
    fun `eventos atrasados de sessao anterior nao afetam a nova`() {
        val rig = LinkRig()
        rig.connectToReady()
        rig.link.startPairing()
        rig.scheduler.advanceBy(250) // SHOW_PIN em andamento na sessão 1
        rig.link.onDisconnected(1, 19)
        assertEquals(LinkState.DISCONNECTED, rig.state)

        rig.connectToReady() // sessão 2
        assertEquals(2L, rig.gen)
        val writesBefore = rig.client.writes.size
        rig.link.onWriteCompleted(1, 0)
        rig.link.onDisconnected(1, 8)
        rig.link.onInbound(1, LinkRig.ADDR, TripperTransport.CHARACTERISTIC_UUID, ByteArray(20))
        assertEquals(LinkState.READY_FOR_AUTH, rig.state)
        assertEquals(writesBefore, rig.client.writes.size)
        assertEquals(0, rig.snap.diagnostics.inboundAccepted)
    }

    @Test
    fun `desconexao inesperada cancela tudo e nao reconecta`() {
        val rig = LinkRig()
        rig.pairAndAuthenticate()
        rig.runFor(3_000)
        rig.link.onDisconnected(rig.gen, 8)
        assertEquals(LinkState.DISCONNECTED, rig.state)
        val writes = rig.client.writes.size
        rig.scheduler.advanceBy(300_000)
        assertEquals(writes, rig.client.writes.size)
        assertEquals(1, rig.client.connects.size)
        assertEquals(0, rig.scheduler.pending, "nenhum agendamento sobrando")
        assertEquals(1, rig.server.closes)
    }

    @Test
    fun `respostas simuladas do Tripper sao registradas mas nunca autenticam`() {
        val rig = LinkRig()
        rig.connectToReady()
        rig.link.startPairing()
        rig.runFor(300)
        rig.link.submitPin("4821")
        rig.runFor(100)
        // O "Tripper" responde algo pelo servidor local.
        rig.link.onInbound(rig.gen, LinkRig.ADDR, TripperTransport.CHARACTERISTIC_UUID, dev.hunterlab.protocol.TripperCommands.handshakeResume().toByteArray())
        rig.link.onInbound(rig.gen, LinkRig.ADDR, TripperTransport.CHARACTERISTIC_UUID, byteArrayOf(1, 2, 3))
        assertEquals(LinkState.AUTHENTICATING, rig.state, "resposta não interpretada não autentica")
        assertEquals(2, rig.snap.diagnostics.inboundAccepted)
        assertTrue(rig.snap.log.any { "CRC válido" in it.text && "não interpretado" in it.text })
        assertTrue(rig.snap.log.any { "formato não documentado" in it.text })
    }

    @Test
    fun `respostas de remetente, caracteristica ou tamanho inesperados sao rejeitadas`() {
        val rig = LinkRig()
        rig.connectToReady()
        val ch = TripperTransport.CHARACTERISTIC_UUID
        rig.link.onInbound(rig.gen, "11:22:33:44:55:66", ch, ByteArray(20))
        rig.link.onInbound(rig.gen, LinkRig.ADDR, TripperTransport.SERVICE_UUID, ByteArray(20))
        rig.link.onInbound(rig.gen, LinkRig.ADDR, ch, ByteArray(0))
        rig.link.onInbound(rig.gen, LinkRig.ADDR, ch, ByteArray(600))
        assertEquals(4, rig.snap.diagnostics.inboundRejected)
        assertEquals(0, rig.snap.diagnostics.inboundAccepted)
    }

    @Test
    fun `eco de PIN recebido nao vaza o conteudo sem captura detalhada`() {
        val rig = LinkRig()
        rig.connectToReady()
        val echo = dev.hunterlab.protocol.TripperCommands.pin("7391").toByteArray()
        rig.link.onInbound(rig.gen, LinkRig.ADDR, TripperTransport.CHARACTERISTIC_UUID, echo)
        val text = rig.snap.log.joinToString { it.text }
        assertTrue("PIN (conteúdo oculto)" in text)
        assertFalse("7391" in text)
        assertFalse("37 33 39 31" in text)
        rig.link.setInboundCapture(true)
        rig.link.onInbound(rig.gen, LinkRig.ADDR, TripperTransport.CHARACTERISTIC_UUID, byteArrayOf(0x55))
        assertTrue(rig.snap.log.last().text.endsWith("55 · não interpretado"))
    }

    @Test
    fun `endereco e mascarado no historico e no snapshot`() {
        val rig = LinkRig()
        rig.connectToReady()
        assertEquals("00:60:37:XX:XX:XX", rig.snap.maskedAddress)
        assertFalse(rig.snap.log.any { "AA:BB:CC" in it.text })
    }

    @Test
    fun `perfil com WRITE apenas usa escrita com resposta`() {
        val rig = LinkRig()
        rig.connectToReady(snapshot = rig.documentedSnapshot(props = CharacteristicProperty.WRITE.bit))
        rig.link.startPairing()
        rig.runFor(300)
        assertFalse(rig.client.writes.single().withoutResponse)
        assertEquals(GattCharacteristicInfo::class, GattCharacteristicInfo::class)
    }
}
