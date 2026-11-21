package dev.hunterlab.app.ui.devices

import android.bluetooth.BluetoothAdapter
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hunterlab.app.ble.BlePermissions
import dev.hunterlab.app.ui.components.BulletText
import dev.hunterlab.app.ui.components.CollapsibleCard
import dev.hunterlab.app.ui.lab.LabScreen
import dev.hunterlab.app.ui.lab.LabViewModel
import dev.hunterlab.app.ui.components.HunterCard
import dev.hunterlab.app.ui.components.KeyValue
import dev.hunterlab.app.ui.components.LabeledSwitch
import dev.hunterlab.app.ui.components.MessageCard
import dev.hunterlab.app.ui.components.Pill
import dev.hunterlab.app.ui.components.SectionLabel
import dev.hunterlab.app.ui.components.copy
import dev.hunterlab.app.ui.theme.HunterColors
import dev.hunterlab.app.ui.theme.HunterIcons
import dev.hunterlab.app.ui.theme.MonoDigits
import dev.hunterlab.core.ble.BleErrorTranslator
import dev.hunterlab.core.ble.CandidateLevel
import dev.hunterlab.core.ble.CharacteristicProperty
import dev.hunterlab.core.ble.CheckOutcome
import dev.hunterlab.core.ble.ConnectionPhase
import dev.hunterlab.core.ble.GattCharacteristicInfo
import dev.hunterlab.core.ble.GattReportFormatter
import dev.hunterlab.core.ble.GattServiceInfo
import dev.hunterlab.core.ble.KnownUuids
import dev.hunterlab.core.ble.ProfileVerdict
import dev.hunterlab.core.ble.ReadinessIssue
import dev.hunterlab.core.ble.UuidCategory

@Composable
fun DevicesScreen(vm: DevicesViewModel, labVm: LabViewModel, contentPadding: PaddingValues) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val all = result.values.all { it }
        val permanently = !all && activity != null &&
            result.filterValues { !it }.keys.any { !ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
        vm.onPermissionsResult(all, permanently)
    }
    val enableBluetooth = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { vm.refresh() }

    val actions = ReadinessActions(
        requestPermissions = { permissionLauncher.launch(BlePermissions.required) },
        enableBluetooth = {
            try {
                enableBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            } catch (_: ActivityNotFoundException) {
                context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            } catch (_: SecurityException) {
                permissionLauncher.launch(BlePermissions.required)
            }
        },
        openAppSettings = {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
            )
        },
    )

    val selected = ui.selected
    if (selected != null && ui.labOpen) {
        LabScreen(labVm, selected.device, contentPadding, onClose = vm::closeLab)
    } else if (selected != null) {
        BackHandler { vm.closeDetail() }
        DeviceDetail(ui, selected, vm, contentPadding)
    } else {
        DeviceList(ui, vm, contentPadding, actions)
    }
}

private class ReadinessActions(
    val requestPermissions: () -> Unit,
    val enableBluetooth: () -> Unit,
    val openAppSettings: () -> Unit,
)

@Composable
private fun DeviceList(ui: DevicesUiState, vm: DevicesViewModel, contentPadding: PaddingValues, actions: ReadinessActions) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 24.dp + contentPadding.calculateBottomPadding()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(top = contentPadding.calculateTopPadding()),
    ) {
        val issue = if (ui.permanentlyDenied) ReadinessIssue.PERMISSIONS_PERMANENTLY_DENIED else ui.readiness.issue
        if (issue != null) {
            item {
                MessageCard(BleErrorTranslator.readiness(issue)) {
                    when (issue) {
                        ReadinessIssue.PERMISSIONS_MISSING -> Button(onClick = actions.requestPermissions) { Text("Conceder permissões") }
                        ReadinessIssue.PERMISSIONS_PERMANENTLY_DENIED -> Button(onClick = actions.openAppSettings) { Text("Abrir configurações do app") }
                        ReadinessIssue.BLUETOOTH_OFF -> Button(onClick = actions.enableBluetooth) { Text("Ativar Bluetooth") }
                        else -> Unit
                    }
                }
            }
        }
        item { ScanCard(ui, vm) }
        ui.message?.let { msg ->
            item { MessageCard(msg) { TextButton(onClick = vm::dismissMessage) { Text("Fechar") } } }
        }
        ui.scan.error?.takeIf { it != ui.message }?.let { item { MessageCard(it) } }
        if (ui.rows.isEmpty()) {
            item {
                Text(
                    if (ui.totalSeen == 0) "Nenhum dispositivo ainda. Ligue a ignição da moto e toque em Buscar em seguida."
                    else "Nenhum dispositivo corresponde aos filtros (${ui.totalSeen} vistos).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = HunterColors.TextSecondary,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
        items(ui.rows, key = { it.device.address }) { row -> DeviceRowItem(row) { vm.select(row.device) } }
    }
}

@Composable
private fun ScanCard(ui: DevicesUiState, vm: DevicesViewModel) {
    HunterCard(
        title = "Busca BLE",
        subtitle = "Somente leitura. O Tripper anuncia por ~30 s após ligar a ignição (observado na Meteor 350).",
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (ui.scan.scanning) {
                OutlinedButton(onClick = vm::stopScan) {
                    Icon(HunterIcons.Stop, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Parar")
                }
            } else {
                Button(onClick = vm::startScan, enabled = ui.readiness.ready) {
                    Icon(HunterIcons.Search, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Buscar dispositivos")
                }
            }
            Text(
                "${ui.rows.size} exibido(s) · ${ui.totalSeen} visto(s)",
                style = MaterialTheme.typography.bodySmall,
                color = HunterColors.TextSecondary,
                modifier = Modifier.weight(1f),
            )
            if (!ui.scan.scanning && ui.totalSeen > 0) {
                IconButton(onClick = vm::clearDevices) { Icon(HunterIcons.Trash, "Limpar lista", tint = HunterColors.TextSecondary) }
            }
        }
        if (ui.scan.scanning) {
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = HunterColors.Amber, trackColor = HunterColors.SurfaceHighest)
            Text("Buscando por até ${ui.scan.durationMs / 1000} s…", style = MaterialTheme.typography.bodySmall, color = HunterColors.TextSecondary)
        }
        LabeledSwitch(
            "Somente candidatos a Tripper",
            "Nome RE_DISP, serviço documentado ou OUI da NXP",
            ui.filters.onlyCandidates,
            { vm.setFilters(ui.filters.copy(onlyCandidates = it)) },
        )
        LabeledSwitch(
            "Mostrar dispositivos sem nome",
            null,
            ui.filters.showUnnamed,
            { vm.setFilters(ui.filters.copy(showUnnamed = it)) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeviceRowItem(row: DeviceRow, onClick: () -> Unit) {
    val strong = row.match.level == CandidateLevel.STRONG
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = HunterColors.Surface,
        border = BorderStroke(1.dp, if (strong) HunterColors.Amber.copy(alpha = 0.7f) else HunterColors.Outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            SignalBars(row.device.rssi)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    row.device.name ?: "Sem nome",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (row.device.name == null) HunterColors.TextSecondary else HunterColors.TextPrimary,
                )
                Text("${row.device.address} · ${row.device.rssi} dBm", style = MonoDigits.copy(fontSize = 11.sp), color = HunterColors.TextMuted)
                if (row.match.level != CandidateLevel.NONE) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                        Pill(row.match.level.label, if (strong) HunterColors.Amber else HunterColors.Info)
                    }
                }
            }
            Icon(HunterIcons.ChevronRight, null, tint = HunterColors.TextMuted)
        }
    }
}

@Composable
private fun SignalBars(rssi: Int) {
    val level = when {
        rssi >= -55 -> 4
        rssi >= -67 -> 3
        rssi >= -78 -> 2
        rssi >= -90 -> 1
        else -> 0
    }
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.height(18.dp)) {
        for (i in 1..4) {
            Box(
                Modifier
                    .width(4.dp)
                    .height((4 + i * 3.5).dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(if (i <= level) HunterColors.Amber else HunterColors.SurfaceHighest),
            )
        }
    }
}

// ---------------------------------------------------------------- Detalhe

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeviceDetail(ui: DevicesUiState, row: DeviceRow, vm: DevicesViewModel, contentPadding: PaddingValues) {
    val context = LocalContext.current
    var showConnectWarning by rememberSaveable { mutableStateOf(false) }
    var showDisconnectWarning by rememberSaveable { mutableStateOf(false) }
    val inspector = ui.inspector
    val device = row.device

    LazyColumn(
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp + contentPadding.calculateBottomPadding()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(top = contentPadding.calculateTopPadding()),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = vm::closeDetail) { Icon(HunterIcons.Back, "Voltar", tint = HunterColors.TextPrimary) }
                Column(Modifier.weight(1f)) {
                    Text(device.name ?: "Sem nome", style = MaterialTheme.typography.titleLarge, color = HunterColors.TextPrimary)
                    Text(device.address, style = MonoDigits, color = HunterColors.TextSecondary)
                }
            }
        }
        if (row.match.level != CandidateLevel.NONE) {
            item {
                HunterCard(title = row.match.level.label, subtitle = "Indícios — não é confirmação de compatibilidade com a Hunter 350") {
                    row.match.reasons.forEach { BulletText(it, HunterColors.TextPrimary) }
                }
            }
        }
        if (row.match.level == CandidateLevel.STRONG) {
            item {
                HunterCard(
                    title = "Laboratório BLE · experimental",
                    subtitle = "Handshake e teste de bússola. Nada é transmitido sem autorização explícita e um toque seu.",
                ) {
                    OutlinedButton(onClick = vm::openLab, enabled = !inspector.connected) {
                        Icon(HunterIcons.Lock, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Abrir laboratório")
                    }
                    if (inspector.connected) {
                        Text("Aguarde a inspeção terminar.", style = MaterialTheme.typography.bodySmall, color = HunterColors.TextMuted)
                    }
                }
            }
        }
        item {
            CollapsibleCard(title = "Anúncio", summary = "RSSI ${device.rssi} dBm · ${device.serviceUuids.size} serviço(s) anunciado(s)") {
                KeyValue("RSSI", "${device.rssi} dBm")
                KeyValue("Conectável", when (device.connectable) { true -> "sim"; false -> "não"; null -> "?" })
                KeyValue("TX power", device.txPower?.let { "$it dBm" } ?: "não informado")
                if (device.serviceUuids.isEmpty()) KeyValue("Serviços anunciados", "nenhum")
                device.serviceUuids.forEach { u ->
                    Column {
                        Text(KnownUuids.display(u), style = MonoDigits.copy(fontSize = 11.sp), color = HunterColors.TextPrimary)
                        KnownUuids.lookup(u)?.let { Text(it.name, style = MaterialTheme.typography.bodySmall, color = HunterColors.TextSecondary) }
                    }
                }
                device.manufacturerData.forEach { (id, hex) ->
                    KeyValue("Fabricante 0x${id.toString(16).uppercase().padStart(4, '0')}", hex)
                }
            }
        }
        item {
            ConnectionCard(
                ui = ui,
                onConnect = { showConnectWarning = true },
                onDisconnect = { showDisconnectWarning = true },
                onReset = vm::resetInspection,
            )
        }
        ui.message?.let { msg -> item { MessageCard(msg) { TextButton(onClick = vm::dismissMessage) { Text("Fechar") } } } }
        ui.profile?.let { report ->
            item {
                val color = when (report.verdict) {
                    ProfileVerdict.MATCHES -> HunterColors.Confirmed
                    ProfileVerdict.PARTIAL -> HunterColors.Amber
                    ProfileVerdict.NOT_FOUND -> HunterColors.Unverified
                }
                CollapsibleCard(title = "Comparação com o perfil documentado", summary = report.verdict.label) {
                    Pill(report.verdict.label, color)
                    report.items.forEach { item ->
                        val (icon, c) = when (item.outcome) {
                            CheckOutcome.PASS -> HunterIcons.Check to HunterColors.Confirmed
                            CheckOutcome.FAIL -> HunterIcons.Close to HunterColors.Unverified
                            CheckOutcome.WARN -> HunterIcons.Warning to HunterColors.Amber
                            CheckOutcome.INFO -> HunterIcons.Info to HunterColors.Info
                        }
                        Row {
                            Icon(icon, null, tint = c, modifier = Modifier.size(16.dp).padding(top = 2.dp))
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(item.label, style = MaterialTheme.typography.bodyMedium, color = HunterColors.TextPrimary)
                                Text(item.detail, style = MaterialTheme.typography.bodySmall, color = HunterColors.TextSecondary)
                            }
                        }
                    }
                }
            }
        }
        inspector.snapshot?.let { snap ->
            item {
                CollapsibleCard(
                    title = "Detalhes GATT",
                    summary = "${snap.services.size} serviço(s) · ${snap.services.sumOf { it.characteristics.size }} característica(s)",
                ) {
                    snap.services.forEach { service ->
                        ServiceCard(service, ui, onRead = { c -> vm.read(service.uuid, c.uuid) })
                    }
                }
            }
            item {
                HunterCard(title = "Relatório", subtitle = "Texto para documentar o teste físico. Contém o endereço do dispositivo; você decide onde compartilhar.") {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { vm.report()?.let { copy(context, it, "Relatório GATT") } }) {
                            Icon(HunterIcons.Copy, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Copiar")
                        }
                        OutlinedButton(onClick = {
                            vm.report()?.let { text ->
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "Hunter Lab — inspeção GATT")
                                    putExtra(Intent.EXTRA_TEXT, text)
                                }
                                context.startActivity(Intent.createChooser(send, "Compartilhar relatório"))
                            }
                        }) {
                            Icon(HunterIcons.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Compartilhar")
                        }
                    }
                }
            }
        }
        if (inspector.log.isNotEmpty()) {
            item {
                CollapsibleCard(title = "Registro da sessão", summary = "${inspector.log.size} linha(s)") {
                    inspector.log.takeLast(30).forEach {
                        Text(it, style = MonoDigits.copy(fontSize = 11.sp), color = HunterColors.TextSecondary)
                    }
                }
            }
        }
    }

    if (showConnectWarning) {
        ConnectWarningDialog(
            deviceName = device.name ?: device.address,
            onConfirm = { showConnectWarning = false; vm.connectSelected() },
            onDismiss = { showConnectWarning = false },
        )
    }
    if (showDisconnectWarning) {
        AlertDialog(
            onDismissRequest = { showDisconnectWarning = false },
            icon = { Icon(HunterIcons.Warning, null, tint = HunterColors.Amber) },
            title = { Text("Desconectar pelo celular?") },
            text = {
                Text(
                    "Na Meteor 350, depois de uma desconexão iniciada pelo celular o Tripper só voltou a anunciar após desligar e ligar a ignição. " +
                        "O Tripper costuma encerrar o link sozinho em ~5 s; o app também libera a conexão automaticamente em ~20 s.",
                )
            },
            confirmButton = { TextButton(onClick = { showDisconnectWarning = false; vm.disconnect() }) { Text("Desconectar") } },
            dismissButton = { TextButton(onClick = { showDisconnectWarning = false }) { Text("Cancelar") } },
            containerColor = HunterColors.SurfaceHigh,
        )
    }
}

@Composable
private fun ConnectionCard(ui: DevicesUiState, onConnect: () -> Unit, onDisconnect: () -> Unit, onReset: () -> Unit) {
    val s = ui.inspector
    val phaseColor = when (s.phase) {
        ConnectionPhase.READY -> HunterColors.Confirmed
        ConnectionPhase.FAILED -> HunterColors.Error
        ConnectionPhase.CONNECTING, ConnectionPhase.CONNECTED, ConnectionPhase.DISCOVERING, ConnectionPhase.DISCONNECTING -> HunterColors.Amber
        else -> HunterColors.TextSecondary
    }
    HunterCard(title = "Inspeção GATT", subtitle = "Somente leitura: nenhum comando é enviado ao dispositivo.") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(s.phase.label, phaseColor)
            if (s.snapshot != null && !s.connected) Pill("Inspeção guardada", HunterColors.Info)
        }
        PhaseSteps(s.phase, s.snapshot != null)
        s.message?.let { MessageCard(it) }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                s.connected -> OutlinedButton(onClick = onDisconnect) {
                    Icon(HunterIcons.Unlink, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Desconectar")
                }
                s.phase == ConnectionPhase.CONNECTING || s.phase == ConnectionPhase.DISCONNECTING -> Unit
                else -> Button(onClick = onConnect, enabled = ui.readiness.ready) {
                    Icon(HunterIcons.Link, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                    Text(if (s.snapshot == null) "Conectar e inspecionar" else "Inspecionar de novo")
                }
            }
            if (!s.connected && s.phase != ConnectionPhase.IDLE && s.phase != ConnectionPhase.CONNECTING) {
                TextButton(onClick = onReset) { Text("Limpar") }
            }
        }
        if (!ui.readiness.ready) {
            Text("Resolva as permissões/Bluetooth na lista de dispositivos.", style = MaterialTheme.typography.bodySmall, color = HunterColors.Amber)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PhaseSteps(phase: ConnectionPhase, hasSnapshot: Boolean) {
    val steps = listOf("Conectar", "Descobrir", "Inspecionar")
    val reached = when (phase) {
        ConnectionPhase.CONNECTING -> 0
        ConnectionPhase.CONNECTED, ConnectionPhase.DISCOVERING -> 1
        ConnectionPhase.READY -> 3
        else -> if (hasSnapshot) 3 else -1
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        steps.forEachIndexed { i, label ->
            val done = i < reached
            val active = i == reached
            val color = when {
                done -> HunterColors.Confirmed
                active -> HunterColors.Amber
                else -> HunterColors.TextMuted
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(22.dp).clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (done) Icon(HunterIcons.Check, null, tint = color, modifier = Modifier.size(14.dp))
                    else Text("${i + 1}", style = MaterialTheme.typography.labelSmall, color = color)
                }
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, color = color)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ServiceCard(service: GattServiceInfo, ui: DevicesUiState, onRead: (GattCharacteristicInfo) -> Unit) {
    var expanded by rememberSaveable(service.uuid.toString()) { mutableStateOf(false) }
    val info = KnownUuids.lookup(service.uuid)
    val sensitive = info?.category == UuidCategory.SENSITIVE
    val documented = info?.category == UuidCategory.TRIPPER_DOCUMENTED
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = HunterColors.Surface,
        border = BorderStroke(1.dp, if (documented) HunterColors.Amber.copy(alpha = 0.6f) else if (sensitive) HunterColors.Error.copy(alpha = 0.6f) else HunterColors.Outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(info?.name ?: "Serviço desconhecido", style = MaterialTheme.typography.titleSmall, color = HunterColors.TextPrimary)
                    Text(KnownUuids.display(service.uuid), style = MonoDigits.copy(fontSize = 11.sp), color = HunterColors.TextSecondary)
                    info?.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = if (sensitive) HunterColors.Error else HunterColors.Amber) }
                }
                if (sensitive) Icon(HunterIcons.Lock, "Bloqueado", tint = HunterColors.Error, modifier = Modifier.padding(end = 8.dp).size(18.dp))
                Text("${service.characteristics.size}", style = MaterialTheme.typography.labelMedium, color = HunterColors.TextMuted)
                Icon(if (expanded) HunterIcons.ChevronDown else HunterIcons.ChevronRight, null, tint = HunterColors.TextMuted)
            }
            if (expanded) {
                service.characteristics.forEach { c ->
                    Box(Modifier.fillMaxWidth().height(1.dp).background(HunterColors.Outline))
                    CharacteristicRow(service, c, ui, sensitive, onRead)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CharacteristicRow(
    service: GattServiceInfo,
    c: GattCharacteristicInfo,
    ui: DevicesUiState,
    sensitive: Boolean,
    onRead: (GattCharacteristicInfo) -> Unit,
) {
    val info = KnownUuids.lookup(c.uuid)
    val key = GattReportFormatter.key(service, c)
    val read = ui.inspector.reads[key]
    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(info?.name ?: "Característica", style = MaterialTheme.typography.bodyMedium, color = HunterColors.TextPrimary)
        Text(KnownUuids.display(c.uuid), style = MonoDigits.copy(fontSize = 11.sp), color = HunterColors.TextSecondary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            c.properties.forEach { p ->
                val color = when {
                    p.isWrite -> HunterColors.Amber
                    p == CharacteristicProperty.READ -> HunterColors.Info
                    p == CharacteristicProperty.NOTIFY || p == CharacteristicProperty.INDICATE -> Color(0xFFB5A3D6)
                    else -> HunterColors.TextSecondary
                }
                Pill(p.label, color, icon = if (p.isWrite) HunterIcons.Lock else null)
            }
            if (c.properties.isEmpty()) Pill("sem propriedades", HunterColors.TextMuted)
        }
        if (c.properties.any { it.isWrite }) {
            Text("Escrita bloqueada nesta versão.", style = MaterialTheme.typography.bodySmall, color = HunterColors.TextMuted)
        }
        if (c.descriptors.isNotEmpty()) {
            Text("Descritores: " + c.descriptors.joinToString { KnownUuids.display(it.uuid) }, style = MaterialTheme.typography.bodySmall, color = HunterColors.TextMuted)
        }
        info?.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = HunterColors.Amber) }
        if (c.readable && !sensitive) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { onRead(c) },
                    enabled = ui.inspector.connected && ui.inspector.pendingRead == null,
                    colors = ButtonDefaults.textButtonColors(contentColor = HunterColors.Info),
                ) { Text(if (ui.inspector.pendingRead == key) "Lendo…" else "Ler valor") }
                if (!ui.inspector.connected) Text("conecte para ler", style = MaterialTheme.typography.bodySmall, color = HunterColors.TextMuted)
            }
        }
        read?.let { r ->
            r.text?.let { Text("\"$it\"", style = MaterialTheme.typography.bodyMedium, color = HunterColors.TextPrimary, fontWeight = FontWeight.Medium) }
            r.hex?.let { Text(it, style = MonoDigits.copy(fontSize = 11.sp), color = HunterColors.TextSecondary) }
            r.error?.let { Text("${it.title}: ${it.detail}", style = MaterialTheme.typography.bodySmall, color = HunterColors.Amber) }
        }
    }
}

@Composable
private fun ConnectWarningDialog(deviceName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    var accepted by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(HunterIcons.Warning, null, tint = HunterColors.Amber) },
        title = { Text("Conectar a $deviceName?") },
        text = {
            Column(Modifier.widthIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BulletText("Pode interferir no aplicativo oficial Royal Enfield: enquanto o Hunter Lab estiver conectado, o app oficial provavelmente não conseguirá se conectar ao Tripper.", HunterColors.TextPrimary)
                BulletText("Esta versão NÃO envia nenhum comando de escrita. Ela só lista serviços e características (e lê valores se você pedir).")
                BulletText("Na Meteor 350, o Tripper encerra a conexão ~5 s depois quando não recebe pacotes. Isso é esperado.")
                BulletText("Se a desconexão partir do celular, o Tripper pode parar de anunciar até a ignição ser desligada e ligada de novo.")
                BulletText("Nada disso foi verificado na Hunter 350 2027. Faça o teste com a moto parada.")
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { accepted = !accepted }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = accepted,
                        onCheckedChange = { accepted = it },
                        colors = CheckboxDefaults.colors(checkedColor = HunterColors.Amber, checkmarkColor = HunterColors.Background),
                    )
                    Text("Entendi os riscos e quero conectar", style = MaterialTheme.typography.bodyMedium, color = HunterColors.TextPrimary)
                }
            }
        },
        confirmButton = { Button(onClick = onConfirm, enabled = accepted) { Text("Conectar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        containerColor = HunterColors.SurfaceHigh,
    )
}
