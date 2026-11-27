package com.alancruse.wallpaper.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ── Static values (used outside composition: sync manager, wallpaper services) ──
val DeepNavyStatic = Color(0xFF000000)
val DarkSurfaceStatic = Color(0xFF0E0E12)
val CardSurfaceStatic = Color(0xFF16161B)
val ElevatedSurfaceStatic = Color(0xFF1E1E24)

// ── Fixed palette ──
val ElectricViolet = Color(0xFF8B5CF6)
val VividPurple = Color(0xFFA78BFA)
val SoftViolet = Color(0xFFC4B5FD)
val CyanAccent = Color(0xFF22D3EE)
val TealGlow = Color(0xFF2DD4BF)
val SoftPink = Color(0xFFF472B6)
val WarmRose = Color(0xFFFB7185)
val AmberGlow = Color(0xFFFBBF24)
val SuccessGreen = Color(0xFF34D399)
val ErrorRed = Color(0xFFFF453A)
val WarningAmber = Color(0xFFFFD60A)

// iOS-style system tints used for setting row icons
object Tints {
    val Blue = Color(0xFF0A84FF)
    val Indigo = Color(0xFF5E5CE6)
    val Purple = Color(0xFFBF5AF2)
    val Pink = Color(0xFFFF375F)
    val Red = Color(0xFFFF453A)
    val Orange = Color(0xFFFF9F0A)
    val Yellow = Color(0xFFFFD60A)
    val Green = Color(0xFF30D158)
    val Teal = Color(0xFF40C8E0)
    val Gray = Color(0xFF8E8E93)
}

/** Full set of theme-dependent colors. */
data class WallpaperColors(
    val isDark: Boolean,
    val isOled: Boolean,
    val background: Color,
    val surface: Color,
    val cardSurface: Color,
    val elevatedSurface: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    /** Translucent fill used by glass panels. */
    val glassFill: Color,
    /** Stronger fill for panels that float over busy content (canvas). */
    val glassFillStrong: Color,
    /** Hairline edge of glass panels. */
    val glassBorder: Color,
    /** Specular highlight at the top edge of glass panels. */
    val glassHighlight: Color,
    val scrim: Color,
    val accent: Color,
    val onAccent: Color,
    /** Soft ambient blobs behind content (transparent in OLED). */
    val ambient: List<Color>,
) {
    // Back-compat alias
    val glassWhite: Color get() = glassFill
}

fun darkColors(accent: Color) = WallpaperColors(
    isDark = true,
    isOled = false,
    background = Color(0xFF0A0A0F),
    surface = Color(0xFF111117),
    cardSurface = Color(0xFF17171E),
    elevatedSurface = Color(0xFF202029),
    textPrimary = Color(0xFFF5F5F7),
    textSecondary = Color(0xFFAEAEB2),
    textMuted = Color(0xFF6E6E76),
    glassFill = Color(0x14FFFFFF),
    glassFillStrong = Color(0x2E1C1C24),
    glassBorder = Color(0x1FFFFFFF),
    glassHighlight = Color(0x40FFFFFF),
    scrim = Color(0x99000000),
    accent = accent,
    onAccent = onColorFor(accent),
    ambient = listOf(accent.copy(alpha = 0.32f), Color(0xFF22D3EE).copy(alpha = 0.18f), Color(0xFFF472B6).copy(alpha = 0.16f)),
)

fun oledColors(accent: Color) = WallpaperColors(
    isDark = true,
    isOled = true,
    background = Color.Black,
    surface = Color.Black,
    cardSurface = Color(0xFF0A0A0A),
    elevatedSurface = Color(0xFF141414),
    textPrimary = Color(0xFFF5F5F7),
    textSecondary = Color(0xFFA1A1A6),
    textMuted = Color(0xFF636366),
    glassFill = Color(0x0FFFFFFF),
    glassFillStrong = Color(0x33000000),
    glassBorder = Color(0x1AFFFFFF),
    glassHighlight = Color(0x2EFFFFFF),
    scrim = Color(0xCC000000),
    accent = accent,
    onAccent = onColorFor(accent),
    ambient = emptyList(),
)

fun lightColors(accent: Color) = WallpaperColors(
    isDark = false,
    isOled = false,
    background = Color(0xFFF2F2F7),
    surface = Color.White,
    cardSurface = Color.White,
    elevatedSurface = Color(0xFFE5E5EA),
    textPrimary = Color(0xFF0B0B0F),
    textSecondary = Color(0xFF3C3C43).copy(alpha = 0.75f),
    textMuted = Color(0xFF3C3C43).copy(alpha = 0.45f),
    glassFill = Color(0x99FFFFFF),
    glassFillStrong = Color(0x66FFFFFF),
    glassBorder = Color(0x14000000),
    glassHighlight = Color(0xE6FFFFFF),
    scrim = Color(0x4D000000),
    accent = accent,
    onAccent = onColorFor(accent),
    ambient = listOf(accent.copy(alpha = 0.22f), Color(0xFF22D3EE).copy(alpha = 0.14f), Color(0xFFFF9F0A).copy(alpha = 0.12f)),
)

private fun onColorFor(c: Color): Color {
    val lum = 0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue
    return if (lum > 0.6f) Color(0xFF0B0B0F) else Color.White
}

val LocalWallpaperColors = staticCompositionLocalOf { darkColors(ElectricViolet) }

// ── Theme-aware shortcuts ──
val Accent: Color @Composable @ReadOnlyComposable get() = LocalWallpaperColors.current.accent
val OnAccent: Color @Composable @ReadOnlyComposable get() = LocalWallpaperColors.current.onAccent
val DeepNavy: Color @Composable @ReadOnlyComposable get() = LocalWallpaperColors.current.background
val DarkSurface: Color @Composable @ReadOnlyComposable get() = LocalWallpaperColors.current.surface
val CardSurface: Color @Composable @ReadOnlyComposable get() = LocalWallpaperColors.current.cardSurface
val ElevatedSurface: Color @Composable @ReadOnlyComposable get() = LocalWallpaperColors.current.elevatedSurface
val TextPrimary: Color @Composable @ReadOnlyComposable get() = LocalWallpaperColors.current.textPrimary
val TextSecondary: Color @Composable @ReadOnlyComposable get() = LocalWallpaperColors.current.textSecondary
val TextMuted: Color @Composable @ReadOnlyComposable get() = LocalWallpaperColors.current.textMuted
val GlassWhite: Color @Composable @ReadOnlyComposable get() = LocalWallpaperColors.current.glassFill
val GlassBorder: Color @Composable @ReadOnlyComposable get() = LocalWallpaperColors.current.glassBorder
val GlassHighlight: Color @Composable @ReadOnlyComposable get() = LocalWallpaperColors.current.glassHighlight
