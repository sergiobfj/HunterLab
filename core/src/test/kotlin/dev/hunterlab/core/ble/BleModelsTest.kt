package dev.hunterlab.core.ble

import dev.hunterlab.protocol.TripperTransport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class BleModelsTest {

    private fun device(name: String?, address: String = "AA:BB:CC:DD:EE:FF", rssi: Int = -60, services: List<UUID> = emptyList()) =
        ScannedDevice(address, name, rssi, services, lastSeenMs = 0)

    private fun uuid16(v: Int) = UUID.fromString("0000${v.toString(16).padStart(4, '0')}-0000-1000-8000-00805f9b34fb")

    @Test
    fun `bits de propriedades seguem o Android`() {
        assertEquals(setOf(CharacteristicProperty.WRITE, CharacteristicProperty.WRITE_NO_RESPONSE), CharacteristicProperty.fromBitmask(0x0C))
        assertEquals(setOf(CharacteristicProperty.READ, CharacteristicProperty.NOTIFY), CharacteristicProperty.fromBitmask(0x12))
        assertTrue(CharacteristicProperty.fromBitmask(0).isEmpty())
        assertEquals(CharacteristicProperty.entries.toSet(), CharacteristicProperty.fromBitmask(0xFF))
    }

    @Test
    fun `classificacao de candidatos a Tripper`() {
        assertEquals(CandidateLevel.STRONG, TripperCandidateClassifier.classify(device("RE_DISP")).level)
        assertEquals(CandidateLevel.STRONG, TripperCandidateClassifier.classify(device(null, services = listOf(TripperTransport.SERVICE_UUID))).level)
        assertEquals(CandidateLevel.POSSIBLE, TripperCandidateClassifier.classify(device("RE_HUNTER")).level)
        assertEquals(CandidateLevel.POSSIBLE, TripperCandidateClassifier.classify(device(null, address = "00:60:37:12:34:56")).level)
        assertEquals(CandidateLevel.NONE, TripperCandidateClassifier.classify(device("Fone XYZ")).level)
        assertEquals(CandidateLevel.NONE, TripperCandidateClassifier.classify(device("RE-DISP")).level) // hífen, não "RE_"
    }

    @Test
    fun `ordenacao poe candidatos primeiro e depois sinal`() {
        val list = listOf(device("Fone", "01", -40), device("RE_DISP", "02", -90), device(null, "00:60:37:00:00:01", -70), device("Relogio", "03", -50))
        assertEquals(listOf("02", "00:60:37:00:00:01", "01", "03"), TripperCandidateClassifier.sort(list).map { it.address })
    }

    @Test
    fun `uuids conhecidos`() {
        assertEquals(0x180A, KnownUuids.short16(uuid16(0x180A)))
        assertEquals("0x180A", KnownUuids.display(uuid16(0x180A)))
        assertNull(KnownUuids.short16(TripperTransport.SERVICE_UUID))
        assertEquals(UuidCategory.TRIPPER_DOCUMENTED, KnownUuids.lookup(TripperTransport.SERVICE_UUID)!!.category)
        assertEquals(UuidCategory.SENSITIVE, KnownUuids.lookup(KnownUuids.NXP_OTAP_SERVICE)!!.category)
        assertEquals("Device Information", KnownUuids.lookup(uuid16(0x180A))!!.name)
        assertNull(KnownUuids.lookup(UUID.randomUUID()))
    }

    private fun snapshot(vararg services: GattServiceInfo) = GattSnapshot("00:60:37:00:00:01", "RE_DISP", services.toList(), 0)

    private fun tripperService(props: Int) = GattServiceInfo(
        TripperTransport.SERVICE_UUID, true,
        listOf(GattCharacteristicInfo(TripperTransport.CHARACTERISTIC_UUID, CharacteristicProperty.fromBitmask(props))),
    )

    @Test
    fun `perfil igual ao documentado`() {
        val r = TripperProfileCheck.check(snapshot(GattServiceInfo(uuid16(0x1800), true, emptyList()), tripperService(0x0C)))
        assertEquals(ProfileVerdict.MATCHES, r.verdict)
    }

    @Test
    fun `perfil com NOTIFY e parcial`() {
        val r = TripperProfileCheck.check(snapshot(tripperService(0x1C)))
        assertEquals(ProfileVerdict.PARTIAL, r.verdict)
        assertTrue(r.items.any { it.outcome == CheckOutcome.WARN && "NOTIFY" in it.label })
    }

    @Test
    fun `perfil sem o servico`() {
        assertEquals(ProfileVerdict.NOT_FOUND, TripperProfileCheck.check(snapshot(GattServiceInfo(uuid16(0x180F), true, emptyList()))).verdict)
        assertEquals(ProfileVerdict.NOT_FOUND, TripperProfileCheck.check(snapshot()).verdict)
    }

    @Test
    fun `servico sensivel gera alerta`() {
        val r = TripperProfileCheck.check(snapshot(tripperService(0x0C), GattServiceInfo(KnownUuids.NXP_OTAP_SERVICE, true, emptyList())))
        assertEquals(ProfileVerdict.PARTIAL, r.verdict)
        assertTrue(r.items.any { it.label == "Serviço sensível" })
    }

    @Test
    fun `traducao de erros de conexao`() {
        val peer = BleErrorTranslator.connectionClosed(19, initiatedByApp = false)
        assertTrue(peer.expected)
        assertTrue(peer.hint!!.contains("5 s"))
        val byApp = BleErrorTranslator.connectionClosed(0, initiatedByApp = true)
        assertTrue(byApp.expected)
        assertTrue(byApp.hint!!.contains("ignição"))
        assertFalse(BleErrorTranslator.connectionClosed(133, false).expected)
        assertNotNull(BleErrorTranslator.connectionClosed(133, false).hint)
        assertEquals("Status 999.", BleErrorTranslator.connectionClosed(999, false).detail)
        assertTrue(BleErrorTranslator.connectionTimeout(15_000).detail.contains("15 s"))
    }

    @Test
    fun `traducao de erros de scan e leitura`() {
        for (code in 1..6) assertTrue(BleErrorTranslator.scanFailed(code).title.isNotBlank())
        assertTrue(BleErrorTranslator.scanFailed(42).detail.contains("42"))
        assertEquals(Severity.WARNING, BleErrorTranslator.scanFailed(6).severity)
        assertTrue(BleErrorTranslator.readFailed(5).hint!!.contains("pareamento"))
        for (issue in ReadinessIssue.entries) assertTrue(BleErrorTranslator.readiness(issue).title.isNotBlank())
    }

    @Test
    fun `limite de 5 buscas em 30 s`() {
        var now = 0L
        val t = ScanThrottle({ now })
        repeat(5) { assertNull(t.tryAcquire()); now += 1_000 }
        assertEquals(25_000L, t.tryAcquire()) // primeira busca foi em t=0, agora t=5000
        now = 30_000
        assertNull(t.tryAcquire())
        assertTrue(BleErrorTranslator.scanThrottled(1_500).hint!!.contains("2 s"))
    }

    @Test
    fun `relatorio declara que nada foi escrito`() {
        val snap = snapshot(tripperService(0x0C))
        val text = GattReportFormatter.format(snap, device("RE_DISP"), emptyMap(), "0.1.0", "2026-11-21 10:00")
        assertTrue(text.contains("Nenhum comando de escrita foi enviado."))
        assertTrue(text.contains("01FF0100-BA5E-F4EE-5CA1-EB1E5E4B1CE0"))
        assertTrue(text.contains("Compatível com o perfil documentado"))
    }
}
