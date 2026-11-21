package dev.hunterlab.core.link

/**
 * Estados da sessão de comunicação com o Tripper (Laboratório BLE).
 *
 * Só [WAITING_FOR_PIN], [AUTHENTICATING], [AUTHENTICATED] e [DISPLAY_ACTIVE] permitem escrita,
 * e mesmo assim apenas com transmissão autorizada para a sessão.
 */
enum class LinkState(val label: String) {
    DISCONNECTED("Desconectado"),
    CONNECTING("Conectando"),
    DISCOVERING("Descobrindo serviços"),
    READY_FOR_AUTH("Pronto para autenticar"),
    WAITING_FOR_PIN("Aguardando PIN"),
    AUTHENTICATING("Autenticando"),
    /** Só por confirmação visual do usuário: o protocolo não tem resposta documentada que confirme. */
    AUTHENTICATED("Autenticado (confirmação visual)"),
    DISPLAY_ACTIVE("Tela de teste ativa"),
    STOPPING("Interrompendo"),
    ERROR("Erro"),
    ;

    val allowsWrites: Boolean
        get() = this == WAITING_FOR_PIN || this == AUTHENTICATING || this == AUTHENTICATED || this == DISPLAY_ACTIVE
}

/** Tabela única de transições válidas. Qualquer outra é erro de programação. */
object LinkTransitions {
    private val table: Map<LinkState, Set<LinkState>> = with(LinkState.entries) {
        mapOf(
            LinkState.DISCONNECTED to setOf(LinkState.CONNECTING),
            LinkState.CONNECTING to setOf(LinkState.DISCOVERING, LinkState.STOPPING, LinkState.ERROR, LinkState.DISCONNECTED),
            LinkState.DISCOVERING to setOf(LinkState.READY_FOR_AUTH, LinkState.STOPPING, LinkState.ERROR, LinkState.DISCONNECTED),
            LinkState.READY_FOR_AUTH to setOf(
                LinkState.WAITING_FOR_PIN, LinkState.AUTHENTICATING, LinkState.STOPPING, LinkState.ERROR, LinkState.DISCONNECTED,
            ),
            LinkState.WAITING_FOR_PIN to setOf(LinkState.AUTHENTICATING, LinkState.STOPPING, LinkState.ERROR, LinkState.DISCONNECTED),
            LinkState.AUTHENTICATING to setOf(
                LinkState.AUTHENTICATED, LinkState.WAITING_FOR_PIN, LinkState.STOPPING, LinkState.ERROR, LinkState.DISCONNECTED,
            ),
            LinkState.AUTHENTICATED to setOf(LinkState.DISPLAY_ACTIVE, LinkState.STOPPING, LinkState.ERROR, LinkState.DISCONNECTED),
            LinkState.DISPLAY_ACTIVE to setOf(LinkState.AUTHENTICATED, LinkState.STOPPING, LinkState.ERROR, LinkState.DISCONNECTED),
            LinkState.STOPPING to setOf(LinkState.DISCONNECTED),
            LinkState.ERROR to setOf(LinkState.STOPPING, LinkState.DISCONNECTED),
        ).also { check(it.keys == toSet()) { "Tabela de transições incompleta" } }
    }

    fun allowed(from: LinkState, to: LinkState): Boolean = to in targets(from)

    fun targets(from: LinkState): Set<LinkState> = table[from].orEmpty()
}

sealed interface CommandResult {
    data object Accepted : CommandResult
    data class Rejected(val reason: String) : CommandResult
}

/**
 * Parâmetros de tempo e limites. Os valores marcados "referência" vêm do WazeTripper (Meteor 350);
 * os demais são escolhas conservadoras do Hunter Lab. Ajustáveis internamente e cobertos por testes.
 */
data class LinkTiming(
    val connectTimeoutMs: Long = 15_000,
    val discoveryTimeoutMs: Long = 10_000,
    /** Referência: esperar ~200 ms entre a descoberta e a primeira escrita. */
    val postDiscoveryDelayMs: Long = 200,
    /** Referência (retomada): RESUME → 200 ms → SET_TIME → 150 ms → PING ×2 → 300 ms. */
    val resumeToSetTimeMs: Long = 200,
    val setTimeToPingMs: Long = 150,
    val pingsToConfirmMs: Long = 300,
    /** Referência: verificar a cada 1 s; PING_FW após 2 s sem escrita. */
    val keepaliveCheckMs: Long = 1_000,
    val keepaliveIdleMs: Long = 2_000,
    /** Referência: telas reenviadas a cada ~2 s (watchdog da bússola ~5 s). */
    val screenResendMs: Long = 2_000,
    /** Referência: só serialização por callback, sem intervalo extra. */
    val minWriteGapMs: Long = 0,
    val writeCompletionTimeoutMs: Long = 1_000,
    val maxQueue: Int = 8,
    val maxPendingAgeMs: Long = 1_500,
    val maxConsecutiveWriteFailures: Int = 3,
    val pinEntryTimeoutMs: Long = 60_000,
    val authConfirmTimeoutMs: Long = 45_000,
    val maxPinAttempts: Int = 3,
    val compassMinMs: Long = 5_000,
    val compassDefaultMs: Long = 20_000,
    val compassMaxMs: Long = 60_000,
    /** Tempo dado ao Tripper para encerrar o link sozinho antes de o app desconectar. */
    val stopGraceMs: Long = 8_000,
    val forceCloseMs: Long = 2_000,
    val sessionMaxMs: Long = 300_000,
    val preAuthorizationTtlMs: Long = 120_000,
) {
    init {
        require(postDiscoveryDelayMs >= 0 && minWriteGapMs >= 0) { "Atrasos não podem ser negativos" }
        require(keepaliveCheckMs > 0 && keepaliveIdleMs > 0 && screenResendMs > 0) { "Intervalos devem ser positivos" }
        require(maxQueue in 1..64) { "Fila deve ter de 1 a 64 posições" }
        require(maxPinAttempts in 1..5) { "Tentativas de PIN devem ficar entre 1 e 5" }
        require(maxConsecutiveWriteFailures >= 1) { "Limite de falhas deve ser ≥ 1" }
        require(compassMinMs in 1..compassDefaultMs && compassDefaultMs <= compassMaxMs) { "Durações da bússola incoerentes" }
        require(compassMaxMs <= 120_000) { "Teste de bússola limitado a 2 min" }
        require(screenResendMs < 5_000) { "Reenvio precisa ser menor que o watchdog (~5 s)" }
    }
}

/** Itens que o usuário confirma antes de liberar transmissão real. */
data class TransmissionAck(
    val bikeStopped: Boolean,
    val officialAppClosed: Boolean,
    val guideRead: Boolean,
    val pairingRiskAccepted: Boolean,
    val knowsHowToInterrupt: Boolean,
) {
    val complete: Boolean get() = bikeStopped && officialAppClosed && guideRead && pairingRiskAccepted && knowsHowToInterrupt
}
