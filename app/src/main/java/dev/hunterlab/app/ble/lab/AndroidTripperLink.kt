package dev.hunterlab.app.ble.lab

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import dev.hunterlab.app.ble.BleDiagnostics
import dev.hunterlab.app.ble.BlePermissions
import dev.hunterlab.app.ble.GattSnapshots
import dev.hunterlab.core.link.Cancellable
import dev.hunterlab.core.link.CommandWhitelist
import dev.hunterlab.core.link.ConnectOptions
import dev.hunterlab.core.link.GattClientPort
import dev.hunterlab.core.link.LinkEvents
import dev.hunterlab.core.link.LinkScheduler
import dev.hunterlab.core.link.LocalServerPort
import dev.hunterlab.core.link.WriteStart
import dev.hunterlab.protocol.TripperTransport

/** Agendador na thread principal (contrato de thread única do TripperLink). */
class HandlerLinkScheduler : LinkScheduler {
    private val handler = Handler(Looper.getMainLooper())
    override fun now(): Long = SystemClock.elapsedRealtime()
    override fun schedule(delayMs: Long, action: () -> Unit): Cancellable {
        val r = Runnable(action)
        handler.postDelayed(r, delayMs)
        return Cancellable { handler.removeCallbacks(r) }
    }
}

/**
 * Adaptador Android do Laboratório BLE. É o **único** arquivo do projeto autorizado a escrever no
 * GATT e a hospedar servidor GATT (verificado por `SafetyGuardTest`). Não decide nada: executa o que o
 * `TripperLink` (core) pede e devolve os eventos na thread principal, marcados com a geração da sessão.
 *
 * Restrições por construção:
 * - escreve **apenas** em `01FF0101-…` do serviço `01FF0100-…` (não há parâmetro de UUID);
 * - nunca ativa notificações, nunca grava descritores, não pareia, não muda MTU, não anuncia;
 * - callbacks de conexões antigas são descartados pela geração.
 */
class AndroidTripperLink(
    private val context: Context,
    private val devicesScanActive: () -> Boolean = { false },
    private val environmentNotes: () -> List<String> = { emptyList() },
) : GattClientPort, LocalServerPort {

    lateinit var events: LinkEvents

    private val main = Handler(Looper.getMainLooper())
    private var gatt: BluetoothGatt? = null
    private var gattGeneration = -1L
    private var target: BluetoothGattCharacteristic? = null
    private var server: BluetoothGattServer? = null
    private var serverGeneration = -1L

    // Busca curta que antecede o connectGatt quando o anúncio recente é obrigatório.
    private var gateCallback: ScanCallback? = null
    private var gateTimeout: Runnable? = null
    private var connectRequestedAt = 0L
    private var connectCalledAt = 0L

    private val manager get() = context.getSystemService(BluetoothManager::class.java)

    private fun diag(generation: Long, text: String) = events.onDiagnostic(generation, text)

    // ------------------------------------------------------------------ cliente

    /**
     * Com anúncio recente obrigatório (padrão), faz uma busca curta e só chama connectGatt ao receber um anúncio
     * conectável do endereço, usando o BluetoothDevice do próprio anúncio — como a referência faz. Sem essa opção,
     * reproduz a 0.2.0: getRemoteDevice(endereço) e connectGatt imediato.
     */
    @SuppressLint("MissingPermission") // verificada explicitamente
    override fun connect(address: String, generation: Long, options: ConnectOptions): Boolean {
        if (!BlePermissions.hasConnect(context)) return false
        val adapter = manager?.adapter ?: return false
        if (!adapter.isEnabled) return false
        closeClient()
        gattGeneration = generation
        connectRequestedAt = SystemClock.elapsedRealtime()
        BleDiagnostics.environment(context, address, devicesScanActive()).forEach { diag(generation, it) }
        environmentNotes().forEach { diag(generation, it) }
        if (!options.requireFreshAdvertisement) {
            val device = try {
                adapter.getRemoteDevice(address)
            } catch (_: IllegalArgumentException) {
                return false
            }
            diag(generation, "Modo 0.2.0: sem anúncio recente; dispositivo de getRemoteDevice(endereço), tipo ${BleDiagnostics.addressType(device)}.")
            return connectNow(device, generation, fromAdvertisement = false)
        }
        return startAdvertisementGate(adapter, address, generation, options.advertisementWaitMs)
    }

    @SuppressLint("MissingPermission")
    private fun startAdvertisementGate(adapter: BluetoothAdapter, address: String, generation: Long, waitMs: Long): Boolean {
        if (!BlePermissions.hasScan(context)) {
            diag(generation, "Sem permissão BLUETOOTH_SCAN para aguardar o anúncio.")
            return false
        }
        val scanner = adapter.bluetoothLeScanner ?: return false
        var otherAddressReported = false
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val self = this
                main.post {
                    if (generation != gattGeneration || gateCallback !== self) return@post
                    val device = result.device
                    if (device.address.equals(address, ignoreCase = true)) {
                        stopGate()
                        val ageMs = ((SystemClock.elapsedRealtimeNanos() - result.timestampNanos) / 1_000_000).coerceAtLeast(0)
                        diag(
                            generation,
                            "Anúncio do alvo: tipo de endereço ${BleDiagnostics.addressType(device)}, conectável=${result.isConnectable}, " +
                                "legado=${result.isLegacy}, idade $ageMs ms, ${SystemClock.elapsedRealtime() - connectRequestedAt} ms após o toque.",
                        )
                        events.onAdvertisementSeen(generation, result.rssi, result.isConnectable)
                        if (!result.isConnectable) {
                            events.onConnectFailed(
                                generation,
                                "O anúncio do Tripper não aceita conexão (não conectável). Ele pode estar conectado a outro celular/app.",
                            )
                            return@post
                        }
                        if (!connectNow(device, generation, fromAdvertisement = true)) {
                            events.onConnectFailed(generation, "connectGatt não abriu a conexão.")
                        }
                    } else if (!otherAddressReported && result.scanRecord?.deviceName == TripperTransport.DEVICE_NAME) {
                        otherAddressReported = true
                        diag(
                            generation,
                            "${TripperTransport.DEVICE_NAME} anunciando com OUTRO endereço ${BleDiagnostics.mask(device.address)} " +
                                "(tipo ${BleDiagnostics.addressType(device)}). Não será usado automaticamente.",
                        )
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                val self = this
                main.post {
                    if (generation != gattGeneration || gateCallback !== self) return@post
                    stopGate()
                    events.onConnectFailed(generation, "A busca pelo anúncio falhou (código $errorCode; 6 = buscas demais em 30 s — aguarde e tente de novo).")
                }
            }
        }
        gateCallback = callback
        try {
            // Sem filtro de hardware: endereço e nome são comparados aqui (evita filtro com tipo de endereço presumido).
            scanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), callback)
        } catch (_: SecurityException) {
            gateCallback = null
            return false
        } catch (_: IllegalStateException) {
            gateCallback = null
            return false
        }
        diag(generation, "Aguardando anúncio de ${BleDiagnostics.mask(address)} por até ${waitMs / 1000} s antes do connectGatt.")
        val timeout = Runnable {
            if (gateCallback === callback && generation == gattGeneration) {
                stopGate()
                events.onConnectFailed(
                    generation,
                    "Nenhum anúncio de ${BleDiagnostics.mask(address)} em ${waitMs / 1000} s: o Tripper não está anunciando. Causas prováveis: " +
                        "ignição desligada ou janela de ~30 s encerrada; desconexão anterior pelo celular no mesmo ciclo de ignição; " +
                        "outro app conectado; endereço diferente. Cicle a ignição e tente de novo.",
                )
            }
        }
        gateTimeout = timeout
        main.postDelayed(timeout, waitMs)
        return true
    }

    @SuppressLint("MissingPermission")
    private fun stopGate() {
        gateTimeout?.let { main.removeCallbacks(it) }
        gateTimeout = null
        val cb = gateCallback ?: return
        gateCallback = null
        try {
            manager?.adapter?.bluetoothLeScanner?.stopScan(cb)
        } catch (_: SecurityException) {
        } catch (_: IllegalStateException) {
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectNow(device: BluetoothDevice, generation: Long, fromAdvertisement: Boolean): Boolean {
        val origin = if (fromAdvertisement) "do anúncio recebido" else "de getRemoteDevice"
        diag(
            generation,
            "connectGatt(autoConnect=false, TRANSPORT_LE) com o dispositivo $origin (tipo ${BleDiagnostics.addressType(device)}), " +
                "${SystemClock.elapsedRealtime() - connectRequestedAt} ms após o toque.",
        )
        connectCalledAt = SystemClock.elapsedRealtime()
        gatt = try {
            // Sobrecarga clássica: a com BluetoothGattConnectionSettings só existe na API 37+ (minSdk 31).
            @Suppress("DEPRECATION")
            device.connectGatt(context, false, ClientCallback(generation), BluetoothDevice.TRANSPORT_LE)
        } catch (_: SecurityException) {
            null
        }
        diag(generation, "connectGatt retornou ${if (gatt == null) "nulo" else "objeto"} em ${SystemClock.elapsedRealtime() - connectCalledAt} ms.")
        return gatt != null
    }

    @SuppressLint("MissingPermission")
    override fun discoverServices(generation: Long): Boolean {
        val g = gatt?.takeIf { generation == gattGeneration } ?: return false
        return try {
            g.discoverServices()
        } catch (_: SecurityException) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    override fun writePacket(generation: Long, bytes: ByteArray, withoutResponse: Boolean): WriteStart {
        val g = gatt
        val ch = target
        if (g == null || ch == null || generation != gattGeneration) return WriteStart.Refused(-1)
        if (ch.uuid != TripperTransport.CHARACTERISTIC_UUID || ch.service?.uuid != TripperTransport.SERVICE_UUID) {
            return WriteStart.Refused(-2)
        }
        if (bytes.size != 20) return WriteStart.Refused(-3)
        // A característica UART também pode transportar atualização de firmware.
        // Valida uma cópia: o mesmo quadro aprovado é usado nos dois caminhos Android.
        val outbound = bytes.copyOf()
        if (!CommandWhitelist.isTransmittable(outbound)) return WriteStart.Refused(-5)
        val type = if (withoutResponse) BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE else BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val code = g.writeCharacteristic(ch, outbound, type)
                if (code == BluetoothStatusCodes.SUCCESS) WriteStart.Started else WriteStart.Refused(code)
            } else {
                @Suppress("DEPRECATION")
                run {
                    ch.writeType = type
                    ch.value = outbound
                    if (g.writeCharacteristic(ch)) WriteStart.Started else WriteStart.Refused(0)
                }
            }
        } catch (_: SecurityException) {
            WriteStart.Refused(-4)
        }
    }

    @SuppressLint("MissingPermission")
    override fun disconnect(generation: Long) {
        if (generation != gattGeneration) return
        try {
            gatt?.disconnect()
        } catch (_: SecurityException) {
        }
    }

    override fun close(generation: Long) {
        if (generation == gattGeneration) closeClient()
    }

    @SuppressLint("MissingPermission")
    private fun closeClient() {
        stopGate()
        try {
            gatt?.close()
        } catch (_: SecurityException) {
        }
        gatt = null
        target = null
        gattGeneration = -1
    }

    private inner class ClientCallback(private val generation: Long) : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            main.post {
                if (generation != gattGeneration) return@post
                diag(generation, "onConnectionStateChange status=$status newState=$newState após ${SystemClock.elapsedRealtime() - connectCalledAt} ms do connectGatt.")
                when {
                    newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS -> events.onConnected(generation)
                    newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS ->
                        events.onDisconnected(generation, status)
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val snapshot = if (status == BluetoothGatt.GATT_SUCCESS) GattSnapshots.from(g, g.device.address, null) else null
            val ch = g.getService(TripperTransport.SERVICE_UUID)?.getCharacteristic(TripperTransport.CHARACTERISTIC_UUID)
            main.post {
                if (generation != gattGeneration) return@post
                target = ch
                events.onServicesDiscovered(generation, status, snapshot)
            }
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            if (c.uuid != TripperTransport.CHARACTERISTIC_UUID) return
            main.post { if (generation == gattGeneration) events.onWriteCompleted(generation, status) }
        }
    }

    // ------------------------------------------------------------------ servidor local

    /**
     * Mesmo serviço/característica do Pod, com WRITE e WRITE_NO_RESPONSE (como a referência).
     * O Pod responde por aqui usando a conexão já existente: não há anúncio.
     */
    @SuppressLint("MissingPermission")
    override fun open(generation: Long): Boolean {
        if (!BlePermissions.hasConnect(context)) return false
        close()
        val s = try {
            manager?.openGattServer(context, ServerCallback(generation))
        } catch (_: SecurityException) {
            null
        } ?: return false
        val service = BluetoothGattService(TripperTransport.SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        service.addCharacteristic(
            BluetoothGattCharacteristic(
                TripperTransport.CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_WRITE,
            ),
        )
        server = s
        serverGeneration = generation
        return try {
            s.addService(service)
        } catch (_: SecurityException) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    override fun close() {
        try {
            server?.close()
        } catch (_: SecurityException) {
        }
        server = null
        serverGeneration = -1
    }

    private inner class ServerCallback(private val generation: Long) : BluetoothGattServerCallback() {
        override fun onServiceAdded(status: Int, service: BluetoothGattService) {
            val ok = status == BluetoothGatt.GATT_SUCCESS && service.uuid == TripperTransport.SERVICE_UUID
            main.post { if (generation == serverGeneration) events.onServerReady(generation, ok) }
        }

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            val address = device.address
            val connected = newState == BluetoothProfile.STATE_CONNECTED
            main.post { if (generation == serverGeneration) events.onServerPeer(generation, address, connected) }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?,
        ) {
            val ours = characteristic.uuid == TripperTransport.CHARACTERISTIC_UUID
            val status = when {
                !ours -> BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED
                preparedWrite -> BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED
                offset != 0 -> BluetoothGatt.GATT_INVALID_OFFSET
                else -> BluetoothGatt.GATT_SUCCESS
            }
            if (responseNeeded) {
                try {
                    server?.sendResponse(device, requestId, status, offset, null)
                } catch (_: SecurityException) {
                }
            }
            val copy = value?.copyOf() ?: ByteArray(0)
            val address = device.address
            val uuid = characteristic.uuid
            main.post { if (generation == serverGeneration) events.onInbound(generation, address, uuid, copy) }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicReadRequest(device: BluetoothDevice, requestId: Int, offset: Int, characteristic: BluetoothGattCharacteristic) {
            // Como a referência: resposta vazia. Nada do celular é exposto por leitura.
            try {
                server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, ByteArray(0))
            } catch (_: SecurityException) {
            }
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?,
        ) {
            if (responseNeeded) {
                try {
                    server?.sendResponse(device, requestId, BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, offset, null)
                } catch (_: SecurityException) {
                }
            }
        }
    }
}
