package dev.hunterlab.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Ícones próprios, todos em traço de 1,8 sobre grade 24×24, para manter um desenho consistente
 * sem depender da biblioteca estendida de ícones.
 */
object HunterIcons {
    val Home by lazy { icon("home", "M3 11l9-8 9 8", "M5 10v11h5v-7h4v7h5V10") }
    val Appearance by lazy { icon("appearance", "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18", "M12 3v18") }
    val Location by lazy { icon("location", "M12 22s7-7 7-13A7 7 0 0 0 5 9c0 6 7 13 7 13z", "M12 6a3 3 0 1 0 0 6a3 3 0 1 0 0-6") }
    private fun icon(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach { d ->
                addPath(
                    pathData = PathParser().parsePathString(d).toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.White),
                    strokeLineWidth = 1.8f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    val Bluetooth by lazy { icon("bluetooth", "M7 7l10 10-5 5V2l5 5L7 17") }
    val Navigation by lazy { icon("navigation", "M6 20v-7a4 4 0 0 1 4-4h9", "M15 5l4 4-4 4") }
    val Radar by lazy {
        icon(
            "radar",
            "M21 12a9 9 0 1 1-9-9",
            "M16.5 12a4.5 4.5 0 1 1-4.5-4.5",
            "M12 12l6-6",
            "M12 11.2a0.8 0.8 0 1 0 0 1.6a0.8 0.8 0 1 0 0-1.6",
        )
    }
    val Playground by lazy {
        icon("playground", "M4 4h6v6H4z", "M14 4h6v6h-6z", "M4 14h6v6H4z", "M14 17a3 3 0 1 0 6 0a3 3 0 1 0-6 0")
    }
    val Search by lazy { icon("search", "M11 4a7 7 0 1 0 0 14a7 7 0 1 0 0-14", "M20 20l-4-4") }
    val Stop by lazy { icon("stop", "M7 7h10v10H7z") }
    val Back by lazy { icon("back", "M15 5l-7 7 7 7") }
    val ChevronRight by lazy { icon("chevron", "M9 5l7 7-7 7") }
    val ChevronDown by lazy { icon("chevron_down", "M5 9l7 7 7-7") }
    val Warning by lazy { icon("warning", "M12 3l10 18H2z", "M12 10v4", "M12 17.5v.5") }
    val Info by lazy { icon("info", "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18", "M12 11v6", "M12 7.5v.5") }
    val Check by lazy { icon("check", "M5 12.5l4.5 4.5L19 7") }
    val Close by lazy { icon("close", "M6 6l12 12", "M18 6L6 18") }
    val Play by lazy { icon("play", "M8 5v14l11-7z") }
    val Pause by lazy { icon("pause", "M8 5v14", "M16 5v14") }
    val Reset by lazy { icon("reset", "M4 12a8 8 0 1 0 2.3-5.6", "M4 4v5h5") }
    val Sun by lazy {
        icon(
            "sun", "M12 8a4 4 0 1 0 0 8a4 4 0 1 0 0-8",
            "M12 2v2", "M12 20v2", "M2 12h2", "M20 12h2", "M4.9 4.9l1.4 1.4", "M17.7 17.7l1.4 1.4", "M4.9 19.1l1.4-1.4", "M17.7 6.3l1.4-1.4",
        )
    }
    val Moon by lazy { icon("moon", "M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z") }
    val Auto by lazy { icon("auto", "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18", "M12 3v18", "M12 7h4", "M12 11h6", "M12 15h5") }
    val Copy by lazy { icon("copy", "M9 9h11v11H9z", "M5 15H4V4h11v1") }
    val Share by lazy {
        icon(
            "share", "M18 3a3 3 0 1 0 0 6a3 3 0 1 0 0-6", "M6 9a3 3 0 1 0 0 6a3 3 0 1 0 0-6",
            "M18 15a3 3 0 1 0 0 6a3 3 0 1 0 0-6", "M8.6 13.5l6.8 4", "M15.4 6.5l-6.8 4",
        )
    }
    val Link by lazy {
        icon("link", "M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1", "M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1")
    }
    val Unlink by lazy { icon("unlink", "M15.7 14l3-3a4 4 0 0 0-5.7-5.7l-1 1", "M8.3 10l-3 3a4 4 0 0 0 5.7 5.7l1-1", "M4 4l16 16") }
    val Lock by lazy { icon("lock", "M6 11h12v9H6z", "M8.5 11V8a3.5 3.5 0 0 1 7 0v3") }
    val Plus by lazy { icon("plus", "M12 5v14", "M5 12h14") }
    val Trash by lazy { icon("trash", "M5 7h14", "M10 7V4h4v3", "M7 7l1 13h8l1-13") }
    val Clock by lazy { icon("clock", "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18", "M12 7v5l3 2") }
    val Route by lazy { icon("route", "M6 19a2 2 0 1 0 0-4a2 2 0 1 0 0 4", "M18 5a2 2 0 1 0 0 4a2 2 0 1 0 0-4", "M8 17h7a3 3 0 0 0 0-6H9a3 3 0 0 1 0-6h7") }
    val Settings by lazy { icon("settings", "M4 7h10", "M18 7h2", "M4 17h4", "M12 17h8", "M16 5v4", "M10 15v4") }
}
