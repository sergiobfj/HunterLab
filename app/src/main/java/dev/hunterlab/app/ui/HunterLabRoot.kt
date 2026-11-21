package dev.hunterlab.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.hunterlab.app.BuildConfigInfo
import dev.hunterlab.app.ble.BleController
import dev.hunterlab.app.ui.components.Pill
import dev.hunterlab.app.ui.devices.DevicesScreen
import dev.hunterlab.app.ui.devices.DevicesViewModel
import dev.hunterlab.app.ui.lab.LabActiveStrip
import dev.hunterlab.app.ui.lab.LabViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hunterlab.app.ui.playground.PlaygroundScreen
import dev.hunterlab.app.ui.playground.PlaygroundViewModel
import dev.hunterlab.app.ui.simulation.NavigationSimScreen
import dev.hunterlab.app.ui.simulation.RadarSimScreen
import dev.hunterlab.app.ui.simulation.SimulationViewModel
import dev.hunterlab.app.ui.theme.HunterColors
import dev.hunterlab.app.ui.theme.HunterIcons

enum class Destination(val label: String, val title: String, val subtitle: String) {
    DEVICES("Dispositivos", "Dispositivos", "Busca e inspeção BLE — somente leitura"),
    NAVIGATION("Navegação", "Simulador de navegação", "Manobras, distâncias e período"),
    RADAR("Radares", "Simulador de radares", "Eventos fictícios e prioridade"),
    PLAYGROUND("Playground", "Playground visual", "Catálogo de símbolos e experimentos");

    val icon: ImageVector
        get() = when (this) {
            DEVICES -> HunterIcons.Bluetooth
            NAVIGATION -> HunterIcons.Navigation
            RADAR -> HunterIcons.Radar
            PLAYGROUND -> HunterIcons.Playground
        }
}

@Composable
fun HunterLabRoot(ble: BleController) {
    var destination by rememberSaveable { mutableStateOf(Destination.DEVICES) }
    val devices: DevicesViewModel = viewModel { DevicesViewModel(ble) }
    val simulation: SimulationViewModel = viewModel()
    val playground: PlaygroundViewModel = viewModel()
    val lab: LabViewModel = viewModel { LabViewModel(ble) }
    val labState by lab.link.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = HunterColors.Background,
        topBar = {
            Column {
                TopBar(destination)
                LabActiveStrip(labState, onInterrupt = lab::interrupt)
            }
        },
        bottomBar = {
            NavigationBar(containerColor = HunterColors.Surface, tonalElevation = 0.dp) {
                Destination.entries.forEach { d ->
                    NavigationBarItem(
                        selected = d == destination,
                        onClick = { destination = d },
                        icon = { Icon(d.icon, contentDescription = null) },
                        label = { Text(d.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = HunterColors.Amber,
                            selectedTextColor = HunterColors.AmberSoft,
                            indicatorColor = HunterColors.AmberContainer,
                            unselectedIconColor = HunterColors.TextMuted,
                            unselectedTextColor = HunterColors.TextMuted,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().background(HunterColors.Background)) {
            when (destination) {
                Destination.DEVICES -> DevicesScreen(devices, lab, padding)
                Destination.NAVIGATION -> NavigationSimScreen(simulation, padding)
                Destination.RADAR -> RadarSimScreen(simulation, padding)
                Destination.PLAYGROUND -> PlaygroundScreen(playground, padding)
            }
        }
    }
}

@Composable
private fun TopBar(destination: Destination) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(HunterColors.Background)
            .statusBarsPadding()
            .padding(start = 20.dp, end = 16.dp, top = 10.dp, bottom = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "HUNTER LAB",
                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 3.sp, fontWeight = FontWeight.Bold),
                color = HunterColors.Amber,
            )
            Spacer(Modifier.width(10.dp))
            Pill("v${BuildConfigInfo.VERSION}", HunterColors.TextSecondary)
            Spacer(Modifier.weight(1f))
            Text("Hunter 350 · Tripper Pod", style = MaterialTheme.typography.labelSmall, color = HunterColors.TextMuted)
        }
        Spacer(Modifier.height(6.dp))
        Text(destination.title, style = MaterialTheme.typography.headlineSmall, color = HunterColors.TextPrimary)
        Text(destination.subtitle, style = MaterialTheme.typography.bodySmall, color = HunterColors.TextSecondary)
    }
}
