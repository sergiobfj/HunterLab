package dev.hunterlab.core.radar

/** Evidence is about a mapped camera, never the speed of an adjacent road segment. */
data class RadarLimitObservation(
    val source: String,
    val raw: String?,
    val conditional: String? = null,
    val estimated: Boolean = false,
    val usable: Boolean = true,
) {
    init { require(source.isNotBlank()) }
    val kmh: Int? get() = OsmTagValues.parseMaxSpeedKmh(raw)
}

enum class RadarLimitStatus(val label: String) {
    RECORDED("Limite cadastrado"),
    MISSING("Limite indisponível"),
    CONFLICT("Limites divergentes"),
    CONDITIONAL("Limite condicionado"),
    ESTIMATED("Limite estimado, não confirmado"),
}

/** A number is exposed only when explicit, unconditional observations agree. No enforcement tolerance. */
class RadarSpeedLimit private constructor(
    val status: RadarLimitStatus,
    val kmh: Int?,
    val observations: List<RadarLimitObservation>,
) {
    init {
        require((status == RadarLimitStatus.RECORDED) == (kmh != null))
        require(kmh == null || kmh in 1..RadarCamera.MAX_SPEED_KMH)
    }
    val sources: List<String> get() = observations.map { it.source }.distinct()
    override fun equals(other: Any?): Boolean = other is RadarSpeedLimit &&
        status == other.status && kmh == other.kmh && observations == other.observations
    override fun hashCode(): Int = 31 * (31 * status.hashCode() + (kmh ?: 0)) + observations.hashCode()
    override fun toString(): String = "RadarSpeedLimit($status, $kmh)"

    companion object {
        fun recorded(kmh: Int?, source: String = RadarCamera.SOURCE_OSM): RadarSpeedLimit {
            require(kmh == null || kmh in 1..RadarCamera.MAX_SPEED_KMH)
            return resolve(listOf(RadarLimitObservation(source, kmh?.toString())))
        }

        fun resolve(observations: Collection<RadarLimitObservation>): RadarSpeedLimit {
            val evidence = observations.distinct().toList()
            val active = evidence.filter { it.usable }
            val values = active.mapNotNull { it.kmh }.toSet()
            val status = when {
                active.any { !it.conditional.isNullOrBlank() } -> RadarLimitStatus.CONDITIONAL
                values.size > 1 -> RadarLimitStatus.CONFLICT
                active.any { it.estimated } -> RadarLimitStatus.ESTIMATED
                active.any { !it.raw.isNullOrBlank() && it.kmh == null } -> RadarLimitStatus.MISSING
                values.size == 1 -> RadarLimitStatus.RECORDED
                else -> RadarLimitStatus.MISSING
            }
            return RadarSpeedLimit(status, values.singleOrNull().takeIf { status == RadarLimitStatus.RECORDED }, evidence)
        }
    }
}
