package dev.hunterlab.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.hunterlab.protocol.Confidence

/** Paleta: grafite quase preto, tinta "osso" quente e âmbar discreto. */
object HunterColors {
    val Background = Color(0xFF0C0D0F)
    val Surface = Color(0xFF14161A)
    val SurfaceHigh = Color(0xFF1B1E23)
    val SurfaceHighest = Color(0xFF23272D)
    val Outline = Color(0xFF2B3037)
    val OutlineStrong = Color(0xFF434A53)

    val TextPrimary = Color(0xFFEDE8E0)
    val TextSecondary = Color(0xFFA5ABB2)
    val TextMuted = Color(0xFF89919B)

    val Amber = Color(0xFFE2A240)
    val AmberSoft = Color(0xFFF3CF92)
    val AmberContainer = Color(0xFF30230E)

    val Confirmed = Color(0xFF7DB38E)
    val Likely = Color(0xFFE2A240)
    val Unverified = Color(0xFFD9775F)
    val Error = Color(0xFFE5675A)
    val Info = Color(0xFF8FB3D1)

    // Prévia do Tripper (aproximação, não as cores reais do display)
    val DisplayNight = Color(0xFF050506)
    val DisplayNightInk = Color(0xFFF2EEE6)
    val DisplayDay = Color(0xFFE6E2DA)
    val DisplayDayInk = Color(0xFF15171A)
    val BezelOuter = Color(0xFF3A3E44)
    val BezelInner = Color(0xFF101215)
}

fun Confidence.color(): Color = when (this) {
    Confidence.CONFIRMED -> HunterColors.Confirmed
    Confidence.LIKELY -> HunterColors.Likely
    Confidence.UNVERIFIED -> HunterColors.Unverified
}

private val scheme = darkColorScheme(
    primary = HunterColors.Amber,
    onPrimary = Color(0xFF1C1405),
    primaryContainer = HunterColors.AmberContainer,
    onPrimaryContainer = HunterColors.AmberSoft,
    secondary = Color(0xFFB9BFC6),
    onSecondary = Color(0xFF15171A),
    secondaryContainer = HunterColors.SurfaceHighest,
    onSecondaryContainer = HunterColors.TextPrimary,
    tertiary = HunterColors.Info,
    background = HunterColors.Background,
    onBackground = HunterColors.TextPrimary,
    surface = HunterColors.Surface,
    onSurface = HunterColors.TextPrimary,
    surfaceVariant = HunterColors.SurfaceHigh,
    onSurfaceVariant = HunterColors.TextSecondary,
    surfaceContainerLowest = HunterColors.Background,
    surfaceContainerLow = HunterColors.Surface,
    surfaceContainer = HunterColors.Surface,
    surfaceContainerHigh = HunterColors.SurfaceHigh,
    surfaceContainerHighest = HunterColors.SurfaceHighest,
    outline = HunterColors.OutlineStrong,
    outlineVariant = HunterColors.Outline,
    error = HunterColors.Error,
    onError = Color(0xFF1E0806),
)

private val base = TextStyle(fontFamily = FontFamily.SansSerif)

private val typography = Typography(
    displayLarge = base.copy(fontSize = 76.sp, lineHeight = 84.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-3).sp, fontFeatureSettings = "tnum"),
    headlineLarge = base.copy(fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.8).sp),
    headlineMedium = base.copy(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = base.copy(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
    titleLarge = base.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = base.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
    titleSmall = base.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = base.copy(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = base.copy(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = base.copy(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = base.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
    labelMedium = base.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp),
    labelSmall = base.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp),
)

/** Estilo para números e bytes (algarismos tabulares, alinhados). */
val MonoDigits = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, fontFeatureSettings = "tnum")

@Composable
fun HunterLabTheme(content: @Composable () -> Unit) {
    // Dark mode é a aparência principal (e única) desta versão.
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
