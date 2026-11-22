package dev.hunterlab.core.search

import dev.hunterlab.core.geo.GeoPoint
import dev.hunterlab.core.route.*
import kotlinx.serialization.json.*
import java.net.URLEncoder

data class Place(val id: String, val name: String, val detail: String?, val point: GeoPoint)
sealed interface PlaceSearchResult {
    data class Found(val places: List<Place>) : PlaceSearchResult
    data class Failed(val kind: RouteFailureKind, val message: String) : PlaceSearchResult
}

/** MapTiler POI/address autocomplete. No IP geolocation, tracking, raw error bodies or key logging. */
class MapTilerPlaceSearch(private val http: HttpGet, private val apiKey: () -> String?) {
    fun search(query: String, near: GeoPoint?): PlaceSearchResult {
        val q = query.trim().take(160)
        if (q.length < 3) return PlaceSearchResult.Found(emptyList())
        val key = apiKey()?.trim().takeUnless { it.isNullOrEmpty() }
            ?: return PlaceSearchResult.Failed(RouteFailureKind.NOT_CONFIGURED, "Configure a chave MapTiler em Configurações para buscar estabelecimentos.")
        return when (val r = try { http.get(buildUrl(q, near, key), 10_000) }
            catch (_: Exception) { HttpResult.Failed(HttpFailureKind.NETWORK, "Falha de rede") }) {
            is HttpResult.Failed -> PlaceSearchResult.Failed(
                if (r.kind == HttpFailureKind.TIMEOUT) RouteFailureKind.TIMEOUT else RouteFailureKind.NETWORK,
                if (r.kind == HttpFailureKind.TIMEOUT) "Busca demorou demais. Tente novamente." else "Sem conexão. Recentes e favoritos continuam disponíveis.")
            is HttpResult.Ok -> when (r.code) {
                200 -> parse(r.body)
                401, 403 -> PlaceSearchResult.Failed(RouteFailureKind.INVALID_KEY, "Chave MapTiler inválida ou sem permissão.")
                429 -> PlaceSearchResult.Failed(RouteFailureKind.QUOTA, "Limite de consultas MapTiler atingido. Tente mais tarde.")
                else -> PlaceSearchResult.Failed(RouteFailureKind.SERVER, "Busca indisponível. Tente novamente.")
            }
        }
    }

    fun buildUrl(query: String, near: GeoPoint?, key: String): String = buildString {
        append("https://api.maptiler.com/geocoding/")
        append(URLEncoder.encode(query, "UTF-8").replace("+", "%20"))
        append(".json?language=pt&country=br&limit=6&autocomplete=true&fuzzyMatch=true")
        near?.let { append("&proximity=").append(String.format(java.util.Locale.ROOT, "%.3f,%.3f", it.lon, it.lat)) }
        append("&key=").append(URLEncoder.encode(key, "UTF-8"))
    }

    companion object {
        fun parse(body: String): PlaceSearchResult = try {
            val features = Json.parseToJsonElement(body).jsonObject["features"]?.jsonArray
                ?: error("missing features")
            val places = features.take(20).mapNotNull { feature -> runCatching {
                val f = feature.jsonObject
                val center = f["center"]?.jsonArray ?: f["geometry"]!!.jsonObject["coordinates"]!!.jsonArray
                val point = GeoPoint(center[1].jsonPrimitive.double, center[0].jsonPrimitive.double)
                val name = f["text"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }?.take(160) ?: return@runCatching null
                val full = f["place_name"]?.jsonPrimitive?.content?.take(400)
                val detail = full?.removePrefix(name)?.trimStart(',', ' ')?.takeIf { it.isNotBlank() && it != name }
                Place(f["id"]?.jsonPrimitive?.content ?: "${point.lat},${point.lon}", name, detail, point)
            }.getOrNull() }.distinctBy { it.id }.take(6)
            PlaceSearchResult.Found(places)
        } catch (_: Exception) { PlaceSearchResult.Failed(RouteFailureKind.INVALID_RESPONSE, "Resposta de busca inválida. Tente novamente.") }
    }
}
