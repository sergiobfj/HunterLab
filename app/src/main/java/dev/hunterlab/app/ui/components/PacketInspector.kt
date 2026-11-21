package dev.hunterlab.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hunterlab.app.ui.theme.HunterColors
import dev.hunterlab.app.ui.theme.HunterIcons
import dev.hunterlab.app.ui.theme.MonoDigits
import dev.hunterlab.protocol.Hex
import dev.hunterlab.protocol.TripperPacket

private enum class ByteGroup(val label: String, val color: Color) {
    CMD("Comando", Color(0xFF8C939B)),
    ICON("Ícone [2]", HunterColors.Amber),
    DIST("Distância [3–5]", HunterColors.Info),
    NIGHT("Noite [6]", Color(0xFFB5A3D6)),
    NEXT("Seta [7]", HunterColors.AmberSoft),
    AUX("Radar exp. [8–9]", HunterColors.Unverified),
    MARK("Marcador [10]", HunterColors.TextMuted),
    BOTTOM("Rodapé [11–13]", HunterColors.Confirmed),
    DIR("Direção [14]", HunterColors.Info),
    DATA("Dados", Color(0xFF9AA3AD)),
    PAD("Zeros", HunterColors.OutlineStrong),
    CRC("CRC [18–19]", Color(0xFFC9C3B8)),
}

private fun groupsFor(p: TripperPacket): List<ByteGroup> {
    val nav = p[0] == 0x10 && p[1] == 0x11
    return (0 until TripperPacket.SIZE).map { i ->
        when {
            i >= 18 -> ByteGroup.CRC
            nav && p[10] == 0x41 -> when (i) {
                0, 1 -> ByteGroup.CMD
                2 -> ByteGroup.ICON
                3, 4, 5 -> ByteGroup.DIST
                6 -> ByteGroup.NIGHT
                7 -> ByteGroup.NEXT
                8, 9 -> ByteGroup.AUX
                10 -> ByteGroup.MARK
                11, 12, 13 -> ByteGroup.BOTTOM
                else -> ByteGroup.PAD
            }
            nav -> when (i) {
                0, 1 -> ByteGroup.CMD
                2 -> ByteGroup.ICON
                6 -> ByteGroup.NIGHT
                14 -> if (p[2] == 0x41) ByteGroup.DIR else ByteGroup.DATA
                else -> if (p[i] == 0) ByteGroup.PAD else ByteGroup.DATA
            }
            i == 0 -> ByteGroup.CMD
            else -> if (p[i] == 0) ByteGroup.PAD else ByteGroup.DATA
        }
    }
}

/** Mostra os 20 bytes, agrupados por função. O pacote existe só em memória. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PacketInspector(packet: TripperPacket?, emptyText: String = "Nenhum pacote de tela: o Pod mostraria o próprio relógio.") {
    val context = LocalContext.current
    CollapsibleCard(
        title = "Pacote em memória",
        summary = if (packet == null) "Sem pacote de tela" else "20 bytes · não transmitido · toque para ver",
    ) {
        if (packet == null) {
            Text(emptyText, style = MaterialTheme.typography.bodySmall, color = HunterColors.TextSecondary)
            return@CollapsibleCard
        }
        IconButton(onClick = { copy(context, packet.toHex()) }) {
            Icon(HunterIcons.Copy, contentDescription = "Copiar hex", tint = HunterColors.TextSecondary)
        }
        val groups = groupsFor(packet)
        for (row in 0..1) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                for (col in 0 until 10) {
                    val i = row * 10 + col
                    val g = groups[i]
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(g.color.copy(alpha = 0.14f))
                            .padding(vertical = 5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("$i", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = HunterColors.TextMuted)
                        Text(Hex.byte(packet[i]), style = MonoDigits.copy(fontSize = 12.sp), color = g.color, textAlign = TextAlign.Center)
                    }
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            groups.distinct().forEach { g ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Dot(g.color, 7.dp)
                    Spacer(Modifier.width(5.dp))
                    Text(g.label, style = MaterialTheme.typography.labelSmall, color = HunterColors.TextSecondary)
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        SelectionContainer {
            Text(packet.toHex(), style = MonoDigits.copy(fontSize = 12.sp), color = HunterColors.TextPrimary, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

fun copy(context: Context, text: String, label: String = "Hunter Lab") {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "Copiado", Toast.LENGTH_SHORT).show()
}

@Composable
fun MiniHex(bytes: String, color: Color = HunterColors.TextSecondary) {
    Text(bytes, style = MonoDigits.copy(fontSize = 11.sp), color = color, modifier = Modifier.padding(top = 2.dp))
}
