package dev.hunterlab.app.settings

import dev.hunterlab.app.ui.contract.DestinationUi

/** Pure local bookmark schema. Reads legacy three-field rows and never stores transient GPS distance. */
internal object DestinationBookmarkCodec {
    private fun clean(text: String) = text.take(400).replace('\n', ' ').replace('|', '/')
    fun encode(d: DestinationUi): String = "${d.lat}|${d.lon}|${clean(d.label)}|${clean(d.detail.orEmpty())}"
    fun decode(line: String): DestinationUi? {
        val parts = line.split('|', limit = 4)
        if (parts.size !in 3..4) return null
        val lat = parts[0].toDoubleOrNull() ?: return null
        val lon = parts[1].toDoubleOrNull() ?: return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        return DestinationUi(parts[2], lat, lon, parts.getOrNull(3)?.takeIf { it.isNotBlank() })
    }
}
