package com.alancruse.wallpaper.ui

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
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
    onDeveloperBypass: (() -> Unit)? = null,
) {
    val c = LocalWallpaperColors.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val haptic = rememberHaptics()
    val pm = remember { PairingManager(currentUid) }
    var pin by remember { mutableStateOf<String?>(null) }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var waiting by remember { mutableStateOf(false) }

    fun claim(code: String) {
        busy = true; error = null
        pm.claimPairingCode(code) { ok, partner, msg ->
            busy = false
            if (ok && partner != null) { haptic(Haptic.Confirm); waiting = false; onPairedSuccess(partner) }
            else { error = msg ?: "Invalid code"; haptic(Haptic.Reject) }
        }
    }

    fun scan() = scanQr(context) { raw ->
        if (raw == null) return@scanQr
        val code = QrPayload.parse(raw, "pair")?.takeIf { it.all(Char::isDigit) }
        if (code == null) { error = "Not a pairing code"; haptic(Haptic.Reject) }
        else if (code == pin) { error = "That's your code"; haptic(Haptic.Reject) }
        else { input = code; claim(code) }
    }

    DisposableEffect(pm) { onDispose { pm.cleanup() } }
    LaunchedEffect(Unit) {
        pm.generatePairingCode(
            onCodeGenerated = { pin = it; waiting = true },
            onPartnerLinked = { haptic(Haptic.Confirm); waiting = false; onPairedSuccess(it) }
        )
    }
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
            GlassTopBar(null, onBack) {
                if (onDeveloperBypass != null) BareIconButton(Icons.Rounded.DeveloperMode, onDeveloperBypass, tint = c.accent, contentDescription = "Developer")
            }
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // My code
                Column(
                    Modifier.fillMaxWidth().enterAnim(0).glass(RoundedCornerShape(30.dp)).padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    AnimatedContent(pin, label = "pin") { p ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            if (p == null) Box(Modifier.size(196.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = c.accent, strokeWidth = 2.dp)
                            } else {
                                QrCode(QrPayload.pair(p), size = 196.dp)
                                CodeChips(p)
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (waiting) Icon(Icons.Rounded.Sync, null, tint = c.accent, modifier = Modifier.size(20.dp).alpha(pulse))
                        pin?.let { p ->
                            BareIconButton(Icons.Rounded.ContentCopy, { clipboard.setText(AnnotatedString(p)) }, size = 40.dp, contentDescription = "Copy")
                            BareIconButton(Icons.Rounded.IosShare, {
                                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, p) }, null))
                            }, size = 40.dp, contentDescription = "Share")
                        }
                    }
                }

                // Partner's code
                AccentButton(::scan, Modifier.fillMaxWidth().enterAnim(1), text = "Scan", icon = Icons.Rounded.QrCodeScanner, loading = busy)
                GlassTextField(
                    input, { if (it.length <= 6 && it.all(Char::isDigit)) input = it }, "Partner's code", Modifier.enterAnim(2),
                    leadingIcon = Icons.Rounded.Link,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = TextStyle(fontSize = 20.sp, letterSpacing = 6.sp, fontWeight = FontWeight.SemiBold),
                    trailing = { SendButton(input.length == 6 && !busy, busy) { claim(input) } }
                )
                AnimatedVisibility(error != null) {
                    Text(error ?: "", color = ErrorRed, fontSize = 14.sp)
                }

                if (onBack == null) {
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
