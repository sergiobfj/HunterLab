package dev.hunterlab.core.radar

import dev.hunterlab.core.geo.GeoPoint
import java.util.Locale

/** Pinned ODbL data, not an official live equipment inventory. No network or executable content. */
object PardalParser {
    sealed interface Result {
        data class Success(val cameras: List<RadarCamera>, val inactive: Int, val invalid: Int) : Result
        data class Failure(val message: String) : Result
    }

    fun parse(text: String): Result {
        if (text.length > 2_000_000) return Result.Failure("Arquivo Pardal acima do limite")
        val rows = runCatching { csv(text.removePrefix("\uFEFF")) }.getOrNull()
            ?: return Result.Failure("CSV Pardal inválido")
        val required = listOf("lat", "lng", "kind", "limit_kmh", "source", "active", "end_lat", "end_lng", "direction_deg")
        val header = rows.firstOrNull() ?: return Result.Failure("CSV Pardal vazio")
        if (header.toSet() != required.toSet() || header.size != required.size) return Result.Failure("Colunas Pardal não reconhecidas")
        var inactive = 0
        var invalid = 0
        val cameras = rows.drop(1).mapNotNull { fields ->
            if (fields.size != header.size) { invalid++; return@mapNotNull null }
            val row = header.zip(fields).toMap()
            if (row["active"] == "0") { inactive++; return@mapNotNull null }
            if (row["active"] != "1") { invalid++; return@mapNotNull null }
            runCatching {
                val point = GeoPoint(row.getValue("lat").toDouble(), row.getValue("lng").toDouble())
                val kind = when (row["kind"]) {
                    "FIXED" -> RadarKind.FIXED
                    "RED_LIGHT" -> RadarKind.TRAFFIC_LIGHT
                    // Existing engine alerts individual points; section enforcement needs separate semantics.
                    else -> error("Tipo não suportado")
                }
                val source = row.getValue("source").trim().also { require(it.isNotBlank()) }
                val rawDirection = row.getValue("direction_deg").trim()
                val direction = rawDirection.takeIf { it.isNotBlank() }?.toDouble()?.also { require(it.isFinite() && it in 0.0..<360.0) }
                val id = String.format(Locale.ROOT, "pardal:PE:%.6f:%.6f:%s:%s", point.lat, point.lon, kind.name, rawDirection)
                RadarCamera(id, point, kind,
                    RadarSpeedLimit.resolve(listOf(RadarLimitObservation("Pardal/$source", row["limit_kmh"]))),
                    direction, source = "Pardal/$source")
            }.getOrElse { invalid++; null }
        }
        val deduped = cameras.groupBy { it.id }.map { (_, group) ->
            group.first().copy(speedLimit = RadarSpeedLimit.resolve(group.flatMap { it.speedLimit.observations }))
        }
        return Result.Success(deduped, inactive, invalid)
    }

    /** Bounded RFC-4180-style records; supports quoted commas, escaped quotes and CRLF. */
    private fun csv(text: String): List<List<String>> {
        val records = mutableListOf<List<String>>()
        var fields = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        fun endField() { fields += field.toString().trim(); field.clear() }
        fun endRow() {
            endField()
            if (fields.any { it.isNotEmpty() }) records += fields
            fields = mutableListOf()
            require(records.size <= 20_000)
        }
        while (i < text.length) {
            val c = text[i++]
            if (c == '"') {
                if (quoted && i < text.length && text[i] == '"') { field.append('"'); i++ }
                else { require(quoted || field.isEmpty()); quoted = !quoted }
            } else if (quoted) field.append(c)
            else when (c) {
                ',' -> endField()
                '\n' -> endRow()
                '\r' -> { if (i < text.length && text[i] == '\n') i++; endRow() }
                else -> field.append(c)
            }
            require(field.length <= 2_000)
        }
        require(!quoted)
        if (field.isNotEmpty() || fields.isNotEmpty()) endRow()
        return records
    }
}
