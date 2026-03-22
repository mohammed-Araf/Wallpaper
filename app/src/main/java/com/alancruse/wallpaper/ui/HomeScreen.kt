package com.alancruse.wallpaper.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alancruse.wallpaper.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    partnerUid: String,
    onOpenCanvas: () -> Unit,
    onActivateWallpaper: () -> Unit,
    onUnpair: () -> Unit,
    onSignOut: () -> Unit
) {
    val view = LocalView.current

    // Staggered entrance
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(60); visible = true }

    val headerScale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.6f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f), label = "headerScale"
    )
    val headerAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(400), label = "headerAlpha"
    )
    val card1Offset by animateFloatAsState(
        targetValue = if (visible) 0f else 60f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 200f), label = "card1"
    )
    val card2Offset by animateFloatAsState(
        targetValue = if (visible) 0f else 60f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 150f), label = "card2"
    )

    // Ambient glow animation
    val infiniteTransition = rememberInfiniteTransition(label = "home")
    val orbFloat by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 15f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "orbFloat"
    )
    val statusPulse by infiniteTransition.animateFloat(
        initialValue = 0.7f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "statusPulse"
    )

    Box(
        modifier = Modifier.fillMaxSize().background(DeepNavy)
    ) {
        // Floating orbs
        Box(
            modifier = Modifier
                .offset(x = (-30).dp, y = (-60 + orbFloat).dp)
                .size(200.dp)
                .blur(100.dp)
                .background(ElectricViolet.copy(alpha = 0.1f), CircleShape)
        )
        Box(
            modifier = Modifier
                .offset(x = (140).dp, y = (480 - orbFloat).dp)
                .size(180.dp)
                .blur(90.dp)
                .background(CyanAccent.copy(alpha = 0.08f), CircleShape)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 64.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Connection status — spring entrance
            Box(
                modifier = Modifier
                    .scale(headerScale)
                    .alpha(headerAlpha)
                    .size(56.dp)
                    .background(SuccessGreen.copy(alpha = 0.1f), CircleShape)
                    .border(
                        1.5.dp,
                        SuccessGreen.copy(alpha = statusPulse * 0.4f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.FavoriteBorder, "Connected",
                    tint = SuccessGreen.copy(alpha = statusPulse),
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Connected",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                letterSpacing = (-0.5).sp,
                modifier = Modifier.alpha(headerAlpha)
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "You're linked with your partner 💜",
                fontSize = 13.sp,
                color = TextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(headerAlpha)
            )

            Spacer(modifier = Modifier.height(28.dp))

            // ── Card 1: Open Canvas — with press animation ──
            ActionCard(
                icon = Icons.Rounded.Brush,
                iconTint = ElectricViolet,
                title = "Live Canvas",
                subtitle = "Draw on your partner's wallpaper in real-time",
                buttonLabel = "Open Canvas",
                buttonGradient = listOf(ElectricViolet, CyanAccent),
                buttonTextColor = Color.White,
                offsetY = card1Offset,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    onOpenCanvas()
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // ── Card 2: Activate Wallpaper ──
            ActionCard(
                icon = Icons.Rounded.Wallpaper,
                iconTint = CyanAccent,
                title = "Live Wallpaper",
                subtitle = "Set the synced canvas as your phone wallpaper",
                buttonLabel = "Set as Wallpaper",
                buttonGradient = listOf(CyanAccent, TealGlow),
                buttonTextColor = DeepNavy,
                offsetY = card2Offset,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    onActivateWallpaper()
                }
            )

            Spacer(modifier = Modifier.weight(1f))

            // Bottom actions — fade in
            Row(
                modifier = Modifier.fillMaxWidth().alpha(headerAlpha),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        onUnpair()
                    },
                    modifier = Modifier.weight(1f).height(42.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, WarmRose.copy(alpha = 0.3f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = WarmRose)
                ) {
                    Icon(Icons.Rounded.LinkOff, null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Unpair", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }

                OutlinedButton(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        onSignOut()
                    },
                    modifier = Modifier.weight(1f).height(42.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, GlassBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMuted)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Logout, null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Sign Out", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun ActionCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    buttonLabel: String,
    buttonGradient: List<Color>,
    buttonTextColor: Color,
    offsetY: Float,
    onClick: () -> Unit
) {
    // Press scale animation
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "pressScale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationY = offsetY
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(RoundedCornerShape(20.dp))
            .background(CardSurface)
            .border(1.dp, GlassBorder, RoundedCornerShape(20.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(20.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(modifier = Modifier.height(3.dp))
            Text(subtitle, fontSize = 12.sp, color = TextMuted, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.horizontalGradient(buttonGradient)),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, tint = buttonTextColor, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(buttonLabel, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = buttonTextColor)
                }
            }
        }
    }
}
