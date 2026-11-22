package dev.hunterlab.core.radar

/** Async platform boundary: preparing/speaking must not block the navigation thread. */
interface RadarSpeechPort {
    fun prepare(ready: (Boolean) -> Unit)
    fun speak(text: String)
    fun stop()
    fun release()
}

enum class RadarVoiceStatus { OFF, READY, PREPARING, UNAVAILABLE }

/** Optional, one announcement per alert/pass. A new route calculation does not reset this session. */
class RadarVoice(private val output: RadarSpeechPort, private val nowMs: () -> Long) {
    var status: RadarVoiceStatus = RadarVoiceStatus.OFF
        private set
    private var session: Long? = null
    private var enabled = false
    private var active = false
    private var simulation = false
    private var generation = 0L
    private var preparing = false
    private var lastNoticeAt: Long? = null
    private val announced = mutableSetOf<String>()

    fun startSession(id: Long, simulated: Boolean) {
        if (session == id) return
        endSession()
        session = id
        simulation = simulated
    }

    fun configure(enabled: Boolean, navigatingWithGoodGps: Boolean) {
        if (this.enabled && !enabled || active && !navigatingWithGoodGps) cancel()
        this.enabled = enabled
        active = navigatingWithGoodGps && session != null
        if (!enabled) status = RadarVoiceStatus.OFF
    }

    fun onEvent(event: RadarEvent) {
        if (!enabled || !active || session == null || event !is RadarEvent.AlertStarted || event.distanceM < 0) return
        val time = nowMs()
        if (event.radar.id in announced || preparing || lastNoticeAt?.let { time-it < MIN_INTERVAL_MS } == true) return
        announced += event.radar.id
        lastNoticeAt = time
        val token = generation
        preparing = true
        status = RadarVoiceStatus.PREPARING
        try {
            output.prepare { ready ->
                if (token != generation || !preparing || !enabled || !active || session == null) return@prepare
                preparing = false
                status = if (ready) RadarVoiceStatus.READY else RadarVoiceStatus.UNAVAILABLE
                // No late speech after a slow engine initialization or a navigation cancellation.
                if (!ready || nowMs()-time !in 0..MAX_PENDING_AGE_MS) return@prepare
                try { output.speak(message(event, simulation)) }
                catch (_: Exception) { status = RadarVoiceStatus.UNAVAILABLE }
            }
        } catch (_: Exception) { preparing = false; status = RadarVoiceStatus.UNAVAILABLE }
    }

    private fun cancel() {
        generation++
        preparing = false
        status = RadarVoiceStatus.OFF
        runCatching { output.stop() }
        runCatching { output.release() }
    }

    fun endSession() {
        cancel()
        session = null
        active = false
        announced.clear()
        lastNoticeAt = null
        status = RadarVoiceStatus.OFF
    }

    companion object {
        const val MIN_INTERVAL_MS = 8_000L
        const val MAX_PENDING_AGE_MS = 5_000L
        fun message(event: RadarEvent.AlertStarted, simulation: Boolean = false): String = buildString {
            if (simulation) append("Simulação. ")
            append(event.radar.kind.label).append(" a ").append(event.distanceM.coerceAtLeast(0)).append(" metros. ")
            val info = event.radar.limitInfo
            val limit = if (info == null) event.radar.speedLimitKmh else info.kmh
            if (limit != null) append("Limite cadastrado de ").append(limit).append(" quilômetros por hora.")
            else append("Limite não confirmado.")
        }
    }
}
