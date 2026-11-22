package dev.hunterlab.core.route

import dev.hunterlab.core.geo.GeoPoint
import dev.hunterlab.core.radar.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GraphHopperSpeedDetailsTest {
    private fun body(rows:String)=Fixtures.mutatePath(Fixtures.TURNS) { it["details"]=Json.parseToJsonElement("""{"max_speed":$rows}""") }
    private fun route(rows:String)=(GraphHopperParser.parse(body(rows)) as RouteResult.Success).route
    private val request=RouteRequest(GeoPoint(-23.5,-46.6),GeoPoint(-23.50487,-46.59607))
    @Test fun `speed spans use polyline indexes and boundary belongs to following segment`() {
        val r=route("[[0,2,50],[2,4,40],[4,7,null]]")
        assertEquals(3,r.roadSpeedLimits.size);assertEquals(50,r.roadSpeedLimitAt(0.0))
        assertEquals(40,r.roadSpeedLimitAt(r.polyline.cumulative[2]));assertNull(r.roadSpeedLimitAt(r.polyline.cumulative[4]))
        assertNull(r.roadSpeedLimitAt(Double.NaN));assertNull(r.roadSpeedLimitAt(-1.0))
    }
    @Test fun `unknown infinity text zero and nonintegral details are never guessed`() {
        for (value in listOf("null","\"Infinity\"","\"50\"","0","50.5","999")) {
            val r=route("[[0,7,$value]]");assertEquals(1,r.roadSpeedLimits.size);assertNull(r.roadSpeedLimitAt(0.0))
        }
    }
    @Test fun `malformed optional details never destroy the navigation route`() {
        for (rows in listOf("[[0,999,50]]","[[2,4,50],[0,2,40]]","[[0,4,50],[3,7,40]]","[[0,0,50]]","[[0,2]]","[42]","null")) {
            val r=route(rows);assertTrue(r.roadSpeedLimits.isEmpty());assertTrue(r.steps.isNotEmpty())
        }
    }
    @Test fun `road segment speed is never converted to the camera speed`() {
        val route=route("[[0,7,80]]")
        val camera=RadarCamera("fixture",route.polyline.points[2],RadarKind.FIXED,null,null)
        val matched=RouteRadarMatcher().match(route.polyline,listOf(camera)).single().toRouteRadar()
        assertEquals(80,route.roadSpeedLimitAt(0.0));assertNull(matched.speedLimitKmh);assertNull(matched.limitInfo?.kmh)
    }
    @Test fun `request uses max speed detail and unsupported servers fall back once to ordinary route`() {
        val urls=mutableListOf<String>()
        val provider=GraphHopperRouteProvider(HttpGet { url,_ -> urls+=url
            if (url.contains("details=max_speed")) HttpResult.Ok(400,"{\"message\":\"Unsupported detail max_speed\"}")
            else HttpResult.Ok(200,Fixtures.text(Fixtures.TURNS)) }, {"CHAVE-FICTICIA"})
        assertTrue(provider.route(request) is RouteResult.Success);assertEquals(2,urls.size)
        assertTrue(urls[0].contains("details=max_speed"));assertFalse(urls[1].contains("details="))
        assertTrue(urls.all { it.contains("profile=car") })
    }
    @Test fun `invalid key rate limits timeout and unrelated bad request never trigger duplicate API requests`() {
        for (result in listOf(HttpResult.Ok(401,"{}"),HttpResult.Ok(429,"{}"),HttpResult.Ok(400,"{\"message\":\"Invalid point\"}"),HttpResult.Failed(HttpFailureKind.TIMEOUT,"fixture"))) {
            var calls=0;val provider=GraphHopperRouteProvider(HttpGet { _,_->calls++;result },{"CHAVE-FICTICIA"})
            assertTrue(provider.route(request) is RouteResult.Failure);assertEquals(1,calls)
        }
    }
}
