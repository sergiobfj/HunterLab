package dev.hunterlab.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.hunterlab.app.ui.contract.*

/** Nenhum passo é disparado por efeito de composição ou mudança de estado. */
@Composable
fun rememberTripperConnect(tripper: TripperUi, actions: NavActions): () -> Unit {
    var showTerms by remember { mutableStateOf(false) }
    if (showTerms) {
        TripperTermsDialog(
            canConnect = tripper.canConnect,
            onDismiss = { showTerms = false },
            onAccept = {
                showTerms = false
                actions.acceptTerms()
                actions.connectTripper()
            },
        )
    }
    return {
        if (tripper.canConnect) {
            if (tripper.needsTerms) showTerms = true else actions.connectTripper()
        }
    }
}

@Composable
private fun TripperTermsDialog(canConnect: Boolean, onDismiss: () -> Unit, onAccept: () -> Unit) {
    val items = listOf(
        "A moto está parada.",
        "O app oficial Royal Enfield está fechado.",
        "Li as instruções de conexão.",
        "Entendo que o pareamento pode afetar o do app oficial.",
        "Sei onde ficam Interromper e Encerrar.",
    )
    var checked by remember { mutableStateOf(emptySet<Int>()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Antes de conectar") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                items.forEachIndexed { index, item ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .toggleable(index in checked, role = Role.Checkbox) {
                                checked = if (it) checked + index else checked - index
                            }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(index in checked, onCheckedChange = null)
                        Spacer(Modifier.width(10.dp))
                        Text(item, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onAccept, enabled = canConnect && checked.size == items.size, modifier = Modifier.heightIn(min = 48.dp)) { Text("Concordar e conectar") } },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Agora não") } },
    )
}

@Composable
fun TripperConnection(tripper: TripperUi, actions: NavActions) {
    val connect = rememberTripperConnect(tripper, actions)
    // Segredo efêmero: nunca Saveable; descartado ao mudar de etapa/tentativa e após enviar.
    var pin by remember(tripper.status, tripper.pinAttempt) { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(tripper.label, style = MaterialTheme.typography.titleSmall)
        tripper.detail?.let { SupportingText(it, error = tripper.status == TripperUiStatus.ERROR) }
        when (tripper.status) {
            TripperUiStatus.DISCONNECTED, TripperUiStatus.ERROR -> {
                OutlinedButton(onClick = connect, enabled = tripper.canConnect, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Conectar Tripper")
                }
            }
            TripperUiStatus.READY_TO_PAIR -> PrimaryAction("Mostrar PIN no Tripper", actions::requestPin)
            TripperUiStatus.WAITING_PIN -> {
                OutlinedTextField(
                    value = pin, onValueChange = { pin = it },
                    label = { Text("PIN mostrado no Tripper") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (tripper.pinAttemptsMax > 0) SupportingText("Tentativa ${tripper.pinAttempt} de ${tripper.pinAttemptsMax}")
                PrimaryAction("Enviar PIN", enabled = pin.isNotBlank(), onClick = {
                    val entered = pin
                    pin = ""
                    actions.submitPin(entered)
                })
            }
            TripperUiStatus.CONFIRM_PIN -> {
                SupportingText("Confira o visor do Tripper.")
                PrimaryAction("Sim, aceitou", actions::confirmPinAccepted, enabled = tripper.pinDelivered)
                OutlinedButton(onClick = actions::reportPinRejected, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("Está pedindo PIN")
                }
            }
            else -> Unit // Conectando, autenticado, transmitindo ou encerrando: só informa o estado.
        }
    }
}
