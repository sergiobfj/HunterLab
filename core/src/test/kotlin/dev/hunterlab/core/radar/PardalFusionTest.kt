package dev.hunterlab.core.radar

import dev.hunterlab.core.geo.GeoPoint
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File

class PardalFusionTest {
    private val header = "lat,lng,kind,limit_kmh,source,active,end_lat,end_lng,direction_deg\n"
    private fun parse(body: String) = PardalParser.parse(header + body) as PardalParser.Result.Success
    private fun camera(id: String, x: Double = 0.0, limit: Int? = 60, direction: Double? = null) =
        RadarCamera(id, GeoPoint(0.1, 0.1+x/111_000), RadarKind.FIXED, limit, direction, source = id)
    @Test fun `inactive unsupported and malformed records never enter the alert engine`() {
        val data = parse("0.1,0.1,FIXED,60,OSM,0,,,\n0.1,0.2,SECTION,80,DNIT,1,,,\n91,0,FIXED,50,OSM,1,,,\n0,0,FIXED,50,OSM,1,,,360\n")
        assertEquals(1, data.inactive); assertEquals(3, data.invalid); assertTrue(data.cameras.isEmpty())
    }
    @Test fun `CSV quote handling blank limits and traffic directions preserve source semantics`() {
        val data = parse("0.1,0.1,FIXED,,\"DER-PE, OSM\",1,,,90\r\n")
        val c = data.cameras.single(); assertNull(c.maxSpeedKmh)
        assertEquals(90.0,c.directionDeg); assertTrue(c.directionKnown)
        assertEquals("Pardal/DER-PE, OSM",c.source)
    }
    @Test fun `malformed schema oversized and truncated CSV fail closed`() {
        for (text in listOf("lat,lng\n0,0",header+"\"unterminated", "x".repeat(2_000_001))) {
            assertTrue(PardalParser.parse(text) is PardalParser.Result.Failure)
        }
    }
    @Test fun `duplicate CSV identity retains conflicting observations without numeric limit`() {
        val data = parse("0.1,0.1,FIXED,40,OSM,1,,,\n0.1,0.1,FIXED,60,DER-PE,1,,,\n")
        assertEquals(1,data.cameras.size); assertEquals(RadarLimitStatus.CONFLICT,data.cameras.single().speedLimit.status)
    }
    @Test fun `pinned PE release is reproducible with regional counts and explicit missing values`() {
        val parsed = PardalParser.parse(File("../app/src/main/assets/radar/pardal-pe.csv").readText()) as PardalParser.Result.Success
        assertEquals(15,parsed.inactive); assertEquals(0,parsed.invalid); assertEquals(497,parsed.cameras.size)
        fun box(s:Double,w:Double,n:Double,e:Double) = parsed.cameras.filter { it.point.lat in s..n && it.point.lon in w..e }
        val urban=box(-8.34,-36.06,-8.20,-35.90); val br104=box(-8.50,-36.10,-8.05,-35.90); val br232=box(-8.40,-36.20,-8.20,-35.78)
        assertEquals(40,urban.size); assertEquals(37,urban.count { it.maxSpeedKmh!=null })
        assertEquals(42,br104.size); assertEquals(38,br104.count { it.maxSpeedKmh!=null })
        assertEquals(46,br232.size); assertEquals(40,br232.count { it.maxSpeedKmh!=null })
        val union=(urban+br104+br232).distinctBy { it.id }
        assertEquals(48,union.size); assertEquals(41,union.count { it.maxSpeedKmh!=null })
        assertEquals(4,union.count { it.directionKnown })
    }
    @Test fun `mutually unique same position cross source duplicates merge but do not count independent votes`() {
        val all=RadarFusion.merge(listOf(camera("OSM")),listOf(camera("Pardal/OSM",2.0)))
        assertEquals(1,all.size); assertEquals("OSM",all.single().id); assertEquals(60,all.single().maxSpeedKmh)
        assertEquals(2,all.single().speedLimit.sources.size)
    }
    @Test fun `conflicting same device and ambiguous close neighbours suppress numeric claim`() {
        for (distance in listOf(0.0, 15.0)) {
            val all=RadarFusion.merge(listOf(camera("a")),listOf(camera("b",distance,40)))
            assertTrue(all.all { it.maxSpeedKmh==null && it.speedLimit.status==RadarLimitStatus.CONFLICT })
            assertEquals(if (distance==0.0) 1 else 2,all.size)
        }
    }
    @Test fun `opposing carriageways and nonunique identities are retained`() {
        val opposite=RadarFusion.merge(listOf(camera("a",direction=90.0)),listOf(camera("b",1.0,40,270.0)))
        assertEquals(2,opposite.size); assertEquals(setOf(40,60),opposite.map { it.maxSpeedKmh }.toSet())
        val ambiguous=RadarFusion.merge(listOf(camera("a"),camera("b",2.0)),listOf(camera("c",1.0)))
        assertEquals(3,ambiguous.size)
    }
    @Test fun `IDs repeated across tile responses are collapsed without silently selecting conflicting values`() {
        val all=RadarFusion.merge(listOf(camera("a"),camera("a",limit=40)),emptyList())
        assertEquals(1,all.size); assertNull(all.single().maxSpeedKmh)
    }
    @Test fun `real regional snapshots reconcile conservatively instead of claiming every candidate is a distinct device`() {
        val osm=listOf("urban","br104","br232").flatMap { name ->
            val body=checkNotNull(javaClass.getResource("/validation/caruaru/$name.json")).readText()
            (OverpassParser.parse(body) as OverpassParser.Result.Success).cameras
        }
        val all=(PardalParser.parse(File("../app/src/main/assets/radar/pardal-pe.csv").readText()) as PardalParser.Result.Success).cameras
        val boxes=listOf(doubleArrayOf(-8.34,-36.06,-8.20,-35.90),doubleArrayOf(-8.50,-36.10,-8.05,-35.90),doubleArrayOf(-8.40,-36.20,-8.20,-35.78))
        val regional=all.filter { c -> boxes.any { c.point.lat in it[0]..it[2] && c.point.lon in it[1]..it[3] } }
        val merged=RadarFusion.merge(osm,regional)
        assertEquals(57,merged.size);assertEquals(57,merged.map { it.id }.distinct().size)
        assertTrue(merged.none { it.speedLimit.status==RadarLimitStatus.CONFLICT })
        assertTrue(merged.any { it.maxSpeedKmh==null })
    }

    @Test fun `bundled CSV hash matches provenance on every platform`() {
        val bytes=File("../app/src/main/assets/radar/pardal-pe.csv").readBytes()
        val digest=java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
        val meta=kotlinx.serialization.json.Json.parseToJsonElement(File("../app/src/main/assets/radar/provenance.json").readText()) as kotlinx.serialization.json.JsonObject
        assertEquals((meta["csv_sha256"] as kotlinx.serialization.json.JsonPrimitive).content,digest)
    }

}
