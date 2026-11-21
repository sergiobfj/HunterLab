package dev.hunterlab.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UiBoundaryTest {
    private val ui = File("src/main/java/dev/hunterlab/app/ui")
    private val areas = listOf("home", "navigation", "personalization", "settings", "contract")
    private val files = areas.flatMap { File(ui, it).walkTopDown().filter { file -> file.isFile && file.extension == "kt" }.toList() }

    @Test
    fun `novas telas nao acessam transporte rede ou persistencia`() {
        assertTrue(files.isNotEmpty())
        val forbidden = listOf("connectGatt", "writeCharacteristic", "writePacket(", "TripperLink", "LinkCommand", "getSharedPreferences", "HttpURLConnection", "requestLocationUpdates")
        files.forEach { file -> forbidden.forEach { api ->
            assertFalse("${file.name} acessa $api", file.readText().contains(api))
        } }
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
