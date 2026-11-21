package dev.hunterlab.core.ble

import dev.hunterlab.protocol.TripperTransport

enum class CandidateLevel(val label: String) {
    STRONG("Tripper provável"),
    POSSIBLE("Possível Tripper"),
    NONE(""),
}

data class CandidateMatch(val level: CandidateLevel, val reasons: List<String>)

/**
 * Classifica um dispositivo do scan. A Hunter 350 2027 pode anunciar outro nome; por isso
 * também olhamos o serviço anunciado e o OUI da NXP.
 */
object TripperCandidateClassifier {
    fun classify(device: ScannedDevice): CandidateMatch {
        val reasons = mutableListOf<String>()
        var level = CandidateLevel.NONE
        if (device.name == TripperTransport.DEVICE_NAME) {
            reasons += "Nome documentado \"${TripperTransport.DEVICE_NAME}\""
            level = CandidateLevel.STRONG
        } else if (device.name?.startsWith("RE_", ignoreCase = true) == true) {
            reasons += "Nome começa com \"RE_\""
            level = CandidateLevel.POSSIBLE
        }
        if (TripperTransport.SERVICE_UUID in device.serviceUuids) {
            reasons += "Anuncia o serviço documentado (NXP Wireless UART)"
            level = CandidateLevel.STRONG
        }
        if (device.address.uppercase().startsWith(TripperTransport.NXP_OUI_PREFIX)) {
            reasons += "Endereço com OUI da NXP (${TripperTransport.NXP_OUI_PREFIX})"
            if (level == CandidateLevel.NONE) level = CandidateLevel.POSSIBLE
        }
        return CandidateMatch(level, reasons)
    }

    /** Candidatos primeiro, depois sinal mais forte. */
    fun sort(devices: Collection<ScannedDevice>): List<ScannedDevice> =
        devices.sortedWith(compareBy<ScannedDevice> { classify(it).level.ordinal }.thenByDescending { it.rssi })
}

enum class CheckOutcome { PASS, FAIL, WARN, INFO }

data class ProfileCheckItem(val label: String, val outcome: CheckOutcome, val detail: String)

enum class ProfileVerdict(val label: String) {
    MATCHES("Compatível com o perfil documentado (Meteor 350)"),
    PARTIAL("Parcialmente compatível — registrar diferenças"),
    NOT_FOUND("Serviço documentado não encontrado"),
}

data class ProfileReport(val verdict: ProfileVerdict, val items: List<ProfileCheckItem>)

/** Compara a tabela GATT descoberta com o perfil documentado da Meteor 350. Somente leitura. */
object TripperProfileCheck {
    fun check(snapshot: GattSnapshot): ProfileReport {
        val items = mutableListOf<ProfileCheckItem>()
        val service = snapshot.services.firstOrNull { it.uuid == TripperTransport.SERVICE_UUID }
        if (service == null) {
            items += ProfileCheckItem("Serviço 01FF0100-…", CheckOutcome.FAIL, "Ausente. Pode ser outro dispositivo ou firmware diferente.")
        } else {
            items += ProfileCheckItem("Serviço 01FF0100-…", CheckOutcome.PASS, "Presente.")
        }
        val ch = service?.characteristics?.firstOrNull { it.uuid == TripperTransport.CHARACTERISTIC_UUID }
        if (service != null && ch == null) {
            items += ProfileCheckItem("Característica 01FF0101-…", CheckOutcome.FAIL, "Ausente no serviço.")
        }
        if (ch != null) {
            items += ProfileCheckItem("Característica 01FF0101-…", CheckOutcome.PASS, "Presente.")
            val writable = ch.properties.any { it == CharacteristicProperty.WRITE || it == CharacteristicProperty.WRITE_NO_RESPONSE }
            items += ProfileCheckItem(
                "Propriedades de escrita",
                if (writable) CheckOutcome.PASS else CheckOutcome.WARN,
                ch.properties.joinToString { it.label }.ifEmpty { "nenhuma" } +
                    if (writable) " (como documentado)" else " (esperado WRITE/WRITE_NO_RESPONSE)",
            )
            val notify = CharacteristicProperty.NOTIFY in ch.properties || CharacteristicProperty.INDICATE in ch.properties
            items += ProfileCheckItem(
                "Sem NOTIFY/INDICATE",
                if (notify) CheckOutcome.WARN else CheckOutcome.PASS,
                if (notify) "Diferente da Meteor 350: há notificação. Registrar no teste físico." else "Como documentado: respostas chegam por servidor GATT local.",
            )
        }
        val sensitive = snapshot.services.filter { KnownUuids.lookup(it.uuid)?.category == UuidCategory.SENSITIVE }
        if (sensitive.isNotEmpty()) {
            items += ProfileCheckItem(
                "Serviço sensível",
                CheckOutcome.WARN,
                "Encontrado: ${sensitive.joinToString { KnownUuids.display(it.uuid) }}. Interação bloqueada pelo app.",
            )
        }
        val unknown = snapshot.services.count { KnownUuids.lookup(it.uuid) == null }
        items += ProfileCheckItem(
            "Serviços",
            CheckOutcome.INFO,
            "${snapshot.services.size} no total, $unknown sem nome conhecido.",
        )
        val verdict = when {
            service == null -> ProfileVerdict.NOT_FOUND
            items.any { it.outcome == CheckOutcome.FAIL || it.outcome == CheckOutcome.WARN } -> ProfileVerdict.PARTIAL
            else -> ProfileVerdict.MATCHES
        }
        return ProfileReport(verdict, items)
    }
}
