package dev.hunterlab.core.route

import dev.hunterlab.core.geo.GeoPoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GraphHopperRouteProviderTest {

    private val key = "CHAVE-FICTICIA-123"
    private val request = RouteRequest(GeoPoint(-23.5, -46.6), GeoPoint(-23.50487, -46.59607), originHeadingDeg = 92.4)

    private class FakeHttp(private val answer: (String) -> HttpResult) : HttpGet {
        val urls = mutableListOf<String>()
        val timeouts = mutableListOf<Int>()
        override fun get(url: String, timeoutMs: Int): HttpResult {
            urls += url
            timeouts += timeoutMs
            return answer(url)
        }
    }

    private fun ok(body: String, code: Int = 200) = FakeHttp { HttpResult.Ok(code, body) }

    @Test
    fun `monta o GET com os parametros exigidos e devolve a rota`() {
        val http = ok(Fixtures.text(Fixtures.TURNS))
        val provider = GraphHopperRouteProvider(http, { key })
        val result = provider.route(request)
        assertTrue(result is RouteResult.Success, "$result")
        val url = http.urls.single()
        assertTrue(url.startsWith("https://graphhopper.com/api/1/route?"), url)
        for (part in listOf(
            "point=-23.500000,-46.600000", "point=-23.504870,-46.596070", "profile=car", "locale=pt_BR",
            "instructions=true", "calc_points=true", "points_encoded=true", "key=$key",
        )) {
            assertTrue(url.contains(part), "faltou $part em ${GraphHopperRouteProvider.redact(url)}")
        }
        assertTrue(url.indexOf("point=-23.500000") < url.indexOf("point=-23.504870"), "origem antes do destino")
        // heading exige ch.disable=true: desligado por padrão.
        assertFalse(url.contains("heading"))
        assertFalse(url.contains("ch.disable"))
        assertEquals(listOf(15_000), http.timeouts)
        assertEquals("graphhopper", provider.id)
        assertTrue(provider.attribution.contains("OpenStreetMap"))
    }

    @Test
    fun `heading so entra quando habilitado e com ch disable`() {
        val http = ok(Fixtures.text(Fixtures.TURNS))
        GraphHopperRouteProvider(http, { key }, useHeading = true).route(request)
        assertTrue(http.urls.single().contains("&ch.disable=true&heading=92&heading_penalty=120"), http.urls.single())
        val semRumo = ok(Fixtures.text(Fixtures.TURNS))
        GraphHopperRouteProvider(semRumo, { key }, useHeading = true).route(request.copy(originHeadingDeg = null))
        assertFalse(semRumo.urls.single().contains("heading"))
    }

    @Test
    fun `sem chave nao chama a rede`() {
        for (missing in listOf(null, "", "   ")) {
            val http = ok(Fixtures.text(Fixtures.TURNS))
            val result = GraphHopperRouteProvider(http, { missing }).route(request)
            assertEquals(RouteFailureKind.NOT_CONFIGURED, (result as RouteResult.Failure).kind)
            assertTrue(http.urls.isEmpty())
        }
    }

    @Test
    fun `redact remove a chave da URL`() {
        val url = GraphHopperRouteProvider(ok(""), { key }).buildUrl(request, key)
        val redacted = GraphHopperRouteProvider.redact(url)
        assertFalse(redacted.contains(key))
        assertTrue(redacted.endsWith("key=***"))
        assertEquals("https://x/route?key=***&point=1,2", GraphHopperRouteProvider.redact("https://x/route?key=abc&point=1,2"))
        assertEquals("https://x/route?a=1&KEY=***", GraphHopperRouteProvider.redact("https://x/route?a=1&KEY=abc"))
    }

    @Test
    fun `chave com caracteres especiais e codificada e baseUrl com query e respeitada`() {
        val http = ok(Fixtures.text(Fixtures.TURNS))
        GraphHopperRouteProvider(http, { "a&b=c" }, baseUrl = "https://example.invalid/route?vehicle=x").route(request)
        val url = http.urls.single()
        assertTrue(url.startsWith("https://example.invalid/route?vehicle=x&point="), url)
        assertTrue(url.endsWith("key=a%26b%3Dc"), url)
    }

    @Test
    fun `mensagens de falha nunca contem a chave`() {
        val echo = """{"message":"Cannot find point 0 for key=$key ($key)"}"""
        val r1 = GraphHopperRouteProvider(ok(echo, 400), { key }).route(request) as RouteResult.Failure
        assertEquals(RouteFailureKind.NO_ROUTE, r1.kind)
        assertFalse(r1.message.contains(key))

        val throwing = FakeHttp { url -> throw IllegalStateException("falhou em $url") }
        val r2 = GraphHopperRouteProvider(throwing, { key }).route(request) as RouteResult.Failure
        assertEquals(RouteFailureKind.NETWORK, r2.kind)
        assertFalse(r2.message.contains(key))

        val failed = FakeHttp { url -> HttpResult.Failed(HttpFailureKind.NETWORK, "erro em $url") }
        val r3 = GraphHopperRouteProvider(failed, { key }).route(request) as RouteResult.Failure
        assertFalse(r3.message.contains(key))
    }

    @Test
    fun `falhas de transporte e HTTP sao mapeadas`() {
        fun kind(http: HttpGet) = (GraphHopperRouteProvider(http, { key }).route(request) as RouteResult.Failure).kind
        assertEquals(RouteFailureKind.TIMEOUT, kind(FakeHttp { HttpResult.Failed(HttpFailureKind.TIMEOUT, "t") }))
        assertEquals(RouteFailureKind.NETWORK, kind(FakeHttp { HttpResult.Failed(HttpFailureKind.NETWORK, "n") }))
        assertEquals(RouteFailureKind.INVALID_KEY, kind(ok(Fixtures.text("graphhopper_error_401.json"), 401)))
        assertEquals(RouteFailureKind.QUOTA, kind(ok(Fixtures.text("graphhopper_error_429.json"), 429)))
        assertEquals(RouteFailureKind.SERVER, kind(ok("", 502)))
        assertEquals(RouteFailureKind.INVALID_RESPONSE, kind(ok(Fixtures.text("graphhopper_malformed.json"))))
    }

    @Test
    fun `a chave e lida a cada pedido`() {
        var current: String? = null
        val http = ok(Fixtures.text(Fixtures.TURNS))
        val provider = GraphHopperRouteProvider(http, { current })
        assertEquals(RouteFailureKind.NOT_CONFIGURED, (provider.route(request) as RouteResult.Failure).kind)
        current = key
        assertTrue(provider.route(request) is RouteResult.Success)
    }
}
