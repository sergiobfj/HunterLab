package dev.hunterlab.core.link

/** Relatório textual da sessão do laboratório. Sem PIN; endereço mascarado; nada é enviado automaticamente. */
object LinkReport {
    fun format(s: LinkSnapshot, appVersion: String, timestamp: String): String = buildString {
        appendLine("Hunter Lab $appVersion — Laboratório BLE (experimental)")
        appendLine("Data: $timestamp")
        appendLine("Sessão: #${s.sessionId} · estado final/atual: ${s.state.label}")
        appendLine("Dispositivo: ${s.deviceName ?: "(sem nome)"} ${s.maskedAddress ?: ""}".trimEnd())
        appendLine("Caminho de autenticação: ${s.authPath?.label ?: "nenhum"} · tentativas de PIN: ${s.pinAttemptsUsed}/${s.pinAttemptsMax}")
        appendLine("Evidência de autenticação: somente confirmação visual do usuário; nenhuma resposta do Tripper é interpretada.")
        if (s.diagnostics.inboundCaptureEverUsed) {
            appendLine("ATENÇÃO: a captura detalhada foi ligada; bytes recebidos aparecem abaixo e podem conter o PIN. Revise antes de compartilhar.")
        }
        val d = s.diagnostics
        appendLine(
            "Escritas entregues ao controlador: ${d.writesDelivered} · falhas: ${d.writeFailures} · " +
                "respostas recebidas: ${d.inboundAccepted} (rejeitadas: ${d.inboundRejected})",
        )
        s.compass?.let {
            appendLine(
                "Último teste de bússola: ${it.direction.label}, ${it.period.label.lowercase()}, " +
                    "${it.elapsedMs / 1000}/${it.durationMs / 1000} s, quadros enfileirados ${it.framesQueued}, entregues ${it.framesDelivered}",
            )
        }
        if (s.observations.isNotEmpty()) {
            appendLine("Observações do usuário:")
            s.observations.forEach { appendLine("  - $it") }
        }
        s.message?.let { appendLine("Última mensagem: ${it.title} — ${it.detail}") }
        if (s.attempts.isNotEmpty()) {
            appendLine()
            appendLine("Tentativas de conexão (tempos desde o toque em Conectar):")
            s.attempts.forEach { a ->
                fun t(ms: Long?) = ms?.let { LinkFormat.seconds(it) } ?: "—"
                appendLine(
                    "  #${a.sessionId} anúncio recente: ${if (a.freshAdvertisementRequired) "exigido" else "não exigido"} · " +
                        "servidor: ${a.serverMode.label} · último anúncio da busca: ${t(a.lastAdvertisementAgeMs)} · " +
                        "anúncio: ${t(a.advertisementSeenAfterMs)} · conectou: ${t(a.connectedAfterMs)} · " +
                        "servidor pronto: ${t(a.serverReadyAfterMs)} · descoberta: ${t(a.discoveredAfterMs)} · " +
                        "fim: ${t(a.endedAfterMs)} status ${a.endStatus ?: "—"} · ${a.outcome}",
                )
            }
        }
        appendLine()
        appendLine("Histórico (PIN nunca registrado; \"entregue\" = aceito pelo Android, não confirmação do Tripper):")
        s.log.forEach { e ->
            val t = e.sessionElapsedMs?.let { "+${LinkFormat.seconds(it)}" } ?: "—"
            appendLine("  [#${e.sessionId} $t ${e.level.tag}] ${e.text}")
        }
    }
}
