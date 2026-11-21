package dev.hunterlab.core.route

import dev.hunterlab.core.geo.GeoPoint
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Rotas pela Routing API do GraphHopper (`GET /api/1/route`, perfil `car`, instruções em pt-BR).
 *
 * **Rumo inicial (`heading`)**: segundo docs.graphhopper.com, `heading`/`heading_penalty` exigem
 * `ch.disable=true` (modo flexível, mais lento e que pode consumir mais créditos). Por isso fica
 * **desligado** por padrão ([useHeading] = false) e o [RouteRequest.originHeadingDeg] é ignorado.
 *
 * A chave vem de [apiKey] a cada pedido (nunca guardada aqui) e nunca aparece em mensagens: use [redact]
 * antes de registrar URLs.
 */
class GraphHopperRouteProvider(
    private val http: HttpGet,
    private val apiKey: () -> String?,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val timeoutMs: Int = 15_000,
    private val useHeading: Boolean = false,
    private val headingPenaltyS: Int = 120,
) : RouteProvider {

    override val id: String = GraphHopperParser.PROVIDER_ID
    override val displayName: String = "GraphHopper"
    override val attribution: String = "Rotas: GraphHopper · Mapa: © colaboradores do OpenStreetMap"

    override fun route(request: RouteRequest): RouteResult {
        val key = apiKey()?.trim()
        if (key.isNullOrEmpty()) {
            return RouteResult.Failure(RouteFailureKind.NOT_CONFIGURED, "Chave da API do GraphHopper não configurada.")
        }
        val url = buildUrl(request, key)
        val response = try {
            http.get(url, timeoutMs)
        } catch (e: Exception) {
            HttpResult.Failed(HttpFailureKind.NETWORK, e.javaClass.simpleName)
        }
        val result = when (response) {
            is HttpResult.Failed -> when (response.kind) {
                HttpFailureKind.TIMEOUT -> RouteResult.Failure(
                    RouteFailureKind.TIMEOUT,
                    "O serviço de rotas demorou demais para responder.",
                )
                HttpFailureKind.NETWORK -> RouteResult.Failure(
                    RouteFailureKind.NETWORK,
                    "Sem conexão com o serviço de rotas.",
                )
            }
            is HttpResult.Ok -> try {
                GraphHopperParser.parseHttp(response.code, response.body)
            } catch (e: Exception) {
                RouteResult.Failure(RouteFailureKind.INVALID_RESPONSE, "Resposta inválida do serviço de rotas.")
            }
        }
        return if (result is RouteResult.Failure) result.copy(message = scrub(result.message, key)) else result
    }

    /** URL completa do pedido (contém a chave: só para a porta HTTP; para log use [redact]). */
    fun buildUrl(request: RouteRequest, key: String): String = buildString {
        append(baseUrl)
        append(if ('?' in baseUrl) '&' else '?')
        append("point=").append(coord(request.origin))
        append("&point=").append(coord(request.destination))
        append("&profile=car&locale=pt_BR&instructions=true&calc_points=true&points_encoded=true")
        val heading = request.originHeadingDeg
        if (useHeading && heading != null) {
            val h = ((heading % 360.0 + 360.0) % 360.0).roundToInt() % 360
            append("&ch.disable=true&heading=").append(h).append("&heading_penalty=").append(headingPenaltyS)
        }
        append("&key=").append(URLEncoder.encode(key, "UTF-8") /* sobrecarga com Charset só no Android 13+ */)
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://graphhopper.com/api/1/route"

        private val KEY_PARAM = Regex("([?&]key=)[^&#]*", RegexOption.IGNORE_CASE)

        /** Remove o valor do parâmetro `key` de uma URL (para log/diagnóstico). */
        fun redact(url: String): String = KEY_PARAM.replace(url, "$1***")

        private fun coord(p: GeoPoint) = String.format(Locale.ROOT, "%.6f,%.6f", p.lat, p.lon)

        private fun scrub(message: String, key: String): String {
            val redacted = redact(message)
            return if (key.length >= 4) redacted.replace(key, "***") else redacted
        }
    }
}
