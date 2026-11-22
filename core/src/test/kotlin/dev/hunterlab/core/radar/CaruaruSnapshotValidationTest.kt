package dev.hunterlab.core.radar

import dev.hunterlab.core.geo.*
import dev.hunterlab.core.route.*
import kotlin.math.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Public map snapshots fetched 2026-11-22. Routes/progress below are synthetic, never GPS or BLE. */
class CaruaruSnapshotValidationTest {
    private fun resource(name: String) = checkNotNull(javaClass.getResource("/validation/caruaru/$name.json")).readText()
    private fun cameras(name: String) = (OverpassParser.parse(resource(name)) as OverpassParser.Result.Success).cameras
    private val known by lazy { cameras("urban").single { it.id == "osm:node/12791592886" } }

    /** A local straight line through a real mapped camera, NOT a surveyed or routable street. */
    private fun at(meters: Double, heading: Double = 200.0): GeoPoint {
        val rad = Math.toRadians(heading)
        val degreeM = Geo.EARTH_RADIUS_M * PI / 180
        return GeoPoint(known.point.lat + cos(rad) * meters / degreeM,
            known.point.lon + sin(rad) * meters / (degreeM * cos(Math.toRadians(known.point.lat))))
    }
    private fun line() = Polyline(listOf(at(-1_500.0), known.point, at(1_500.0)))

    @Test fun `real snapshots preserve counts IDs missing limits and unknown directions`() {
        val urban = cameras("urban"); val north = cameras("br104"); val eastWest = cameras("br232")
        assertEquals(37, urban.size); assertEquals(38, north.size); assertEquals(43, eastWest.size)
        val union = (urban + north + eastWest).distinctBy { it.id }
        assertEquals(44, union.size)
        assertEquals(42, union.count { !it.directionKnown })
        assertEquals(10, union.count { it.maxSpeedKmh == null })
        assertTrue(union.all { it.kind == RadarKind.FIXED && it.source == RadarCamera.SOURCE_OSM })
        assertEquals(40, known.maxSpeedKmh); assertEquals(200.0, known.directionDeg)
        assertTrue(union.all { it.id.startsWith("osm:node/") })
    }

    @Test fun `mapped camera emits one 700m detection and one 300m warning on a synthetic approach`() {
        val s = RouteRadarSession()
        s.setRoute(line(), listOf(known), CoverageStatus.COMPLETE)
        assertTrue(s.matches.single().directionConfirmed)
        val events = (0..3_000 step 10).flatMap { s.update(it.toDouble()) }
        val detected = events.filterIsInstance<RadarEvent.Detected>().single()
        val alert = events.filterIsInstance<RadarEvent.AlertStarted>().single()
        assertTrue(detected.distanceM in 690..700); assertTrue(alert.distanceM in 290..300)
        assertEquals(800.0, detected.atPositionM, 11.0); assertEquals(1_200.0, alert.atPositionM, 11.0)
        assertEquals(known.id, alert.radar.id)
        assertTrue(events.any { it is RadarEvent.Passed })
        assertEquals(RadarPhase.Clear, s.phase)
    }

    @Test fun `known direction reverse parallel exit and duplicate cases use the same real camera`() {
        val m = RouteRadarMatcher(); val forward = line()
        assertEquals(1, m.match(forward, listOf(known, known)).size)
        assertTrue(m.match(Polyline(forward.points.reversed()), listOf(known)).isEmpty())
        val shift = at(100.0, 110.0)
        val parallel = Polyline(forward.points.map { GeoPoint(it.lat + shift.lat-known.point.lat, it.lon + shift.lon-known.point.lon) })
        assertTrue(m.match(parallel, listOf(known)).isEmpty())
        val exit = Polyline(listOf(at(-1_500.0), at(-500.0), GeoPoint(at(-500.0).lat + shift.lat-known.point.lat, at(-500.0).lon + shift.lon-known.point.lon)))
        assertTrue(m.match(exit, listOf(known)).isEmpty())
        // A real unknown direction is not silently turned into a confirmed direction.
        val unknown = cameras("urban").first { !it.directionKnown }
        val translated = unknown.copy(point = known.point) // Explicit fixture relocation only.
        assertFalse(m.match(forward, listOf(translated)).single().directionConfirmed)
        assertFalse(m.match(Polyline(forward.points.reversed()), listOf(translated)).single().directionConfirmed)
    }

    @Test fun `real tile data survives serialization expiry and offline failure at the app fourteen day policy`() {
        val day = 24L*60*60*1_000; val fetchedAt = 1_795_356_000_000L
        val tile = TileKey.of(known.point)
        val data = RadarTileData(tile, fetchedAt, cameras("urban").filter { TileKey.of(it.point) == tile })
        val restored = RadarTileData.deserialize(data.serialize())
        assertEquals(data, restored)
        val policy = RadarUpdatePolicy(validityMs = 14*day)
        val state = RadarTileState(tile, data)
        assertEquals(TileFreshness.FRESH, policy.freshness(state, fetchedAt+14*day))
        assertEquals(TileFreshness.STALE, policy.freshness(state, fetchedAt+14*day+1))
        val failed = policy.onFetchFailure(state, fetchedAt+15*day)
        assertEquals(data, failed.data)
        assertEquals(TileFreshness.STALE, policy.freshness(failed, fetchedAt+15*day))
        assertFalse(policy.canFetchNow(failed, fetchedAt+15*day+59*60_000))
        assertTrue(policy.canFetchNow(failed, fetchedAt+15*day+60*60_000))
    }

    @Test fun `empty successful data and no data never invent radar warnings`() {
        for (coverage in listOf(CoverageStatus.COMPLETE, CoverageStatus.NONE)) {
            val s = RouteRadarSession(); s.setRoute(line(), emptyList(), coverage)
            assertTrue((0..3_000 step 10).flatMap { s.update(it.toDouble()) }.isEmpty())
        }
    }

    @Test fun `public Caruaru coordinate inputs map to car requests and captured real 401 stays an invalid key`() {
        val input = listOf("-8.286360,-35.972500", "geo:-8.286360,-35.972500", "geo:0,0?q=-8.286360,-35.972500(Av.%20Agamenon)")
        for (text in input) {
            val point = (CoordinateParser.parse(text) as ParseResult.Ok).place.point
            assertEquals(GeoPoint(-8.28636,-35.9725), point)
            val http = HttpGet { url, _ ->
                assertTrue(url.contains("profile=car")); assertTrue(url.contains("point=-8.286360,-35.972500"))
                HttpResult.Ok(401, resource("graphhopper-invalid-key"))
            }
            val provider = GraphHopperRouteProvider(http, { "CHAVE-FICTICIA-CARUARU" })
            val result = provider.route(RouteRequest(GeoPoint(-8.29256,-35.9549), point)) as RouteResult.Failure
            assertEquals(RouteFailureKind.INVALID_KEY, result.kind)
            assertFalse(result.message.contains("CHAVE-FICTICIA-CARUARU"))
        }
        // Street names belong to Android's Geocoder, not the offline coordinate parser.
        assertTrue(CoordinateParser.parse("Avenida Agamenon Magalhães, Caruaru, PE") is ParseResult.Error)
    }
}
