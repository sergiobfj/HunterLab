package dev.hunterlab.app.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.hunterlab.app.ui.components.PrimaryAction
import dev.hunterlab.app.ui.components.ProductPanel
import dev.hunterlab.app.ui.contract.*
import dev.hunterlab.app.ui.theme.HunterColors
import dev.hunterlab.app.ui.theme.HunterIcons

fun TripperUi.statusColor() = when (status) {
    TripperUiStatus.AUTHENTICATED, TripperUiStatus.SHOWING -> HunterColors.Confirmed
    TripperUiStatus.ERROR -> HunterColors.Error
    TripperUiStatus.DISCONNECTED -> HunterColors.TextSecondary
    else -> HunterColors.Amber
}

@Composable
fun TripperConnectButton(state: TripperUi, actions: NavActions, legacyConnect: (() -> Unit)? = null) {
    var terms by remember { mutableStateOf(false) }
    OutlinedButton(
        onClick = {
            when {
                legacyConnect != null -> legacyConnect()
                state.needsTerms -> terms = true
                else -> actions.connectTripper()
            }
        },
        enabled = legacyConnect != null || state.canConnect,
        modifier = Modifier.heightIn(min = 48.dp),
    ) {
        Icon(HunterIcons.Bluetooth, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(if (state.status == TripperUiStatus.ERROR) "Tentar conexão" else "Conectar Tripper")
    }
    if (terms) ConnectionTerms(
        onDismiss = { terms = false },
        onConfirm = {
            terms = false
            actions.acceptTerms()
            actions.connectTripper()
        },
    )
}

@Composable
private fun ConnectionTerms(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val items = listOf(
        "A moto está parada em um local seguro.",
        "O aplicativo oficial Royal Enfield está fechado.",
        "Li as instruções de conexão.",
        "Entendo que o pareamento pode afetar a conexão do app oficial.",
        "Sei onde encontrar Interromper e Encerrar.",
    )
    var accepted by remember { mutableStateOf(List(items.size) { false }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Antes de conectar") },
        text = {
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                items.forEachIndexed { index, label ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .toggleable(accepted[index], role = Role.Checkbox, onValueChange = { value ->
                                accepted = accepted.toMutableList().also { it[index] = value }
                            }).padding(vertical = 6.dp),
                    ) {
                        Checkbox(accepted[index], onCheckedChange = null)
                        Spacer(Modifier.width(10.dp))
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { Button(onConfirm, enabled = accepted.all { it }) { Text("Conectar") } },
        dismissButton = { TextButton(onDismiss) { Text("Agora não") } },
    )
}

/** Cada operação é um toque. O código digitado vive apenas nesta composição. */
@Composable
fun TripperControls(state: TripperUi, actions: NavActions, showInterrupt: Boolean = true) {
    ProductPanel {
        Text(state.label, style = MaterialTheme.typography.titleMedium, color = state.statusColor())
        state.detail?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = HunterColors.TextSecondary) }
        when (state.status) {
            TripperUiStatus.DISCONNECTED, TripperUiStatus.ERROR -> TripperConnectButton(state, actions)
            TripperUiStatus.CONNECTING, TripperUiStatus.STOPPING -> LinearProgressIndicator(Modifier.fillMaxWidth())
            TripperUiStatus.READY_TO_PAIR -> PrimaryAction("Mostrar PIN no Tripper", actions::requestPin, icon = HunterIcons.Lock)
            TripperUiStatus.WAITING_PIN -> PinInput(state, actions)
            TripperUiStatus.CONFIRM_PIN -> {
                Text("Confira o display do Tripper antes de confirmar.", style = MaterialTheme.typography.bodyMedium)
                PrimaryAction("Sim, aceitou", actions::confirmPinAccepted, enabled = state.pinDelivered, icon = HunterIcons.Check)
                TextButton(actions::reportPinRejected, Modifier.heightIn(min = 48.dp)) { Text("Está pedindo PIN") }
            }
            TripperUiStatus.AUTHENTICATED, TripperUiStatus.SHOWING -> Unit
        }
        if (showInterrupt && state.canInterrupt) {
            TextButton(actions::interruptTripper, Modifier.heightIn(min = 48.dp), colors = ButtonDefaults.textButtonColors(contentColor = HunterColors.Error)) {
                Text("Interromper Tripper")
            }
        }
    }
}

@Composable
private fun PinInput(state: TripperUi, actions: NavActions) {
    val code = remember(state.status) { TextFieldState() }
    OutlinedSecureTextField(
        state = code,
        inputTransformation = InputTransformation {
            if (length > 6 || asCharSequence().any { it !in '0'..'9' && it !in 'a'..'z' && it !in 'A'..'Z' }) revertAllChanges()
        },
        label = { Text("PIN no display") },
        modifier = Modifier.fillMaxWidth(),
        textObfuscationMode = TextObfuscationMode.Hidden,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
    )
    Text("Tentativa ${(state.pinAttempt + 1).coerceAtMost(state.pinAttemptsMax)} de ${state.pinAttemptsMax}", style = MaterialTheme.typography.bodySmall, color = HunterColors.TextSecondary)
    PrimaryAction("Enviar PIN", onClick = {
        val entered = code.text.toString()
        code.edit { replace(0, length, "") }
        actions.submitPin(entered)
    }, enabled = code.text.isNotEmpty() && state.pinAttempt < state.pinAttemptsMax, icon = HunterIcons.Lock)
}
