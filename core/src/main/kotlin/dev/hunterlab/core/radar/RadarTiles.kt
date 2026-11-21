package dev.hunterlab.core.radar

import dev.hunterlab.core.geo.Geo
import dev.hunterlab.core.geo.GeoPoint
import dev.hunterlab.core.geo.Polyline
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Tile fixo de 0,2° × 0,2° (≈ 22 km × 20 km no Brasil). Índices = `floor(grau × 5)`.
 * [id] serve de nome de arquivo no cache (`-51_-243`).
 */
data class TileKey(val latIndex: Int, val lonIndex: Int) : Comparable<TileKey> {
    init {
        require(latIndex in MIN_LAT_INDEX..MAX_LAT_INDEX) { "Índice de latitude fora da faixa: $latIndex" }
        require(lonIndex in MIN_LON_INDEX..MAX_LON_INDEX) { "Índice de longitude fora da faixa: $lonIndex" }
    }

    val south: Double get() = latIndex / PER_DEGREE.toDouble()
    val north: Double get() = (latIndex + 1) / PER_DEGREE.toDouble()
    val west: Double get() = lonIndex / PER_DEGREE.toDouble()
    val east: Double get() = (lonIndex + 1) / PER_DEGREE.toDouble()
    val id: String get() = "${latIndex}_$lonIndex"

    fun contains(p: GeoPoint): Boolean = p.lat in south..north && p.lon in west..east

    fun overpassQuery(timeoutS: Int = 25): String = OverpassQuery.forTile(this, timeoutS)

    override fun compareTo(other: TileKey): Int = compareValuesBy(this, other, { it.latIndex }, { it.lonIndex })

    override fun toString(): String = "tile $id"

    companion object {
        const val SIZE_DEG = 0.2
        const val PER_DEGREE = 5
        const val MIN_LAT_INDEX = -90 * PER_DEGREE
        const val MAX_LAT_INDEX = 90 * PER_DEGREE - 1
        const val MIN_LON_INDEX = -180 * PER_DEGREE
        const val MAX_LON_INDEX = 180 * PER_DEGREE - 1
        const val DEFAULT_ROUTE_MARGIN_M = 1_000.0
        const val DEFAULT_MAX_TILES_PER_ROUTE = 12

        /** Passo de amostragem da rota ao calcular a cobertura (a margem é ampliada em metade dele). */
        const val ROUTE_SAMPLE_STEP_M = 500.0

        private const val M_PER_DEG_LAT = Geo.EARTH_RADIUS_M * PI / 180.0
        private const val INDEX_EPS = 1e-9

        fun of(p: GeoPoint): TileKey = TileKey(latIndexOf(p.lat), lonIndexOf(p.lon))

        fun parse(id: String): TileKey? {
            val parts = id.split('_')
            if (parts.size != 2) return null
            val lat = parts[0].toIntOrNull() ?: return null
            val lon = parts[1].toIntOrNull() ?: return null
            return runCatching { TileKey(lat, lon) }.getOrNull()
        }

        /** Todos os tiles que intersectam o retângulo (sul, oeste, norte, leste). */
        fun coverBox(south: Double, west: Double, north: Double, east: Double): List<TileKey> {
            require(south <= north && west <= east) { "bbox invertida" }
            // Folga mínima (≈ 0,02 mm) para bordas exatas não puxarem o tile vizinho por arredondamento.
            val s = floor(south * PER_DEGREE + INDEX_EPS).toInt().coerceIn(MIN_LAT_INDEX, MAX_LAT_INDEX)
            val n = (ceil(north * PER_DEGREE - INDEX_EPS).toInt() - 1).coerceIn(s, MAX_LAT_INDEX)
            val w = floor(west * PER_DEGREE + INDEX_EPS).toInt().coerceIn(MIN_LON_INDEX, MAX_LON_INDEX)
            val e = (ceil(east * PER_DEGREE - INDEX_EPS).toInt() - 1).coerceIn(w, MAX_LON_INDEX)
            return (s..n).flatMap { lat -> (w..e).map { lon -> TileKey(lat, lon) } }
        }

        /** Tiles do retângulo envolvente da rota inteira, com [marginM] (sem limite; prefira [coverRoute]). */
        fun coverRouteBoundingBox(route: Polyline, marginM: Double = DEFAULT_ROUTE_MARGIN_M): List<TileKey> {
            val pts = route.points
            val south = pts.minOf { it.lat }
            val north = pts.maxOf { it.lat }
            val west = pts.minOf { it.lon }
            val east = pts.maxOf { it.lon }
            val maxAbsLat = max(abs(south), abs(north))
            val dLat = marginM / M_PER_DEG_LAT
            val dLon = marginM / (M_PER_DEG_LAT * max(cos(maxAbsLat * PI / 180.0), 0.01))
            return coverBox(
                (south - dLat).coerceAtLeast(-90.0), (west - dLon).coerceAtLeast(-180.0),
                (north + dLat).coerceAtMost(90.0), (east + dLon).coerceAtMost(180.0),
            )
        }

        /**
         * Tiles do **corredor** da rota (cada ponto a até [marginM] da linha), na ordem em que a rota os encontra.
         * Nunca usa mais tiles que o retângulo envolvente e, numa rota diagonal, usa bem menos.
         * Acima de [maxTiles], fica com os primeiros (início da rota) e declara cobertura parcial.
         */
        fun coverRoute(
            route: Polyline,
            marginM: Double = DEFAULT_ROUTE_MARGIN_M,
            maxTiles: Int = DEFAULT_MAX_TILES_PER_ROUTE,
        ): RouteTileCoverage {
            require(marginM >= 0 && marginM.isFinite()) { "Margem inválida: $marginM" }
            require(maxTiles >= 1) { "Limite de tiles deve ser positivo" }
            val half = marginM + ROUTE_SAMPLE_STEP_M / 2
            val ordered = LinkedHashSet<TileKey>()
            val samples = mutableListOf<Pair<Double, List<TileKey>>>()
            var s = 0.0
            while (true) {
                val tiles = around(route.pointAt(s), half)
                samples += s to tiles
                ordered += tiles
                if (s >= route.lengthM) break
                s = min(s + ROUTE_SAMPLE_STEP_M, route.lengthM)
            }
            val selected = ordered.take(maxTiles)
            val coveredUntil = if (selected.size == ordered.size) {
                route.lengthM
            } else {
                val set = selected.toHashSet()
                val firstMissing = samples.indexOfFirst { !set.containsAll(it.second) }
                if (firstMissing <= 0) 0.0 else samples[firstMissing - 1].first
            }
            return RouteTileCoverage(selected, ordered.size, coveredUntil, route.lengthM)
        }

        private fun around(p: GeoPoint, halfM: Double): List<TileKey> {
            val dLat = halfM / M_PER_DEG_LAT
            val dLon = halfM / (M_PER_DEG_LAT * max(cos(p.lat * PI / 180.0), 0.01))
            return coverBox(
                (p.lat - dLat).coerceAtLeast(-90.0), (p.lon - dLon).coerceAtLeast(-180.0),
                (p.lat + dLat).coerceAtMost(90.0), (p.lon + dLon).coerceAtMost(180.0),
            )
        }

        private fun latIndexOf(lat: Double): Int = floor(lat * PER_DEGREE).toInt().coerceIn(MIN_LAT_INDEX, MAX_LAT_INDEX)

        private fun lonIndexOf(lon: Double): Int = floor(lon * PER_DEGREE).toInt().coerceIn(MIN_LON_INDEX, MAX_LON_INDEX)
    }
}

/**
 * Tiles escolhidos para uma rota. [partial] quando o limite cortou tiles necessários; nesse caso a rota tem
 * dados garantidos só até [coveredUntilM] metros do início.
 */
data class RouteTileCoverage(
    val tiles: List<TileKey>,
    val requiredTileCount: Int,
    val coveredUntilM: Double,
    val routeLengthM: Double,
) {
    val partial: Boolean get() = tiles.size < requiredTileCount
}

/** Radares de um tile, como baixados. Serializado em JSON versionado ([SCHEMA_VERSION]). */
data class RadarTileData(
    val tile: TileKey,
    val fetchedAtMs: Long,
    val cameras: List<RadarCamera>,
    val attribution: String = OSM_ATTRIBUTION,
) {
    fun serialize(): String = buildJsonObject {
        put("schema", SCHEMA_VERSION)
        put("tile", tile.id)
        put("fetchedAtMs", fetchedAtMs)
        put("attribution", attribution)
        putJsonArray("cameras") {
            cameras.forEach { c ->
                add(
                    buildJsonObject {
                        put("id", c.id)
                        put("lat", c.point.lat)
                        put("lon", c.point.lon)
                        put("kind", c.kind.name)
                        put("maxSpeedKmh", c.maxSpeedKmh)
                        put("directionDeg", c.directionDeg)
                        put("directionKnown", c.directionKnown)
                        put("source", c.source)
                    },
                )
            }
        }
    }.toString()

    companion object {
        const val SCHEMA_VERSION = 1
        const val OSM_ATTRIBUTION = "© colaboradores do OpenStreetMap (ODbL)"

        /** Lê o JSON de [serialize]. Lança [IllegalArgumentException] se malformado ou com `schema` desconhecido. */
        fun deserialize(text: String): RadarTileData {
            val root = try {
                Json.parseToJsonElement(text)
            } catch (e: Exception) {
                throw IllegalArgumentException("Cache de radar com JSON inválido", e)
            }
            val obj = root as? JsonObject ?: throw IllegalArgumentException("Cache de radar não é um objeto JSON")
            val schema = obj.prim("schema")?.intOrNull ?: throw IllegalArgumentException("Cache de radar sem \"schema\"")
            require(schema == SCHEMA_VERSION) { "Versão de esquema desconhecida: $schema (esperada $SCHEMA_VERSION)" }
            val tile = obj.prim("tile")?.contentOrNull?.let(TileKey::parse)
                ?: throw IllegalArgumentException("Cache de radar com tile inválido")
            val fetchedAt = obj.prim("fetchedAtMs")?.longOrNull
                ?: throw IllegalArgumentException("Cache de radar sem fetchedAtMs")
            val attribution = obj.prim("attribution")?.contentOrNull ?: OSM_ATTRIBUTION
            val arr = obj["cameras"] as? JsonArray ?: throw IllegalArgumentException("Cache de radar sem lista de câmeras")
            val cameras = arr.mapIndexed { i, el ->
                try {
                    readCamera(el as JsonObject)
                } catch (e: Exception) {
                    throw IllegalArgumentException("Câmera $i inválida no cache: ${e.message}", e)
                }
            }
            return RadarTileData(tile, fetchedAt, cameras, attribution)
        }

        /** Junta as câmeras de vários tiles sem repetir o mesmo id (fica a do tile baixado mais recentemente). */
        fun mergeCameras(tiles: Collection<RadarTileData>): List<RadarCamera> {
            val byId = LinkedHashMap<String, RadarCamera>()
            tiles.sortedByDescending { it.fetchedAtMs }.forEach { t -> t.cameras.forEach { byId.putIfAbsent(it.id, it) } }
            return byId.values.sortedBy { it.id }
        }

        private fun readCamera(o: JsonObject): RadarCamera {
            val direction = o.prim("directionDeg")?.doubleOrNull
            return RadarCamera(
                id = o.prim("id")?.contentOrNull ?: error("sem id"),
                point = GeoPoint(o.prim("lat")?.doubleOrNull ?: error("sem lat"), o.prim("lon")?.doubleOrNull ?: error("sem lon")),
                kind = RadarKind.valueOf(o.prim("kind")?.contentOrNull ?: error("sem kind")),
                maxSpeedKmh = o.prim("maxSpeedKmh")?.intOrNull,
                directionDeg = direction,
                directionKnown = o.prim("directionKnown")?.contentOrNull?.toBooleanStrictOrNull() ?: (direction != null),
                source = o.prim("source")?.contentOrNull ?: RadarCamera.SOURCE_OSM,
            )
        }

        private fun JsonObject.prim(key: String): JsonPrimitive? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }
    }
}

/** Situação de um tile para a política de atualização. */
enum class TileFreshness { FRESH, STALE, MISSING }

/** O que o app sabe de um tile: dado em cache (se houver) e a última tentativa de download. */
data class RadarTileState(
    val tile: TileKey,
    val data: RadarTileData? = null,
    val lastAttemptMs: Long? = null,
    val lastAttemptFailed: Boolean = false,
) {
    init {
        require(data == null || data.tile == tile) { "Dados de outro tile" }
    }
}

/**
 * Política pura de cache. Validade padrão 30 dias; no máximo um download por tile a cada 1 h (sucesso ou falha).
 * Após falha o dado antigo é **mantido** e tratado como desatualizado até um download dar certo.
 */
class RadarUpdatePolicy(
    val validityMs: Long = DEFAULT_VALIDITY_MS,
    val minFetchIntervalMs: Long = DEFAULT_MIN_FETCH_INTERVAL_MS,
) {
    init {
        require(validityMs > 0 && minFetchIntervalMs >= 0) { "Intervalos inválidos" }
    }

    fun freshness(state: RadarTileState, nowMs: Long): TileFreshness {
        val data = state.data ?: return TileFreshness.MISSING
        if (state.lastAttemptFailed) return TileFreshness.STALE
        val age = nowMs - data.fetchedAtMs
        // Data no futuro além da tolerância = relógio suspeito → desatualizado.
        return if (age in -CLOCK_SKEW_TOLERANCE_MS..validityMs) TileFreshness.FRESH else TileFreshness.STALE
    }

    /** Respeita o intervalo mínimo entre downloads do mesmo tile (relógio que voltou atrás libera). */
    fun canFetchNow(state: RadarTileState, nowMs: Long): Boolean {
        val last = state.lastAttemptMs ?: return true
        return nowMs < last || nowMs - last >= minFetchIntervalMs
    }

    /** Baixar se faltar dado ou estiver desatualizado ([force] ignora a validade, nunca o intervalo mínimo). */
    fun shouldFetch(state: RadarTileState, nowMs: Long, force: Boolean = false): Boolean =
        (force || freshness(state, nowMs) != TileFreshness.FRESH) && canFetchNow(state, nowMs)

    fun onFetchSuccess(state: RadarTileState, data: RadarTileData, nowMs: Long): RadarTileState =
        state.copy(data = data, lastAttemptMs = nowMs, lastAttemptFailed = false)

    /** Falha: mantém o dado antigo (se houver), que passa a contar como desatualizado. */
    fun onFetchFailure(state: RadarTileState, nowMs: Long): RadarTileState =
        state.copy(lastAttemptMs = nowMs, lastAttemptFailed = true)

    fun tilesToFetch(coverage: RouteTileCoverage, states: Map<TileKey, RadarTileState>, nowMs: Long): List<TileKey> =
        coverage.tiles.filter { shouldFetch(states[it] ?: RadarTileState(it), nowMs) }

    /** NONE (nenhum tile com dado) > PARTIAL (faltam tiles ou limite cortou) > STALE > COMPLETE. */
    fun coverageOf(coverage: RouteTileCoverage, states: Map<TileKey, RadarTileState>, nowMs: Long): CoverageStatus {
        val f = coverage.tiles.map { freshness(states[it] ?: RadarTileState(it), nowMs) }
        return when {
            f.all { it == TileFreshness.MISSING } -> CoverageStatus.NONE
            coverage.partial || TileFreshness.MISSING in f -> CoverageStatus.PARTIAL
            TileFreshness.STALE in f -> CoverageStatus.STALE
            else -> CoverageStatus.COMPLETE
        }
    }

    companion object {
        const val DEFAULT_VALIDITY_MS: Long = 30L * 24 * 60 * 60 * 1000
        const val DEFAULT_MIN_FETCH_INTERVAL_MS: Long = 60L * 60 * 1000
        const val CLOCK_SKEW_TOLERANCE_MS: Long = 24L * 60 * 60 * 1000
    }
}

/** Cobertura de radares da rota atual, com texto curto para a interface. */
enum class CoverageStatus(val label: String) {
    COMPLETE("Radares atualizados"),
    STALE("Radares desatualizados"),
    PARTIAL("Cobertura parcial de radares"),
    NONE("Sem dados de radar"),
}
