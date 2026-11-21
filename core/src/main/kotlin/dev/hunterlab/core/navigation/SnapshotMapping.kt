package dev.hunterlab.core.navigation

import dev.hunterlab.core.nav.NavigationSnapshot
import dev.hunterlab.core.nav.NavigationStatus
import dev.hunterlab.protocol.Maneuver
import kotlin.math.ceil
import kotlin.math.roundToLong

/**
 * Converte o estado do motor no [NavigationSnapshot] neutro que o `ScreenComposer` transforma em tela do Tripper.
 *
 * - NAVIGATING → NAVIGATING (próxima manobra, distância até ela, manobra seguinte, restante, minutos restantes).
 * - OFF_ROUTE / REROUTING → RECALCULATING.
 * - ARRIVED → ARRIVED.
 * - ROUTING (rota inicial), FAILED, IDLE → IDLE (o Pod mostra o relógio nativo).
 *
 * Distâncias em metros inteiros (arredondadas), minutos arredondados para cima; nunca negativos.
 */
fun NavigationState.toSnapshot(): NavigationSnapshot {
    val source = route?.providerId ?: sourceId
    val track = track
    return when (status) {
        EngineStatus.NAVIGATING -> if (track == null) {
            NavigationSnapshot(NavigationStatus.IDLE, sourceId = source, simulated = false)
        } else {
            NavigationSnapshot(
                status = NavigationStatus.NAVIGATING,
                maneuver = track.nextManeuver,
                distanceToManeuverM = track.distanceToNextManeuverM.toMeters(),
                nextManeuver = track.followingManeuver,
                remainingDistanceM = track.remainingDistanceM.toMeters(),
                remainingTimeMin = track.remainingTimeS.toMinutesCeil(),
                sourceId = source,
                simulated = false,
            )
        }
        EngineStatus.OFF_ROUTE, EngineStatus.REROUTING ->
            NavigationSnapshot(NavigationStatus.RECALCULATING, sourceId = source, simulated = false)
        EngineStatus.ARRIVED -> NavigationSnapshot(
            status = NavigationStatus.ARRIVED,
            maneuver = track?.nextManeuver as? Maneuver.Arrive ?: Maneuver.Arrive(),
            distanceToManeuverM = 0,
            nextManeuver = null,
            remainingDistanceM = 0,
            remainingTimeMin = 0,
            sourceId = source,
            simulated = false,
        )
        EngineStatus.ROUTING, EngineStatus.FAILED, EngineStatus.IDLE ->
            NavigationSnapshot(NavigationStatus.IDLE, sourceId = source, simulated = false)
    }
}

internal fun Double.toMeters(): Int =
    if (isNaN() || this <= 0.0) 0 else roundToLong().coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

internal fun Double.toMinutesCeil(): Int =
    if (isNaN() || this <= 0.0) 0 else ceil(this / 60.0).toLong().coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
