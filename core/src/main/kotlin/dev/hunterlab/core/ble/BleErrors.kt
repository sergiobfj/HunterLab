package dev.hunterlab.core.ble

enum class Severity { INFO, WARNING, ERROR }

/** Mensagem pronta para o usuário, em pt-BR. [expected] marca situações normais (ex.: watchdog do Pod). */
data class UserMessage(
    val title: String,
    val detail: String,
    val hint: String? = null,
    val severity: Severity = Severity.ERROR,
    val expected: Boolean = false,
)

enum class ReadinessIssue {
    NO_BLUETOOTH,
    NO_BLE,
    PERMISSIONS_MISSING,
    PERMISSIONS_PERMANENTLY_DENIED,
    BLUETOOTH_OFF,
}

/** Tradução de códigos do Android para mensagens claras. Sem dependência do SDK (só números). */
object BleErrorTranslator {

    fun readiness(issue: ReadinessIssue): UserMessage = when (issue) {
        ReadinessIssue.NO_BLUETOOTH -> UserMessage(
            "Sem Bluetooth", "Este aparelho não tem adaptador Bluetooth.", severity = Severity.ERROR,
        )
        ReadinessIssue.NO_BLE -> UserMessage(
            "Sem Bluetooth Low Energy", "O aparelho não suporta BLE, necessário para o Tripper.",
        )
        ReadinessIssue.PERMISSIONS_MISSING -> UserMessage(
            "Permissões necessárias",
            "Para buscar e inspecionar dispositivos, o app precisa de \"Dispositivos por perto\" (buscar e conectar).",
            "O app não usa localização: a busca é declarada como \"nunca para localização\".",
            Severity.WARNING,
        )
        ReadinessIssue.PERMISSIONS_PERMANENTLY_DENIED -> UserMessage(
            "Permissões negadas",
            "O Android não vai mais mostrar o pedido de permissão.",
            "Abra as configurações do app e permita \"Dispositivos por perto\".",
            Severity.ERROR,
        )
        ReadinessIssue.BLUETOOTH_OFF -> UserMessage(
            "Bluetooth desligado", "Ligue o Bluetooth para buscar dispositivos.", severity = Severity.WARNING,
        )
    }

    /** Códigos de `ScanCallback.SCAN_FAILED_*`. */
    fun scanFailed(code: Int): UserMessage = when (code) {
        1 -> UserMessage("Busca já em andamento", "O Android informou que já existe uma busca ativa.", "Pare e inicie de novo.", Severity.WARNING)
        2 -> UserMessage("Falha ao registrar a busca", "O Android não registrou o app no scanner BLE (código 2).", "Desligue e ligue o Bluetooth.")
        3 -> UserMessage("Erro interno do Bluetooth", "Falha interna da pilha Bluetooth (código 3).", "Desligue e ligue o Bluetooth.")
        4 -> UserMessage("Recurso não suportado", "Este aparelho não suporta a busca solicitada (código 4).")
        5 -> UserMessage("Sem recursos de hardware", "O controlador Bluetooth está sem recursos (código 5).", "Feche outros apps que usem Bluetooth.")
        6 -> scanThrottled(null)
        else -> UserMessage("Falha na busca", "O Android recusou a busca (código $code).")
    }

    /** O Android limita a 5 inícios de busca a cada 30 s por app. */
    fun scanThrottled(waitMs: Long?): UserMessage = UserMessage(
        "Buscas muito frequentes",
        "O Android limita a 5 buscas a cada 30 segundos.",
        waitMs?.let { "Aguarde ${(it + 999) / 1000} s e tente de novo." } ?: "Aguarde alguns segundos e tente de novo.",
        Severity.WARNING,
    )

    /**
     * Status recebido em `onConnectionStateChange` ao desconectar.
     * @param initiatedByApp `true` se o próprio app pediu a desconexão.
     */
    fun connectionClosed(status: Int, initiatedByApp: Boolean): UserMessage = when {
        initiatedByApp -> UserMessage(
            "Desconectado pelo app",
            "A conexão foi encerrada a pedido do usuário.",
            "Atenção: na Meteor 350, após desconexão iniciada pelo celular o Tripper pode só voltar a anunciar depois de desligar e ligar a ignição.",
            Severity.INFO, expected = true,
        )
        status == 19 -> UserMessage(
            "O dispositivo encerrou a conexão",
            "Status 19 (encerrado pelo dispositivo remoto).",
            "Esperado no Tripper: sem pacotes, o firmware derruba o link em ~5 s. A inspeção já capturada continua disponível.",
            Severity.INFO, expected = true,
        )
        status == 8 -> UserMessage(
            "Conexão perdida (timeout)",
            "Status 8: o dispositivo parou de responder (supervisão do link).",
            "Aproxime o celular e confirme que a ignição está ligada.",
            Severity.WARNING,
        )
        status == 22 -> UserMessage("Encerrada pelo Android", "Status 22: a pilha Bluetooth do celular encerrou o link.", severity = Severity.WARNING)
        status == 62 -> UserMessage(
            "Falha ao estabelecer conexão",
            "Status 62: a conexão não chegou a ser estabelecida.",
            "O dispositivo pode ter parado de anunciar. No Tripper, desligue e ligue a ignição e tente em até 30 s.",
        )
        status == 133 -> UserMessage(
            "Erro genérico de GATT (133)",
            "Falha comum do Android ao conectar BLE.",
            "Tente de novo; se persistir, desligue/ligue o Bluetooth. Confirme que o app oficial não está conectado ao Tripper.",
        )
        status == 147 -> UserMessage(
            "Tempo de conexão esgotado (147)",
            "Status 147 (GATT_CONNECTION_TIMEOUT): o link não foi estabelecido — o Tripper não aceitou conexão naquele endereço a tempo.",
            "Causas prováveis: o Tripper não está anunciando (janela de ~30 s encerrada, ou parou após uma desconexão pelo celular " +
                "no mesmo ciclo de ignição), outro app conectado a ele, ou endereço/tipo de endereço diferente. Cicle a ignição e busque de novo.",
        )
        status == 0 -> UserMessage("Desconectado", "O link foi encerrado normalmente.", severity = Severity.INFO, expected = true)
        else -> UserMessage("Conexão encerrada", "Status $status.", severity = Severity.WARNING)
    }

    fun connectionTimeout(timeoutMs: Long): UserMessage = UserMessage(
        "Sem resposta do dispositivo",
        "Nenhuma conexão em ${timeoutMs / 1000} s.",
        "O Tripper só anuncia por ~30 s após ligar a ignição. Desligue e ligue a ignição e tente logo em seguida.",
        Severity.WARNING,
    )

    fun discoveryFailed(status: Int): UserMessage = UserMessage(
        "Falha ao descobrir serviços",
        "O Android não conseguiu listar os serviços GATT (status $status).",
        "Reconecte e tente de novo.",
    )

    fun readFailed(status: Int): UserMessage = when (status) {
        2 -> UserMessage("Leitura não permitida", "A característica recusou a leitura (status 2).", severity = Severity.WARNING)
        5, 15 -> UserMessage(
            "Leitura exige autenticação",
            "O dispositivo pediu pareamento/criptografia (status $status).",
            "Esta versão não faz pareamento no nível do sistema; leitura ignorada.",
            Severity.WARNING,
        )
        else -> UserMessage("Falha na leitura", "Status $status.", severity = Severity.WARNING)
    }

    fun permissionRevoked(): UserMessage = UserMessage(
        "Permissão ausente",
        "O Android bloqueou a operação por falta de permissão Bluetooth.",
        "Conceda \"Dispositivos por perto\" e tente de novo.",
    )
}

/**
 * Antecipa o limite do Android (5 inícios de scan em 30 s) para dar uma mensagem clara
 * em vez de uma busca que silenciosamente não retorna nada.
 */
class ScanThrottle(
    private val clock: () -> Long,
    private val maxStarts: Int = 5,
    private val windowMs: Long = 30_000,
) {
    private val starts = ArrayDeque<Long>()

    /** `null` se pode iniciar (e registra o início); senão, quantos ms esperar. */
    fun tryAcquire(): Long? {
        val now = clock()
        while (starts.isNotEmpty() && now - starts.first() >= windowMs) starts.removeFirst()
        if (starts.size >= maxStarts) return windowMs - (now - starts.first())
        starts.addLast(now)
        return null
    }
}
