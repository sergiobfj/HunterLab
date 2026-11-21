package dev.hunterlab.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Regressão de segurança da versão 0.2.0 (varre o código de produção dos três módulos):
 *
 * - Proibido em qualquer lugar: notificações/descritores, pareamento do sistema, MTU, anúncio, rede,
 *   persistência (SharedPreferences/DataStore).
 * - Escrita GATT e servidor GATT: só no adaptador do Laboratório BLE.
 * - Comandos de transmissão: só no núcleo (TripperLink) e na UI do laboratório — nunca na inicialização.
 *
 * Roda na JVM; o diretório de trabalho dos testes unitários é o do módulo `app`.
 */
class SafetyGuardTest {

    private val root = File("..").canonicalFile
    private val sources: Map<String, String> = listOf("app/src/main", "core/src/main", "protocol/src/main")
        .map { File(root, it) }
        .flatMap { dir -> dir.walkTopDown().filter { it.isFile && it.extension in setOf("kt", "java", "xml") }.toList() }
        .associate { it.relativeTo(root).invariantSeparatorsPath to it.readText() }

    private val labAdapter = "app/src/main/java/dev/hunterlab/app/ble/lab/AndroidTripperLink.kt"

    private fun filesContaining(token: String) = sources.filterValues { it.contains(token, ignoreCase = true) }.keys

    @Test
    fun `existem fontes para verificar`() {
        assertTrue("nenhum fonte encontrado em $root", sources.size > 30)
        assertTrue(labAdapter in sources)
    }

    @Test
    fun `APIs proibidas nao aparecem em lugar nenhum`() {
        val forbidden = listOf(
            "writeDescriptor", "setCharacteristicNotification", "requestMtu", "createBond", "startAdvertising",
            "BluetoothLeAdvertiser", "android.permission.INTERNET", "java.net.", "HttpURLConnection", "okhttp",
            "getSharedPreferences", "DataStore", "openFileOutput",
        )
        val hits = forbidden.flatMap { token -> filesContaining(token).map { "$it: $token" } }
        assertEquals("Chamadas proibidas:\n" + hits.joinToString("\n"), emptyList<String>(), hits)
    }

    @Test
    fun `escrita GATT e servidor local so existem no adaptador do laboratorio`() {
        for (token in listOf("writeCharacteristic", "openGattServer", "sendResponse")) {
            assertEquals("$token fora do adaptador", setOf(labAdapter), filesContaining(token))
        }
    }

    @Test
    fun `a porta de escrita so e chamada pelo nucleo`() {
        assertEquals(
            setOf(
                "core/src/main/kotlin/dev/hunterlab/core/link/LinkPorts.kt",
                "core/src/main/kotlin/dev/hunterlab/core/link/TripperLink.kt",
                labAdapter,
            ),
            filesContaining("writePacket("),
        )
    }

    @Test
    fun `nenhuma transmissao e disparada pela inicializacao do app`() {
        val allowed = setOf(
            "core/src/main/kotlin/dev/hunterlab/core/link/TripperLink.kt",
            "app/src/main/java/dev/hunterlab/app/ui/lab/LabViewModel.kt",
            "app/src/main/java/dev/hunterlab/app/ui/lab/LabScreen.kt",
            // Contrato, fallback sem operações e envio manual exclusivamente por NavActions.
            "app/src/main/java/dev/hunterlab/app/ui/contract/NavContracts.kt",
            "app/src/main/java/dev/hunterlab/app/ui/home/LocalAppearanceActions.kt",
            "app/src/main/java/dev/hunterlab/app/ui/navigation/TripperControls.kt",
        )
        for (token in listOf("startPairing", "submitPin", "startCompassTest", "authorizeTransmission", "syncClock")) {
            val outside = filesContaining(token) - allowed
            assertTrue("$token aparece fora do laboratório: $outside", outside.isEmpty())
        }
        val startup = listOf(
            "app/src/main/java/dev/hunterlab/app/MainActivity.kt",
            "app/src/main/java/dev/hunterlab/app/HunterLabApplication.kt",
            "app/src/main/java/dev/hunterlab/app/ble/BleController.kt",
        )
        startup.forEach { f ->
            val text = sources.getValue(f)
            assertTrue("$f não pode conectar o laboratório", !text.contains("lab.connect"))
        }
    }

    @Test
    fun `o app nao monta comandos nem pacotes para o radio`() {
        val appFiles = sources.filterKeys { it.startsWith("app/") }
        val hits = appFiles.filterValues { it.contains("LinkCommand") }.keys
        assertTrue("o app não deve referenciar LinkCommand: $hits", hits.isEmpty())
    }

    @Test
    fun `PIN nao vai para estado persistente da UI`() {
        val labUi = sources.getValue("app/src/main/java/dev/hunterlab/app/ui/lab/LabScreen.kt")
        assertTrue("LabScreen não pode usar rememberSaveable", !labUi.contains("rememberSaveable"))
        val vm = sources.getValue("app/src/main/java/dev/hunterlab/app/ui/lab/LabViewModel.kt")
        assertTrue("LabViewModel não pode guardar PIN em campo", !Regex("va[lr]\\s+\\w*pin\\w*\\s*[:=]", RegexOption.IGNORE_CASE).containsMatchIn(vm))
    }

    @Test
    fun `diagnostico de conexao so consulta estado`() {
        val diag = sources.getValue("app/src/main/java/dev/hunterlab/app/ble/BleDiagnostics.kt")
        for (token in listOf("connectGatt", ".disconnect(", ".close(", "writeCharacteristic", "startScan", "openGattServer")) {
            assertTrue("BleDiagnostics não pode chamar $token", !diag.contains(token))
        }
    }

    @Test
    fun `conexao do laboratorio nao escreve nem dispara handshake`() {
        val adapter = sources.getValue(labAdapter)
        val connect = adapter.substringAfter("override fun connect(").substringBefore("override fun discoverServices(")
        for (token in listOf("writeCharacteristic", "writePacket", "startPairing", "sendResponse")) {
            assertTrue("o caminho de conexão não pode conter $token", !connect.contains(token))
        }
    }

    @Test
    fun `manifesto declara scan sem localizacao e nenhuma permissao extra`() {
        val manifest = sources.getValue("app/src/main/AndroidManifest.xml")
        assertTrue(manifest.contains("neverForLocation"))
        val permissions = Regex("uses-permission\\s+android:name=\"([^\"]+)\"").findAll(manifest).map { it.groupValues[1] }.toSet()
        assertEquals(setOf("android.permission.BLUETOOTH_SCAN", "android.permission.BLUETOOTH_CONNECT"), permissions)
    }

    @Test
    fun `versao exibida bate com versionName`() {
        val gradle = File(root, "app/build.gradle.kts").readText()
        assertTrue(gradle.contains("versionName = \"${BuildConfigInfo.VERSION}\""))
    }
}
