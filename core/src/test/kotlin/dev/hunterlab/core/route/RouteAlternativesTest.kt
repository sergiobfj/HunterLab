package dev.hunterlab.core.route

import dev.hunterlab.core.geo.*
import dev.hunterlab.core.radar.*
import dev.hunterlab.protocol.Maneuver
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RouteAlternativesTest {
    private val request = RouteRequest(GeoPoint(0.0,0.0),GeoPoint(.02,0.0))
    private fun route(via:Double=0.0):Route {
        val line=Polyline(listOf(request.origin,GeoPoint(.01,via),request.destination))
        return Route(line,listOf(RouteStep(Maneuver.Depart,0.0,line.lengthM,100.0,"Rua pública","Siga"),
            RouteStep(Maneuver.Arrive(),line.lengthM,0.0,0.0,null,"Chegada")),line.lengthM,100.0,"fixture","Fixture sintética")
    }
    private fun path(via:Double=0.0):JsonObject {
        val r=route(via)
        return buildJsonObject {
            put("distance",r.distanceM);put("time",100000);put("points_encoded",false)
            put("description",JsonArray(listOf(JsonPrimitive("Via pública"))))
            put("points",buildJsonObject{put("type","LineString");put("coordinates",JsonArray(r.polyline.points.map{JsonArray(listOf(JsonPrimitive(it.lon),JsonPrimitive(it.lat)))}))})
            put("instructions",JsonArray(listOf(
                buildJsonObject{put("sign",0);put("distance",r.distanceM);put("time",100000);put("interval",JsonArray(listOf(JsonPrimitive(0),JsonPrimitive(2))))},
                buildJsonObject{put("sign",4);put("distance",0);put("time",0);put("interval",JsonArray(listOf(JsonPrimitive(2),JsonPrimitive(2))))}
            )))
        }
    }
    private fun body(vararg paths:JsonObject)=buildJsonObject{put("paths",JsonArray(paths.toList()))}.toString()
    private fun parsed(vararg paths:JsonObject)=(GraphHopperParser.parseAlternatives(200,body(*paths)) as RouteChoicesResult.Success).routes
    @Test fun `one actual path remains one`() { assertEquals(1,parsed(path()).size) }
    @Test fun `two distinct actual paths`() { assertEquals(2,parsed(path(),path(.003)).size) }
    @Test fun `three actual paths and road names`() { val r=parsed(path(),path(.003),path(-.003));assertEquals(3,r.size);assertEquals(listOf("Via pública"),r[1].roadNames) }
    @Test fun `almost identical geometry not sold as alternative`() { assertEquals(1,parsed(path(),path(.00001),path()).size) }
    @Test fun `malformed alternative cannot replace valid geometry`() { assertEquals(1,parsed(path(),JsonObject(mapOf("distance" to JsonPrimitive(1)))).size) }
    @Test fun `no valid path fails closed`() { assertTrue(GraphHopperParser.parseAlternatives(200,body(JsonObject(emptyMap()))) is RouteChoicesResult.Failure) }
    @Test fun `malformed response fails closed`() { assertTrue(GraphHopperParser.parseAlternatives(200,"not-json") is RouteChoicesResult.Failure) }
    @Test fun `no fabricated fourth path`() { assertEquals(3,parsed(path(),path(.003),path(-.003),path(.006)).size) }
    @Test fun `selected object instructions and geometry retained`() {
        val a=route();val b=route(.003);val plan=PreparedRoutePlan(listOf(a,b),request.origin,100)
        assertTrue(plan.select(1));assertSame(b,plan.selected);assertSame(b.steps,plan.selected.steps);assertSame(b.polyline,plan.selected.polyline)
    }
    @Test fun `invalid selection preserves previous route`() {
        val b=route(.003);val plan=PreparedRoutePlan(listOf(route(),b),request.origin,100);plan.select(1)
        assertFalse(plan.select(-1));assertFalse(plan.select(2));assertSame(b,plan.selected)
    }
    @Test fun `selection valid at unchanged origin`() { assertTrue(PreparedRoutePlan(listOf(route()),request.origin,100).usableAt(request.origin,200,false)) }
    @Test fun `changed origin requires explicit replan`() { assertFalse(PreparedRoutePlan(listOf(route()),request.origin,100).usableAt(GeoPoint(.01,0.0),200,false)) }
    @Test fun `expired preview cannot silently start a different route`() { assertFalse(PreparedRoutePlan(listOf(route()),request.origin,100).usableAt(request.origin,180101,false)) }
    @Test fun `no permission or location cannot start real navigation`() { assertFalse(PreparedRoutePlan(listOf(route()),request.origin,100).usableAt(null,200,false)) }
    @Test fun `simulator uses exact selected geometry without GPS`() {
        val b=route(.003);val plan=PreparedRoutePlan(listOf(route(),b),request.origin,0);plan.select(1)
        assertTrue(plan.usableAt(null,999999,true));assertSame(b,plan.selected)
    }
    @Test fun `POI to alternatives to selected navigation uses one exact route with fixtures`() {
        val poiJson="""{"features":[{"id":"poi.fixture","text":"Caruaru Shopping","place_name":"Caruaru Shopping, Endereço sintético","center":[0,0.02]}]}"""
        val search=dev.hunterlab.core.search.MapTilerPlaceSearch(HttpGet{_,_->HttpResult.Ok(200,poiJson)},{"fixture-key"})
        val destination=(search.search("Caruaru Shopping",request.origin) as dev.hunterlab.core.search.PlaceSearchResult.Found).places.single()
        assertEquals("Caruaru Shopping",destination.name)
        var routeCalls=0
        val provider=GraphHopperRouteProvider(HttpGet{_,_->routeCalls++;HttpResult.Ok(200,body(path(),path(.003)))},{"fixture-key"})
        val choices=(provider.alternatives(RouteRequest(request.origin,destination.point)) as RouteChoicesResult.Success).routes
        val plan=PreparedRoutePlan(choices,request.origin,0);plan.select(1)
        val engine=dev.hunterlab.core.navigation.NavigationEngine()
        engine.start(destination.point,0)
        val fix=dev.hunterlab.core.navigation.LocationFix(request.origin,5f,0f,0f,1)
        val pending=engine.onFix(fix,1).filterIsInstance<dev.hunterlab.core.navigation.NavigationEffect.RequestRoute>().single()
        engine.onRouteResult(pending.requestId,RouteResult.Success(plan.selected),1)
        assertSame(choices[1],engine.state.route)
        assertSame(choices[1].steps,engine.state.route!!.steps)
        assertEquals(1,routeCalls)
    }
    @Test fun `switching route changes radar matches without mixing parallel street`() {
        val a=route();val b=route(.003)
        val ca=RadarCamera("a",a.polyline.pointAt(a.polyline.lengthM/2),RadarKind.FIXED,60,null)
        val cb=RadarCamera("b",b.polyline.pointAt(b.polyline.lengthM/2),RadarKind.FIXED,40,null)
        val matcher=RouteRadarMatcher()
        assertEquals(listOf("a"),matcher.match(a.polyline,listOf(ca,cb)).map{it.camera.id})
        assertEquals(listOf("b"),matcher.match(b.polyline,listOf(ca,cb)).map{it.camera.id})
    }
    @Test fun `planning request uses API documented limits`() {
        val urls=mutableListOf<String>();val http=HttpGet{url,_->urls.add(url);HttpResult.Ok(200,body(path(),path(.003)))}
        val result=GraphHopperRouteProvider(http,{"fixture-key-not-real"}).alternatives(request)
        assertEquals(2,(result as RouteChoicesResult.Success).routes.size)
        val url=urls.single();assertTrue(url.contains("algorithm=alternative_route"));assertTrue(url.contains("alternative_route.max_paths=3"));assertTrue(url.contains("profile=car"));assertTrue(url.contains("details=max_speed"))
    }
    @Test fun `missing key makes zero network calls`() {
        val http=HttpGet{_,_->error("must not call")}
        assertEquals(RouteFailureKind.NOT_CONFIGURED,(GraphHopperRouteProvider(http,{null}).alternatives(request) as RouteChoicesResult.Failure).failure.kind)
    }
    @Test fun `quota and invalid key do not expose response body`() {
        for((code,kind) in listOf(429 to RouteFailureKind.QUOTA,403 to RouteFailureKind.INVALID_KEY,502 to RouteFailureKind.SERVER)) {
            val http=HttpGet{_,_->HttpResult.Ok(code,"fixture-error-body")}
            val f=(GraphHopperRouteProvider(http,{"fixture-key"}).alternatives(request) as RouteChoicesResult.Failure).failure
            assertEquals(kind,f.kind);assertFalse(f.message.contains("fixture-error-body"))
        }
    }
    @Test fun `network and timeout have explicit states`() {
        for(kind in HttpFailureKind.entries) {
            val http=HttpGet{_,_->HttpResult.Failed(kind,"fixture-error-body")}
            val f=(GraphHopperRouteProvider(http,{"fixture-key"}).alternatives(request) as RouteChoicesResult.Failure).failure
            assertEquals(if(kind==HttpFailureKind.TIMEOUT) RouteFailureKind.TIMEOUT else RouteFailureKind.NETWORK,f.kind);assertFalse(f.message.contains("fixture-error-body"))
        }
    }
    @Test fun `ordinary rerouting never requests alternatives`() {
        val urls=mutableListOf<String>();val http=HttpGet{url,_->urls.add(url);HttpResult.Ok(200,body(path()))}
        assertTrue(GraphHopperRouteProvider(http,{"fixture-key"}).route(request) is RouteResult.Success)
        assertFalse(urls.single().contains("alternative_route"))
    }
    @Test fun `explicit alternative capability rejection falls back transparently`() {
        val urls=mutableListOf<String>()
        val http=HttpGet{url,_->urls.add(url);if(url.contains("algorithm=")) HttpResult.Ok(400,"alternative_route not supported") else HttpResult.Ok(200,body(path()))}
        val result=GraphHopperRouteProvider(http,{"fixture-key"}).alternatives(request) as RouteChoicesResult.Success
        assertEquals(1,result.routes.size);assertNotNull(result.notice)
        assertEquals(2,urls.size);assertFalse(urls.last().contains("algorithm="))
    }
    @Test fun `quota never falls back to another billable request`() {
        var count=0
        val result=GraphHopperRouteProvider(HttpGet{_,_->count++;HttpResult.Ok(429,"alternative quota")},{"fixture-key"}).alternatives(request)
        assertTrue(result is RouteChoicesResult.Failure);assertEquals(1,count)
    }
    @Test fun `degenerate duplicate points do not qualify as valid geometry`() {
        val zero=JsonObject(path().toMutableMap().apply {
            put("distance",JsonPrimitive(0))
            put("points",buildJsonObject{put("type","LineString");put("coordinates",JsonArray(List(3){JsonArray(listOf(JsonPrimitive(0),JsonPrimitive(0)))}))})
        })
        assertTrue(GraphHopperParser.parseAlternatives(200,body(zero)) is RouteChoicesResult.Failure)
    }
    @Test fun `unsupported optional speed details retry preserves alternatives request`() {
        val urls=mutableListOf<String>();val http=HttpGet{url,_->urls.add(url);if(urls.size==1)HttpResult.Ok(400,"detail max_speed unavailable")else HttpResult.Ok(200,body(path()))}
        assertTrue(GraphHopperRouteProvider(http,{"fixture-key"}).alternatives(request) is RouteChoicesResult.Success)
        assertEquals(2,urls.size);assertTrue(urls.all{it.contains("alternative_route.max_paths=3")});assertFalse(urls.last().contains("details="))
    }
}
