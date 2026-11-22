package dev.hunterlab.app.ui.preview

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.hunterlab.app.ui.contract.*
import dev.hunterlab.app.ui.navigation.NavigationPlannerScreen
import dev.hunterlab.app.ui.navigation.DestinationSearchContent
import dev.hunterlab.app.ui.theme.HunterLabTheme
import dev.hunterlab.core.geo.GeoPoint

/** Synthetic data for layout inspection only. No provider request, current position or real camera claim. */
object PlannerPreviewStates {
    val destination = DestinationUi("Caruaru Shopping", -8.30, -35.95, "Endereço sintético · Caruaru, PE", "2,4 km em linha reta")
    val routes = (0..2).map { i -> RouteOptionUi(i,
        listOf(GeoPoint(-8.284,-35.974), GeoPoint(-8.29-i*.002,-35.96),GeoPoint(-8.30,-35.95)),
        RouteSummaryUi(listOf("4,2 km","4,8 km","5,1 km")[i],listOf("12 min","14 min","16 min")[i],"18:42","Fixture sintética"),
        listOf("Via pública A","Via pública B","Via pública C")[i]) }
    val home = HomeUi(destination=destination, providerConfigured=true, canStart=true, gps=GpsUi.OK,
        route=routes[0].summary,planner=PlannerUi(mapConfigured=true,currentPosition=GeoPoint(-8.284,-35.974),
        alternatives=routes,radarStatus="2 radares sintéticos · sem comprovação de cobertura",favorites=listOf(destination)))
    val search = home.copy(search=SearchUi(query="Caruaru Shopping",results=listOf(destination,
        destination.copy(label="Estabelecimento de teste",detail="Rua sintética · Caruaru, PE",straightLineDistanceText="3,1 km em linha reta"))))
}

@Composable
private fun PlannerSurface(content:@Composable ()->Unit) = HunterLabTheme { Surface(Modifier.fillMaxSize(),content=content) }

@Preview(name="Planejamento · alternativas",widthDp=360,heightDp=800,locale="pt-rBR")
@Composable
fun PlannerRoutesPreview() = PlannerSurface { NavigationPlannerScreen(PlannerPreviewStates.home,NavUi(),EmptyNavActions,{}) }

@Preview(name="Planejamento · 320dp · fonte 150%",widthDp=320,heightDp=720,fontScale=1.5f,locale="pt-rBR")
@Composable
fun PlannerLargeFontPreview() = PlannerSurface { NavigationPlannerScreen(PlannerPreviewStates.home,NavUi(),EmptyNavActions,{}) }

@Preview(name="Planejamento · uma rota",widthDp=360,heightDp=800,locale="pt-rBR")
@Composable
fun PlannerSinglePreview() = PlannerSurface {
    val h=PlannerPreviewStates.home
    NavigationPlannerScreen(h.copy(planner=h.planner.copy(alternatives=h.planner.alternatives.take(1))),NavUi(),EmptyNavActions,{})
}

@Preview(name="Busca · nome comercial",widthDp=360,heightDp=800,locale="pt-rBR")
@Composable
fun PlannerSearchPreview() = PlannerSurface { DestinationSearchContent(PlannerPreviewStates.search,EmptyNavActions,{},{},{}) }

@Preview(name="Busca · 320dp · fonte 150%",widthDp=320,heightDp=720,fontScale=1.5f,locale="pt-rBR")
@Composable
fun PlannerSearchLargePreview() = PlannerSurface { DestinationSearchContent(PlannerPreviewStates.search,EmptyNavActions,{},{},{}) }

@Preview(name="Planejamento · sem localização e chave",widthDp=360,heightDp=800,locale="pt-rBR")
@Composable
fun PlannerEmptyPreview() = PlannerSurface { NavigationPlannerScreen(HomeUi(gps=GpsUi.NO_PERMISSION),NavUi(),EmptyNavActions,{}) }

@Preview(name="Planejamento · calculando",widthDp=360,heightDp=800,locale="pt-rBR")
@Composable
fun PlannerLoadingPreview() = PlannerSurface { NavigationPlannerScreen(PlannerPreviewStates.home.copy(routing=true,canStart=false,planner=PlannerUi(mapConfigured=true,currentPosition=GeoPoint(-8.284,-35.974))),NavUi(),EmptyNavActions,{}) }

@Preview(name="Planejamento · erro de rede",widthDp=360,heightDp=800,locale="pt-rBR")
@Composable
fun PlannerErrorPreview() = PlannerSurface { NavigationPlannerScreen(PlannerPreviewStates.home.copy(canStart=false,routeError="Sem conexão com o serviço de rotas.",planner=PlannerUi(mapConfigured=true,currentPosition=GeoPoint(-8.284,-35.974))),NavUi(),EmptyNavActions,{}) }
