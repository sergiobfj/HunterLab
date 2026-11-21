package dev.hunterlab.core.route

import dev.hunterlab.protocol.Maneuver
import dev.hunterlab.protocol.Side
import dev.hunterlab.protocol.TurnSharpness
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GraphHopperParserTest {

    private fun success(body: String): Route {
        val r = GraphHopperParser.parse(body)
        assertTrue(r is RouteResult.Success, "esperava sucesso, veio $r")
        return (r as RouteResult.Success).route
    }

    private fun assertInvalid(body: String) {
        val r = GraphHopperParser.parse(body)
        assertTrue(r is RouteResult.Failure, "esperava falha, veio $r")
        assertEquals(RouteFailureKind.INVALID_RESPONSE, (r as RouteResult.Failure).kind, r.message)
        assertTrue(r.message.startsWith("Resposta inválida"), r.message)
    }

    private fun failure(code: Int, fixture: String) = GraphHopperParser.parseHttp(code, Fixtures.text(fixture)) as RouteResult.Failure

    @Test
    fun `retas e curvas viram manobras neutras com a partida no inicio`() {
        val route = success(Fixtures.text(Fixtures.TURNS))
        assertEquals(
            listOf(
                Maneuver.Depart,
                Maneuver.Straight,
                Maneuver.Turn(Side.RIGHT),
                Maneuver.Turn(Side.LEFT, TurnSharpness.SLIGHT),
                Maneuver.Turn(Side.RIGHT, TurnSharpness.SHARP),
                Maneuver.Arrive(),
            ),
            route.steps.map { it.maneuver },
        )
        // atAlongM = cumulative[interval[0]] (intervalos 0,1,2,4,6,7).
        val c = route.polyline.cumulative
        assertEquals(listOf(c[0], c[1], c[2], c[4], c[6], c[7]), route.steps.map { it.atAlongM })
        assertEquals(route.polyline.lengthM, route.steps.last().atAlongM, 1e-9)
        assertEquals("graphhopper", route.providerId)
        assertEquals("GraphHopper, OpenStreetMap contributors", route.attribution)
        assertEquals("Rua dos Ipês", route.steps[2].streetName)
        assertEquals("Vire à direita na Rua dos Ipês", route.steps[2].text)
        assertNull(route.steps.last().streetName) // street_name vazio → null
        assertEquals(route.polyline.lengthM, route.distanceM, 0.01)
        assertEquals(route.steps.sumOf { it.durationS }, route.durationS, 0.01)
        assertEquals(route.steps[2].lengthM, c[4] - c[2], 0.01)
    }

    @Test
    fun `rotatoria usa o numero da saida`() {
        val route = success(Fixtures.text(Fixtures.ROUNDABOUT))
        assertEquals(
            listOf(Maneuver.Depart, Maneuver.Roundabout(2), Maneuver.Turn(Side.LEFT), Maneuver.Arrive()),
            route.steps.map { it.maneuver },
        )
    }

    @Test
    fun `rotatoria sem saida valida cai para a primeira`() {
        val semSaida = Fixtures.mutateInstruction(Fixtures.ROUNDABOUT, 1) { it.remove("exit_number") }
        assertEquals(Maneuver.Roundabout(1), success(semSaida).steps[1].maneuver)
        val saidaZero = Fixtures.mutateInstruction(Fixtures.ROUNDABOUT, 1) { it["exit_number"] = JsonPrimitive(0) }
        assertEquals(Maneuver.Roundabout(1), success(saidaZero).steps[1].maneuver)
    }

    @Test
    fun `retorno vira UTurn`() {
        val route = success(Fixtures.text(Fixtures.UTURN))
        assertEquals(
            listOf(Maneuver.Depart, Maneuver.UTurn, Maneuver.Turn(Side.RIGHT), Maneuver.Arrive()),
            route.steps.map { it.maneuver },
        )
    }

    @Test
    fun `tabela completa de sinais`() {
        val expected = mapOf(
            -98 to Maneuver.UTurn,
            -8 to Maneuver.UTurn,
            -7 to Maneuver.Keep(Side.LEFT),
            -6 to Maneuver.Straight,
            -3 to Maneuver.Turn(Side.LEFT, TurnSharpness.SHARP),
            -2 to Maneuver.Turn(Side.LEFT),
            -1 to Maneuver.Turn(Side.LEFT, TurnSharpness.SLIGHT),
            0 to Maneuver.Straight,
            1 to Maneuver.Turn(Side.RIGHT, TurnSharpness.SLIGHT),
            2 to Maneuver.Turn(Side.RIGHT),
            3 to Maneuver.Turn(Side.RIGHT, TurnSharpness.SHARP),
            4 to Maneuver.Arrive(),
            5 to Maneuver.Straight,
            6 to Maneuver.Roundabout(3),
            7 to Maneuver.Keep(Side.RIGHT),
            8 to Maneuver.UTurn,
            42 to Maneuver.Straight, // desconhecido
            -99 to Maneuver.Straight,
        )
        for ((sign, m) in expected) assertEquals(m, GraphHopperParser.maneuverForSign(sign, 3), "sign $sign")
    }

    @Test
    fun `sinal desconhecido na resposta vira seguir em frente`() {
        val body = Fixtures.mutateInstruction(Fixtures.TURNS, 2) { it["sign"] = JsonPrimitive(17) }
        assertEquals(Maneuver.Straight, success(body).steps[2].maneuver)
    }

    @Test
    fun `primeira instrucao vira partida mesmo com outro sinal`() {
        val body = Fixtures.mutateInstruction(Fixtures.TURNS, 0) { it["sign"] = JsonPrimitive(2) }
        assertEquals(Maneuver.Depart, success(body).steps[0].maneuver)
    }

    @Test
    fun `respeita points_encoded_multiplier`() {
        val original = Fixtures.route(Fixtures.TURNS)
        val body = Fixtures.mutatePath(Fixtures.TURNS) {
            it["points"] = JsonPrimitive(PolylineCodec.encode(original.polyline.points, 1e6))
            it["points_encoded_multiplier"] = JsonPrimitive(1_000_000.0)
        }
        val route = success(body)
        assertEquals(original.polyline.points, route.polyline.points)
        val invalido = Fixtures.mutatePath(Fixtures.TURNS) { it["points_encoded_multiplier"] = JsonPrimitive(-1) }
        assertInvalid(invalido)
    }

    @Test
    fun `aceita geometria GeoJSON quando points_encoded e falso`() {
        val original = Fixtures.route(Fixtures.TURNS)
        val body = Fixtures.mutatePath(Fixtures.TURNS) {
            it["points_encoded"] = JsonPrimitive(false)
            it["points"] = JsonObject(
                mapOf(
                    "type" to JsonPrimitive("LineString"),
                    "coordinates" to JsonArray(
                        original.polyline.points.map { p -> JsonArray(listOf(JsonPrimitive(p.lon), JsonPrimitive(p.lat))) },
                    ),
                ),
            )
        }
        assertEquals(original.polyline.points, success(body).polyline.points)
    }

    @Test
    fun `intervalos fora da geometria sao rejeitados`() {
        assertInvalid(Fixtures.mutateInstruction(Fixtures.TURNS, 4) { it["interval"] = JsonArray(listOf(JsonPrimitive(6), JsonPrimitive(99))) })
        assertInvalid(Fixtures.mutateInstruction(Fixtures.TURNS, 3) { it["interval"] = JsonArray(listOf(JsonPrimitive(-1), JsonPrimitive(6))) })
        assertInvalid(Fixtures.mutateInstruction(Fixtures.TURNS, 3) { it["interval"] = JsonArray(listOf(JsonPrimitive(6), JsonPrimitive(4))) })
        assertInvalid(Fixtures.mutateInstruction(Fixtures.TURNS, 3) { it["interval"] = JsonArray(listOf(JsonPrimitive(4))) })
        assertInvalid(Fixtures.mutateInstruction(Fixtures.TURNS, 3) { it["interval"] = JsonArray(listOf(JsonPrimitive(1.5), JsonPrimitive(6))) })
        // Fora de ordem: a instrução 3 começa antes da 2.
        assertInvalid(Fixtures.mutateInstruction(Fixtures.TURNS, 3) { it["interval"] = JsonArray(listOf(JsonPrimitive(1), JsonPrimitive(6))) })
    }

    @Test
    fun `distancias e tempos negativos ou NaN sao rejeitados`() {
        assertInvalid(Fixtures.mutateInstruction(Fixtures.TURNS, 2) { it["distance"] = JsonPrimitive(-5.0) })
        assertInvalid(Fixtures.mutateInstruction(Fixtures.TURNS, 2) { it["time"] = JsonPrimitive(-1) })
        assertInvalid(Fixtures.mutateInstruction(Fixtures.TURNS, 2) { it["distance"] = JsonPrimitive("NaN") })
        assertInvalid(Fixtures.mutatePath(Fixtures.TURNS) { it["distance"] = JsonPrimitive(-1.0) })
        assertInvalid(Fixtures.mutatePath(Fixtures.TURNS) { it.remove("time") })
        // NaN literal (JSON inválido, mas há servidores que emitem).
        val nanLiteral = Fixtures.mutatePath(Fixtures.TURNS) { it["distance"] = JsonPrimitive("__NAN__") }
            .replace("\"__NAN__\"", "NaN")
        assertInvalid(nanLiteral)
        assertInvalid(Fixtures.mutateInstruction(Fixtures.TURNS, 1) { it.remove("sign") })
    }

    @Test
    fun `distancia total incoerente com a geometria e rejeitada mas pequena diferenca e aceita`() {
        assertInvalid(Fixtures.mutatePath(Fixtures.TURNS) { it["distance"] = JsonPrimitive(5_000.0) })
        val leve = success(Fixtures.mutatePath(Fixtures.TURNS) { it["distance"] = JsonPrimitive(1_330.0) })
        assertEquals(1_330.0, leve.distanceM, 1e-9)
        // Instruções com distância incoerente com a geometria: aceitas; o progresso usa a geometria.
        val instrucao = success(Fixtures.mutateInstruction(Fixtures.TURNS, 2) { it["distance"] = JsonPrimitive(9_999.0) })
        assertEquals(9_999.0, instrucao.steps[2].lengthM, 1e-9)
        assertEquals(instrucao.polyline.cumulative[2], instrucao.steps[2].atAlongM, 1e-9)
    }

    @Test
    fun `geometria com menos de 2 pontos ou ausente e rejeitada`() {
        val umPonto = PolylineCodec.encode(Fixtures.route(Fixtures.TURNS).polyline.points.take(1))
        assertInvalid(Fixtures.mutatePath(Fixtures.TURNS) { it["points"] = JsonPrimitive(umPonto) })
        assertInvalid(Fixtures.mutatePath(Fixtures.TURNS) { it.remove("points") })
        assertInvalid(Fixtures.mutatePath(Fixtures.TURNS) { it["points"] = JsonPrimitive("_p~iF~ps|") })
    }

    @Test
    fun `instrucoes vazias ou ausentes sao rejeitadas`() {
        assertInvalid(Fixtures.mutatePath(Fixtures.TURNS) { it["instructions"] = JsonArray(emptyList()) })
        assertInvalid(Fixtures.mutatePath(Fixtures.TURNS) { it.remove("instructions") })
    }

    @Test
    fun `resposta malformada ou sem caminhos e rejeitada`() {
        assertInvalid(Fixtures.text("graphhopper_malformed.json"))
        assertInvalid("")
        assertInvalid("[]")
        assertInvalid("""{"paths":[]}""")
        assertInvalid("""{"message":"ok?"}""")
    }

    @Test
    fun `ponto nao encontrado ou sem conexao vira NO_ROUTE`() {
        val p = failure(400, "graphhopper_error_400_point_not_found.json")
        assertEquals(RouteFailureKind.NO_ROUTE, p.kind)
        assertFalse(p.message.contains("Cannot find"), "mensagem deve ser pt-BR: ${p.message}")
        assertEquals(RouteFailureKind.NO_ROUTE, failure(400, "graphhopper_error_400_connection_not_found.json").kind)
    }

    @Test
    fun `outros erros HTTP sao classificados`() {
        assertEquals(RouteFailureKind.BAD_REQUEST, failure(400, "graphhopper_error_400_bad_request.json").kind)
        assertEquals(RouteFailureKind.INVALID_KEY, failure(401, "graphhopper_error_401.json").kind)
        assertEquals(RouteFailureKind.INVALID_KEY, failure(403, "graphhopper_error_401.json").kind)
        assertEquals(RouteFailureKind.QUOTA, failure(429, "graphhopper_error_429.json").kind)
        assertEquals(RouteFailureKind.SERVER, (GraphHopperParser.parseHttp(500, "<html>erro</html>") as RouteResult.Failure).kind)
        assertEquals(RouteFailureKind.SERVER, (GraphHopperParser.parseHttp(503, "") as RouteResult.Failure).kind)
        assertEquals(RouteFailureKind.BAD_REQUEST, (GraphHopperParser.parseHttp(400, "não é json") as RouteResult.Failure).kind)
        assertEquals(RouteFailureKind.BAD_REQUEST, (GraphHopperParser.parseHttp(404, "") as RouteResult.Failure).kind)
        assertEquals(RouteFailureKind.INVALID_RESPONSE, (GraphHopperParser.parseHttp(302, "") as RouteResult.Failure).kind)
        // 200 com corpo de erro não vira sucesso.
        assertEquals(
            RouteFailureKind.INVALID_RESPONSE,
            (GraphHopperParser.parseHttp(200, Fixtures.text("graphhopper_error_401.json")) as RouteResult.Failure).kind,
        )
    }

    @Test
    fun `fixtures nao tem coordenadas fora da grade ficticia`() {
        for (name in listOf(Fixtures.TURNS, Fixtures.ROUNDABOUT, Fixtures.UTURN)) {
            val route = Fixtures.route(name)
            assertTrue(route.polyline.points.all { it.lat in -23.51..-23.49 && it.lon in -46.61..-46.59 }, name)
            // Coerência entre instruções e passos.
            val json = kotlinx.serialization.json.Json.parseToJsonElement(Fixtures.text(name)).jsonObject
            val instructions = json["paths"]!!.jsonArray[0].jsonObject["instructions"]!!.jsonArray
            assertEquals(instructions.size, route.steps.size, name)
        }
    }
}
