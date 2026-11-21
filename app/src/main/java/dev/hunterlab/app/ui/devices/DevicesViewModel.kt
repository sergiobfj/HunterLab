package dev.hunterlab.app.ui.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.hunterlab.app.BuildConfigInfo
import dev.hunterlab.app.ble.BleController
import dev.hunterlab.app.ble.BleReadiness
import dev.hunterlab.app.ble.InspectorState
import dev.hunterlab.app.ble.ScanState
import dev.hunterlab.core.ble.CandidateLevel
import dev.hunterlab.core.ble.CandidateMatch
import dev.hunterlab.core.ble.ProfileReport
import dev.hunterlab.core.ble.ScannedDevice
import dev.hunterlab.core.ble.TripperCandidateClassifier
import dev.hunterlab.core.ble.TripperProfileCheck
import dev.hunterlab.core.ble.UserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.util.UUID

data class DeviceRow(val device: ScannedDevice, val match: CandidateMatch)

data class DeviceFilters(val onlyCandidates: Boolean = false, val showUnnamed: Boolean = true)

data class DevicesUiState(
    val readiness: BleReadiness,
    val permanentlyDenied: Boolean = false,
    val scan: ScanState = ScanState(),
    val rows: List<DeviceRow> = emptyList(),
    val totalSeen: Int = 0,
    val filters: DeviceFilters = DeviceFilters(),
    val selected: DeviceRow? = null,
    val inspector: InspectorState = InspectorState(),
    val profile: ProfileReport? = null,
    val message: UserMessage? = null,
    val labOpen: Boolean = false,
)

private data class Local(
    val filters: DeviceFilters = DeviceFilters(),
    val selectedAddress: String? = null,
    val selectedSnapshot: ScannedDevice? = null,
    val permanentlyDenied: Boolean = false,
    val message: UserMessage? = null,
    val labOpen: Boolean = false,
)

class DevicesViewModel(private val ble: BleController) : ViewModel() {

    private val local = MutableStateFlow(Local())

    val ui: StateFlow<DevicesUiState> = combine(ble.readiness, ble.scanner.state, ble.inspector.state, local) { readiness, scan, inspector, l ->
        val all = TripperCandidateClassifier.sort(scan.devices.values).map { DeviceRow(it, TripperCandidateClassifier.classify(it)) }
        val rows = all.filter { row ->
            (!l.filters.onlyCandidates || row.match.level != CandidateLevel.NONE) &&
                (l.filters.showUnnamed || row.device.name != null || row.match.level != CandidateLevel.NONE)
        }
        val selected = l.selectedAddress?.let { addr ->
            all.firstOrNull { it.device.address == addr }
                ?: l.selectedSnapshot?.let { DeviceRow(it, TripperCandidateClassifier.classify(it)) }
        }
        val inspectorForSelected = if (inspector.address == l.selectedAddress) inspector else InspectorState()
        DevicesUiState(
            readiness = readiness,
            permanentlyDenied = l.permanentlyDenied && !readiness.permissionsGranted,
            scan = scan,
            rows = rows,
            totalSeen = all.size,
            filters = l.filters,
            selected = selected,
            inspector = inspectorForSelected,
            profile = inspectorForSelected.snapshot?.let(TripperProfileCheck::check),
            message = l.message,
            labOpen = l.labOpen && selected != null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DevicesUiState(ble.readiness.value))

    fun refresh() = ble.refresh()

    fun onPermissionsResult(allGranted: Boolean, permanentlyDenied: Boolean) {
        ble.refresh()
        local.update { it.copy(permanentlyDenied = !allGranted && permanentlyDenied) }
    }

    fun startScan() {
        ble.refresh()
        val error = ble.scanner.start()
        local.update { it.copy(message = error) }
    }

    fun stopScan() = ble.scanner.stop()

    fun clearDevices() = ble.scanner.clear()

    fun setFilters(filters: DeviceFilters) = local.update { it.copy(filters = filters) }

    fun select(device: ScannedDevice) = local.update { it.copy(selectedAddress = device.address, selectedSnapshot = device, message = null) }

    fun closeDetail() = local.update { it.copy(selectedAddress = null, selectedSnapshot = null, labOpen = false) }

    fun openLab() = local.update { it.copy(labOpen = true, message = null) }

    fun closeLab() = local.update { it.copy(labOpen = false) }

    fun dismissMessage() = local.update { it.copy(message = null) }

    /** Só chamado depois da confirmação explícita do usuário no aviso de conexão. */
    fun connectSelected() {
        val device = ui.value.selected?.device ?: return
        if (ble.lab.snapshot.value.active) {
            local.update { it.copy(message = UserMessage("Laboratório BLE ativo", "Interrompa a sessão do laboratório antes de inspecionar.")) }
            return
        }
        ble.scanner.stop() // buscar e conectar ao mesmo tempo atrapalha a conexão em muitos aparelhos
        local.update { it.copy(message = ble.inspector.connect(device)) }
    }

    fun disconnect() = ble.inspector.disconnect()

    fun resetInspection() = ble.inspector.reset()

    fun read(service: UUID, characteristic: UUID) {
        local.update { it.copy(message = ble.inspector.read(service, characteristic)) }
    }

    fun report(): String? = ble.inspector.report(ui.value.selected?.device, BuildConfigInfo.VERSION)
}
