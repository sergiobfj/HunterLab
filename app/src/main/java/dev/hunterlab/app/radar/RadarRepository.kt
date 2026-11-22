package dev.hunterlab.app.radar

import android.content.Context
import dev.hunterlab.core.geo.Polyline
import dev.hunterlab.core.radar.CoverageStatus
import dev.hunterlab.core.radar.OverpassParser
import dev.hunterlab.core.radar.RadarFusion
import dev.hunterlab.core.radar.RadarSpeedLimit
import dev.hunterlab.core.radar.RadarTileData
import dev.hunterlab.core.radar.RadarTileState
import dev.hunterlab.core.radar.RadarUpdatePolicy
import dev.hunterlab.core.radar.TileKey
import dev.hunterlab.core.route.HttpGet
import dev.hunterlab.core.route.HttpResult
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicLong

/**
 * Radares fixos do OpenStreetMap via Overpass, com cache local por tile (`files/radar/<tile>.json`).
 * Único lugar do app que grava arquivos (verificado pelo SafetyGuardTest).
 *
 * Política (docs/RADAR_DATA.md): um download por tile só quando o dado está ausente ou vencido, no máximo um a
 * cada hora por tile, sequencial e com pausa entre pedidos; nunca a cada atualização do GPS. Falha mantém o dado
 * antigo como "desatualizado". Sem dados, a navegação não emite alertas e diz isso.
 */
class RadarRepository internal constructor(
    private val dir: File,
    private val http: HttpGet,
    private val io: Executor,
    private val policy: RadarUpdatePolicy = RadarUpdatePolicy(validityMs = VALIDITY_MS),
    private val offlineSource: () -> OfflineRadarData = { OfflineRadarData.EMPTY },
) {
    constructor(context: Context, http: HttpGet, io: Executor) : this(
        File(context.applicationContext.filesDir, "radar"), http, io, offlineSource = PardalAssets.source(context),
    )

    private val states = ConcurrentHashMap<TileKey, RadarTileState>()
    private val generation = AtomicLong()
    @Volatile var dataSourceLabel: String = "OpenStreetMap"
        private set

    /** Interrompe a fila da rota. Um HTTP já em curso termina pelo timeout e ainda pode alimentar o cache. */
    fun cancel() { generation.incrementAndGet() }

    data class RouteRadarData(val tiles: List<RadarTileData>, val coverage: CoverageStatus, val downloaded: Int, val failed: Int)

    /** Carrega do cache e baixa o que faltar para a rota, em segundo plano. [done] é chamado na thread de [io]. */
    fun prepare(route: Polyline, nowMs: () -> Long, done: (RouteRadarData) -> Unit) {
        val request = generation.incrementAndGet()
        io.execute {
            if (generation.get() != request) return@execute
            val offline = offlineSource()
            if (generation.get() != request) return@execute
            dataSourceLabel = if (offline.cameras.isNotEmpty()) "OpenStreetMap · ${offline.label}" else "OpenStreetMap"
            val cover = TileKey.coverRoute(route)
            cover.tiles.forEach { loadFromDisk(it) }
            var downloaded = 0
            var failed = 0
            for (tile in policy.tilesToFetch(cover, states, nowMs())) {
                if (generation.get() != request) return@execute
                if (failed >= MAX_FAILURES_PER_ROUTE) break
                if (fetch(tile, nowMs())) downloaded++ else failed++
                if (generation.get() != request) return@execute
                Thread.sleep(PAUSE_BETWEEN_REQUESTS_MS)
            }
            if (generation.get() != request) return@execute
            val now = nowMs()
            var coverage = policy.coverageOf(cover, states, now)
            val offlineStale = now - offline.dateMs !in 0..VALIDITY_MS
            val allowedTiles = cover.tiles.toSet()
            val osm = cover.tiles.flatMap { tile ->
                val data = states[tile]?.data
                val cameras = data?.cameras.orEmpty().filter { TileKey.of(it.point) in allowedTiles }
                if (states[tile]?.let { policy.freshness(it, now) } == dev.hunterlab.core.radar.TileFreshness.STALE)
                    cameras.map { camera -> camera.copy(speedLimit = RadarSpeedLimit.resolve(camera.speedLimit.observations.map { it.copy(usable = false) })) }
                else cameras
            }
            val local = offline.cameras.filter { camera -> TileKey.of(camera.point) in allowedTiles }
            val usableLocal = if (offlineStale) local.map { camera -> camera.copy(
                speedLimit = RadarSpeedLimit.resolve(camera.speedLimit.observations.map { it.copy(usable = false) })) }
                else local
            // Reconcile before splitting into tiles: a border must not duplicate one device.
            val combined = RadarFusion.merge(osm, usableLocal)
            val tiles = cover.tiles.mapNotNull { tile ->
                val data = states[tile]?.data
                val cameras = combined.filter { TileKey.of(it.point) == tile }
                if (data == null && cameras.isEmpty()) null else RadarTileData(tile, data?.fetchedAtMs ?: offline.dateMs, cameras)
            }
            if (tiles.isNotEmpty() && coverage == CoverageStatus.NONE) coverage = if (offlineStale) CoverageStatus.STALE else CoverageStatus.PARTIAL
            if (offlineStale && tiles.any { t -> t.cameras.any { it.source.contains("Pardal/") } }) coverage = CoverageStatus.STALE
            done(RouteRadarData(tiles, coverage, downloaded, failed))
        }
    }

    private fun loadFromDisk(tile: TileKey) {
        if (states[tile]?.data != null) return
        val f = File(dir, "${tile.id}.json")
        if (!f.isFile) return
        val data = runCatching { RadarTileData.deserialize(f.readText()) }.getOrNull()
        if (data == null || data.tile != tile) {
            f.delete()
            return
        }
        states[tile] = (states[tile] ?: RadarTileState(tile)).copy(data = data)
    }

    private fun fetch(tile: TileKey, now: Long): Boolean {
        val state = states[tile] ?: RadarTileState(tile)
        val url = OVERPASS_URL + URLEncoder.encode(tile.overpassQuery(), "UTF-8")
        val result = http.get(url, TIMEOUT_MS)
        val body = (result as? HttpResult.Ok)?.takeIf { it.code == 200 }?.body
        val parsed = body?.let { OverpassParser.parse(it) as? OverpassParser.Result.Success }
        if (parsed == null) {
            states[tile] = policy.onFetchFailure(state, now)
            return false
        }
        val data = RadarTileData(tile, now, parsed.cameras)
        states[tile] = policy.onFetchSuccess(state, data, now)
        runCatching {
            dir.mkdirs()
            val tmp = File(dir, "${tile.id}.json.tmp")
            tmp.writeText(data.serialize())
            tmp.renameTo(File(dir, "${tile.id}.json"))
        }
        return true
    }

    /** Apaga o cache local (Configurações). */
    fun clear() {
        states.clear()
        dir.listFiles()?.forEach { it.delete() }
    }

    private companion object {
        const val OVERPASS_URL = "https://overpass-api.de/api/interpreter?data="
        const val TIMEOUT_MS = 40_000
        const val PAUSE_BETWEEN_REQUESTS_MS = 1_500L
        const val MAX_FAILURES_PER_ROUTE = 2
        const val VALIDITY_MS = 14L * 24 * 60 * 60 * 1000
    }
}
