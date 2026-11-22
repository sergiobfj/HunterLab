package dev.hunterlab.app

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class PlaygroundBoundaryTest {
    private val screen = File("src/main/java/dev/hunterlab/app/ui/playground/PlaygroundScreen.kt").readText()
    private val model = File("src/main/java/dev/hunterlab/app/ui/playground/PlaygroundViewModel.kt").readText()
    private fun forbidden(text: String) = listOf("connectGatt", "writeCharacteristic", "writePacket(", "TripperCommands.", "TripperPacket", "ble.lab.").any(text::contains)

    @Test fun `tela nao monta pacotes nem acessa radio`() {
        assertFalse(forbidden(screen))
        // Contraexemplos: o guard precisa continuar recusando uma capacidade introduzida por qualquer componente.
        listOf("device.connectGatt(...) ", "gatt.writeCharacteristic(...) ", "port.writePacket(bytes)", "TripperCommands.compass(...) ", "val p: TripperPacket", "ble.lab.startCompassTest(...)").forEach { assertTrue(it, forbidden(it)) }
        assertFalse(model.contains("startCompassTest("))
        assertFalse(model.contains("writePacket("))
    }
    @Test fun `abrir ViewModel nao arma conecta nem exibe automaticamente`() {
        val initial = model.substringAfter("    init {").substringBefore("    private fun append")
        for (call in listOf("hardware.arm(", "hardware.connect(", "hardware.show(")) assertFalse(call, initial.contains(call))
    }
    @Test fun `PIN e efemero oculto limpo e nao exportado`() {
        assertFalse(screen.contains("rememberSaveable"))
        assertTrue(screen.contains("OutlinedSecureTextField("))
        assertTrue(screen.contains("TextObfuscationMode.Hidden"))
        assertTrue(screen.contains("replace(0, length, \"\")"))
        val report = model.substringAfter("override fun report(): String").substringBefore("override fun onCleared")
        assertTrue(report.contains("ExperimentLog.report")); assertFalse(report.contains("ble.lab.report"))
    }
}
