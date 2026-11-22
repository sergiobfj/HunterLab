package dev.hunterlab.app.ui.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.hunterlab.app.ui.components.*
import dev.hunterlab.app.ui.contract.*
import dev.hunterlab.app.ui.theme.HunterColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DestinationSearchContent(home: HomeUi, actions: NavActions, onSelected: () -> Unit,
    onCoordinates: () -> Unit, onSettings: () -> Unit) {
    val keyboard=LocalSoftwareKeyboardController.current
    Column(Modifier.fillMaxWidth().fillMaxHeight(.9f).imePadding().padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("Buscar destino",style=MaterialTheme.typography.titleLarge)
        OutlinedTextField(value=home.search.query,onValueChange=actions::search,label={Text("Lugar ou endereço")},
            singleLine=true,modifier=Modifier.fillMaxWidth(),keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),
            keyboardActions=KeyboardActions(onSearch={actions.search(home.search.query);keyboard?.hide()}))
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp), verticalArrangement=Arrangement.spacedBy(4.dp)) { TextButton(onClick=onCoordinates) {Text("Coordenadas ou geo:")};TextButton(onClick=onSettings) {Text("Chaves")}}
        if (home.search.searching) LinearProgressIndicator(Modifier.fillMaxWidth())
        home.search.error?.let {SupportingText(it,error=true)}
        val found=home.search.results
        if (!home.search.searching&&home.search.query.length>=3&&found.isEmpty()&&home.search.error==null) SupportingText("Nenhum estabelecimento encontrado. Tente o nome e a cidade.")
        LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(bottom=24.dp)) {
            fun select(d:DestinationUi) { actions.selectDestination(d);onSelected() }
            if(found.isNotEmpty()) {
                item {SectionTitle("Sugestões")}
                items(found) {d->PlaceRow(d,home.planner.favorites.any{samePlace(it,d)}, {select(d)}, {actions.toggleFavorite(d)})}
            } else if(home.search.query.isBlank()) {
                if(home.planner.favorites.isNotEmpty()) {
                    item{SectionTitle("Favoritos")};items(home.planner.favorites){d->PlaceRow(d,true,{select(d)},{actions.toggleFavorite(d)})}
                }
                if(home.recent.isNotEmpty()) {
                    item{SectionTitle("Recentes")};items(home.recent){d->PlaceRow(d,home.planner.favorites.any{samePlace(it,d)},{select(d)},{actions.toggleFavorite(d)})}
                }
            }
            item {SupportingText("Busca: MapTiler / OpenStreetMap. Distância das sugestões é em linha reta; o percurso é calculado após selecionar.")}
        }
    }
}

internal fun samePlace(a:DestinationUi,b:DestinationUi)=kotlin.math.abs(a.lat-b.lat)<1e-5&&kotlin.math.abs(a.lon-b.lon)<1e-5

@Composable
private fun PlaceRow(d:DestinationUi,saved:Boolean,onSelect:()->Unit,onSave:()->Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f).clickable(onClick=onSelect).padding(vertical=8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(d.label,style=MaterialTheme.typography.titleMedium)
            d.detail?.let{Text(it,style=MaterialTheme.typography.bodyMedium,color=HunterColors.TextSecondary)}
            Text(d.straightLineDistanceText?:"Distância em linha reta indisponível",style=MaterialTheme.typography.labelMedium,color=HunterColors.TextSecondary)
        }
        TextButton(onClick=onSave) {Text(if(saved) "Salvo" else "Salvar")}
    }
    HorizontalDivider()
}
