package dev.hunterlab.app.ui.map

import java.net.URLEncoder

/** Only a fixed MapTiler raster endpoint. No arbitrary style URLs, scripts, offline download or telemetry. */
object MapStyleConfig {
    fun style(key: String?): String? {
        if (key.isNullOrBlank()) return null
        val encoded = URLEncoder.encode(key.trim(), "UTF-8")
        return """{"version":8,"sources":{"base":{"type":"raster","tiles":["https://api.maptiler.com/maps/streets-v4-dark/256/{z}/{x}/{y}.png?key=$encoded"],"tileSize":256,"maxzoom":20,"attribution":"© MapTiler © OpenStreetMap contributors"}},"layers":[{"id":"background","type":"background","paint":{"background-color":"#101416"}},{"id":"base","type":"raster","source":"base"}]}"""
    }
    const val EMPTY = """{"version":8,"sources":{},"layers":[{"id":"background","type":"background","paint":{"background-color":"#101416"}}]}"""
}
