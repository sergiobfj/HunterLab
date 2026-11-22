package dev.hunterlab.app.ui.playground

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hunterlab.app.ui.components.*
import dev.hunterlab.app.ui.theme.HunterColors
import dev.hunterlab.app.ui.theme.HunterIcons
import dev.hunterlab.app.ui.tripper.TripperDisplay
import dev.hunterlab.core.link.*
import dev.hunterlab.core.playground.*
import dev.hunterlab.protocol.DayPeriod
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class PlaygroundTab(val label: String) { CATALOG("Catálogo"), EFFECTS("Efeitos"), HISTORY("Histórico") }

@Composable
fun PlaygroundScreen(vm: PlaygroundViewModel, contentPadding: PaddingValues) {
    val state by vm.ui.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val view = LocalView.current
    DisposableEffect(lifecycle, vm) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) vm.leave() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); vm.leave() }
    }
    DisposableEffect(state.link.active, state.link.purpose) {
        view.keepScreenOn = state.link.active && state.link.purpose == SessionPurpose.PLAYGROUND
        onDispose { view.keepScreenOn = false }
    }
    PlaygroundContent(state, vm, contentPadding)
}

/** Composição sem ViewModel, também usada nas prévias sem hardware. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlaygroundContent(state: PlaygroundUiState, actions: PlaygroundActions, contentPadding: PaddingValues = PaddingValues(), initialTab: PlaygroundTab = PlaygroundTab.CATALOG) {
    var tab by remember { mutableStateOf(initialTab) }
    var safetyDialog by remember { mutableStateOf(false) }
    val shownSymbol = state.playingIndex?.let { state.sequence.stages.getOrNull(it) }?.symbolId
        ?.let(PlaygroundCatalog::find) ?: state.selection.symbol
    Column(Modifier.fillMaxSize().padding(contentPadding)) {
    LazyColumn(
        Modifier.weight(1f).widthIn(max = 720.dp).fillMaxWidth().align(Alignment.CenterHorizontally), contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            DailyHeading("Tripper Playground", "Explore o visor com a moto parada.")
            if (state.navigationActive) SupportingText("Navegação ativa: o modo físico está bloqueado.", error = true)
        }
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TripperDisplay(state.preview, LocalTime.of(14, 32), Modifier.widthIn(max = 190.dp), showCaption = false)
                Text(shownSymbol.label, style = MaterialTheme.typography.titleLarge)
                Text(shownSymbol.evidence(state.selection.period).label, style = MaterialTheme.typography.bodySmall, color = HunterColors.TextSecondary)
                state.playingIndex?.let { SupportingText("Etapa ${it + 1} de ${state.sequence.stages.size} · prévia simulada") }
                SupportingText(if (state.physicalRunning) "Experimento físico em andamento" else "Prévia aproximada · não é uma captura do visor")
            }
        }
        item {
            ChoiceRow(DayPeriod.entries, state.selection.period, { it.label }, actions::period)
            if (state.selection.symbol.iconCode != null && state.selection.symbol.iconCode !in setOf(0x45, 0x1C)) {
                ChoiceRow(IconSlot.entries, state.selection.slot, { if (it == IconSlot.LARGE) "Ícone principal" else "Seta pequena" }, actions::slot)
            }
            ChoiceRow(PlaygroundTab.entries, tab, { it.label }, { tab = it })
            state.message?.let { SupportingText(it, error = true) }
        }
        when (tab) {
            PlaygroundTab.CATALOG -> {
                item { HardwarePanel(state, actions, onEnable = { safetyDialog = true }) }
                item {
                    ChoiceRow(PlaygroundCategory.entries, state.category, { it.label }, actions::category)
                    EvidenceFilter(state.evidence, actions::evidence)
                }
                if (state.symbols.isEmpty()) item { SupportingText("Nenhum símbolo com essa evidência nesta categoria e modo.") }
                items(state.symbols.size, key = { state.symbols[it].id }) { index ->
                    val symbol = state.symbols[index]
                    OutlinedButton(
                        onClick = { actions.select(symbol.id) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (state.selection.symbolId == symbol.id) MaterialTheme.colorScheme.primaryContainer else HunterColors.Surface,
                            contentColor = HunterColors.TextPrimary,
                        ), shape = MaterialTheme.shapes.small,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(symbol.label, style = MaterialTheme.typography.bodyLarge)
                            SupportingText(symbol.evidence(state.selection.period).label)
                        }
                        if (state.selection.symbolId == symbol.id) Icon(HunterIcons.Check, "Selecionado", Modifier.size(18.dp))
                    }
                }
                item {
                    CollapsibleCard("Detalhes do símbolo", summary = "Comando e limites") {
                        SupportingText("Tipo: ${state.selection.symbol.category.label} · ${state.selection.symbol.command}")
                        SupportingText("Builder: ${state.selection.symbol.builder}")
                        SupportingText("71 códigos catalogados não significam 71 símbolos validados na Hunter. Não há envio livre de bytes.")
                        SupportingText("GIFs, vídeos e imagens livres não são suportados pelo protocolo conhecido do Tripper.")
                    }
                }
            }
            PlaygroundTab.EFFECTS -> {
                item {
                    SectionTitle("Sequências na prévia")
                    SupportingText("Até 8 etapas e 45 segundos. Sequências não são GIFs. Execução física bloqueada.")
                    ChoiceRow(PreviewSequence.presets.keys.toList(), null, { it }, actions::preset)
                }
                items(state.sequence.stages.size) { index ->
                    val stage = state.sequence.stages[index]
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${index + 1}. ${PlaygroundCatalog.find(stage.symbolId)?.label}", style = MaterialTheme.typography.titleMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { actions.moveStage(index, -1) }, enabled = index > 0) { Text("Subir") }
                            TextButton(onClick = { actions.moveStage(index, 1) }, enabled = index < state.sequence.stages.lastIndex) { Text("Descer") }
                            TextButton(onClick = { actions.removeStage(index) }) { Text("Remover") }
                        }
                        ChoiceRow(listOf(2_000L, 4_000L, 6_000L, 10_000L), stage.durationMs, { "${it / 1000} s" }, { actions.stageDuration(index, it) })
                        HorizontalDivider()
                    }
                }
                item {
                    TextButton(onClick = actions::addStage, enabled = state.sequence.stages.size < PreviewSequence.MAX_STAGES) { Text("Adicionar ${state.selection.symbol.label}") }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = actions::play, enabled = state.sequence.valid && !state.physicalRunning) { Text(if (state.playback == PlaybackPhase.PAUSED) "Continuar" else "Reproduzir") }
                        OutlinedButton(onClick = actions::pause, enabled = state.playback == PlaybackPhase.PLAYING) { Text("Pausar") }
                        TextButton(onClick = { actions.stopPreview() }) { Text("Interromper prévia") }
                        TextButton(onClick = actions::restart, enabled = state.sequence.valid && !state.physicalRunning) { Text("Reiniciar") }
                    }
                    SupportingText("${state.sequence.durationMs / 1000} s · ${state.playback.name.lowercase()}")
                }
            }
            PlaygroundTab.HISTORY -> {
                item {
                    SectionTitle("Resultados locais")
                    SupportingText("Registre o que você viu. Escrita entregue ao Android não confirma o símbolo no Tripper. Observações são opções estruturadas, sem texto livre ou segredos.")
                    val context = LocalContext.current
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { copy(context, "Experimentos Tripper", actions.report()) }, enabled = state.records.isNotEmpty()) { Text("Copiar relatório sanitizado") }
                        TextButton(onClick = actions::clearHistory, enabled = state.records.isNotEmpty()) { Text("Apagar histórico") }
                    }
                    if (state.records.isEmpty()) SupportingText("Nenhum experimento registrado. Uma prévia concluída ou um teste físico aparece aqui.")
                }
                items(state.records.size) { reverse ->
                    val index = state.records.lastIndex - reverse
                    val record = state.records[index]
                    CollapsibleCard(
                        record.symbols.joinToString { PlaygroundCatalog.find(it)?.label ?: it },
                        summary = "${record.kind.label} · ${record.durationMs / 1000} s · ${record.end.label}",
                    ) {
                        SupportingText(Instant.ofEpochMilli(record.atEpochMs).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")))
                        SupportingText("Pacotes entregues: ${record.delivered ?: "não se aplica"} · respostas: ${record.responses ?: "não se aplica"}")
                        ChoiceRow(VisualObservation.entries, record.observation, { it.label }, { actions.observe(index, it) })
                        if (record.kind == ExperimentKind.PHYSICAL) {
                            SectionTitle("Retorno do relógio")
                            ChoiceRow(ClockRecovery.entries, record.clockRecovery, { it.label }, { actions.clock(index, it) })
                        }
                    }
                }
            }
        }
    }
        // Fora da rolagem: permanece acessível em todas as abas durante a sessão física.
        if (state.link.active && state.link.purpose == SessionPurpose.PLAYGROUND) {
            Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().align(Alignment.CenterHorizontally).padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (tab == PlaygroundTab.CATALOG && state.link.state == LinkState.AUTHENTICATED) {
                    PrimaryAction("Exibir no Tripper", actions::show,
                        enabled = state.physicalEnabled && state.selection.symbol.hardwareEligible && state.link.transmissionAuthorized && !state.navigationActive && !state.physicalRunning && state.playback !in setOf(PlaybackPhase.PLAYING, PlaybackPhase.PAUSED))
                }
                OutlinedButton(onClick = actions::stopPhysical, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Interromper") }
            }
        }
    }
    if (safetyDialog) PhysicalSafetyDialog(
        enabled = !state.navigationActive,
        onDismiss = { safetyDialog = false },
        onConfirm = { actions.arm(it); safetyDialog = false },
    )
}

@Composable
private fun EvidenceFilter(selected: VisualEvidence?, onSelect: (VisualEvidence?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("Evidência: ${selected?.label ?: "todas"}")
            Icon(HunterIcons.ChevronDown, null, Modifier.size(18.dp))
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            (listOf<VisualEvidence?>(null) + VisualEvidence.entries).forEach { evidence ->
                DropdownMenuItem(text = { Text(evidence?.label ?: "Todas as evidências") }, onClick = { onSelect(evidence); expanded = false })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HardwarePanel(state: PlaygroundUiState, actions: PlaygroundActions, onEnable: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalDivider()
        SectionTitle("Teste no Tripper")
        DailySwitch("Modo físico", "Somente bússolas documentadas nesta versão.", state.physicalEnabled, { if (it) onEnable() else actions.stopPhysical() })
        if (!state.selection.symbol.hardwareEligible) SupportingText("Este símbolo está disponível somente na prévia. Não será enviado.")
        if (state.physicalEnabled || state.link.purpose == SessionPurpose.PLAYGROUND && state.link.active) {
            SupportingText("Moto parada · até 15 s · reenvio a cada 2 s")
            val link = state.link
            if (!link.active) {
                OutlinedButton(onClick = actions::scan, enabled = !state.scanning && !state.navigationActive) { Text(if (state.scanning) "Buscando Tripper…" else "Buscar Tripper") }
                SupportingText("${state.candidates} candidato(s). Mais de um bloqueia a conexão.")
                OutlinedButton(onClick = actions::connect, enabled = !state.scanning && state.candidates == 1 && !state.navigationActive) { Text("Conectar Tripper") }
            } else if (link.purpose != SessionPurpose.PLAYGROUND) {
                SupportingText("Outra ferramenta controla a sessão. Encerre-a na tela de origem.", error = true)
            } else {
                SupportingText(link.state.label)
                link.message?.let { SupportingText(it.detail, error = link.state == LinkState.ERROR) }
                when (link.state) {
                    LinkState.READY_FOR_AUTH -> Button(onClick = actions::requestPin) { Text("Mostrar PIN no Tripper") }
                    LinkState.WAITING_FOR_PIN -> PinEntry(link.pinAttemptsUsed, actions)
                    LinkState.AUTHENTICATING -> {
                        SupportingText("Confira o visor: o Tripper aceitou o PIN?")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = actions::confirmPin, enabled = link.authCommandDelivered) { Text("Sim, aceitou") }
                            OutlinedButton(onClick = actions::rejectPin) { Text("Está pedindo PIN") }
                        }
                    }
                    LinkState.AUTHENTICATED -> {
                        ChoiceRow(listOf(5_000L, 10_000L, 15_000L), state.durationMs, { "${it / 1000} s" }, actions::duration)
                    }
                    else -> Unit
                }
                OutlinedButton(onClick = actions::stopPhysical, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Interromper") }
            }
            SupportingText("Interromper cancela novos envios. O relógio pode voltar pelo watchdog ou exigir ciclo de ignição; não há restauração imediata garantida.")
        }
    }
}

@Composable
private fun PinEntry(attempt: Int, actions: PlaygroundActions) {
    val entered = remember(attempt) { TextFieldState() }
    OutlinedSecureTextField(
        state = entered, label = { Text("PIN mostrado no visor") },
        textObfuscationMode = TextObfuscationMode.Hidden,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth(),
    )
    Button(onClick = { val value = entered.text.toString(); entered.edit { replace(0, length, "") }; actions.submitPin(value) }, enabled = entered.text.isNotBlank()) { Text("Enviar PIN") }
}

@Composable
private fun PhysicalSafetyDialog(enabled: Boolean, onDismiss: () -> Unit, onConfirm: (TransmissionAck) -> Unit) {
    val labels = listOf("A moto está parada.", "O aplicativo oficial está fechado.", "Li o procedimento de conexão e de testes físicos.", "Aceito o risco de alterar o pareamento.", "Sei como interromper e conferir o retorno do relógio.")
    var checked by remember { mutableStateOf(emptySet<Int>()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Teste físico estacionário") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) { labels.forEachIndexed { index, label ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(index in checked, { checked = if (it) checked + index else checked - index })
                Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
        } }
    }, confirmButton = {
        TextButton(onClick = { onConfirm(TransmissionAck(true, true, true, true, true)) }, enabled = enabled && checked.size == labels.size) { Text("Ativar nesta sessão") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}
