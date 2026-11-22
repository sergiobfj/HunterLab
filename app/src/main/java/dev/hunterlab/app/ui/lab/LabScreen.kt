package dev.hunterlab.app.ui.lab

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hunterlab.app.ui.components.BulletText
import dev.hunterlab.app.ui.components.ChoiceRow
import dev.hunterlab.app.ui.components.CollapsibleCard
import dev.hunterlab.app.ui.components.HunterCard
import dev.hunterlab.app.ui.components.KeyValue
import dev.hunterlab.app.ui.components.LabeledSwitch
import dev.hunterlab.app.ui.components.MessageCard
import dev.hunterlab.app.ui.components.Pill
import dev.hunterlab.app.ui.components.copy
import dev.hunterlab.app.ui.theme.HunterColors
import dev.hunterlab.app.ui.theme.HunterIcons
import dev.hunterlab.app.ui.theme.MonoDigits
import dev.hunterlab.app.ui.tripper.TripperDisplay
import dev.hunterlab.core.ble.ScannedDevice
import dev.hunterlab.core.ble.Severity
import dev.hunterlab.core.ble.UserMessage
import dev.hunterlab.core.display.PreviewMapper
import dev.hunterlab.core.link.AfterTestObservation
import dev.hunterlab.core.link.CompassObservation
import dev.hunterlab.core.link.ConnectOptions
import dev.hunterlab.core.link.ServerMode
import dev.hunterlab.core.link.LinkFormat
import dev.hunterlab.core.link.LinkSnapshot
import dev.hunterlab.core.link.LinkState
import dev.hunterlab.core.link.LogLevel
import dev.hunterlab.core.link.TransmissionAck
import dev.hunterlab.protocol.CompassDirection
import dev.hunterlab.protocol.DayPeriod
import dev.hunterlab.protocol.TripperScreen
import java.time.LocalTime

/**
 * Laboratório BLE (experimental). Um passo por vez; Interromper sempre visível com sessão ativa.
 * O campo de PIN usa `remember` (não é salvo em estado persistente) e é limpo após o envio.
 */
@Composable
fun LabScreen(vm: LabViewModel, device: ScannedDevice, contentPadding: PaddingValues, onClose: () -> Unit) {
    val s by vm.link.collectAsStateWithLifecycle()
    val feedback by vm.feedback.collectAsStateWithLifecycle()
    val options by vm.options.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<LabDialog?>(null) }

    BackHandler {
        if (s.active) vm.interrupt()
        onClose()
    }

    // Sair do app interrompe a sessão (segurança). Para a tela apagar por inatividade não interromper o
    // handshake no meio (ex.: enquanto o PIN é digitado), a tela fica acesa só durante a sessão.
    val view = LocalView.current
    DisposableEffect(s.active) {
        view.keepScreenOn = s.active
        onDispose { view.keepScreenOn = false }
    }

    Column(Modifier.padding(top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding())) {
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Header(device, s, onBack = { if (s.active) vm.interrupt(); onClose() }) }
            item { StatusCard(s) }
            feedback?.let { item { MessageCard(UserMessage("Comando recusado", it, severity = Severity.WARNING)) { TextButton(onClick = vm::dismissFeedback) { Text("Fechar") } } } }
            s.message?.takeIf { s.state == LinkState.ERROR || s.state == LinkState.DISCONNECTED }?.let { item { MessageCard(it) } }
            item { NextStepCard(s, device, vm, openDialog = { dialog = it }) }
            if (s.state == LinkState.AUTHENTICATED || s.state == LinkState.DISPLAY_ACTIVE || s.compassTestsRun > 0) {
                item { CompassCard(s, vm, openDialog = { dialog = it }) }
            }
            item { DiagnosticsCard(s) }
            item { ConnectionDiagnosticsCard(s, options, vm) }
            item { HistoryCard(s, vm) }
        }
        if (s.active) InterruptBar(s, onInterrupt = vm::interrupt, onDisconnectNow = { dialog = LabDialog.DisconnectNow })
    }

    when (val d = dialog) {
        LabDialog.Authorize -> AuthorizeDialog(onConfirm = { vm.authorize(it); dialog = null }, onDismiss = { dialog = null })
        LabDialog.Connect -> ConfirmDialog(
            title = "Conectar ao Tripper?",
            lines = listOf(
                "Conectar não envia nada. O app só verifica o perfil GATT e abre o servidor local.",
                "Opções: anúncio recente ${if (options.requireFreshAdvertisement) "obrigatório" else "NÃO exigido (modo 0.2.0)"} · servidor local: ${options.serverMode.label.lowercase()}.",
                "Não faça a inspeção da aba Dispositivos no mesmo ciclo de ignição: após uma desconexão pelo celular, o Tripper pode parar de anunciar.",
                "Enquanto conectado, o app oficial Royal Enfield provavelmente não conseguirá se conectar.",
                "Se a transmissão estiver autorizada, toque no handshake logo depois: o Tripper pode encerrar o link ~5 s sem pacotes.",
            ),
            confirm = "Conectar",
            onConfirm = { vm.connect(device); dialog = null },
            onDismiss = { dialog = null },
        )
        is LabDialog.Compass -> ConfirmDialog(
            title = "Transmitir bússola real?",
            lines = listOf(
                "Direção ${d.direction.label}, ${d.period.label.lowercase()}, por até ${d.durationMs / 1000} s; reenvio a cada ~2 s.",
                "Só o comando de bússola documentado (Confirmado na Meteor 350; não testado na Hunter).",
                "Ao terminar, pela referência, o Tripper encerra o link ~5 s depois. O relógio pode só voltar após nova conexão ou ciclo de ignição.",
                "Use Interromper a qualquer momento.",
            ),
            confirm = "Transmitir",
            requireCheck = "Entendi e a moto está parada",
            onConfirm = { vm.startCompass(d.direction, d.period, d.durationMs); dialog = null },
            onDismiss = { dialog = null },
        )
        LabDialog.DisconnectNow -> ConfirmDialog(
            title = "Desconectar pelo celular agora?",
            lines = listOf(
                "Na Meteor 350, após desconexão iniciada pelo celular o Tripper só voltou a anunciar depois de desligar e ligar a ignição.",
                "Prefira aguardar: o Tripper costuma encerrar o link sozinho em ~5 s.",
            ),
            confirm = "Desconectar",
            onConfirm = { vm.disconnectNow(); dialog = null },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

private sealed interface LabDialog {
    data object Authorize : LabDialog
    data object Connect : LabDialog
    data object DisconnectNow : LabDialog
    data class Compass(val direction: CompassDirection, val period: DayPeriod, val durationMs: Long) : LabDialog
}

@Composable
private fun Header(device: ScannedDevice, s: LinkSnapshot, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(HunterIcons.Back, "Voltar", tint = HunterColors.TextPrimary) }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Laboratório BLE", style = MaterialTheme.typography.titleLarge, color = HunterColors.TextPrimary)
                Pill("EXPERIMENTAL", HunterColors.Unverified)
            }
            Text(
                "${device.name ?: "Sem nome"} · ${LinkFormat.maskAddress(device.address)}",
                style = MonoDigits.copy(fontSize = 12.sp),
                color = HunterColors.TextSecondary,
            )
        }
    }
}

private fun connectionText(state: LinkState) = when (state) {
    LinkState.DISCONNECTED, LinkState.CONNECTING, LinkState.DISCOVERING, LinkState.STOPPING, LinkState.ERROR -> state.label
    else -> "Conectado"
}

private fun authText(s: LinkSnapshot) = when (s.state) {
    LinkState.WAITING_FOR_PIN -> "Aguardando PIN (${s.pinAttemptsUsed}/${s.pinAttemptsMax})"
    LinkState.AUTHENTICATING -> "Aguardando sua confirmação"
    LinkState.AUTHENTICATED, LinkState.DISPLAY_ACTIVE -> "Autenticado por confirmação visual (não pelo protocolo)"
    else -> "Não autenticado"
}

@Composable
private fun StatusCard(s: LinkSnapshot) {
    val stateColor = when (s.state) {
        LinkState.ERROR -> HunterColors.Error
        LinkState.AUTHENTICATED, LinkState.DISPLAY_ACTIVE -> HunterColors.Confirmed
        LinkState.DISCONNECTED -> HunterColors.TextSecondary
        else -> HunterColors.Amber
    }
    HunterCard(title = "Estado", trailing = { Pill(s.state.label, stateColor) }) {
        KeyValue("Conexão", connectionText(s.state))
        KeyValue("Autenticação", authText(s))
        KeyValue(
            "Transmissão real",
            when {
                s.transmissionAuthorized -> "Autorizada nesta sessão"
                s.preAuthorizedRemainingMs != null -> "Pré-autorizada para a próxima conexão"
                else -> "Bloqueada"
            },
            if (s.transmissionAuthorized || s.preAuthorizedRemainingMs != null) HunterColors.Amber else HunterColors.Confirmed,
        )
        if (s.deadline != null && s.deadlineRemainingMs != null) {
            KeyValue("Tempo para ${s.deadline!!.label}", "${(s.deadlineRemainingMs!! + 999) / 1000} s", HunterColors.Amber)
        }
    }
}

@Composable
private fun NextStepCard(s: LinkSnapshot, device: ScannedDevice, vm: LabViewModel, openDialog: (LabDialog) -> Unit) {
    HunterCard(title = "Próximo passo") {
        when (s.state) {
            LinkState.DISCONNECTED -> {
                Text(
                    "Leia docs/PRIMEIRO_HANDSHAKE.md. Ligue a ignição só quando for conectar: o Tripper anuncia por ~30 s.",
                    style = MaterialTheme.typography.bodySmall, color = HunterColors.TextSecondary,
                )
                if (s.preAuthorizedRemainingMs == null) {
                    OutlinedButton(onClick = { openDialog(LabDialog.Authorize) }) { Text("Autorizar transmissão (próxima conexão)") }
                } else {
                    TextButton(onClick = vm::cancelPreAuthorization) { Text("Cancelar pré-autorização") }
                }
                Button(onClick = { openDialog(LabDialog.Connect) }) {
                    Icon(HunterIcons.Link, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Conectar (não envia nada)")
                }
            }
            LinkState.CONNECTING, LinkState.DISCOVERING -> {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = HunterColors.Amber, trackColor = HunterColors.SurfaceHighest)
                Text("${s.state.label}… nenhuma escrita é feita nesta fase.", style = MaterialTheme.typography.bodySmall, color = HunterColors.TextSecondary)
            }
            LinkState.READY_FOR_AUTH -> {
                if (!s.transmissionAuthorized) {
                    Text("Perfil verificado. Transmissão bloqueada.", style = MaterialTheme.typography.bodyMedium, color = HunterColors.TextPrimary)
                    OutlinedButton(onClick = { openDialog(LabDialog.Authorize) }) { Text("Autorizar transmissão") }
                } else {
                    Text(
                        "Toque logo: sem pacotes o Tripper pode encerrar o link em ~5 s (se cair, ele volta a anunciar e você pode conectar de novo).",
                        style = MaterialTheme.typography.bodySmall, color = HunterColors.Amber,
                    )
                    Button(onClick = vm::startPairing) { Text("Enviar HANDSHAKE_SHOW_PIN (parear)") }
                    OutlinedButton(onClick = vm::resume, enabled = s.canResume) { Text("Retomar (HANDSHAKE_RESUME)") }
                    if (!s.canResume) {
                        Text("Retomar só fica disponível após uma autenticação confirmada nesta execução do app.", style = MaterialTheme.typography.bodySmall, color = HunterColors.TextMuted)
                    }
                }
            }
            LinkState.WAITING_FOR_PIN -> PinEntry(s, vm)
            LinkState.AUTHENTICATING -> {
                Text("O Tripper aceitou?", style = MaterialTheme.typography.titleMedium, color = HunterColors.TextPrimary)
                Text(
                    "As respostas do Tripper não são documentadas, então só você pode confirmar pelo display. " +
                        if (s.authCommandDelivered) "O comando já foi entregue ao Bluetooth do celular." else "Aguardando o Android entregar o comando…",
                    style = MaterialTheme.typography.bodySmall, color = HunterColors.TextSecondary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = vm::confirmAuthenticated, enabled = s.authCommandDelivered) { Text("Sim, aceitou") }
                    OutlinedButton(onClick = vm::reportPinRequired) { Text("Está pedindo PIN") }
                }
            }
            LinkState.AUTHENTICATED -> {
                Text("Autenticado por confirmação visual sua — o protocolo não confirma. Sem tela enviada, o Tripper deve mostrar o próprio relógio.", style = MaterialTheme.typography.bodySmall, color = HunterColors.TextSecondary)
                OutlinedButton(onClick = vm::syncClock) { Text("Acertar relógio (SET_TIME)") }
            }
            LinkState.DISPLAY_ACTIVE -> Text("Teste de tela em andamento (abaixo).", style = MaterialTheme.typography.bodySmall, color = HunterColors.Amber)
            LinkState.STOPPING -> Text(
                "Transmissão encerrada. Aguardando o Tripper fechar o link sozinho. Encerrar a sessão não garante a volta imediata do relógio.",
                style = MaterialTheme.typography.bodySmall, color = HunterColors.TextSecondary,
            )
            LinkState.ERROR -> Text(
                "Nada mais será transmitido nesta sessão. Toque em Interromper; o app libera o link em seguida.",
                style = MaterialTheme.typography.bodySmall, color = HunterColors.Error,
            )
        }
    }
}

@Composable
private fun PinEntry(s: LinkSnapshot, vm: LabViewModel) {
    // Só `remember`: o PIN não entra no estado salvo da Activity (que pode ir para disco).
    var pin by remember { mutableStateOf("") }
    Text("Digite o PIN mostrado no display do Tripper.", style = MaterialTheme.typography.bodyMedium, color = HunterColors.TextPrimary)
    OutlinedTextField(
        value = pin,
        onValueChange = { v -> pin = v.filter { it.isLetterOrDigit() }.take(6) },
        label = { Text("PIN") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false, imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { if (pin.isNotEmpty()) { vm.submitPin(pin); pin = "" } }),
        modifier = Modifier.widthIn(max = 240.dp),
    )
    Text(
        "Tentativa ${s.pinAttemptsUsed + 1} de ${s.pinAttemptsMax}. O PIN não é salvo nem registrado; nenhuma tentativa é automática.",
        style = MaterialTheme.typography.bodySmall, color = HunterColors.TextMuted,
    )
    Button(onClick = { vm.submitPin(pin); pin = "" }, enabled = pin.isNotEmpty()) { Text("Enviar PIN") }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompassCard(s: LinkSnapshot, vm: LabViewModel, openDialog: (LabDialog) -> Unit) {
    var direction by remember { mutableStateOf(CompassDirection.N) }
    var period by remember { mutableStateOf(DayPeriod.DAY) }
    var duration by remember { mutableLongStateOf(vm.timing.compassDefaultMs) }
    HunterCard(title = "Primeiro experimento de tela: bússola", subtitle = "Direção fixa · somente o comando documentado") {
        val run = s.compass
        if (s.state == LinkState.DISPLAY_ACTIVE && run != null) {
            LinearProgressIndicator(
                progress = { (run.elapsedMs.toFloat() / run.durationMs).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(), color = HunterColors.Amber, trackColor = HunterColors.SurfaceHighest,
            )
            KeyValue("Direção", "${run.direction.label} · ${run.period.label}")
            KeyValue("Quadros entregues ao Bluetooth", "${run.framesDelivered} de ${run.framesQueued}")
            Button(onClick = vm::stopCompass, colors = ButtonDefaults.buttonColors(containerColor = HunterColors.Error)) { Text("Parar teste") }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TripperDisplay(PreviewMapper.from(TripperScreen.Compass(direction, period)), LocalTime.now(), Modifier.width(120.dp), showCaption = false)
                Column(Modifier.padding(start = 12.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceRow(CompassDirection.entries, direction, { it.label }, { direction = it })
                }
            }
            ChoiceRow(DayPeriod.entries, period, { it.label }, { period = it })
            ChoiceRow(listOf(10_000L, 20_000L, 30_000L), duration, { "${it / 1000} s" }, { duration = it })
            Button(
                onClick = { openDialog(LabDialog.Compass(direction, period, duration)) },
                enabled = s.state == LinkState.AUTHENTICATED,
            ) { Text("Iniciar teste de bússola") }
        }
        if (s.compassTestsRun > 0 && s.state != LinkState.DISPLAY_ACTIVE) {
            Text("Registre o que você viu no Tripper:", style = MaterialTheme.typography.bodySmall, color = HunterColors.TextSecondary)
            ChoiceRow(CompassObservation.entries, null, { it.label }, vm::observe)
            ChoiceRow(AfterTestObservation.entries, null, { it.label }, vm::observe)
            s.observations.takeLast(4).forEach { BulletText(it) }
        }
    }
}

@Composable
private fun DiagnosticsCard(s: LinkSnapshot) {
    val d = s.diagnostics
    HunterCard(title = "Diagnóstico") {
        KeyValue("Perfil GATT", if (d.profileVerified) "verificado · ${d.writeMode}" else "não verificado")
        KeyValue("Servidor local", d.server.label + if (d.serverPeerConnected) " · Tripper conectado" else "")
        KeyValue("Escritas entregues / falhas", "${d.writesDelivered} / ${d.writeFailures}")
        KeyValue("Último envio", d.lastWrite?.let { "$it · há ${LinkFormat.seconds(d.lastWriteAgoMs ?: 0)}" } ?: "nenhum")
        KeyValue("Respostas recebidas", "${d.inboundAccepted} (não interpretadas)" + if (d.inboundRejected > 0) " · ${d.inboundRejected} rejeitadas" else "")
        KeyValue("Keepalive", if (d.keepaliveActive) "ativo (PING_FW após 2 s sem envio)" else "inativo")
    }
}

@Composable
private fun ConnectionDiagnosticsCard(s: LinkSnapshot, options: ConnectOptions, vm: LabViewModel) {
    val editable = s.state == LinkState.DISCONNECTED
    CollapsibleCard(
        title = "Diagnóstico de conexão (sem escrita)",
        summary = "${s.attempts.size} tentativa(s) registrada(s) · anúncio recente ${if (options.requireFreshAdvertisement) "exigido" else "não exigido"}",
    ) {
        Text(
            "Para investigar falhas como o status 147. Não autorize a transmissão durante o diagnóstico: sem autorização, " +
                "nenhuma variante escreve no Tripper. Cicle a ignição entre tentativas.",
            style = MaterialTheme.typography.bodySmall, color = HunterColors.TextSecondary,
        )
        LabeledSwitch(
            "Exigir anúncio recente (recomendado)",
            "Só chama connectGatt ao receber um anúncio conectável do Tripper, usando o dispositivo do próprio anúncio. Desligado = comportamento da 0.2.0.",
            options.requireFreshAdvertisement,
            { if (editable) vm.setRequireFreshAdvertisement(it) },
        )
        Text("Servidor GATT local", style = MaterialTheme.typography.bodySmall, color = HunterColors.TextSecondary)
        ChoiceRow(ServerMode.entries, options.serverMode, { it.label }, { if (editable) vm.setServerMode(it) }, enabled = { editable })
        Text(
            "Para comparar com a inspeção da 0.1.0: Dispositivos → Conectar e inspecionar (o Registro da sessão mostra as mesmas linhas \"diag:\").",
            style = MaterialTheme.typography.bodySmall, color = HunterColors.TextMuted,
        )
        s.attempts.takeLast(6).reversed().forEach { a ->
            fun t(ms: Long?) = ms?.let { LinkFormat.seconds(it) } ?: "—"
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "#${a.sessionId} · ${a.outcome}" + (a.endStatus?.let { " · status $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (a.endStatus == 147 || a.connectedAfterMs == null && a.endedAfterMs != null) HunterColors.Amber else HunterColors.TextPrimary,
                )
                Text(
                    "anúncio ${if (a.freshAdvertisementRequired) "exigido" else "não exigido"} · servidor ${a.serverMode.name.lowercase()} · " +
                        "última busca ${t(a.lastAdvertisementAgeMs)} · anúncio ${t(a.advertisementSeenAfterMs)} · conectou ${t(a.connectedAfterMs)} · " +
                        "descoberta ${t(a.discoveredAfterMs)} · fim ${t(a.endedAfterMs)}",
                    style = MonoDigits.copy(fontSize = 10.sp), color = HunterColors.TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun HistoryCard(s: LinkSnapshot, vm: LabViewModel) {
    val context = LocalContext.current
    CollapsibleCard(title = "Histórico técnico", summary = "${s.log.size} evento(s) · só em memória · PIN nunca registrado") {
        LabeledSwitch(
            "Captura detalhada de bytes recebidos",
            "Mostra os bytes que o Tripper enviar. Podem conter o PIN e entram no relatório copiado. Mantenha desligada em T07–T09.",
            s.diagnostics.inboundCaptureDetailed,
            vm::setInboundCapture,
        )
        OutlinedButton(onClick = { copy(context, vm.report(), "Relatório do laboratório") }) {
            Icon(HunterIcons.Copy, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Copiar relatório")
        }
        s.log.takeLast(40).forEach { e ->
            val color = when (e.level) {
                LogLevel.ERROR -> HunterColors.Error
                LogLevel.WARN -> HunterColors.Amber
                LogLevel.TX -> HunterColors.AmberSoft
                LogLevel.RX -> HunterColors.Info
                LogLevel.INFO -> HunterColors.TextSecondary
            }
            Text(
                "${e.sessionElapsedMs?.let { "+" + LinkFormat.seconds(it) } ?: "—"} ${e.level.tag} ${e.text}",
                style = MonoDigits.copy(fontSize = 11.sp), color = color,
            )
        }
    }
}

@Composable
private fun InterruptBar(s: LinkSnapshot, onInterrupt: () -> Unit, onDisconnectNow: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(HunterColors.Surface)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        if (s.state == LinkState.STOPPING || s.state == LinkState.ERROR) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (s.state == LinkState.STOPPING) "Interrompendo: aguardando o Tripper encerrar…" else "Erro: transmissão parada.",
                    style = MaterialTheme.typography.bodySmall, color = HunterColors.TextSecondary, modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDisconnectNow, enabled = s.state == LinkState.STOPPING || s.state == LinkState.ERROR) { Text("Desconectar agora") }
            }
            if (s.state == LinkState.ERROR) {
                Button(onClick = onInterrupt, colors = ButtonDefaults.buttonColors(containerColor = HunterColors.Error), modifier = Modifier.fillMaxWidth()) {
                    Text("Interromper")
                }
            }
        } else {
            Button(
                onClick = onInterrupt,
                colors = ButtonDefaults.buttonColors(containerColor = HunterColors.Error, contentColor = HunterColors.Background),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(HunterIcons.Stop, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Interromper")
            }
        }
    }
}

@Composable
private fun AuthorizeDialog(onConfirm: (TransmissionAck) -> Unit, onDismiss: () -> Unit) {
    val items = listOf(
        "A moto está parada, em local seguro.",
        "O app oficial Royal Enfield está fechado.",
        "Li docs/PRIMEIRO_HANDSHAKE.md.",
        "Entendo que o pareamento pode afetar o pareamento do app oficial.",
        "Sei onde está o botão Interromper.",
    )
    val checked = remember { mutableStateOf(List(items.size) { false }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(HunterIcons.Warning, null, tint = HunterColors.Amber) },
        title = { Text("Autorizar transmissão real?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Vale só para uma sessão. Cada etapa (handshake, PIN, relógio, bússola) exige um toque seu. " +
                        "Depois do handshake o app envia PING_FW sozinho quando passa ~2 s sem escrita (keepalive); a retomada " +
                        "envia SET_TIME e dois PING_FW em seguida; a bússola é reenviada a cada ~2 s. Só comandos documentados.",
                    style = MaterialTheme.typography.bodySmall, color = HunterColors.TextSecondary,
                )
                items.forEachIndexed { i, label ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable {
                            checked.value = checked.value.toMutableList().also { it[i] = !it[i] }
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = checked.value[i],
                            onCheckedChange = { v -> checked.value = checked.value.toMutableList().also { it[i] = v } },
                            colors = CheckboxDefaults.colors(checkedColor = HunterColors.Amber, checkmarkColor = HunterColors.Background),
                        )
                        Text(label, style = MaterialTheme.typography.bodySmall, color = HunterColors.TextPrimary)
                    }
                }
            }
        },
        confirmButton = {
            val c = checked.value
            Button(onClick = { onConfirm(TransmissionAck(c[0], c[1], c[2], c[3], c[4])) }, enabled = c.all { it }) { Text("Autorizar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        containerColor = HunterColors.SurfaceHigh,
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    lines: List<String>,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    requireCheck: String? = null,
) {
    var accepted by remember { mutableStateOf(requireCheck == null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(HunterIcons.Warning, null, tint = HunterColors.Amber) },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                lines.forEach { BulletText(it, HunterColors.TextPrimary) }
                requireCheck?.let { label ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { accepted = !accepted },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = accepted, onCheckedChange = { accepted = it },
                            colors = CheckboxDefaults.colors(checkedColor = HunterColors.Amber, checkmarkColor = HunterColors.Background),
                        )
                        Text(label, style = MaterialTheme.typography.bodySmall, color = HunterColors.TextPrimary)
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onConfirm, enabled = accepted) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        containerColor = HunterColors.SurfaceHigh,
    )
}

/** Faixa global: aparece em qualquer aba enquanto houver sessão do laboratório. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LabActiveStrip(s: LinkSnapshot, onInterrupt: () -> Unit) {
    if (!s.active || s.purpose != dev.hunterlab.core.link.SessionPurpose.LAB) return
    FlowRow(
        Modifier
            .fillMaxWidth()
            .background(HunterColors.AmberContainer)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.Center,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Laboratório BLE ativo · ${s.state.label}",
            style = MaterialTheme.typography.labelMedium, color = HunterColors.AmberSoft,
            modifier = Modifier.align(Alignment.CenterVertically),
        )
        if (s.state != LinkState.STOPPING) {
            TextButton(onClick = onInterrupt, colors = ButtonDefaults.textButtonColors(contentColor = HunterColors.Error)) { Text("Interromper") }
        }
    }
}
