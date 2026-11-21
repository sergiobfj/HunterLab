package dev.hunterlab.app

import androidx.compose.ui.graphics.Color
import dev.hunterlab.app.ui.theme.HunterColors
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class UiAccessibilityTest {
    private fun luminance(color: Color): Double {
        fun linear(value: Float): Double = if (value <= 0.04045f) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
        return linear(color.red) * 0.2126 + linear(color.green) * 0.7152 + linear(color.blue) * 0.0722
    }

    private fun contrast(a: Color, b: Color): Double {
        val first = luminance(a)
        val second = luminance(b)
        return (max(first, second) + 0.05) / (min(first, second) + 0.05)
    }

    @Test
    fun `textos pequenos mantem contraste AA em todas as superficies do produto`() {
        val inks = listOf(HunterColors.TextPrimary, HunterColors.TextSecondary, HunterColors.TextMuted)
        val surfaces = listOf(HunterColors.Background, HunterColors.Surface, HunterColors.SurfaceHigh, HunterColors.SurfaceHighest)
        inks.forEach { ink -> surfaces.forEach { surface ->
            val ratio = contrast(ink, surface)
            assertTrue("Contraste insuficiente: $ink / $surface = $ratio", ratio >= 4.5)
        } }
    }

    @Test
    fun `cores de aviso e confirmacao preservam contraste de texto`() {
        listOf(HunterColors.AmberSoft, HunterColors.Confirmed, HunterColors.Error).forEach { ink ->
            assertTrue(contrast(ink, HunterColors.Surface) >= 4.5)
        }
    }
}
