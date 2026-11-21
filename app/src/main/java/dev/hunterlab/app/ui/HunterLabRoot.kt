package dev.hunterlab.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.hunterlab.app.BuildConfigInfo
import dev.hunterlab.app.ble.BleController
import dev.hunterlab.app.ui.components.ActiveTripperStrip
import dev.hunterlab.app.ui.contract.*
import dev.hunterlab.app.ui.devices.DevicesScreen
import dev.hunterlab.app.ui.devices.DevicesViewModel
import dev.hunterlab.app.ui.home.*
import dev.hunterlab.app.ui.lab.LabActiveStrip
import dev.hunterlab.app.ui.lab.LabViewModel
import dev.hunterlab.app.ui.navigation.NavigationScreen
import dev.hunterlab.app.ui.personalization.PersonalizationScreen
import dev.hunterlab.app.ui.playground.PlaygroundScreen
import dev.hunterlab.app.ui.playground.PlaygroundViewModel
import dev.hunterlab.app.ui.settings.*
import dev.hunterlab.app.ui.simulation.NavigationSimScreen
import dev.hunterlab.app.ui.simulation.RadarSimScreen
import dev.hunterlab.app.ui.simulation.SimulationViewModel
import dev.hunterlab.app.ui.theme.HunterColors
import dev.hunterlab.app.ui.theme.HunterIcons

enum class PrimaryArea(val label: String, val title: String, val icon: ImageVector) {
    HOME("Início", "Início", HunterIcons.Home),
    NAVIGATION("Navegar", "Navegação", HunterIcons.Navigation),
    APPEARANCE("Visual", "Personalização", HunterIcons.Appearance),
    SETTINGS("Ajustes", "Configurações", HunterIcons.Settings),
}

/** NavBindings é a única entrada do MVP. Sem ele, a base mantém seus recursos reais e simuladores. */
@Composable
fun HunterLabRoot(ble: BleController, nav: NavBindings? = null) {
    var area by rememberSaveable { mutableStateOf(PrimaryArea.HOME) }
    var advanced by rememberSaveable { mutableStateOf<AdvancedTool?>(null) }
    val devices: DevicesViewModel = viewModel { DevicesViewModel(ble) }
    val simulation: SimulationViewModel = viewModel()
    val playground: PlaygroundViewModel = viewModel()
    val lab: LabViewModel = viewModel { LabViewModel(ble) }
    val labState by lab.link.collectAsStateWithLifecycle()
    val readiness by ble.readiness.collectAsStateWithLifecycle()
    var appearance by remember { mutableStateOf(SettingsUi(version = BuildConfigInfo.VERSION)) }
    val appearanceActions = remember { LocalAppearanceActions { change -> appearance = change(appearance) } }
    val home = nav?.home?.collectAsStateWithLifecycle()?.value ?: publishedHomeState(labState)
    val journey = nav?.nav?.collectAsStateWithLifecycle()?.value ?: NavUi()
    val settings = nav?.settings?.collectAsStateWithLifecycle()?.value
        ?: appearance.copy(permissions = PermissionsUi(bluetooth = readiness.permissionsGranted))
    val actions = nav?.actions ?: appearanceActions
    val permissions = rememberPermissionRequests { ble.refresh(); nav?.actions?.refreshPermissions() }
    val openHome = { advanced = null; area = PrimaryArea.HOME }
    val openNavigation = { advanced = null; area = PrimaryArea.NAVIGATION }
    BackHandler(enabled = advanced != null || area != PrimaryArea.HOME) {
        if (advanced != null) advanced = null else area = PrimaryArea.HOME
    }

    Scaffold(
        containerColor = HunterColors.Background,
        topBar = {
            Column {
                HunterBrandBar(advanced?.title, onBack = { advanced = null })
                LabActiveStrip(labState, onInterrupt = lab::interrupt)
                if (nav != null && journey.tripper.canInterrupt) {
                    ActiveTripperStrip(journey.tripper.label, actions::interruptTripper)
                }
            }
        },
        bottomBar = {
            if (advanced == null) {
                HunterNavigationBar(area) { area = it }
            }
        },
    ) { padding ->
        when (advanced) {
            AdvancedTool.DEVICES -> DevicesScreen(devices, lab, padding)
            AdvancedTool.NAV_SIM -> NavigationSimScreen(simulation, padding)
            AdvancedTool.RADAR_SIM -> RadarSimScreen(simulation, padding)
            AdvancedTool.PLAYGROUND -> PlaygroundScreen(playground, padding)
            null -> when (area) {
                PrimaryArea.HOME -> HomeScreen(home, actions, padding, engineAvailable = nav != null,
                    onSettings = { area = PrimaryArea.SETTINGS }, onNavigation = openNavigation,
                    onSimulator = { advanced = AdvancedTool.NAV_SIM },
                    legacyConnect = if (nav == null) { { advanced = AdvancedTool.DEVICES } } else null)
                PrimaryArea.NAVIGATION -> NavigationScreen(journey, actions, padding, engineAvailable = nav != null, onHome = openHome)
                PrimaryArea.APPEARANCE -> PersonalizationScreen(settings, actions, padding, journey.tripperPreview, engineAvailable = nav != null)
                PrimaryArea.SETTINGS -> SettingsScreen(settings, actions, padding, engineAvailable = nav != null, permissions = permissions, onAdvanced = { advanced = it })
            }
        }
    }
}

@Composable
fun HunterBrandBar(advancedTitle: String? = null, onBack: () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = if (advancedTitle == null) 20.dp else 4.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (advancedTitle != null) {
            IconButton(onBack, Modifier.size(48.dp)) { Icon(HunterIcons.Back, "Voltar") }
            Text(advancedTitle, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        } else {
            Text("HUNTER", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 2.sp))
            Text("LAB", style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 2.sp), color = HunterColors.Amber)
            Spacer(Modifier.weight(1f))
            Text("350", style = MaterialTheme.typography.labelLarge, color = HunterColors.TextMuted)
        }
    }
}

@Composable
fun HunterNavigationBar(area: PrimaryArea, onAreaSelected: (PrimaryArea) -> Unit) {
    Column {
        HorizontalDivider(color = HunterColors.Outline)
        NavigationBar(containerColor = HunterColors.Background, tonalElevation = 0.dp) {
            PrimaryArea.entries.forEach { item ->
                NavigationBarItem(
                    selected = area == item,
                    onClick = { onAreaSelected(item) },
                    icon = { Icon(item.icon, contentDescription = item.title) },
                    label = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = HunterColors.Amber,
                        selectedTextColor = HunterColors.TextPrimary,
                        indicatorColor = HunterColors.AmberContainer,
                        unselectedIconColor = HunterColors.TextMuted,
                        unselectedTextColor = HunterColors.TextMuted,
                    ),
                )
            }
        }
    }
}
