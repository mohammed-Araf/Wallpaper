package com.alancruse.wallpaper.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ── Static values for references in CanvasSyncManager, theme configurations or lockscreen ──
val DeepNavyStatic = Color(0xFF08080F)
val DarkSurfaceStatic = Color(0xFF111118)
val CardSurfaceStatic = Color(0xFF18181F)
val ElevatedSurfaceStatic = Color(0xFF1F1F28)

// ── Primary Accent ── (refined violet spectrum)
val ElectricViolet = Color(0xFF8B5CF6)
val VividPurple = Color(0xFFA78BFA)
val SoftViolet = Color(0xFFC4B5FD)

// ── Secondary Accent ── (cooler cyan/teal)
val CyanAccent = Color(0xFF22D3EE)
val TealGlow = Color(0xFF2DD4BF)

// ── Warm Accents ──
val SoftPink = Color(0xFFF472B6)
val WarmRose = Color(0xFFFB7185)
val AmberGlow = Color(0xFFFBBF24)

// ── Status ──
val SuccessGreen = Color(0xFF4ADE80)
val ErrorRed = Color(0xFFF87171)
val WarningAmber = Color(0xFFFBBF24)

// ── Theme-aware dynamic values ──
val DeepNavy: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalWallpaperColors.current.background

val DarkSurface: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalWallpaperColors.current.surface

val CardSurface: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalWallpaperColors.current.cardSurface

val ElevatedSurface: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalWallpaperColors.current.elevatedSurface

val TextPrimary: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalWallpaperColors.current.textPrimary

val TextSecondary: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalWallpaperColors.current.textSecondary

val TextMuted: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalWallpaperColors.current.textMuted

// ── Glass ── (subtler for modern look)
val GlassWhite: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalWallpaperColors.current.glassWhite

val GlassBorder: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalWallpaperColors.current.glassBorder

val GlassHighlight = Color(0x33FFFFFF)

// ── Theme Data Structure ──
data class WallpaperColors(
    val background: Color,
    val surface: Color,
    val cardSurface: Color,
    val elevatedSurface: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val glassBorder: Color,
    val glassWhite: Color
)

val DarkWallpaperColors = WallpaperColors(
    background = Color(0xFF08080F),
    surface = Color(0xFF111118),
    cardSurface = Color(0xFF18181F),
    elevatedSurface = Color(0xFF1F1F28),
    textPrimary = Color(0xFFFAFAFA),
    textSecondary = Color(0xFFA1A1AA),
    textMuted = Color(0xFF71717A),
    glassBorder = Color(0x1AFFFFFF),
    glassWhite = Color(0x0DFFFFFF)
)

val LightWallpaperColors = WallpaperColors(
    background = Color(0xFFF8FAFC), // Slate 50
    surface = Color.White,
    cardSurface = Color(0xFFF1F5F9), // Slate 100
    elevatedSurface = Color(0xFFE2E8F0), // Slate 200
    textPrimary = Color(0xFF0F172A), // Slate 900
    textSecondary = Color(0xFF475569), // Slate 600
    textMuted = Color(0xFF94A3B8), // Slate 400
    glassBorder = Color(0x1E000000), // Light dark border
    glassWhite = Color(0x0A000000)
)

val LocalWallpaperColors = staticCompositionLocalOf { DarkWallpaperColors }
