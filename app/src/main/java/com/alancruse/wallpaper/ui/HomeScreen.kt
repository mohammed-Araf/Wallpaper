package com.alancruse.wallpaper.ui

import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alancruse.wallpaper.ui.components.*
import com.alancruse.wallpaper.ui.theme.*
import com.alancruse.wallpaper.wallpaper.CanvasRenderer
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(
    rooms: List<CanvasRoom>,
    isPaired: Boolean,
    homeRoomId: String?,
    lockRoomId: String?,
    thumbVersion: Int,
    onOpenCanvas: (CanvasRoom) -> Unit,
    onSetHome: (CanvasRoom) -> Unit,
    onSetLock: (CanvasRoom) -> Unit,
    onManageGroup: (String) -> Unit,
    onLeaveGroup: (String) -> Unit,
    onGroups: () -> Unit,
    onPair: () -> Unit,
    onApplyWallpaper: () -> Unit,
    onSchedule: () -> Unit,
    onSettings: () -> Unit,
) {
    val c = LocalWallpaperColors.current
    val context = LocalContext.current
    val haze = remember { HazeState() }
    var actionsFor by remember { mutableStateOf<CanvasRoom?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf<CanvasRoom?>(null) }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().haze(haze)) {
            AmbientBackground()
            LazyVerticalGrid(
                columns = GridCells.Adaptive(160.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 140.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(Modifier.statusBarsPadding().padding(top = 20.dp, bottom = 6.dp, start = 4.dp), verticalAlignment = Alignment.Bottom) {
                        Text("Canvases", color = c.textPrimary, fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp)
                    }
                }
                if (rooms.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { EmptyState(isPaired, onPair, onGroups) }
                items(rooms, key = { it.roomId }) { room ->
                    CanvasTile(
                        room = room,
                        isHome = room.roomId == homeRoomId,
                        isLock = room.roomId == lockRoomId,
                        thumbVersion = thumbVersion,
                        onClick = { onOpenCanvas(room) },
                        onLongClick = { actionsFor = room },
                    )
                }
            }
        }

        CompositionLocalProvider(LocalHazeState provides haze) {
            // Floating dock
            Row(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 18.dp)
                    .glass(CircleShape, strong = true).padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                BareIconButton(Icons.Rounded.Add, { showAdd = true }, size = 52.dp, contentDescription = "Add")
                BareIconButton(Icons.Rounded.Schedule, onSchedule, size = 52.dp, contentDescription = "Schedule")
                Box(
                    Modifier.height(52.dp).clip(CircleShape).background(c.accent)
                        .combinedClickableNoRipple(onApplyWallpaper)
                        .padding(horizontal = 22.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Wallpaper, "Apply wallpaper", tint = c.onAccent, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Live", color = c.onAccent, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    }
                }
                BareIconButton(Icons.Rounded.Settings, onSettings, size = 52.dp, contentDescription = "Settings")
            }

            // Canvas actions
            GlassSheet(actionsFor != null, { actionsFor = null }) {
                val r = actionsFor
                if (r != null) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (r.roomType == "group") Icons.Rounded.Group else Icons.Rounded.Favorite, null, tint = c.accent)
                        Spacer(Modifier.width(10.dp))
                        Text(r.displayName, color = c.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (r.joinCode != null) Text(r.joinCode, color = c.textMuted, fontSize = 14.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SheetAction(Icons.Rounded.Brush, "Open", { actionsFor = null; onOpenCanvas(r) }, Modifier.weight(1f))
                        SheetAction(Icons.Rounded.Home, "Home", { actionsFor = null; onSetHome(r) }, Modifier.weight(1f), active = r.roomId == homeRoomId)
                        SheetAction(Icons.Rounded.Lock, "Lock", { actionsFor = null; onSetLock(r) }, Modifier.weight(1f), active = r.roomId == lockRoomId)
                    }
                    if (r.groupId != null) {
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                            SheetAction(Icons.Rounded.IosShare, "Invite", {
                                actionsFor = null
                                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"; putExtra(Intent.EXTRA_TEXT, r.joinCode ?: "")
                                }, null))
                            }, Modifier.weight(1f))
                            SheetAction(Icons.Rounded.Group, "Members", { actionsFor = null; onManageGroup(r.groupId) }, Modifier.weight(1f))
                            SheetAction(Icons.AutoMirrored.Rounded.Logout, "Leave", { actionsFor = null; leaving = r }, Modifier.weight(1f), color = ErrorRed)
                        }
                    } else Spacer(Modifier.height(8.dp))
                }
            }

            GlassSheet(showAdd, { showAdd = false }) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    if (!isPaired) SheetAction(Icons.Rounded.Favorite, "Partner", { showAdd = false; onPair() }, Modifier.weight(1f))
                    SheetAction(Icons.Rounded.GroupAdd, "Group", { showAdd = false; onGroups() }, Modifier.weight(1f))
                }
            }
        }

        leaving?.let { r ->
            TimedConfirmDialog(
                title = "Leave ${r.displayName}?",
                message = "",
                confirmLabel = "Leave",
                waitSeconds = 3,
                onConfirm = { leaving = null; r.groupId?.let(onLeaveGroup) },
                onDismiss = { leaving = null }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.combinedClickableNoRipple(onClick: () -> Unit): Modifier {
    val haptic = rememberHaptics()
    val src = remember { MutableInteractionSource() }
    return this.pressScale(src, 0.95f).combinedClickable(src, null) { haptic(Haptic.Confirm); onClick() }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CanvasTile(
    room: CanvasRoom, isHome: Boolean, isLock: Boolean, thumbVersion: Int,
    onClick: () -> Unit, onLongClick: () -> Unit,
) {
    val c = LocalWallpaperColors.current
    val context = LocalContext.current
    val haptic = rememberHaptics()
    val src = remember { MutableInteractionSource() }
    var thumb by remember(room.roomId) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(room.roomId, thumbVersion) {
        thumb = withContext(Dispatchers.IO) {
            val f = CanvasRenderer.thumbFile(context, room.roomId)
            if (f.exists()) BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap() else null
        } ?: thumb
    }
    val shape = RoundedCornerShape(30.dp)
    Box(
        Modifier.fillMaxWidth().aspectRatio(0.62f).pressScale(src, 0.96f)
            .glass(shape)
            .combinedClickable(src, null, onLongClick = { haptic(Haptic.Confirm); onLongClick() }) { haptic(Haptic.Tick); onClick() }
    ) {
        val t = thumb
        if (t != null) Image(t, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().padding(5.dp).clip(RoundedCornerShape(26.dp)))
        else Box(
            Modifier.fillMaxSize().padding(5.dp).clip(RoundedCornerShape(26.dp))
                .background(Brush.linearGradient(listOf(c.accent.copy(alpha = 0.35f), c.accent.copy(alpha = 0.05f)))),
            contentAlignment = Alignment.Center
        ) {
            Icon(if (room.roomType == "group") Icons.Rounded.Group else Icons.Rounded.Favorite, null, tint = c.textPrimary.copy(alpha = 0.6f), modifier = Modifier.size(44.dp))
        }

        Row(Modifier.align(Alignment.TopEnd).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (isHome) Badge(Icons.Rounded.Home)
            if (isLock) Badge(Icons.Rounded.Lock)
        }

        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(10.dp)
                .clip(RoundedCornerShape(20.dp)).background(Color.Black.copy(alpha = 0.35f))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(if (room.roomType == "group") Icons.Rounded.Group else Icons.Rounded.Favorite, null, tint = Color.White, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(7.dp))
            Text(room.displayName, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (room.roomType == "group") Text("${room.memberCount}", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun Badge(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    val c = LocalWallpaperColors.current
    Box(Modifier.size(28.dp).clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = c.onAccent, modifier = Modifier.size(15.dp))
    }
}

@Composable
private fun EmptyState(isPaired: Boolean, onPair: () -> Unit, onGroups: () -> Unit) {
    val c = LocalWallpaperColors.current
    Column(Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(96.dp).glass(RoundedCornerShape(32.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Draw, null, tint = c.accent, modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!isPaired) AccentButton(onPair, icon = Icons.Rounded.Favorite, text = "Partner")
            AccentButton(onGroups, icon = Icons.Rounded.GroupAdd, text = "Group", color = if (isPaired) null else Tints.Indigo)
        }
    }
}
