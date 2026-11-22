package dev.hunterlab.app.radar

import android.content.Context
import dev.hunterlab.core.radar.PardalParser
import dev.hunterlab.core.radar.RadarCamera
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import org.json.JSONObject

internal data class OfflineRadarData(val cameras: List<RadarCamera>, val dateMs: Long, val label: String) {
    companion object { val EMPTY = OfflineRadarData(emptyList(), 0, "Base local indisponível") }
}

/** Lazily read on the repository IO executor, never on the navigation/UI thread. */
internal object PardalAssets {
    fun source(context: Context): () -> OfflineRadarData {
        val app = context.applicationContext
        val data by lazy {
            runCatching {
                val meta = app.assets.open("radar/provenance.json").bufferedReader().use { JSONObject(it.readText()) }
                val bytes = app.assets.open("radar/pardal-pe.csv").use { it.readBytes() }
                val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
                require(digest == meta.getString("csv_sha256"))
                val parsed = PardalParser.parse(bytes.toString(Charsets.UTF_8)) as? PardalParser.Result.Success
                    ?: error("Base local inválida")
                val date = Instant.parse(meta.getString("published"))
                val label = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneOffset.UTC).format(date)
                OfflineRadarData(parsed.cameras, date.toEpochMilli(), "Pardal PE · $label")
            }.getOrDefault(OfflineRadarData.EMPTY)
        }
        return { data }
    }
}
