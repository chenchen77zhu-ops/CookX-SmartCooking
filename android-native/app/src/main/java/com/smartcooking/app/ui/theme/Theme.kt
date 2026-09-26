package com.smartcooking.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** CookX brand palette – unchanged from the web design system (自然 × 温暖 × AI 科技). */
object CookX {
    val Primary = Color(0xFF173F35)
    val PrimaryDark = Color(0xFF102E27)
    val Deep = Color(0xFF092A22)
    val DeepAlt = Color(0xFF0B352A)
    val Accent = Color(0xFFD86B35)
    val AccentLight = Color(0xFFEF8B39)
    val Gold = Color(0xFFE9A23B)
    val Bg = Color(0xFFF7F5EF)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceMuted = Color(0xFFFAF9F5)
    val Text = Color(0xFF202522)
    val TextBody = Color(0xFF4E5651)
    val TextSecondary = Color(0xFF737A75)
    val TextTertiary = Color(0xFFA2A8A3)
    val Success = Color(0xFF4D8B69)
    val SuccessBright = Color(0xFF279744)
    val Danger = Color(0xFFD84A3A)
    val Border = Color(0x14173F35)
    val BorderStrong = Color(0x29173F35)
    val Mint = Color(0xFFEAF2EC)
    val MintDeep = Color(0xFFDCEADF)
    val DangerBg = Color(0xFFFBEDEC)
    val WarningBg = Color(0xFFFDF5EB)
    val AccentBg = Color(0xFFFBEBE0)
    val OnDark = Color(0xFFFFFFFF)
    val OnDarkMuted = Color(0xB3FFFFFF)
    val OnDarkFaint = Color(0x14FFFFFF)
    val OnDarkBorder = Color(0x29FFFFFF)

    val heroBrush = Brush.linearGradient(listOf(Deep, PrimaryDark, DeepAlt))
    val accentBrush = Brush.linearGradient(listOf(AccentLight, Accent))
    val senseBrush = Brush.linearGradient(listOf(Color(0xFF07352A), Color(0xFF03271F)))
    val quickGreen = Brush.linearGradient(listOf(Color(0xFF69CF75), Color(0xFF36AD51)))
    val quickGold = Brush.linearGradient(listOf(Color(0xFFF5B14E), Color(0xFFE9862F)))
    val quickDeep = Brush.linearGradient(listOf(Color(0xFF2E9C7B), Primary))
    val quickOrange = Brush.linearGradient(listOf(Color(0xFFFF8A53), Color(0xFFE85C38)))
}

object CookXShapes {
    val Large = RoundedCornerShape(24.dp)
    val Card = RoundedCornerShape(20.dp)
    val Tile = RoundedCornerShape(16.dp)
    val Button = RoundedCornerShape(15.dp)
    val Input = RoundedCornerShape(14.dp)
    val Icon = RoundedCornerShape(13.dp)
    val Small = RoundedCornerShape(10.dp)
    val Pill = RoundedCornerShape(50)
}

private val colors = lightColorScheme(
    primary = CookX.Primary,
    onPrimary = Color.White,
    primaryContainer = CookX.Mint,
    onPrimaryContainer = CookX.Primary,
    secondary = CookX.Accent,
    onSecondary = Color.White,
    secondaryContainer = CookX.AccentBg,
    onSecondaryContainer = Color(0xFF7A3413),
    tertiary = CookX.Gold,
    background = CookX.Bg,
    onBackground = CookX.Text,
    surface = CookX.Surface,
    onSurface = CookX.Text,
    surfaceVariant = CookX.SurfaceMuted,
    onSurfaceVariant = CookX.TextSecondary,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = CookX.SurfaceMuted,
    surfaceContainer = Color(0xFFF3F1EA),
    surfaceContainerHigh = Color(0xFFEFEDE6),
    surfaceContainerHighest = Color(0xFFE9E7E0),
    outline = CookX.BorderStrong,
    outlineVariant = CookX.Border,
    error = CookX.Danger,
    onError = Color.White,
    errorContainer = CookX.DangerBg,
    onErrorContainer = Color(0xFF8C2A1E),
)

private val base = Typography()
private val typography = Typography(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold, fontSize = 30.sp, letterSpacing = (-0.6).sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.4).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 25.sp),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = base.bodyLarge.copy(fontSize = 15.sp, lineHeight = 23.sp),
    bodyMedium = base.bodyMedium.copy(fontSize = 13.5.sp, lineHeight = 21.sp),
    bodySmall = base.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    labelMedium = base.labelMedium.copy(fontWeight = FontWeight.Medium, fontSize = 12.sp),
    labelSmall = base.labelSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp, letterSpacing = 1.2.sp),
)

private val shapes = Shapes(
    extraSmall = CookXShapes.Small,
    small = CookXShapes.Input,
    medium = CookXShapes.Tile,
    large = CookXShapes.Card,
    extraLarge = CookXShapes.Large,
)

@Composable
fun CookXTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, shapes = shapes, content = content)
}

/** Uppercase kicker such as "COOKX AI" above hero titles. */
val KickerStyle = TextStyle(fontSize = 10.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
