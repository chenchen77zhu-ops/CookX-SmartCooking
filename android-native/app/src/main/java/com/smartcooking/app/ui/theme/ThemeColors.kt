package com.smartcooking.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** Material colour scheme derived from a CookX palette (Material 3 surface-container roles). */
internal fun colorsFor(p: CookXPalette): ColorScheme = if (p.dark) darkColorScheme(
    primary = p.primary, onPrimary = Color(0xFF06140D), primaryContainer = p.mint, onPrimaryContainer = p.primary,
    secondary = p.accent, onSecondary = Color.White, secondaryContainer = p.accentBg, onSecondaryContainer = p.accentText,
    tertiary = p.gold, background = p.bg, onBackground = p.text, surface = p.surface, onSurface = p.text,
    surfaceVariant = p.surfaceMuted, onSurfaceVariant = p.textSecondary, surfaceContainerLowest = p.surfaceSunken,
    surfaceContainerLow = p.surface, surfaceContainer = p.surface, surfaceContainerHigh = p.surfaceMuted,
    surfaceContainerHighest = p.neutralBg, outline = p.borderStrong, outlineVariant = p.border,
    error = p.danger, onError = Color.White, errorContainer = p.dangerBg, onErrorContainer = p.danger,
) else lightColorScheme(
    primary = p.primary, onPrimary = Color.White, primaryContainer = p.mint, onPrimaryContainer = p.primary,
    secondary = p.accent, onSecondary = Color.White, secondaryContainer = p.accentBg, onSecondaryContainer = p.accentText,
    tertiary = p.gold, background = p.bg, onBackground = p.text, surface = p.surface, onSurface = p.text,
    surfaceVariant = p.surfaceMuted, onSurfaceVariant = p.textSecondary, surfaceContainerLowest = Color.White,
    surfaceContainerLow = p.surfaceMuted, surfaceContainer = Color(0xFFF3F1EA), surfaceContainerHigh = Color(0xFFEFEDE6),
    surfaceContainerHighest = Color(0xFFE9E7E0), outline = p.borderStrong, outlineVariant = p.border,
    error = p.danger, onError = Color.White, errorContainer = p.dangerBg, onErrorContainer = Color(0xFF8C2A1E),
)

