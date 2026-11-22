package dev.hunterlab.core.search

import dev.hunterlab.core.geo.GeoPoint
import dev.hunterlab.core.route.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PlaceSearchTest {
    // Name requested by the user; coordinates/address below are fictional for parser testing.
    private val fixture="""{"features":[{"id":"poi.fixture","text":"Caruaru Shopping","place_name":"Caruaru Shopping, Avenida de teste, Caruaru, Brasil","center":[-35.95,-8.30]}]}"""
    @Test fun `commercial name kept separate from secondary address`() {
        val p=(MapTilerPlaceSearch.parse(fixture) as PlaceSearchResult.Found).places.single()
        assertEquals("Caruaru Shopping",p.name);assertEquals("Avenida de teste, Caruaru, Brasil",p.detail)
    }
    @Test fun `coordinates use longitude latitude order`() { assertEquals(GeoPoint(-8.30,-35.95),(MapTilerPlaceSearch.parse(fixture) as PlaceSearchResult.Found).places.single().point) }
    @Test fun `malformed response is not empty success`() {assertTrue(MapTilerPlaceSearch.parse("bad-json") is PlaceSearchResult.Failed)}
    @Test fun `no results explicit empty list`() {assertTrue((MapTilerPlaceSearch.parse("""{"features":[]}""") as PlaceSearchResult.Found).places.isEmpty())}
    @Test fun `invalid coordinate discarded`() { assertTrue((MapTilerPlaceSearch.parse(fixture.replace("-8.30","999")) as PlaceSearchResult.Found).places.isEmpty()) }
    @Test fun `minimum query and missing key never call API`() {
        val http=HttpGet{_,_->error("must not call")}
        assertTrue(MapTilerPlaceSearch(http,{null}).search("Ca",null) is PlaceSearchResult.Found)
        assertEquals(RouteFailureKind.NOT_CONFIGURED,(MapTilerPlaceSearch(http,{null}).search("Caruaru",null) as PlaceSearchResult.Failed).kind)
    }
    @Test fun `query encoding and authorized geographic bias`() {
        val client=MapTilerPlaceSearch(HttpGet{_,_->HttpResult.Ok(200,fixture)},{"fixture-key"})
        val url=client.buildUrl("Café & pão",GeoPoint(-8.28,-35.97),"fixture-key")
        assertTrue(url.contains("Caf%C3%A9%20%26%20p%C3%A3o.json"));assertTrue(url.contains("proximity=-35.970,-8.280"));assertTrue(url.contains("country=br"))
    }
    @Test fun `without location no IP or invented proximity`() {
        val client=MapTilerPlaceSearch(HttpGet{_,_->HttpResult.Ok(200,fixture)},{"fixture-key"})
        assertFalse(client.buildUrl("Caruaru Shopping",null,"fixture-key").contains("proximity"))
    }
    @Test fun `network timeout quota key failure safe messages`() {
        val cases=listOf(HttpResult.Failed(HttpFailureKind.NETWORK,"fixture-error-body") to RouteFailureKind.NETWORK,
            HttpResult.Failed(HttpFailureKind.TIMEOUT,"fixture-error-body") to RouteFailureKind.TIMEOUT,
            HttpResult.Ok(429,"fixture-error-body") to RouteFailureKind.QUOTA,HttpResult.Ok(403,"fixture-error-body") to RouteFailureKind.INVALID_KEY)
        for((response,kind) in cases) {
            val result=MapTilerPlaceSearch(HttpGet{_,_->response},{"fixture-key"}).search("Caruaru Shopping",null) as PlaceSearchResult.Failed
            assertEquals(kind,result.kind);assertFalse(result.message.contains("fixture-error-body"))
        }
    }
}
