package dev.hunterlab.app.ui.simulation

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.hunterlab.core.display.BottomLineMode
import dev.hunterlab.core.display.ComposedFrame
import dev.hunterlab.core.display.DayNightMode
import dev.hunterlab.core.display.DisplaySettings
import dev.hunterlab.core.display.ScreenComposer
import dev.hunterlab.core.nav.FutureNavigationProvider
import dev.hunterlab.core.nav.NavigationProvider
import dev.hunterlab.core.nav.NavigationSnapshot
import dev.hunterlab.core.nav.NavigationStatus
import dev.hunterlab.core.nav.ProviderInfo
import dev.hunterlab.core.nav.SimulatedNavigationProvider
import dev.hunterlab.core.nav.SimulatedRoute
import dev.hunterlab.core.radar.RadarConfig
import dev.hunterlab.core.radar.RadarEvent
import dev.hunterlab.core.radar.RadarPhase
import dev.hunterlab.core.radar.RadarSimulator
import dev.hunterlab.core.radar.SimulatedRadar
import dev.hunterlab.protocol.Maneuver
import dev.hunterlab.protocol.RadarDisplayMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.temporal.ChronoUnit

enum class RadarLogKind { DETECTED, ALERT, PASSED, NORMAL }

data class RadarLogEntry(val id: Long, val simTimeMs: Long, val positionM: Double, val text: String, val kind: RadarLogKind)

data class RadarUi(
    val positionM: Double,
    val routeLengthM: Int,
    val speedKmh: Int,
    val timeScale: Double,
    val running: Boolean,
    val phase: RadarPhase,
    val radars: List<SimulatedRadar>,
    val config: RadarConfig,
    val log: List<RadarLogEntry>,
)

data class DemoUi(val running: Boolean, val progress: Double, val speedKmh: Int, val timeScale: Double)

data class SimulationUiState(
    val nav: NavigationSnapshot,
    val settings: DisplaySettings,
    val now: LocalTime,
    val frame: ComposedFrame,
    val radar: RadarUi,
    val demo: DemoUi,
    val providers: List<ProviderInfo>,
)

/**
 * Estado compartilhado pelas abas Navegação e Radares. Tudo é simulado e local:
 * nenhum GPS, nenhuma rede, nenhum envio ao Tripper.
 */
class SimulationViewModel : ViewModel() {
    private val clock: () -> Long = SystemClock::elapsedRealtime

    private val navigation = SimulatedNavigationProvider(clock)
    private val providers: List<NavigationProvider> = listOf(navigation, FutureNavigationProvider())

    private val settings = MutableStateFlow(DisplaySettings())
    private val now = MutableStateFlow(currentMinute())

    private val radarSim = RadarSimulator()
    private var simElapsedMs = 0L
    private var logId = 0L
    private val radar = MutableStateFlow(radarUi(running = false, timeScale = 1.0, log = emptyList()))
    private val demo = MutableStateFlow(DemoUi(running = false, progress = 0.0, speedKmh = 60, timeScale = 5.0))

    val ui: StateFlow<SimulationUiState> = combine(navigation.snapshots, settings, now, radar, demo) { nav, s, t, r, d ->
        build(nav, s, t, r, d)
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        build(navigation.snapshots.value, settings.value, now.value, radar.value, demo.value),
    )

    init {
        viewModelScope.launch {
            var last = clock()
            while (isActive) {
                delay(TICK_MS)
                val t = clock()
                val dt = t - last
                last = t
                navigation.tick()
                now.value = currentMinute()
                if (navigation.isDemoRunning) {
                    val d = demo.value
                    navigation.advanceDemo(d.speedKmh / 3.6 * (dt / 1000.0) * d.timeScale)
                }
                demo.update { it.copy(running = navigation.isDemoRunning, progress = navigation.demoProgress) }
                if (radar.value.running) stepRadar(dt)
            }
        }
    }

    private fun build(nav: NavigationSnapshot, s: DisplaySettings, t: LocalTime, r: RadarUi, d: DemoUi) = SimulationUiState(
        nav = nav,
        settings = s,
        now = t,
        frame = ScreenComposer.compose(nav, r.phase, s, t),
        radar = r,
        demo = d,
        providers = providers.map { it.info },
    )

    // ---------------------------------------------------------------- Navegação

    fun setManeuver(m: Maneuver) = navigation.setManeuver(m)
    fun setNextManeuver(m: Maneuver?) = navigation.setNextManeuver(m)
    fun setDistance(meters: Int) = navigation.setDistanceToManeuver(meters)
    fun setRemainingDistance(meters: Int) = navigation.setRemaining(meters, navigation.snapshots.value.remainingTimeMin)
    fun setRemainingTime(minutes: Int) = navigation.setRemaining(navigation.snapshots.value.remainingDistanceM, minutes)
    fun startRoute() = navigation.startRoute()
    fun recalculate() = navigation.recalculate()
    fun arrive() = navigation.arrive()
    fun stopRoute() = navigation.stopRoute()

    fun startDemo() = navigation.startDemo(SimulatedRoute.DEMO, demo.value.speedKmh)
    fun setDemoTimeScale(scale: Double) = demo.update { it.copy(timeScale = scale) }

    /** Para ver o alerta sobreposto, a navegação precisa estar ativa. */
    fun ensureNavigating() {
        if (navigation.snapshots.value.status == NavigationStatus.IDLE) {
            navigation.setManeuver(navigation.snapshots.value.maneuver ?: Maneuver.Straight)
        }
    }

    // ---------------------------------------------------------------- Exibição

    fun setDayNight(mode: DayNightMode) = settings.update { it.copy(dayNightMode = mode) }
    fun setBottomLine(mode: BottomLineMode) = settings.update { it.copy(bottomLineMode = mode) }
    fun setIntensity(enabled: Boolean) = settings.update { it.copy(intensityByDistance = enabled) }
    fun setRadarOnTripper(enabled: Boolean) = settings.update { it.copy(radarOnTripper = enabled) }
    fun setRadarMode(mode: RadarDisplayMode) = settings.update { it.copy(radarMode = mode) }

    // ---------------------------------------------------------------- Radares (fictícios)

    fun radarPlay() {
        if (radarSim.finished) radarReset()
        radar.update { it.copy(running = true) }
    }

    fun radarPause() = radar.update { it.copy(running = false) }

    fun radarReset() {
        radarSim.reset()
        simElapsedMs = 0
        radar.value = radarUi(running = false, timeScale = radar.value.timeScale, log = emptyList())
    }

    fun radarSpeed(kmh: Int) {
        radarSim.speedKmh = kmh
        radar.update { it.copy(speedKmh = kmh) }
    }

    fun radarTimeScale(scale: Double) = radar.update { it.copy(timeScale = scale) }

    fun radarSeek(positionM: Double) {
        val events = radarSim.seek(positionM.coerceIn(0.0, radarSim.routeLengthM.toDouble()))
        publish(events)
    }

    private fun stepRadar(dtMs: Long) {
        val scale = radar.value.timeScale
        simElapsedMs += (dtMs * scale).toLong()
        publish(radarSim.step(dtMs, scale))
        if (radarSim.finished) radar.update { it.copy(running = false) }
    }

    private fun publish(events: List<RadarEvent>) {
        val entries = events.map { e ->
            val kind = when (e) {
                is RadarEvent.Detected -> RadarLogKind.DETECTED
                is RadarEvent.AlertStarted -> RadarLogKind.ALERT
                is RadarEvent.Passed -> RadarLogKind.PASSED
                is RadarEvent.ReturnedToNormal -> RadarLogKind.NORMAL
            }
            RadarLogEntry(logId++, simElapsedMs, e.atPositionM, e.description, kind)
        }
        radar.update {
            it.copy(positionM = radarSim.positionM, phase = radarSim.phase, log = (entries.reversed() + it.log).take(MAX_LOG))
        }
    }

    private fun radarUi(running: Boolean, timeScale: Double, log: List<RadarLogEntry>) = RadarUi(
        positionM = radarSim.positionM,
        routeLengthM = radarSim.routeLengthM,
        speedKmh = radarSim.speedKmh,
        timeScale = timeScale,
        running = running,
        phase = radarSim.phase,
        radars = radarSim.engine.radars,
        config = radarSim.engine.config,
        log = log,
    )

    private fun currentMinute(): LocalTime = LocalTime.now().truncatedTo(ChronoUnit.MINUTES)

    companion object {
        private const val TICK_MS = 100L
        private const val MAX_LOG = 60
    }
}
