package dev.hunterlab.core.playground

import dev.hunterlab.core.ble.ScannedDevice
import dev.hunterlab.core.ble.TripperCandidateClassifier
import dev.hunterlab.core.ble.CandidateLevel
import dev.hunterlab.core.link.*
import dev.hunterlab.protocol.DayPeriod
import dev.hunterlab.protocol.TripperTransport

/** Controla somente testes individuais de bússola, pelo link existente. Tudo na thread do LinkScheduler. */
class PlaygroundController(
    private val link: TripperLink,
    private val scheduler: LinkScheduler,
    private val navigationActive: () -> Boolean,
    private val epochMs: () -> Long,
    private val record: (PlaygroundExperiment) -> Unit,
) {
    private var acknowledgement: TransmissionAck? = null
    private var armedAt = 0L
    private var timer: Cancellable? = null
    private var generation = 0L
    private data class Run(val id: String, val period: DayPeriod, val started: Long, val at: Long, val session: Long, val writes: Int, val responses: Int)
    private var run: Run? = null
    val running: Boolean get() = run != null
    val enabled: Boolean get() = acknowledgement != null && scheduler.now() - armedAt < ARM_TTL_MS && !navigationActive() && !link.navigationHasPriority && (!link.snapshot.value.active || link.snapshot.value.purpose == SessionPurpose.PLAYGROUND)

    fun arm(ack: TransmissionAck): CommandResult {
        if (!ack.complete) return rejected("Confirme todas as condições, incluindo moto parada.")
        if (navigationActive() || link.navigationHasPriority) return rejected("Encerre a navegação antes do Playground físico.")
        if (link.snapshot.value.active) return rejected("Outra sessão BLE está em uso. Encerre-a antes.")
        acknowledgement = ack; armedAt = scheduler.now()
        return CommandResult.Accepted
    }

    /** Apenas um RE_DISP visto na busca; nenhum endereço é persistido. Perfil GATT ainda será verificado pelo link. */
    fun connect(candidates: Collection<ScannedDevice>, nowEpochMs: Long): CommandResult {
        if (!enabled) return rejected("Ative o teste físico e confirme as condições novamente.")
        if (link.snapshot.value.active) return rejected("O Bluetooth já está ocupado.")
        val targets = strongCandidates(candidates)
        if (targets.size != 1) return rejected("Identidade ambígua ou Tripper não encontrado. Mantenha só um Tripper próximo.")
        val target = targets.single()
        if (target.name != TripperTransport.DEVICE_NAME || target.address.isBlank()) return rejected("Identidade insuficiente para o teste físico.")
        val age = nowEpochMs - target.lastSeenMs
        if (target.connectable == false || age !in 0..5_000) return rejected("Anúncio indisponível ou antigo; busque novamente.")
        val auth = link.authorizeTransmission(requireNotNull(acknowledgement))
        if (auth is CommandResult.Rejected) return auth
        return link.connect(target.address, target.name, ConnectOptions(purpose = SessionPurpose.PLAYGROUND), age)
    }

    private fun owned(action: () -> CommandResult): CommandResult {
        if (!enabled || link.navigationHasPriority) return rejected("Modo físico bloqueado; confirme as condições novamente.")
        if (link.snapshot.value.purpose != SessionPurpose.PLAYGROUND || !link.snapshot.value.active) return rejected("Sem sessão do Playground.")
        return action()
    }
    fun requestPin(): CommandResult = owned { link.startPairing() }
    fun submitPin(value: String): CommandResult = owned { link.submitPin(value) }
    fun confirmPin(): CommandResult = owned { link.confirmAuthenticated() }
    fun rejectPin(): CommandResult = owned { link.reportPinRequired() }

    fun show(id: String, period: DayPeriod, durationMs: Long): CommandResult = owned {
        if (running) return@owned rejected("Interrompa o experimento atual primeiro.")
        val symbol = PlaygroundCatalog.find(id)
        val direction = symbol?.direction ?: return@owned rejected("Nesta versão, somente bússolas documentadas podem ser transmitidas.")
        if (durationMs !in MIN_TEST_MS..MAX_TEST_MS || link.timing.screenResendMs != 2_000L) return@owned rejected("Teste de 5 a 15 segundos, com cadência de 2 segundos.")
        val s = link.snapshot.value
        if (s.state != LinkState.AUTHENTICATED || !s.transmissionAuthorized) return@owned rejected("Exige autorização e confirmação visual da autenticação.")
        val next = Run(id, period, scheduler.now(), epochMs(), s.sessionId, s.diagnostics.writesDelivered, s.diagnostics.inboundAccepted)
        val result = link.startCompassTest(direction, period, durationMs)
        if (result is CommandResult.Accepted) {
            run = next
            val gen = ++generation
            timer = scheduler.schedule(durationMs) { if (generation == gen) finish(ExperimentEnd.DURATION) }
        }
        result
    }

    fun onLinkChanged(snapshot: LinkSnapshot) {
        if (acknowledgement != null && snapshot.active && snapshot.purpose != SessionPurpose.PLAYGROUND) { acknowledgement = null }
        if (navigationActive() || link.navigationHasPriority) { stop(ExperimentEnd.NAVIGATION_PRIORITY); return }
        if (acknowledgement != null && !enabled && snapshot.active && snapshot.purpose == SessionPurpose.PLAYGROUND) { stop(ExperimentEnd.ERROR); return }
        val current = run ?: return
        if (navigationActive() || link.navigationHasPriority) { finish(ExperimentEnd.NAVIGATION_PRIORITY); return }
        if (snapshot.sessionId != current.session || snapshot.purpose != SessionPurpose.PLAYGROUND) { finish(ExperimentEnd.DISCONNECTED); return }
        when (snapshot.state) {
            LinkState.AUTHENTICATED -> finish(ExperimentEnd.DURATION)
            LinkState.DISCONNECTED, LinkState.STOPPING -> finish(ExperimentEnd.DISCONNECTED)
            LinkState.ERROR -> finish(ExperimentEnd.ERROR)
            else -> Unit
        }
    }

    fun stop(reason: ExperimentEnd = ExperimentEnd.USER_STOP) {
        if (running) finish(reason) else if (link.snapshot.value.active && link.snapshot.value.purpose == SessionPurpose.PLAYGROUND) link.interrupt("Playground: ${reason.label}")
        acknowledgement = null
        timer?.cancel(); timer = null; generation++
    }

    private fun finish(reason: ExperimentEnd) {
        val ended = run ?: return
        run = null; timer?.cancel(); timer = null; generation++
        val s = link.snapshot.value
        val experiment = PlaygroundExperiment(
            ended.at, listOf(ended.id), ExperimentKind.PHYSICAL, ended.period,
            (scheduler.now() - ended.started).coerceIn(0, MAX_TEST_MS),
            (s.diagnostics.writesDelivered - ended.writes).coerceAtLeast(0),
            (s.diagnostics.inboundAccepted - ended.responses).coerceAtLeast(0), reason,
        )
        acknowledgement = null
        // Segurança não pode depender de sucesso da persistência ou do consumidor do relatório.
        if (s.active && s.purpose == SessionPurpose.PLAYGROUND && s.sessionId == ended.session) link.interrupt("Playground: ${reason.label}")
        record(experiment)
    }
    private fun rejected(reason: String) = CommandResult.Rejected(reason)
    companion object {
        fun strongCandidates(devices: Collection<ScannedDevice>): List<ScannedDevice> = devices.filter { TripperCandidateClassifier.classify(it).level == CandidateLevel.STRONG }.distinctBy { it.address }
        const val MIN_TEST_MS = 5_000L
        const val MAX_TEST_MS = 15_000L
        const val ARM_TTL_MS = 120_000L
    }
}
