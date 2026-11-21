package dev.hunterlab.core.ble

import java.util.UUID

/** Dispositivo visto no scan. Dados brutos de anúncio em hex (sem ByteArray, para igualdade estrutural). */
data class ScannedDevice(
    val address: String,
    val name: String?,
    val rssi: Int,
    val serviceUuids: List<UUID> = emptyList(),
    /** Company ID → bytes em hex. */
    val manufacturerData: Map<Int, String> = emptyMap(),
    val connectable: Boolean? = null,
    val txPower: Int? = null,
    val lastSeenMs: Long,
    /** Tipo de endereço informado pelo anúncio (0 = público, 1 = aleatório), quando o Android expõe (API 35+). */
    val addressType: Int? = null,
)

/** Propriedades de característica GATT; os bits são os mesmos de `BluetoothGattCharacteristic`. */
enum class CharacteristicProperty(val bit: Int, val label: String) {
    BROADCAST(0x01, "BROADCAST"),
    READ(0x02, "READ"),
    WRITE_NO_RESPONSE(0x04, "WRITE_NO_RESPONSE"),
    WRITE(0x08, "WRITE"),
    NOTIFY(0x10, "NOTIFY"),
    INDICATE(0x20, "INDICATE"),
    SIGNED_WRITE(0x40, "SIGNED_WRITE"),
    EXTENDED_PROPS(0x80, "EXTENDED_PROPS");

    val isWrite: Boolean get() = this == WRITE || this == WRITE_NO_RESPONSE || this == SIGNED_WRITE

    companion object {
        fun fromBitmask(mask: Int): Set<CharacteristicProperty> = entries.filter { mask and it.bit != 0 }.toSet()
    }
}

data class GattDescriptorInfo(val uuid: UUID)

data class GattCharacteristicInfo(
    val uuid: UUID,
    val properties: Set<CharacteristicProperty>,
    val descriptors: List<GattDescriptorInfo> = emptyList(),
) {
    val readable: Boolean get() = CharacteristicProperty.READ in properties
}

data class GattServiceInfo(
    val uuid: UUID,
    val primary: Boolean,
    val characteristics: List<GattCharacteristicInfo>,
)

/** Fotografia da tabela GATT, guardada em memória para sobreviver à queda do link (watchdog do Pod). */
data class GattSnapshot(
    val address: String,
    val name: String?,
    val services: List<GattServiceInfo>,
    val capturedAtMs: Long,
) {
    fun findCharacteristic(service: UUID, characteristic: UUID): GattCharacteristicInfo? =
        services.firstOrNull { it.uuid == service }?.characteristics?.firstOrNull { it.uuid == characteristic }
}

enum class ConnectionPhase(val label: String) {
    IDLE("Não conectado"),
    CONNECTING("Conectando…"),
    CONNECTED("Conectado"),
    DISCOVERING("Descobrindo serviços…"),
    READY("Serviços inspecionados"),
    DISCONNECTING("Desconectando…"),
    DISCONNECTED("Desconectado"),
    FAILED("Falha"),
}
