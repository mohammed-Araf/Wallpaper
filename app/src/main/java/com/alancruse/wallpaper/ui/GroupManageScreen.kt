package com.alancruse.wallpaper.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alancruse.wallpaper.pairing.GroupManager
import com.alancruse.wallpaper.ui.components.*
import com.alancruse.wallpaper.ui.theme.*

@Composable
fun GroupManageScreen(currentUid: String, groupId: String, onBack: () -> Unit) {
    val c = LocalWallpaperColors.current
    val clipboard = LocalClipboardManager.current
    val gm = remember { GroupManager(currentUid) }
    var info by remember { mutableStateOf<GroupManager.GroupInfo?>(null) }
    var members by remember { mutableStateOf<List<GroupManager.MemberInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var toRemove by remember { mutableStateOf<GroupManager.MemberInfo?>(null) }
    var toBlock by remember { mutableStateOf<GroupManager.MemberInfo?>(null) }
    var expanded by remember { mutableStateOf<String?>(null) }
    var showQr by remember { mutableStateOf(false) }
    val isOwner = info?.ownerId == currentUid

    fun refresh() { gm.getGroupDetails(groupId) { i, m -> info = i; members = m; loading = false } }
    LaunchedEffect(groupId) { refresh() }

    Box(Modifier.fillMaxSize()) {
        AmbientBackground()
        Column(Modifier.fillMaxSize()) {
            GlassTopBar(info?.name ?: "", onBack)
            info?.let { g ->
                Column(
                    Modifier.padding(horizontal = 16.dp).fillMaxWidth().glass(RoundedCornerShape(26.dp)).padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CodeChips(g.joinCode, small = true)
                        Spacer(Modifier.weight(1f))
                        BareIconButton(Icons.Rounded.QrCode2, { showQr = !showQr }, active = showQr, contentDescription = "QR")
                        BareIconButton(Icons.Rounded.ContentCopy, { clipboard.setText(AnnotatedString(g.joinCode)) }, contentDescription = "Copy")
                    }
                    AnimatedVisibility(showQr) { QrCode(QrPayload.group(g.joinCode), Modifier.padding(top = 14.dp), size = 180.dp) }
                }
            }
            Spacer(Modifier.height(14.dp))
            if (loading) Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = c.accent, strokeWidth = 2.dp)
            } else LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(members, key = { it.uid }) { m ->
                    val owner = m.uid == info?.ownerId
                    val me = m.uid == currentUid
                    val canManage = isOwner && !me && !owner
                    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp)).clickable(enabled = canManage) { expanded = if (expanded == m.uid) null else m.uid }.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(42.dp).clip(CircleShape).background(if (owner) c.accent else c.textPrimary.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                                Text(m.name.firstOrNull()?.uppercase() ?: "?", color = if (owner) c.onAccent else c.textPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(m.name + if (me) " · you" else "", color = c.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            when {
                                owner -> Icon(Icons.Rounded.WorkspacePremium, "Owner", tint = c.accent)
                                canManage -> BareIconButton(
                                    if (m.canEdit) Icons.Rounded.Edit else Icons.Rounded.Visibility,
                                    { gm.setEditPermission(groupId, m.uid, !m.canEdit) { refresh() } },
                                    size = 38.dp, tint = if (m.canEdit) SuccessGreen else c.textMuted, contentDescription = "Edit permission"
                                )
                                else -> Icon(if (m.canEdit) Icons.Rounded.Edit else Icons.Rounded.Visibility, null, tint = c.textMuted, modifier = Modifier.size(18.dp))
                            }
                        }
                        AnimatedVisibility(expanded == m.uid && canManage) {
                            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                SheetAction(Icons.Rounded.PersonRemove, "Remove", { toRemove = m }, Modifier.weight(1f), color = WarmRose)
                                SheetAction(Icons.Rounded.Block, "Block", { toBlock = m }, Modifier.weight(1f), color = ErrorRed)
                            }
                        }
                    }
                }
            }
        }
    }

    toRemove?.let { m ->
        TimedConfirmDialog("Remove ${m.name}?", "", "Remove", WarmRose, 3,
            onConfirm = { toRemove = null; gm.removeMember(groupId, m.uid) { refresh() } }, onDismiss = { toRemove = null })
    }
    toBlock?.let { m ->
        TimedConfirmDialog("Block ${m.name}?", "", "Block", ErrorRed, 5,
            onConfirm = { toBlock = null; gm.blockMember(groupId, m.uid) { refresh() } }, onDismiss = { toBlock = null })
    }
}
