package dev.hunterlab.core.link

import dev.hunterlab.core.ble.CharacteristicProperty
import dev.hunterlab.core.ble.GattSnapshot
import dev.hunterlab.core.ble.KnownUuids
import dev.hunterlab.core.ble.UuidCategory
import dev.hunterlab.protocol.TripperTransport

/** Resultado da verificação do alvo de escrita, feita antes de qualquer transmissão. */
data class LinkProfileVerification(
    val ok: Boolean,
    val withoutResponse: Boolean,
    val reason: String,
    val notes: List<String>,
)

/**
 * Só aceita escrever na característica documentada `01FF0101-…` dentro do serviço `01FF0100-…`,
 * e só se ela anunciar WRITE_NO_RESPONSE ou WRITE. Serviços sensíveis (provável OTA) são ignorados.
 */
object LinkProfile {
    fun verify(snapshot: GattSnapshot): LinkProfileVerification {
        val notes = mutableListOf<String>()
        val sensitive = snapshot.services.filter { KnownUuids.lookup(it.uuid)?.category == UuidCategory.SENSITIVE }
        if (sensitive.isNotEmpty()) notes += "Serviço(s) sensível(is) presente(s) e ignorado(s): ${sensitive.joinToString { KnownUuids.display(it.uuid) }}"

        val service = snapshot.services.firstOrNull { it.uuid == TripperTransport.SERVICE_UUID }
            ?: return fail("Serviço documentado 01FF0100-… ausente.", notes)
        val ch = service.characteristics.firstOrNull { it.uuid == TripperTransport.CHARACTERISTIC_UUID }
            ?: return fail("Característica documentada 01FF0101-… ausente.", notes)
        val wnr = CharacteristicProperty.WRITE_NO_RESPONSE in ch.properties
        val w = CharacteristicProperty.WRITE in ch.properties
        if (!wnr && !w) return fail("A característica 01FF0101-… não anuncia escrita.", notes)
        if (CharacteristicProperty.NOTIFY in ch.properties || CharacteristicProperty.INDICATE in ch.properties) {
            notes += "A característica anuncia NOTIFY/INDICATE (diferente do documentado). Não será ativada."
        }
        val mode = if (wnr) "WRITE_NO_RESPONSE" else "WRITE (com resposta)"
        return LinkProfileVerification(true, wnr, "Perfil documentado encontrado. Modo de escrita: $mode.", notes)
    }

    private fun fail(reason: String, notes: List<String>) = LinkProfileVerification(false, false, reason, notes)
}
