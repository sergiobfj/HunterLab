package dev.hunterlab.app.ble

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattService
import dev.hunterlab.core.ble.CharacteristicProperty
import dev.hunterlab.core.ble.GattCharacteristicInfo
import dev.hunterlab.core.ble.GattDescriptorInfo
import dev.hunterlab.core.ble.GattServiceInfo
import dev.hunterlab.core.ble.GattSnapshot

/** Fotografia da tabela GATT descoberta. Compartilhada pela inspeção (0.1.0) e pelo Laboratório BLE. */
object GattSnapshots {
    fun from(gatt: BluetoothGatt, address: String, name: String?): GattSnapshot = GattSnapshot(
        address = address,
        name = name,
        services = gatt.services.map { s ->
            GattServiceInfo(
                uuid = s.uuid,
                primary = s.type == BluetoothGattService.SERVICE_TYPE_PRIMARY,
                characteristics = s.characteristics.map { c ->
                    GattCharacteristicInfo(
                        uuid = c.uuid,
                        properties = CharacteristicProperty.fromBitmask(c.properties),
                        descriptors = c.descriptors.map { GattDescriptorInfo(it.uuid) },
                    )
                },
            )
        },
        capturedAtMs = System.currentTimeMillis(),
    )
}
