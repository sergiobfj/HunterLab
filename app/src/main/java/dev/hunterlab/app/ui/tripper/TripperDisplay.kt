package dev.hunterlab.app.ui.tripper

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.hunterlab.app.ui.theme.HunterColors
import dev.hunterlab.core.display.PreviewKind
import dev.hunterlab.core.display.TripperPreview
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val HHMM = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Representação circular do Tripper. Disposição e desenho são **aproximados**: servem para
 * visualizar o conteúdo dos pacotes, não para reproduzir o display real.
 */
@Composable
fun TripperDisplay(
    preview: TripperPreview,
    clock: LocalTime,
    modifier: Modifier = Modifier,
    showCaption: Boolean = true,
) {
    val tm = rememberTextMeasurer()
    val description = describe(preview)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier
                .widthIn(max = 300.dp)
                .fillMaxWidth()
                .aspectRatio(1f)
                .semantics { contentDescription = description },
        ) { drawTripper(preview, clock, tm) }
        if (showCaption) {
            Text(
                "Prévia aproximada — não reproduz os pixels do display real",
                style = MaterialTheme.typography.bodySmall,
                color = HunterColors.TextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

private fun describe(p: TripperPreview): String = when (p.kind) {
    PreviewKind.NATIVE_CLOCK -> "Prévia do Tripper: relógio nativo"
    PreviewKind.COMPASS -> "Prévia do Tripper: bússola ${p.compass?.label}"
    PreviewKind.ROUTE_STARTED -> "Prévia do Tripper: rota iniciada"
    PreviewKind.RECALCULATING -> "Prévia do Tripper: recalculando"
    PreviewKind.NAVIGATION -> buildString {
        append("Prévia do Tripper: ${p.largeIcon?.name}, ${p.distanceText}")
        p.smallIcon?.let { append(", próxima: ${it.name}") }
        if (p.radarActive) append(", radar ativo")
        p.bottomText?.let { append(", rodapé $it") }
    }
}

private fun DrawScope.drawTripper(p: TripperPreview, clock: LocalTime, tm: TextMeasurer) {
    val outer = size.minDimension / 2f
    val c = center
    // Aro
    drawCircle(
        Brush.radialGradient(
            listOf(HunterColors.BezelOuter, HunterColors.BezelInner),
            center = c - Offset(outer * 0.35f, outer * 0.45f),
            radius = outer * 2f,
        ),
        outer, c,
    )
    drawCircle(Color.White.copy(alpha = 0.07f), outer * 0.975f, c, style = Stroke(outer * 0.012f))
    val r = outer * 0.86f
    val bg = if (p.night) HunterColors.DisplayNight else HunterColors.DisplayDay
    val ink = if (p.night) HunterColors.DisplayNightInk else HunterColors.DisplayDayInk
    val faint = ink.copy(alpha = 0.35f)
    drawCircle(bg, r, c)
    drawCircle(Color.Black.copy(alpha = 0.5f), r, c, style = Stroke(outer * 0.02f))

    when (p.kind) {
        PreviewKind.NATIVE_CLOCK -> {
            centeredText(tm, clock.format(HHMM), Offset(c.x, c.y - r * 0.06f), r * 0.36f, ink, FontWeight.SemiBold)
            centeredText(tm, "relógio do Pod", Offset(c.x, c.y + r * 0.36f), r * 0.1f, faint)
        }
        PreviewKind.COMPASS -> {
            val dir = p.compass
            rotate(dir?.degrees?.toFloat() ?: 0f, pivot = c) {
                val needle = Path().apply {
                    moveTo(c.x, c.y - r * 0.52f)
                    lineTo(c.x + r * 0.16f, c.y + r * 0.12f)
                    lineTo(c.x, c.y + r * 0.02f)
                    lineTo(c.x - r * 0.16f, c.y + r * 0.12f)
                    close()
                }
                drawPath(needle, ink)
            }
            centeredText(tm, dir?.label ?: "?", Offset(c.x, c.y + r * 0.56f), r * 0.2f, ink, FontWeight.Bold)
        }
        PreviewKind.ROUTE_STARTED, PreviewKind.RECALCULATING -> {
            p.largeIcon?.let { icon -> inBox(Offset(c.x, c.y - r * 0.1f), r * 0.86f) { drawDepiction(icon.depiction, ink, tm) } }
            val caption = if (p.kind == PreviewKind.ROUTE_STARTED) "rota iniciada" else "recalculando"
            centeredText(tm, caption, Offset(c.x, c.y + r * 0.58f), r * 0.11f, faint)
        }
        PreviewKind.NAVIGATION -> drawNavigation(p, c, r, ink, faint, tm)
    }
}

private fun DrawScope.drawNavigation(p: TripperPreview, c: Offset, r: Float, ink: Color, faint: Color, tm: TextMeasurer) {
    p.largeIcon?.let { icon -> inBox(Offset(c.x, c.y - r * 0.24f), r * 0.78f) { drawDepiction(icon.depiction, ink, tm) } }

    // Seta pequena (próxima manobra) no canto superior direito.
    val smallCenter = Offset(c.x + r * 0.52f, c.y - r * 0.5f)
    val box = r * 0.3f
    p.smallIcon?.let { icon ->
        val outline = if (p.radarActive) HunterColors.Amber else faint
        drawRoundRect(
            outline, topLeft = smallCenter - Offset(box * 0.62f, box * 0.62f), size = Size(box * 1.24f, box * 1.24f),
            cornerRadius = CornerRadius(box * 0.25f), style = Stroke(r * 0.012f),
        )
        inBox(smallCenter, box) { drawDepiction(icon.depiction, ink, tm) }
    }
    p.auxDistanceText?.let {
        centeredText(tm, "$it ?", Offset(smallCenter.x, smallCenter.y + box * 0.95f), r * 0.085f, HunterColors.Amber)
    }
    if (p.callOverlay) {
        val callCenter = Offset(c.x - r * 0.52f, c.y - r * 0.5f)
        drawRoundRect(ink, topLeft = callCenter - Offset(box * 0.62f, box * 0.42f), size = Size(box * 1.24f, box * 0.84f), cornerRadius = CornerRadius(box * 0.2f))
        centeredText(tm, "TEL", callCenter, r * 0.085f, if (ink == HunterColors.DisplayNightInk) HunterColors.DisplayNight else HunterColors.DisplayDay, FontWeight.Bold)
    }

    // Distância até a manobra: número grande + unidade.
    p.distanceText?.let { text ->
        val number = text.substringBefore(' ')
        val unit = text.substringAfter(' ', "")
        val annotated = buildAnnotatedString {
            withStyle(SpanStyle(fontSize = (r * 0.3f).toSp(), fontWeight = FontWeight.SemiBold)) { append(number) }
            withStyle(SpanStyle(fontSize = (r * 0.13f).toSp(), fontWeight = FontWeight.Medium)) { append(" $unit") }
        }
        val layout = tm.measure(annotated, TextStyle(color = ink, fontFeatureSettings = "tnum"))
        drawText(layout, topLeft = Offset(c.x - layout.size.width / 2f, c.y + r * 0.34f - layout.size.height / 2f))
    }
    drawLine(faint, Offset(c.x - r * 0.26f, c.y + r * 0.53f), Offset(c.x + r * 0.26f, c.y + r * 0.53f), strokeWidth = r * 0.01f)
    val bottomColor = if (p.bottomIsRadar) HunterColors.Amber else ink
    centeredText(tm, p.bottomText ?: "– – –", Offset(c.x, c.y + r * 0.67f), r * 0.12f, if (p.bottomText == null) faint else bottomColor)
}

private fun DrawScope.centeredText(
    tm: TextMeasurer,
    text: String,
    center: Offset,
    sizePx: Float,
    color: Color,
    weight: FontWeight = FontWeight.Medium,
) {
    val layout = tm.measure(
        AnnotatedString(text),
        TextStyle(color = color, fontSize = sizePx.toSp(), fontWeight = weight, fontFeatureSettings = "tnum"),
    )
    drawText(layout, topLeft = center - Offset(layout.size.width / 2f, layout.size.height / 2f))
}

/** Miniatura de um símbolo, usada no catálogo. */
@Composable
fun IconGlyph(depiction: dev.hunterlab.protocol.IconDepiction, ink: Color, modifier: Modifier = Modifier) {
    val tm = rememberTextMeasurer()
    Canvas(modifier) {
        if (depiction == dev.hunterlab.protocol.IconDepiction.Blank) {
            drawCircle(ink.copy(alpha = 0.25f), size.minDimension * 0.3f, style = Stroke(size.minDimension * 0.04f))
        } else {
            drawDepiction(depiction, ink, tm)
        }
    }
}
