package com.alancruse.wallpaper.ui.theme

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

private val WallPaperColorScheme = darkColorScheme(
    primary = ElectricViolet,
    onPrimary = TextPrimary,
    primaryContainer = VividPurple,
    onPrimaryContainer = TextPrimary,
    secondary = CyanAccent,
    onSecondary = DeepNavy,
    secondaryContainer = TealGlow,
    onSecondaryContainer = TextPrimary,
    tertiary = SoftPink,
    onTertiary = DeepNavy,
    background = DeepNavy,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = CardSurface,
    onSurfaceVariant = TextSecondary,
    error = ErrorRed,
    onError = TextPrimary,
    outline = GlassBorder
)

@Composable
fun WallPaperTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = WallPaperColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Edge-to-edge immersive
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            val controller = WindowInsetsControllerCompat(window, view)
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
            // Keep screen on during canvas interactions
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
