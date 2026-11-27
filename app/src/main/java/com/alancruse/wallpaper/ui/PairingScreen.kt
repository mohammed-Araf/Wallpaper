package com.alancruse.wallpaper.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alancruse.wallpaper.pairing.PairingManager
import com.alancruse.wallpaper.ui.components.*
import com.alancruse.wallpaper.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun PairingScreen(
    currentUid: String,
    onPairedSuccess: (String) -> Unit,
    onSkipToGroups: () -> Unit = {},
    onBack: (() -> Unit)? = null,
) {
    val c = LocalWallpaperColors.current
    val haptic = rememberHaptics()
    val pm = remember { PairingManager(currentUid) }
    var pin by remember { mutableStateOf<String?>(null) }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var waiting by remember { mutableStateOf(false) }

    DisposableEffect(pm) { onDispose { pm.cleanup() } }
    LaunchedEffect(waiting) {
        while (waiting) {
            delay(2000)
            pm.checkExistingPairing { p -> if (p != null && waiting) { waiting = false; onPairedSuccess(p) } }
        }
    }
    val pulse by rememberInfiniteTransition(label = "p").animateFloat(0.35f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "a")

    Box(Modifier.fillMaxSize()) {
        AmbientBackground()
        Column(Modifier.fillMaxSize().imePadding()) {
            GlassTopBar(null, onBack)
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = if (onBack == null) 40.dp else 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(Icons.Rounded.Favorite, null, tint = c.accent, modifier = Modifier.size(56.dp))
                Spacer(Modifier.height(8.dp))

                AnimatedContent(pin, label = "pin") { p ->
                    if (p == null) AccentButton({
                        pm.generatePairingCode(
                            onCodeGenerated = { pin = it; waiting = true },
                            onPartnerLinked = { haptic(Haptic.Confirm); waiting = false; onPairedSuccess(it) }
                        )
                    }, Modifier.fillMaxWidth(), text = "My code", icon = Icons.Rounded.QrCode2)
                    else Column(
                        Modifier.fillMaxWidth().glass(RoundedCornerShape(28.dp)).padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CodeChips(p)
                        if (waiting) Icon(Icons.Rounded.Sync, null, tint = c.accent, modifier = Modifier.size(20.dp).alpha(pulse))
                    }
                }

                GlassTextField(
                    input, { if (it.length <= 6 && it.all(Char::isDigit)) input = it }, "Partner's code",
                    leadingIcon = Icons.Rounded.Link,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = TextStyle(fontSize = 20.sp, letterSpacing = 6.sp, fontWeight = FontWeight.SemiBold),
                    trailing = {
                        SendButton(input.length == 6 && !busy, busy) {
                            busy = true; error = null
                            pm.claimPairingCode(input) { ok, partner, msg ->
                                busy = false
                                if (ok && partner != null) { haptic(Haptic.Confirm); onPairedSuccess(partner) }
                                else { error = msg ?: "Invalid code"; haptic(Haptic.Reject) }
                            }
                        }
                    }
                )
                AnimatedVisibility(error != null) {
                    Text(error ?: "", color = ErrorRed, fontSize = 14.sp)
                }

                if (onBack == null) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.glass(RoundedCornerShape(50)).padding(start = 18.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Group, null, tint = c.textSecondary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Groups only", color = c.textSecondary, fontSize = 15.sp)
                        Spacer(Modifier.width(6.dp))
                        BareIconButton(Icons.AutoMirrored.Rounded.ArrowForward, onSkipToGroups, size = 40.dp)
                    }
                }
            }
        }
    }
}
