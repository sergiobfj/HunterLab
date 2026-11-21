package dev.hunterlab.core.geo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CoordinateParserTest {
    private fun ok(text: String): ParsedPlace {
        val r = CoordinateParser.parse(text)
        assertTrue(r is ParseResult.Ok, "$text → $r")
        return (r as ParseResult.Ok).place
    }

    private fun error(text: String): String {
        val r = CoordinateParser.parse(text)
        assertTrue(r is ParseResult.Error, "$text → $r")
        return (r as ParseResult.Error).message
    }

    @Test
    fun `pares digitados`() {
        assertEquals(GeoPoint(-23.5614, -46.6559), ok("-23.5614, -46.6559").point)
        assertEquals(GeoPoint(-23.5614, -46.6559), ok("  -23.5614 -46.6559 ").point)
        assertEquals(GeoPoint(-23.5614, -46.6559), ok("-23,5614; -46,6559").point)
        assertEquals(GeoPoint(12.0, 45.5), ok("+12,45.5").point)
    }

    @Test
    fun `links geo`() {
        assertEquals(GeoPoint(-22.9, -43.2), ok("geo:-22.9,-43.2").point)
        val p = ok("geo:0,0?q=-22.9519,-43.2105(Cristo%20Redentor)")
        assertEquals(GeoPoint(-22.9519, -43.2105), p.point)
        assertEquals("Cristo Redentor", p.label)
        assertTrue("nome" in error("geo:0,0?q=Padaria+Central"))
    }

    @Test
    fun `links de mapa com coordenadas`() {
        assertEquals(GeoPoint(-23.5505, -46.6333), ok("https://www.google.com/maps/@-23.5505,-46.6333,15z").point)
        assertEquals(GeoPoint(-23.5505, -46.6333), ok("https://maps.google.com/?q=-23.5505,-46.6333").point)
        assertEquals(GeoPoint(-23.5505, -46.6333), ok("https://www.google.com/maps/dir/?api=1&destination=-23.5505%2C-46.6333").point)
    }

    @Test
    fun `entradas invalidas explicam o problema`() {
        assertTrue("Informe" in error(""))
        assertTrue("intervalo" in error("95.0, 10.0"))
        assertTrue("encurtado" in error("https://maps.app.goo.gl/abc123"))
        assertTrue("Formato" in error("Rua Augusta 100"))
    }
}
