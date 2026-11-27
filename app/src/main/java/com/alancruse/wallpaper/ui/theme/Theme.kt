package com.alancruse.wallpaper.ui.theme

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.alancruse.wallpaper.settings.AppSettings
import com.alancruse.wallpaper.settings.LocalAppSettings
import com.alancruse.wallpaper.settings.ThemeMode

@Composable
fun WallPaperTheme(content: @Composable () -> Unit) {
    val settings = AppSettings.get(LocalContext.current)
    val systemDark = isSystemInDarkTheme()
    val accent = settings.accent.color
    val target = when (settings.themeMode) {
        ThemeMode.LIGHT -> lightColors(accent)
        ThemeMode.DARK -> darkColors(accent)
        ThemeMode.OLED -> oledColors(accent)
        ThemeMode.SYSTEM -> if (systemDark) oledColors(accent) else lightColors(accent)
    }
    val colors = com.alancruse.wallpaper.ui.components.animatedColors(target)

    val scheme = if (colors.isDark) darkColorScheme(
        primary = colors.accent, onPrimary = colors.onAccent,
        background = colors.background, onBackground = colors.textPrimary,
        surface = colors.surface, onSurface = colors.textPrimary,
        surfaceVariant = colors.cardSurface, onSurfaceVariant = colors.textSecondary,
        error = ErrorRed, outline = colors.glassBorder,
    ) else lightColorScheme(
        primary = colors.accent, onPrimary = colors.onAccent,
        background = colors.background, onBackground = colors.textPrimary,
        surface = colors.surface, onSurface = colors.textPrimary,
        surfaceVariant = colors.cardSurface, onSurfaceVariant = colors.textSecondary,
        error = ErrorRed, outline = colors.glassBorder,
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(colors.background.toArgb()))
            WindowInsetsControllerCompat(window, view).apply {
                isAppearanceLightStatusBars = !colors.isDark
                isAppearanceLightNavigationBars = !colors.isDark
            }
            if (settings.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    CompositionLocalProvider(
        LocalWallpaperColors provides colors,
        LocalAppSettings provides settings,
    ) {
        MaterialTheme(colorScheme = scheme, typography = Typography, content = content)
    }
}
