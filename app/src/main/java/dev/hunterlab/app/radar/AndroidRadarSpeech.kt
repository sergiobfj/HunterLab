package dev.hunterlab.app.radar

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import dev.hunterlab.core.radar.RadarSpeechPort
import java.util.Locale

/** Lazy platform speech. Creating this adapter does not initialize an engine or speak. */
class AndroidRadarSpeech(context: Context) : RadarSpeechPort {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var engine: TextToSpeech? = null
    private var ready = false
    private var generation = 0L
    private val callbacks = mutableListOf<(Boolean) -> Unit>()

    override fun prepare(ready: (Boolean) -> Unit) {
        if (this.ready) { ready(true); return }
        callbacks += ready
        if (engine != null) return
        val token = ++generation
        try {
            engine = TextToSpeech(app) { result ->
                // Post ensures the engine field has been assigned even with an immediate callback.
                main.post {
                    if (token != generation) return@post
                    val tts = engine
                    val available = runCatching {
                        result == TextToSpeech.SUCCESS && tts != null &&
                            tts.setLanguage(Locale.forLanguageTag("pt-BR")) >= TextToSpeech.LANG_AVAILABLE &&
                            tts.setAudioAttributes(AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()) == TextToSpeech.SUCCESS
                    }.getOrDefault(false)
                    val offlineVoice = if (available) tts?.voices?.firstOrNull {
                        !it.isNetworkConnectionRequired &&
                            !it.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) && it.locale.language == "pt" && it.locale.country == "BR"
                    } else null
                    val offlineAvailable = available && offlineVoice != null && tts?.setVoice(offlineVoice) == TextToSpeech.SUCCESS
                    this.ready = offlineAvailable
                    val waiting = callbacks.toList(); callbacks.clear()
                    if (!offlineAvailable) { runCatching { tts?.shutdown() }; engine = null }
                    waiting.forEach { it(offlineAvailable) }
                }
            }
        } catch (_: RuntimeException) {
            this.ready = false
            val waiting = callbacks.toList(); callbacks.clear()
            waiting.forEach { it(false) }
        }
    }

    override fun speak(text: String) {
        check(ready)
        val code = engine?.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), "hunter-radar-$generation")
        check(code == TextToSpeech.SUCCESS) { "Voz indisponível" }
    }

    override fun stop() { runCatching { engine?.stop() } }

    override fun release() {
        generation++
        ready = false
        callbacks.clear()
        runCatching { engine?.stop() }
        runCatching { engine?.shutdown() }
        engine = null
    }
}
