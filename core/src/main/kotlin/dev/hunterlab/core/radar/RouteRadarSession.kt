package dev.hunterlab.core.radar

import dev.hunterlab.core.geo.Polyline

/**
 * Alertas de radares **reais** durante uma navegação. Pura e sem relógio: o app informa a rota, as câmeras dos
 * tiles + a cobertura, e a posição ao longo da rota ([update], vinda do rastreador de navegação).
 *
 * - Reaproveita o [RadarEngine] (limiares de [RadarConfig], padrão 700/300 m) sobre os radares casados por
 *   [RouteRadarMatcher]. [phase] vai direto para o `ScreenComposer`.
 * - Eventos: [RadarEvent.Detected] (detectado), [RadarEvent.AlertStarted] (aproximação), [RadarEvent.Passed]
 *   (ultrapassado), [RadarEvent.ReturnedToNormal] (encerrado). Cada câmera é anunciada no máximo uma vez por
 *   aproximação, inclusive através de recálculos de rota e de chegada de dados novos.
 * - Cobertura [CoverageStatus.NONE]: nunca alerta; [statusText] = "Sem dados de radar".
 * - Recálculo ([setRoute] com rota já ativa): uma câmera ultrapassada há até [rerouteGuardM] m de percurso não
 *   é anunciada de novo se reaparecer nos primeiros [rerouteGuardM] m da rota nova.
 * - Recuos de até [backtrackToleranceM] m na posição (ruído) são ignorados; recuos maiores valem.
 */
class RouteRadarSession(
    val config: RadarConfig = RadarConfig(),
    val matcher: RouteRadarMatcher = RouteRadarMatcher(),
    val rerouteGuardM: Int = config.searchRadiusM,
    val backtrackToleranceM: Int = 30,
) {
    init {
        require(rerouteGuardM >= 0 && backtrackToleranceM >= 0) { "Valores negativos não são permitidos" }
    }

    private enum class Stage { DETECTED, ALERTED }
    private enum class Rebuild { NONE, DATA, REROUTE }

    var route: Polyline? = null
        private set

    var coverage: CoverageStatus = CoverageStatus.NONE
        private set

    val statusText: String get() = coverage.label

    /** Sem dados de radar, nenhum alerta é possível: a interface deve deixar isso claro. */
    val alertsEnabled: Boolean get() = route != null && coverage != CoverageStatus.NONE

    var phase: RadarPhase = RadarPhase.Clear
        private set

    /** Radares casados com a rota atual, por posição. */
    var matches: List<RouteRadarMatch> = emptyList()
        private set

    private var engine: RadarEngine? = null
    private var pending = Rebuild.NONE
    private var closing: RouteRadar? = null
    private val cameraOfRadar = HashMap<String, String>()
    private val announced = HashMap<String, Stage>()
    private val passedAtOdometer = HashMap<String, Double>()
    private var odometerM = 0.0
    private var lastAlongM: Double? = null

    /** Nova rota (início ou recálculo). O efeito aparece no próximo [update]. */
    fun setRoute(route: Polyline, cameras: Collection<RadarCamera>, coverage: CoverageStatus) {
        val reroute = this.route != null
        this.route = route
        lastAlongM = null
        load(cameras, coverage)
        pending = if (reroute) Rebuild.REROUTE else maxOf(pending, Rebuild.DATA)
    }

    /** Dados novos para a **mesma** rota (tiles baixados ou atualizados no meio do caminho). */
    fun updateData(cameras: Collection<RadarCamera>, coverage: CoverageStatus) {
        load(cameras, coverage)
        if (route != null) pending = maxOf(pending, Rebuild.DATA)
    }

    /** Conveniência: câmeras de vários tiles, sem repetir. */
    fun setRouteFromTiles(route: Polyline, tiles: Collection<RadarTileData>, coverage: CoverageStatus) =
        setRoute(route, RadarTileData.mergeCameras(tiles), coverage)

    fun matchFor(radar: RouteRadar): RouteRadarMatch? = matches.firstOrNull { it.radarId == radar.id }

    fun update(alongM: Double): List<RadarEvent> {
        require(alongM.isFinite()) { "Posição inválida: $alongM" }
        val raw = alongM.coerceAtLeast(0.0)
        val last = lastAlongM
        // Recuo pequeno = ruído do GPS: segura a posição (não "desultrapassa" o radar nem pisca o alerta).
        val pos = if (last != null && raw < last && last - raw <= backtrackToleranceM) last else raw
        if (last != null && pos > last) odometerM += pos - last
        lastAlongM = pos
        val events = mutableListOf<RadarEvent>()
        closing?.let {
            events += RadarEvent.ReturnedToNormal(it, pos)
            closing = null
        }
        if (route == null) return events

        if (pending != Rebuild.NONE) {
            val reroute = pending == Rebuild.REROUTE
            pending = Rebuild.NONE
            engine = if (coverage == CoverageStatus.NONE) null else RadarEngine(radarsFor(pos, reroute), config)
            val eng = engine ?: return events
            val prev = phase
            val raw = eng.update(pos)
            val next = eng.phase
            val prevRadar = radarOf(prev)
            val prevCam = prevRadar?.let(::cameraIdOf)
            if (prevRadar != null && prevCam != radarOf(next)?.let(::cameraIdOf)) {
                // A aproximação anterior não continua na rota/dados novos: encerra explicitamente.
                if (prev !is RadarPhase.Passed || next is RadarPhase.Clear) events += RadarEvent.ReturnedToNormal(prevRadar, pos)
                prevCam?.let { announced.remove(it) }
            }
            events += deduplicate(raw)
            phase = next
            return events
        }

        val eng = engine
        if (eng == null) {
            phase = RadarPhase.Clear
            return events
        }
        events += deduplicate(eng.update(pos))
        phase = eng.phase
        return events
    }

    /** Fim da navegação: encerra o alerta em curso e volta ao estado inicial. */
    fun end(): List<RadarEvent> {
        val pos = lastAlongM ?: 0.0
        val events = listOfNotNull(closing, radarOf(phase)).map { RadarEvent.ReturnedToNormal(it, pos) }
        route = null
        coverage = CoverageStatus.NONE
        phase = RadarPhase.Clear
        matches = emptyList()
        engine = null
        pending = Rebuild.NONE
        closing = null
        cameraOfRadar.clear()
        announced.clear()
        passedAtOdometer.clear()
        odometerM = 0.0
        lastAlongM = null
        return events
    }

    private fun load(cameras: Collection<RadarCamera>, coverage: CoverageStatus) {
        this.coverage = coverage
        val r = route
        matches = if (r == null || coverage == CoverageStatus.NONE) emptyList() else matcher.match(r, cameras)
        matches.forEach { cameraOfRadar[it.radarId] = it.camera.id }
        if (coverage == CoverageStatus.NONE) {
            // Sem dados: nenhum alerta pode continuar visível.
            radarOf(phase)?.let { closing = it }
            phase = RadarPhase.Clear
            engine = null
            announced.clear()
        }
    }

    private fun radarsFor(pos: Double, reroute: Boolean): List<RouteRadar> {
        if (!reroute) return matches.map { it.toRouteRadar() }
        val considered = HashSet<String>()
        return matches.filter { m ->
            val cam = m.camera.id
            if (m.alongM < pos) return@filter true // já atrás na rota nova: o motor ignora
            val firstAhead = considered.add(cam)
            val passedOdo = passedAtOdometer[cam]
            val suppress = firstAhead && passedOdo != null &&
                odometerM - passedOdo <= rerouteGuardM && m.alongM - pos <= rerouteGuardM
            !suppress
        }.map { it.toRouteRadar() }
    }

    private fun deduplicate(raw: List<RadarEvent>): List<RadarEvent> = raw.filter { e ->
        when (e) {
            is RadarEvent.Detected -> {
                val cam = cameraIdOf(e.radar)
                if (cam in announced) false else true.also { announced[cam] = Stage.DETECTED }
            }
            is RadarEvent.AlertStarted -> {
                val cam = cameraIdOf(e.radar)
                if (announced[cam] == Stage.ALERTED) false else true.also { announced[cam] = Stage.ALERTED }
            }
            is RadarEvent.Passed -> {
                val cam = cameraIdOf(e.radar)
                announced.remove(cam)
                passedAtOdometer[cam] = odometerM
                true
            }
            is RadarEvent.ReturnedToNormal -> {
                e.radar?.let { announced.remove(cameraIdOf(it)) }
                true
            }
        }
    }

    private fun cameraIdOf(radar: RouteRadar): String = cameraOfRadar[radar.id] ?: radar.id

    private fun radarOf(p: RadarPhase): RouteRadar? = when (p) {
        is RadarPhase.Detected -> p.radar
        is RadarPhase.Alert -> p.radar
        is RadarPhase.Passed -> p.radar
        RadarPhase.Clear -> null
    }
}
