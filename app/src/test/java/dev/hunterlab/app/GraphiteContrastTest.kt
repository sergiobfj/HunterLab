package dev.hunterlab.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import dev.hunterlab.app.ui.theme.HunterColors as C
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphiteContrastTest {
    private fun contrast(a: Color, b: Color): Float {
        val x = a.luminance(); val y = b.luminance()
        return (maxOf(x, y) + .05f) / (minOf(x, y) + .05f)
    }
    @Test fun `texto e sinais legiveis nao usam os tokens decorativos de baixo contraste`() {
        for (surface in listOf(C.Background, C.Surface, C.SurfaceHigh, C.SurfaceHighest)) {
            for (text in listOf(C.TextPrimary, C.TextSecondary, C.TextMuted, C.ErrorInk, C.InfoInk, C.AmberSoft)) {
                assertTrue("Contraste $text / $surface", contrast(text, surface) >= 4.5f)
            }
        }
        assertTrue(contrast(C.OnPrimary, C.Primary) >= 4.5f)
        assertTrue(contrast(C.OnPrimaryPressed, C.PrimaryPressed) >= 4.5f)
        assertTrue(contrast(C.OnPrimaryContainer, C.PrimaryContainer) >= 4.5f)
    }
}
