package dev.hunterlab.core.route

import dev.hunterlab.core.geo.GeoPoint
import dev.hunterlab.core.geo.Polyline
import dev.hunterlab.protocol.Maneuver

/** Pedido de rota. [originHeadingDeg] é o rumo atual (0..360), usado só se o provedor aceitar. */
data class RouteRequest(
    val origin: GeoPoint,
    val destination: GeoPoint,
    val originHeadingDeg: Double? = null,
) {
    init {
        require(originHeadingDeg == null || originHeadingDeg.isFinite()) { "Rumo de origem inválido: $originHeadingDeg" }
    }
}

/**
 * Um passo da rota: a [maneuver] acontece em [atAlongM] metros ao longo da [Route.polyline];
 * [lengthM]/[durationS] descrevem o trecho percorrido **depois** dela, até o próximo passo.
 */
data class RouteStep(
    val maneuver: Maneuver,
    val atAlongM: Double,
    val lengthM: Double,
    val durationS: Double,
    val streetName: String?,
    val text: String?,
) {
    init {
        require(atAlongM.isFinite() && atAlongM >= 0) { "Posição da manobra inválida: $atAlongM" }
        require(lengthM.isFinite() && lengthM >= 0) { "Comprimento do passo inválido: $lengthM" }
        require(durationS.isFinite() && durationS >= 0) { "Duração do passo inválida: $durationS" }
    }
}

/**
 * Rota calculada. Os passos estão em ordem de [RouteStep.atAlongM] (não decrescente) e dentro da linha.
 * [distanceM]/[durationS] são os totais informados pelo provedor; o progresso usa sempre a geometria.
 */
data class Route(
    val polyline: Polyline,
    val steps: List<RouteStep>,
    val distanceM: Double,
    val durationS: Double,
    val providerId: String,
    val attribution: String,
) {
    init {
        require(steps.isNotEmpty()) { "Rota sem passos" }
        require(distanceM.isFinite() && distanceM >= 0) { "Distância total inválida: $distanceM" }
        require(durationS.isFinite() && durationS >= 0) { "Duração total inválida: $durationS" }
        val limit = polyline.lengthM + ALONG_EPSILON_M
        steps.forEachIndexed { i, s ->
            require(s.atAlongM <= limit) { "Passo $i fora da linha (${s.atAlongM} m > ${polyline.lengthM} m)" }
            if (i > 0) require(s.atAlongM >= steps[i - 1].atAlongM) { "Passos fora de ordem no índice $i" }
        }
    }

    val destination: GeoPoint get() = polyline.points.last()

    companion object {
        const val ALONG_EPSILON_M = 0.5
    }
}

/** Categoria de falha. [retryable] = faz sentido tentar de novo sem intervenção do usuário. */
enum class RouteFailureKind(val label: String, val retryable: Boolean) {
    NETWORK("Sem conexão", true),
    TIMEOUT("Tempo esgotado", true),
    NO_ROUTE("Rota não encontrada", true),
    INVALID_KEY("Chave inválida", false),
    QUOTA("Limite de uso atingido", true),
    BAD_REQUEST("Pedido recusado", true),
    SERVER("Erro no servidor", true),
    INVALID_RESPONSE("Resposta inválida", true),
    NOT_CONFIGURED("Chave não configurada", false),
}

sealed interface RouteResult {
    data class Success(val route: Route) : RouteResult

    /** [message] em pt-BR, curta, pronta para a UI. Nunca contém a chave da API. */
    data class Failure(val kind: RouteFailureKind, val message: String) : RouteResult
}

/** Fonte de rotas (bloqueante: o app chama em `Dispatchers.IO`). */
interface RouteProvider {
    val id: String
    val displayName: String
    val attribution: String
    fun route(request: RouteRequest): RouteResult
}
