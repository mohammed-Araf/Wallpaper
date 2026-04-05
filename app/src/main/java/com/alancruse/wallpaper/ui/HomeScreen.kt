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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alancruse.wallpaper.pairing.GroupManager
import com.alancruse.wallpaper.pairing.PairingPrefs
import com.alancruse.wallpaper.ui.theme.*
import kotlinx.coroutines.delay

data class CanvasRoom(
    val roomId: String,
    val roomType: String,      // "couple" or "group"
    val displayName: String,
    val subtitle: String,
    val emoji: String,
    val groupId: String? = null // null for couple
)

@Composable
fun HomeScreen(
    currentUid: String,
    partnerUid: String?,
    groups: List<GroupManager.GroupInfo>,
    activeWallpaperRoomId: String?,
    lockscreenRoomId: String?,
    onOpenCanvas: (roomId: String, roomType: String) -> Unit,
    onSetWallpaper: (roomId: String, roomType: String) -> Unit,
    onSetLockscreen: (roomId: String, roomType: String) -> Unit,
    onActivateWallpaper: () -> Unit,
    onCreateGroup: () -> Unit,
    onJoinGroup: () -> Unit,
    onLeaveGroup: (groupId: String) -> Unit,
    onUnpair: () -> Unit,
    onSignOut: () -> Unit,
    onAutoSwitch: () -> Unit
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

    // Ambient glow
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

    // Build room list
    val rooms = remember(partnerUid, groups) {
        buildList {
            if (!partnerUid.isNullOrEmpty()) {
                val coupleRoomId = if (currentUid < partnerUid) "${currentUid}_${partnerUid}"
                else "${partnerUid}_${currentUid}"
                add(CanvasRoom(coupleRoomId, "couple", "Partner Canvas", "Draw on each other's wallpaper", "❤️"))
            }
            groups.forEach { g ->
                add(CanvasRoom(g.roomId, "group", g.name, "${g.memberCount} members · ${g.joinCode}", "👥", g.groupId))
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(DeepNavy)) {
        // Floating orbs
        Box(
            modifier = Modifier
                .offset(x = (-30).dp, y = (-60 + orbFloat).dp)
                .size(200.dp).blur(100.dp)
                .background(ElectricViolet.copy(alpha = 0.1f), CircleShape)
        )
        Box(
            modifier = Modifier
                .offset(x = 140.dp, y = (480 - orbFloat).dp)
                .size(180.dp).blur(90.dp)
                .background(CyanAccent.copy(alpha = 0.08f), CircleShape)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 56.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Header ──
            Box(
                modifier = Modifier
                    .scale(headerScale)
                    .alpha(headerAlpha)
                    .size(48.dp)
                    .background(SuccessGreen.copy(alpha = 0.1f), CircleShape)
                    .border(1.5.dp, SuccessGreen.copy(alpha = statusPulse * 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.FavoriteBorder, "Connected",
                    tint = SuccessGreen.copy(alpha = statusPulse),
                    modifier = Modifier.size(22.dp))
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text("Your Canvases", fontSize = 22.sp, fontWeight = FontWeight.Bold,
                color = TextPrimary, letterSpacing = (-0.5).sp, modifier = Modifier.alpha(headerAlpha))
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                "${rooms.size} canvas${if (rooms.size != 1) "es" else ""} · tap ★ to set as wallpaper",
                fontSize = 12.sp, color = TextMuted, modifier = Modifier.alpha(headerAlpha)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ── Canvas list ──
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(rooms, key = { it.roomId }) { room ->
                    val isHomeActive = room.roomId == activeWallpaperRoomId
                    val isLockActive = room.roomId == lockscreenRoomId
                    CanvasCard(
                        room = room,
                        isHomeWallpaper = isHomeActive,
                        isLockWallpaper = isLockActive,
                        onOpen = {
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            onOpenCanvas(room.roomId, room.roomType)
                        },
                        onSetHomeWallpaper = {
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            onSetWallpaper(room.roomId, room.roomType)
                        },
                        onSetLockWallpaper = {
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            onSetLockscreen(room.roomId, room.roomType)
                        },
                        onLeave = if (room.roomType == "group" && room.groupId != null) {
                            {
                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                onLeaveGroup(room.groupId)
                            }
                        } else null
                    )
                }

                // Empty state
                if (rooms.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(CardSurface)
                                .border(1.dp, GlassBorder, RoundedCornerShape(20.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No canvases yet.\nPair with a partner or create a group!",
                                fontSize = 13.sp, color = TextMuted, textAlign = TextAlign.Center)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Action buttons ──
            Row(
                modifier = Modifier.fillMaxWidth().alpha(headerAlpha),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Create Group
                SmallActionButton(
                    icon = Icons.Rounded.GroupAdd,
                    label = "Create",
                    color = ElectricViolet,
                    modifier = Modifier.weight(1f),
                    onClick = { view.performHapticFeedback(HapticFeedbackConstants.CONFIRM); onCreateGroup() }
                )
                // Join Group
                SmallActionButton(
                    icon = Icons.Rounded.Link,
                    label = "Join",
                    color = CyanAccent,
                    modifier = Modifier.weight(1f),
                    onClick = { view.performHapticFeedback(HapticFeedbackConstants.CONFIRM); onJoinGroup() }
                )
                // Auto-Switch
                SmallActionButton(
                    icon = Icons.Rounded.Schedule,
                    label = "Schedule",
                    color = TealGlow,
                    modifier = Modifier.weight(1f),
                    onClick = { view.performHapticFeedback(HapticFeedbackConstants.CONFIRM); onAutoSwitch() }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Set Wallpaper button
            Button(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    onActivateWallpaper()
                },
                modifier = Modifier.fillMaxWidth().height(42.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues(0.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize()
                        .background(Brush.horizontalGradient(listOf(CyanAccent, TealGlow)), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Wallpaper, null, tint = DeepNavy, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Set as Live Wallpaper", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = DeepNavy)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Bottom row
            Row(
                modifier = Modifier.fillMaxWidth().alpha(headerAlpha),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (partnerUid != null) {
                    OutlinedButton(
                        onClick = { view.performHapticFeedback(HapticFeedbackConstants.CONFIRM); onUnpair() },
                        modifier = Modifier.weight(1f).height(38.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, WarmRose.copy(alpha = 0.3f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = WarmRose)
                    ) {
                        Icon(Icons.Rounded.LinkOff, null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Unpair", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                }
                OutlinedButton(
                    onClick = { view.performHapticFeedback(HapticFeedbackConstants.CONFIRM); onSignOut() },
                    modifier = Modifier.weight(1f).height(38.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, GlassBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMuted)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Logout, null, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Sign Out", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

// ── Canvas Card ──

@Composable
private fun CanvasCard(
    room: CanvasRoom,
    isHomeWallpaper: Boolean,
    isLockWallpaper: Boolean,
    onOpen: () -> Unit,
    onSetHomeWallpaper: () -> Unit,
    onSetLockWallpaper: () -> Unit,
    onLeave: (() -> Unit)?
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f), label = "pressScale"
    )

    var showWallpaperMenu by remember { mutableStateOf(false) }

    val isActive = isHomeWallpaper || isLockWallpaper
    val activeGlow = if (isActive) ElectricViolet.copy(alpha = 0.3f) else GlassBorder

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
            .clip(RoundedCornerShape(18.dp))
            .background(CardSurface)
            .border(1.dp, activeGlow, RoundedCornerShape(18.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onOpen)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Emoji badge
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(if (isActive) ElectricViolet.copy(alpha = 0.15f) else ElevatedSurface),
                contentAlignment = Alignment.Center
            ) {
                Text(room.emoji, fontSize = 20.sp)
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    room.displayName, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    room.subtitle, fontSize = 11.sp, color = TextMuted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                if (isHomeWallpaper || isLockWallpaper) {
                    Spacer(modifier = Modifier.height(2.dp))
                    val labels = buildList {
                        if (isHomeWallpaper) add("🏠 Home")
                        if (isLockWallpaper) add("🔒 Lock")
                    }.joinToString(" · ")
                    Text(labels, fontSize = 10.sp, color = ElectricViolet, fontWeight = FontWeight.Medium)
                }
            }

            // Actions
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box {
                    IconButton(
                        onClick = { showWallpaperMenu = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            if (isActive) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                            "Set Wallpaper",
                            tint = if (isActive) ElectricViolet else TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showWallpaperMenu,
                        onDismissRequest = { showWallpaperMenu = false },
                        modifier = Modifier.background(CardSurface)
                    ) {
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(if (isHomeWallpaper) "✅" else "🏠", fontSize = 14.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Home Screen", fontSize = 13.sp, color = TextPrimary)
                                }
                            },
                            onClick = { showWallpaperMenu = false; onSetHomeWallpaper() }
                        )
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(if (isLockWallpaper) "✅" else "🔒", fontSize = 14.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Lock Screen", fontSize = 13.sp, color = TextPrimary)
                                }
                            },
                            onClick = { showWallpaperMenu = false; onSetLockWallpaper() }
                        )
                    }
                }
                // Leave group
                if (onLeave != null) {
                    IconButton(
                        onClick = onLeave,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Rounded.Close, "Leave", tint = WarmRose.copy(alpha = 0.5f), modifier = Modifier.size(12.dp))
                    }
                }
            }
        }
    }
}

// ── Small Action Button ──

@Composable
private fun SmallActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(38.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
        contentPadding = PaddingValues(horizontal = 8.dp)
    ) {
        Icon(icon, null, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}
