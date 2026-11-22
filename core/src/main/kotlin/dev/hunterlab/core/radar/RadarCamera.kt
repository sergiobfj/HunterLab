package dev.hunterlab.core.radar

import dev.hunterlab.core.geo.Geo
import dev.hunterlab.core.geo.GeoPoint
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Radar fixo **real**, vindo de uma base aberta (OpenStreetMap). Não depende de rota: é só o dispositivo no mapa.
 * [RouteRadarMatcher] decide se ele está na rota e em que posição.
 *
 * - [id]: identificador estável, ex. `osm:node/123` (o nó do dispositivo; relações `enforcement` também usam o nó).
 * - [maxSpeedKmh]: limite fiscalizado em km/h, ou `null` quando ausente/não numérico.
 * - [directionDeg]: sentido do **tráfego fiscalizado** (0 = norte, horário), quando conhecido.
 * - [directionKnown]: por padrão `directionDeg != null`. `forward`/`backward` dependem da via e não são resolvidos
 *   aqui → `directionDeg = null`, `directionKnown = false`.
 */
data class RadarCamera(
    val id: String,
    val point: GeoPoint,
    val kind: RadarKind,
    val speedLimit: RadarSpeedLimit,
    val directionDeg: Double?,
    val directionKnown: Boolean = directionDeg != null,
    val source: String = SOURCE_OSM,
    /** Orientation of the camera itself, not a proven traffic heading. */
    val facingDirectionDeg: Double? = null,
) {
    /** Compatibility constructor for documented explicit data/fixtures. */
    constructor(
        id: String, point: GeoPoint, kind: RadarKind, maxSpeedKmh: Int?, directionDeg: Double?,
        directionKnown: Boolean = directionDeg != null, source: String = SOURCE_OSM,
    ) : this(id, point, kind, RadarSpeedLimit.recorded(maxSpeedKmh, source), directionDeg, directionKnown, source)

    val maxSpeedKmh: Int? get() = speedLimit.kmh
    init {
        require(id.isNotBlank()) { "Radar sem identificador" }
        require(maxSpeedKmh == null || maxSpeedKmh in 1..MAX_SPEED_KMH) { "Limite fora da faixa: $maxSpeedKmh" }
        require(directionDeg == null || (directionDeg.isFinite() && directionDeg >= 0.0 && directionDeg < 360.0)) {
            "Direção fora de 0..360: $directionDeg"
        }
        require(!directionKnown || directionDeg != null) { "Direção marcada como conhecida, mas sem valor" }
        require(facingDirectionDeg == null || facingDirectionDeg.isFinite() && facingDirectionDeg in 0.0..<360.0)
    }

    companion object {
        const val SOURCE_OSM = "OpenStreetMap"
        const val MAX_SPEED_KMH = 250

        fun osmNodeId(nodeId: Long): String = "osm:node/$nodeId"
    }
}

/** Valor de `direction=*` do OSM já interpretado. */
sealed interface OsmDirection {
    data class Degrees(val deg: Double) : OsmDirection

    /** `forward`/`backward`: relativos ao sentido de desenho da via — não resolvidos sem a geometria da via. */
    data class WayRelative(val raw: String) : OsmDirection

    /** `both`/`all`: fiscaliza os dois sentidos. */
    data object Both : OsmDirection

    data object Absent : OsmDirection

    data class Unrecognized(val raw: String) : OsmDirection
}

/** Interpretação de valores de tags OSM usados por radares. Funções puras. */
object OsmTagValues {
    const val MPH_TO_KMH = 1.609344

    private val CARDINALS: Map<String, Double> = listOf(
        "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
        "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW",
    ).mapIndexed { i, c -> c to i * 22.5 }.toMap()

    private val SPEED = Regex("""^(\d{1,3}(?:\.\d+)?)\s*(km/h|kmh|kph|mph)?$""")

    /**
     * `maxspeed` → km/h inteiro. Aceita `60`, `60 km/h`, `40 mph` (convertido). Valores não numéricos
     * (`BR:urban`, `none`, `signals`), múltiplos (`60;40`) ou fora de 1..[RadarCamera.MAX_SPEED_KMH] → `null`.
     */
    fun parseMaxSpeedKmh(raw: String?): Int? {
        val v = raw?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() } ?: return null
        val m = SPEED.matchEntire(v) ?: return null
        val n = m.groupValues[1].toDoubleOrNull() ?: return null
        val kmh = if (m.groupValues[2] == "mph") n * MPH_TO_KMH else n
        return kmh.roundToInt().takeIf { it in 1..RadarCamera.MAX_SPEED_KMH }
    }

    /** `direction` → graus (numérico ou cardeal de 16 pontos), relativo à via, ambos, ausente ou não reconhecido. */
    fun parseDirection(raw: String?): OsmDirection {
        val v = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return OsmDirection.Absent
        when (v.lowercase(Locale.ROOT)) {
            "forward", "backward" -> return OsmDirection.WayRelative(v.lowercase(Locale.ROOT))
            "both", "all" -> return OsmDirection.Both
        }
        CARDINALS[v.uppercase(Locale.ROOT)]?.let { return OsmDirection.Degrees(it) }
        val numeric = v.removeSuffix("°").trim().toDoubleOrNull()
        if (numeric != null && numeric.isFinite()) return OsmDirection.Degrees(Geo.normalize(numeric))
        return OsmDirection.Unrecognized(v)
    }
}
