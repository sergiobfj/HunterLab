package dev.hunterlab.app

import android.media.AudioManager
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import java.util.concurrent.atomic.AtomicInteger
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.hunterlab.app.radar.AndroidRadarSpeech
import dev.hunterlab.app.settings.AppPreferences
import dev.hunterlab.app.ui.HunterLabRoot
import dev.hunterlab.app.ui.contract.*
import dev.hunterlab.app.ui.preview.EmptyNavActions
import dev.hunterlab.app.ui.theme.HunterLabTheme
import dev.hunterlab.core.geo.GeoPoint
import dev.hunterlab.core.geo.Polyline
import dev.hunterlab.core.radar.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.After
import org.junit.Rule
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/** Device fixtures, not GPS, network routing, physical cameras or Tripper validation. No radio actions. */
@RunWith(AndroidJUnit4::class)
class RadarDeviceReviewTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val output get() = File(context.filesDir, "device-review").apply { mkdirs() }
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    @After fun noAutomaticRadioActivity() {
        val ble = (context.applicationContext as HunterLabApplication).ble
        val link = ble.lab.snapshot.value
        assertEquals(dev.hunterlab.core.link.LinkState.DISCONNECTED, link.state)
        assertEquals(0L, link.sessionId)
        assertEquals(0, link.diagnostics.writesDelivered)
        assertEquals(0, link.diagnostics.queueSize)
        assertEquals(dev.hunterlab.core.link.ServerState.CLOSED, link.diagnostics.server)
        assertFalse(ble.scanner.state.value.scanning)
        assertNull(ble.scanner.state.value.startedAtMs)
        assertEquals(dev.hunterlab.core.ble.ConnectionPhase.IDLE, ble.inspector.state.value.phase)
        save("radio-observation", JSONObject().put("session_count", 0).put("writes_delivered", 0)
            .put("scan_started", false).put("gatt_server", "closed").put("inspector", "idle"))
    }

    private fun save(name: String, value: JSONObject) = File(output, "$name.json").writeText(value.toString(2))

    @Test fun renderSyntheticRadarStates() {
        val label = InstrumentationRegistry.getArguments().getString("reviewLabel", "device")
        require(label.matches(Regex("[a-z0-9-]+")))
        val activity = compose.activity
        val nav = MutableStateFlow(NavUi())
        val home = MutableStateFlow(HomeUi(navigationActive = true))
        val bindings = NavBindings(home, nav, MutableStateFlow(SettingsUi()), EmptyNavActions)
        val app = context.applicationContext as HunterLabApplication
        val results = JSONArray()
        try {
            main { activity.setContent { HunterLabTheme { HunterLabRoot(app.ble, bindings) } } }
            compose.waitForIdle()
            val compact = context.resources.configuration.screenWidthDp < 360 && context.resources.configuration.fontScale > 1.2f
            if (compact) {
                for (area in listOf("Início", "Navegação", "Personalização", "Configurações"))
                    compose.onNodeWithContentDescription(area).assertIsDisplayed().assertHasClickAction()
                compose.onNodeWithContentDescription("Navegação").performClick()
                compose.onNodeWithText("Navegação").assertIsDisplayed()
            } else compose.onNodeWithText("Navegação").performClick()
            val cases = listOf("known" to (100 to "Limite cadastrado"), "missing" to (null to "Limite indisponível"),
                "conflict" to (null to "Limites divergentes"), "conditional" to (null to "Limite condicionado"))
            for ((name, value) in cases) {
                main {
                    nav.value = NavUi(phase = NavPhaseUi.NAVIGATING, simulated = true,
                        current = ManeuverUi(0x15, "Vire à direita", "300 m", "Via de teste"),
                        next = ManeuverUi(dev.hunterlab.protocol.TripperIcons.STRAIGHT, "Siga em frente", "1,2 km"),
                        gps = GpsUi.OK, tripper = TripperUi(),
                        remainingDistanceText = "2,4 km", remainingTimeText = "6 min", arrivalText = "15:30",
                        radar = RadarUi(true, "Dados de teste", "Radar fixo a 280 m", 280,
                            kindText = "Radar fixo", limitKmh = value.first, limitStatus = value.second,
                            sourceText = "Fixture sintética · sem GPS ou BLE", directionUnknown = true))
                }
                compose.waitForIdle()
                instrumentation.waitForIdleSync(); Thread.sleep(350)
                // Copy only our real device window, excluding private notifications/other app overlays.
                val bitmap = Bitmap.createBitmap(activity.window.decorView.width, activity.window.decorView.height, Bitmap.Config.ARGB_8888)
                val copied = CountDownLatch(1)
                val copyResult = AtomicInteger(-1)
                main { PixelCopy.request(activity.window, bitmap, { code -> copyResult.set(code); copied.countDown() }, Handler(Looper.getMainLooper())) }
                assertTrue("Window capture timed out", copied.await(5, TimeUnit.SECONDS))
                assertEquals(PixelCopy.SUCCESS, copyResult.get())
                File(output, "$label-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                val distance = compose.onNodeWithText("280 m")
                val sign = compose.onNodeWithContentDescription(value.first?.let { "Limite cadastrado: $it quilômetros por hora" } ?: "Limite não confirmado")
                val stop = compose.onNodeWithText("Encerrar navegação")
                val status = compose.onNodeWithText(value.second)
                val visible = runCatching { distance.assertIsDisplayed(); sign.assertIsDisplayed(); status.assertIsDisplayed() }.isSuccess
                val d = runCatching { distance.fetchSemanticsNode().boundsInRoot }.getOrNull()
                val s = runCatching { sign.fetchSemanticsNode().boundsInRoot }.getOrNull()
                val b = runCatching { stop.fetchSemanticsNode().boundsInRoot }.getOrNull()
                val labelBounds = status.getUnclippedBoundsInRoot()
                val stopBounds = stop.getUnclippedBoundsInRoot()
                val unobstructed = visible && d != null && s != null &&
                    (b == null || maxOf(d.bottom, s.bottom) <= b.top) && labelBounds.bottom <= stopBounds.top
                results.put(JSONObject().put("case", name).put("critical_visible", unobstructed)
                    .put("distance_bounds", d.toString()).put("sign_bounds", s.toString())
                    .put("font_scale", context.resources.configuration.fontScale)
                    .put("screen_width_dp", context.resources.configuration.screenWidthDp))
            }
            save("$label-layout", JSONObject().put("fixtures", true).put("capture", "device_app_window_pixelcopy").put("cases", results))
            assertTrue("Radar distance/sign must remain visible: $results", (0 until results.length()).all { results.getJSONObject(it).getBoolean("critical_visible") })
        } finally { main { activity.recreate() } }
    }

    @Test fun unknownDirectionAndParallelStreetRemainDistinct() {
        val p = GeoPoint(-8.3, -35.97)
        val route = Polyline(listOf(GeoPoint(-8.31, p.lon), GeoPoint(-8.29, p.lon)))
        val camera = RadarCamera("FIXTURE-ONLY", p, RadarKind.FIXED, null, null)
        val matcher = RouteRadarMatcher()
        assertFalse(matcher.match(route, listOf(camera)).single().directionConfirmed)
        assertFalse(matcher.match(Polyline(route.points.reversed()), listOf(camera)).single().directionConfirmed)
        assertTrue(matcher.match(route, listOf(camera.copy(point = GeoPoint(p.lat, p.lon + .001)))).isEmpty())
        val conflict = RadarSpeedLimit.resolve(listOf(RadarLimitObservation("fixture-a", "40"), RadarLimitObservation("fixture-b", "60")))
        assertNull(conflict.kmh)
        save("route-fixtures", JSONObject().put("unknown_direction_preserved", true).put("parallel_street_100m_excluded", true)
            .put("conflicting_limit_hidden", true).put("fixtures_not_gps", true))
    }

    @Test fun reviewTtsOnDevice() {
        assumeTrue("Audible test requires explicit invocation", InstrumentationRegistry.getArguments().getString("allowAudibleTts") == "true")
        assertTrue("Enable the real app voice preference before this review", AppPreferences(context).radarVoiceEnabled)
        val report = JSONObject().put("fixtures", true).put("auditory_approval", "requires_human_confirmation")
        val music = context.getSystemService(AudioManager::class.java)
        report.put("media_volume", music.getStreamVolume(AudioManager.STREAM_MUSIC))
        val started = CountDownLatch(1); val done = CountDownLatch(1)
        val lifecycle = CopyOnWriteArrayList<String>(); val spoken = CopyOnWriteArrayList<String>()
        val available = AtomicBoolean(false); val prepared = CountDownLatch(1)
        lateinit var adapter: AndroidRadarSpeech
        main { adapter = AndroidRadarSpeech(context) }
        fun attachListener() {
            val field = AndroidRadarSpeech::class.java.getDeclaredField("engine").apply { isAccessible = true }
            val engine = field.get(adapter) as? TextToSpeech ?: return
            report.put("engine", engine.defaultEngine).put("voice_locale", engine.voice?.locale?.toLanguageTag())
                .put("voice_requires_network", engine.voice?.isNetworkConnectionRequired)
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) { lifecycle += "start"; started.countDown() }
                override fun onDone(id: String?) { lifecycle += "done"; done.countDown() }
                @Deprecated("Platform callback") override fun onError(id: String?) { lifecycle += "error"; done.countDown() }
                override fun onStop(id: String?, interrupted: Boolean) { lifecycle += "stop" }
            })
        }
        val port = object : RadarSpeechPort {
            override fun prepare(ready: (Boolean) -> Unit) = adapter.prepare { ok ->
                available.set(ok); if (ok) attachListener(); prepared.countDown(); ready(ok)
            }
            override fun speak(text: String) { spoken += text; adapter.speak(text) }
            override fun stop() = adapter.stop()
            override fun release() = adapter.release()
        }
        val voice = RadarVoice(port, SystemClock::elapsedRealtime)
        val event = RadarEvent.AlertStarted(RouteRadar("DEVICE-FIXTURE", 1000, RadarKind.FIXED, 60, "Fixture", simulated = true), 300, 700.0)
        try {
            main { voice.startSession(1, true); voice.configure(true, true); repeat(3) { voice.onEvent(event) } }
            assertTrue("TTS initialization timeout", prepared.await(12, TimeUnit.SECONDS))
            report.put("offline_pt_br_available", available.get())
            if (!available.get()) { save("tts-platform", report); fail("No installed offline pt-BR voice") }
            assertTrue("Engine did not start utterance", started.await(10, TimeUnit.SECONDS))
            assertTrue("Engine did not finish utterance", done.await(20, TimeUnit.SECONDS))
            main { voice.onEvent(event) }; Thread.sleep(1500)
            assertEquals("Same alert was repeated", 1, spoken.size)
            report.put("first_alert_requests", spoken.size).put("first_engine_callbacks", JSONArray(lifecycle.toList()))
            main { voice.endSession(); voice.startSession(2, true); voice.configure(true, true)
                voice.onEvent(event); voice.configure(false, false) }
            Thread.sleep(2200)
            assertEquals("Cancelled initialization spoke", 1, spoken.size)
            report.put("pending_cancel_prevented_speech", true)
            // A second audible alert is deliberately interrupted; human listening is still required.
            main { voice.startSession(3, true); voice.configure(true, true); voice.onEvent(event) }
            val deadline = SystemClock.elapsedRealtime() + 8_000
            while (lifecycle.count { it == "start" } < 2 && SystemClock.elapsedRealtime() < deadline) Thread.sleep(50)
            assertTrue("Second utterance did not start for cancellation review", lifecycle.count { it == "start" } >= 2)
            Thread.sleep(1000)
            main { voice.endSession() }
            val atStop = spoken.size; Thread.sleep(2500)
            assertEquals("Speech request appeared after stop", atStop, spoken.size)
            report.put("total_speech_requests", spoken.size).put("all_engine_callbacks", JSONArray(lifecycle.toList()))
                .put("no_requests_after_stop", true)
            save("tts-platform", report)
        } finally { main { voice.endSession() }; save("tts-platform", report) }
    }
}
