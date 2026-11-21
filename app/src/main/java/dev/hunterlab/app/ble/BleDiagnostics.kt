package dev.hunterlab.app.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import dev.hunterlab.core.link.LinkFormat

/**
 * Medidas de diagnóstico de conexão, idênticas para a inspeção (0.1.0) e o Laboratório BLE, para comparar os dois
 * caminhos no mesmo aparelho. Somente leitura de estado do Android; endereços sempre mascarados.
 */
object BleDiagnostics {

    fun mask(address: String?): String = LinkFormat.maskAddress(address) ?: "?"

    /** Tipo de endereço do objeto BluetoothDevice usado na conexão (API 35+). */
    fun addressType(device: BluetoothDevice): String =
        if (Build.VERSION.SDK_INT >= 35) {
            when (device.addressType) {
                BluetoothDevice.ADDRESS_TYPE_PUBLIC -> "público"
                BluetoothDevice.ADDRESS_TYPE_RANDOM -> "aleatório"
                BluetoothDevice.ADDRESS_TYPE_ANONYMOUS -> "anônimo"
                else -> "desconhecido (${device.addressType})"
            }
        } else {
            "indisponível (Android < 15)"
        }

    fun scanAddressType(type: Int?): String = when (type) {
        null -> "não informado"
        0 -> "público"
        1 -> "aleatório"
        else -> "código $type"
    }

    fun ageText(lastSeenWallMs: Long?): String =
        lastSeenWallMs?.let { "há ${LinkFormat.seconds((System.currentTimeMillis() - it).coerceAtLeast(0))}" } ?: "desconhecido"

    /** Estado do rádio no instante anterior à conexão: busca ativa, outras conexões GATT (de qualquer app). */
    @SuppressLint("MissingPermission")
    fun environment(context: Context, targetAddress: String, devicesScanActive: Boolean): List<String> {
        val lines = mutableListOf<String>()
        val manager = context.getSystemService(BluetoothManager::class.java)
        val adapter = manager?.adapter
        lines += "Bluetooth: ${when (adapter?.state) {
            BluetoothAdapter.STATE_ON -> "ligado"
            BluetoothAdapter.STATE_TURNING_ON -> "ligando"
            BluetoothAdapter.STATE_TURNING_OFF -> "desligando"
            BluetoothAdapter.STATE_OFF -> "desligado"
            else -> "?"
        }} · busca da aba Dispositivos: ${if (devicesScanActive) "ATIVA" else "parada"}"
        if (manager == null || adapter == null || !BlePermissions.hasConnect(context)) return lines
        try {
            val gatt = manager.getConnectedDevices(BluetoothProfile.GATT)
            val server = manager.getConnectedDevices(BluetoothProfile.GATT_SERVER)
            val target = adapter.getRemoteDevice(targetAddress)
            val targetState = when (manager.getConnectionState(target, BluetoothProfile.GATT)) {
                BluetoothProfile.STATE_CONNECTED -> "CONECTADO"
                BluetoothProfile.STATE_CONNECTING -> "conectando"
                BluetoothProfile.STATE_DISCONNECTING -> "desconectando"
                else -> "desconectado"
            }
            lines += "Conexões GATT no sistema (qualquer app): ${gatt.size}" +
                (if (gatt.isEmpty()) "" else " → ${gatt.joinToString { mask(it.address) }}") +
                " · lado servidor: ${server.size}"
            lines += "Alvo ${mask(targetAddress)} no sistema: $targetState" +
                if (gatt.any { it.address.equals(targetAddress, ignoreCase = true) }) " — JÁ HÁ OUTRA CONEXÃO GATT COM ELE" else ""
        } catch (_: SecurityException) {
            lines += "Sem permissão para consultar conexões."
        } catch (_: IllegalArgumentException) {
            lines += "Endereço inválido para consulta."
        }
        return lines
    }
}
