package dev.hunterlab.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Regressão de segurança (varre o código de produção dos três módulos). Desde a 0.3.0 o app usa rede, GPS e
 * preferências para navegar — cada capacidade fica confinada a UM arquivo, verificado aqui:
 *
 * - Proibido em qualquer lugar: notificações/descritores BLE, pareamento do sistema, MTU, anúncio, DataStore,
 *   localização em segundo plano, bibliotecas de rede de terceiros.
 * - Rede: só `net/HttpsClient.kt`, só HTTPS e só hosts da lista (rotas e radares). Sem telemetria.
 * - Preferências: só `settings/AppPreferences.kt`, sem PIN. Arquivos: só o cache de radares.
 * - Escrita GATT e servidor GATT: só no adaptador BLE. Comandos de transmissão: só no núcleo, no ViewModel do
 *   laboratório e no controlador de navegação — nunca na inicialização.
 *
 * Roda na JVM; o diretório de trabalho dos testes unitários é o do módulo `app`.
 */
class SafetyGuardTest {

    private val root = File("..").canonicalFile
    private val sources: Map<String, String> = listOf("app/src/main", "core/src/main", "protocol/src/main")
        .map { File(root, it) }
        .flatMap { dir -> dir.walkTopDown().filter { it.isFile && it.extension in setOf("kt", "java", "xml") }.toList() }
        .associate { it.relativeTo(root).invariantSeparatorsPath to it.readText() }

    private val app = "app/src/main/java/dev/hunterlab/app"
    private val labAdapter = "$app/ble/lab/AndroidTripperLink.kt"
    private val httpClient = "$app/net/HttpsClient.kt"
    private val preferences = "$app/settings/AppPreferences.kt"
    private val navController = "$app/nav/NavigationController.kt"

    private fun filesContaining(token: String) = sources.filterValues { it.contains(token, ignoreCase = true) }.keys
    private fun filesMatching(regex: Regex) = sources.filterValues { regex.containsMatchIn(it) }.keys
    private fun isUi(path: String) = path.startsWith("$app/ui/")

    @Test
    fun `existem fontes para verificar`() {
        assertTrue("nenhum fonte encontrado em $root", sources.size > 30)
        for (f in listOf(labAdapter, httpClient, preferences, navController)) assertTrue("faltando $f", f in sources)
    }

    @Test
    fun `APIs proibidas nao aparecem em lugar nenhum`() {
        val forbidden = listOf(
            "writeDescriptor", "setCharacteristicNotification", "requestMtu", "createBond", "startAdvertising",
            "BluetoothLeAdvertiser", "DataStore", "ACCESS_BACKGROUND_LOCATION", "okhttp", "retrofit", "io.ktor",
            "ServerSocket", "DatagramSocket", " Socket(", "firebase", "crashlytics", "analytics",
        )
        val hits = forbidden.flatMap { token -> filesContaining(token).map { "$it: $token" } }
        assertEquals("Chamadas proibidas:\n" + hits.joinToString("\n"), emptyList<String>(), hits)
    }

    @Test
    fun `rede so no cliente HTTPS e so para hosts permitidos`() {
        for (token in listOf("HttpURLConnection", "HttpsURLConnection", "openConnection", "URLConnection")) {
            val outside = filesContaining(token) - httpClient
            assertTrue("$token fora do cliente HTTPS: $outside", outside.isEmpty())
        }
        val client = sources.getValue(httpClient)
        assertTrue("o cliente usa HttpsURLConnection", client.contains("HttpsURLConnection"))
        assertTrue("o cliente precisa recusar http://", client.contains("\"https\""))
        // Toda URL literal fora das telas (links abertos no navegador) aponta para um host da lista do cliente.
        val allowed = Regex("\"([a-z0-9.-]+\\.[a-z]{2,})\"").findAll(client.substringAfter("val ALLOWED_HOSTS").substringBefore(")"))
            .map { it.groupValues[1] }.toSet()
        assertTrue("lista de hosts vazia", allowed.isNotEmpty())
        val urls = sources.filterKeys { it.endsWith(".kt") && !isUi(it) }.flatMap { (file, text) ->
            Regex("\"https?://([^/\"?:]+)").findAll(text).map { file to it.groupValues[1] }.toList()
        }
        val outside = urls.filter { it.second !in allowed }
        assertTrue("URLs para hosts fora da lista: $outside", outside.isEmpty())
        val manifest = sources.getValue("app/src/main/AndroidManifest.xml")
        assertTrue("sem tráfego em texto puro", !manifest.contains("usesCleartextTraffic=\"true\""))
    }

    @Test
    fun `persistencia so nas preferencias e no cache de radares, nunca o PIN`() {
        assertEquals(setOf(preferences), filesContaining("getSharedPreferences"))
        val fileWriters = filesMatching(Regex("""openFileOutput|\.writeText\(|FileOutputStream|\.writeBytes\("""))
        assertTrue("escrita em arquivo fora do cache de radares: $fileWriters", fileWriters.all { it.startsWith("$app/radar/") })
        val prefs = sources.getValue(preferences)
        assertTrue("preferências não podem ter chave de PIN", !Regex("KEY_\\w*PIN", RegexOption.IGNORE_CASE).containsMatchIn(prefs))
        assertTrue("preferências não guardam endereço do Tripper", !prefs.contains("address", ignoreCase = true))
    }

    @Test
    fun `localizacao so na fonte de GPS`() {
        assertEquals(setOf("$app/nav/AndroidLocationSource.kt"), filesContaining("requestLocationUpdates"))
    }

    @Test
    fun `escrita GATT e servidor local so existem no adaptador do laboratorio`() {
        for (token in listOf("writeCharacteristic", "openGattServer", "sendResponse")) {
            assertEquals("$token fora do adaptador", setOf(labAdapter), filesContaining(token))
        }
    }

    @Test
    fun `os dois caminhos Android validam o quadro antes de tocar a caracteristica`() {
        val source = sources.getValue(labAdapter)
        val write = source.substringAfter("override fun writePacket(").substringBefore("override fun disconnect(")
        val guard = write.indexOf("if (!CommandWhitelist.isTransmittable(outbound)) return WriteStart.Refused(-5)")
        assertTrue("barreira final ausente", guard >= 0)
        assertTrue(write.indexOf("val outbound = bytes.copyOf()") in 0 until guard)
        val modern = write.indexOf("g.writeCharacteristic(ch, outbound, type)")
        val legacy = write.indexOf("ch.value = outbound")
        assertTrue("caminho API 33+ deve usar a cópia validada", modern > guard)
        assertTrue("caminho API 31-32 deve usar a mesma cópia validada", legacy > guard)
        assertTrue("bytes não validados não podem chegar ao rádio", !write.contains("ch.value = bytes") && !write.contains("writeCharacteristic(ch, bytes"))
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
    fun `transmissao so por acao do usuario e nunca na inicializacao`() {
        val calls = Regex("""\.(startPairing|submitPin|startCompassTest|authorizeTransmission|syncClock|startScreenStream|updateScreen)\(""")
        val allowedOutsideUi = setOf(
            "core/src/main/kotlin/dev/hunterlab/core/link/TripperLink.kt",
            "core/src/main/kotlin/dev/hunterlab/core/display/TripperDisplayController.kt",
            "core/src/main/kotlin/dev/hunterlab/core/playground/PlaygroundController.kt",
            navController,
        )
        val outside = filesMatching(calls).filterNot { isUi(it) } - allowedOutsideUi
        assertTrue("chamadas de transmissão fora do núcleo/controlador: $outside", outside.isEmpty())
        // Telas só falam com ViewModels/NavActions; o link só aparece no laboratório.
        val uiTouchingLink = sources.filterKeys { isUi(it) && !it.startsWith("$app/ui/lab/") }
            .filterValues { it.contains("TripperLink") || it.contains("startScreenStream") }.keys
        assertTrue("telas acessando o link diretamente: $uiTouchingLink", uiTouchingLink.isEmpty())
        val startup = listOf("$app/MainActivity.kt", "$app/HunterLabApplication.kt", "$app/ble/BleController.kt", "$app/nav/NavigationService.kt")
        startup.forEach { f ->
            val text = sources.getValue(f)
            assertTrue("$f não pode conectar ao Tripper", !text.contains("lab.connect") && !text.contains("connectTripper"))
            assertTrue("$f não pode transmitir", !calls.containsMatchIn(text))
        }
    }

    @Test
    fun `o app nao monta comandos nem pacotes para o radio`() {
        val appFiles = sources.filterKeys { it.startsWith("app/") }
        val hits = appFiles.filterValues { it.contains("LinkCommand") }.keys
        assertTrue("o app não deve referenciar LinkCommand: $hits", hits.isEmpty())
    }

    @Test
    fun `PIN nao vai para estado persistente`() {
        val pinUis = sources.filterKeys { isUi(it) }.filterValues { it.contains("submitPin") }.keys
        assertTrue("há uma tela de PIN", pinUis.isNotEmpty())
        pinUis.forEach { f -> assertTrue("$f não pode usar rememberSaveable (tela com PIN)", !sources.getValue(f).contains("rememberSaveable")) }
        val pinField = Regex("va[lr]\\s+\\w*pin\\w*\\s*[:=]", RegexOption.IGNORE_CASE)
        for (f in listOf("$app/ui/lab/LabViewModel.kt", navController)) {
            assertTrue("$f não pode guardar PIN em campo", !pinField.containsMatchIn(sources.getValue(f)))
        }
    }

    @Test
    fun `diagnostico de conexao so consulta estado`() {
        val diag = sources.getValue("$app/ble/BleDiagnostics.kt")
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
    fun `manifesto declara so as permissoes necessarias`() {
        val manifest = sources.getValue("app/src/main/AndroidManifest.xml")
        assertTrue(manifest.contains("neverForLocation"))
        val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val document = factory.newDocumentBuilder().parse(manifest.byteInputStream())
        val tags = document.getElementsByTagName("uses-permission")
        val elements = (0 until tags.length).map { tags.item(it) as org.w3c.dom.Element }
        val androidNs = "http://schemas.android.com/apk/res/android"
        val toolsNs = "http://schemas.android.com/tools"
        val permissions = elements.filter { it.getAttributeNS(toolsNs, "node") != "remove" }
            .map { it.getAttributeNS(androidNs, "name") }.toSet()
        val removed = elements.filter { it.getAttributeNS(toolsNs, "node") == "remove" }
            .map { it.getAttributeNS(androidNs, "name") }.toSet()
        assertEquals(setOf("android.permission.ACCESS_WIFI_STATE"), removed)
        val expected = setOf(
            "BLUETOOTH_SCAN", "BLUETOOTH_CONNECT", "INTERNET", "ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION",
            "FOREGROUND_SERVICE", "FOREGROUND_SERVICE_LOCATION", "FOREGROUND_SERVICE_CONNECTED_DEVICE",
            "POST_NOTIFICATIONS", "WAKE_LOCK",
        ).map { "android.permission.$it" }.toSet()
        assertEquals(expected, permissions)
        assertTrue("serviço de navegação não exportado", manifest.contains("android:exported=\"false\""))
        assertTrue("sem backup na nuvem (chave de API, preferências)", manifest.contains("android:allowBackup=\"false\""))
        assertTrue(manifest.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\""))
        val rules = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(root, "app/src/main/res/xml/data_extraction_rules.xml"))
        for (mode in listOf("cloud-backup", "device-transfer")) {
            val excludes = (rules.getElementsByTagName(mode).item(0) as org.w3c.dom.Element).getElementsByTagName("exclude")
            val domains = (0 until excludes.length).map { (excludes.item(it) as org.w3c.dom.Element).getAttribute("domain") }.toSet()
            assertTrue("$mode deve excluir chave e dados de localização", domains.containsAll(setOf("sharedpref", "file", "root", "device_sharedpref", "device_file")))
        }
    }

    @Test
    fun `versao exibida bate com versionName`() {
        val gradle = File(root, "app/build.gradle.kts").readText()
        assertTrue(gradle.contains("versionName = \"${BuildConfigInfo.VERSION}\""))
    }
}
