package dev.hunterlab.core.ble

/**
 * Relatório em texto da inspeção GATT, para colar em docs/TESTES_FISICOS.md.
 * Fica no aparelho: o usuário decide se copia/compartilha. Nada é enviado automaticamente.
 */
object GattReportFormatter {
    fun format(
        snapshot: GattSnapshot,
        device: ScannedDevice?,
        reads: Map<String, String>,
        appVersion: String,
        timestamp: String,
    ): String = buildString {
        appendLine("Hunter Lab $appVersion — inspeção GATT (somente leitura)")
        appendLine("Data: $timestamp")
        appendLine("Dispositivo: ${snapshot.name ?: "(sem nome)"} — ${snapshot.address}")
        device?.let {
            appendLine("RSSI: ${it.rssi} dBm · Conectável: ${it.connectable ?: "?"} · TX power: ${it.txPower ?: "?"}")
            if (it.serviceUuids.isNotEmpty()) appendLine("Serviços anunciados: ${it.serviceUuids.joinToString { u -> KnownUuids.display(u) }}")
            it.manufacturerData.forEach { (id, hex) -> appendLine("Dados do fabricante 0x${id.toString(16).uppercase().padStart(4, '0')}: $hex") }
            val match = TripperCandidateClassifier.classify(it)
            if (match.reasons.isNotEmpty()) appendLine("Indícios de Tripper: ${match.reasons.joinToString("; ")}")
        }
        appendLine()
        val report = TripperProfileCheck.check(snapshot)
        appendLine("Perfil: ${report.verdict.label}")
        report.items.forEach { appendLine("  [${it.outcome}] ${it.label}: ${it.detail}") }
        appendLine()
        appendLine("Serviços (${snapshot.services.size}):")
        snapshot.services.forEach { s ->
            val sName = KnownUuids.lookup(s.uuid)?.name ?: "desconhecido"
            appendLine("- ${s.uuid.toString().uppercase()} ($sName)${if (s.primary) "" else " [secundário]"}")
            s.characteristics.forEach { c ->
                val cName = KnownUuids.lookup(c.uuid)?.name ?: "desconhecida"
                appendLine("    • ${c.uuid.toString().uppercase()} ($cName)")
                appendLine("      propriedades: ${c.properties.joinToString { it.label }.ifEmpty { "nenhuma" }}")
                if (c.descriptors.isNotEmpty()) appendLine("      descritores: ${c.descriptors.joinToString { KnownUuids.display(it.uuid) }}")
                reads[key(s, c)]?.let { appendLine("      leitura: $it") }
            }
        }
        appendLine()
        appendLine("Nenhum comando de escrita foi enviado.")
    }

    fun key(service: GattServiceInfo, characteristic: GattCharacteristicInfo): String =
        "${service.uuid}/${characteristic.uuid}"
}
