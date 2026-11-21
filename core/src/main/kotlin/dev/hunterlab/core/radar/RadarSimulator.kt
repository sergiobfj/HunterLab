package dev.hunterlab.core.radar

/** Dados **fictícios**, identificados como simulação. Nenhum radar real é conhecido pelo app. */
object FictitiousRadars {
    const val ROUTE_LENGTH_M: Int = 5_000

    val DEFAULT: List<SimulatedRadar> = listOf(
        SimulatedRadar("SIM-01", 900, RadarKind.FIXED, 60, "Radar fictício SIM-01"),
        SimulatedRadar("SIM-02", 2_100, RadarKind.TRAFFIC_LIGHT, 50, "Radar fictício SIM-02"),
        // 250 m depois do anterior: exercita radares consecutivos.
        SimulatedRadar("SIM-03", 2_350, RadarKind.FIXED, 50, "Radar fictício SIM-03"),
        SimulatedRadar("SIM-04", 3_800, RadarKind.AVERAGE_SPEED, 80, "Radar fictício SIM-04"),
    )
}

/**
 * Motociclista simulado andando numa rota linear a velocidade constante.
 * O tempo é passado explicitamente em [step] (sem relógio interno).
 */
class RadarSimulator(
    radars: List<SimulatedRadar> = FictitiousRadars.DEFAULT,
    val routeLengthM: Int = FictitiousRadars.ROUTE_LENGTH_M,
    config: RadarConfig = RadarConfig(),
) {
    init {
        require(routeLengthM > 0) { "Rota precisa ter comprimento positivo" }
    }

    val engine = RadarEngine(radars, config)

    var positionM: Double = 0.0
        private set

    var speedKmh: Int = 60
        set(value) {
            require(value in 1..250) { "Velocidade fora da faixa: $value" }
            field = value
        }

    val finished: Boolean get() = positionM >= routeLengthM
    val phase: RadarPhase get() = engine.phase

    /** Avança [elapsedMs] de tempo simulado multiplicado por [timeScale]. */
    fun step(elapsedMs: Long, timeScale: Double = 1.0): List<RadarEvent> {
        require(elapsedMs >= 0) { "Tempo negativo" }
        require(timeScale > 0) { "Escala de tempo deve ser positiva" }
        if (finished) return emptyList()
        val meters = speedKmh / 3.6 * (elapsedMs / 1000.0) * timeScale
        positionM = (positionM + meters).coerceAtMost(routeLengthM.toDouble())
        return engine.update(positionM)
    }

    fun seek(positionM: Double): List<RadarEvent> {
        require(positionM in 0.0..routeLengthM.toDouble()) { "Posição fora da rota" }
        this.positionM = positionM
        engine.reset()
        return engine.update(positionM)
    }

    fun reset() {
        positionM = 0.0
        engine.reset()
    }
}
