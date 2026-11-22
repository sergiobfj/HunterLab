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

/** Graphite Grey: cores de sinal não substituem os tons de texto acessíveis. */
object HunterColors {
    val Background = Color(0xFF101416)
    val Surface = Color(0xFF171C1F)
    val SurfaceHigh = Color(0xFF1D2327)
    val SurfaceHighest = Color(0xFF222A2E)
    val Outline = Color(0xFF2C3439)
    val OutlineStrong = Color(0xFF66747C)
    val TextPrimary = Color(0xFFE8ECEE)
    val TextSecondary = Color(0xFFA7B0B5)
    val TextWeak = Color(0xFF6F7A80) // somente desabilitado/decorativo
    val TextMuted = Color(0xFF939FA5) // texto pequeno: contraste >= 4,5:1 nas superfícies
    val Primary = Color(0xFF6E8F45)
    val PrimaryPressed = Color(0xFF5C773A)
    val PrimaryContainer = Color(0xFF24301F)
    val OnPrimary = Color(0xFF080D07)
    val OnPrimaryPressed = Color(0xFFF3F7EF)
    val OnPrimaryContainer = Color(0xFFDCE9CF)
    val Success = Color(0xFF5E9E52)
    val Warning = Color(0xFFC98A2E)
    val Error = Color(0xFFC65A4A)
    val Info = Color(0xFF4F6773)
    val ErrorInk = Color(0xFFEBA092)
    val InfoInk = Color(0xFFA8BBC5)
    val Amber = Warning
    val AmberSoft = Color(0xFFE7BA78)
    val AmberContainer = Color(0xFF302619)
    val Confirmed = Success
    val Likely = AmberSoft
    val Unverified = AmberSoft
    // O desenho do display é uma aproximação do hardware; não recebe a cor da marca.
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
    primary = HunterColors.Primary, onPrimary = HunterColors.OnPrimary,
    primaryContainer = HunterColors.PrimaryContainer, onPrimaryContainer = HunterColors.OnPrimaryContainer,
    secondary = HunterColors.TextSecondary, onSecondary = HunterColors.Background,
    secondaryContainer = HunterColors.SurfaceHighest, onSecondaryContainer = HunterColors.TextPrimary,
    tertiary = HunterColors.InfoInk, onTertiary = HunterColors.Background,
    background = HunterColors.Background, onBackground = HunterColors.TextPrimary,
    surface = HunterColors.Surface, onSurface = HunterColors.TextPrimary,
    surfaceVariant = HunterColors.SurfaceHigh, onSurfaceVariant = HunterColors.TextSecondary,
    surfaceContainerLowest = HunterColors.Background, surfaceContainerLow = HunterColors.Surface,
    surfaceContainer = HunterColors.Surface, surfaceContainerHigh = HunterColors.SurfaceHigh,
    surfaceContainerHighest = HunterColors.SurfaceHighest,
    surfaceTint = Color.Transparent,
    outline = HunterColors.OutlineStrong, outlineVariant = HunterColors.Outline,
    error = HunterColors.ErrorInk, onError = HunterColors.Background,
    errorContainer = Color(0xFF38211E), onErrorContainer = HunterColors.ErrorInk,
)

private val base = TextStyle(fontFamily = FontFamily.SansSerif)
private val typography = Typography(
    displayLarge = base.copy(fontSize = 72.sp, lineHeight = 80.sp, fontWeight = FontWeight.Medium, letterSpacing = (-2).sp, fontFeatureSettings = "tnum"),
    displayMedium = base.copy(fontSize = 56.sp, lineHeight = 64.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"),
    displaySmall = base.copy(fontSize = 44.sp, lineHeight = 52.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"),
    headlineLarge = base.copy(fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.5).sp),
    headlineMedium = base.copy(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.4).sp),
    headlineSmall = base.copy(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Medium),
    titleLarge = base.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium),
    titleMedium = base.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    titleSmall = base.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    bodyLarge = base.copy(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = base.copy(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = base.copy(fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = base.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = base.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
    labelSmall = base.copy(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp),
)

val MonoDigits = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp, fontFeatureSettings = "tnum")

@Composable
fun HunterLabTheme(content: @Composable () -> Unit) {
    // Identidade estável: sem dynamic color ou cores do papel de parede.
    MaterialTheme(colorScheme = scheme, typography = typography, shapes = HunterShapes.Material, content = content)
}
