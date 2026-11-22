package dev.hunterlab.app

import dev.hunterlab.app.radar.RadarRepository
import dev.hunterlab.core.geo.GeoPoint
import dev.hunterlab.core.geo.Polyline
import dev.hunterlab.core.radar.*
import dev.hunterlab.core.route.*
import java.io.File
import java.util.concurrent.Executor
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Actual repository/file IO on the JVM, temporary directories and fixture HTTP only. No Android/phone cache. */
class CaruaruRadarCacheTest {
    @get:Rule val temp = TemporaryFolder()
    private val direct = Executor { it.run() }
    private val route = Polyline(listOf(GeoPoint(-8.28430, -35.96830), GeoPoint(-8.28410, -35.96820)))
    private val tile = TileKey.of(route.points.first())
    private val body get() = File("../core/src/test/resources/validation/caruaru/urban.json").readText()
    private fun prepare(repo: RadarRepository, now: Long): RadarRepository.RouteRadarData {
        var result: RadarRepository.RouteRadarData? = null
        repo.prepare(route, { now }) { result = it }
        return checkNotNull(result)
    }

    @Test fun `cold fetch writes a cache that a new repository reads without network`() {
        val dir = temp.newFolder("fresh")
        var requests = 0
        val http = HttpGet { url, timeout ->
            requests++
            assertTrue(url.startsWith("https://overpass-api.de/api/interpreter?data="))
            assertEquals(40_000, timeout)
            // Captured public urban response, served by a fake port, not another live request.
            HttpResult.Ok(200, body)
        }
        val first = prepare(RadarRepository(dir, http, direct), 100_000)
        assertEquals(1, requests); assertEquals(1, first.downloaded)
        assertEquals(CoverageStatus.COMPLETE, first.coverage)
        val file = File(dir, "${tile.id}.json")
        assertTrue(file.isFile)
        val persisted = RadarTileData.deserialize(file.readText())
        assertEquals(37, persisted.cameras.size)
        val noNetwork = HttpGet { _, _ -> error("Fresh disk cache must not call network") }
        val warm = prepare(RadarRepository(dir, noNetwork, direct), 100_001)
        assertEquals(0, warm.downloaded); assertEquals(CoverageStatus.COMPLETE, warm.coverage)
        assertEquals(persisted.cameras, warm.tiles.single().cameras)
    }

    @Test fun `expired fourteen day disk cache survives offline refresh and is marked stale`() {
        val dir = temp.newFolder("expired")
        val cameras = (OverpassParser.parse(body) as OverpassParser.Result.Success).cameras.filter { TileKey.of(it.point) == tile }
        val file = File(dir, "${tile.id}.json")
        val original = RadarTileData(tile, 100_000, cameras).serialize()
        file.writeText(original)
        var requests = 0
        val offline = HttpGet { _, _ -> requests++; HttpResult.Failed(HttpFailureKind.NETWORK, "fixture offline") }
        val result = prepare(RadarRepository(dir, offline, direct), 100_000 + 14L*24*60*60*1_000 + 1)
        assertEquals(1, requests); assertEquals(1, result.failed)
        assertEquals(CoverageStatus.STALE, result.coverage)
        assertEquals(original, file.readText())
        assertEquals(cameras, result.tiles.single().cameras)
    }

    @Test fun `corrupt disk cache and failed HTTP produce no usable radar data`() {
        val dir = temp.newFolder("corrupt")
        val file = File(dir, "${tile.id}.json"); file.writeText("{invalid fixture")
        val failed = HttpGet { _, _ -> HttpResult.Ok(502, "fixture gateway error") }
        val result = prepare(RadarRepository(dir, failed, direct), 100_000)
        assertEquals(CoverageStatus.NONE, result.coverage)
        assertTrue(result.tiles.isEmpty()); assertFalse(file.exists())
    }
}
