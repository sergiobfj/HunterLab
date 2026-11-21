package dev.hunterlab.core.display

import dev.hunterlab.core.nav.NavigationSnapshot
import dev.hunterlab.core.nav.NavigationStatus
import dev.hunterlab.core.radar.RadarKind
import dev.hunterlab.core.radar.RadarPhase
import dev.hunterlab.core.radar.SimulatedRadar
import dev.hunterlab.protocol.Confidence
import dev.hunterlab.protocol.DayPeriod
import dev.hunterlab.protocol.DecodedPacket
import dev.hunterlab.protocol.Maneuver
import dev.hunterlab.protocol.PacketDecoder
import dev.hunterlab.protocol.RadarDisplayMode
import dev.hunterlab.protocol.Side
import dev.hunterlab.protocol.TripperScreen
import dev.hunterlab.protocol.TurnSharpness
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalTime

class ScreenComposerTest {

    private val night = DisplaySettings(dayNightMode = DayNightMode.NIGHT)
    private val noon = LocalTime.NOON
    private val radar = SimulatedRadar("T", 1000, RadarKind.FIXED, 60, "teste")

    private fun nav(
        status: NavigationStatus = NavigationStatus.NAVIGATING,
        maneuver: Maneuver = Maneuver.Turn(Side.LEFT),
        distance: Int? = 167,
        next: Maneuver? = null,
        total: Int? = 2542,
        time: Int? = 8,
    ) = NavigationSnapshot(status, maneuver, distance, next, total, time, sourceId = "t", simulated = true)

    @Test
    fun `sem rota nao gera pacote de tela`() {
        val f = ScreenComposer.compose(nav(NavigationStatus.IDLE), RadarPhase.Clear, night, noon)
        assertEquals(TripperScreen.NativeClock, f.screen)
        assertNull(f.packet)
        assertEquals(PreviewKind.NATIVE_CLOCK, f.preview.kind)
    }

    @Test
    fun `navegacao de ponta a ponta reproduz captura REAL`() {
        val f = ScreenComposer.compose(nav(), RadarPhase.Clear, night, noon)
        assertEquals("10 11 14 00 A7 01 01 FF FF FF 41 00 19 02 00 00 00 00 19 1A", f.packet!!.toHex())
        assertEquals("167 m", f.preview.distanceText)
        assertEquals("2,5 km", f.preview.bottomText)
        assertTrue(f.preview.night)
    }

    @Test
    fun `radar em alerta sobrepoe no modo compativel reproduzindo captura REAL`() {
        val f = ScreenComposer.compose(nav(distance = 174), RadarPhase.Alert(radar, 260), night, noon)
        assertEquals("10 11 14 00 AE 01 01 3C FF FF 41 01 04 01 00 00 00 00 3E E1", f.packet!!.toHex())
        assertTrue(f.radarOnTripper)
        assertTrue(f.preview.radarActive)
        assertTrue(f.preview.bottomIsRadar)
    }

    @Test
    fun `radar nunca altera os campos essenciais da navegacao`() {
        val maneuvers = listOf(
            Maneuver.Straight, Maneuver.UTurn, Maneuver.Roundabout(3), Maneuver.Arrive(),
            Maneuver.Turn(Side.RIGHT, TurnSharpness.SHARP), Maneuver.Exit(Side.LEFT),
        )
        for (m in maneuvers) for (mode in RadarDisplayMode.entries) for (d in listOf(5, 80, 999, 4500)) {
            val settings = night.copy(radarMode = mode, intensityByDistance = true)
            val base = ScreenComposer.compose(nav(maneuver = m, distance = d), RadarPhase.Clear, settings, noon).packet!!
            val withRadar = ScreenComposer.compose(nav(maneuver = m, distance = d), RadarPhase.Alert(radar, 150), settings, noon).packet!!
            for (i in 0..6) assertEquals(base[i], withRadar[i], "$m/$mode/$d m: byte [$i]")
        }
    }

    @Test
    fun `telas de maior prioridade suprimem o radar`() {
        val alert = RadarPhase.Alert(radar, 120)
        for (status in listOf(NavigationStatus.RECALCULATING, NavigationStatus.ROUTE_STARTED, NavigationStatus.ARRIVED)) {
            val f = ScreenComposer.compose(nav(status), alert, night, noon)
            assertFalse(f.radarOnTripper, status.name)
            assertTrue(f.notes.any { "suprimido" in it }, status.name)
        }
        val recalc = ScreenComposer.compose(nav(NavigationStatus.RECALCULATING), alert, night, noon)
        assertEquals(TripperScreen.Recalculating(DayPeriod.NIGHT), recalc.screen)
    }

    @Test
    fun `radar sem rota aparece so no app`() {
        val f = ScreenComposer.compose(nav(NavigationStatus.IDLE), RadarPhase.Alert(radar, 100), night, noon)
        assertNull(f.packet)
        assertFalse(f.radarOnTripper)
        assertTrue(f.notes.any { "só no app" in it })
    }

    @Test
    fun `radar detectado fora do raio de alerta nao vai ao Tripper`() {
        val f = ScreenComposer.compose(nav(), RadarPhase.Detected(radar, 600), night, noon)
        assertFalse(f.radarOnTripper)
        assertEquals(0xFF, f.packet!![7])
    }

    @Test
    fun `radar desligado nas configuracoes nao sobrepoe`() {
        val f = ScreenComposer.compose(nav(), RadarPhase.Alert(radar, 100), night.copy(radarOnTripper = false), noon)
        assertFalse(f.radarOnTripper)
        assertEquals(0xFF, f.packet!![7])
    }

    @Test
    fun `chegada mostra destino a 0 m`() {
        val f = ScreenComposer.compose(nav(NavigationStatus.ARRIVED, Maneuver.Arrive(), 0), RadarPhase.Clear, night, noon)
        val d = PacketDecoder.decode(f.packet!!) as DecodedPacket.Navigation
        assertEquals(0x00, d.icon)
        assertEquals(0, d.distance!!.value)
    }

    @Test
    fun `alternancia dia e noite no modo automatico`() {
        val auto = DisplaySettings(dayNightMode = DayNightMode.AUTO)
        assertEquals(0, ScreenComposer.compose(nav(), RadarPhase.Clear, auto, LocalTime.of(10, 0)).packet!![6] and 1)
        assertEquals(1, ScreenComposer.compose(nav(), RadarPhase.Clear, auto, LocalTime.of(20, 0)).packet!![6] and 1)
        val forcedDay = DisplaySettings(dayNightMode = DayNightMode.DAY)
        assertEquals(DayPeriod.DAY, ScreenComposer.compose(nav(), RadarPhase.Clear, forcedDay, LocalTime.of(23, 0)).period)
    }

    @Test
    fun `modos de rodape`() {
        val at17 = LocalTime.of(17, 0)
        fun bottom(mode: BottomLineMode) =
            ScreenComposer.compose(nav(time = 28), RadarPhase.Clear, night.copy(bottomLineMode = mode), at17).packet!!
                .let { listOf(it[11], it[12], it[13]) }
        assertEquals(listOf(0x00, 0x19, 0x02), bottom(BottomLineMode.TOTAL_DISTANCE))
        assertEquals(listOf(0x00, 28, 0x00), bottom(BottomLineMode.TIME_REMAINING))
        assertEquals(listOf(0x11, 0x1C, 0x00), bottom(BottomLineMode.ARRIVAL_24H))
        assertEquals(listOf(0x85, 0x1C, 0x00), bottom(BottomLineMode.ARRIVAL_12H))
        val unknown = ScreenComposer.compose(nav(total = null), RadarPhase.Clear, night, noon).packet!!
        assertEquals(listOf(0xFF, 0xFF, 0xFF), listOf(unknown[11], unknown[12], unknown[13]))
    }

    @Test
    fun `confianca reflete a parte mais fraca`() {
        assertEquals(Confidence.CONFIRMED, ScreenComposer.compose(nav(maneuver = Maneuver.Straight), RadarPhase.Clear, night, noon).confidence)
        assertEquals(Confidence.LIKELY, ScreenComposer.compose(nav(), RadarPhase.Clear, night, noon).confidence)
        val exp = night.copy(radarMode = RadarDisplayMode.EXPERIMENTAL)
        assertEquals(
            Confidence.UNVERIFIED,
            ScreenComposer.compose(nav(maneuver = Maneuver.Straight), RadarPhase.Alert(radar, 50), exp, noon).confidence,
        )
    }

    @Test
    fun `previa e pacote sempre coerentes`() {
        val f = ScreenComposer.compose(nav(next = Maneuver.Roundabout(2)), RadarPhase.Clear, night, noon)
        val d = PacketDecoder.decode(f.packet!!) as DecodedPacket.Navigation
        assertEquals(d.icon, f.preview.largeIcon!!.code)
        assertEquals(d.nextIcon, f.preview.smallIcon!!.code)
        assertEquals(d.night, f.preview.night)
        assertEquals(d.distance!!.format(), f.preview.distanceText)
    }

    @Test
    fun `agenda dia e noite`() {
        val s = DayNightSchedule()
        assertEquals(DayPeriod.DAY, s.periodAt(LocalTime.of(17, 59)))
        assertEquals(DayPeriod.NIGHT, s.periodAt(LocalTime.of(18, 0)))
        assertEquals(DayPeriod.NIGHT, s.periodAt(LocalTime.MIDNIGHT))
        assertEquals(DayPeriod.NIGHT, s.periodAt(LocalTime.of(5, 59)))
        assertEquals(DayPeriod.DAY, s.periodAt(LocalTime.of(6, 0)))
        val sameDay = DayNightSchedule(nightStarts = LocalTime.of(1, 0), dayStarts = LocalTime.of(5, 0))
        assertEquals(DayPeriod.NIGHT, sameDay.periodAt(LocalTime.of(3, 0)))
        assertEquals(DayPeriod.DAY, sameDay.periodAt(LocalTime.of(23, 0)))
        assertThrows<IllegalArgumentException> { DayNightSchedule(LocalTime.NOON, LocalTime.NOON) }
    }

    @Test
    fun `formatacao do rodape na previa`() {
        assertEquals("0h08", PreviewMapper.formatBottomLine(dev.hunterlab.protocol.BottomLine.TimeRemaining(8)))
        assertEquals("12:15 PM", PreviewMapper.formatBottomLine(dev.hunterlab.protocol.BottomLine.ArrivalTime(12, 15, dev.hunterlab.protocol.ClockFormat.H12)))
        assertEquals("12:05 AM", PreviewMapper.formatBottomLine(dev.hunterlab.protocol.BottomLine.ArrivalTime(0, 5, dev.hunterlab.protocol.ClockFormat.H12)))
        assertEquals("07:30", PreviewMapper.formatBottomLine(dev.hunterlab.protocol.BottomLine.ArrivalTime(7, 30, dev.hunterlab.protocol.ClockFormat.H24)))
        assertNull(PreviewMapper.formatBottomLine(dev.hunterlab.protocol.BottomLine.None))
    }
}
