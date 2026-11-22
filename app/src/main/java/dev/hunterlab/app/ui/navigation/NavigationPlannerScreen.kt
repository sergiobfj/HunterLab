package dev.hunterlab.app.ui.navigation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.hunterlab.app.ui.components.*
import dev.hunterlab.app.ui.contract.*
import dev.hunterlab.app.ui.map.RouteMap
import dev.hunterlab.app.ui.theme.HunterColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationPlannerScreen(home: HomeUi, nav: NavUi, actions: NavActions, onSettings: () -> Unit,
    modifier: Modifier = Modifier) {
    var searchOpen by remember { mutableStateOf(false) }
    var coordinatesOpen by remember { mutableStateOf(false) }
    var coordinates by remember { mutableStateOf("") }
    var coordinateError by remember { mutableStateOf<String?>(null) }
    var recenter by remember { mutableIntStateOf(0) }
    val keyboard = LocalSoftwareKeyboardController.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { actions.refreshPermissions(); actions.openPlanner() }
    DisposableEffect(actions) { actions.openPlanner(); onDispose { actions.closePlanner() } }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val panelMax = maxHeight * .42f
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=8.dp), verticalAlignment=Alignment.CenterVertically) {
                OutlinedButton(onClick={searchOpen=true}, enabled=!home.navigationActive, modifier=Modifier.weight(1f).heightIn(min=48.dp)) {
                    Text(home.destination?.label ?: "Buscar destino", maxLines=2)
                }
            }
            Box(Modifier.fillMaxWidth().weight(1f).heightIn(min=120.dp)) {
                RouteMap(home.planner, home.destination, actions, Modifier.fillMaxSize(), recenter)
                FilledTonalButton(onClick={actions.openPlanner();recenter++}, modifier=Modifier.align(Alignment.TopEnd).padding(8.dp)) { Text("Recentrar") }
            }
            Column(Modifier.fillMaxWidth().heightIn(max=panelMax).verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                if (home.gps == GpsUi.NO_PERMISSION) {
                    Text("Sem localização autorizada. Você pode explorar o mapa e escolher o destino.", style=MaterialTheme.typography.bodyMedium)
                    TextButton(onClick={ permission.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION)) }) {Text("Permitir localização")}
                } else if (home.planner.currentPosition == null) SupportingText("${home.gps.label} · sem posição atual no mapa")
                else if (home.gps != GpsUi.OK) SupportingText(home.gps.label)
                if (home.navigationActive) {
                    Text(nav.phase.label, style=MaterialTheme.typography.titleMedium)
                    nav.current?.let { Text("${it.distanceText} · ${it.instruction}", style=MaterialTheme.typography.headlineSmall) }
                    nav.radar?.takeIf { it.alertText != null }?.let { RadarAlert(it) }
                    nav.message?.let { SupportingText(it) }
                    TextButton(onClick=actions::stopNavigation) { Text("Encerrar navegação") }
                } else {
                    home.destination?.let { destination ->
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Text(destination.label, style=MaterialTheme.typography.titleLarge);destination.detail?.let {SupportingText(it)} }
                            TextButton(onClick={actions.toggleFavorite(destination)}) {Text(if(home.planner.favorites.any{samePlace(it,destination)}) "Salvo" else "Salvar")}
                        }
                        if (home.routing) { LinearProgressIndicator(Modifier.fillMaxWidth());SupportingText("Calculando percursos…") }
                        home.routeError?.let {SupportingText(it,error=true)}
                        if (home.planner.alternatives.isNotEmpty()) {
                            Text(if(home.planner.alternatives.size==1) "Uma rota disponível" else "Escolha o percurso", style=MaterialTheme.typography.titleMedium)
                            val routeCards = rememberLazyListState(initialFirstVisibleItemIndex=home.planner.selectedIndex.coerceIn(home.planner.alternatives.indices))
                            LaunchedEffect(home.planner.selectedIndex, home.planner.alternatives.size) {
                                routeCards.scrollToItem(home.planner.selectedIndex.coerceIn(home.planner.alternatives.indices))
                            }
                            LazyRow(state=routeCards, horizontalArrangement=Arrangement.spacedBy(10.dp), modifier=Modifier.fillMaxWidth()) {
                                items(home.planner.alternatives, key={it.index}) { route ->
                                    val selected=route.index==home.planner.selectedIndex
                                    Surface(onClick={actions.selectRoute(route.index)}, modifier=Modifier.width(220.dp).semantics {contentDescription="Rota ${route.index+1}${if(selected) ", selecionada" else ""}"},
                                        shape=MaterialTheme.shapes.medium, color=if(selected) HunterColors.SurfaceHigh else HunterColors.Surface,
                                        border=BorderStroke(1.dp,if(selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)) {
                                        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                                            Text("Rota ${route.index+1}${if(selected) " · selecionada" else ""}",style=MaterialTheme.typography.labelLarge)
                                            Text(route.summary.durationText,style=MaterialTheme.typography.headlineSmall)
                                            Text("${route.summary.distanceText} por vias",style=MaterialTheme.typography.bodyLarge)
                                            route.roads?.let { Text(it,style=MaterialTheme.typography.bodySmall) }
                                        }
                                    }
                                }
                            }
                            home.planner.notice?.let {SupportingText(it)}
                            SupportingText(home.planner.radarStatus)
                            SupportingText("Radares: © colaboradores do OpenStreetMap · Pardal / fontes oficiais (ODbL)")
                            SupportingText("Toque nos marcadores de radar para consultar limite e evidência. Grupos se separam ao aproximar.")
                            TextButton(onClick={actions.startNavigation(true)},enabled=home.canStart&&!home.routing) {Text("Simular rota selecionada")}
                        }
                        OutlinedButton(onClick=actions::previewRoute,enabled=home.providerConfigured&&!home.routing,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                            Text(if(home.planner.alternatives.isEmpty()) "Calcular rota" else "Atualizar percursos")
                        }
                        TextButton(onClick=actions::clearDestination) {Text("Limpar destino")}
                    } ?: run {
                        Text("Seu próximo caminho",style=MaterialTheme.typography.titleLarge)
                        SupportingText("Busque um estabelecimento ou escolha um destino salvo.")
                        if (!home.planner.mapConfigured) TextButton(onClick=onSettings) {Text("Configurar mapas e busca")}
                    }
                    if (!home.providerConfigured&&home.destination!=null) TextButton(onClick=onSettings) {Text("Configurar GraphHopper")}
                    SupportingText("Perfil car · não otimizado ou validado para motos. Confira as restrições de circulação.")
                    if(home.tripper.status != TripperUiStatus.DISCONNECTED) TripperConnection(home.tripper, actions)
                }
            }
            if (!home.navigationActive && home.planner.alternatives.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().padding(horizontal=16.dp, vertical=8.dp)) {
                    home.planner.alternatives.firstOrNull { it.index == home.planner.selectedIndex }?.let { selected ->
                        Text("Rota ${selected.index+1} · ${selected.summary.durationText} · ${selected.summary.distanceText}", style=MaterialTheme.typography.labelLarge)
                    }
                    PrimaryAction("Iniciar navegação", { actions.startNavigation(false) }, enabled=home.canStart&&!home.routing)
                }
            }
        }
    }
    if (searchOpen) ModalBottomSheet(onDismissRequest={searchOpen=false;actions.search("");keyboard?.hide()}, sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        DestinationSearchContent(home, actions,
            onSelected={searchOpen=false;keyboard?.hide()},
            onCoordinates={coordinatesOpen=true;keyboard?.hide()},
            onSettings={searchOpen=false;keyboard?.hide();onSettings()})
    }

    if(coordinatesOpen) AlertDialog(onDismissRequest={coordinatesOpen=false}, title={Text("Coordenadas ou link geo:")},text={Column {
        OutlinedTextField(coordinates,{coordinates=it;coordinateError=null},label={Text("Latitude, longitude / geo:")},modifier=Modifier.fillMaxWidth())
        coordinateError?.let{SupportingText(it,error=true)}
    }},confirmButton={TextButton(onClick={coordinateError=actions.setDestinationFromText(coordinates);if(coordinateError==null){coordinatesOpen=false;searchOpen=false;keyboard?.hide()}}){Text("Usar destino")}},dismissButton={TextButton(onClick={coordinatesOpen=false}){Text("Cancelar")}})
}
