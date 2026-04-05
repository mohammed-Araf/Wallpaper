package com.alancruse.wallpaper

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.alancruse.wallpaper.canvas.CanvasSyncManager
import com.alancruse.wallpaper.ui.LiveCanvasScreen
import com.alancruse.wallpaper.ui.theme.DeepNavy
import com.alancruse.wallpaper.ui.theme.TextMuted
import com.alancruse.wallpaper.ui.theme.WallPaperTheme

class LiveLockscreenActivity : ComponentActivity() {

    private var syncManager: CanvasSyncManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Prefer direct roomId (new flow), fall back to uid pair (legacy)
        val roomId = intent.getStringExtra("roomId")
        val partnerUid = intent.getStringExtra("partnerUid") ?: ""
        val currentUid = intent.getStringExtra("currentUid") ?: ""

        Log.d("Canvas", "Opening canvas: roomId=$roomId, current=$currentUid, partner=$partnerUid")

        syncManager = try {
            if (!roomId.isNullOrEmpty()) {
                // Direct room ID — for groups or any room
                CanvasSyncManager.forRoom(roomId)
            } else if (currentUid.isNotEmpty() && partnerUid.isNotEmpty()) {
                // Legacy couple flow
                CanvasSyncManager(currentUid, partnerUid)
            } else {
                Log.e("Canvas", "Missing UIDs! roomId=$roomId, current=$currentUid, partner=$partnerUid")
                setContent {
                    WallPaperTheme {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Error: Missing user data. Please re-pair.", color = TextMuted)
                        }
                    }
                }
                return
            }
        } catch (e: Exception) {
            Log.e("Canvas", "Failed to create sync manager", e)
            setContent {
                WallPaperTheme {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Error: Could not connect. Check your internet.", color = TextMuted)
                    }
                }
            }
            return
        }

        setContent {
            WallPaperTheme {
                LiveCanvasScreen(
                    syncManager = syncManager!!,
                    onDone = { finish() }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        syncManager?.cleanup()
    }
}
