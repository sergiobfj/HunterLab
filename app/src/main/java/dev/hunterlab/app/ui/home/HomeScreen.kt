package dev.hunterlab.app.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.hunterlab.app.ui.components.*
import dev.hunterlab.app.ui.contract.*
import dev.hunterlab.app.ui.theme.HunterColors
import dev.hunterlab.app.ui.theme.HunterIcons

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    state: HomeUi,
    actions: NavActions,
    onNavigation: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val query = state.search.query
    var coordinatesOpen by rememberSaveable { mutableStateOf(false) }
    var coordinates by rememberSaveable { mutableStateOf("") }
    var coordinateError by remember { mutableStateOf<String?>(null) }
    val connect = rememberTripperConnect(state.tripper, actions)
    val keyboardVisible = WindowInsets.isImeVisible
    Column(modifier.fillMaxSize()) {
        DailyScreen(Modifier.weight(1f)) {
            DailyHeading("Para onde vamos?", "Sua próxima saída começa aqui.")
            OutlinedButton(
                onClick = { if (state.tripper.canConnect) connect() else onNavigation() },
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Icon(HunterIcons.Bluetooth, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (state.tripper.canConnect) "Conectar Tripper" else state.tripper.label)
            }
            if (state.tripper.canConnect) SupportingText(state.tripper.label)
            if (state.navigationActive) {
                ActionRow("Voltar à navegação", onNavigation, "Seu trajeto está em andamento.", HunterIcons.Navigation)
            }
            OutlinedTextField(
                value = query,
                onValueChange = actions::search,
                label = { Text("Buscar destino") },
                placeholder = { Text("Lugar ou endereço") },
                leadingIcon = { Icon(HunterIcons.Search, contentDescription = null) },
                trailingIcon = if (query.isNotEmpty()) {
                    { IconButton(onClick = { actions.search("") }) { Icon(HunterIcons.Close, "Limpar busca") } }
                } else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { actions.search(query) }),
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = { coordinatesOpen = !coordinatesOpen }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(if (coordinatesOpen) "Fechar coordenadas" else "Coordenadas")
            }
            if (coordinatesOpen) {
                OutlinedTextField(
                    value = coordinates, onValueChange = { coordinates = it; coordinateError = null },
                    label = { Text("Latitude, longitude") }, placeholder = { Text("-23.5505, -46.6333") },
                    supportingText = { Text(coordinateError ?: "Cole ou digite as coordenadas do destino.") },
                    isError = coordinateError != null, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(
                    onClick = {
                        coordinateError = actions.setDestinationFromText(coordinates)
                        if (coordinateError == null) coordinatesOpen = false
                    }, enabled = coordinates.isNotBlank(), modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("Usar coordenadas") }
            }
            if (!state.providerConfigured) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Configure a chave de rotas para traçar seu caminho.", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onSettings, modifier = Modifier.heightIn(min = 48.dp)) { Text("Abrir Configurações") }
                }
            }
            if (state.search.searching) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                SupportingText("Buscando destinos…")
            }
            state.search.error?.let { SupportingText(it, error = true) }
            if (state.search.results.isNotEmpty()) {
                SectionTitle("Resultados")
                state.search.results.forEach { destination ->
                    ActionRow(destination.label, { actions.selectDestination(destination) }, destination.detail, HunterIcons.Route)
                }
            } else if (query.isBlank() && state.recent.isNotEmpty()) {
                SectionTitle("Recentes")
                state.recent.forEach { destination ->
                    ActionRow(destination.label, { actions.selectDestination(destination) }, destination.detail, HunterIcons.Clock)
                }
            }
            state.destination?.let { destination ->
                HorizontalDivider()
                Row {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SupportingText("DESTINO")
                        Text(destination.label, style = MaterialTheme.typography.titleLarge)
                        destination.detail?.let { SupportingText(it) }
                    }
                    IconButton(onClick = actions::clearDestination) { Icon(HunterIcons.Close, "Limpar destino") }
                }
                if (state.routing) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    SupportingText("Calculando rota…")
                }
                state.routeError?.let { SupportingText(it, error = true) }
                state.route?.let { route ->
                    Text("${route.distanceText} · ${route.durationText}", style = MaterialTheme.typography.headlineSmall)
                    Text("Chegada ${route.arrivalText}", color = HunterColors.TextSecondary)
                    SupportingText(route.attribution)
                }
                OutlinedButton(
                    onClick = actions::previewRoute,
                    enabled = state.providerConfigured && !state.routing && !state.navigationActive,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text(if (state.route == null) "Calcular rota" else "Atualizar rota") }
            }
        }
        if (!keyboardVisible) Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
            SupportingText("Perfil de carro · Rotas não validadas para motos. Confira as restrições de circulação.")
            PrimaryAction(
                "Iniciar navegação",
                onClick = { actions.startNavigation(false); onNavigation() },
                enabled = state.canStart && !state.routing && !state.navigationActive,
                icon = HunterIcons.Navigation,
            )
            TextButton(
                onClick = { actions.startNavigation(true); onNavigation() },
                enabled = state.canStart && !state.routing && !state.navigationActive,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text("Simular trajeto") }
        }
    }
}
