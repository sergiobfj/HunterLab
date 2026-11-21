package dev.hunterlab.core.link

import dev.hunterlab.core.ble.BleErrorTranslator
import dev.hunterlab.core.ble.UserMessage
import dev.hunterlab.protocol.ClockFormat
import dev.hunterlab.protocol.CompassDirection
import dev.hunterlab.protocol.DayPeriod
import dev.hunterlab.protocol.TripperCommands
import dev.hunterlab.protocol.TripperTransport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalTime
import java.util.UUID

/**
 * Sessão de comunicação com o Tripper — Laboratório BLE da 0.2.0.
 *
 * Garantias (todas cobertas por testes em `core/src/test/.../link`):
 * - Conectar **não escreve nada**. Cada envio de handshake exige um comando manual.
 * - Nenhuma escrita sem [authorizeTransmission] na sessão corrente; a autorização morre com a sessão.
 * - Só comandos documentados ([CommandKind]); não há API para bytes livres.
 * - Uma escrita por vez, liberada pelo callback de conclusão (ou timeout); fila limitada.
 * - Autenticação **nunca** é inferida do sucesso de uma escrita: exige confirmação do usuário.
 * - [interrupt] cancela fila e agendamentos na hora; nada é escrito depois.
 * - Sem reconexão automática. Eventos de sessões anteriores são descartados.
 *
 * Contrato de thread: todos os métodos e eventos na thread do [scheduler].
 */
class TripperLink(
    private val client: GattClientPort,
    private val server: LocalServerPort,
    private val scheduler: LinkScheduler,
    private val pairings: PairingRegistry,
    val timing: LinkTiming = LinkTiming(),
    private val timeOfDay: () -> LocalTime = { LocalTime.now() },
    private val clockFormat: () -> ClockFormat = { ClockFormat.H24 },
) : LinkEvents {

    private var state = LinkState.DISCONNECTED
    private var generation = 0L
    private var session: Session? = null
    private var preAuthorizedAt: Long? = null
    private val queue = WriteQueue(timing.maxQueue)
    private val log = LinkLog()
    private var message: UserMessage? = null
    private var captureInboundBytes = false
    private var captureEverUsed = false
    private var compassTestsRun = 0
    private val observations = mutableListOf<String>()
    private var nextEntryId = 1L
    private val attempts = ArrayDeque<ConnectAttemptSummary>()

    private val _snapshot = MutableStateFlow(LinkSnapshot(pinAttemptsMax = timing.maxPinAttempts))
    val snapshot: StateFlow<LinkSnapshot> = _snapshot.asStateFlow()

    val currentState: LinkState get() = state

    private inner class Session(val generation: Long, val address: String, val name: String?, val startedAt: Long) {
        val tasks = mutableSetOf<Cancellable>()
        var connected = false
        var authorized = false
        var authorizedAt: Long? = null
        var discoveredAt: Long? = null
        var withoutResponse = true
        var profileOk = false
        var server = ServerState.OPENING
        var serverPeer = false
        var authPath: AuthPath? = null
        var handshakeStarted = false
        var authDelivered = false
        var pinAttempts = 0
        var deadline: DeadlineKind? = null
        var deadlineAt: Long? = null
        var deadlineTask: Cancellable? = null
        var lastWriteAt: Long? = null
        var lastWriteLabel: String? = null
        var lastCompletionAt: Long? = null
        var quarantineUntil = 0L
        var writesDelivered = 0
        var writeFailures = 0
        var consecutiveFailures = 0
        var inboundOk = 0
        var inboundRejected = 0
        var compass: CompassRunState? = null
        var completionTask: Cancellable? = null
        var pumpTask: Cancellable? = null

        // Diagnóstico de conexão (tempos relativos a startedAt)
        var options = ConnectOptions()
        var lastAdvertisementAgeMs: Long? = null
        var advertisementSeenAt: Long? = null
        var connectedAt: Long? = null
        var serverReadyAt: Long? = null
        var reachedReady = false
    }

    private class CompassRunState(
        val direction: CompassDirection,
        val period: DayPeriod,
        val startedAt: Long,
        val durationMs: Long,
        var queued: Int = 0,
        var delivered: Int = 0,
        var resendTask: Cancellable? = null,
        var endTask: Cancellable? = null,
    )

    // =========================================================================== comandos do usuário

    /** Libera transmissão real para a sessão atual, ou arma para a próxima conexão (expira). */
    fun authorizeTransmission(ack: TransmissionAck): CommandResult {
        if (!ack.complete) return reject("Confirme todos os itens antes de autorizar.")
        val now = scheduler.now()
        when (state) {
            LinkState.DISCONNECTED -> {
                preAuthorizedAt = now
                info("Transmissão pré-autorizada para a PRÓXIMA conexão (expira em ${timing.preAuthorizationTtlMs / 1000} s). Conectar não envia nada.")
            }
            LinkState.CONNECTING, LinkState.DISCOVERING, LinkState.READY_FOR_AUTH -> {
                val s = session ?: return reject("Sem sessão.")
                s.authorized = true
                s.authorizedAt = now
                info("Transmissão autorizada para a sessão #${s.generation}. Nada é enviado até um comando manual.")
            }
            else -> return reject("A autorização só pode ser dada antes da autenticação.")
        }
        publish()
        return CommandResult.Accepted
    }

    fun cancelPreAuthorization() {
        if (preAuthorizedAt != null) {
            preAuthorizedAt = null
            info("Pré-autorização cancelada.")
            publish()
        }
    }

    /** Liga/desliga o registro dos bytes recebidos (pode conter dados sensíveis; só em memória). */
    fun setInboundCapture(detailed: Boolean) {
        captureInboundBytes = detailed
        if (detailed) captureEverUsed = true
        if (detailed) {
            warn("Captura detalhada LIGADA: bytes recebidos entram no histórico e no relatório e podem conter o PIN (só em memória).")
        } else {
            info("Captura detalhada desligada.")
        }
        publish()
    }

    /**
     * Conecta e verifica o perfil. Não escreve nada.
     * @param lastAdvertisementAgeMs idade do último anúncio visto pela busca da aba Dispositivos (só para diagnóstico).
     */
    fun connect(
        address: String,
        name: String?,
        options: ConnectOptions = ConnectOptions(),
        lastAdvertisementAgeMs: Long? = null,
    ): CommandResult {
        if (state != LinkState.DISCONNECTED) return reject("Já existe uma sessão (${state.label}). Interrompa antes.")
        require(address.isNotBlank()) { "Endereço vazio" }
        message = null
        generation += 1
        val now = scheduler.now()
        val s = Session(generation, address, name, now)
        s.options = options
        s.lastAdvertisementAgeMs = lastAdvertisementAgeMs
        session = s
        val pre = preAuthorizedAt
        preAuthorizedAt = null
        moveTo(LinkState.CONNECTING)
        info("Sessão #${s.generation}: conectando a ${LinkFormat.maskAddress(address)}. Nenhuma escrita sem comando manual.")
        if (pre != null) {
            if (now - pre <= timing.preAuthorizationTtlMs) {
                s.authorized = true
                s.authorizedAt = now
                info("Pré-autorização vinculada a esta sessão.")
            } else {
                warn("Pré-autorização expirada: transmissão segue bloqueada.")
            }
        }
        val freshText = if (options.requireFreshAdvertisement) {
            "obrigatório (até ${options.advertisementWaitMs / 1000} s)"
        } else {
            "NÃO exigido (comportamento da 0.2.0)"
        }
        val ageText = lastAdvertisementAgeMs?.let { "há ${LinkFormat.seconds(it)}" } ?: "desconhecido"
        info("Opções: anúncio recente $freshText; servidor local: ${options.serverMode.label}. Último anúncio visto na busca: $ageText.")
        startTick(s)
        if (options.serverMode == ServerMode.BEFORE_CONNECT) {
            if (!openServer(s)) return reject("Servidor local indisponível.")
        } else {
            s.server = ServerState.CLOSED
        }
        if (!client.connect(address, s.generation, options)) {
            fail(UserMessage("Conexão não iniciada", "O Android recusou abrir a conexão GATT."))
            return reject("Conexão recusada pelo Android.")
        }
        val connectWindow = timing.connectTimeoutMs + if (options.requireFreshAdvertisement) options.advertisementWaitMs else 0
        setDeadline(s, DeadlineKind.CONNECT, connectWindow)
        publish()
        return CommandResult.Accepted
    }

    private fun openServer(s: Session): Boolean {
        s.server = ServerState.OPENING
        info("Abrindo servidor GATT local (${s.options.serverMode.label.lowercase()}).")
        if (!server.open(s.generation)) {
            s.server = ServerState.FAILED
            fail(UserMessage("Servidor GATT local não abriu", "O Android recusou abrir o servidor local usado para as respostas do Tripper."))
            return false
        }
        return true
    }

    /** Envia HANDSHAKE_SHOW_PIN (dispositivo novo: o Tripper mostra um PIN). */
    fun startPairing(): CommandResult {
        val s = session ?: return reject("Sem sessão.")
        if (state != LinkState.READY_FOR_AUTH) return reject("Disponível só em \"${LinkState.READY_FOR_AUTH.label}\".")
        if (!s.authorized) return reject("Transmissão não autorizada nesta sessão.")
        if (s.options.serverMode == ServerMode.DISABLED) return reject(DIAGNOSTIC_ONLY)
        if (s.server != ServerState.READY) return reject("Servidor GATT local ainda não está pronto.")
        s.authPath = AuthPath.PAIRING
        s.handshakeStarted = true
        moveTo(LinkState.WAITING_FOR_PIN)
        if (!enqueue(s, LinkCommand.showPin())) return failAndReject("HANDSHAKE_SHOW_PIN não pôde ser enfileirado.")
        setDeadline(s, DeadlineKind.PIN_ENTRY, timing.pinEntryTimeoutMs)
        info("HANDSHAKE_SHOW_PIN solicitado. Aguarde o PIN no display do Tripper e digite-o.")
        publish()
        return CommandResult.Accepted
    }

    /** Sequência de retomada documentada. Só com evidência de sessão reconhecida (confirmada) nesta execução. */
    fun resume(): CommandResult {
        val s = session ?: return reject("Sem sessão.")
        if (state != LinkState.READY_FOR_AUTH) return reject("Disponível só em \"${LinkState.READY_FOR_AUTH.label}\".")
        if (!s.authorized) return reject("Transmissão não autorizada nesta sessão.")
        if (s.options.serverMode == ServerMode.DISABLED) return reject(DIAGNOSTIC_ONLY)
        if (s.server != ServerState.READY) return reject("Servidor GATT local ainda não está pronto.")
        if (!pairings.isRecognized(s.address)) {
            return reject("Sem sessão reconhecida para este Tripper nesta execução do app. Use o pareamento com PIN.")
        }
        s.authPath = AuthPath.RESUME
        s.handshakeStarted = true
        moveTo(LinkState.AUTHENTICATING)
        if (!enqueue(s, LinkCommand.resume())) return failAndReject("HANDSHAKE_RESUME não pôde ser enfileirado.")
        // Os intervalos da referência contam a partir do envio do RESUME, que pode esperar o atraso pós-descoberta.
        val now = scheduler.now()
        val resumeAt = maxOf(now, (s.discoveredAt ?: now) + timing.postDiscoveryDelayMs)
        later(s, resumeAt - now + timing.resumeToSetTimeMs) {
            if (state == LinkState.AUTHENTICATING) enqueue(s, LinkCommand.setTime(timeOfDay(), clockFormat()))
            later(s, timing.setTimeToPingMs) {
                if (state == LinkState.AUTHENTICATING) {
                    enqueue(s, LinkCommand.handshakePing())
                    enqueue(s, LinkCommand.handshakePing())
                }
                later(s, timing.pingsToConfirmMs) {
                    if (state == LinkState.AUTHENTICATING) info("Sequência de retomada enfileirada. Confirme pelo display do Tripper.")
                }
            }
        }
        setDeadline(s, DeadlineKind.AUTH_CONFIRM, timing.authConfirmTimeoutMs)
        info("HANDSHAKE_RESUME solicitado (sessão reconhecida nesta execução).")
        publish()
        return CommandResult.Accepted
    }

    /** Envia o PIN digitado. O PIN não é guardado nem registrado. Sem novas tentativas automáticas. */
    fun submitPin(code: String): CommandResult {
        val s = session ?: return reject("Sem sessão.")
        if (state != LinkState.WAITING_FOR_PIN) return reject("O PIN só pode ser enviado em \"${LinkState.WAITING_FOR_PIN.label}\".")
        if (!s.authorized) return reject("Transmissão não autorizada nesta sessão.")
        val valid = code.length in 1..TripperCommands.PIN_MAX_LENGTH && code.all { it in '0'..'9' || it in 'A'..'Z' || it in 'a'..'z' }
        if (!valid) return reject("PIN inválido: digite de 1 a ${TripperCommands.PIN_MAX_LENGTH} letras/dígitos, exatamente como no Tripper.")
        if (s.pinAttempts >= timing.maxPinAttempts) return reject("Tentativas de PIN esgotadas nesta sessão.")
        s.pinAttempts += 1
        s.authDelivered = false
        moveTo(LinkState.AUTHENTICATING)
        if (!enqueue(s, LinkCommand.pin(code))) return failAndReject("O PIN não pôde ser enfileirado.")
        setDeadline(s, DeadlineKind.AUTH_CONFIRM, timing.authConfirmTimeoutMs)
        info("PIN enfileirado (tentativa ${s.pinAttempts}/${timing.maxPinAttempts}). Conteúdo não registrado.")
        publish()
        return CommandResult.Accepted
    }

    /** O usuário observou no display que o Tripper aceitou. Única forma de chegar a AUTHENTICATED. */
    fun confirmAuthenticated(): CommandResult {
        val s = session ?: return reject("Sem sessão.")
        if (state != LinkState.AUTHENTICATING) return reject("Nada aguardando confirmação.")
        if (!s.authDelivered) return reject("O Android ainda não concluiu o envio do comando de autenticação.")
        clearDeadline(s)
        moveTo(LinkState.AUTHENTICATED)
        pairings.markRecognized(s.address, scheduler.now())
        info(
            "Autenticação CONFIRMADA PELO USUÁRIO (observação do display). Respostas recebidas pelo servidor local: " +
                "${s.inboundOk} — formato não documentado, não interpretadas.",
        )
        publish()
        return CommandResult.Accepted
    }

    /** O Tripper está pedindo/mostrando PIN (rejeitou, ou a retomada não foi reconhecida). */
    fun reportPinRequired(): CommandResult {
        val s = session ?: return reject("Sem sessão.")
        if (state != LinkState.AUTHENTICATING) return reject("Nada aguardando confirmação.")
        if (s.pinAttempts >= timing.maxPinAttempts) {
            fail(UserMessage("Tentativas de PIN esgotadas", "Foram ${s.pinAttempts} tentativas. Interrompa, revise o procedimento e comece outra sessão."))
            return reject("Tentativas esgotadas.")
        }
        clearDeadline(s)
        moveTo(LinkState.WAITING_FOR_PIN)
        setDeadline(s, DeadlineKind.PIN_ENTRY, timing.pinEntryTimeoutMs)
        info("Usuário informou que o Tripper pede PIN. Nenhum reenvio automático.")
        publish()
        return CommandResult.Accepted
    }

    fun syncClock(): CommandResult {
        val s = session ?: return reject("Sem sessão.")
        if (state != LinkState.AUTHENTICATED) return reject("Disponível só após autenticação confirmada.")
        if (!enqueue(s, LinkCommand.setTime(timeOfDay(), clockFormat()))) return reject("Não foi possível enfileirar SET_TIME.")
        publish()
        return CommandResult.Accepted
    }

    /** Primeiro experimento de tela: bússola com direção fixa, reenviada a cada ~2 s, com duração limitada. */
    fun startCompassTest(direction: CompassDirection, period: DayPeriod, durationMs: Long = timing.compassDefaultMs): CommandResult {
        val s = session ?: return reject("Sem sessão.")
        if (state != LinkState.AUTHENTICATED) return reject("O teste de bússola exige autenticação confirmada.")
        if (durationMs !in timing.compassMinMs..timing.compassMaxMs) {
            return reject("Duração deve ficar entre ${timing.compassMinMs / 1000} e ${timing.compassMaxMs / 1000} s.")
        }
        val run = CompassRunState(direction, period, scheduler.now(), durationMs)
        s.compass = run
        compassTestsRun += 1
        moveTo(LinkState.DISPLAY_ACTIVE)
        info("Teste de bússola iniciado: ${direction.label}, ${period.label.lowercase()}, até ${durationMs / 1000} s.")
        sendCompassFrame(s, run)
        run.endTask = later(s, durationMs) { stopCompassTest("duração limite atingida") }
        publish()
        return CommandResult.Accepted
    }

    fun stopCompassTest(reason: String = "parado pelo usuário"): CommandResult {
        val s = session ?: return reject("Sem sessão.")
        if (state != LinkState.DISPLAY_ACTIVE) return reject("Nenhum teste de tela ativo.")
        endCompass(s)
        moveTo(LinkState.AUTHENTICATED)
        info(
            "Teste de bússola encerrado ($reason). Sem novas telas: pela referência, o Tripper encerra o link ~5 s " +
                "após a última tela (PING não renova). Registre o que o display mostrou.",
        )
        publish()
        return CommandResult.Accepted
    }

    fun recordObservation(observation: CompassObservation) = addObservation("Durante o teste: ${observation.label}")
    fun recordObservation(observation: AfterTestObservation) = addObservation("Após o teste: ${observation.label}")

    private fun addObservation(text: String): CommandResult {
        if (compassTestsRun == 0) return reject("Nenhum teste de tela foi executado.")
        observations += "Teste #$compassTestsRun — $text"
        info("Observação registrada: $text")
        publish()
        return CommandResult.Accepted
    }

    /**
     * Interromper: cancela fila, agendamentos e autorização **imediatamente** — nada mais é escrito.
     * Depois aguarda o Tripper encerrar o link sozinho (forma documentada de voltar ao relógio);
     * se não encerrar em [LinkTiming.stopGraceMs], o app desconecta e libera os recursos.
     */
    fun interrupt(reason: String = "pedido do usuário"): CommandResult {
        preAuthorizedAt = null
        val s = session
        if (s == null || state == LinkState.DISCONNECTED) {
            publish()
            return CommandResult.Accepted
        }
        if (state == LinkState.STOPPING) return CommandResult.Accepted
        val dropped = haltTransmissions(s)
        moveTo(LinkState.STOPPING)
        warn("INTERROMPER ($reason): fila ($dropped item(ns)) e agendamentos cancelados; nenhuma escrita a partir de agora.")
        if (!s.connected) {
            finalize(s, status = null, note = "Sessão encerrada antes de conectar.")
            return CommandResult.Accepted
        }
        info("Aguardando o Tripper encerrar o link (até ${timing.stopGraceMs / 1000} s). Encerrar a sessão não garante a volta imediata do relógio.")
        setDeadline(s, DeadlineKind.STOP_GRACE, timing.stopGraceMs)
        publish()
        return CommandResult.Accepted
    }

    /** Em "interrompendo"/"erro": desconecta pelo celular agora (pode exigir ciclar a ignição para o Pod voltar a anunciar). */
    fun disconnectNow(): CommandResult {
        val s = session ?: return reject("Sem sessão.")
        if (state != LinkState.STOPPING && state != LinkState.ERROR) return reject("Use Interromper primeiro.")
        forceDisconnect(s)
        return CommandResult.Accepted
    }

    // =========================================================================== eventos do Android

    override fun onConnected(generation: Long) {
        val s = current(generation) ?: return
        if (state != LinkState.CONNECTING) return ignored("conexão fora de ordem")
        s.connected = true
        val connectedAt = scheduler.now()
        s.connectedAt = connectedAt
        clearDeadline(s)
        moveTo(LinkState.DISCOVERING)
        info("Conectado após ${LinkFormat.seconds(connectedAt - s.startedAt)}. Descobrindo serviços (somente leitura).")
        if (s.options.serverMode == ServerMode.AFTER_CONNECT && !openServer(s)) return
        setDeadline(s, DeadlineKind.DISCOVERY, timing.discoveryTimeoutMs)
        if (!client.discoverServices(s.generation)) {
            fail(UserMessage("Descoberta não iniciada", "O Android recusou a descoberta de serviços."))
        }
        publish()
    }

    override fun onServicesDiscovered(generation: Long, status: Int, snapshot: dev.hunterlab.core.ble.GattSnapshot?) {
        val s = current(generation) ?: return
        if (state != LinkState.DISCOVERING) return ignored("descoberta fora de ordem")
        clearDeadline(s)
        if (status != 0 || snapshot == null) {
            fail(BleErrorTranslator.discoveryFailed(status))
            return
        }
        val v = LinkProfile.verify(snapshot)
        v.notes.forEach { warn(it) }
        if (!v.ok) {
            fail(UserMessage("Perfil incompatível — escrita bloqueada", v.reason))
            return
        }
        if (s.server == ServerState.FAILED) {
            fail(UserMessage("Servidor GATT local indisponível", "Sem ele, as respostas do Tripper não seriam recebidas."))
            return
        }
        s.profileOk = true
        s.reachedReady = true
        s.withoutResponse = v.withoutResponse
        s.discoveredAt = scheduler.now()
        moveTo(LinkState.READY_FOR_AUTH)
        info("${v.reason} Nenhuma escrita enviada.")
        if (s.options.serverMode == ServerMode.DISABLED) {
            info("Modo diagnóstico sem servidor local: o handshake fica indisponível nesta sessão.")
        } else if (!s.authorized) {
            info("Transmissão bloqueada: autorize para habilitar o handshake.")
        }
        publish()
    }

    override fun onWriteCompleted(generation: Long, status: Int) {
        val s = current(generation) ?: return
        val entry = queue.inFlight
        if (entry == null || entry.session != s.generation) return ignored("conclusão de escrita sem envio pendente")
        s.completionTask?.let { it.cancel(); s.tasks.remove(it) }
        s.completionTask = null
        queue.completeInFlight()
        s.lastCompletionAt = scheduler.now()
        if (status == 0) {
            s.writesDelivered += 1
            s.consecutiveFailures = 0
            val kind = entry.command.kind
            if (kind == CommandKind.PIN || kind == CommandKind.RESUME) s.authDelivered = true
            if (kind == CommandKind.COMPASS) s.compass?.let { it.delivered += 1 }
            if (kind != CommandKind.PING) info("${kind.label}: entregue ao controlador Bluetooth (não é confirmação do Tripper).")
        } else {
            writeFailed(s, entry, "status $status")
            if (session !== s) return
        }
        pump(s)
        publish()
    }

    override fun onDisconnected(generation: Long, status: Int) {
        val s = current(generation) ?: return
        finalize(s, status, note = null)
    }

    override fun onServerReady(generation: Long, ok: Boolean) {
        val s = current(generation) ?: return
        s.server = if (ok) ServerState.READY else ServerState.FAILED
        if (ok) s.serverReadyAt = scheduler.now()
        if (ok) info("Servidor GATT local pronto após ${LinkFormat.seconds(scheduler.now() - s.startedAt)} (serviço 01FF0100-…, sem anúncio).")
        else fail(UserMessage("Servidor GATT local falhou", "O serviço local não pôde ser registrado."))
        publish()
    }

    override fun onServerPeer(generation: Long, address: String, connected: Boolean) {
        val s = current(generation) ?: return
        val same = address.equals(s.address, ignoreCase = true)
        if (same) s.serverPeer = connected
        info("Servidor local: ${LinkFormat.maskAddress(address)} ${if (connected) "conectou" else "desconectou"}${if (same) "" else " (não é o Tripper da sessão)"}.")
        publish()
    }

    override fun onInbound(generation: Long, from: String, characteristic: UUID, value: ByteArray) {
        val s = current(generation) ?: return
        val problems = buildList {
            if (!from.equals(s.address, ignoreCase = true)) add("remetente inesperado ${LinkFormat.maskAddress(from)}")
            if (characteristic != TripperTransport.CHARACTERISTIC_UUID) add("característica inesperada ${InboundClassifier.characteristicName(characteristic)}")
            if (value.isEmpty() || value.size > InboundClassifier.MAX_INBOUND_BYTES) add("tamanho inválido (${value.size})")
        }
        if (problems.isNotEmpty()) {
            s.inboundRejected += 1
            warn("RX rejeitado: ${problems.joinToString("; ")}.")
        } else {
            s.inboundOk += 1
            log(LogLevel.RX, InboundClassifier.describe(value, captureInboundBytes) + " · não interpretado")
        }
        publish()
    }

    override fun onAdvertisementSeen(generation: Long, rssi: Int, connectable: Boolean?) {
        val s = current(generation) ?: return
        if (state != LinkState.CONNECTING) return ignored("anúncio fora da fase de conexão")
        val seenAt = scheduler.now()
        s.advertisementSeenAt = seenAt
        val conn = when (connectable) {
            true -> "sim"
            false -> "NÃO"
            null -> "?"
        }
        info("Anúncio recente recebido após ${LinkFormat.seconds(seenAt - s.startedAt)} (RSSI $rssi dBm, conectável: $conn). Chamando connectGatt.")
        publish()
    }

    override fun onConnectFailed(generation: Long, reason: String) {
        current(generation) ?: return
        if (state != LinkState.CONNECTING) return ignored("falha de conexão fora de ordem")
        fail(UserMessage("Conexão não iniciada", LinkFormat.maskMacs(reason)))
    }

    override fun onDiagnostic(generation: Long, text: String) {
        val s = current(generation) ?: return
        log(LogLevel.INFO, "diag: " + LinkFormat.maskMacs(text))
        if (session === s) publish()
    }

    // =========================================================================== fila e escrita

    private fun enqueue(s: Session, command: LinkCommand): Boolean {
        if (session !== s) return false
        if (!s.authorized || !state.allowsWrites || !s.connected || !s.profileOk) {
            warn("Bloqueado: ${command.kind.label} fora de estado/autorização (${state.label}).")
            return false
        }
        val maxAge = if (command.kind.handshake) timing.maxPendingAgeMs * 2 else timing.maxPendingAgeMs
        val entry = WriteQueue.Entry(nextEntryId++, s.generation, command, scheduler.now(), maxAge)
        when (val r = queue.offer(entry)) {
            WriteQueue.Offer.Accepted -> Unit
            is WriteQueue.Offer.Replaced -> info("${command.kind.label}: pendente anterior descartado (obsoleto).")
            WriteQueue.Offer.Duplicate -> {
                if (command.policy != DedupePolicy.DROP_NEW) warn("${command.kind.label} duplicado ignorado.")
                return command.policy == DedupePolicy.DROP_NEW
            }
            WriteQueue.Offer.Full -> {
                warn("Fila cheia (${timing.maxQueue}): ${command.kind.label} descartado.")
                return false
            }
        }
        pump(s)
        return true
    }

    private fun pump(s: Session) {
        if (session !== s || queue.inFlight != null || queue.isEmpty() || s.pumpTask != null) return
        if (!s.authorized || !state.allowsWrites || !s.connected) return
        val now = scheduler.now()
        val earliest = maxOf(
            (s.discoveredAt ?: now) + timing.postDiscoveryDelayMs,
            (s.lastCompletionAt ?: Long.MIN_VALUE / 2) + timing.minWriteGapMs,
            s.quarantineUntil,
        )
        if (now < earliest) {
            s.pumpTask = later(s, earliest - now) {
                s.pumpTask = null
                pump(s)
            }
            return
        }
        val (entry, stale) = queue.poll(now)
        stale.forEach { warn("${it.command.kind.label} descartado por estar obsoleto (${now - it.enqueuedAtMs} ms na fila).") }
        if (entry == null) return
        if (entry.session != s.generation || !CommandWhitelist.matches(entry.command)) {
            fail(UserMessage("Pacote bloqueado", "Um pacote não passou na lista de comandos documentados. Nada foi enviado."))
            return
        }
        queue.markInFlight(entry)
        s.lastWriteAt = now
        s.lastWriteLabel = entry.command.kind.label
        log(LogLevel.TX, entry.command.logLabel)
        when (val r = client.writePacket(s.generation, entry.command.packet.toByteArray(), s.withoutResponse)) {
            WriteStart.Started -> s.completionTask = later(s, timing.writeCompletionTimeoutMs) {
                s.completionTask = null
                onWriteTimeout(s)
            }
            is WriteStart.Refused -> {
                queue.completeInFlight()
                writeFailed(s, entry, "recusada pelo Android (código ${r.code})")
                if (session === s) pump(s)
            }
        }
        publish()
    }

    private fun onWriteTimeout(s: Session) {
        val entry = queue.completeInFlight() ?: return
        // Um callback muito atrasado poderia ser atribuído à próxima escrita: pausa a fila por um intervalo.
        s.quarantineUntil = scheduler.now() + timing.writeCompletionTimeoutMs
        writeFailed(s, entry, "sem conclusão do Android em ${timing.writeCompletionTimeoutMs} ms")
        if (session === s) pump(s)
        publish()
    }

    private fun writeFailed(s: Session, entry: WriteQueue.Entry, reason: String) {
        s.writeFailures += 1
        s.consecutiveFailures += 1
        error("Falha de escrita: ${entry.command.kind.label} — $reason.")
        if (entry.command.kind.handshake) {
            fail(UserMessage("Handshake não enviado", "${entry.command.kind.label}: $reason. Nenhuma nova tentativa automática."))
        } else if (s.consecutiveFailures >= timing.maxConsecutiveWriteFailures) {
            fail(UserMessage("Falhas de escrita consecutivas", "${s.consecutiveFailures} falhas seguidas. Transmissão interrompida."))
        }
    }

    // =========================================================================== keepalive, bússola

    private fun keepaliveActive(s: Session) = s.authorized && s.handshakeStarted && s.connected && state.allowsWrites

    private fun startTick(s: Session) {
        later(s, timing.keepaliveCheckMs) {
            onTick(s)
            if (session === s) startTick(s)
        }
    }

    private fun onTick(s: Session) {
        val now = scheduler.now()
        val authorizedAt = s.authorizedAt
        if (authorizedAt != null && s.authorized && now - authorizedAt >= timing.sessionMaxMs) {
            interrupt("limite de ${timing.sessionMaxMs / 60_000} min de sessão autorizada")
            return
        }
        val last = s.lastWriteAt
        // Durante o teste de tela o reenvio da bússola (a cada ~2 s) já mantém o link.
        if (keepaliveActive(s) && s.compass == null && last != null && queue.inFlight == null && now - last >= timing.keepaliveIdleMs) {
            enqueue(s, LinkCommand.keepalivePing())
        }
        publish()
    }

    private fun sendCompassFrame(s: Session, run: CompassRunState) {
        if (session !== s || state != LinkState.DISPLAY_ACTIVE || s.compass !== run) return
        if (scheduler.now() - run.startedAt >= run.durationMs) return
        if (enqueue(s, LinkCommand.compass(run.direction, run.period))) run.queued += 1
        run.resendTask = later(s, timing.screenResendMs) { sendCompassFrame(s, run) }
    }

    private fun endCompass(s: Session) {
        val run = s.compass ?: return
        run.resendTask?.cancel()
        run.endTask?.cancel()
        val removed = queue.removeKind(CommandKind.COMPASS)
        if (removed > 0) info("$removed quadro(s) de bússola pendente(s) descartado(s).")
        s.compass = null
        lastCompass = CompassRun(run.direction, run.period, run.durationMs, scheduler.now() - run.startedAt, run.queued, run.delivered)
    }

    private var lastCompass: CompassRun? = null

    // =========================================================================== falha, interrupção, limpeza

    /** Para toda transmissão da sessão (sem fechar o link). Devolve quantos itens foram descartados. */
    private fun haltTransmissions(s: Session): Int {
        s.authorized = false
        endCompass(s)
        s.pumpTask?.cancel()
        s.pumpTask = null
        s.completionTask?.cancel()
        s.completionTask = null
        clearDeadline(s)
        return queue.clear()
    }

    private fun fail(msg: UserMessage) {
        val s = session ?: return
        if (state == LinkState.STOPPING || state == LinkState.DISCONNECTED || state == LinkState.ERROR) {
            if (state == LinkState.ERROR) error("${msg.title}: ${msg.detail}")
            return
        }
        message = msg
        error("${msg.title}: ${msg.detail}")
        haltTransmissions(s)
        moveTo(LinkState.ERROR)
        if (!s.connected) {
            finalize(s, status = null, note = "Recursos liberados após a falha.")
            return
        }
        setDeadline(s, DeadlineKind.STOP_GRACE, timing.stopGraceMs)
        publish()
    }

    private fun forceDisconnect(s: Session) {
        clearDeadline(s)
        warn("Desconectando pelo celular. Na Meteor 350, o Tripper só voltou a anunciar após ciclar a ignição nesse caso.")
        client.disconnect(s.generation)
        later(s, timing.forceCloseMs) { finalize(s, status = null, note = "Fechado pelo app após a desconexão.") }
        publish()
    }

    private fun finalize(s: Session, status: Int?, note: String?) {
        if (session !== s) return
        val previous = state
        haltTransmissions(s)
        s.tasks.toList().forEach { it.cancel() }
        s.tasks.clear()
        client.close(s.generation)
        server.close()
        session = null
        if (status != null && previous != LinkState.ERROR) {
            message = BleErrorTranslator.connectionClosed(status, initiatedByApp = false)
        }
        val outcome = when {
            s.reachedReady -> "Chegou a \"${LinkState.READY_FOR_AUTH.label}\""
            previous == LinkState.STOPPING -> "Interrompida antes de ficar pronta"
            else -> message?.title ?: "Encerrada"
        }
        attempts.addLast(summarize(s, status, scheduler.now() - s.startedAt, outcome))
        while (attempts.size > MAX_ATTEMPTS) attempts.removeFirst()
        info(
            buildString {
                append("Sessão #${s.generation} encerrada")
                if (status != null) append(" (status $status)")
                append(". Cliente GATT e servidor local fechados; fila vazia; sem reconexão automática.")
                note?.let { append(" $it") }
            },
        )
        moveTo(LinkState.DISCONNECTED)
        publish()
    }

    private fun summarize(s: Session, status: Int?, endedAfter: Long?, outcome: String) = ConnectAttemptSummary(
        sessionId = s.generation,
        freshAdvertisementRequired = s.options.requireFreshAdvertisement,
        serverMode = s.options.serverMode,
        lastAdvertisementAgeMs = s.lastAdvertisementAgeMs,
        advertisementSeenAfterMs = s.advertisementSeenAt?.let { it - s.startedAt },
        connectedAfterMs = s.connectedAt?.let { it - s.startedAt },
        serverReadyAfterMs = s.serverReadyAt?.let { it - s.startedAt },
        discoveredAfterMs = s.discoveredAt?.let { it - s.startedAt },
        endStatus = status,
        endedAfterMs = endedAfter,
        outcome = outcome,
    )

    // =========================================================================== utilitários

    private fun current(generation: Long): Session? {
        val s = session
        if (s == null || s.generation != generation) {
            if (generation != this.generation || s == null) log(LogLevel.INFO, "Evento de sessão anterior (#$generation) ignorado.")
            return null
        }
        return s
    }

    private fun moveTo(next: LinkState) {
        check(LinkTransitions.allowed(state, next)) { "Transição inválida: $state → $next" }
        state = next
    }

    private fun setDeadline(s: Session, kind: DeadlineKind, delayMs: Long) {
        clearDeadline(s)
        s.deadline = kind
        s.deadlineAt = scheduler.now() + delayMs
        s.deadlineTask = later(s, delayMs) {
            s.deadlineTask = null
            onDeadline(s, kind)
        }
    }

    private fun clearDeadline(s: Session) {
        s.deadlineTask?.cancel()
        s.deadlineTask?.let { s.tasks.remove(it) }
        s.deadlineTask = null
        s.deadline = null
        s.deadlineAt = null
    }

    private fun onDeadline(s: Session, kind: DeadlineKind) {
        s.deadline = null
        s.deadlineAt = null
        when (kind) {
            DeadlineKind.CONNECT -> fail(BleErrorTranslator.connectionTimeout(timing.connectTimeoutMs))
            DeadlineKind.DISCOVERY -> fail(UserMessage("Descoberta não concluída", "Sem resposta em ${timing.discoveryTimeoutMs / 1000} s."))
            DeadlineKind.PIN_ENTRY -> fail(UserMessage("PIN não informado a tempo", "Limite de ${timing.pinEntryTimeoutMs / 1000} s. Nenhum PIN foi tentado automaticamente."))
            DeadlineKind.AUTH_CONFIRM -> fail(UserMessage("Autenticação não confirmada", "Sem confirmação em ${timing.authConfirmTimeoutMs / 1000} s. O app não presume autenticação."))
            DeadlineKind.STOP_GRACE -> forceDisconnect(s)
        }
    }

    private fun later(s: Session, delayMs: Long, action: () -> Unit): Cancellable {
        val holder = arrayOfNulls<Cancellable>(1)
        val c = scheduler.schedule(delayMs) {
            holder[0]?.let { s.tasks.remove(it) }
            if (session === s) action()
        }
        holder[0] = c
        s.tasks += c
        return c
    }

    private fun ignored(what: String) = log(LogLevel.INFO, "Evento ignorado: $what.")

    private fun failAndReject(reason: String): CommandResult {
        fail(UserMessage("Handshake não enfileirado", reason))
        return CommandResult.Rejected(reason)
    }

    private fun reject(reason: String): CommandResult {
        warn("Comando recusado: $reason")
        publish()
        return CommandResult.Rejected(reason)
    }

    private fun info(text: String) = log(LogLevel.INFO, text)
    private fun warn(text: String) = log(LogLevel.WARN, text)
    private fun error(text: String) = log(LogLevel.ERROR, text)

    private fun log(level: LogLevel, text: String) {
        val s = session
        log.add(s?.generation ?: generation, s?.let { scheduler.now() - it.startedAt }, level, text)
    }

    private fun publish() {
        val s = session
        val now = scheduler.now()
        val pre = preAuthorizedAt?.let { timing.preAuthorizationTtlMs - (now - it) }?.takeIf { it > 0 }
        _snapshot.value = LinkSnapshot(
            state = state,
            sessionId = s?.generation ?: generation,
            deviceName = s?.name,
            maskedAddress = LinkFormat.maskAddress(s?.address),
            transmissionAuthorized = s?.authorized == true,
            preAuthorizedRemainingMs = if (s == null) pre else null,
            authPath = s?.authPath,
            pinAttemptsUsed = s?.pinAttempts ?: 0,
            pinAttemptsMax = timing.maxPinAttempts,
            canResume = s != null && pairings.isRecognized(s.address),
            authCommandDelivered = s?.authDelivered == true,
            deadline = s?.deadline,
            deadlineRemainingMs = s?.deadlineAt?.let { (it - now).coerceAtLeast(0) },
            compass = s?.compass?.let { CompassRun(it.direction, it.period, it.durationMs, now - it.startedAt, it.queued, it.delivered) }
                ?: lastCompass,
            compassTestsRun = compassTestsRun,
            observations = observations.toList(),
            diagnostics = if (s == null) {
                LinkDiagnostics(inboundCaptureDetailed = captureInboundBytes, inboundCaptureEverUsed = captureEverUsed)
            } else {
                LinkDiagnostics(
                    profileVerified = s.profileOk,
                    writeMode = if (!s.profileOk) null else if (s.withoutResponse) "WRITE_NO_RESPONSE" else "WRITE",
                    server = s.server,
                    serverPeerConnected = s.serverPeer,
                    keepaliveActive = keepaliveActive(s),
                    queueSize = queue.size,
                    inFlight = queue.inFlight?.command?.kind?.label,
                    writesDelivered = s.writesDelivered,
                    writeFailures = s.writeFailures,
                    lastWrite = s.lastWriteLabel,
                    lastWriteAgoMs = s.lastWriteAt?.let { now - it },
                    inboundAccepted = s.inboundOk,
                    inboundRejected = s.inboundRejected,
                    inboundCaptureDetailed = captureInboundBytes,
                    inboundCaptureEverUsed = captureEverUsed,
                )
            },
            message = message,
            log = log.snapshot(),
            connectOptions = s?.options,
            attempts = attempts.toList() + listOfNotNull(s?.let { summarize(it, null, null, "Em andamento (${state.label})") }),
        )
    }

    private companion object {
        const val MAX_ATTEMPTS = 10
        const val DIAGNOSTIC_ONLY = "Servidor local desligado (modo diagnóstico): handshake indisponível."
    }

    /** Relatório em texto (sem PIN, endereço mascarado) para documentar o teste físico. */
    fun report(appVersion: String, timestamp: String): String = LinkReport.format(snapshot.value, appVersion, timestamp)
}
