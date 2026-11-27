package com.alancruse.wallpaper.ui

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alancruse.wallpaper.pairing.GroupManager
import com.alancruse.wallpaper.ui.components.*
import com.alancruse.wallpaper.ui.theme.*

@Composable
fun GroupScreen(
    currentUid: String,
    displayName: String,
    onGroupCreated: (groupId: String, roomId: String) -> Unit,
    onGroupJoined: (groupId: String, roomId: String) -> Unit,
    onBack: () -> Unit
) {
    val c = LocalWallpaperColors.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val haptic = rememberHaptics()
    val gm = remember { GroupManager(currentUid) }
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var created by remember { mutableStateOf<Pair<String, String>?>(null) } // groupId, code

    fun join(jc: String) {
        busy = true; error = null
        gm.joinGroup(jc, displayName) { ok, gid, _, err ->
            busy = false
            if (ok && gid != null) { haptic(Haptic.Confirm); onGroupJoined(gid, "group_$gid") }
            else { error = err ?: "Failed"; haptic(Haptic.Reject) }
        }
    }

    Box(Modifier.fillMaxSize()) {
        AmbientBackground()
        Column(Modifier.fillMaxSize().imePadding()) {
            GlassTopBar("Groups", onBack)
            Column(Modifier.padding(horizontal = 16.dp).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                AnimatedContent(created, label = "create") { cr ->
                    if (cr == null) {
                        GlassTextField(
                            name, { if (it.length <= 20) name = it }, "New group",
                            leadingIcon = Icons.Rounded.GroupAdd,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                            trailing = {
                                SendButton(enabled = name.isNotBlank() && !busy, busy = busy) {
                                    busy = true; error = null
                                    gm.createGroup(name.trim(), displayName) { ok, gid, jc, err ->
                                        busy = false
                                        if (ok && gid != null && jc != null) { created = gid to jc; haptic(Haptic.Confirm) }
                                        else { error = err ?: "Failed"; haptic(Haptic.Reject) }
                                    }
                                }
                            }
                        )
                    } else {
                        Column(
                            Modifier.fillMaxWidth().glass(RoundedCornerShape(28.dp)).padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            QrCode(QrPayload.group(cr.second), size = 180.dp)
                            CodeChips(cr.second)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                GlassIconButton(Icons.Rounded.ContentCopy, { clipboard.setText(AnnotatedString(cr.second)) }, contentDescription = "Copy")
                                GlassIconButton(Icons.Rounded.IosShare, {
                                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, cr.second) }, null))
                                }, contentDescription = "Share")
                                AccentButton({ onGroupCreated(cr.first, "group_${cr.first}") }, icon = Icons.Rounded.Brush, height = 46.dp)
                            }
                        }
                    }
                }

                GlassTextField(
                    code, { if (it.length <= 6) code = it.uppercase().filter { ch -> ch.isLetterOrDigit() } }, "Join code",
                    leadingIcon = Icons.Rounded.Key,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    textStyle = TextStyle(fontSize = 20.sp, letterSpacing = 6.sp, fontWeight = FontWeight.SemiBold),
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BareIconButton(Icons.Rounded.QrCodeScanner, {
                                scanQr(context) { raw ->
                                    if (raw == null) return@scanQr
                                    val jc = QrPayload.parse(raw, "group")?.uppercase()
                                    if (jc == null) { error = "Not a group code"; haptic(Haptic.Reject) } else { code = jc; join(jc) }
                                }
                            }, size = 46.dp, contentDescription = "Scan")
                            SendButton(enabled = code.length == 6 && !busy, busy = busy) { join(code) }
                        }
                    }
                )

                AnimatedVisibility(error != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp)) {
                        Icon(Icons.Rounded.ErrorOutline, null, tint = ErrorRed, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(error ?: "", color = ErrorRed, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
internal fun SendButton(enabled: Boolean, busy: Boolean, onClick: () -> Unit) {
    val c = LocalWallpaperColors.current
    if (busy) Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(22.dp), color = c.accent, strokeWidth = 2.dp) }
    else BareIconButton(Icons.AutoMirrored.Rounded.ArrowForward, onClick, size = 46.dp, active = enabled, enabled = enabled)
}

@Composable
internal fun CodeChips(code: String, small: Boolean = false) {
    val c = LocalWallpaperColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(if (small) 4.dp else 8.dp)) {
        code.forEach { ch ->
            Box(
                Modifier.size(if (small) 30.dp else 44.dp, if (small) 38.dp else 54.dp).clip(RoundedCornerShape(if (small) 9.dp else 14.dp))
                    .background(c.textPrimary.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) { Text(ch.toString(), color = c.textPrimary, fontSize = if (small) 16.sp else 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) }
        }
    }
}
