package dev.hunterlab.core.radar

import dev.hunterlab.core.geo.Geo
import dev.hunterlab.core.geo.GeoPoint
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import java.util.Locale

/** Consulta Overpass QL (saída JSON) para radares fixos do OpenStreetMap. Só monta o texto; o app faz a rede. */
object OverpassQuery {
    /** Valores de `enforcement=*` aceitos (o parser trata exatamente estes). */
    val ENFORCEMENT_VALUES: List<String> = listOf("maxspeed", "average_speed", "traffic_signals")

    /**
     * Nós `highway=speed_camera` e relações `type=enforcement` com `enforcement` ∈ [ENFORCEMENT_VALUES]
     * (também em listas `a;b`), mais os membros com coordenadas (`out body; >; out body qt;`).
     * bbox na ordem Overpass: sul, oeste, norte, leste. Não atravessa o antimeridiano.
     */
    fun forBox(south: Double, west: Double, north: Double, east: Double, timeoutS: Int = 25): String {
        require(south.isFinite() && north.isFinite() && west.isFinite() && east.isFinite()) { "bbox com valor inválido" }
        require(south >= -90.0 && north <= 90.0 && south < north) { "Latitudes inválidas: $south..$north" }
        require(west >= -180.0 && east <= 180.0 && west < east) { "Longitudes inválidas: $west..$east" }
        require(timeoutS in 1..900) { "Timeout fora da faixa: $timeoutS" }
        val bbox = listOf(south, west, north, east).joinToString(",") { "%.6f".format(Locale.ROOT, it) }
        val enforcement = "(^|;)(" + ENFORCEMENT_VALUES.joinToString("|") + ")(;|$)"
        return buildString {
            append("[out:json][timeout:").append(timeoutS).append("];\n")
            append("(\n")
            append("  node[\"highway\"=\"speed_camera\"](").append(bbox).append(");\n")
            append("  relation[\"type\"=\"enforcement\"][\"enforcement\"~\"").append(enforcement).append("\"](")
                .append(bbox).append(");\n")
            append(");\n")
            append("out body;\n")
            append(">;\n")
            append("out body qt;\n")
        }
    }

    fun forTile(tile: TileKey, timeoutS: Int = 25): String = forBox(tile.south, tile.west, tile.north, tile.east, timeoutS)
}

/**
 * Converte a resposta JSON do Overpass em [RadarCamera].
 *
 * Semântica:
 * - nó `highway=speed_camera` → [RadarKind.FIXED] (maxspeed/direction do próprio nó);
 * - relação `enforcement=maxspeed` → FIXED no membro `device` (ou `from` se não houver device), maxspeed da relação
 *   (ou do nó do dispositivo);
 * - `enforcement=average_speed` → [RadarKind.AVERAGE_SPEED] no membro `from` (início do trecho; senão `device`);
 * - `enforcement=traffic_signals` → [RadarKind.TRAFFIC_LIGHT] no membro `device` (ou `from`).
 *
 * `direction` numérica é orientação da câmera, sem equivalência presumida ao fluxo fiscalizado.
 * Somente uma geometria curta from→to inequívoca confirma rumo de tráfego; conflitos mantêm rumo desconhecido.
 * Nó e relação conservam suas evidências de limite: divergência ou condição impede expor um número.
 */
object OverpassParser {
    const val DERIVED_DIRECTION_MIN_M = 5.0
    const val DERIVED_DIRECTION_MAX_M = 200.0
    private const val MAX_WARNINGS = 50

    enum class ErrorCode { INVALID_RESPONSE }

    sealed interface Result {
        data class Success(val cameras: List<RadarCamera>, val warnings: List<String>) : Result
        data class Failure(val code: ErrorCode, val message: String) : Result
    }

    private data class Member(val type: String, val ref: Long, val role: String)
    private data class Relation(val id: Long, val members: List<Member>, val tags: Map<String, String>)
    private data class Candidate(
        val nodeId: Long,
        val kind: RadarKind,
        val limits: List<RadarLimitObservation>,
        val directionDeg: Double?,
        val fromRelation: Boolean,
        val facingDeg: Double? = null,
    )

    fun parse(body: String): Result {
        val root = try {
            Json.parseToJsonElement(body)
        } catch (e: Exception) {
            return invalid("JSON inválido: ${e.message?.lineSequence()?.firstOrNull()?.take(120) ?: e::class.simpleName}")
        }
        val obj = root as? JsonObject ?: return invalid("A resposta não é um objeto JSON")
        val remark = (obj["remark"] as? JsonPrimitive)?.contentOrNull
        if (remark != null && remark.contains("error", ignoreCase = true)) {
            return invalid("Overpass informou erro: ${remark.take(160)}")
        }
        val elements = obj["elements"] as? JsonArray ?: return invalid("Campo \"elements\" ausente ou inválido")

        val warnings = mutableListOf<String>()
        fun warn(msg: String) {
            if (warnings.size < MAX_WARNINGS) warnings += msg
        }

        val coords = HashMap<Long, GeoPoint>()
        val nodeTags = HashMap<Long, Map<String, String>>()
        val relations = mutableListOf<Relation>()

        for (el in elements) {
            val o = el as? JsonObject
            val type = o?.string("type")
            val id = o?.long("id")
            if (o == null || type == null || id == null) {
                warn("Elemento sem tipo/id ignorado")
                continue
            }
            when (type) {
                "node" -> {
                    val tags = o.tags()
                    if (tags.isNotEmpty()) nodeTags[id] = (nodeTags[id].orEmpty() + tags)
                    val point = o.point()
                    if (point != null) coords[id] = point
                }
                "relation" -> relations += Relation(id, o.members(), o.tags())
            }
        }

        val candidates = mutableListOf<Candidate>()

        for ((id, tags) in nodeTags) {
            if (tags["highway"] != "speed_camera") continue
            if (id !in coords) {
                warn("Nó $id (speed_camera) sem coordenadas: ignorado")
                continue
            }
            val direction = OsmTagValues.parseDirection(tags["direction"])
            noteDirection(direction, "nó $id", ::warn)
            candidates += Candidate(
                nodeId = id,
                kind = RadarKind.FIXED,
                limits = listOf(limit(tags, "OSM node/$id", ::warn)),
                // OSM direction is camera-facing orientation, NOT a proven traffic heading.
                directionDeg = null,
                facingDeg = (direction as? OsmDirection.Degrees)?.deg,
                fromRelation = false,
            )
        }

        for (rel in relations) {
            if (rel.tags["type"] != "enforcement") continue
            val kind = enforcementKind(rel.tags["enforcement"]) ?: continue
            fun node(role: String): Long? =
                rel.members.firstOrNull { it.type == "node" && it.role == role && it.ref in coords }?.ref
            val pointId = when (kind) {
                RadarKind.AVERAGE_SPEED -> node("from") ?: node("device")
                else -> node("device") ?: node("from")
            }
            if (pointId == null) {
                warn("Relação ${rel.id} (${rel.tags["enforcement"]}) sem membro device/from com coordenadas: ignorada")
                continue
            }
            val pointTags = nodeTags[pointId].orEmpty()
            val relDirection = OsmTagValues.parseDirection(rel.tags["direction"])
            val direction = if (relDirection == OsmDirection.Absent) {
                OsmTagValues.parseDirection(pointTags["direction"])
            } else {
                relDirection
            }
            noteDirection(direction, "relação ${rel.id}", ::warn)
            // Only relation geometry establishes a traffic direction in the OSM import.
            val deg = if (direction == OsmDirection.Both) null else derivedDirection(node("from"), node("to"), coords)
            candidates += Candidate(
                nodeId = pointId,
                kind = kind,
                limits = listOf(limit(pointTags, "OSM node/$pointId", ::warn), limit(rel.tags, "OSM relation/${rel.id}", ::warn)),
                directionDeg = deg,
                fromRelation = true,
            )
        }

        val cameras = candidates.groupBy { it.nodeId }.map { (nodeId, group) ->
            val ordered = group.sortedByDescending { it.fromRelation }
            val deg = ordered.mapNotNull { it.directionDeg }.distinct().singleOrNull()
            RadarCamera(
                id = RadarCamera.osmNodeId(nodeId),
                point = coords.getValue(nodeId),
                kind = group.maxBy { kindPriority(it.kind) }.kind,
                speedLimit = RadarSpeedLimit.resolve(group.flatMap { it.limits }),
                directionDeg = deg,
                directionKnown = deg != null,
                facingDirectionDeg = ordered.firstNotNullOfOrNull { it.facingDeg },
            )
        }.sortedBy { it.id.removePrefix("osm:node/").toLongOrNull() ?: Long.MAX_VALUE }

        return Result.Success(cameras, warnings)
    }

    private fun limit(tags: Map<String, String>, source: String, warn: (String) -> Unit): RadarLimitObservation {
        speed(tags["maxspeed"], source, warn) // Preserve parser diagnostics for unsupported values.
        // Directional/conditional variants cannot be reduced to one unconditional number here.
        val unresolved = tags.filterKeys {
            it.startsWith("maxspeed:") && (it.endsWith(":conditional") ||
                it == "maxspeed:forward" || it == "maxspeed:backward" || it == "maxspeed:lanes")
        }.entries.joinToString("; ") { "${it.key}=${it.value}" }.ifBlank { null }
        return RadarLimitObservation(source, tags["maxspeed:motorcycle"] ?: tags["maxspeed"], unresolved)
    }

    private fun invalid(message: String) = Result.Failure(ErrorCode.INVALID_RESPONSE, message)

    private fun enforcementKind(raw: String?): RadarKind? {
        val values = raw?.split(';')?.map { it.trim() }.orEmpty()
        return values.mapNotNull {
            when (it) {
                "maxspeed" -> RadarKind.FIXED
                "average_speed" -> RadarKind.AVERAGE_SPEED
                "traffic_signals" -> RadarKind.TRAFFIC_LIGHT
                else -> null
            }
        }.maxByOrNull(::kindPriority)
    }

    private fun kindPriority(kind: RadarKind): Int = when (kind) {
        RadarKind.AVERAGE_SPEED -> 3
        RadarKind.FIXED -> 2
        RadarKind.TRAFFIC_LIGHT -> 1
        RadarKind.MOBILE -> 0
    }

    private fun derivedDirection(from: Long?, to: Long?, coords: Map<Long, GeoPoint>): Double? {
        val a = from?.let(coords::get) ?: return null
        val b = to?.let(coords::get) ?: return null
        val d = Geo.distanceM(a, b)
        return if (d in DERIVED_DIRECTION_MIN_M..DERIVED_DIRECTION_MAX_M) Geo.bearingDeg(a, b) else null
    }

    private fun speed(raw: String?, where: String, warn: (String) -> Unit): Int? {
        val v = OsmTagValues.parseMaxSpeedKmh(raw)
        if (v == null && !raw.isNullOrBlank()) warn("maxspeed \"${raw.take(30)}\" não numérico ($where): limite desconhecido")
        return v
    }

    private fun noteDirection(d: OsmDirection, where: String, warn: (String) -> Unit) {
        if (d is OsmDirection.Unrecognized) warn("direction \"${d.raw.take(30)}\" não reconhecida ($where): sentido desconhecido")
    }

    // --- leitura tolerante da árvore JSON ---

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull

    private fun JsonObject.point(): GeoPoint? {
        val lat = (this["lat"] as? JsonPrimitive)?.doubleOrNull ?: return null
        val lon = (this["lon"] as? JsonPrimitive)?.doubleOrNull ?: return null
        return runCatching { GeoPoint(lat, lon) }.getOrNull()
    }

    private fun JsonObject.tags(): Map<String, String> {
        val t = this["tags"] as? JsonObject ?: return emptyMap()
        return t.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.contentOrNull?.let { k to it } }.toMap()
    }

    private fun JsonObject.members(): List<Member> {
        val arr = this["members"] as? JsonArray ?: return emptyList()
        return arr.mapNotNull { m ->
            val o = m as? JsonObject ?: return@mapNotNull null
            val type = o.string("type") ?: return@mapNotNull null
            val ref = o.long("ref") ?: return@mapNotNull null
            Member(type, ref, o.string("role").orEmpty())
        }
    }
}
