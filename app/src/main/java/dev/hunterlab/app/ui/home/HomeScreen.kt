package dev.hunterlab.app.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.hunterlab.app.ui.components.*
import dev.hunterlab.app.ui.contract.*
import dev.hunterlab.app.ui.navigation.TripperConnectButton
import dev.hunterlab.app.ui.navigation.statusColor
import dev.hunterlab.app.ui.theme.HunterColors
import dev.hunterlab.app.ui.theme.HunterIcons

@Composable
fun HomeScreen(
    state: HomeUi,
    actions: NavActions,
    padding: PaddingValues = PaddingValues(),
    engineAvailable: Boolean = true,
    onSettings: () -> Unit = {},
    onNavigation: () -> Unit = {},
    onSimulator: () -> Unit = {},
    legacyConnect: (() -> Unit)? = null,
) {
    var coordinates by remember { mutableStateOf(false) }
    ProductPage(padding = padding) {
        PageHeading("FEITA PARA O SEU CAMINHO", if (state.destination == null) "Para onde vamos?" else "Seu próximo trajeto.")
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StatusLine(state.tripper.label to state.tripper.statusColor())
            if (state.tripper.status in setOf(TripperUiStatus.DISCONNECTED, TripperUiStatus.ERROR)) {
                TripperConnectButton(state.tripper, actions, legacyConnect)
            } else if (legacyConnect != null) {
                TextButton(legacyConnect, Modifier.heightIn(min = 48.dp)) { Text("Ver conexão") }
            } else if (state.tripper.status in setOf(TripperUiStatus.READY_TO_PAIR, TripperUiStatus.WAITING_PIN, TripperUiStatus.CONFIRM_PIN)) {
                TextButton(onNavigation, Modifier.heightIn(min = 48.dp)) { Text("Concluir conexão") }
            }
        }
        if (!engineAvailable) {
            ProductPanel {
                Text("Seu Tripper, sem complicação.", style = MaterialTheme.typography.titleLarge)
                Text("Conecte o display e explore os recursos já disponíveis. A navegação real chega na próxima versão.", color = HunterColors.TextSecondary)
                PrimaryAction("Explorar navegação", onSimulator)
                Text("Abre um simulador. Nenhuma instrução de rota é enviada ao Tripper.", style = MaterialTheme.typography.bodySmall, color = HunterColors.TextSecondary)
            }
        } else if (state.navigationActive) {
            ProductPanel {
                SectionLabel("SEU TRAJETO")
                Text(state.destination?.label ?: "Navegação em andamento", style = MaterialTheme.typography.titleLarge)
                PrimaryAction("Continuar navegação", onNavigation)
            }
        } else {
            if (state.destination == null) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.search.query, onValueChange = actions::search,
                    label = { Text("Buscar destino") },
                    placeholder = { Text("Um lugar, um novo caminho") },
                    leadingIcon = { Icon(HunterIcons.Search, null) },
                    trailingIcon = {
                        if (state.search.searching) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { actions.search(state.search.query) }),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                )
                TextButton({ coordinates = true }, Modifier.heightIn(min = 48.dp)) { Text("Usar coordenadas") }
                state.search.error?.let { InlineNotice(it, warning = true) }
                state.search.results.forEach { result ->
                    ActionRow(result.label, result.detail, HunterIcons.Location, { actions.selectDestination(result) })
                }
                if (!state.search.searching && state.search.query.isNotBlank() && state.search.results.isEmpty() && state.search.error == null) {
                    Text("Nenhum destino encontrado. Tente outro nome ou use coordenadas.", style = MaterialTheme.typography.bodyMedium, color = HunterColors.TextSecondary)
                }
            }
            if (!state.providerConfigured) InlineNotice(
                "Configure seu provedor de rotas para começar.", action = "Configurar rotas", onAction = onSettings,
            )
            state.destination?.let { destination ->
                ProductPanel {
                    SectionLabel("DESTINO")
                    Text(destination.label, style = MaterialTheme.typography.titleLarge)
                    if (!state.routing) state.route?.let { route ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Metric("Distância", route.distanceText, Modifier.weight(1f))
                            Metric("Duração", route.durationText, Modifier.weight(1f))
                        }
                        Text("Chegada ${route.arrivalText}", color = HunterColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (state.routing) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("Preparando seu caminho…", style = MaterialTheme.typography.bodyMedium)
                    }
                    state.routeError?.let { InlineNotice(it, warning = true) }
                    if (state.route == null) {
                        PrimaryAction(if (state.routing) "Preparando trajeto…" else if (state.routeError != null) "Tentar novamente" else "Calcular rota", actions::previewRoute,
                            enabled = state.providerConfigured && !state.routing, icon = HunterIcons.Route)
                    } else {
                        PrimaryAction("Iniciar navegação", { actions.startNavigation(false); onNavigation() }, enabled = state.canStart && !state.routing)
                        TextButton({ actions.startNavigation(true); onNavigation() }, Modifier.heightIn(min = 48.dp), enabled = state.canStart && !state.routing) {
                            Text("Simular este trajeto")
                        }
                    }
                    TextButton(actions::clearDestination, Modifier.heightIn(min = 48.dp)) { Text("Trocar destino") }
                }
                state.route?.attribution?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = HunterColors.TextMuted) }
            }
            if (state.destination == null && state.recent.isNotEmpty() && state.search.query.isBlank()) {
                Column {
                    SectionLabel("DESTINOS RECENTES")
                    state.recent.forEach { recent -> ActionRow(recent.label, recent.detail, HunterIcons.Clock, { actions.selectDestination(recent) }) }
                }
            }
        }
        Text("HUNTER 350 · TRIPPER POD", style = MaterialTheme.typography.labelSmall, color = HunterColors.TextMuted)
    }
    if (coordinates) CoordinateDialog(actions, onDismiss = { coordinates = false })
}

@Composable
private fun CoordinateDialog(actions: NavActions, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Destino por coordenadas") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Cole latitude e longitude ou um link geo:.", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(value, { value = it; error = null }, label = { Text("Latitude, longitude") }, modifier = Modifier.fillMaxWidth(), isError = error != null)
                error?.let { Text(it, color = HunterColors.Error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button({ error = actions.setDestinationFromText(value); if (error == null) onDismiss() }, enabled = value.isNotBlank()) { Text("Usar destino") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancelar") } },
    )
}
