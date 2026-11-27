package com.alancruse.wallpaper.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.alancruse.wallpaper.ui.theme.WallpaperColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Shared motion specs: a soft spring with a hint of overshoot. */
object Motion {
    fun <T> bouncy() = spring<T>(dampingRatio = 0.62f, stiffness = 420f)
    fun <T> snappy() = spring<T>(dampingRatio = 0.8f, stiffness = 650f)
    fun <T> gentle() = spring<T>(dampingRatio = 0.9f, stiffness = 260f)
}

/**
 * Pop + rise in on first composition, staggered by [index].
 * Cheap: runs once per element, purely in the graphics layer.
 */
fun Modifier.enterAnim(index: Int = 0, rise: Float = 24f): Modifier = composed {
    val p = remember { Animatable(0f) }
    val risePx = with(LocalDensity.current) { rise.dp.toPx() }
    LaunchedEffect(Unit) {
        delay((index.coerceAtMost(12) * 35L))
        p.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 300f))
    }
    graphicsLayer {
        val v = p.value
        alpha = v.coerceIn(0f, 1f)
        translationY = (1f - v) * risePx
        val s = 0.94f + 0.06f * v
        scaleX = s; scaleY = s
    }
}

/** Drag down to dismiss; dragging up rubber-bands. */
fun Modifier.dragToDismiss(onDismiss: () -> Unit): Modifier = composed {
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val threshold = with(LocalDensity.current) { 120.dp.toPx() }
    this
        .graphicsLayer { translationY = offset.value }
        .draggable(
            orientation = Orientation.Vertical,
            state = rememberDraggableState { d ->
                scope.launch {
                    val cur = offset.value
                    // Resist upward pulls, and pulls far past the threshold.
                    val k = if (cur + d < 0) 0.25f else if (cur > threshold) 0.6f else 1f
                    offset.snapTo(cur + d * k)
                }
            },
            onDragStopped = { v ->
                if (offset.value > threshold || v > 1800f) onDismiss()
                scope.launch { offset.animateTo(0f, Motion.bouncy()) }
            }
        )
}

/** Smoothly cross-fades every colour of the palette when the theme or accent changes. */
@Composable
fun animatedColors(target: WallpaperColors): WallpaperColors {
    val spec = tween<Color>(450, easing = FastOutSlowInEasing)
    @Composable fun a(c: Color, l: String) = animateColorAsState(c, spec, label = l).value
    return target.copy(
        background = a(target.background, "bg"),
        surface = a(target.surface, "sf"),
        cardSurface = a(target.cardSurface, "cs"),
        elevatedSurface = a(target.elevatedSurface, "es"),
        textPrimary = a(target.textPrimary, "tp"),
        textSecondary = a(target.textSecondary, "ts"),
        textMuted = a(target.textMuted, "tm"),
        glassFill = a(target.glassFill, "gf"),
        glassFillStrong = a(target.glassFillStrong, "gs"),
        glassBorder = a(target.glassBorder, "gb"),
        glassHighlight = a(target.glassHighlight, "gh"),
        scrim = a(target.scrim, "sc"),
        accent = a(target.accent, "ac"),
        onAccent = a(target.onAccent, "oa"),
    )
}
