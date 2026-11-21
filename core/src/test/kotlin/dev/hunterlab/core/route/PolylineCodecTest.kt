package dev.hunterlab.core.route

import dev.hunterlab.core.geo.GeoPoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PolylineCodecTest {

    // Exemplo da documentação do algoritmo (coordenadas no oceano/EUA, não pessoais).
    private val example = "_p~iF~ps|U_ulLnnqC_mqNvxq`@"
    private val examplePoints = listOf(GeoPoint(38.5, -120.2), GeoPoint(40.7, -120.95), GeoPoint(43.252, -126.453))

    @Test
    fun `decodifica o exemplo de referencia`() {
        val pts = PolylineCodec.decode(example)
        assertEquals(3, pts.size)
        pts.zip(examplePoints).forEach { (a, b) ->
            assertEquals(b.lat, a.lat, 1e-9)
            assertEquals(b.lon, a.lon, 1e-9)
        }
    }

    @Test
    fun `codifica o exemplo de referencia`() {
        assertEquals(example, PolylineCodec.encode(examplePoints))
    }

    @Test
    fun `ida e volta com multiplicador 1e6`() {
        val pts = listOf(GeoPoint(-23.512345, -46.612345), GeoPoint(-23.5, -46.6), GeoPoint(-23.499999, -46.600001))
        val encoded = PolylineCodec.encode(pts, 1e6)
        val back = PolylineCodec.decode(encoded, 1e6)
        pts.zip(back).forEach { (a, b) ->
            assertEquals(a.lat, b.lat, 1e-9)
            assertEquals(a.lon, b.lon, 1e-9)
        }
        // Lido com o multiplicador errado, a posição fica 10x longe: o multiplicador importa.
        assertTrue(PolylineCodec.decode(encoded, 1e7).first().lat > -3.0)
    }

    @Test
    fun `texto vazio vira lista vazia`() {
        assertEquals(emptyList<GeoPoint>(), PolylineCodec.decode(""))
    }

    @Test
    fun `texto truncado ou invalido e rejeitado`() {
        assertThrows<IllegalArgumentException> { PolylineCodec.decode("_p~iF~ps|U_ulL") } // latitude sem longitude
        assertThrows<IllegalArgumentException> { PolylineCodec.decode("_p~iF~ps|") } // bloco sem terminador
        assertThrows<IllegalArgumentException> { PolylineCodec.decode("_p~iF ps|U") } // caractere fora da faixa
        assertThrows<IllegalArgumentException> { PolylineCodec.decode(example, 0.0) }
    }

    @Test
    fun `fixture do GraphHopper decodifica a grade ficticia`() {
        val route = Fixtures.route(Fixtures.TURNS)
        assertEquals(8, route.polyline.points.size)
        assertEquals(GeoPoint(-23.5, -46.6), route.polyline.points.first())
        assertEquals(1298.8, route.polyline.lengthM, 0.5)
    }
}
