package dev.hunterlab.core.radar

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class OverpassParserTest {

    private fun fixture(name: String): String =
        requireNotNull(javaClass.getResource("/radar/$name")) { "Fixture ausente: $name" }.readText()

    private fun ok(body: String): OverpassParser.Result.Success = when (val r = OverpassParser.parse(body)) {
        is OverpassParser.Result.Success -> r
        is OverpassParser.Result.Failure -> fail("Esperava sucesso, veio ${r.message}")
    }

    private fun failure(body: String): OverpassParser.Result.Failure =
        OverpassParser.parse(body) as? OverpassParser.Result.Failure ?: fail("Esperava falha")

    private fun OverpassParser.Result.Success.byNode(n: Long): RadarCamera =
        cameras.single { it.id == RadarCamera.osmNodeId(n) }

    @Test
    fun `no speed_camera simples vira radar fixo com limite`() {
        val r = ok(fixture("overpass_node_simple.json"))
        val c = r.cameras.single()
        assertEquals("osm:node/1001", c.id)
        assertEquals(RadarKind.FIXED, c.kind)
        assertEquals(60, c.maxSpeedKmh)
        assertEquals(-10.5, c.point.lat, 1e-9)
        assertEquals(-48.3, c.point.lon, 1e-9)
        assertNull(c.directionDeg)
        assertFalse(c.directionKnown)
        assertEquals("OpenStreetMap", c.source)
        assertTrue(r.warnings.isEmpty())
    }

    @Test
    fun `relacoes enforcement usam device, from e o tipo certo`() {
        val r = ok(fixture("overpass_relations.json"))
        assertEquals(listOf(1101L, 1201L, 1301L, 1401L, 1601L).map(RadarCamera::osmNodeId), r.cameras.map { it.id })

        val device = r.byNode(1101) // maxspeed com device: usa o device, não o from
        assertEquals(RadarKind.FIXED, device.kind)
        assertEquals(80, device.maxSpeedKmh)
        assertEquals(-10.51, device.point.lat, 1e-9)
        assertFalse(device.directionKnown)

        val fromOnly = r.byNode(1201) // sem device: usa o from; a relação "check" é ignorada
        assertEquals(RadarKind.FIXED, fromOnly.kind)
        assertEquals(50, fromOnly.maxSpeedKmh)

        val average = r.byNode(1301) // velocidade média: início do trecho; from→to longo demais para dar rumo
        assertEquals(RadarKind.AVERAGE_SPEED, average.kind)
        assertEquals(100, average.maxSpeedKmh)
        assertFalse(average.directionKnown)

        val signal = r.byNode(1401)
        assertEquals(RadarKind.TRAFFIC_LIGHT, signal.kind)
        assertNull(signal.maxSpeedKmh)

        // direction=forward resolvido pela geometria curta from→to (≈ 109 m para leste).
        val derived = r.byNode(1601)
        assertTrue(derived.directionKnown)
        assertEquals(90.0, derived.directionDeg!!, 0.5)

        // Relação cujo device não veio com coordenadas: ignorada com aviso.
        assertTrue(r.warnings.any { "2005" in it }, r.warnings.toString())
    }

    @Test
    fun `maxspeed em mph e convertido e valores nao numericos viram nulo`() {
        val r = ok(fixture("overpass_values.json"))
        assertEquals(64, r.byNode(3001).maxSpeedKmh) // 40 mph
        assertNull(r.byNode(3002).maxSpeedKmh) // BR:urban
        assertEquals(60, r.byNode(3003).maxSpeedKmh) // "60 km/h"
        assertNull(r.byNode(3004).maxSpeedKmh) // none
        assertNull(r.byNode(3005).maxSpeedKmh) // 60;40
        assertEquals(46, r.byNode(3010).maxSpeedKmh) // 45.6
        assertTrue(r.warnings.any { "BR:urban" in it })

        assertEquals(64, OsmTagValues.parseMaxSpeedKmh("40mph"))
        assertEquals(30, OsmTagValues.parseMaxSpeedKmh(" 30 KMH "))
        assertNull(OsmTagValues.parseMaxSpeedKmh("signals"))
        assertNull(OsmTagValues.parseMaxSpeedKmh("0"))
        assertNull(OsmTagValues.parseMaxSpeedKmh("999"))
        assertNull(OsmTagValues.parseMaxSpeedKmh(""))
        assertNull(OsmTagValues.parseMaxSpeedKmh(null))
    }

    @Test
    fun `direcao numerica, cardeal e relativa a via`() {
        val r = ok(fixture("overpass_values.json"))
        assertEquals(90.0, r.byNode(3001).directionDeg)
        assertEquals(45.0, r.byNode(3002).directionDeg) // NE
        assertEquals(247.5, r.byNode(3004).directionDeg) // WSW
        assertEquals(270.0, r.byNode(3006).directionDeg) // -90 normalizado
        assertEquals(337.5, r.byNode(3010).directionDeg) // nnw
        listOf(3003L, 3005L, 3007L).forEach { n ->
            // forward / backward / "abc": sentido desconhecido
            assertFalse(r.byNode(n).directionKnown, "nó $n")
            assertNull(r.byNode(n).directionDeg, "nó $n")
        }
        assertTrue(r.warnings.any { "abc" in it })

        assertEquals(OsmDirection.WayRelative("forward"), OsmTagValues.parseDirection("Forward"))
        assertEquals(OsmDirection.Both, OsmTagValues.parseDirection("both"))
        assertEquals(OsmDirection.Absent, OsmTagValues.parseDirection(" "))
        assertEquals(OsmDirection.Degrees(0.0), OsmTagValues.parseDirection("360"))
        assertEquals(OsmDirection.Degrees(112.5), OsmTagValues.parseDirection("ESE"))
    }

    @Test
    fun `entradas sem coordenadas sao ignoradas`() {
        val r = ok(fixture("overpass_values.json"))
        assertTrue(r.cameras.none { it.id == "osm:node/3008" })
        assertTrue(r.cameras.none { it.id == "osm:node/3009" }) // banco de praça não é radar
        assertEquals(8, r.cameras.size)
        assertTrue(r.warnings.any { "3008" in it })
    }

    @Test
    fun `mesmo dispositivo como no e membro de relacao vira um radar so`() {
        val r = ok(fixture("overpass_duplicates.json"))
        assertEquals(listOf("osm:node/4001", "osm:node/4101"), r.cameras.map { it.id })

        val merged = r.byNode(4001)
        assertEquals(RadarKind.FIXED, merged.kind)
        assertEquals(60, merged.maxSpeedKmh) // da relação
        assertEquals(180.0, merged.directionDeg) // do nó
        assertTrue(merged.directionKnown)

        // Radar de velocidade que também fiscaliza sinal: continua "fixo", com o limite do nó.
        val both = r.byNode(4101)
        assertEquals(RadarKind.FIXED, both.kind)
        assertEquals(50, both.maxSpeedKmh)
    }

    @Test
    fun `resposta malformada ou com erro do servidor e invalida`() {
        assertEquals(OverpassParser.ErrorCode.INVALID_RESPONSE, failure(fixture("overpass_malformed.txt")).code)
        assertEquals(OverpassParser.ErrorCode.INVALID_RESPONSE, failure(fixture("overpass_runtime_error.json")).code)
        assertTrue("timed out" in failure(fixture("overpass_runtime_error.json")).message)
        failure("<html><body>429 Too Many Requests</body></html>")
        failure("[]")
        failure("""{"version":0.6}""")
        failure("""{"elements":{}}""")
        failure("")
        // Lista vazia válida: sucesso sem radares (área sem radares mapeados).
        assertTrue(ok("""{"elements":[]}""").cameras.isEmpty())
    }

    @Test
    fun `consulta overpass traz nos, relacoes e membros com coordenadas`() {
        val q = OverpassQuery.forBox(-10.6, -48.4, -10.4, -48.2)
        assertTrue(q.startsWith("[out:json][timeout:25];"), q)
        assertTrue("node[\"highway\"=\"speed_camera\"](-10.600000,-48.400000,-10.400000,-48.200000);" in q, q)
        assertTrue("relation[\"type\"=\"enforcement\"]" in q, q)
        listOf("maxspeed", "average_speed", "traffic_signals").forEach { assertTrue(it in q, it) }
        assertTrue(q.trimEnd().endsWith("out body;\n>;\nout skel qt;"), q)
        assertTrue("[timeout:60]" in OverpassQuery.forBox(0.0, 0.0, 0.2, 0.2, timeoutS = 60))
        assertEquals(q, TileKey.of(RadarTestGeo.ORIGIN).overpassQuery())
        assertThrows<IllegalArgumentException> { OverpassQuery.forBox(-10.4, -48.4, -10.6, -48.2) }
        assertThrows<IllegalArgumentException> { OverpassQuery.forBox(-10.6, -48.2, -10.4, -48.4) }
        assertThrows<IllegalArgumentException> { OverpassQuery.forBox(-10.6, -48.4, -10.4, -48.2, timeoutS = 0) }
    }
}
