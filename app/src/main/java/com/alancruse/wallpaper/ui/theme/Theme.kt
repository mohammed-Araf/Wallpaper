package com.alancruse.wallpaper.ui.theme

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

private val WallPaperColorScheme = darkColorScheme(
    primary = ElectricViolet,
    onPrimary = Color(0xFFFAFAFA),
    primaryContainer = VividPurple,
    onPrimaryContainer = Color(0xFFFAFAFA),
    secondary = CyanAccent,
    onSecondary = DeepNavyStatic,
    secondaryContainer = TealGlow,
    onSecondaryContainer = Color(0xFFFAFAFA),
    tertiary = SoftPink,
    onTertiary = DeepNavyStatic,
    background = DeepNavyStatic,
    onBackground = Color(0xFFFAFAFA),
    surface = DarkSurfaceStatic,
    onSurface = Color(0xFFFAFAFA),
    surfaceVariant = CardSurfaceStatic,
    onSurfaceVariant = Color(0xFFA1A1AA),
    error = ErrorRed,
    onError = Color(0xFFFAFAFA),
    outline = Color(0x1AFFFFFF)
)

private val WallPaperLightColorScheme = lightColorScheme(
    primary = ElectricViolet,
    onPrimary = Color.White,
    primaryContainer = SoftViolet,
    onPrimaryContainer = Color(0xFF1E1B4B),
    secondary = Color(0xFF0891B2),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFFAFE),
    onSecondaryContainer = Color(0xFF083344),
    tertiary = SoftPink,
    onTertiary = Color.White,
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF475569),
    error = ErrorRed,
    onError = Color.White,
    outline = Color(0x1E000000)
)

@Composable
fun WallPaperTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val wallpaperColors = if (darkTheme) DarkWallpaperColors else LightWallpaperColors
    val colorScheme = if (darkTheme) WallPaperColorScheme else WallPaperLightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Edge-to-edge immersive
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            val controller = WindowInsetsControllerCompat(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
            // Keep screen on during canvas interactions
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    CompositionLocalProvider(
        LocalWallpaperColors provides wallpaperColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
