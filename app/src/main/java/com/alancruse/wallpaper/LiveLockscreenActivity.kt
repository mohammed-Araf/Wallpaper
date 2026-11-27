package com.alancruse.wallpaper

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import com.alancruse.wallpaper.canvas.CanvasSyncManager
import com.alancruse.wallpaper.pairing.PairingPrefs
import com.alancruse.wallpaper.ui.CanvasRoom
import com.alancruse.wallpaper.ui.LiveCanvasScreen
import com.alancruse.wallpaper.ui.theme.WallPaperTheme
import com.alancruse.wallpaper.wallpaper.LockscreenWallpaperService
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

/** Canvas editor. Hosts room switching in place, so changing canvas never restarts the screen. */
class LiveLockscreenActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = PairingPrefs(this)
        val startRoom = intent.getStringExtra("roomId") ?: run { finish(); return }
        val rooms = CanvasRoom.readAll(intent).ifEmpty { listOf(CanvasRoom(startRoom, "group", "Canvas")) }
        val uid = FirebaseAuth.getInstance().currentUser?.uid

        setContent {
            WallPaperTheme {
                var room by remember { mutableStateOf(rooms.firstOrNull { it.roomId == startRoom } ?: rooms.first()) }
                var homeId by remember { mutableStateOf(prefs.getWallpaperRoomId()) }
                var lockId by remember { mutableStateOf(prefs.lockscreenRoomId) }
                var readOnly by remember(room.roomId) { mutableStateOf(false) }

                // Group edit permission (owner can switch members to view-only)
                DisposableEffect(room.roomId, uid) {
                    val gid = room.groupId
                    if (gid == null || uid == null) return@DisposableEffect onDispose {}
                    val ref = FirebaseDatabase.getInstance().reference.child("groups").child(gid).child("members").child(uid).child("canEdit")
                    val l = object : ValueEventListener {
                        override fun onDataChange(s: DataSnapshot) { readOnly = s.getValue(Boolean::class.java) == false }
                        override fun onCancelled(error: DatabaseError) {}
                    }
                    ref.addValueEventListener(l)
                    onDispose { ref.removeEventListener(l) }
                }

                Crossfade(room, animationSpec = tween(220), label = "room") { r ->
                    val sync = remember(r.roomId) { CanvasSyncManager.forRoom(r.roomId) }
                    DisposableEffect(sync) { onDispose { sync.cleanup() } }
                    LiveCanvasScreen(
                        syncManager = sync,
                        rooms = rooms,
                        readOnly = readOnly && r.roomId == room.roomId,
                        homeRoomId = homeId,
                        lockRoomId = lockId,
                        onSwitchRoom = { room = it },
                        onSetHome = {
                            prefs.setActiveWallpaper(r.roomId, r.roomType); homeId = r.roomId
                            Toast.makeText(this@LiveLockscreenActivity, "🏠", Toast.LENGTH_SHORT).show()
                        },
                        onSetLock = {
                            prefs.setLockscreenWallpaper(r.roomId, r.roomType); lockId = r.roomId
                            LockscreenWallpaperService.start(this@LiveLockscreenActivity)
                            Toast.makeText(this@LiveLockscreenActivity, "🔒", Toast.LENGTH_SHORT).show()
                        },
                        onDone = { finish() },
                    )
                }
            }
        }
    }
}
