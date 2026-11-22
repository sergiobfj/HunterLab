package dev.hunterlab.app.ui.map

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.hunterlab.app.R
import dev.hunterlab.app.ui.contract.*
import dev.hunterlab.app.ui.theme.HunterColors
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.*
import org.maplibre.android.log.Logger
import org.maplibre.android.style.layers.*
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.expressions.Expression.*
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.geojson.*
import android.graphics.RectF

/** SDK view confined to cartography. Location is supplied by the existing authorized GPS source. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RouteMap(state: PlannerUi, destination: DestinationUi?, actions: NavActions, modifier: Modifier,
    recenterToken: Int = 0) {
    if (LocalInspectionMode.current) {
        Box(modifier.background(HunterColors.Surface), contentAlignment = Alignment.Center) {
            Text("Mapa interativo · prévia de composição", color = HunterColors.TextSecondary)
        }
        return
    }
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    val owner = LocalLifecycleOwner.current
    val latestState by rememberUpdatedState(state)
    val latestActions by rememberUpdatedState(actions)
    var selectedRadar by remember { mutableStateOf<MapRadarUi?>(null) }
    var clusterMessage by remember { mutableStateOf<String?>(null) }
    var loadError by remember { mutableStateOf(false) }
    var firstPositionCentered by remember { mutableStateOf(false) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    val mapView = remember(context) {
        MapLibre.getInstance(context.applicationContext)
        // The SDK must not write resource URLs (which contain a user API key) to Logcat.
        Logger.setVerbosity(Logger.NONE)
        MapView(context, MapLibreMapOptions.createFromAttributes(context).textureMode(true)).apply { onCreate(null) }
    }
    DisposableEffect(mapView, owner) {
        var started = false
        var resumed = false
        fun start() { if (!started) { mapView.onStart(); started = true } }
        fun resume() { start(); if (!resumed) { mapView.onResume(); resumed = true } }
        fun pause() { if (resumed) { mapView.onPause(); resumed = false } }
        fun stop() { pause(); if (started) { mapView.onStop(); started = false } }
        val observer = LifecycleEventObserver { _, event -> when (event) {
            Lifecycle.Event.ON_START -> start()
            Lifecycle.Event.ON_RESUME -> resume()
            Lifecycle.Event.ON_PAUSE -> pause()
            Lifecycle.Event.ON_STOP -> stop()
            else -> Unit
        } }
        owner.lifecycle.addObserver(observer)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) start()
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) resume()
        onDispose { owner.lifecycle.removeObserver(observer); stop(); mapView.onDestroy() }
    }
    LaunchedEffect(mapView) {
        mapView.addOnDidFailLoadingMapListener { loadError = true }
        mapView.addOnTileActionListener { operation, _, _, _, _, _, _ ->
            if (operation == org.maplibre.android.tile.TileOperation.Error)
                mapView.post { loadError = true }
            else if (operation == org.maplibre.android.tile.TileOperation.LoadFromNetwork ||
                operation == org.maplibre.android.tile.TileOperation.LoadFromCache)
                mapView.post { loadError = false }
        }
        mapView.getMapAsync { m ->
            m.uiSettings.isLogoEnabled = false // MapTiler attribution is rendered below, always visible.
            m.uiSettings.isAttributionEnabled = false
            m.uiSettings.isCompassEnabled = true
            m.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(-8.284, -35.974), 12.0))
            m.addOnMapClickListener { coordinate ->
                val pixel = m.projection.toScreenLocation(coordinate)
                val hit = m.queryRenderedFeatures(RectF(pixel.x-18, pixel.y-18, pixel.x+18, pixel.y+18), "cameras", "clusters", "selected-route", "routes")
                val camera = hit.firstOrNull { it.hasProperty("cameraId") }
                when {
                    camera != null -> selectedRadar = latestState.radars.firstOrNull { it.id == camera.getStringProperty("cameraId") }
                    hit.any { it.hasProperty("point_count") } -> clusterMessage = "Radares agrupados. Aproxime o mapa para consultar cada equipamento."
                    else -> hit.firstOrNull { it.hasProperty("routeIndex") }?.getNumberProperty("routeIndex")?.toInt()?.let { latestActions.selectRoute(it) }
                }
                hit.isNotEmpty()
            }
            map = m
        }
    }
    LaunchedEffect(map, state.mapRevision, state.mapConfigured) {
        val m = map ?: return@LaunchedEffect
        style = null; loadError = false
        m.setStyle(Style.Builder().fromJson(actions.mapStyleJson() ?: MapStyleConfig.EMPTY)) { loaded ->
            loaded.addSource(GeoJsonSource("route-data"))
            loaded.addLayer(LineLayer("routes", "route-data").withProperties(lineColor("#6F7A80"), lineWidth(5f), lineOpacity(.65f), lineCap("round"), lineJoin("round")))
            loaded.addLayer(LineLayer("selected-route", "route-data").withProperties(lineColor("#B7CF62"), lineWidth(7f), lineCap("round"), lineJoin("round")))
            loaded.addSource(GeoJsonSource("camera-data", GeoJsonOptions().withCluster(true).withClusterRadius(36).withClusterMaxZoom(14)))
            loaded.addLayer(CircleLayer("clusters", "camera-data").withFilter(has("point_count")).withProperties(circleRadius(16f), circleColor("#C98A2E"), circleStrokeColor("#101416"), circleStrokeWidth(2f)))
            loaded.addLayer(CircleLayer("cameras", "camera-data").withFilter(not(has("point_count"))).withProperties(circleRadius(8f), circleColor("#C98A2E"), circleStrokeColor("#101416"), circleStrokeWidth(2f)))
            loaded.addSource(GeoJsonSource("destination-data"))
            loaded.addLayer(CircleLayer("destination", "destination-data").withProperties(circleRadius(10f), circleColor("#B7CF62"), circleStrokeColor("#101416"), circleStrokeWidth(3f)))
            loaded.addSource(GeoJsonSource("position-data"))
            loaded.addLayer(CircleLayer("position", "position-data").withProperties(circleRadius(7f), circleColor("#E8ECEE"), circleStrokeColor("#4F6773"), circleStrokeWidth(3f)))
            style = loaded
        }
    }
    // Route geometry must not be rebuilt/serialized on every GPS fix.
    LaunchedEffect(style, state.alternatives.map { it.index to it.points }) {
        val s = style ?: return@LaunchedEffect
        val features = state.alternatives.map { route -> Feature.fromGeometry(LineString.fromLngLats(route.points.map { Point.fromLngLat(it.lon,it.lat) })).apply { addNumberProperty("routeIndex", route.index) } }
        s.getSourceAs<GeoJsonSource>("route-data")?.setGeoJson(FeatureCollection.fromFeatures(features))
    }
    LaunchedEffect(style, state.selectedIndex) {
        style?.getLayerAs<LineLayer>("selected-route")?.setFilter(eq(get("routeIndex"), literal(state.selectedIndex)))
    }
    LaunchedEffect(style, state.radars) {
        val s = style ?: return@LaunchedEffect
        s.getSourceAs<GeoJsonSource>("camera-data")?.setGeoJson(FeatureCollection.fromFeatures(state.radars.map {
            Feature.fromGeometry(Point.fromLngLat(it.point.lon,it.point.lat)).apply { addStringProperty("cameraId", it.id) }
        }))
    }
    LaunchedEffect(style, destination) {
        val s = style ?: return@LaunchedEffect
        s.getSourceAs<GeoJsonSource>("destination-data")?.setGeoJson(FeatureCollection.fromFeatures(destination?.let {
            listOf(Feature.fromGeometry(Point.fromLngLat(it.lon,it.lat)))
        }.orEmpty()))
    }
    LaunchedEffect(style, state.currentPosition) {
        val s = style ?: return@LaunchedEffect
        s.getSourceAs<GeoJsonSource>("position-data")?.setGeoJson(FeatureCollection.fromFeatures(state.currentPosition?.let {
            listOf(Feature.fromGeometry(Point.fromLngLat(it.lon,it.lat)))
        }.orEmpty()))
    }
    // Geometry changes fit all returned paths; selecting another path does not reset a user's zoom.
    LaunchedEffect(map, style, state.alternatives.map { it.points }, destination) {
        val m = map ?: return@LaunchedEffect
        val pts = state.alternatives.flatMap { it.points }.ifEmpty {
            listOfNotNull(state.currentPosition, destination?.let { dev.hunterlab.core.geo.GeoPoint(it.lat,it.lon) })
        }
        if (pts.size >= 2 && pts.distinct().size > 1) m.easeCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().includes(pts.map { LatLng(it.lat,it.lon) }).build(), 44))
        else pts.firstOrNull()?.let { m.easeCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.lat,it.lon), 14.0)) }
    }
    LaunchedEffect(map, style, state.currentPosition) {
        val m = map ?: return@LaunchedEffect
        if (!firstPositionCentered && state.alternatives.isEmpty() && destination == null) state.currentPosition?.let {
            m.easeCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.lat, it.lon), 14.0))
            firstPositionCentered = true
        }
    }
    LaunchedEffect(map, recenterToken) {
        if (recenterToken > 0) state.currentPosition?.let { point ->
            map?.easeCamera(CameraUpdateFactory.newLatLngZoom(LatLng(point.lat, point.lon), 15.0))
        }
    }
    Box(modifier) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        if (!state.mapConfigured) Text("Configure a chave MapTiler para carregar o mapa.", Modifier.align(Alignment.TopCenter).background(HunterColors.Surface).padding(12.dp), style = MaterialTheme.typography.bodyMedium)
        if (loadError) Text("Mapa indisponível. Verifique a chave ou a conexão.", Modifier.align(Alignment.TopCenter).background(HunterColors.Surface).padding(12.dp), style = MaterialTheme.typography.bodyMedium)
        // Visible links and official vendor logo, including on Free accounts. Never behind the route sheet.
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(HunterColors.Background.copy(alpha=.94f)).padding(horizontal=8.dp,vertical=4.dp)) {
            Image(painterResource(R.drawable.maptiler_attribution_logo), "MapTiler", Modifier.height(20.dp).width(67.dp).clickable { uri.openUri("https://www.maptiler.com/") })
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("© MapTiler", Modifier.clickable { uri.openUri("https://www.maptiler.com/copyright/") }, style=MaterialTheme.typography.labelSmall)
                Text("© OpenStreetMap", Modifier.clickable { uri.openUri("https://www.openstreetmap.org/copyright") }, style=MaterialTheme.typography.labelSmall)
            }
        }
    }
    selectedRadar?.let { radar -> AlertDialog(onDismissRequest={ selectedRadar=null }, title={ Text("Radar cadastrado") }, text={ Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        dev.hunterlab.app.ui.navigation.SpeedLimitSign(radar.limitKmh)
        Text(radar.limitStatus)
        if (radar.directionUnknown) Text("Sentido não confirmado")
        Text(radar.source)
        Text("Cadastro não comprova funcionamento ou cobertura completa.")
    } }, confirmButton={ TextButton(onClick={selectedRadar=null}) { Text("Fechar") } }) }
    clusterMessage?.let { message -> AlertDialog(onDismissRequest={clusterMessage=null}, text={Text(message)}, confirmButton={TextButton(onClick={clusterMessage=null}) {Text("Entendi")}}) }
}
