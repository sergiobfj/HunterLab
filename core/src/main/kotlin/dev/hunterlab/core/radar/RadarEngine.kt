package dev.hunterlab.core.radar

enum class RadarKind(val label: String) {
    FIXED("Radar fixo"),
    TRAFFIC_LIGHT("Avanço de sinal"),
    AVERAGE_SPEED("Velocidade média"),
    MOBILE("Radar móvel"),
}

/** Radar **fictício**. Não existe base de radares reais nesta versão. */
data class SimulatedRadar(
    val id: String,
    val positionM: Int,
    val kind: RadarKind,
    val speedLimitKmh: Int,
    val label: String,
) {
    init {
        require(positionM >= 0) { "Posição negativa" }
        require(speedLimitKmh > 0) { "Limite deve ser positivo" }
    }

    val isSimulated: Boolean get() = true
}

/**
 * Limiares. Padrões vindos da referência: busca até 700 m, exibição até 300 m.
 * [alertHysteresisM] evita piscar na borda dos 300 m; [passedHoldM] mantém "ultrapassado" visível um pouco.
 */
data class RadarConfig(
    val searchRadiusM: Int = 700,
    val alertRadiusM: Int = 300,
    val alertHysteresisM: Int = 30,
    val passedHoldM: Int = 60,
) {
    init {
        require(alertRadiusM in 1..searchRadiusM) { "Raio de alerta deve estar entre 1 e o raio de busca" }
        require(alertHysteresisM >= 0 && passedHoldM >= 0) { "Valores negativos não são permitidos" }
    }
}

sealed interface RadarPhase {
    val label: String

    data object Clear : RadarPhase {
        override val label: String get() = "Navegação normal"
    }

    /** Dentro do raio de busca, fora do raio de alerta: aparece só no app. */
    data class Detected(val radar: SimulatedRadar, val distanceM: Int) : RadarPhase {
        override val label: String get() = "Radar detectado"
    }

    /** Dentro do raio de alerta: elegível para o Tripper (respeitando a prioridade da navegação). */
    data class Alert(val radar: SimulatedRadar, val distanceM: Int) : RadarPhase {
        override val label: String get() = "Alerta ativo"
    }

    data class Passed(val radar: SimulatedRadar, val passedAtM: Double) : RadarPhase {
        override val label: String get() = "Radar ultrapassado"
    }
}

sealed interface RadarEvent {
    val atPositionM: Double
    val description: String

    data class Detected(val radar: SimulatedRadar, val distanceM: Int, override val atPositionM: Double) : RadarEvent {
        override val description: String get() = "Radar detectado: ${radar.label} a $distanceM m"
    }

    data class AlertStarted(val radar: SimulatedRadar, val distanceM: Int, override val atPositionM: Double) : RadarEvent {
        override val description: String get() = "Alerta ativo: ${radar.kind.label}, limite ${radar.speedLimitKmh} km/h, a $distanceM m"
    }

    data class Passed(val radar: SimulatedRadar, override val atPositionM: Double) : RadarEvent {
        override val description: String get() = "Radar ultrapassado: ${radar.label}"
    }

    data class ReturnedToNormal(val radar: SimulatedRadar?, override val atPositionM: Double) : RadarEvent {
        override val description: String get() = "Retorno à navegação normal"
    }
}

/**
 * Máquina de estados pura: recebe a posição ao longo de uma rota linear e devolve eventos.
 * Determinística e sem relógio — fácil de testar e de alimentar no futuro com outra fonte.
 */
class RadarEngine(radars: List<SimulatedRadar>, val config: RadarConfig = RadarConfig()) {

    val radars: List<SimulatedRadar> = radars.sortedBy { it.positionM }

    var phase: RadarPhase = RadarPhase.Clear
        private set

    fun reset() {
        phase = RadarPhase.Clear
    }

    fun update(positionM: Double): List<RadarEvent> {
        require(positionM.isFinite() && positionM >= 0) { "Posição inválida: $positionM" }
        val events = mutableListOf<RadarEvent>()
        val ahead = radars.firstOrNull { it.positionM >= positionM }
        val aheadDistance = ahead?.let { (it.positionM - positionM).toInt() }

        // 1) Segura "ultrapassado" por um trecho — a menos que outro radar já esteja no raio de alerta.
        (phase as? RadarPhase.Passed)?.let { passed ->
            val holdOver = positionM - passed.passedAtM >= config.passedHoldM || positionM < passed.passedAtM
            val nextIsUrgent = aheadDistance != null && aheadDistance <= config.alertRadiusM
            when {
                holdOver -> {
                    events += RadarEvent.ReturnedToNormal(passed.radar, positionM)
                    phase = RadarPhase.Clear
                }
                nextIsUrgent -> phase = RadarPhase.Clear // vai direto para o próximo alerta
                else -> return events
            }
        }

        // 2) O radar acompanhado ficou para trás.
        val tracked = trackedRadar()
        if (tracked != null && positionM > tracked.positionM) {
            phase = RadarPhase.Passed(tracked, positionM)
            events += RadarEvent.Passed(tracked, positionM)
            return events
        }

        // 3) Avalia o radar mais próximo à frente.
        if (ahead == null || aheadDistance == null || aheadDistance > config.searchRadiusM) {
            if (phase !is RadarPhase.Clear) events += RadarEvent.ReturnedToNormal(tracked, positionM)
            phase = RadarPhase.Clear
            return events
        }
        val same = tracked == ahead
        val wasAlert = phase is RadarPhase.Alert && same
        phase = when {
            aheadDistance <= config.alertRadiusM -> {
                if (!same) events += RadarEvent.Detected(ahead, aheadDistance, positionM)
                if (!wasAlert) events += RadarEvent.AlertStarted(ahead, aheadDistance, positionM)
                RadarPhase.Alert(ahead, aheadDistance)
            }
            wasAlert && aheadDistance <= config.alertRadiusM + config.alertHysteresisM ->
                RadarPhase.Alert(ahead, aheadDistance)
            else -> {
                if (!same) events += RadarEvent.Detected(ahead, aheadDistance, positionM)
                RadarPhase.Detected(ahead, aheadDistance)
            }
        }
        return events
    }

    private fun trackedRadar(): SimulatedRadar? = when (val p = phase) {
        is RadarPhase.Detected -> p.radar
        is RadarPhase.Alert -> p.radar
        else -> null
    }
}
