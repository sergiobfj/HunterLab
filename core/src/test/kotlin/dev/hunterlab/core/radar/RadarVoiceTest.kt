package dev.hunterlab.core.radar

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RadarVoiceTest {
    private class Speaker : RadarSpeechPort {
        val callbacks=mutableListOf<(Boolean)->Unit>(); val words=mutableListOf<String>(); var releases=0
        override fun prepare(ready:(Boolean)->Unit) { callbacks+=ready }
        override fun speak(text:String) { words+=text }
        override fun stop() = Unit
        override fun release() { releases++ }
    }
    private var now=100_000L
    private val speaker=Speaker()
    private val voice=RadarVoice(speaker) { now }
    private fun alert(id:String="a",limit:RadarSpeedLimit=RadarSpeedLimit.recorded(60)) = RadarEvent.AlertStarted(
        RouteRadar(id,1_000,RadarKind.FIXED,limit.kmh,"fixture",simulated=false,limitInfo=limit),300,700.0)
    private fun activate(simulation:Boolean=false) { voice.startSession(1,simulation); voice.configure(true,true) }
    @Test fun `default off no session bad GPS and non alerts never initialize speech`() {
        voice.onEvent(alert()); voice.startSession(1,false); voice.onEvent(alert())
        voice.configure(true,false); voice.onEvent(alert()); voice.configure(true,true)
        voice.onEvent(RadarEvent.Detected(alert().radar,700,300.0))
        assertTrue(speaker.callbacks.isEmpty()); assertTrue(speaker.words.isEmpty())
    }
    @Test fun `prepare is asynchronous repeated alert speaks at most once per session`() {
        activate(); voice.onEvent(alert()); voice.onEvent(alert())
        assertEquals(1,speaker.callbacks.size); assertTrue(speaker.words.isEmpty())
        speaker.callbacks.single()(true)
        assertEquals(listOf("Radar fixo a 300 metros. Limite cadastrado de 60 quilômetros por hora."),speaker.words)
        now+=60_000; voice.onEvent(alert()); assertEquals(1,speaker.callbacks.size)
    }
    @Test fun `disable GPS loss end and new session invalidate late callbacks`() {
        for (cancel in listOf<(RadarVoice)->Unit>({it.configure(false,true)},{it.configure(true,false)},{it.endSession()},{it.startSession(2,false)})) {
            val s=Speaker(); val v=RadarVoice(s) { now };v.startSession(1,false);v.configure(true,true);v.onEvent(alert());cancel(v)
            s.callbacks.single()(true); assertTrue(s.words.isEmpty()); assertTrue(s.releases>0)
        }
    }
    @Test fun `late initialization and unavailable voices are silent and do not crash navigation`() {
        activate(); voice.onEvent(alert());now+=5_001;speaker.callbacks.single()(true);assertTrue(speaker.words.isEmpty())
        now+=8_000;voice.onEvent(alert("b"));speaker.callbacks.last()(false)
        assertEquals(RadarVoiceStatus.UNAVAILABLE,voice.status);assertTrue(speaker.words.isEmpty())
    }
    @Test fun `cooldown prevents back to back notices while distinct later alerts remain possible`() {
        activate();voice.onEvent(alert());speaker.callbacks.single()(true)
        now+=1_000;voice.onEvent(alert("b"));assertEquals(1,speaker.callbacks.size)
        now+=8_000;voice.onEvent(alert("b"));speaker.callbacks.last()(true);assertEquals(2,speaker.words.size)
    }
    @Test fun `simulation is explicit and unknown conditional conflicting limits never spoken as known`() {
        val limits=listOf(RadarSpeedLimit.recorded(null),RadarSpeedLimit.resolve(listOf(RadarLimitObservation("a","40"),RadarLimitObservation("b","60"))),
            RadarSpeedLimit.resolve(listOf(RadarLimitObservation("a","60","40 @ (wet)"))))
        limits.forEach { limit -> val text=RadarVoice.message(alert(limit=limit),true)
            assertTrue(text.startsWith("Simulação."));assertTrue(text.contains("Limite não confirmado"));assertFalse(text.contains("quilômetros por hora")) }
    }
    @Test fun `speech exceptions never escape into route or BLE logic`() {
        val broken=object:RadarSpeechPort { override fun prepare(ready:(Boolean)->Unit) { ready(true) };override fun speak(text:String) { error("fixture") };override fun stop() { error("fixture") };override fun release() { error("fixture") } }
        val v=RadarVoice(broken) { now };v.startSession(1,false);v.configure(true,true)
        assertDoesNotThrow { v.onEvent(alert()) };assertEquals(RadarVoiceStatus.UNAVAILABLE,v.status);assertDoesNotThrow { v.endSession() }
    }
    @Test fun `a duplicated platform initialization callback cannot repeat an utterance`() {
        activate();voice.onEvent(alert());speaker.callbacks.single()(true);speaker.callbacks.single()(true)
        assertEquals(1,speaker.words.size)
    }
    @Test fun `a reroute does not reset announcement identity but a new navigation may announce again`() {
        activate();voice.onEvent(alert());speaker.callbacks.last()(true);voice.startSession(1,false);now+=60_000;voice.onEvent(alert())
        assertEquals(1,speaker.words.size)
        voice.startSession(2,false);voice.configure(true,true);voice.onEvent(alert());speaker.callbacks.last()(true);assertEquals(2,speaker.words.size)
    }
}
