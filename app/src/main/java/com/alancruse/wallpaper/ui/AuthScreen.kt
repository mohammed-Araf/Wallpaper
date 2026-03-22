package com.alancruse.wallpaper.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Brush
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alancruse.wallpaper.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun AuthScreen(onSignInClick: () -> Unit) {
    val view = LocalView.current

    // Staggered entrance animations
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(100); visible = true }

    // Smooth spring for icon entrance
    val iconScale by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 300f),
        label = "iconScale"
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(600, delayMillis = 200, easing = FastOutSlowInEasing),
        label = "contentAlpha"
    )
    val cardSlide by animateFloatAsState(
        targetValue = if (visible) 0f else 60f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 200f),
        label = "cardSlide"
    )

    // Ambient glow animation — ultra smooth
    val infiniteTransition = rememberInfiniteTransition(label = "bg")
    val orbOffset1 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 20f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "orb1"
    )
    val orbOffset2 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = -15f,
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "orb2"
    )

    // Breathing pulse for icon
    val breathe by infiniteTransition.animateFloat(
        initialValue = 0.97f, targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "breathe"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepNavy),
        contentAlignment = Alignment.Center
    ) {
        // Floating ambient orbs
        Box(
            modifier = Modifier
                .offset(x = (-50).dp, y = (-180 + orbOffset1).dp)
                .size(240.dp)
                .blur(100.dp)
                .background(ElectricViolet.copy(alpha = 0.15f), CircleShape)
        )
        Box(
            modifier = Modifier
                .offset(x = (100 + orbOffset2).dp, y = (200 - orbOffset1).dp)
                .size(200.dp)
                .blur(90.dp)
                .background(CyanAccent.copy(alpha = 0.1f), CircleShape)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App icon — spring entrance + breathing
            Box(
                modifier = Modifier
                    .scale(iconScale * breathe)
                    .size(88.dp)
                    .background(
                        Brush.linearGradient(listOf(ElectricViolet, CyanAccent)),
                        RoundedCornerShape(28.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Brush,
                    contentDescription = "WallPaper",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // App name — fade in
            Text(
                text = "WallPaper",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                letterSpacing = (-0.5).sp,
                modifier = Modifier.alpha(contentAlpha)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Draw together, share your walls",
                fontSize = 15.sp,
                color = TextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(contentAlpha)
            )

            Spacer(modifier = Modifier.height(56.dp))

            // Sign-in card — slide up + fade
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        translationY = cardSlide
                        alpha = contentAlpha
                    }
                    .clip(RoundedCornerShape(24.dp))
                    .background(CardSurface)
                    .border(1.dp, GlassBorder, RoundedCornerShape(24.dp))
                    .padding(28.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Get Started",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Sign in to connect with your partner",
                        fontSize = 13.sp,
                        color = TextMuted,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(28.dp))

                    Button(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            onSignInClick()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(listOf(ElectricViolet, CyanAccent)),
                                    RoundedCornerShape(16.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Sign in with Google",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            Text(
                text = "Your partner's phone becomes your canvas ✨",
                fontSize = 12.sp,
                color = TextMuted.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(contentAlpha)
            )
        }
    }
}
