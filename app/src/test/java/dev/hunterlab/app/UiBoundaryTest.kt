package dev.hunterlab.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UiBoundaryTest {
    private val ui = File("src/main/java/dev/hunterlab/app/ui")
    private val areas = listOf("home", "navigation", "personalization", "settings", "contract")
    private val files = areas.flatMap { File(ui, it).walkTopDown().filter { file -> file.isFile && file.extension == "kt" }.toList() }
    private fun forbiddenAccess(text: String): Boolean = listOf(
        "connectGatt", "writeCharacteristic", "writePacket(", "TripperLink", "LinkCommand",
        "getSharedPreferences", "HttpURLConnection", "requestLocationUpdates",
    ).any(text::contains)

    @Test
    fun `novas telas nao acessam transporte rede ou persistencia`() {
        assertTrue(files.isNotEmpty())
        // Contraexemplos: reconhecer uma tela de NavActions nunca libera rádio, GPS ou armazenamento direto.
        listOf(
            "device.connectGatt(context, false, callback)", "gatt.writeCharacteristic(value)",
            "port.writePacket(bytes)", "val link: TripperLink", "prefs.getSharedPreferences(name, 0)",
            "val connection: HttpURLConnection", "gps.requestLocationUpdates(provider, listener)",
        ).forEach { forbidden -> assertTrue("Comportamento proibido não detectado: $forbidden", forbiddenAccess(forbidden)) }
        files.forEach { file -> assertFalse("${file.name} acessa capacidade proibida", forbiddenAccess(file.readText())) }
    }

    @Test
    fun `campos de PIN e chave nao entram no estado salvo nem em logs`() {
        val secrets = listOf(File(ui, "navigation/TripperControls.kt"), File(ui, "settings/SettingsScreen.kt"))
        secrets.forEach { file ->
            val text = file.readText()
            assertFalse("${file.name}: estado persistente", text.contains("rememberSaveable"))
            assertFalse("${file.name}: log", Regex("(?:Log\\.|println\\(|logger\\.)").containsMatchIn(text))
            assertTrue("${file.name}: campo sem máscara", text.contains("OutlinedSecureTextField("))
            assertTrue("${file.name}: caracteres visíveis", text.contains("TextObfuscationMode.Hidden"))
            assertTrue("${file.name}: código não é limpo", text.contains("replace(0, length, \"\")"))
        }
    }

    @Test
    fun `telas de uso nao importam estados ficticios das previas`() {
        files.forEach { file ->
            assertFalse("${file.name}: dados fictícios no fluxo real", file.readText().contains("SampleStates"))
        }
    }
}
