package dev.hunterlab.core.nav

import dev.hunterlab.protocol.Maneuver
import dev.hunterlab.protocol.Side
import dev.hunterlab.protocol.TurnSharpness

/** Trecho que termina numa manobra. */
data class RouteLeg(val lengthM: Int, val maneuverAtEnd: Maneuver) {
    init {
        require(lengthM > 0) { "Trecho precisa ter comprimento positivo" }
    }
}

data class RoutePosition(val maneuver: Maneuver, val distanceToManeuverM: Int, val nextManeuver: Maneuver?)

/** Rota fictícia, linear, para demonstração. */
data class SimulatedRoute(val name: String, val legs: List<RouteLeg>) {
    init {
        require(legs.isNotEmpty()) { "Rota precisa de ao menos um trecho" }
    }

    val totalLengthM: Int = legs.sumOf { it.lengthM }

    fun positionAt(traveledM: Double): RoutePosition {
        require(traveledM >= 0) { "Posição negativa" }
        var legEnd = 0.0
        legs.forEachIndexed { i, leg ->
            legEnd += leg.lengthM
            if (traveledM < legEnd) {
                return RoutePosition(leg.maneuverAtEnd, (legEnd - traveledM).toInt(), legs.getOrNull(i + 1)?.maneuverAtEnd)
            }
        }
        return RoutePosition(legs.last().maneuverAtEnd, 0, null)
    }

    companion object {
        /** Rota de demonstração — dados fictícios. */
        val DEMO = SimulatedRoute(
            name = "Rota demonstrativa (fictícia)",
            legs = listOf(
                RouteLeg(600, Maneuver.Turn(Side.RIGHT)),
                RouteLeg(1_500, Maneuver.Roundabout(2)),
                RouteLeg(2_300, Maneuver.Keep(Side.LEFT)),
                RouteLeg(900, Maneuver.Turn(Side.LEFT, TurnSharpness.SLIGHT)),
                RouteLeg(350, Maneuver.UTurn),
                RouteLeg(450, Maneuver.Arrive(Side.RIGHT)),
            ),
        )
    }
}
