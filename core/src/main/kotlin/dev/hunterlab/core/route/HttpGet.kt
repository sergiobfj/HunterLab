package dev.hunterlab.core.route

/** Porta HTTP mínima (bloqueante). A implementação real fica no app; testes usam um falso. */
fun interface HttpGet {
    /**
     * Faz um GET em [url]. Qualquer resposta HTTP (inclusive 4xx/5xx) é [HttpResult.Ok];
     * só falhas de transporte viram [HttpResult.Failed].
     */
    fun get(url: String, timeoutMs: Int): HttpResult
}

enum class HttpFailureKind { NETWORK, TIMEOUT }

sealed interface HttpResult {
    data class Ok(val code: Int, val body: String) : HttpResult

    /** [detail] é só para log e pode conter a URL: nunca mostre sem passar por um redator. */
    data class Failed(val kind: HttpFailureKind, val detail: String) : HttpResult
}
