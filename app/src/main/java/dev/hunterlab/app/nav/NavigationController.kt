package dev.hunterlab.app.nav

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import dev.hunterlab.app.BuildConfigInfo
import dev.hunterlab.app.ble.BleController
import dev.hunterlab.app.ble.BlePermissions
import dev.hunterlab.app.net.HttpsClient
import dev.hunterlab.app.radar.RadarRepository
import dev.hunterlab.app.settings.AppPreferences
import dev.hunterlab.app.ui.contract.DestinationUi
import dev.hunterlab.app.ui.contract.GpsUi
import dev.hunterlab.app.ui.contract.HomeUi
import dev.hunterlab.app.ui.contract.ManeuverUi
import dev.hunterlab.app.ui.contract.NavActions
import dev.hunterlab.app.ui.contract.NavBindings
import dev.hunterlab.app.ui.contract.NavPhaseUi
import dev.hunterlab.app.ui.contract.NavUi
import dev.hunterlab.app.ui.contract.PermissionsUi
import dev.hunterlab.app.ui.contract.RadarUi
import dev.hunterlab.app.ui.contract.RouteSummaryUi
import dev.hunterlab.app.ui.contract.SearchUi
import dev.hunterlab.app.ui.contract.SettingsUi
import dev.hunterlab.app.ui.contract.TripperUi
import dev.hunterlab.app.ui.contract.TripperUiStatus
import dev.hunterlab.core.ble.CandidateLevel
import dev.hunterlab.core.ble.TripperCandidateClassifier
import dev.hunterlab.core.display.BottomLineMode
import dev.hunterlab.core.display.DayNightMode
import dev.hunterlab.core.display.DisplaySettings
import dev.hunterlab.core.display.TripperDisplayController
import dev.hunterlab.core.display.TripperDisplayStatus
import dev.hunterlab.core.geo.CoordinateParser
import dev.hunterlab.core.geo.GeoPoint
import dev.hunterlab.core.geo.ParseResult
import dev.hunterlab.core.link.CommandResult
import dev.hunterlab.core.link.ConnectOptions
import dev.hunterlab.core.link.LinkSnapshot
import dev.hunterlab.core.link.LinkState
import dev.hunterlab.core.link.SessionPurpose
import dev.hunterlab.core.link.TransmissionAck
import dev.hunterlab.core.navigation.EngineStatus
import dev.hunterlab.core.navigation.LocationFix
import dev.hunterlab.core.navigation.NavigationEffect
import dev.hunterlab.core.navigation.NavigationEngine
import dev.hunterlab.core.navigation.RouteDriveSimulator
import dev.hunterlab.core.radar.RadarConfig
import dev.hunterlab.core.radar.RadarPhase
import dev.hunterlab.core.radar.RouteRadarSession
import dev.hunterlab.core.route.GraphHopperRouteProvider
import dev.hunterlab.core.route.Route
import dev.hunterlab.core.route.RouteRequest
import dev.hunterlab.core.route.RouteResult
import dev.hunterlab.core.route.RouteChoicesResult
import dev.hunterlab.core.route.PreparedRoutePlan
import dev.hunterlab.core.search.MapTilerPlaceSearch
import dev.hunterlab.core.search.PlaceSearchResult
import dev.hunterlab.app.ui.contract.PlannerUi
import dev.hunterlab.app.ui.contract.MapRadarUi
import dev.hunterlab.app.ui.contract.RouteOptionUi
import dev.hunterlab.core.radar.RouteRadarMatcher
import dev.hunterlab.protocol.ManeuverMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Orquestra a navegação do MVP (Agente A): destino → rota (GraphHopper) → GPS → [NavigationEngine] → radar
 * ([RouteRadarSession]) → tela do Tripper ([TripperDisplayController] → TripperLink em sessão de NAVEGAÇÃO).
 *
 * Contrato de thread: tudo na thread principal (o motor não é thread-safe); rede e disco em [io].
 * Segurança: cada passo do Tripper é um toque do usuário; PIN nunca é guardado nem tentado automaticamente;
 * a navegação continua no celular sem o Tripper.
 */
class NavigationController(context: Context, private val ble: BleController) : NavActions {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val mainExecutor = ContextCompat.getMainExecutor(app)
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "hunterlab-io").apply { isDaemon = true } }

    /** Rotas num executor próprio: um download longo de radares não pode atrasar um recálculo. */
    private val routeIo = Executors.newSingleThreadExecutor { r -> Thread(r, "hunterlab-route").apply { isDaemon = true } }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val prefs = AppPreferences(app)
    private val http = HttpsClient(BuildConfigInfo.VERSION)
    private val provider = GraphHopperRouteProvider(http, apiKey = { prefs.routingApiKey })
    private val location = AndroidLocationSource(app, mainExecutor)
    private val search = MapTilerPlaceSearch(http) { prefs.mapApiKey }
    private val searchIo = Executors.newSingleThreadExecutor { r -> Thread(r, "hunterlab-search").apply { isDaemon = true } }
    private var placeSearchGeneration = 0
    private var searchFuture: java.util.concurrent.Future<*>? = null
    private var routePlan: PreparedRoutePlan? = null
    private var planNotice: String? = null
    private var planningFuture: java.util.concurrent.Future<*>? = null
    private var planningGeneration = 0
    private var mapRevision = 0
    private var plannerOpen = false
    private var previewRadars: List<MapRadarUi> = emptyList()
    private var previewRadarStatus = "Selecione uma rota para consultar radares."
    private val radarRepo = RadarRepository(app, http, io)
    private val display = TripperDisplayController(ble.lab)

    private var engine = NavigationEngine()
    private val radarVoice = dev.hunterlab.core.radar.RadarVoice(dev.hunterlab.app.radar.AndroidRadarSpeech(app), SystemClock::elapsedRealtime)
    private var radar = newRadarSession()
    private var destination: DestinationUi? = null
    private var previewRoute: Route? = null
    private var previewOrigin: GeoPoint? = null
    private var previewDest: GeoPoint? = null
    private var searchGen = 0
    private var firstCandidateAtMs: Long? = null
    private var serviceRunning = false
    private var arrivalScheduled = false
    private var navGen = 0
    private var previewAtMs = 0L
    private var routingPreview = false
    private var routeError: String? = null
    private var lastFix: LocationFix? = null
    private var gpsEnabled = true
    private var locationRunning = false
    private var navigating = false
    private var simulated = false
    private var simulator: Iterator<LocationFix>? = null
    private var radarRoute: Route? = null
    private var displayStatus = TripperDisplayStatus.NOT_CONNECTED
    private var tripperSearch: String? = null
    private var tripperMessage: String? = null
    private var searchState = SearchUi()
    private var lastPreview: dev.hunterlab.core.display.TripperPreview? = null
    private var ticking = false

    private val _home = MutableStateFlow(HomeUi())
    private val _nav = MutableStateFlow(NavUi())
    private val _settings = MutableStateFlow(SettingsUi())
    private val _notification = MutableStateFlow("Preparando navegação…")
    private val _serviceNeeded = MutableStateFlow(false)

    val notificationText: StateFlow<String> = _notification.asStateFlow()
    val serviceNeeded: StateFlow<Boolean> = _serviceNeeded.asStateFlow()
    val bindings = NavBindings(_home.asStateFlow(), _nav.asStateFlow(), _settings.asStateFlow(), this)

    init {
        scope.launch { ble.lab.snapshot.collect { publish() } }
        scope.launch { ble.scanner.state.collect { onScanUpdate() } }
        publish()
    }

    // ============================================================================ destino e rota

    override fun search(query: String) {
        if (navigating) {
            searchState = SearchUi(query = query.take(160), error = "Encerre a navegação para alterar o destino.")
            publish(); return
        }
        val gen = ++placeSearchGeneration
        searchFuture?.cancel(true)
        val q = query.take(160)
        searchState = SearchUi(query = q, searching = q.trim().length >= 3)
        publish()
        if (q.trim().length < 3) return
        main.postDelayed({
            if (gen != placeSearchGeneration) return@postDelayed
            val near = freshFix(PREVIEW_FIX_MAX_AGE_MS)?.takeIf { location.hasPermission() }?.point
            searchFuture = searchIo.submit {
                val result = search.search(q, near)
                main.post {
                    if (gen != placeSearchGeneration || navigating) return@post
                    searchState = when (result) {
                        is PlaceSearchResult.Found -> SearchUi(query = q, results = result.places.map {
                            DestinationUi(it.name, it.point.lat, it.point.lon, it.detail,
                                near?.let { origin -> Format.distance(dev.hunterlab.core.geo.Geo.distanceM(origin, it.point)) + " em linha reta" })
                        })
                        is PlaceSearchResult.Failed -> SearchUi(query = q, error = result.message)
                    }
                    publish()
                }
            }
        }, 400)
    }

    override fun openPlanner() { plannerOpen = true; ensureLocation(); publish() }
    override fun closePlanner() {
        plannerOpen = false
        ++placeSearchGeneration
        searchFuture?.cancel(true)
        searchState = SearchUi()
        if (!navigating) { location.stop(); locationRunning = false }
        publish()
    }
    override fun mapStyleJson(): String? = dev.hunterlab.app.ui.map.MapStyleConfig.style(prefs.mapApiKey)
    override fun setMapKey(key: String) { prefs.mapApiKey = key; mapRevision++; publish() }
    override fun clearMapKey() { prefs.mapApiKey = null; mapRevision++; search(""); publish() }
    override fun toggleFavorite(destination: DestinationUi) { prefs.toggleFavorite(destination); publish() }

    override fun selectDestination(destination: DestinationUi) {
        if (navigating) return
        ++planningGeneration
        planningFuture?.cancel(true)
        routingPreview = false
        ++placeSearchGeneration
        searchFuture?.cancel(true)
        routePlan = null
        planNotice = null
        previewRadars = emptyList()
        previewRadarStatus = "Selecione uma rota para consultar radares."
        this.destination = destination
        previewRoute = null
        routeError = null
        searchState = SearchUi()
        prefs.addRecent(destination)
        ensureLocation()
        publish()
        if (prefs.routingApiKey != null) previewRoute()
    }

    override fun setDestinationFromText(text: String): String? = when (val r = CoordinateParser.parse(text)) {
        is ParseResult.Error -> r.message
        is ParseResult.Ok -> {
            val p = r.place.point
            selectDestination(DestinationUi(r.place.label ?: "%.5f, %.5f".format(Locale.ROOT, p.lat, p.lon), p.lat, p.lon))
            null
        }
    }

    /** Link `geo:` recebido de outro app. */
    fun onExternalDestination(uri: String) {
        setDestinationFromText(uri)?.let { routeError = it; publish() }
    }

    override fun clearDestination() {
        if (navigating) return
        ++planningGeneration
        planningFuture?.cancel(true)
        routingPreview = false
        routePlan = null
        planNotice = null
        radarRepo.cancel()
        previewRadars = emptyList()
        destination = null
        previewRoute = null
        routeError = null
        publish()
    }

    override fun previewRoute() {
        if (navigating) return
        val dest = destination ?: return
        val gen = ++planningGeneration
        planningFuture?.cancel(true)
        routingPreview = false
        routeError = null
        if (prefs.routingApiKey == null) {
            routeError = "Cadastre a chave GraphHopper em Configurações."
            publish(); return
        }
        val origin = freshFix(PREVIEW_FIX_MAX_AGE_MS)?.takeIf { location.hasPermission() }?.point
        if (origin == null) {
            ensureLocation()
            routeError = if (location.hasPermission()) "Aguardando a localização atual. Toque em Calcular rota quando o GPS estiver pronto."
                else "Permita a localização em Configurações para calcular a rota. O mapa e a busca continuam disponíveis."
            publish(); return
        }
        routingPreview = true
        routePlan = null
        planNotice = null
        previewRoute = null
        previewRadars = emptyList()
        radarRepo.cancel()
        publish()
        val request = RouteRequest(origin, GeoPoint(dest.lat, dest.lon))
        planningFuture = routeIo.submit {
            val result = provider.alternatives(request)
            main.post {
                if (gen != planningGeneration || destination !== dest || navigating) return@post
                routingPreview = false
                when (result) {
                    is RouteChoicesResult.Success -> {
                        planNotice = result.notice
                        routePlan = PreparedRoutePlan(result.routes, origin, SystemClock.elapsedRealtime())
                        previewOrigin = origin
                        previewDest = request.destination
                        previewAtMs = SystemClock.elapsedRealtime()
                        selectRoute(0)
                    }
                    is RouteChoicesResult.Failure -> routeError = result.failure.message
                }
                publish()
            }
        }
    }

    override fun selectRoute(index: Int) {
        if (navigating || routePlan?.select(index) != true) return
        previewRoute = routePlan!!.selected
        previewRadars = emptyList()
        routeError = null
        preparePreviewRadars(previewRoute!!)
        publish()
    }

    private fun preparePreviewRadars(route: Route) {
        radarRepo.cancel()
        if (!prefs.radarEnabled) { previewRadarStatus = "Radares desativados."; return }
        previewRadarStatus = "Consultando radares desta rota…"
        radarRepo.prepare(route.polyline, System::currentTimeMillis) { data ->
            val matches = RouteRadarMatcher().match(route.polyline, data.tiles.flatMap { it.cameras })
            main.post {
                if (navigating || previewRoute !== route) return@post
                previewRadars = matches.distinctBy { it.camera.id }.map { match ->
                    val c = match.camera
                    MapRadarUi(c.id, c.point, c.maxSpeedKmh, c.speedLimit.status.label, c.source, !match.directionConfirmed)
                }
                previewRadarStatus = "${previewRadars.size} radares cadastrados na rota · ${data.coverage.label}. Cobertura limitada."
                publish()
            }
        }
    }

    // ============================================================================ navegação

    override fun startNavigation(simulated: Boolean) {
        val dest = destination ?: return
        if (navigating) return
        val plan = routePlan
        if (plan == null || previewRoute !== plan.selected || routingPreview) {
            routeError = "Calcule e selecione uma rota antes de iniciar."
            publish(); return
        }
        if (!plan.usableAt(freshFix(START_FIX_MAX_AGE_MS)?.point, SystemClock.elapsedRealtime(), simulated)) {
            routeError = "A origem mudou ou a prévia expirou. Atualize a rota e confira as alternativas antes de iniciar."
            publish(); return
        }
        ++placeSearchGeneration
        searchFuture?.cancel(true)
        ++planningGeneration
        radarRepo.cancel()
        if (!simulated && !location.hasPermission()) {
            routeError = "Permita a localização precisa para navegar."
            publish()
            return
        }
        this.simulated = simulated
        if (simulated) {
            // Trajeto simulado não pode receber posições reais do GPS.
            location.stop()
            locationRunning = false
        }
        ble.lab.setNavigationPriority(true)
        navigating = true
        navGen++
        radarVoice.startSession(navGen.toLong(), simulated)
        arrivalScheduled = false
        engine = NavigationEngine()
        radar = newRadarSession()
        radarRoute = null
        display.reset()
        val now = SystemClock.elapsedRealtime()
        handle(engine.start(GeoPoint(dest.lat, dest.lon), now))
        if (simulated) {
            simulator = RouteDriveSimulator(previewRoute!!, speedMps = SIM_SPEED_MPS, intervalMs = 1_000, startTimeMs = now).fixes().iterator()
        } else {
            ensureLocation()
            freshFix(START_FIX_MAX_AGE_MS)?.let { handle(engine.onFix(it, now)) }
        }
        startTicking()
        _serviceNeeded.value = true
        startService()
        publish()
    }

    override fun stopNavigation() {
        if (!navigating && !tripperNavSessionActive()) return
        navigating = false
        radarVoice.endSession()
        ble.lab.setNavigationPriority(false)
        simulator = null
        radarRepo.cancel()
        radarRoute = null
        handle(engine.stop())
        radar.end()
        display.stop()
        location.stop()
        locationRunning = false
        simulated = false
        publish()
        // O Tripper encerra o link ~5 s após a última tela. Se não encerrar, o app encerra (sem PING por horas).
        val gen = navGen
        main.postDelayed({
            if (!navigating && navGen == gen && tripperNavSessionActive()) ble.lab.interrupt("navegação encerrada")
            publish()
        }, LINGER_AFTER_STOP_MS)
    }

    /** "Encerrar" da notificação ou app removido dos recentes: para tudo, inclusive o link com o Tripper. */
    fun stopAll() {
        stopNavigation()
        interruptTripper()
    }

    /** Sem a tela do app e sem o serviço em primeiro plano não há Interromper visível: encerra o Tripper. */
    fun onAppBackgrounded() {
        if (!navigating) { location.stop(); locationRunning = false }
        ++placeSearchGeneration
        searchFuture?.cancel(true)
        if (searchState.searching) searchState = searchState.copy(searching = false,
            error = "Busca pausada. Digite novamente para continuar.")
        if (!serviceRunning && (tripperNavSessionActive() || tripperSearch != null)) interruptTripper()
    }

    fun onServiceStarted() {
        serviceRunning = true
    }

    fun onServiceStopped() {
        serviceRunning = false
        radarVoice.endSession()
        // Sem serviço foreground não podem continuar GPS, simulação ou rádio.
        stopAll()
        location.stop()
        locationRunning = false
    }

    /** O serviço só declara o tipo "location" quando há navegação real com permissão. */
    val wantsLocation: Boolean get() = navigating && !simulated && location.hasPermission()

    private fun startService() {
        try {
            NavigationService.start(app)
        } catch (e: IllegalStateException) {
            onServiceFailed("Não foi possível manter a navegação em primeiro plano. Abra o app e tente de novo.")
        } catch (e: SecurityException) {
            onServiceFailed("Permissão necessária ausente para o serviço de navegação.")
        }
    }

    private fun freshFix(maxAgeMs: Long): LocationFix? =
        lastFix?.takeIf { SystemClock.elapsedRealtime() - it.timeMs <= maxAgeMs }

    fun onServiceFailed(message: String) {
        onServiceStopped()
        tripperMessage = message
        routeError = message
        publish()
    }

    private fun startTicking() {
        if (ticking) return
        ticking = true
        main.post(object : Runnable {
            override fun run() {
                if (!navigating) {
                    ticking = false
                    return
                }
                val now = SystemClock.elapsedRealtime()
                simulator?.let { sim ->
                    if (sim.hasNext()) feedFix(sim.next().copy(timeMs = now)) else simulator = null
                }
                handle(engine.onTick(now))
                afterEngineUpdate(now)
                main.postDelayed(this, TICK_MS)
            }
        })
    }

    /** Fix do GPS real. Ignorado no trajeto simulado. */
    private fun onFix(fix: LocationFix) {
        if (simulated) return
        feedFix(fix)
    }

    private fun feedFix(fix: LocationFix) {
        lastFix = fix
        if (!navigating) {
            publish()
            return
        }
        val now = SystemClock.elapsedRealtime()
        handle(engine.onFix(fix, now))
        afterEngineUpdate(now)
    }

    private fun handle(effects: List<NavigationEffect>) {
        for (e in effects) {
            when (e) {
                is NavigationEffect.RequestRoute -> requestRoute(e.requestId, e.request)
                is NavigationEffect.CancelRequest -> Unit // o motor descarta respostas de pedidos antigos
            }
        }
    }

    private fun requestRoute(id: Long, request: RouteRequest) {
        val preview = previewRoute
        val eng = engine
        val sameDestination = previewDest?.let { dev.hunterlab.core.geo.Geo.distanceM(it, request.destination) < 30 } == true
        val reusable = preview != null && sameDestination && engine.state.route == null
        if (reusable) {
            // Rota inicial = a do resumo (economiza um crédito da API).
            main.post {
                if (navigating && engine === eng) {
                    handle(engine.onRouteResult(id, RouteResult.Success(preview), SystemClock.elapsedRealtime()))
                    afterEngineUpdate(SystemClock.elapsedRealtime())
                }
            }
            return
        }
        routeIo.execute {
            val result = provider.route(request)
            main.post {
                // Ids recomeçam a cada motor: resposta de uma navegação anterior é descartada.
                if (!navigating || engine !== eng) return@post
                val now = SystemClock.elapsedRealtime()
                handle(engine.onRouteResult(id, result, now))
                afterEngineUpdate(now)
            }
        }
    }

    private fun afterEngineUpdate(now: Long) {
        val state = engine.state
        val route = state.route
        if (route != null && route !== radarRoute && prefs.radarEnabled) {
            radarRoute = route
            previewRadars = emptyList()
            radar.setRoute(route.polyline, emptyList(), dev.hunterlab.core.radar.CoverageStatus.NONE)
            radarRepo.prepare(route.polyline, { System.currentTimeMillis() }) { data ->
                main.post {
                    if (navigating && prefs.radarEnabled && radarRoute === route) {
                        radar.setRouteFromTiles(route.polyline, data.tiles, data.coverage)
                        previewRadars = RouteRadarMatcher().match(route.polyline, data.tiles.flatMap { it.cameras })
                            .distinctBy { it.camera.id }.map { match -> val c = match.camera
                                MapRadarUi(c.id,c.point,c.maxSpeedKmh,c.speedLimit.status.label,c.source,!match.directionConfirmed) }
                        publish()
                    }
                }
            }
        }
        radarVoice.configure(prefs.radarVoiceEnabled && prefs.radarEnabled,
            navigating && gpsUi() == GpsUi.OK && state.status == EngineStatus.NAVIGATING)
        state.track?.let { if (route != null && prefs.radarEnabled) {
            radar.update(it.progressM).forEach(radarVoice::onEvent)
        } }
        val radarPhase = if (prefs.radarEnabled) radar.phase else RadarPhase.Clear
        val update = display.update(engine.toSnapshot(), radarPhase, displaySettings(), LocalTime.now(), now)
        displayStatus = update.status
        lastPreview = update.composed.preview
        if (state.status == EngineStatus.ARRIVED && !arrivalScheduled) {
            // Chegou: mostra o destino (no Tripper por ~15 s) e encerra GPS, serviço e wake lock.
            arrivalScheduled = true
            val gen = navGen
            main.postDelayed({ if (navigating && navGen == gen) stopNavigation() }, ARRIVAL_FINISH_MS)
        }
        publish()
    }

    private fun ensureLocation() {
        if (simulated || locationRunning) return
        val availability = location.start(onFix = ::onFix, onProviderChange = { enabled -> gpsEnabled = enabled; if (!enabled && !navigating) lastFix = null; publish() })
        locationRunning = availability != AndroidLocationSource.Availability.NO_PERMISSION
        gpsEnabled = availability == AndroidLocationSource.Availability.ACTIVE
        if (!navigating) main.postDelayed({ if (!navigating && locationRunning && !plannerOpen) { location.stop(); locationRunning = false; publish() } }, IDLE_LOCATION_MS)
    }

    // ============================================================================ Tripper

    override fun acceptTerms() {
        prefs.termsAcceptedVersion = AppPreferences.TERMS_VERSION
        publish()
    }

    override fun connectTripper() {
        val snap = ble.lab.snapshot.value
        if (snap.active) {
            tripperMessage = if (snap.purpose != SessionPurpose.NAVIGATION) "O Laboratório BLE está em uso. Interrompa-o antes." else null
            publish()
            return
        }
        if (prefs.termsAcceptedVersion < AppPreferences.TERMS_VERSION) {
            tripperMessage = "Aceite os termos antes de conectar."
            publish()
            return
        }
        if (!BlePermissions.allGranted(app)) {
            tripperMessage = "Permita \"Dispositivos por perto\" para conectar ao Tripper."
            publish()
            return
        }
        ble.refresh()
        if (!ble.readiness.value.ready) {
            tripperMessage = "Ligue o Bluetooth do celular."
            publish()
            return
        }
        tripperMessage = null
        firstCandidateAtMs = null
        val gen = ++searchGen
        if (navigating) display.reset() // nova conexão na mesma navegação volta a mostrar a rota
        ble.scanner.clear()
        val error = ble.scanner.start(TRIPPER_SCAN_MS)
        if (error != null) {
            tripperMessage = error.detail
            publish()
            return
        }
        // Só depois de a busca começar: antes disso o scanner parado seria lido como "interrompido".
        tripperSearch = "Procurando o Tripper… (ligue a ignição)"
        // O toque do usuário é o momento em que o app está visível: o serviço precisa nascer aqui.
        _serviceNeeded.value = true
        startService()
        publish()
        main.postDelayed({
            if (tripperSearch != null && searchGen == gen) {
                tripperSearch = null
                ble.scanner.stop()
                tripperMessage = "Tripper não encontrado. Cicle a ignição e conecte em até 30 s."
                publish()
            }
        }, TRIPPER_SCAN_MS + 500)
    }

    private fun onScanUpdate() {
        if (tripperSearch == null) return
        val scan = ble.scanner.state.value
        val candidates = scan.devices.values.filter {
            it.name == TRIPPER_NAME && TripperCandidateClassifier.classify(it).level == CandidateLevel.STRONG
        }
        if (candidates.isEmpty()) {
            if (!scan.scanning && scan.startedAtMs != null) {
                // Outra tela parou a busca: não conecta a nada.
                tripperSearch = null
                tripperMessage = "Busca pelo Tripper interrompida."
                publish()
            }
            return
        }
        val now = SystemClock.elapsedRealtime()
        val first = firstCandidateAtMs
        if (first == null) {
            // Espera um pouco para perceber outros Trippers por perto (passeio em grupo).
            firstCandidateAtMs = now
            val gen = searchGen
            main.postDelayed({ if (searchGen == gen) onScanUpdate() }, CANDIDATE_COLLECT_MS)
            return
        }
        if (now - first < CANDIDATE_COLLECT_MS && scan.scanning) return
        tripperSearch = null
        ble.scanner.stop()
        if (candidates.size > 1) {
            tripperMessage = "${candidates.size} Trippers por perto. Afaste-se das outras motos ou escolha pelo Laboratório BLE."
            publish()
            return
        }
        val candidate = candidates.single()
        if (ble.lab.currentState != LinkState.DISCONNECTED) {
            tripperMessage = "Já existe uma sessão com o Tripper. Interrompa-a antes."
            publish()
            return
        }
        ble.prepareLab()
        // Autorização para ESTA conexão (vale 2 min), só depois dos termos aceitos e do toque do usuário.
        val ack = TransmissionAck(bikeStopped = true, officialAppClosed = true, guideRead = true, pairingRiskAccepted = true, knowsHowToInterrupt = true)
        ble.lab.authorizeTransmission(ack)
        val age = (System.currentTimeMillis() - candidate.lastSeenMs).coerceAtLeast(0)
        val result = ble.lab.connect(candidate.address, candidate.name, ConnectOptions(purpose = SessionPurpose.NAVIGATION), lastAdvertisementAgeMs = age)
        tripperMessage = if (result is CommandResult.Rejected) result.reason else null
        publish()
    }

    override fun requestPin() = tripper { ble.lab.startPairing() }
    override fun submitPin(pin: String) = tripper { ble.lab.submitPin(pin) }
    override fun confirmPinAccepted() {
        tripper { ble.lab.confirmAuthenticated() }
        val session = ble.lab.snapshot.value.sessionId
        main.postDelayed({
            val s = ble.lab.snapshot.value
            if (!navigating && s.sessionId == session && s.purpose == SessionPurpose.NAVIGATION && s.state == LinkState.AUTHENTICATED) {
                ble.lab.interrupt("Tripper conectado sem navegação por ${IDLE_TRIPPER_MS / 60_000} min")
            }
        }, IDLE_TRIPPER_MS)
    }
    override fun reportPinRejected() = tripper { ble.lab.reportPinRequired() }

    override fun interruptTripper() {
        if (tripperSearch != null) {
            tripperSearch = null
            searchGen++
            ble.scanner.stop()
        }
        firstCandidateAtMs = null
        // O interrupt do link já para as telas; a navegação no celular continua e pode reconectar depois.
        if (tripperNavSessionActive()) ble.lab.interrupt("pedido do usuário (navegação)")
        publish()
    }

    private inline fun tripper(action: () -> CommandResult) {
        if (ble.lab.snapshot.value.purpose != SessionPurpose.NAVIGATION) return
        tripperMessage = (action() as? CommandResult.Rejected)?.reason
        if (navigating) afterEngineUpdate(SystemClock.elapsedRealtime()) else publish()
    }

    private fun tripperNavSessionActive(): Boolean {
        val s = ble.lab.snapshot.value
        return s.active && s.purpose == SessionPurpose.NAVIGATION
    }

    private fun updateServiceNeeded() {
        _serviceNeeded.value = navigating || tripperNavSessionActive() || tripperSearch != null
    }

    // ============================================================================ configurações

    override fun setApiKey(key: String) {
        prefs.routingApiKey = key
        publish()
        if (destination != null && previewRoute == null) previewRoute()
    }

    override fun clearApiKey() {
        prefs.routingApiKey = null
        publish()
    }

    override fun setDayNight(mode: DayNightMode) { prefs.dayNight = mode; refreshDisplay() }
    override fun setBottomLine(mode: BottomLineMode) { prefs.bottomLine = mode; refreshDisplay() }
    override fun setRadarEnabled(enabled: Boolean) {
        prefs.radarEnabled = enabled
        if (!enabled) {
            previewRadars = emptyList()
            previewRadarStatus = "Radares desativados."
            radarVoice.configure(false, false)
            radarRepo.cancel()
            radarRoute = null
            radar.end()
        }
        refreshDisplay()
    }
    override fun setRadarVoiceEnabled(enabled: Boolean) {
        prefs.radarVoiceEnabled = enabled
        radarVoice.configure(enabled && prefs.radarEnabled, navigating && gpsUi() == GpsUi.OK)
        publish()
    }

    override fun setRadarOnTripper(enabled: Boolean) { prefs.radarOnTripper = enabled; refreshDisplay() }

    override fun setRadarThresholds(searchM: Int, alertM: Int) {
        val s = searchM.coerceIn(200, 2_000)
        prefs.radarSearchM = s
        prefs.radarAlertM = alertM.coerceIn(100, s)
        publish() // vale a partir da próxima navegação
    }

    override fun refreshPermissions() {
        ble.refresh()
        if (!location.hasPermission()) { location.stop(); locationRunning = false; lastFix = null }
        if (plannerOpen) ensureLocation()
        publish()
    }

    private fun refreshDisplay() {
        if (navigating) afterEngineUpdate(SystemClock.elapsedRealtime()) else publish()
    }

    private fun displaySettings() = DisplaySettings(
        dayNightMode = prefs.dayNight,
        bottomLineMode = prefs.bottomLine,
        radarOnTripper = prefs.radarOnTripper,
    )

    private fun newRadarSession() = RouteRadarSession(
        RadarConfig(searchRadiusM = prefs.radarSearchM, alertRadiusM = prefs.radarAlertM.coerceAtMost(prefs.radarSearchM)),
    )

    // ============================================================================ estado da interface

    private fun publish() {
        val snap = ble.lab.snapshot.value
        val tripperUi = tripperUi(snap)
        val gps = gpsUi()
        val state = engine.state
        val keyOk = prefs.routingApiKey != null
        val route = previewRoute

        _home.value = HomeUi(
            destination = destination,
            search = searchState,
            route = route?.let { summary(it) },
            routing = routingPreview,
            routeError = routeError,
            providerConfigured = keyOk,
            canStart = previewRoute != null && routePlan != null && !routingPreview && !navigating && keyOk,
            navigationActive = navigating,
            recent = prefs.recent,
            planner = PlannerUi(
                mapConfigured = prefs.mapApiKey != null,
                mapRevision = mapRevision,
                currentPosition = freshFix(PREVIEW_FIX_MAX_AGE_MS)?.takeIf { location.hasPermission() }?.point,
                alternatives = if (navigating) state.route?.let { listOf(RouteOptionUi(0, it.polyline.points, summary(it), it.roadNames.joinToString(" · ").ifBlank { null })) }.orEmpty()
                    else routePlan?.routes?.mapIndexed { i, r -> RouteOptionUi(i, r.polyline.points, summary(r),
                        r.roadNames.ifEmpty { r.steps.mapNotNull { it.streetName }.distinct().take(3) }.joinToString(" · ").ifBlank { null }) }.orEmpty(),
                selectedIndex = if (navigating) 0 else routePlan?.selectedIndex ?: 0,
                radars = previewRadars,
                radarStatus = previewRadarStatus,
                notice = planNotice,
                favorites = prefs.favorites,
            ),
            tripper = tripperUi,
            gps = gps,
        )

        val track = state.track
        val activeRoute = state.route
        val next = track?.let { t -> t.nextStepIndex?.let { i -> activeRoute?.steps?.getOrNull(i) } }
        val following = track?.let { t -> t.nextStepIndex?.let { i -> activeRoute?.steps?.getOrNull(i + 1) } }
        _nav.value = NavUi(
            phase = if (!navigating && state.status != EngineStatus.ARRIVED) NavPhaseUi.IDLE else phaseOf(state.status),
            destination = destination,
            current = track?.let { t ->
                ManeuverUi(
                    iconCode = ManeuverMapper.map(t.nextManeuver).large,
                    instruction = next?.text ?: t.nextManeuver.label,
                    distanceText = Format.distance(t.distanceToNextManeuverM),
                    streetName = next?.streetName,
                )
            },
            next = t2(track?.followingManeuver, following),
            remainingDistanceText = track?.let { Format.distance(it.remainingDistanceM) },
            remainingTimeText = track?.let { Format.duration(it.remainingTimeS) },
            arrivalText = track?.let { Format.arrival(it.remainingTimeS) },
            gps = gps,
            gpsAccuracyText = lastFix?.accuracyM?.let { "±${it.toInt()} m" },
            roadLimitKmh = if (gps == GpsUi.OK && state.status == EngineStatus.NAVIGATING) track?.let { state.route?.roadSpeedLimitAt(it.progressM) } else null,
            tripper = tripperUi,
            radar = if (navigating) radarUi() else null,
            voiceStatusText = if (prefs.radarVoiceEnabled && radarVoice.status == dev.hunterlab.core.radar.RadarVoiceStatus.UNAVAILABLE) "Voz indisponível. Configure uma voz pt-BR offline nas opções de texto para voz do Android." else null,
            message = navMessage(state.status, state.message),
            simulated = simulated,
            tripperPreview = lastPreview,
        )

        _settings.value = SettingsUi(
            apiKeyConfigured = keyOk,
            mapKeyConfigured = prefs.mapApiKey != null,
            apiKeyMasked = AppPreferences.mask(prefs.routingApiKey),
            dayNight = prefs.dayNight,
            bottomLine = prefs.bottomLine,
            radarEnabled = prefs.radarEnabled,
            radarOnTripper = prefs.radarOnTripper,
            radarVoiceEnabled = prefs.radarVoiceEnabled,
            radarSearchM = prefs.radarSearchM,
            radarAlertM = prefs.radarAlertM,
            permissions = PermissionsUi(
                location = granted(Manifest.permission.ACCESS_FINE_LOCATION),
                notifications = Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS),
                bluetooth = BlePermissions.allGranted(app),
            ),
            version = BuildConfigInfo.VERSION,
            routingAttribution = ATTRIBUTION,
        )

        _notification.value = buildString {
            val cur = _nav.value.current
            if (navigating && cur != null) append("${cur.distanceText} · ${cur.instruction}") else append(_nav.value.phase.label)
            append(" · ").append(tripperUi.label)
        }
        updateServiceNeeded()
    }

    private fun t2(maneuver: dev.hunterlab.protocol.Maneuver?, step: dev.hunterlab.core.route.RouteStep?): ManeuverUi? {
        maneuver ?: return null
        return ManeuverUi(ManeuverMapper.map(maneuver).small, step?.text ?: maneuver.label, "", step?.streetName)
    }

    private fun phaseOf(status: EngineStatus) = when (status) {
        EngineStatus.IDLE -> NavPhaseUi.IDLE
        EngineStatus.ROUTING -> NavPhaseUi.ROUTING
        EngineStatus.NAVIGATING -> NavPhaseUi.NAVIGATING
        EngineStatus.OFF_ROUTE -> NavPhaseUi.OFF_ROUTE
        EngineStatus.REROUTING -> NavPhaseUi.REROUTING
        EngineStatus.ARRIVED -> NavPhaseUi.ARRIVED
        EngineStatus.FAILED -> NavPhaseUi.FAILED
    }

    private fun navMessage(status: EngineStatus, engineMessage: String): String? = when {
        !navigating && status != EngineStatus.ARRIVED -> routeError
        status == EngineStatus.NAVIGATING && !engine.state.gpsLost -> tripperMessage
        else -> listOfNotNull(engineMessage.takeIf { it.isNotBlank() }, tripperMessage).joinToString(" · ").ifBlank { null }
    }

    private fun gpsUi(): GpsUi = when {
        simulated -> GpsUi.OK
        !location.hasPermission() -> GpsUi.NO_PERMISSION
        !gpsEnabled && !location.gpsEnabled() -> GpsUi.OFF
        navigating && engine.state.gpsLost -> GpsUi.LOST
        lastFix == null || (!navigating && freshFix(PREVIEW_FIX_MAX_AGE_MS) == null) -> GpsUi.SEARCHING
        (lastFix?.accuracyM ?: 0f) > IMPRECISE_M -> GpsUi.IMPRECISE
        else -> GpsUi.OK
    }

    private fun radarUi(): RadarUi {
        if (!prefs.radarEnabled) return RadarUi(enabled = false, coverageText = "Alertas de radar desligados")
        if (gpsUi() != GpsUi.OK) return RadarUi(enabled = true, coverageText = "Aguardando GPS preciso para alertar", sourceText = radarRepo.dataSourceLabel)
        val p = radar.phase
        val item = when (p) {
            is RadarPhase.Alert -> p.radar to p.distanceM
            is RadarPhase.Detected -> p.radar to p.distanceM
            else -> null
        }
        val camera = item?.first
        val info = camera?.limitInfo
        return RadarUi(enabled = true, coverageText = radar.statusText,
            alertText = item?.let { "${it.first.kind.label} a ${it.second} m" },
            alertDistanceM = item?.second, kindText = camera?.kind?.label ?: "Radar",
            limitKmh = info?.kmh, limitStatus = info?.status?.label ?: "Limite indisponível",
            sourceText = listOfNotNull(camera?.sourceLabel?.takeIf { it.isNotBlank() }, radarRepo.dataSourceLabel).distinct().joinToString(" · "),
            directionUnknown = camera?.directionConfirmed != true,
            attribution = "© colaboradores do OpenStreetMap · Pardal / fontes oficiais (ODbL)")
    }

    private fun tripperUi(s: LinkSnapshot): TripperUi {
        val needsTerms = prefs.termsAcceptedVersion < AppPreferences.TERMS_VERSION
        val btReady = BlePermissions.allGranted(app) && ble.readiness.value.ready
        val search = tripperSearch
        if (search != null) {
            return TripperUi(TripperUiStatus.CONNECTING, search, canInterrupt = true, needsTerms = needsTerms)
        }
        if (s.active && s.purpose != SessionPurpose.NAVIGATION) {
            return TripperUi(TripperUiStatus.DISCONNECTED, "Laboratório BLE em uso", "Interrompa o laboratório para usar o Tripper na navegação.", needsTerms = needsTerms)
        }
        val base = when (s.state) {
            LinkState.DISCONNECTED -> TripperUi(
                TripperUiStatus.DISCONNECTED,
                if (navigating) "Tripper desconectado — navegação só no celular" else "Tripper desconectado",
                tripperMessage ?: s.message?.detail?.takeIf { s.purpose == SessionPurpose.NAVIGATION },
                canConnect = btReady,
            )
            LinkState.CONNECTING, LinkState.DISCOVERING -> TripperUi(TripperUiStatus.CONNECTING, "Conectando ao Tripper…", canInterrupt = true)
            LinkState.READY_FOR_AUTH -> TripperUi(TripperUiStatus.READY_TO_PAIR, "Toque para mostrar o PIN no Tripper", canInterrupt = true)
            LinkState.WAITING_FOR_PIN -> TripperUi(TripperUiStatus.WAITING_PIN, "Digite o PIN mostrado no Tripper", canInterrupt = true)
            LinkState.AUTHENTICATING -> TripperUi(TripperUiStatus.CONFIRM_PIN, "O Tripper aceitou o PIN?", canInterrupt = true)
            LinkState.AUTHENTICATED -> TripperUi(
                TripperUiStatus.AUTHENTICATED,
                if (displayStatus == TripperDisplayStatus.ENDED) "Navegação encerrada no Tripper" else "Tripper conectado",
                if (navigating) null else "A navegação aparece no Tripper ao iniciar a rota.",
                canInterrupt = true,
            )
            LinkState.DISPLAY_ACTIVE -> TripperUi(TripperUiStatus.SHOWING, "Navegação no Tripper", canInterrupt = true)
            LinkState.STOPPING -> TripperUi(TripperUiStatus.STOPPING, "Encerrando a conexão com o Tripper…")
            LinkState.ERROR -> TripperUi(TripperUiStatus.ERROR, s.message?.title ?: "Erro no Tripper", s.message?.detail, canInterrupt = true)
        }
        return base.copy(
            detail = base.detail ?: tripperMessage,
            pinAttempt = s.pinAttemptsUsed,
            pinAttemptsMax = s.pinAttemptsMax,
            pinDelivered = s.authCommandDelivered,
            needsTerms = needsTerms,
        )
    }

    private fun summary(r: Route) = RouteSummaryUi(
        distanceText = Format.distance(r.distanceM),
        durationText = Format.duration(r.durationS),
        arrivalText = Format.arrival(r.durationS),
        attribution = r.attribution.ifBlank { ATTRIBUTION },
    )

    private fun granted(p: String) = ContextCompat.checkSelfPermission(app, p) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val TICK_MS = 1_000L
        const val SIM_SPEED_MPS = 11.0 // ~40 km/h
        const val IMPRECISE_M = 50f
        const val TRIPPER_SCAN_MS = 15_000L
        const val IDLE_LOCATION_MS = 120_000L
        const val TRIPPER_NAME = "RE_DISP"
        const val CANDIDATE_COLLECT_MS = 3_000L
        const val PREVIEW_FIX_MAX_AGE_MS = 60_000L
        const val START_FIX_MAX_AGE_MS = 10_000L
        const val ARRIVAL_FINISH_MS = 20_000L
        const val LINGER_AFTER_STOP_MS = 12_000L
        const val IDLE_TRIPPER_MS = 10 * 60_000L
        const val ATTRIBUTION = "Rotas: GraphHopper · Dados © colaboradores do OpenStreetMap (ODbL)"
    }
}

/** Textos curtos em pt-BR para a interface. */
internal object Format {
    private val clock = DateTimeFormatter.ofPattern("HH:mm")

    fun distance(m: Double): String = when {
        m < 0 || m.isNaN() -> "—"
        m < 950 -> "${(Math.round(m / 10.0) * 10).toInt()} m"
        m < 9_950 -> String.format(Locale.forLanguageTag("pt-BR"), "%.1f km", m / 1000)
        else -> "${Math.round(m / 1000)} km"
    }

    fun duration(s: Double): String {
        val min = kotlin.math.ceil(s.coerceAtLeast(0.0) / 60).toInt()
        return if (min < 60) "$min min" else "${min / 60} h %02d min".format(min % 60)
    }

    fun arrival(s: Double): String = LocalTime.now().plusSeconds(s.coerceAtLeast(0.0).toLong()).format(clock)
}
