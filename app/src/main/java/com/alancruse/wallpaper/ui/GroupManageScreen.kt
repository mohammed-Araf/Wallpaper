package com.alancruse.wallpaper.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alancruse.wallpaper.pairing.GroupManager
import com.alancruse.wallpaper.ui.theme.*

@Composable
fun GroupManageScreen(
    currentUid: String,
    groupId: String,
    onBack: () -> Unit
) {
    val view = LocalView.current
    val clipboardManager = LocalClipboardManager.current
    val groupManager = remember { GroupManager(currentUid) }

    var groupInfo by remember { mutableStateOf<GroupManager.GroupInfo?>(null) }
    var members by remember { mutableStateOf<List<GroupManager.MemberInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var statusMessage by remember { mutableStateOf("") }

    // Dialog states
    var memberToRemove by remember { mutableStateOf<GroupManager.MemberInfo?>(null) }
    var memberToBlock by remember { mutableStateOf<GroupManager.MemberInfo?>(null) }

    val isOwner = groupInfo?.ownerId == currentUid

    // Load group details
    fun refresh() {
        isLoading = true
        groupManager.getGroupDetails(groupId) { info, memberList ->
            groupInfo = info
            members = memberList
            isLoading = false
        }
    }

    LaunchedEffect(groupId) { refresh() }

    // Entrance animation
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(80); visible = true }
    val headerAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(500), label = "headerAlpha"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "mgOrb")
    val orbFloat by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 12f,
        animationSpec = infiniteRepeatable(
            animation = tween(3500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "orbF"
    )

    // Remove dialog
    memberToRemove?.let { member ->
        TimedConfirmDialog(
            title = "Remove ${member.name}?",
            message = "They will be removed from the group but can rejoin with the code.",
            confirmLabel = "Remove",
            accentColor = WarmRose,
            waitSeconds = 3,
            onConfirm = {
                memberToRemove = null
                groupManager.removeMember(groupId, member.uid) { refresh() }
            },
            onDismiss = { memberToRemove = null }
        )
    }

    // Block dialog
    memberToBlock?.let { member ->
        TimedConfirmDialog(
            title = "Block ${member.name}?",
            message = "They will be removed and won't be able to rejoin this group.",
            confirmLabel = "Block",
            accentColor = ErrorRed,
            waitSeconds = 5,
            onConfirm = {
                memberToBlock = null
                groupManager.blockMember(groupId, member.uid) { refresh() }
            },
            onDismiss = { memberToBlock = null }
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(DeepNavy)) {
        // Orbs
        Box(
            modifier = Modifier
                .offset(x = (-40).dp, y = (-50 + orbFloat).dp)
                .size(180.dp).blur(90.dp)
                .background(ElectricViolet.copy(alpha = 0.1f), CircleShape)
        )
        Box(
            modifier = Modifier
                .offset(x = 150.dp, y = (400 - orbFloat).dp)
                .size(160.dp).blur(80.dp)
                .background(CyanAccent.copy(alpha = 0.08f), CircleShape)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 56.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().alpha(headerAlpha),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    onBack()
                }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = TextMuted)
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column {
                    Text(
                        text = groupInfo?.name ?: "Group",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        text = if (isOwner) "Owner · ${members.size} members" else "${members.size} members",
                        fontSize = 12.sp, color = TextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Join code card (always visible)
            if (groupInfo != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(CardSurface)
                        .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Key, null, tint = CyanAccent, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Join Code", fontSize = 12.sp, color = TextMuted, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.weight(1f))

                        // Code characters
                        groupInfo!!.joinCode.forEach { ch ->
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(ElevatedSurface)
                                    .border(1.dp, ElectricViolet.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(ch.toString(), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            }
                            Spacer(modifier = Modifier.width(3.dp))
                        }

                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                clipboardManager.setText(AnnotatedString(groupInfo!!.joinCode))
                                statusMessage = "Code copied!"
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Rounded.ContentCopy, "Copy", tint = TextMuted, modifier = Modifier.size(14.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Status message
            AnimatedVisibility(
                visible = statusMessage.isNotEmpty(),
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SuccessGreen.copy(alpha = 0.1f))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(statusMessage, fontSize = 12.sp, color = SuccessGreen, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth())
                }
                LaunchedEffect(statusMessage) {
                    kotlinx.coroutines.delay(2000)
                    statusMessage = ""
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Section header
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Group, null, tint = TextMuted, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Members", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextMuted)
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Members list
            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = CyanAccent)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(members, key = { it.uid }) { member ->
                        MemberRow(
                            member = member,
                            isOwner = isOwner,
                            isCurrentUser = member.uid == currentUid,
                            isGroupOwner = member.uid == groupInfo?.ownerId,
                            onToggleEdit = {
                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                groupManager.setEditPermission(groupId, member.uid, !member.canEdit) {
                                    refresh()
                                }
                            },
                            onRemove = {
                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                memberToRemove = member
                            },
                            onBlock = {
                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                memberToBlock = member
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MemberRow(
    member: GroupManager.MemberInfo,
    isOwner: Boolean,
    isCurrentUser: Boolean,
    isGroupOwner: Boolean,
    onToggleEdit: () -> Unit,
    onRemove: () -> Unit,
    onBlock: () -> Unit
) {
    var showActions by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardSurface)
            .border(
                1.dp,
                if (isGroupOwner) ElectricViolet.copy(alpha = 0.3f) else GlassBorder,
                RoundedCornerShape(14.dp)
            )
            .clickable(enabled = isOwner && !isCurrentUser) { showActions = !showActions }
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Avatar
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (isGroupOwner) ElectricViolet.copy(alpha = 0.15f)
                            else ElevatedSurface
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = member.name.firstOrNull()?.uppercase() ?: "?",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isGroupOwner) ElectricViolet else TextMuted
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = member.name,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isCurrentUser) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("(you)", fontSize = 11.sp, color = TextMuted)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isGroupOwner) {
                            Text("👑 Owner", fontSize = 11.sp, color = ElectricViolet, fontWeight = FontWeight.Medium)
                        } else {
                            Text(
                                if (member.canEdit) "✏️ Can edit" else "👁️ View only",
                                fontSize = 11.sp,
                                color = if (member.canEdit) SuccessGreen else TextMuted
                            )
                        }
                    }
                }

                // Toggle edit permission icon (quick toggle for owner)
                if (isOwner && !isCurrentUser && !isGroupOwner) {
                    IconButton(
                        onClick = onToggleEdit,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            if (member.canEdit) Icons.Rounded.Edit else Icons.Rounded.EditOff,
                            "Toggle edit",
                            tint = if (member.canEdit) SuccessGreen else TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Expanded action buttons (owner only, not for self or group owner)
            AnimatedVisibility(
                visible = showActions && isOwner && !isCurrentUser && !isGroupOwner,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Remove
                    OutlinedButton(
                        onClick = onRemove,
                        modifier = Modifier.weight(1f).height(34.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, WarmRose.copy(alpha = 0.3f)),
                        contentPadding = PaddingValues(4.dp)
                    ) {
                        Icon(Icons.Rounded.PersonRemove, null, modifier = Modifier.size(13.dp), tint = WarmRose)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Remove", fontSize = 11.sp, color = WarmRose)
                    }

                    // Block
                    OutlinedButton(
                        onClick = onBlock,
                        modifier = Modifier.weight(1f).height(34.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ErrorRed.copy(alpha = 0.3f)),
                        contentPadding = PaddingValues(4.dp)
                    ) {
                        Icon(Icons.Rounded.Block, null, modifier = Modifier.size(13.dp), tint = ErrorRed)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Block", fontSize = 11.sp, color = ErrorRed)
                    }
                }
            }
        }
    }
}
