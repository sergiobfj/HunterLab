package dev.hunterlab.core.route

import dev.hunterlab.core.geo.GeoPoint
import dev.hunterlab.core.geo.Polyline
import dev.hunterlab.protocol.Maneuver
import dev.hunterlab.protocol.Side
import dev.hunterlab.protocol.TurnSharpness
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

/**
 * Leitura pura (sem rede) das respostas da Routing API do GraphHopper (`GET /api/1/route`).
 * Usa só `paths[0]`. Qualquer incoerência estrutural vira [RouteFailureKind.INVALID_RESPONSE] —
 * nunca uma exceção e nunca uma rota "meio certa".
 */
object GraphHopperParser {
    const val PROVIDER_ID = "graphhopper"
    const val DEFAULT_ATTRIBUTION = "GraphHopper, OpenStreetMap contributors"

    /**
     * Tolerância entre `paths[0].distance` e o comprimento da geometria decodificada. Pequenas diferenças
     * (arredondamento de 1e-5°) são normais e o progresso usa sempre a geometria; acima disso a resposta é lixo.
     */
    const val DISTANCE_TOLERANCE_MIN_M = 100.0
    const val DISTANCE_TOLERANCE_FRACTION = 0.25

    /** Resposta HTTP completa: 2xx vai para [parse]; o resto, para [parseError]. */
    fun parseHttp(code: Int, body: String): RouteResult =
        if (code in 200..299) parse(body) else parseError(code, body)

    /** Corpo de uma resposta 200. */
    fun parse(body: String): RouteResult {
        val root = try {
            Json.parseToJsonElement(body)
        } catch (e: Exception) {
            return invalid("JSON malformado")
        }
        val obj = root as? JsonObject ?: return invalid("a raiz não é um objeto")
        val paths = obj["paths"] as? JsonArray ?: return invalid("campo 'paths' ausente")
        val path = paths.firstOrNull() as? JsonObject ?: return invalid("nenhum caminho em 'paths'")

        val distanceM = path["distance"].finiteNonNegative() ?: return invalid("distância total ausente ou inválida")
        val timeMs = path["time"].finiteNonNegative() ?: return invalid("tempo total ausente ou inválido")

        val points = when (val decoded = decodePoints(path)) {
            is Decoded.Points -> decoded.points
            is Decoded.Error -> return invalid(decoded.reason)
        }
        if (points.size < 2) return invalid("a geometria tem menos de 2 pontos")
        val polyline = Polyline(points)
        val tolerance = max(DISTANCE_TOLERANCE_MIN_M, DISTANCE_TOLERANCE_FRACTION * polyline.lengthM)
        if (abs(distanceM - polyline.lengthM) > tolerance) {
            return invalid("distância total (${distanceM.toInt()} m) incoerente com a geometria (${polyline.lengthM.toInt()} m)")
        }

        val instructions = path["instructions"] as? JsonArray ?: return invalid("campo 'instructions' ausente")
        if (instructions.isEmpty()) return invalid("lista de instruções vazia")

        val lastIndex = points.size - 1
        val steps = ArrayList<RouteStep>(instructions.size)
        var previousFrom = 0
        instructions.forEachIndexed { i, element ->
            val ins = element as? JsonObject ?: return invalid("instrução $i não é um objeto")
            val sign = ins["sign"].intValue() ?: return invalid("instrução $i sem 'sign' válido")
            val interval = ins["interval"] as? JsonArray ?: return invalid("instrução $i sem 'interval'")
            if (interval.size != 2) return invalid("instrução $i: 'interval' deve ter 2 índices")
            val from = interval[0].intValue() ?: return invalid("instrução $i: índice inicial inválido")
            val to = interval[1].intValue() ?: return invalid("instrução $i: índice final inválido")
            if (from !in 0..lastIndex || to !in 0..lastIndex || from > to) {
                return invalid("instrução $i: intervalo [$from, $to] fora da geometria (0..$lastIndex)")
            }
            if (from < previousFrom) return invalid("instrução $i fora de ordem na geometria")
            previousFrom = from
            val dist = ins["distance"].finiteNonNegative() ?: return invalid("instrução $i: distância inválida")
            val time = ins["time"].finiteNonNegative() ?: return invalid("instrução $i: tempo inválido")
            val exit = ins["exit_number"].intValue()
            val maneuver = if (i == 0) Maneuver.Depart else maneuverForSign(sign, exit)
            steps += RouteStep(
                maneuver = maneuver,
                atAlongM = polyline.cumulative[from],
                lengthM = dist,
                durationS = time / 1000.0,
                streetName = ins["street_name"].text(),
                text = ins["text"].text(),
            )
        }

        val attribution = (obj["info"] as? JsonObject)?.get("copyrights")
            ?.let { it as? JsonArray }
            ?.mapNotNull { it.text() }
            ?.takeIf { it.isNotEmpty() }
            ?.joinToString(", ")
            ?: DEFAULT_ATTRIBUTION

        return try {
            RouteResult.Success(
                Route(
                    polyline = polyline,
                    steps = steps,
                    distanceM = distanceM,
                    durationS = timeMs / 1000.0,
                    providerId = PROVIDER_ID,
                    attribution = attribution,
                ),
            )
        } catch (e: IllegalArgumentException) {
            invalid("rota incoerente")
        }
    }

    /** Resposta HTTP não-2xx. A mensagem do servidor (em inglês) só é usada para classificar. */
    fun parseError(code: Int, body: String): RouteResult.Failure {
        val serverText = errorText(body).lowercase()
        return when {
            code == 401 || code == 403 -> RouteResult.Failure(
                RouteFailureKind.INVALID_KEY,
                "Chave da API do GraphHopper inválida ou sem permissão (HTTP $code).",
            )
            code == 429 -> RouteResult.Failure(
                RouteFailureKind.QUOTA,
                "Limite de uso da API do GraphHopper atingido. Tente mais tarde.",
            )
            code == 400 && noRouteMessage(serverText) != null -> RouteResult.Failure(
                RouteFailureKind.NO_ROUTE,
                noRouteMessage(serverText)!!,
            )
            code in 400..499 -> RouteResult.Failure(
                RouteFailureKind.BAD_REQUEST,
                "Pedido de rota recusado pelo GraphHopper (HTTP $code).",
            )
            code in 500..599 -> RouteResult.Failure(
                RouteFailureKind.SERVER,
                "Erro no servidor do GraphHopper (HTTP $code). Tente mais tarde.",
            )
            else -> RouteResult.Failure(
                RouteFailureKind.INVALID_RESPONSE,
                "Resposta inesperada do GraphHopper (HTTP $code).",
            )
        }
    }

    /**
     * `sign` do GraphHopper → manobra neutra. Sinais desconhecidos (a API avisa que pode criar novos) viram
     * [Maneuver.Straight]. `-6` ("sair da rotatória") ainda não é usado pela API.
     */
    fun maneuverForSign(sign: Int, exitNumber: Int? = null): Maneuver = when (sign) {
        -98, -8, 8 -> Maneuver.UTurn
        -7 -> Maneuver.Keep(Side.LEFT)
        -3 -> Maneuver.Turn(Side.LEFT, TurnSharpness.SHARP)
        -2 -> Maneuver.Turn(Side.LEFT)
        -1 -> Maneuver.Turn(Side.LEFT, TurnSharpness.SLIGHT)
        0 -> Maneuver.Straight
        1 -> Maneuver.Turn(Side.RIGHT, TurnSharpness.SLIGHT)
        2 -> Maneuver.Turn(Side.RIGHT)
        3 -> Maneuver.Turn(Side.RIGHT, TurnSharpness.SHARP)
        4 -> Maneuver.Arrive()
        5 -> Maneuver.Straight
        6 -> Maneuver.Roundabout(exitNumber?.takeIf { it >= 1 } ?: 1)
        -6 -> Maneuver.Straight
        7 -> Maneuver.Keep(Side.RIGHT)
        else -> Maneuver.Straight
    }

    private fun noRouteMessage(text: String): String? = when {
        "pointnotfound" in text || "cannot find point" in text ->
            "Nenhuma via encontrada perto da origem ou do destino."
        "connectionnotfound" in text || "connection between locations not found" in text ->
            "Não há ligação por vias entre a origem e o destino."
        "pointoutofbounds" in text || "out of bounds" in text ->
            "Origem ou destino fora da área atendida pelo serviço de rotas."
        else -> null
    }

    private fun errorText(body: String): String {
        val obj = try {
            Json.parseToJsonElement(body) as? JsonObject
        } catch (e: Exception) {
            null
        } ?: return body.take(500)
        val parts = mutableListOf<String>()
        obj["message"].text()?.let { parts += it }
        (obj["hints"] as? JsonArray)?.forEach { hint ->
            (hint as? JsonObject)?.let { h ->
                h["message"].text()?.let { parts += it }
                h["details"].text()?.let { parts += it }
            }
        }
        return parts.joinToString(" | ")
    }

    private sealed interface Decoded {
        data class Points(val points: List<GeoPoint>) : Decoded
        data class Error(val reason: String) : Decoded
    }

    private fun decodePoints(path: JsonObject): Decoded {
        val encodedFlag = (path["points_encoded"] as? JsonPrimitive)?.takeIf { !it.isString }?.content
        val encoded = when (encodedFlag) {
            null, "true" -> true // padrão da API
            "false" -> false
            else -> return Decoded.Error("'points_encoded' inválido")
        }
        val raw = path["points"] ?: return Decoded.Error("geometria ('points') ausente")
        return try {
            if (encoded) {
                val text = (raw as? JsonPrimitive)?.takeIf { it.isString }?.content
                    ?: return Decoded.Error("geometria codificada não é texto")
                val multiplier = path["points_encoded_multiplier"]?.takeIf { it !is JsonNull }?.let {
                    it.number()?.takeIf { m -> m.isFinite() && m > 0 }
                        ?: return Decoded.Error("'points_encoded_multiplier' inválido")
                } ?: 1e5
                Decoded.Points(PolylineCodec.decode(text, multiplier))
            } else {
                // GeoJSON LineString: [[lon, lat(, ele)], ...]
                val coords = ((raw as? JsonObject)?.get("coordinates") as? JsonArray)
                    ?: return Decoded.Error("geometria GeoJSON sem 'coordinates'")
                Decoded.Points(
                    coords.map { c ->
                        val pair = c as? JsonArray ?: throw IllegalArgumentException("coordenada não é lista")
                        require(pair.size >= 2) { "coordenada incompleta" }
                        val lon = pair[0].number() ?: throw IllegalArgumentException("longitude inválida")
                        val lat = pair[1].number() ?: throw IllegalArgumentException("latitude inválida")
                        GeoPoint(lat, lon)
                    },
                )
            }
        } catch (e: IllegalArgumentException) {
            Decoded.Error("geometria inválida (${e.message})")
        }
    }

    private fun invalid(reason: String) = RouteResult.Failure(
        RouteFailureKind.INVALID_RESPONSE,
        "Resposta inválida do serviço de rotas: $reason.",
    )

    private fun JsonElement?.number(): Double? {
        val p = this as? JsonPrimitive ?: return null
        if (p.isString) return null
        return p.content.toDoubleOrNull()
    }

    private fun JsonElement?.finiteNonNegative(): Double? = number()?.takeIf { it.isFinite() && it >= 0 }

    private fun JsonElement?.intValue(): Int? {
        val d = number() ?: return null
        if (!d.isFinite() || d != floor(d) || abs(d) > Int.MAX_VALUE) return null
        return d.toInt()
    }

    private fun JsonElement?.text(): String? =
        (this as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()?.takeIf { it.isNotEmpty() }
}
