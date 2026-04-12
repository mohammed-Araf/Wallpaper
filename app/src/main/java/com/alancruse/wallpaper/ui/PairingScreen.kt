package com.alancruse.wallpaper.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.QrCode
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alancruse.wallpaper.pairing.PairingManager
import com.alancruse.wallpaper.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun PairingScreen(
    currentUid: String,
    onPairedSuccess: (String) -> Unit,
    onSkipToGroups: () -> Unit = {}
) {
    val view = LocalView.current
    val pairingManager = remember { PairingManager(currentUid) }
    var generatedPin by remember { mutableStateOf<String?>(null) }
    var inputPin by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var isWaitingForPartner by remember { mutableStateOf(false) }

    // Staggered entrance
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(80); visible = true }

    val headerAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(500, easing = FastOutSlowInEasing), label = "headerAlpha"
    )
    val card1Offset by animateFloatAsState(
        targetValue = if (visible) 0f else 50f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 200f), label = "card1"
    )
    val card2Offset by animateFloatAsState(
        targetValue = if (visible) 0f else 50f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 150f), label = "card2"
    )

    // Waiting pulse
    val infiniteTransition = rememberInfiniteTransition(label = "waiting")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "pulse"
    )
    val orbFloat by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 12f,
        animationSpec = infiniteRepeatable(
            animation = tween(3500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "orbFloat"
    )

    DisposableEffect(pairingManager) {
        onDispose { pairingManager.cleanup() }
    }

    // Robust polling: when waiting for partner, check Firebase every 2s directly
    // from Compose scope. This guarantees the state change triggers navigation
    // even if the realtime listener callback doesn't reach Compose properly.
    LaunchedEffect(isWaitingForPartner) {
        if (!isWaitingForPartner) return@LaunchedEffect
        while (isWaitingForPartner) {
            delay(2000)
            pairingManager.checkExistingPairing { existingPartner ->
                if (existingPartner != null && isWaitingForPartner) {
                    isWaitingForPartner = false
                    onPairedSuccess(existingPartner)
                }
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(DeepNavy)
    ) {
        // Ambient orbs
        Box(
            modifier = Modifier
                .offset(x = 100.dp, y = (-80 + orbFloat).dp)
                .size(180.dp)
                .blur(90.dp)
                .background(ElectricViolet.copy(alpha = 0.12f), CircleShape)
        )
        Box(
            modifier = Modifier
                .offset(x = (-60).dp, y = (380 - orbFloat).dp)
                .size(160.dp)
                .blur(80.dp)
                .background(CyanAccent.copy(alpha = 0.08f), CircleShape)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 72.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Icon(
                imageVector = Icons.Rounded.Link,
                contentDescription = null,
                tint = CyanAccent,
                modifier = Modifier
                    .size(36.dp)
                    .alpha(headerAlpha)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Link to Partner",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                letterSpacing = (-0.5).sp,
                modifier = Modifier.alpha(headerAlpha)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Connect your devices to share wallpapers",
                fontSize = 13.sp,
                color = TextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(headerAlpha)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // ── Card 1: Generate Code ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        translationY = card1Offset
                        alpha = if (visible) 1f else 0f
                    }
                    .clip(RoundedCornerShape(20.dp))
                    .background(CardSurface)
                    .border(1.dp, GlassBorder, RoundedCornerShape(20.dp))
                    .padding(20.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.QrCode, null, tint = ElectricViolet, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Generate a Code", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (generatedPin == null) {
                        Button(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                pairingManager.generatePairingCode(
                                    onCodeGenerated = { pin ->
                                        generatedPin = pin
                                        isWaitingForPartner = true
                                        statusMessage = "Share this PIN with your partner"
                                        isError = false
                                    },
                                    onPartnerLinked = { partnerUid ->
                                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                        isWaitingForPartner = false
                                        onPairedSuccess(partnerUid)
                                    }
                                )
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize()
                                    .background(
                                        Brush.horizontalGradient(listOf(ElectricViolet, VividPurple)),
                                        RoundedCornerShape(14.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Generate Pairing Code", fontWeight = FontWeight.SemiBold, color = Color.White, fontSize = 14.sp)
                            }
                        }
                    } else {
                        // PIN digit boxes with animated border
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            generatedPin!!.forEach { digit ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(max = 52.dp)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(ElevatedSurface)
                                        .border(
                                            1.dp,
                                            ElectricViolet.copy(alpha = if (isWaitingForPartner) pulseAlpha * 0.6f else 0.3f),
                                            RoundedCornerShape(12.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = digit.toString(),
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                }
                            }
                        }

                        if (isWaitingForPartner) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 1.5.dp,
                                    color = CyanAccent.copy(alpha = pulseAlpha)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Waiting for partner…",
                                    fontSize = 12.sp,
                                    color = TextMuted.copy(alpha = pulseAlpha * 0.8f)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Divider
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().alpha(headerAlpha)) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = GlassBorder)
                Text("  OR  ", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                HorizontalDivider(modifier = Modifier.weight(1f), color = GlassBorder)
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Card 2: Enter Code ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        translationY = card2Offset
                        alpha = if (visible) 1f else 0f
                    }
                    .clip(RoundedCornerShape(20.dp))
                    .background(CardSurface)
                    .border(1.dp, GlassBorder, RoundedCornerShape(20.dp))
                    .padding(20.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Dialpad, null, tint = CyanAccent, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Enter Partner's Code", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = inputPin,
                        onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) inputPin = it },
                        label = { Text("6-digit PIN", color = TextMuted, fontSize = 12.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricViolet,
                            unfocusedBorderColor = GlassBorder,
                            cursorColor = CyanAccent,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        textStyle = LocalTextStyle.current.copy(
                            fontSize = 18.sp,
                            letterSpacing = 6.sp,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.SemiBold
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (inputPin.length == 6) {
                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                isLoading = true
                                isError = false
                                statusMessage = ""
                                pairingManager.claimPairingCode(inputPin) { success, partnerUid, message ->
                                    isLoading = false
                                    statusMessage = message ?: ""
                                    if (success && partnerUid != null) {
                                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                        onPairedSuccess(partnerUid)
                                    } else {
                                        view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                                        isError = true
                                    }
                                }
                            }
                        },
                        enabled = inputPin.length == 6 && !isLoading,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            disabledContainerColor = ElevatedSurface
                        ),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize()
                                .background(
                                    if (inputPin.length == 6 && !isLoading)
                                        Brush.horizontalGradient(listOf(CyanAccent, TealGlow))
                                    else
                                        Brush.horizontalGradient(listOf(ElevatedSurface, ElevatedSurface)),
                                    RoundedCornerShape(14.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                            } else {
                                Text(
                                    "Link Devices",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = if (inputPin.length == 6) DeepNavy else TextMuted
                                )
                            }
                        }
                    }
                }
            }

            // Status message with animated entrance
            AnimatedVisibility(
                visible = statusMessage.isNotEmpty(),
                enter = slideInVertically(spring(dampingRatio = 0.7f)) + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isError) ErrorRed.copy(alpha = 0.1f) else SuccessGreen.copy(alpha = 0.1f)
                        )
                        .border(
                            1.dp,
                            if (isError) ErrorRed.copy(alpha = 0.2f) else SuccessGreen.copy(alpha = 0.2f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = statusMessage,
                        color = if (isError) ErrorRed else SuccessGreen,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Skip to Groups
            Spacer(modifier = Modifier.height(24.dp))
            TextButton(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    onSkipToGroups()
                },
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
                Text(
                    "Skip pairing · Use Groups only →",
                    fontSize = 13.sp,
                    color = CyanAccent,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
