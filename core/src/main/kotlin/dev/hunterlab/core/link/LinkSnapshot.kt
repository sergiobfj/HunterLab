package dev.hunterlab.core.link

import dev.hunterlab.core.ble.UserMessage
import dev.hunterlab.protocol.CompassDirection
import dev.hunterlab.protocol.DayPeriod

enum class AuthPath(val label: String) {
    PAIRING("Pareamento com PIN"),
    RESUME("Retomada (HANDSHAKE_RESUME)"),
}

enum class DeadlineKind(val label: String) {
    CONNECT("conectar"),
    DISCOVERY("descobrir os serviços"),
    PIN_ENTRY("digitar o PIN"),
    AUTH_CONFIRM("confirmar a autenticação"),
    STOP_GRACE("o Tripper encerrar o link"),
}

enum class ServerState(val label: String) {
    CLOSED("fechado"),
    OPENING("abrindo"),
    READY("pronto"),
    FAILED("falhou"),
}

data class CompassRun(
    val direction: CompassDirection,
    val period: DayPeriod,
    val durationMs: Long,
    val elapsedMs: Long,
    val framesQueued: Int,
    val framesDelivered: Int,
)

enum class CompassObservation(val label: String) {
    SHOWN("A bússola apareceu no Tripper"),
    NOTHING("Nada mudou no display"),
    OTHER("Apareceu outra tela"),
}

enum class AfterTestObservation(val label: String) {
    CLOCK_RETURNED("Voltou ao relógio"),
    STUCK("Ficou na última tela"),
    LINK_DROPPED("A conexão caiu"),
    NOT_OBSERVED("Não observei"),
}

data class LinkDiagnostics(
    val profileVerified: Boolean = false,
    val writeMode: String? = null,
    val server: ServerState = ServerState.CLOSED,
    val serverPeerConnected: Boolean = false,
    val keepaliveActive: Boolean = false,
    val queueSize: Int = 0,
    val inFlight: String? = null,
    val writesDelivered: Int = 0,
    val writeFailures: Int = 0,
    val lastWrite: String? = null,
    val lastWriteAgoMs: Long? = null,
    val inboundAccepted: Int = 0,
    val inboundRejected: Int = 0,
    val inboundCaptureDetailed: Boolean = false,
    /** Verdadeiro se a captura detalhada foi ligada em algum momento desde que o app abriu. */
    val inboundCaptureEverUsed: Boolean = false,
)

/** Resumo de uma tentativa de conexão, para comparar variantes no mesmo aparelho. Tempos a partir do toque em Conectar. */
data class ConnectAttemptSummary(
    val sessionId: Long,
    val freshAdvertisementRequired: Boolean,
    val serverMode: ServerMode,
    val lastAdvertisementAgeMs: Long?,
    val advertisementSeenAfterMs: Long?,
    val connectedAfterMs: Long?,
    val serverReadyAfterMs: Long?,
    val discoveredAfterMs: Long?,
    val endStatus: Int?,
    val endedAfterMs: Long?,
    val outcome: String,
)

data class LinkSnapshot(
    val state: LinkState = LinkState.DISCONNECTED,
    val sessionId: Long = 0,
    val deviceName: String? = null,
    val maskedAddress: String? = null,
    val transmissionAuthorized: Boolean = false,
    val preAuthorizedRemainingMs: Long? = null,
    val authPath: AuthPath? = null,
    val pinAttemptsUsed: Int = 0,
    val pinAttemptsMax: Int = 3,
    val canResume: Boolean = false,
    val authCommandDelivered: Boolean = false,
    val deadline: DeadlineKind? = null,
    val deadlineRemainingMs: Long? = null,
    val compass: CompassRun? = null,
    val compassTestsRun: Int = 0,
    val observations: List<String> = emptyList(),
    val diagnostics: LinkDiagnostics = LinkDiagnostics(),
    val message: UserMessage? = null,
    val log: List<LinkLogEntry> = emptyList(),
    val connectOptions: ConnectOptions? = null,
    val attempts: List<ConnectAttemptSummary> = emptyList(),
) {
    val active: Boolean get() = state != LinkState.DISCONNECTED
}
