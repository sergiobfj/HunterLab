package dev.hunterlab.core.route

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/** Fixtures GraphHopper com coordenadas fictícias (grade sintética), em `src/test/resources/route`. */
object Fixtures {
    const val TURNS = "graphhopper_turns.json"
    const val ROUNDABOUT = "graphhopper_roundabout.json"
    const val UTURN = "graphhopper_uturn.json"

    fun text(name: String): String =
        requireNotNull(Fixtures::class.java.getResource("/route/$name")) { "Fixture ausente: $name" }.readText()

    fun route(name: String): Route {
        val result = GraphHopperParser.parse(text(name))
        check(result is RouteResult.Success) { "Fixture $name não gerou rota: $result" }
        return result.route
    }

    /** Copia a fixture alterando `paths[0]`. */
    fun mutatePath(name: String, block: (MutableMap<String, JsonElement>) -> Unit): String {
        val root = Json.parseToJsonElement(text(name)).jsonObject.toMutableMap()
        val path = root["paths"]!!.jsonArray[0].jsonObject.toMutableMap()
        block(path)
        root["paths"] = JsonArray(listOf(JsonObject(path)))
        return JsonObject(root).toString()
    }

    /** Copia a fixture alterando `paths[0].instructions[index]`. */
    fun mutateInstruction(name: String, index: Int, block: (MutableMap<String, JsonElement>) -> Unit): String =
        mutatePath(name) { path ->
            val list = path["instructions"]!!.jsonArray.toMutableList()
            val ins = list[index].jsonObject.toMutableMap()
            block(ins)
            list[index] = JsonObject(ins)
            path["instructions"] = JsonArray(list)
        }
}
