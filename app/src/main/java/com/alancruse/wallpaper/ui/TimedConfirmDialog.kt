package com.alancruse.wallpaper.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.alancruse.wallpaper.ui.theme.*
import kotlinx.coroutines.delay

/**
 * A confirmation dialog that forces the user to wait [waitSeconds] before
 * they can tap "Confirm". The button shows a live countdown timer.
 */
@Composable
fun TimedConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String = "Confirm",
    accentColor: Color = WarmRose,
    waitSeconds: Int = 5,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var remainingSeconds by remember { mutableIntStateOf(waitSeconds) }
    val canConfirm = remainingSeconds <= 0

    // Countdown timer
    LaunchedEffect(Unit) {
        while (remainingSeconds > 0) {
            delay(1000L)
            remainingSeconds--
        }
    }

    // Progress animation
    val progress by animateFloatAsState(
        targetValue = if (canConfirm) 1f else (waitSeconds - remainingSeconds).toFloat() / waitSeconds,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "timerProgress"
    )

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(CardSurface)
                .border(1.dp, GlassBorder, RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = message,
                    fontSize = 13.sp,
                    color = TextMuted,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Confirm button with timer
                Button(
                    onClick = { if (canConfirm) onConfirm() },
                    enabled = canConfirm,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent
                    ),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        // Progress background
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progress)
                                .align(Alignment.CenterStart)
                                .background(
                                    if (canConfirm)
                                        Brush.horizontalGradient(listOf(accentColor, accentColor.copy(alpha = 0.8f)))
                                    else
                                        Brush.horizontalGradient(listOf(accentColor.copy(alpha = 0.15f), accentColor.copy(alpha = 0.1f))),
                                    RoundedCornerShape(14.dp)
                                )
                        )
                        // Full border
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .border(1.dp, accentColor.copy(alpha = if (canConfirm) 0.6f else 0.2f), RoundedCornerShape(14.dp))
                        )
                        // Label
                        Text(
                            text = if (canConfirm) confirmLabel else "$confirmLabel (${remainingSeconds}s)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (canConfirm) Color.White else accentColor.copy(alpha = 0.5f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Cancel button
                TextButton(onClick = onDismiss) {
                    Text("Cancel", fontSize = 13.sp, color = TextMuted)
                }
            }
        }
    }
}
