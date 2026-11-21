package dev.hunterlab.app.ui.simulation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.hunterlab.app.ui.components.BulletText
import dev.hunterlab.app.ui.components.ConfidenceBadge
import dev.hunterlab.app.ui.components.HunterCard
import dev.hunterlab.app.ui.components.Pill
import dev.hunterlab.app.ui.theme.HunterColors
import dev.hunterlab.app.ui.theme.HunterIcons
import dev.hunterlab.app.ui.tripper.TripperDisplay
import dev.hunterlab.core.display.ComposedFrame
import dev.hunterlab.core.nav.NavigationStatus
import dev.hunterlab.protocol.DayPeriod
import java.time.LocalTime

/**
 * Celular: prévia compacta fixa no topo + controles roláveis (a prévia fica sempre visível).
 * Tela larga (≥ 720 dp): prévia à esquerda, controles à direita.
 */
@Composable
fun SimulatorLayout(
    contentPadding: PaddingValues,
    header: @Composable (compact: Boolean) -> Unit,
    controls: LazyListScope.() -> Unit,
) {
    BoxWithConstraints(Modifier.padding(top = contentPadding.calculateTopPadding())) {
        val listPadding = PaddingValues(16.dp, 12.dp, 16.dp, 24.dp + contentPadding.calculateBottomPadding())
        if (maxWidth >= 720.dp) {
            Row(Modifier.fillMaxHeight()) {
                Column(
                    Modifier.weight(0.42f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) { header(false) }
                LazyColumn(Modifier.weight(0.58f), contentPadding = listPadding, verticalArrangement = Arrangement.spacedBy(12.dp), content = controls)
            }
        } else {
            Column {
                Column(
                    Modifier.fillMaxWidth().background(HunterColors.Background).padding(horizontal = 16.dp, vertical = 8.dp),
                ) { header(true) }
                Column(Modifier.fillMaxWidth().height(1.dp).background(HunterColors.Outline)) {}
                LazyColumn(Modifier.weight(1f), contentPadding = listPadding, verticalArrangement = Arrangement.spacedBy(12.dp), content = controls)
            }
        }
    }
}

@Composable
fun FramePreviewHeader(frame: ComposedFrame, status: NavigationStatus, now: LocalTime, compact: Boolean, extra: (@Composable () -> Unit)? = null) {
    if (compact) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TripperDisplay(frame.preview, now, Modifier.width(158.dp), showCaption = false)
            Column(Modifier.padding(start = 14.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusPills(frame, status)
                extra?.invoke()
                Text("Prévia aproximada", style = MaterialTheme.typography.labelSmall, color = HunterColors.TextMuted)
            }
        }
    } else {
        HunterCard {
            TripperDisplay(frame.preview, now, Modifier.fillMaxWidth())
            StatusPills(frame, status)
            extra?.invoke()
            FrameNotes(frame)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusPills(frame: ComposedFrame, status: NavigationStatus) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Pill(status.label, if (status == NavigationStatus.IDLE) HunterColors.TextSecondary else HunterColors.Amber)
        ConfidenceBadge(frame.confidence)
        Pill(
            frame.period.label,
            HunterColors.Info,
            icon = if (frame.period == DayPeriod.NIGHT) HunterIcons.Moon else HunterIcons.Sun,
        )
        if (frame.radarOnTripper) Pill("Radar", HunterColors.Amber, icon = HunterIcons.Radar)
    }
}

@Composable
fun FrameNotes(frame: ComposedFrame) {
    if (frame.notes.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { frame.notes.forEach { BulletText(it) } }
}

fun formatKm(meters: Double): String {
    val tenths = (meters / 100).toInt()
    return "${tenths / 10},${tenths % 10} km"
}
