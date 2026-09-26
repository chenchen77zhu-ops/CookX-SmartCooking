package com.smartcooking.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartcooking.app.R

/**
 * One set of CookX colours. Light is the default (Apple Home–style: warm paper background, white
 * cards); dark keeps the same accents on near-black surfaces. The live kitchen and cooking screens
 * are always dark and use [KitchenColors] instead.
 */
data class CookXPalette(
    val dark: Boolean,
    val bg: Color,
    val surface: Color,
    val surfaceMuted: Color,
    val surfaceSunken: Color,
    val text: Color,
    val textBody: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val primary: Color,
    val primaryDark: Color,
    val mint: Color,
    val mintDeep: Color,
    val accent: Color,
    val accentLight: Color,
    val accentBg: Color,
    val accentText: Color,
    val gold: Color,
    val success: Color,
    val successBright: Color,
    val freshBg: Color,
    val danger: Color,
    val dangerBg: Color,
    val warningBg: Color,
    val warningText: Color,
    val neutralBg: Color,
    val border: Color,
    val borderStrong: Color,
    val shadow: Color,
    val tabBar: Color,
)

val LightPalette = CookXPalette(
    dark = false,
    bg = Color(0xFFF4F2ED),
    surface = Color(0xFFFFFFFF),
    surfaceMuted = Color(0xFFF8F7F3),
    surfaceSunken = Color(0xFFEFEDE7),
    text = Color(0xFF1B1D1C),
    textBody = Color(0xFF4A504C),
    textSecondary = Color(0xFF7A807C),
    textTertiary = Color(0xFFA6ABA7),
    primary = Color(0xFF1F5C45),
    primaryDark = Color(0xFF15402F),
    mint = Color(0xFFE7F2EB),
    mintDeep = Color(0xFFD5EADC),
    accent = Color(0xFFF0701E),
    accentLight = Color(0xFFFF9A45),
    accentBg = Color(0xFFFFEEE2),
    accentText = Color(0xFFC4530F),
    gold = Color(0xFFE9A23B),
    success = Color(0xFF3F8A62),
    successBright = Color(0xFF24A148),
    freshBg = Color(0xFFE6F4EA),
    danger = Color(0xFFE5483A),
    dangerBg = Color(0xFFFDEDEB),
    warningBg = Color(0xFFFFF4E3),
    warningText = Color(0xFFB7701A),
    neutralBg = Color(0xFFF0EEE9),
    border = Color(0x12000000),
    borderStrong = Color(0x21000000),
    shadow = Color(0x1A3A2A1A),
    tabBar = Color(0xF7FFFFFF),
)

val DarkPalette = CookXPalette(
    dark = true,
    bg = Color(0xFF0E100F),
    surface = Color(0xFF1A1D1C),
    surfaceMuted = Color(0xFF202422),
    surfaceSunken = Color(0xFF151816),
    text = Color(0xFFF3F1EC),
    textBody = Color(0xFFCFD2CE),
    textSecondary = Color(0xFF9CA29E),
    textTertiary = Color(0xFF6E7571),
    primary = Color(0xFF6FCF97),
    primaryDark = Color(0xFF4DAF78),
    mint = Color(0xFF16291F),
    mintDeep = Color(0xFF1C3527),
    accent = Color(0xFFFF8A3D),
    accentLight = Color(0xFFFFAA66),
    accentBg = Color(0xFF3A2416),
    accentText = Color(0xFFFFB27F),
    gold = Color(0xFFF2B558),
    success = Color(0xFF62C38D),
    successBright = Color(0xFF4FD37F),
    freshBg = Color(0xFF15301F),
    danger = Color(0xFFFF6B5B),
    dangerBg = Color(0xFF3A1714),
    warningBg = Color(0xFF362A14),
    warningText = Color(0xFFFFC15A),
    neutralBg = Color(0xFF252927),
    border = Color(0x17FFFFFF),
    borderStrong = Color(0x29FFFFFF),
    shadow = Color(0x66000000),
    tabBar = Color(0xF21A1D1C),
)

/**
 * CookX colour tokens. Every token reads the current palette from snapshot state, so screens
 * recompose when the appearance changes and the tokens stay usable outside composition too.
 */
object CookX {
    var palette by mutableStateOf(LightPalette)

    val isDark: Boolean get() = palette.dark
    val Primary: Color get() = palette.primary
    val PrimaryDark: Color get() = palette.primaryDark
    val Deep = Color(0xFF092A22)
    val DeepAlt = Color(0xFF0B352A)
    val Accent: Color get() = palette.accent
    val AccentLight: Color get() = palette.accentLight
    val AccentText: Color get() = palette.accentText
    val Gold: Color get() = palette.gold
    val Bg: Color get() = palette.bg
    val Surface: Color get() = palette.surface
    val SurfaceMuted: Color get() = palette.surfaceMuted
    val SurfaceSunken: Color get() = palette.surfaceSunken
    val Text: Color get() = palette.text
    val TextBody: Color get() = palette.textBody
    val TextSecondary: Color get() = palette.textSecondary
    val TextTertiary: Color get() = palette.textTertiary
    val Success: Color get() = palette.success
    val SuccessBright: Color get() = palette.successBright
    val FreshBg: Color get() = palette.freshBg
    val Danger: Color get() = palette.danger
    val Border: Color get() = palette.border
    val BorderStrong: Color get() = palette.borderStrong
    val Mint: Color get() = palette.mint
    val MintDeep: Color get() = palette.mintDeep
    val DangerBg: Color get() = palette.dangerBg
    val WarningBg: Color get() = palette.warningBg
    val WarningText: Color get() = palette.warningText
    val NeutralBg: Color get() = palette.neutralBg
    val AccentBg: Color get() = palette.accentBg
    val Shadow: Color get() = palette.shadow
    val TabBar: Color get() = palette.tabBar
    val OnDark = Color(0xFFFFFFFF)
    val OnDarkMuted = Color(0xB3FFFFFF)
    val OnDarkFaint = Color(0x1AFFFFFF)
    val OnDarkBorder = Color(0x29FFFFFF)

    /** Deep forest gradient kept for the sign-in page and the fridge card. */
    val heroBrush: Brush get() = Brush.linearGradient(listOf(Deep, Color(0xFF102E27), DeepAlt))
    val accentBrush: Brush get() = Brush.linearGradient(listOf(Color(0xFFFF9A45), Color(0xFFF0701E)))
    val senseBrush: Brush get() = Brush.linearGradient(listOf(Color(0xFF1C1B19), Color(0xFF0E0F0E)))
    val fridgeBrush: Brush get() = Brush.linearGradient(listOf(Color(0xFF4E9C77), Color(0xFF2E6F52), Color(0xFF245A43)))
    val quickGreen: Brush get() = Brush.linearGradient(listOf(Color(0xFF69CF75), Color(0xFF36AD51)))
    val quickGold: Brush get() = Brush.linearGradient(listOf(Color(0xFFF5B14E), Color(0xFFE9862F)))
    val quickDeep: Brush get() = Brush.linearGradient(listOf(Color(0xFF2E9C7B), Color(0xFF1F5C45)))
    val quickOrange: Brush get() = Brush.linearGradient(listOf(Color(0xFFFF8A53), Color(0xFFE85C38)))
}

/** Fixed colours of the always-dark photographic kitchen (Apple Weather–style). */
object KitchenColors {
    val Base = Color(0xFF0B0C0B)
    val Text = Color(0xFFF6F3EE)
    val TextMuted = Color(0xC7F6F3EE)
    val TextFaint = Color(0x80F6F3EE)
    val Glass = Color(0x7A1C1B19)
    val GlassStrong = Color(0x9E161514)
    val GlassBorder = Color(0x1FFFFFFF)
    val Heat = Color(0xFFFF8A2E)
    val HeatDeep = Color(0xFFF06418)
    val HeatSoft = Color(0xFFFFB25C)
    val PillBg = Color(0xB35A2E14)
    val PillBorder = Color(0x40FFA05A)
    val Alert = Color(0xFFFF4A3D)
    val AlertSoft = Color(0xFFFF7A5C)
    val AlertGlass = Color(0xB3781C12)
    val Fresh = Color(0xFF4ADE80)
    val Cream = Color(0xFFFBF4EA)
    val CreamText = Color(0xFF2A1E14)
    val CreamMuted = Color(0xFF8A7A6A)
    val heatBrush = Brush.linearGradient(listOf(HeatSoft, Heat, HeatDeep))
    val alertBrush = Brush.linearGradient(listOf(AlertSoft, Alert, Color(0xFFD1170C)))
}

object CookXShapes {
    val Large = RoundedCornerShape(26.dp)
    val Card = RoundedCornerShape(22.dp)
    val Tile = RoundedCornerShape(16.dp)
    val Button = RoundedCornerShape(15.dp)
    val Input = RoundedCornerShape(14.dp)
    val Icon = RoundedCornerShape(13.dp)
    val Small = RoundedCornerShape(10.dp)
    val Pill = RoundedCornerShape(50)
}

/** Rounded geometric numerals (Outfit, OFL) for temperatures, timers and counts. */
val NumericFont = FontFamily(
    Font(R.font.outfit_500, FontWeight.Medium),
    Font(R.font.outfit_600, FontWeight.SemiBold),
    Font(R.font.outfit_700, FontWeight.Bold),
)

private val base = Typography()
private val typography = Typography(
    displayLarge = base.displayLarge.copy(fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.8).sp),
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold, fontSize = 30.sp, letterSpacing = (-0.6).sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.4).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 24.sp),
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

/** Appearance preference: "light" (default), "dark" or "system". */
object Appearance {
    const val KEY = "cookx:theme"
    val options = listOf("light" to "浅色", "dark" to "深色", "system" to "跟随系统")
    var preference by mutableStateOf("light")
}

@Composable
fun CookXTheme(content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (Appearance.preference) { "dark" -> true; "system" -> systemDark; else -> false }
    val palette = if (dark) DarkPalette else LightPalette
    // Assigned during composition so the very first frame already uses the right palette.
    if (CookX.palette !== palette) CookX.palette = palette
    SideEffect { if (CookX.palette !== palette) CookX.palette = palette }
    MaterialTheme(colorScheme = colorsFor(palette), typography = typography, shapes = shapes, content = content)
}

/** Uppercase kicker such as "COOKX AI" above hero titles. */
val KickerStyle = TextStyle(fontSize = 10.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
