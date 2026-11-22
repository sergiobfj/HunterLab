package dev.hunterlab.app.ui.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.hunterlab.app.ui.components.*
import dev.hunterlab.app.ui.contract.*
import dev.hunterlab.app.ui.theme.HunterIcons
import kotlin.math.roundToInt

enum class AdvancedDestination(val title: String) {
    DEVICES("Laboratório BLE e diagnóstico"),
    NAVIGATION_SIM("Simulador de navegação"),
    RADAR_SIM("Simulador de radares"),
    PLAYGROUND("Tripper Playground"),
}

@Composable
fun SettingsScreen(state: SettingsUi, actions: NavActions, onAdvanced: (AdvancedDestination) -> Unit, modifier: Modifier = Modifier) {
    // A chave em edição também não sobrevive à recriação da Activity.
    var apiKey by remember { mutableStateOf("") }
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val appContext = LocalContext.current.applicationContext
    var licensesOpen by remember { mutableStateOf(false) }
    var mapKey by remember { mutableStateOf("") }
    var keySaved by remember { mutableStateOf(false) }
    DailyScreen(modifier) {
        DailyHeading("Configurações")
        SectionTitle("Rotas")
        SupportingText("A chave fica no armazenamento privado do app, sem criptografia adicional. Evite aparelhos com root ou depuração acessível a terceiros.")
        SupportingText(if (state.apiKeyConfigured) "Chave configurada: ${state.apiKeyMasked ?: "••••••"}" else "Adicione a chave do provedor de rotas.")
        OutlinedTextField(
            value = apiKey, onValueChange = { apiKey = it; keySaved = false },
            label = { Text(if (state.apiKeyConfigured) "Nova chave da API" else "Chave da API de rotas") },
            singleLine = true, visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { actions.setApiKey(apiKey.trim()); apiKey = ""; keySaved = true },
                enabled = apiKey.isNotBlank(), modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("Salvar chave") }
            if (state.apiKeyConfigured) {
                TextButton(onClick = { actions.clearApiKey(); apiKey = ""; keySaved = false }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Remover chave")
                }
            }
        }
        if (keySaved && state.apiKeyConfigured) SupportingText("Chave salva.")
        HorizontalDivider()
        SectionTitle("Mapas e estabelecimentos")
        SupportingText(if(state.mapKeyConfigured) "Chave MapTiler configurada." else "MapLibre renderiza o mapa; MapTiler fornece cartografia e busca. Cadastre sua chave MapTiler.")
        SupportingText("Consultas e tiles usam a quota da sua conta. O plano Free é para uso pessoal/não comercial; confira os limites atuais no provedor. Não há download offline em massa.")
        OutlinedTextField(value=mapKey,onValueChange={mapKey=it},label={Text("Chave MapTiler")},singleLine=true,
            visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),modifier=Modifier.fillMaxWidth())
        Row {
            TextButton(onClick={actions.setMapKey(mapKey);mapKey=""},enabled=mapKey.isNotBlank()) {Text("Salvar MapTiler")}
            if(state.mapKeyConfigured) TextButton(onClick={actions.clearMapKey();mapKey=""}) {Text("Remover MapTiler")}
        }
        TextButton(onClick={uriHandler.openUri("https://cloud.maptiler.com/")}) {Text("Obter minha chave MapTiler")}
        TextButton(onClick={licensesOpen=true}) {Text("Licenças de mapas")}
        SupportingText("As duas chaves ficam nas preferências privadas, sem backup ou logs. MapTiler recebe a busca e, quando autorizada, a região aproximada para priorizar resultados. Favoritos ficam somente no aparelho.")
        HorizontalDivider()
        SectionTitle("Radares")
        DailySwitch("Alertas de radar", checked = state.radarEnabled, onChecked = actions::setRadarEnabled)
        if (state.radarEnabled) {
            DailySwitch("Aviso por voz", checked = state.radarVoiceEnabled, onChecked = actions::setRadarVoiceEnabled)
            SupportingText("Opcional. Usa voz pt-BR instalada, sem rede. Um aviso por radar na passagem; teste o volume antes de sair.")
            ThresholdControl("Buscar radares até", state.radarSearchM, 100, 3000) { search ->
                actions.setRadarThresholds(search, state.radarAlertM.coerceAtMost(search))
            }
            ThresholdControl("Alertar a partir de", state.radarAlertM, 100, state.radarSearchM.coerceAtLeast(100)) { alert ->
                actions.setRadarThresholds(state.radarSearchM, alert)
            }
            TextButton(onClick = { actions.setRadarThresholds(700, 300) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Usar padrão · 700 / 300 m")
            }
        }
        SupportingText("Cobertura limitada: a ausência de alerta não significa ausência de fiscalização.")
        HorizontalDivider()
        SectionTitle("Permissões")
        PermissionsPanel(state.permissions, actions)
        HorizontalDivider()
        SectionTitle("Avançado")
        SupportingText("Diagnóstico e testes com a moto parada.")
        AdvancedDestination.entries.forEach { destination ->
            ActionRow(
                destination.title, { onAdvanced(destination) },
                icon = when (destination) {
                    AdvancedDestination.DEVICES -> HunterIcons.Bluetooth
                    AdvancedDestination.NAVIGATION_SIM -> HunterIcons.Navigation
                    AdvancedDestination.RADAR_SIM -> HunterIcons.Radar
                    AdvancedDestination.PLAYGROUND -> HunterIcons.Playground
                },
            )
        }
        HorizontalDivider()
        SectionTitle("Sobre")
        Text("Hunter Lab · ${state.version.ifBlank { "Versão não informada" }}", style = MaterialTheme.typography.bodyMedium)
        SupportingText("Royal Enfield Hunter 350 · Tripper Pod")
        if (state.routingAttribution.isNotBlank()) SupportingText(state.routingAttribution)
        SupportingText("Radares: © colaboradores do OpenStreetMap · Pardal (ODbL). Fontes: DER-PE, DNIT, INMETRO, PCR, SEINFRA-BA. Consulte a documentação de dados.")
    }
    if (licensesOpen) AlertDialog(onDismissRequest={licensesOpen=false}, title={Text("MapLibre e dependências")},
        text={ Text(remember { appContext.assets.open("licenses/maplibre-native-android-13.7.1.txt").bufferedReader().use { it.readText() } },
            Modifier.heightIn(max=400.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()),style=MaterialTheme.typography.bodySmall) },
        confirmButton={TextButton(onClick={licensesOpen=false}) {Text("Fechar")}})

}

@Composable
private fun ThresholdControl(label: String, value: Int, minimum: Int, maximum: Int, onChange: (Int) -> Unit) {
    Column {
        Text("$label · $value m", style = MaterialTheme.typography.bodyLarge)
        if (maximum > minimum) {
            HunterSlider(
                value = (value - minimum).toFloat() / (maximum - minimum),
                onValueChange = { normalized ->
                    val stepped = (minimum + (normalized * (maximum - minimum) / 100).roundToInt() * 100).coerceIn(minimum, maximum)
                    if (stepped != value) onChange(stepped)
                },
                steps = ((maximum - minimum) / 100 - 1).coerceAtLeast(0),
                modifier = Modifier.fillMaxWidth().semantics {
                    contentDescription = label
                    stateDescription = "$value metros"
                },
            )
        } else SupportingText("A distância de alerta acompanha o limite de busca.")
    }
}

@Composable
private fun PermissionsPanel(state: PermissionsUi, actions: NavActions) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { actions.refreshPermissions() }
    val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { actions.refreshPermissions() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentActions by rememberUpdatedState(actions)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) currentActions.refreshPermissions()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    PermissionRow("Localização precisa", state.location, "Necessária para acompanhar o trajeto.") {
        launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    if (Build.VERSION.SDK_INT >= 33) {
        PermissionRow("Notificações", state.notifications, "Alertas durante a navegação.") {
            launcher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        }
    }
    PermissionRow("Dispositivos por perto", state.bluetooth, "Bluetooth para conectar ao Tripper.") {
        launcher.launch(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT))
    }
    TextButton(
        onClick = {
            settingsLauncher.launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
        }, modifier = Modifier.heightIn(min = 48.dp),
    ) { Text("Permissões nas configurações do Android") }
}

@Composable
private fun PermissionRow(title: String, granted: Boolean, detail: String, onRequest: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        SupportingText(if (granted) "Permitida" else detail)
        if (!granted) {
            OutlinedButton(onClick = onRequest, modifier = Modifier.heightIn(min = 48.dp)) { Text("Permitir $title") }
        }
    }
}
