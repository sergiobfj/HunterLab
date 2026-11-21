package dev.hunterlab.app.ui.settings

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.hunterlab.app.ui.components.*
import dev.hunterlab.app.ui.contract.NavActions
import dev.hunterlab.app.ui.contract.SettingsUi
import dev.hunterlab.app.ui.theme.HunterColors
import dev.hunterlab.app.ui.theme.HunterIcons

enum class AdvancedTool(val title: String) {
    DEVICES("Laboratório BLE e diagnóstico"),
    NAV_SIM("Simulador de navegação"),
    RADAR_SIM("Simulador de radares"),
    PLAYGROUND("Playground"),
}

@Composable
fun SettingsScreen(
    state: SettingsUi,
    actions: NavActions,
    padding: PaddingValues = PaddingValues(),
    engineAvailable: Boolean = true,
    permissions: PermissionRequests? = null,
    onAdvanced: (AdvancedTool) -> Unit = {},
) {
    ProductPage(padding = padding) {
        PageHeading("TUDO NO LUGAR", "Configurações")
        if (engineAvailable) {
            ApiKeySettings(state, actions)
            ProductPanel {
                LabeledSwitch("Alertas de radar", "Avisos durante o trajeto.", state.radarEnabled, actions::setRadarEnabled)
                if (state.radarEnabled) {
                    HorizontalDivider(color = HunterColors.Outline)
                    ThresholdRow("Área de busca", state.radarSearchM,
                        decrease = { actions.setRadarThresholds(maxOf(state.radarAlertM, 100, state.radarSearchM - 100), state.radarAlertM) },
                        increase = { actions.setRadarThresholds(state.radarSearchM.coerceAtMost(Int.MAX_VALUE - 100) + 100, state.radarAlertM) },
                        canDecrease = state.radarSearchM > maxOf(100, state.radarAlertM))
                    ThresholdRow("Avisar a partir de", state.radarAlertM,
                        decrease = { actions.setRadarThresholds(state.radarSearchM, maxOf(100, state.radarAlertM - 100)) },
                        increase = { actions.setRadarThresholds(state.radarSearchM, minOf(state.radarSearchM, state.radarAlertM.coerceAtMost(Int.MAX_VALUE - 100) + 100)) },
                        canDecrease = state.radarAlertM > 100, canIncrease = state.radarAlertM < state.radarSearchM)
                }
                Text("Cobertura limitada. A ausência de alerta não significa ausência de fiscalização.", style = MaterialTheme.typography.bodySmall, color = HunterColors.TextSecondary)
            }
        }
        ProductPanel {
            SectionLabel("PERMISSÕES")
            PermissionRow("Bluetooth", "Dispositivos por perto", state.permissions.bluetooth,
                available = permissions != null, onRequest = { permissions?.bluetooth?.invoke() },
                denied = permissions?.denied?.any { it == Manifest.permission.BLUETOOTH_SCAN || it == Manifest.permission.BLUETOOTH_CONNECT } == true,
                onOpenSettings = { permissions?.openSystemSettings?.invoke() })
            if (engineAvailable) {
                PermissionRow("Localização precisa", "Para acompanhar sua posição no trajeto", state.permissions.location,
                    available = permissions?.locationAvailable == true, onRequest = { permissions?.location?.invoke() },
                    denied = permissions?.denied?.contains(Manifest.permission.ACCESS_FINE_LOCATION) == true,
                    onOpenSettings = { permissions?.openSystemSettings?.invoke() })
                if (Build.VERSION.SDK_INT >= 33) PermissionRow("Notificações", "Para acompanhar a navegação fora do app", state.permissions.notifications,
                    available = permissions?.notificationsAvailable == true, onRequest = { permissions?.notifications?.invoke() },
                    denied = permissions?.denied?.contains(Manifest.permission.POST_NOTIFICATIONS) == true,
                    onOpenSettings = { permissions?.openSystemSettings?.invoke() })
            }
        }
        Column {
            SectionLabel("AVANÇADO")
            AdvancedTool.entries.forEach { tool ->
                val icon = when (tool) {
                    AdvancedTool.DEVICES -> HunterIcons.Bluetooth
                    AdvancedTool.NAV_SIM -> HunterIcons.Navigation
                    AdvancedTool.RADAR_SIM -> HunterIcons.Radar
                    AdvancedTool.PLAYGROUND -> HunterIcons.Playground
                }
                ActionRow(tool.title, icon = icon, onClick = { onAdvanced(tool) })
                HorizontalDivider(color = HunterColors.Outline)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Hunter Lab ${state.version}", style = MaterialTheme.typography.titleSmall)
            Text("Um projeto independente para a Hunter 350. Sem afiliação com a Royal Enfield.", style = MaterialTheme.typography.bodySmall, color = HunterColors.TextSecondary)
            Text("Use com a moto parada. Não opere o celular pilotando.", style = MaterialTheme.typography.bodySmall, color = HunterColors.TextSecondary)
            if (state.routingAttribution.isNotBlank()) Text(state.routingAttribution, style = MaterialTheme.typography.bodySmall, color = HunterColors.TextMuted)
            if (engineAvailable) Text("Dados de radar: © colaboradores do OpenStreetMap (ODbL).", style = MaterialTheme.typography.bodySmall, color = HunterColors.TextMuted)
        }
    }
}

@Composable
private fun ApiKeySettings(state: SettingsUi, actions: NavActions) {
    val draft = remember { TextFieldState() }
    ProductPanel {
        SectionLabel("ROTAS ONLINE")
        Text(if (state.apiKeyConfigured) "Provedor configurado" else "Configure seu provedor", style = MaterialTheme.typography.titleMedium)
        state.apiKeyMasked?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = HunterColors.TextSecondary) }
        OutlinedSecureTextField(
            state = draft, modifier = Modifier.fillMaxWidth(), label = { Text("Chave da API de rotas") },
            textObfuscationMode = TextObfuscationMode.Hidden,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        )
        PrimaryAction(if (state.apiKeyConfigured) "Substituir chave" else "Salvar chave", {
            val entered = draft.text.toString().trim()
            draft.edit { replace(0, length, "") }
            actions.setApiKey(entered)
        }, enabled = draft.text.isNotBlank(), icon = HunterIcons.Lock)
        if (state.apiKeyConfigured) TextButton({ draft.edit { replace(0, length, "") }; actions.clearApiKey() }, Modifier.heightIn(min = 48.dp)) { Text("Remover chave") }
    }
}

@Composable
private fun PermissionRow(title: String, description: String, granted: Boolean, available: Boolean, onRequest: () -> Unit, denied: Boolean, onOpenSettings: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(description, style = MaterialTheme.typography.bodySmall, color = HunterColors.TextSecondary)
        if (granted) StatusLine("Permitido" to HunterColors.Confirmed)
        else if (denied) InlineNotice("Permissão não concedida.", action = "Abrir ajustes do Android", onAction = onOpenSettings)
        else if (available) OutlinedButton(onRequest, Modifier.heightIn(min = 48.dp)) { Text("Permitir $title") }
        else Text("Indisponível nesta versão", style = MaterialTheme.typography.bodySmall, color = HunterColors.TextSecondary)
    }
}

@Composable
private fun ThresholdRow(label: String, value: Int, decrease: () -> Unit, increase: () -> Unit, canDecrease: Boolean, canIncrease: Boolean = true) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(decrease, Modifier.size(48.dp).semantics { contentDescription = "Diminuir $label" }, enabled = canDecrease) { Text("−", style = MaterialTheme.typography.headlineSmall) }
            Text("$value m", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            IconButton(increase, Modifier.size(48.dp), enabled = canIncrease) { Icon(HunterIcons.Plus, contentDescription = "Aumentar $label") }
        }
    }
}
