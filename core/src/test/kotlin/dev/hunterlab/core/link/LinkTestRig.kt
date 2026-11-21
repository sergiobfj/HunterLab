package dev.hunterlab.core.link

import dev.hunterlab.core.ble.CharacteristicProperty
import dev.hunterlab.core.ble.GattCharacteristicInfo
import dev.hunterlab.core.ble.GattServiceInfo
import dev.hunterlab.core.ble.GattSnapshot
import dev.hunterlab.core.ble.KnownUuids
import dev.hunterlab.protocol.ClockFormat
import dev.hunterlab.protocol.DecodedPacket
import dev.hunterlab.protocol.PacketDecoder
import dev.hunterlab.protocol.PacketParseResult
import dev.hunterlab.protocol.TripperPacket
import dev.hunterlab.protocol.TripperTransport
import java.time.LocalTime

/** Relógio virtual determinístico: só avança quando o teste manda. */
class VirtualScheduler : LinkScheduler {
    var time = 0L
        private set
    private var seq = 0L

    private class Task(val at: Long, val seq: Long, val action: () -> Unit)

    private val tasks = mutableListOf<Task>()

    override fun now() = time

    override fun schedule(delayMs: Long, action: () -> Unit): Cancellable {
        require(delayMs >= 0) { "atraso negativo" }
        val t = Task(time + delayMs, seq++, action)
        tasks += t
        return Cancellable { tasks.remove(t) }
    }

    fun advanceBy(ms: Long) {
        val target = time + ms
        while (true) {
            val next = tasks.filter { it.at <= target }.minWithOrNull(compareBy<Task>({ it.at }, { it.seq })) ?: break
            tasks.remove(next)
            time = next.at
            next.action()
        }
        time = target
    }

    val pending: Int get() = tasks.size
}

/** Cliente GATT falso: registra chamadas; os callbacks são disparados pelo teste. */
class FakeGattClient(private val scheduler: VirtualScheduler) : GattClientPort {
    data class Write(val generation: Long, val bytes: ByteArray, val withoutResponse: Boolean, val atMs: Long)

    val connects = mutableListOf<Pair<String, Long>>()
    val connectOptions = mutableListOf<ConnectOptions>()
    /** Ordem das chamadas ao rádio (cliente e servidor), para verificar a sequência de inicialização. */
    var calls: MutableList<String> = mutableListOf()
    val writes = mutableListOf<Write>()
    val disconnects = mutableListOf<Long>()
    val closes = mutableListOf<Long>()
    var discoverCalls = 0
    var acceptConnect = true
    var acceptDiscover = true
    var nextWrite: WriteStart = WriteStart.Started

    override fun connect(address: String, generation: Long, options: ConnectOptions): Boolean {
        connects += address to generation
        connectOptions += options
        calls += "client.connect"
        return acceptConnect
    }

    override fun discoverServices(generation: Long): Boolean {
        discoverCalls++
        calls += "client.discover"
        return acceptDiscover
    }

    override fun writePacket(generation: Long, bytes: ByteArray, withoutResponse: Boolean): WriteStart {
        writes += Write(generation, bytes.copyOf(), withoutResponse, scheduler.now())
        return nextWrite
    }

    override fun disconnect(generation: Long) {
        disconnects += generation
    }

    override fun close(generation: Long) {
        closes += generation
    }
}

class FakeLocalServer(private val calls: () -> MutableList<String> = { mutableListOf() }) : LocalServerPort {
    var opens = 0
    var closes = 0
    var acceptOpen = true
    override fun open(generation: Long): Boolean {
        opens++
        calls() += "server.open"
        return acceptOpen
    }

    override fun close() {
        closes++
    }
}

/** Monta um TripperLink com fakes e um "Tripper" simulado pelos eventos. */
class LinkRig(timing: LinkTiming = LinkTiming()) {
    val scheduler = VirtualScheduler()
    val client = FakeGattClient(scheduler)
    val server = FakeLocalServer { client.calls }
    val pairings = InMemoryPairingRegistry()
    val link = TripperLink(client, server, scheduler, pairings, timing, { LocalTime.of(13, 55) }, { ClockFormat.H12 })

    val state get() = link.currentState
    val gen get() = link.snapshot.value.sessionId
    val snap get() = link.snapshot.value

    fun documentedSnapshot(props: Int = CharacteristicProperty.WRITE_NO_RESPONSE.bit, extra: List<GattServiceInfo> = emptyList()) =
        GattSnapshot(
            ADDR, "RE_DISP",
            listOf(
                GattServiceInfo(
                    TripperTransport.SERVICE_UUID, true,
                    listOf(GattCharacteristicInfo(TripperTransport.CHARACTERISTIC_UUID, CharacteristicProperty.fromBitmask(props))),
                ),
            ) + extra,
            0,
        )

    fun connectToReady(
        authorize: Boolean = true,
        snapshot: GattSnapshot = documentedSnapshot(),
        options: ConnectOptions = ConnectOptions(),
    ) {
        if (authorize) check(link.authorizeTransmission(ACK) == CommandResult.Accepted)
        check(link.connect(ADDR, "RE_DISP", options) == CommandResult.Accepted)
        if (options.serverMode == ServerMode.BEFORE_CONNECT) link.onServerReady(gen, true)
        if (options.requireFreshAdvertisement) link.onAdvertisementSeen(gen, -60, true)
        link.onConnected(gen)
        if (options.serverMode == ServerMode.AFTER_CONNECT) link.onServerReady(gen, true)
        link.onServicesDiscovered(gen, 0, snapshot)
    }

    /** Confirma (status 0) a escrita em andamento, se houver. Devolve true se havia. */
    fun completeWrite(status: Int = 0): Boolean {
        val before = client.writes.size
        if (link.snapshot.value.diagnostics.inFlight == null) return false
        link.onWriteCompleted(gen, status)
        return client.writes.size >= before
    }

    /** Avança o tempo em passos, confirmando cada escrita como o Android faria. */
    fun runFor(ms: Long, step: Long = 50) {
        var left = ms
        while (left > 0) {
            val d = minOf(step, left)
            scheduler.advanceBy(d)
            while (link.snapshot.value.diagnostics.inFlight != null && link.currentState.allowsWrites) link.onWriteCompleted(gen, 0)
            left -= d
        }
    }

    fun pairAndAuthenticate(pin: String = "4821", snapshot: GattSnapshot = documentedSnapshot()) {
        if (link.currentState == LinkState.DISCONNECTED) connectToReady(snapshot = snapshot)
        check(link.startPairing() == CommandResult.Accepted)
        runFor(300)
        check(link.submitPin(pin) == CommandResult.Accepted)
        runFor(100)
        check(link.confirmAuthenticated() == CommandResult.Accepted) { "confirmação recusada: ${snap.log.last().text}" }
    }

    fun writtenKinds(): List<String> = client.writes.map { kindOf(it.bytes) }

    companion object {
        const val ADDR = "00:60:37:AA:BB:CC"
        val ACK = TransmissionAck(true, true, true, true, true)
        val OTA_SERVICE = GattServiceInfo(KnownUuids.NXP_OTAP_SERVICE, true, emptyList())

        fun kindOf(bytes: ByteArray): String {
            val p = (TripperPacket.parse(bytes) as? PacketParseResult.Valid)?.packet ?: return "INVÁLIDO"
            return when (PacketDecoder.decode(p)) {
                DecodedPacket.HandshakeShowPin -> "SHOW_PIN"
                DecodedPacket.HandshakeResume -> "RESUME"
                DecodedPacket.PingFirmware -> "PING"
                is DecodedPacket.Pin -> "PIN"
                is DecodedPacket.SetTime -> "SET_TIME"
                is DecodedPacket.Compass -> "COMPASS"
                else -> "OUTRO"
            }
        }
    }
}
