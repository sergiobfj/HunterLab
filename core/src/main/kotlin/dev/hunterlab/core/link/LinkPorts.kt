package dev.hunterlab.core.link

import dev.hunterlab.core.ble.GattSnapshot
import java.util.UUID

fun interface Cancellable {
    fun cancel()
}

/** Relógio e agendador. Contrato: tudo roda numa única thread (no Android, a principal). */
interface LinkScheduler {
    fun now(): Long
    fun schedule(delayMs: Long, action: () -> Unit): Cancellable
}

/** Quando abrir o servidor GATT local. O padrão (antes da conexão) é o da referência; os demais existem para diagnóstico. */
enum class SessionPurpose(val label: String) {
    LAB("Laboratório BLE"),
    NAVIGATION("Navegação"),
}

enum class ServerMode(val label: String) {
    BEFORE_CONNECT("Antes da conexão (padrão)"),
    AFTER_CONNECT("Depois da conexão, antes da descoberta"),
    DISABLED("Desligado — só diagnóstico, sem handshake"),
}

/**
 * Opções de conexão. [requireFreshAdvertisement]: só chama connectGatt depois de receber um anúncio do endereço,
 * usando o dispositivo do próprio anúncio (como a referência faz). Desligado reproduz o comportamento da 0.2.0.
 */
data class ConnectOptions(
    val requireFreshAdvertisement: Boolean = true,
    val advertisementWaitMs: Long = 12_000,
    val serverMode: ServerMode = ServerMode.BEFORE_CONNECT,
    /** Dono da sessão: o laboratório (limite de 5 min, interrompe ao sair da tela) ou a navegação (serviço em primeiro plano). */
    val purpose: SessionPurpose = SessionPurpose.LAB,
) {
    init {
        require(advertisementWaitMs in 1_000..25_000) { "Espera por anúncio deve ficar entre 1 e 25 s" }
    }
}

sealed interface WriteStart {
    data object Started : WriteStart
    data class Refused(val code: Int) : WriteStart
}

/**
 * Cliente GATT, implementado pela camada Android. Não recebe UUID: a escrita vai sempre para a
 * característica documentada (`01FF0101-…`) do serviço documentado — escrever em outro serviço é
 * impossível por construção. Cada sessão tem uma [generation]; eventos de gerações antigas são descartados.
 */
interface GattClientPort {
    fun connect(address: String, generation: Long, options: ConnectOptions): Boolean
    fun discoverServices(generation: Long): Boolean
    fun writePacket(generation: Long, bytes: ByteArray, withoutResponse: Boolean): WriteStart
    fun disconnect(generation: Long)
    fun close(generation: Long)
}

/** Servidor GATT local com o serviço que o Pod usa para responder. Não anuncia. */
interface LocalServerPort {
    fun open(generation: Long): Boolean
    fun close()
}

/** Eventos entregues pela camada Android, sempre na thread do agendador. */
interface LinkEvents {
    fun onConnected(generation: Long)
    fun onDisconnected(generation: Long, status: Int)
    fun onServicesDiscovered(generation: Long, status: Int, snapshot: GattSnapshot?)
    fun onWriteCompleted(generation: Long, status: Int)
    fun onServerReady(generation: Long, ok: Boolean)
    fun onServerPeer(generation: Long, address: String, connected: Boolean)
    fun onInbound(generation: Long, from: String, characteristic: UUID, value: ByteArray)

    /** Anúncio recente do alvo recebido (modo anúncio obrigatório), imediatamente antes do connectGatt. */
    fun onAdvertisementSeen(generation: Long, rssi: Int, connectable: Boolean?)

    /** A camada Android desistiu antes de conectar (sem anúncio, busca recusada, connectGatt nulo...). */
    fun onConnectFailed(generation: Long, reason: String)

    /** Linha de diagnóstico da camada Android (tempos, status, estado do rádio). Endereços são mascarados. */
    fun onDiagnostic(generation: Long, text: String)
}

/** Registro de dispositivos cuja autenticação foi confirmada pelo usuário. Nunca guarda PIN. */
interface PairingRegistry {
    fun isRecognized(address: String): Boolean
    fun markRecognized(address: String, atMs: Long)
    fun forget(address: String)
}

/**
 * Implementação em memória: vale só enquanto o processo do app vive. Persistir exige decisão
 * explícita do usuário (ver docs/ROADMAP.md).
 */
class InMemoryPairingRegistry : PairingRegistry {
    private val records = mutableMapOf<String, Long>()
    override fun isRecognized(address: String) = address.uppercase() in records
    override fun markRecognized(address: String, atMs: Long) {
        records[address.uppercase()] = atMs
    }

    override fun forget(address: String) {
        records.remove(address.uppercase())
    }
}

/** O que a navegação pode fazer com o Tripper agora. */
enum class StreamAvailability {
    /** Sem sessão de navegação autenticada. */
    UNAVAILABLE,

    /** Autenticado numa sessão de navegação: pode iniciar a transmissão de telas. */
    READY,

    /** Telas de navegação sendo transmitidas (reenvio periódico). */
    STREAMING,
}

/** Porta usada pelo controlador de tela da navegação. Só telas documentadas; nunca bytes livres. */
interface ScreenStreamPort {
    val streamAvailability: StreamAvailability
    fun startScreenStream(screen: dev.hunterlab.protocol.TripperScreen): CommandResult
    fun updateScreen(screen: dev.hunterlab.protocol.TripperScreen): CommandResult
    fun stopScreenStream(reason: String): CommandResult
}
