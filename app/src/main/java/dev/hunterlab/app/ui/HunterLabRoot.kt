package dev.hunterlab.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.hunterlab.app.ble.BleController
import dev.hunterlab.app.ui.components.SessionSafetyActions
import dev.hunterlab.app.ui.contract.NavBindings
import dev.hunterlab.app.ui.devices.DevicesScreen
import dev.hunterlab.app.ui.devices.DevicesViewModel
import dev.hunterlab.app.ui.home.HomeScreen
import dev.hunterlab.app.ui.lab.LabActiveStrip
import dev.hunterlab.app.ui.lab.LabViewModel
import dev.hunterlab.app.ui.navigation.NavigationScreen
import dev.hunterlab.app.ui.navigation.hasActiveNavigation
import dev.hunterlab.app.ui.personalization.PersonalizationScreen
import dev.hunterlab.app.ui.playground.PlaygroundScreen
import dev.hunterlab.app.ui.playground.PlaygroundViewModel
import dev.hunterlab.app.ui.settings.AdvancedDestination
import dev.hunterlab.app.ui.settings.SettingsScreen
import dev.hunterlab.app.ui.simulation.NavigationSimScreen
import dev.hunterlab.app.ui.simulation.RadarSimScreen
import dev.hunterlab.app.ui.simulation.SimulationViewModel
import dev.hunterlab.app.ui.theme.HunterColors
import dev.hunterlab.app.ui.theme.HunterIcons

private enum class DailyDestination(val label: String) {
    HOME("Início"), NAVIGATION("Navegação"), PERSONALIZATION("Personalização"), SETTINGS("Configurações");

    val icon: ImageVector
        get() = when (this) {
            HOME -> HunterIcons.Home
            NAVIGATION -> HunterIcons.Navigation
            PERSONALIZATION -> HunterIcons.Personalization
            SETTINGS -> HunterIcons.Settings
        }
}

/** Apenas esta composição faz a ponte com os ViewModels das ferramentas existentes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HunterLabRoot(ble: BleController, nav: NavBindings) {
    var destination by rememberSaveable { mutableStateOf(DailyDestination.HOME) }
    var advanced by rememberSaveable { mutableStateOf<AdvancedDestination?>(null) }
    val homeState by nav.home.collectAsStateWithLifecycle()
    val navState by nav.nav.collectAsStateWithLifecycle()
    val settingsState by nav.settings.collectAsStateWithLifecycle()
    val devices: DevicesViewModel = viewModel { DevicesViewModel(ble) }
    val simulation: SimulationViewModel = viewModel()
    val appContext = LocalContext.current.applicationContext
    val playground: PlaygroundViewModel = viewModel { PlaygroundViewModel(ble, nav, appContext) }
    val lab: LabViewModel = viewModel { LabViewModel(ble) }
    val labState by lab.link.collectAsStateWithLifecycle()
    val active = homeState.navigationActive || navState.hasActiveNavigation()
    val canInterrupt = navState.tripper.canInterrupt || homeState.tripper.canInterrupt
    val keyboardVisible = WindowInsets.isImeVisible

    BackHandler(enabled = advanced != null || destination != DailyDestination.HOME) {
        if (advanced != null) advanced = null else destination = DailyDestination.HOME
    }
    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = HunterColors.Background,
        topBar = {
            Column(Modifier.statusBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (advanced != null) {
                        IconButton(onClick = { advanced = null }) { Icon(HunterIcons.Back, "Voltar para Configurações") }
                        Text(advanced!!.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    } else {
                        Text(
                            "HUNTER LAB", style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 3.sp, fontWeight = FontWeight.Bold),
                            color = HunterColors.TextSecondary,
                        )
                    }
                }
                LabActiveStrip(labState, onInterrupt = lab::interrupt)
            }
        },
        bottomBar = {
            Column {
                SessionSafetyActions(active, canInterrupt, nav.actions)
                if (!keyboardVisible) NavigationBar(containerColor = HunterColors.Surface, tonalElevation = 0.dp) {
                    DailyDestination.entries.forEach { item ->
                        NavigationBarItem(
                            selected = item == destination,
                            onClick = { destination = item; advanced = null },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = HunterColors.TextPrimary,
                                selectedTextColor = HunterColors.TextPrimary,
                                indicatorColor = HunterColors.SurfaceHighest,
                                unselectedIconColor = HunterColors.TextSecondary,
                                unselectedTextColor = HunterColors.TextSecondary,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        val advancedDestination = advanced
        if (advancedDestination != null) {
            // Mesmas telas, ViewModels e convenção de contentPadding da raiz original.
            when (advancedDestination) {
                AdvancedDestination.DEVICES -> DevicesScreen(devices, lab, padding)
                AdvancedDestination.NAVIGATION_SIM -> NavigationSimScreen(simulation, padding)
                AdvancedDestination.RADAR_SIM -> RadarSimScreen(simulation, padding)
                AdvancedDestination.PLAYGROUND -> PlaygroundScreen(playground, padding)
            }
        } else {
            val content = Modifier.fillMaxSize().padding(padding)
            when (destination) {
                DailyDestination.HOME -> HomeScreen(
                    homeState, nav.actions,
                    onNavigation = { destination = DailyDestination.NAVIGATION },
                    onSettings = { destination = DailyDestination.SETTINGS }, modifier = content,
                )
                DailyDestination.NAVIGATION -> NavigationScreen(
                    navState, nav.actions, onHome = { destination = DailyDestination.HOME }, modifier = content,
                    navigationActive = active, showSafetyActions = false,
                )
                DailyDestination.PERSONALIZATION -> PersonalizationScreen(settingsState, nav.actions, content, navState.tripperPreview, onPlayground = { advanced = AdvancedDestination.PLAYGROUND })
                DailyDestination.SETTINGS -> SettingsScreen(settingsState, nav.actions, onAdvanced = { advanced = it }, modifier = content)
            }
        }
    }
}
