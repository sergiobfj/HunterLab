package dev.hunterlab.core.radar

import dev.hunterlab.core.geo.GeoPoint
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RadarLimitEvidenceTest {
    private fun obs(raw: String?, source: String = "fixture", condition: String? = null) = RadarLimitObservation(source, raw, condition)
    @Test fun `explicit equivalent units agree without tolerance`() {
        val info = RadarSpeedLimit.resolve(listOf(obs("60"), obs("60 km/h", "other")))
        assertEquals(RadarLimitStatus.RECORDED, info.status); assertEquals(60, info.kmh)
        assertEquals(2, info.sources.size)
    }
    @Test fun `conflicting speeds never choose minimum maximum or majority`() {
        val info = RadarSpeedLimit.resolve(listOf(obs("40"), obs("60", "other"), obs("60", "third")))
        assertEquals(RadarLimitStatus.CONFLICT, info.status); assertNull(info.kmh)
    }
    @Test fun `conditional estimated malformed and absent observations never assert a number`() {
        val examples = listOf(listOf(obs("60", condition = "40 @ (wet)")),
            listOf(obs("60").copy(estimated = true)), listOf(obs("BR:urban")),
            listOf(obs("40;60")), listOf(obs(null)), listOf(obs("0")), listOf(obs("999")))
        examples.forEach { assertNull(RadarSpeedLimit.resolve(it).kmh, "$it") }
    }
    @Test fun `expired evidence is retained but does not assert or overrule a current limit`() {
        val old = obs("80").copy(usable = false)
        assertNull(RadarSpeedLimit.resolve(listOf(old)).kmh)
        val info = RadarSpeedLimit.resolve(listOf(old, obs("40", "current")))
        assertEquals(40, info.kmh); assertEquals(2, info.observations.size)
    }
    private fun parse(tags: String, relationTags: String? = null): RadarCamera {
        val relation = relationTags?.let { "," + """{"type":"relation","id":2,"tags":{"type":"enforcement","enforcement":"maxspeed",$it},"members":[{"type":"node","ref":1,"role":"device"}]}""" } ?: ""
        val body = """{"elements":[{"type":"node","id":1,"lat":0.1,"lon":0.1,"tags":{"highway":"speed_camera",$tags}}$relation]}"""
        return (OverpassParser.parse(body) as OverpassParser.Result.Success).cameras.single()
    }
    @Test fun `enforcement relation supplements missing node speed with source evidence`() {
        val c = parse("\"direction\":\"90\"", "\"maxspeed\":\"50\"")
        assertEquals(50, c.maxSpeedKmh); assertNull(c.directionDeg); assertEquals(90.0, c.facingDirectionDeg)
        assertTrue(c.speedLimit.sources.contains("OSM relation/2"))
    }
    @Test fun `relation cannot silently override conflicting device tag`() {
        val c = parse("\"maxspeed\":\"40\"", "\"maxspeed\":\"60\"")
        assertEquals(RadarLimitStatus.CONFLICT, c.speedLimit.status); assertNull(c.maxSpeedKmh)
    }
    @Test fun `conditional directional lane and motorcycle limits are handled conservatively`() {
        for (key in listOf("maxspeed:conditional", "maxspeed:motorcycle:conditional", "maxspeed:forward:conditional", "maxspeed:forward", "maxspeed:lanes")) {
            val c = parse("\"maxspeed\":\"60\",\"$key\":\"40 @ (wet)\"")
            assertEquals(RadarLimitStatus.CONDITIONAL, c.speedLimit.status); assertNull(c.maxSpeedKmh)
        }
        assertEquals(40, parse("\"maxspeed\":\"60\",\"maxspeed:motorcycle\":\"40\"").maxSpeedKmh)
    }
    @Test fun `v2 cache preserves conflicts conditions source and camera facing orientation`() {
        val c = parse("\"maxspeed\":\"40\",\"direction\":\"90\"", "\"maxspeed\":\"60\"")
        val data = RadarTileData(TileKey.of(c.point), 1, listOf(c))
        assertEquals(data, RadarTileData.deserialize(data.serialize()))
    }
    @Test fun `v1 cache migrates positions without turning lost conditions into confirmed limits`() {
        val old = """{"schema":1,"tile":"0_0","fetchedAtMs":1,"cameras":[{"id":"old","lat":0.1,"lon":0.1,"kind":"FIXED","maxSpeedKmh":60,"directionDeg":90,"directionKnown":true}]}"""
        val data = RadarTileData.deserialize(old)
        assertTrue(data.needsRefresh); assertNull(data.cameras.single().maxSpeedKmh)
        assertFalse(data.cameras.single().directionKnown)
        assertEquals("60", data.cameras.single().speedLimit.observations.single().raw)
        assertEquals(TileFreshness.STALE, RadarUpdatePolicy().freshness(RadarTileState(data.tile, data), 1))
    }
    @Test fun `v2 cache rejects speed without consistent evidence`() {
        val c = RadarCamera("a", GeoPoint(0.1,0.1), RadarKind.FIXED, 60, null)
        val text = RadarTileData(TileKey.of(c.point), 1, listOf(c)).serialize().replace("\"maxSpeedKmh\":60", "\"maxSpeedKmh\":80")
        assertThrows(IllegalArgumentException::class.java) { RadarTileData.deserialize(text) }
    }
}
