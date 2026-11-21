package dev.hunterlab.core.ble

import dev.hunterlab.protocol.TripperTransport
import java.util.UUID

enum class UuidCategory {
    STANDARD,
    TRIPPER_DOCUMENTED,

    /** Serviço que pode alterar firmware/estado do dispositivo: a UI bloqueia qualquer interação. */
    SENSITIVE,
}

data class UuidInfo(val name: String, val category: UuidCategory, val note: String? = null)

object KnownUuids {
    private const val BASE_SUFFIX = "-0000-1000-8000-00805f9b34fb"

    /** Serviço OTAP do SDK BLE da NXP (atualização de firmware). Identificação provável. */
    val NXP_OTAP_SERVICE: UUID = UUID.fromString("01FF5550-BA5E-F4EE-5CA1-EB1E5E4B1CE0")

    private val standard16: Map<Int, String> = mapOf(
        0x1800 to "Generic Access",
        0x1801 to "Generic Attribute",
        0x180A to "Device Information",
        0x180F to "Battery Service",
        0x2A00 to "Device Name",
        0x2A01 to "Appearance",
        0x2A04 to "Peripheral Preferred Connection Parameters",
        0x2A05 to "Service Changed",
        0x2A19 to "Battery Level",
        0x2A23 to "System ID",
        0x2A24 to "Model Number String",
        0x2A25 to "Serial Number String",
        0x2A26 to "Firmware Revision String",
        0x2A27 to "Hardware Revision String",
        0x2A28 to "Software Revision String",
        0x2A29 to "Manufacturer Name String",
        0x2A50 to "PnP ID",
        0x2AA6 to "Central Address Resolution",
        0x2B29 to "Client Supported Features",
        0x2B2A to "Database Hash",
        0x2902 to "Client Characteristic Configuration (CCCD)",
        0x2901 to "Characteristic User Description",
    )

    fun lookup(uuid: UUID): UuidInfo? {
        when (uuid) {
            TripperTransport.SERVICE_UUID -> return UuidInfo(
                "Wireless UART (NXP) — serviço documentado do Tripper", UuidCategory.TRIPPER_DOCUMENTED,
            )
            TripperTransport.CHARACTERISTIC_UUID -> return UuidInfo(
                "Característica de dados do Tripper", UuidCategory.TRIPPER_DOCUMENTED,
                "Meteor 350: WRITE/WRITE_NO_RESPONSE. Hunter 350 (observado): WRITE_NO_RESPONSE. Escrita só pelo Laboratório BLE, com autorização.",
            )
        }
        if (uuid.toString().startsWith("01ff555", ignoreCase = true) && uuid.toString().endsWith("ba5e-f4ee-5ca1-eb1e5e4b1ce0", true)) {
            return UuidInfo(
                "Provável OTAP da NXP (atualização de firmware)", UuidCategory.SENSITIVE,
                "Não interagir: atualização de firmware está fora do escopo e é proibida neste projeto.",
            )
        }
        return short16(uuid)?.let { s -> standard16[s]?.let { UuidInfo(it, UuidCategory.STANDARD) } }
    }

    /** UUID de 16 bits da base Bluetooth SIG, ou nulo. */
    fun short16(uuid: UUID): Int? {
        val s = uuid.toString()
        if (!s.endsWith(BASE_SUFFIX, ignoreCase = true) || !s.startsWith("0000")) return null
        return s.substring(4, 8).toInt(16)
    }

    fun display(uuid: UUID): String = short16(uuid)?.let { "0x" + it.toString(16).uppercase().padStart(4, '0') }
        ?: uuid.toString().uppercase()
}
