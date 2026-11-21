package dev.hunterlab.app.ui.tripper

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import dev.hunterlab.protocol.IconDepiction
import dev.hunterlab.protocol.Side
import kotlin.math.cos
import kotlin.math.sin

/** Desenha [content] dentro de um quadrado de lado [box] centrado em [center]. */
fun DrawScope.inBox(center: Offset, box: Float, content: DrawScope.() -> Unit) = inset(
    left = center.x - box / 2f,
    top = center.y - box / 2f,
    right = size.width - center.x - box / 2f,
    bottom = size.height - center.y - box / 2f,
    block = content,
)

/**
 * Desenha uma aproximação do símbolo. É uma interpretação do significado documentado do código —
 * **não** os pixels do display real, que não são conhecidos.
 */
fun DrawScope.drawDepiction(depiction: IconDepiction, ink: Color, textMeasurer: TextMeasurer) {
    GlyphPainter(this, ink, textMeasurer).draw(depiction)
}

private fun Offset.normalized(): Offset {
    val l = getDistance()
    return if (l == 0f) Offset(0f, -1f) else this / l
}

private class GlyphPainter(val scope: DrawScope, val ink: Color, val tm: TextMeasurer) {
    val s = scope.size.minDimension
    private val ox = (scope.size.width - s) / 2f
    private val oy = (scope.size.height - s) / 2f
    val w = s * 0.085f
    val faint = ink.copy(alpha = 0.28f)

    fun p(x: Float, y: Float) = Offset(ox + x * s, oy + y * s)
    fun stroke(width: Float = w) = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)

    fun line(a: Offset, b: Offset, c: Offset? = null, color: Color = ink, width: Float = w) =
        polyline(listOfNotNull(a, b, c), color, width)

    private fun polyline(pts: List<Offset>, color: Color, width: Float) {
        val path = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
        }
        scope.drawPath(path, color, style = stroke(width))
    }

    fun head(tip: Offset, dir: Offset, color: Color = ink, size: Float = s * 0.17f) {
        val d = dir.normalized()
        val n = Offset(-d.y, d.x)
        val base = tip - d * size
        val path = Path().apply {
            moveTo(tip.x, tip.y)
            lineTo(base.x + n.x * size * 0.62f, base.y + n.y * size * 0.62f)
            lineTo(base.x - n.x * size * 0.62f, base.y - n.y * size * 0.62f)
            close()
        }
        scope.drawPath(path, color)
    }

    /** Polilinha terminando em seta; o traço para na base da ponta. */
    fun arrow(a: Offset, b: Offset, c: Offset? = null, color: Color = ink, headSize: Float = s * 0.17f, width: Float = w) {
        val pts = listOfNotNull(a, b, c)
        val last = pts.last()
        val d = (last - pts[pts.size - 2]).normalized()
        val stop = last - d * (headSize * 0.7f)
        polyline(pts.dropLast(1) + stop, color, width)
        head(last, d, color, headSize)
    }

    fun text(t: String, center: Offset, size: Float, color: Color = ink) {
        val style = TextStyle(color = color, fontSize = with(scope) { size.toSp() }, fontWeight = FontWeight.Bold)
        val layout = tm.measure(AnnotatedString(t), style)
        scope.drawText(layout, topLeft = center - Offset(layout.size.width / 2f, layout.size.height / 2f))
    }

    private fun dirFromUp(angleDeg: Float, sx: Float): Offset {
        val rad = Math.toRadians(angleDeg.toDouble())
        return Offset((sin(rad) * sx).toFloat(), (-cos(rad)).toFloat())
    }

    private fun sx(side: Side) = if (side == Side.LEFT) -1f else 1f

    fun draw(d: IconDepiction) {
        when (d) {
            IconDepiction.Straight -> arrow(p(0.5f, 0.9f), p(0.5f, 0.1f))
            is IconDepiction.Turn -> turn(sx(d.side), d.angle.toFloat())
            is IconDepiction.UTurn -> uTurn(d.clockwise)
            is IconDepiction.Roundabout -> roundabout(d.clockwise, d.exit)
            is IconDepiction.Destination -> destination(d.side)
            is IconDepiction.Merge -> merge(d.side)
            is IconDepiction.Fork -> fork(sx(d.side))
            is IconDepiction.OffRamp -> offRamp(sx(d.side), d.angle)
            is IconDepiction.OnRamp -> {
                val x = sx(d.side)
                line(p(0.5f + 0.3f * x, 0.92f), p(0.5f + 0.3f * x, 0.08f), color = faint)
                turn(x, d.angle.toFloat())
            }
            is IconDepiction.SoftExit -> softExit(sx(d.side))
            is IconDepiction.KeepLane -> keepLane(d.side)
            is IconDepiction.Ferry -> if (d.train) train() else boat()
            IconDepiction.Recalculating -> recalculating()
            IconDepiction.Depart -> pin()
            IconDepiction.RouteStarted -> {
                scope.drawCircle(ink, s * 0.32f, p(0.5f, 0.5f), style = stroke())
                val tri = Path().apply {
                    val a = p(0.42f, 0.36f); val b = p(0.42f, 0.64f); val c = p(0.66f, 0.5f)
                    moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); close()
                }
                scope.drawPath(tri, ink)
            }
            IconDepiction.NoMobileData -> {
                listOf(0.26f to 0.68f, 0.42f to 0.56f, 0.58f to 0.42f, 0.74f to 0.28f).forEach { (x, top) ->
                    line(p(x, 0.8f), p(x, top), color = faint)
                }
                line(p(0.18f, 0.84f), p(0.82f, 0.18f))
            }
            IconDepiction.LowBattery -> {
                scope.drawRoundRect(ink, topLeft = p(0.16f, 0.34f), size = Size(s * 0.6f, s * 0.32f), style = stroke(w * 0.8f))
                scope.drawRect(ink, topLeft = p(0.78f, 0.44f), size = Size(s * 0.07f, s * 0.12f))
                scope.drawRect(ink, topLeft = p(0.22f, 0.40f), size = Size(s * 0.1f, s * 0.2f))
            }
            IconDepiction.Blank -> Unit
            IconDepiction.Unknown -> text("?", p(0.5f, 0.5f), s * 0.5f)
        }
    }

    private fun turn(sx: Float, angle: Float) {
        val rad = Math.toRadians(angle.toDouble())
        val stemX = 0.5f - sx * 0.14f * sin(rad).toFloat()
        val pivotY = when {
            angle >= 120f -> 0.38f
            angle <= 50f -> 0.56f
            else -> 0.5f
        }
        val pivot = p(stemX, pivotY)
        val len = s * if (angle >= 120f) 0.30f else 0.34f
        arrow(p(stemX, 0.9f), pivot, pivot + dirFromUp(angle, sx) * len)
    }

    private fun uTurn(clockwise: Boolean) {
        val m = if (clockwise) -1f else 1f // anti-horário: sobe pela direita, desce pela esquerda
        val start = p(0.5f + 0.15f * m, 0.9f)
        val top = p(0.5f + 0.15f * m, 0.42f)
        val end = p(0.5f - 0.15f * m, 0.6f)
        val path = Path().apply {
            moveTo(start.x, start.y)
            lineTo(top.x, top.y)
            arcTo(
                Rect(center = p(0.5f, 0.42f), radius = 0.15f * s),
                startAngleDegrees = if (m > 0) 0f else 180f,
                sweepAngleDegrees = if (m > 0) -180f else 180f,
                forceMoveTo = false,
            )
            lineTo(end.x, end.y)
        }
        scope.drawPath(path, ink, style = stroke())
        head(p(0.5f - 0.15f * m, 0.76f), Offset(0f, 1f))
    }

    private fun roundabout(clockwise: Boolean, exit: Int?) {
        val c = p(0.5f, 0.42f)
        val r = 0.17f * s
        val m = if (clockwise) -1f else 1f
        val arcSize = Size(2 * r, 2 * r)
        val topLeft = c - Offset(r, r)
        scope.drawCircle(faint, r, c, style = stroke(w * 0.8f))
        line(p(0.5f, 0.92f), Offset(c.x, c.y + r))
        if (exit == null) {
            scope.drawArc(ink, 90f, -150f * m, false, topLeft, arcSize, style = stroke(w * 0.8f))
            head(Offset(c.x + r * m, c.y - s * 0.02f), Offset(0f, -1f), size = s * 0.13f)
            return
        }
        val theta = (if (exit <= 4) 90f - (exit - 1) * 90f else 90f - (exit - 1) * 45f) * m
        var sweep = ((180f - theta * m) % 360f + 360f) % 360f
        if (sweep < 1f) sweep = 360f
        sweep = sweep.coerceAtMost(350f)
        scope.drawArc(ink, 90f, -sweep * m, false, topLeft, arcSize, style = stroke(w * 0.8f))
        val dir = dirFromUp(theta, 1f)
        arrow(c + dir * r, c + dir * (r + 0.24f * s), headSize = s * 0.15f)
        text(exit.toString(), c, s * 0.17f)
    }

    private fun destination(side: Side) {
        val (x, arrowFrom, arrowTo) = when (side) {
            Side.NONE -> Triple(0.38f, null, null)
            Side.LEFT -> Triple(0.26f, 0.84f, 0.42f)
            Side.RIGHT -> Triple(0.58f, 0.16f, 0.48f)
        }
        line(p(x, 0.88f), p(x, 0.14f))
        val flag = Path().apply {
            val a = p(x, 0.14f); val b = p(x + 0.34f, 0.25f); val c = p(x, 0.4f)
            moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); close()
        }
        scope.drawPath(flag, ink)
        if (arrowFrom != null && arrowTo != null) {
            arrow(p(arrowFrom, 0.8f), p(arrowTo, 0.8f), headSize = s * 0.13f, width = w * 0.8f)
        }
    }

    private fun merge(side: Side) {
        if (side == Side.NONE) {
            line(p(0.2f, 0.9f), p(0.5f, 0.52f), color = faint)
            line(p(0.8f, 0.9f), p(0.5f, 0.52f), color = faint)
            arrow(p(0.5f, 0.9f), p(0.5f, 0.1f))
            return
        }
        val x = sx(side)
        val mainX = 0.5f + 0.14f * x
        val fromX = 0.5f - 0.22f * x
        line(p(mainX, 0.92f), p(mainX, 0.45f), color = faint)
        val path = Path().apply {
            val a = p(fromX, 0.9f); val b = p(fromX, 0.62f); val c = p(mainX, 0.36f)
            moveTo(a.x, a.y); lineTo(b.x, b.y)
            quadraticTo(b.x, c.y, c.x, c.y)
            lineTo(c.x, p(0f, 0.26f).y)
        }
        scope.drawPath(path, ink, style = stroke())
        head(p(mainX, 0.1f), Offset(0f, -1f))
    }

    private fun fork(sx: Float) {
        val pivot = p(0.5f, 0.56f)
        line(p(0.5f, 0.9f), pivot)
        line(pivot, p(0.5f - 0.26f * sx, 0.18f), color = faint)
        arrow(pivot, p(0.5f + 0.26f * sx, 0.14f))
    }

    private fun offRamp(sx: Float, angle: Int) {
        val mainX = 0.5f - 0.1f * sx
        line(p(mainX, 0.92f), p(mainX, 0.1f), color = faint)
        val a = when {
            angle <= 30 -> 28f
            angle <= 45 -> 45f
            else -> 72f
        }
        val pivot = p(mainX, 0.56f)
        arrow(p(mainX, 0.9f), pivot, pivot + dirFromUp(a, sx) * (s * 0.4f))
    }

    private fun softExit(sx: Float) {
        line(p(0.42f, 0.6f), p(0.42f, 0.12f), color = faint)
        val endX = 0.42f + 0.3f * sx
        val path = Path().apply {
            val a = p(0.42f, 0.9f); val b = p(0.42f, 0.6f); val end = p(endX, 0.26f)
            moveTo(a.x, a.y); lineTo(b.x, b.y)
            cubicTo(b.x, p(0f, 0.44f).y, end.x, p(0f, 0.42f).y, end.x, end.y)
        }
        scope.drawPath(path, ink, style = stroke())
        head(p(endX, 0.1f), Offset(0f, -1f))
    }

    private fun keepLane(side: Side) {
        val lanes = listOf(0.24f, 0.5f, 0.76f)
        val highlighted = if (side == Side.LEFT) 0 else 2
        lanes.forEachIndexed { i, x ->
            val color = if (i == highlighted) ink else faint
            arrow(p(x, 0.86f), p(x, 0.18f), color = color, headSize = s * 0.14f, width = w * if (i == highlighted) 1f else 0.7f)
        }
    }

    private fun boat() {
        val hull = Path().apply {
            val a = p(0.16f, 0.58f); val b = p(0.84f, 0.58f); val c = p(0.72f, 0.74f); val d = p(0.28f, 0.74f)
            moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); lineTo(d.x, d.y); close()
        }
        scope.drawPath(hull, ink)
        scope.drawRect(ink, topLeft = p(0.36f, 0.4f), size = Size(s * 0.26f, s * 0.18f), style = stroke(w * 0.7f))
        line(p(0.12f, 0.86f), p(0.88f, 0.86f), width = w * 0.6f)
    }

    private fun train() {
        scope.drawRoundRect(
            ink, topLeft = p(0.26f, 0.16f), size = Size(s * 0.48f, s * 0.54f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.08f), style = stroke(w * 0.8f),
        )
        scope.drawRect(ink, topLeft = p(0.34f, 0.26f), size = Size(s * 0.32f, s * 0.16f))
        scope.drawCircle(ink, s * 0.05f, p(0.38f, 0.58f))
        scope.drawCircle(ink, s * 0.05f, p(0.62f, 0.58f))
        line(p(0.32f, 0.78f), p(0.2f, 0.9f), width = w * 0.6f)
        line(p(0.68f, 0.78f), p(0.8f, 0.9f), width = w * 0.6f)
    }

    private fun recalculating() {
        val c = p(0.5f, 0.5f)
        val r = s * 0.28f
        val start = -60f
        val sweep = 290f
        scope.drawArc(ink, start, sweep, false, c - Offset(r, r), Size(2 * r, 2 * r), style = stroke())
        val endRad = Math.toRadians((start + sweep).toDouble())
        val end = c + Offset((cos(endRad) * r).toFloat(), (sin(endRad) * r).toFloat())
        val tangent = Offset((-sin(endRad)).toFloat(), cos(endRad).toFloat())
        head(end + tangent * (s * 0.08f), tangent)
    }

    private fun pin() {
        val c = p(0.5f, 0.4f)
        val r = s * 0.24f
        val tip = p(0.5f, 0.9f)
        val path = Path().apply {
            moveTo(tip.x, tip.y)
            cubicTo(tip.x, tip.y, c.x - r, c.y + r * 0.9f, c.x - r, c.y)
            arcTo(Rect(center = c, radius = r), 180f, 180f, false)
            cubicTo(c.x + r, c.y + r * 0.9f, tip.x, tip.y, tip.x, tip.y)
            close()
        }
        scope.drawPath(path, ink, style = stroke(w * 0.85f))
        scope.drawCircle(ink, s * 0.08f, c)
    }
}
