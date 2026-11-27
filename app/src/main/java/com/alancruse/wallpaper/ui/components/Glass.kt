package com.alancruse.wallpaper.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alancruse.wallpaper.settings.LocalAppSettings
import com.alancruse.wallpaper.ui.theme.*
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeChild

/** Backdrop that glass panels on the current screen blur. Null = no live blur. */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

// ═════════════════════ Haptics ═════════════════════

@Composable
fun rememberHaptics(): (Int) -> Unit {
    val view = LocalView.current
    val settings = LocalAppSettings.current
    return remember(view, settings) {
        { type: Int -> if (settings.haptics) view.performHapticFeedback(type) }
    }
}

object Haptic {
    const val Tick = HapticFeedbackConstants.CLOCK_TICK
    const val Confirm = HapticFeedbackConstants.CONFIRM
    val Reject = if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
}

// ═════════════════════ Glass modifier ═════════════════════

/**
 * Liquid-glass surface: live backdrop blur (API 31+, when a HazeState is provided),
 * translucent tint, a top specular sheen and a gradient hairline edge.
 */
fun Modifier.glass(shape: Shape, strong: Boolean = false, tint: Color? = null): Modifier = composed {
    val c = LocalWallpaperColors.current
    val settings = LocalAppSettings.current
    val haze = LocalHazeState.current
    val fill = tint ?: if (strong) c.glassFillStrong else c.glassFill
    val useBlur = haze != null && settings.glassBlur && Build.VERSION.SDK_INT >= 31
    val base = this.clip(shape)
    val blurred = if (useBlur) base.hazeChild(
        state = haze!!,
        style = HazeStyle(
            backgroundColor = c.background,
            tint = HazeTint(fill),
            blurRadius = if (strong) 28.dp else 22.dp,
            noiseFactor = 0.04f,
            fallbackTint = HazeTint(c.cardSurface.copy(alpha = 0.92f)),
        )
    ) else base.background(
        // No live blur: more opaque fill so text stays legible
        if (strong || haze != null) c.cardSurface.copy(alpha = if (c.isDark) 0.9f else 0.94f) else fill
    )
    blurred
        .background(Brush.verticalGradient(listOf(c.glassHighlight.copy(alpha = c.glassHighlight.alpha * 0.25f), Color.Transparent), endY = 120f))
        .border(
            1.dp,
            Brush.linearGradient(
                listOf(c.glassHighlight, c.glassBorder, c.glassBorder, c.glassHighlight.copy(alpha = c.glassHighlight.alpha * 0.4f)),
                start = Offset.Zero, end = Offset.Infinite
            ),
            shape
        )
}

// ═════════════════════ Background ═════════════════════

/** Screen background with soft ambient color fields (pure black in OLED). */
@Composable
fun AmbientBackground(modifier: Modifier = Modifier) {
    val c = LocalWallpaperColors.current
    val motion = LocalAppSettings.current.ambientMotion
    val t = if (motion && c.ambient.isNotEmpty()) {
        val inf = rememberInfiniteTransition(label = "amb")
        inf.animateFloat(0f, 1f, infiniteRepeatable(tween(12000, easing = LinearEasing), RepeatMode.Reverse), label = "t").value
    } else 0.5f
    Canvas(modifier.fillMaxSize().background(c.background)) {
        if (c.ambient.isEmpty()) return@Canvas
        val w = size.width; val h = size.height
        val spots = listOf(
            Offset(w * (0.15f + 0.2f * t), h * (0.12f + 0.06f * t)) to w * 0.85f,
            Offset(w * (0.95f - 0.15f * t), h * (0.45f + 0.1f * t)) to w * 0.75f,
            Offset(w * (0.3f + 0.25f * t), h * (0.92f - 0.08f * t)) to w * 0.8f,
        )
        spots.forEachIndexed { i, (center, r) ->
            val col = c.ambient.getOrNull(i) ?: return@forEachIndexed
            drawCircle(Brush.radialGradient(listOf(col, Color.Transparent), center, r), r, center)
        }
    }
}

// ═════════════════════ Buttons ═════════════════════

@Composable
fun Modifier.pressScale(source: MutableInteractionSource, pressed: Float = 0.92f): Modifier {
    val isPressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (isPressed) pressed else 1f, spring(0.5f, 700f), label = "ps")
    return this.scale(s)
}

@Composable
fun GlassIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    active: Boolean = false,
    enabled: Boolean = true,
    tint: Color? = null,
    strong: Boolean = false,
    contentDescription: String? = null,
) {
    val src = remember { MutableInteractionSource() }
    val haptic = rememberHaptics()
    val c = LocalWallpaperColors.current
    val bg by animateColorAsState(if (active) c.accent else Color.Transparent, tween(180), label = "bg")
    val fg by animateColorAsState(
        when { active -> c.onAccent; else -> tint ?: c.textPrimary }, tween(180), label = "fg"
    )
    Box(
        modifier
            .pressScale(src)
            .size(size)
            .glass(CircleShape, strong)
            .background(bg, CircleShape)
            .alpha(if (enabled) 1f else 0.35f)
            .clickable(src, null, enabled = enabled) { haptic(Haptic.Tick); onClick() },
        contentAlignment = Alignment.Center
    ) { Icon(icon, contentDescription, tint = fg, modifier = Modifier.size(size * 0.46f)) }
}

/** Plain icon (no glass) for use inside a glass container. */
@Composable
fun BareIconButton(
    icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier,
    size: Dp = 44.dp, active: Boolean = false, enabled: Boolean = true, tint: Color? = null,
    contentDescription: String? = null,
) {
    val src = remember { MutableInteractionSource() }
    val haptic = rememberHaptics()
    val c = LocalWallpaperColors.current
    val bg by animateColorAsState(if (active) c.accent else Color.Transparent, tween(180), label = "bg")
    val fg by animateColorAsState(if (active) c.onAccent else tint ?: c.textPrimary, tween(180), label = "fg")
    val pop by animateFloatAsState(if (active) 1f else 0.96f, Motion.bouncy(), label = "pop")
    Box(
        modifier.pressScale(src).scale(pop).size(size).clip(CircleShape).background(bg)
            .alpha(if (enabled) 1f else 0.3f)
            .clickable(src, null, enabled = enabled) { haptic(Haptic.Tick); onClick() },
        contentAlignment = Alignment.Center
    ) { Icon(icon, contentDescription, tint = fg, modifier = Modifier.size(size * 0.48f)) }
}

@Composable
fun AccentButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    color: Color? = null,
    height: Dp = 54.dp,
) {
    val src = remember { MutableInteractionSource() }
    val haptic = rememberHaptics()
    val c = LocalWallpaperColors.current
    val bg = color ?: c.accent
    val fg = if (color != null) Color.White else c.onAccent
    Box(
        modifier.pressScale(src, 0.96f).height(height).clip(RoundedCornerShape(height / 2))
            .background(if (enabled) bg else c.elevatedSurface)
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent)))
            .clickable(src, null, enabled = enabled && !loading) { haptic(Haptic.Confirm); onClick() }
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(22.dp), color = fg, strokeWidth = 2.5.dp)
        else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val tc = if (enabled) fg else c.textMuted
            if (icon != null) Icon(icon, null, tint = tc, modifier = Modifier.size(20.dp))
            if (text != null) Text(text, color = tc, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ═════════════════════ Inputs ═════════════════════

@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    textStyle: TextStyle = TextStyle(fontSize = 17.sp),
    trailing: @Composable (() -> Unit)? = null,
) {
    val c = LocalWallpaperColors.current
    Row(
        modifier.fillMaxWidth().height(58.dp).glass(RoundedCornerShape(20.dp)).padding(start = 18.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, null, tint = c.textMuted, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text(placeholder, style = textStyle.copy(color = c.textMuted))
            BasicTextField(
                value, onValueChange, singleLine = true,
                textStyle = textStyle.copy(color = c.textPrimary),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = keyboardOptions,
                modifier = Modifier.fillMaxWidth()
            )
        }
        trailing?.invoke()
    }
}

// ═════════════════════ Segmented / Switch ═════════════════════

@Composable
fun GlassSegmented(
    options: List<Pair<ImageVector, String?>>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalWallpaperColors.current
    val haptic = rememberHaptics()
    BoxWithConstraints(
        modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(14.dp))
            .background(c.textPrimary.copy(alpha = 0.06f)).padding(3.dp)
    ) {
        val n = options.size.coerceAtLeast(1)
        val w = maxWidth / n
        val x by animateDpAsState(w * selected.coerceAtLeast(0), Motion.bouncy(), label = "segx")
        // Sliding pill
        if (selected >= 0) Box(Modifier.offset(x = x).width(w).fillMaxHeight().clip(RoundedCornerShape(11.dp)).background(c.accent))
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, (icon, label) ->
                val sel = i == selected
                val src = remember { MutableInteractionSource() }
                val fg by animateColorAsState(if (sel) c.onAccent else c.textSecondary, tween(220), label = "segfg")
                Row(
                    Modifier.weight(1f).fillMaxHeight().pressScale(src, 0.9f).clip(RoundedCornerShape(11.dp))
                        .clickable(src, null) { haptic(Haptic.Tick); onSelect(i) },
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(icon, label, tint = fg, modifier = Modifier.size(17.dp))
                    if (label != null) {
                        Spacer(Modifier.width(5.dp))
                        Text(label, color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
fun GlassSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val c = LocalWallpaperColors.current
    val haptic = rememberHaptics()
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val thumbW by animateDpAsState(if (pressed) 33.dp else 27.dp, Motion.snappy(), label = "tw")
    val x by animateDpAsState(if (checked) 47.dp - thumbW else 0.dp, Motion.bouncy(), label = "sw")
    val track by animateColorAsState(if (checked) c.accent else c.textPrimary.copy(alpha = 0.14f), tween(220), label = "tr")
    Box(
        Modifier.pressScale(src, 0.95f).size(51.dp, 31.dp).clip(CircleShape).background(track)
            .clickable(src, null) { haptic(Haptic.Tick); onCheckedChange(!checked) }.padding(2.dp)
    ) { Box(Modifier.offset(x = x).size(thumbW, 27.dp).clip(CircleShape).background(Color.White)) }
}

// ═════════════════════ Settings rows ═════════════════════

@Composable
fun GlassGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp)).animateContentSize(Motion.gentle()).padding(vertical = 4.dp), content = content)
}

@Composable
fun IconBadge(icon: ImageVector, color: Color, size: Dp = 32.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(color),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * 0.58f)) }
}

@Composable
fun SettingRow(
    icon: ImageVector,
    color: Color,
    title: String,
    onClick: (() -> Unit)? = null,
    titleColor: Color? = null,
    trailing: @Composable (() -> Unit)? = { if (onClick != null) Icon(Icons.Rounded.ChevronRight, null, tint = LocalWallpaperColors.current.textMuted) },
) {
    val haptic = rememberHaptics()
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val hl by animateColorAsState(if (pressed) LocalWallpaperColors.current.textPrimary.copy(alpha = 0.07f) else Color.Transparent, tween(140), label = "hl")
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).pressScale(src, 0.98f).background(hl)
            .then(if (onClick != null) Modifier.clickable(src, null) { haptic(Haptic.Tick); onClick() } else Modifier)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(icon, color)
        Spacer(Modifier.width(14.dp))
        Text(title, color = titleColor ?: LocalWallpaperColors.current.textPrimary, fontSize = 16.sp, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text.uppercase(), color = LocalWallpaperColors.current.textMuted, fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp,
        modifier = Modifier.padding(start = 18.dp, top = 22.dp, bottom = 8.dp)
    )
}

// ═════════════════════ Header ═════════════════════

@Composable
fun GlassTopBar(
    title: String?,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, onBack, contentDescription = "Back")
        if (title != null) Text(
            title, color = LocalWallpaperColors.current.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp, modifier = Modifier.padding(start = if (onBack != null) 14.dp else 4.dp)
        )
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), content = actions)
    }
}

// ═════════════════════ Bottom sheet ═════════════════════

/** Glass bottom sheet rendered inside the current screen (place last in a full-size Box). */
@Composable
fun GlassSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalWallpaperColors.current
    if (visible) BackHandler(onBack = onDismiss)
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
            Box(Modifier.fillMaxSize().background(c.scrim).clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss))
        }
        AnimatedVisibility(
            visible, modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(spring(0.72f, 380f)) { it } + fadeIn() + scaleIn(spring(0.7f, 380f), 0.94f),
            exit = slideOutVertically(tween(220)) { it } + fadeOut(tween(220)),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(8.dp).navigationBarsPadding()
                    .dragToDismiss(onDismiss)
                    .glass(RoundedCornerShape(32.dp), strong = true, tint = c.cardSurface.copy(alpha = if (c.isDark) 0.75f else 0.85f))
                    .clickable(remember { MutableInteractionSource() }, null) {}
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(Modifier.padding(bottom = 10.dp).size(36.dp, 5.dp).clip(CircleShape).background(c.textMuted.copy(alpha = 0.5f)))
                content()
            }
        }
    }
}

/** Large icon + short label tile used inside sheets. */
@Composable
fun SheetAction(
    icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    color: Color? = null, active: Boolean = false,
) {
    val c = LocalWallpaperColors.current
    val haptic = rememberHaptics()
    val src = remember { MutableInteractionSource() }
    Column(
        modifier.pressScale(src, 0.95f).clip(RoundedCornerShape(22.dp))
            .background(if (active) c.accent.copy(alpha = 0.18f) else c.textPrimary.copy(alpha = 0.06f))
            .clickable(src, null) { haptic(Haptic.Confirm); onClick() }
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, label, tint = color ?: if (active) c.accent else c.textPrimary, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(8.dp))
        Text(label, color = color ?: c.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}
