package dev.hunterlab.core.geo

import java.net.URLDecoder

/** Destino lido de texto: coordenadas digitadas, link `geo:` ou link de mapa com coordenadas explícitas. */
data class ParsedPlace(val point: GeoPoint, val label: String?)

sealed interface ParseResult {
    data class Ok(val place: ParsedPlace) : ParseResult
    data class Error(val message: String) : ParseResult
}

/**
 * Fallback de destino sem geocodificação (sem chave, sem rede):
 * - "-23.5614, -46.6559" ou "-23.5614 -46.6559" (ponto decimal; vírgula decimal com ";" como separador também)
 * - "geo:-23.56,-46.65", "geo:0,0?q=-23.56,-46.65(Nome)" (Android/WhatsApp)
 * - links com "@lat,lon", "q=lat,lon", "ll=lat,lon", "query=lat,lon", "destination=lat,lon"
 * Links encurtados (ex.: maps.app.goo.gl) não trazem coordenadas e são recusados com explicação.
 */
object CoordinateParser {
    private const val NUM = """[-+]?\d{1,3}(?:\.\d+)?"""
    private val plainPair = Regex("""^\s*($NUM)\s*[,\s]\s*($NUM)\s*$""")
    private val commaDecimalPair = Regex("""^\s*([-+]?\d{1,3},\d+)\s*;\s*([-+]?\d{1,3},\d+)\s*$""")
    private val atPair = Regex("""@($NUM),($NUM)""")
    private val paramPair = Regex("""(?:[?&](?:q|ll|query|destination|daddr)=)(?:loc:)?($NUM)\s*,\s*($NUM)""")
    private val geoPath = Regex("""^geo:($NUM),($NUM)""", RegexOption.IGNORE_CASE)
    private val geoLabel = Regex("""\(([^)]*)\)\s*$""")

    fun parse(input: String): ParseResult {
        val raw = input.trim()
        if (raw.isEmpty()) return ParseResult.Error("Informe as coordenadas, por exemplo: -23.5614, -46.6559")
        val text = runCatching { URLDecoder.decode(raw, "UTF-8") /* sobrecarga com Charset só no Android 13+ */ }.getOrDefault(raw)

        plainPair.matchEntire(text)?.let { return build(it.groupValues[1], it.groupValues[2], null) }
        commaDecimalPair.matchEntire(text)?.let {
            return build(it.groupValues[1].replace(',', '.'), it.groupValues[2].replace(',', '.'), null)
        }
        if (text.startsWith("geo:", ignoreCase = true)) {
            val label = geoLabel.find(text)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
            paramPair.find(text)?.let {
                return build(it.groupValues[1], it.groupValues[2], label)
            }
            geoPath.find(text)?.let { m ->
                val lat = m.groupValues[1].toDouble()
                val lon = m.groupValues[2].toDouble()
                if (lat == 0.0 && lon == 0.0) return ParseResult.Error("O link geo: não traz coordenadas (só um nome de lugar). Busque pelo nome.")
                return build(m.groupValues[1], m.groupValues[2], label)
            }
            return ParseResult.Error("Link geo: sem coordenadas.")
        }
        atPair.find(text)?.let { return build(it.groupValues[1], it.groupValues[2], null) }
        paramPair.find(text)?.let { return build(it.groupValues[1], it.groupValues[2], null) }
        if (text.contains("goo.gl") || text.contains("maps.app")) {
            return ParseResult.Error("Link encurtado não traz coordenadas. No mapa, copie as coordenadas do ponto (ex.: -23.5614, -46.6559).")
        }
        return ParseResult.Error("Formato não reconhecido. Use latitude, longitude — ex.: -23.5614, -46.6559")
    }

    private fun build(latText: String, lonText: String, label: String?): ParseResult {
        val lat = latText.toDoubleOrNull()
        val lon = lonText.toDoubleOrNull()
        if (lat == null || lon == null) return ParseResult.Error("Coordenadas inválidas.")
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return ParseResult.Error("Coordenadas fora do intervalo (latitude ±90, longitude ±180).")
        return ParseResult.Ok(ParsedPlace(GeoPoint(lat, lon), label))
    }
}
