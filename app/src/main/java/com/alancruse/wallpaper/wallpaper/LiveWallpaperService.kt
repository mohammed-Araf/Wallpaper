package com.alancruse.wallpaper.wallpaper

import android.graphics.Canvas
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.SurfaceHolder
import com.alancruse.wallpaper.canvas.DrawingStroke
import com.alancruse.wallpaper.canvas.ImageElement
import com.alancruse.wallpaper.canvas.StickerElement
import com.alancruse.wallpaper.canvas.TextElement
import com.alancruse.wallpaper.pairing.PairingPrefs
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class LiveWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = WallpaperEngine()

    inner class WallpaperEngine : Engine() {

        private val handler = Handler(Looper.getMainLooper())
        private val listeners = mutableListOf<Pair<com.google.firebase.database.DatabaseReference, ValueEventListener>>()

        private var strokes = listOf<DrawingStroke>()
        private var stickers = listOf<StickerElement>()
        private var texts = listOf<TextElement>()
        private var images = listOf<ImageElement>()

        private var bgColor = Color.parseColor("#0D0D1A")
        private var currentListeningRoomId: String? = null

        // Listen for wallpaper room changes from the app
        private val prefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "active_room_id") {
                handler.post { startListening() }
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            PairingPrefs(this@LiveWallpaperService).registerOnChangeListener(prefsListener)
            startListening()
            // After the live wallpaper is applied, override lock screen if a separate one is set
            triggerLockscreenOverride()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            drawFrame()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            if (visible) {
                startListening()
                drawFrame()
                // Re-apply lock screen override each time wallpaper becomes visible
                triggerLockscreenOverride()
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder?) {
            super.onSurfaceDestroyed(holder); stopListening()
        }

        override fun onDestroy() {
            super.onDestroy()
            stopListening()
            PairingPrefs(this@LiveWallpaperService).unregisterOnChangeListener(prefsListener)
        }

        /**
         * If the user has a separate lock screen canvas configured,
         * start the LockscreenWallpaperService to override the lock screen
         * with a static bitmap (using FLAG_LOCK).
         * Delayed by 2s to let Android finish applying the live wallpaper.
         */
        private fun triggerLockscreenOverride() {
            val prefs = PairingPrefs(this@LiveWallpaperService)
            if (prefs.lockscreenRoomId != null) {
                handler.postDelayed({
                    LockscreenWallpaperService.start(this@LiveWallpaperService)
                }, 2000L)
            }
        }

        private fun startListening() {
            val prefs = PairingPrefs(this@LiveWallpaperService)

            // Get the active wallpaper room ID (supports both couple and group)
            val roomId = prefs.getWallpaperRoomId()

            if (roomId == null) {
                stopListening()
                strokes = emptyList(); stickers = emptyList()
                texts = emptyList(); images = emptyList()
                drawFrame(); return
            }

            // Skip if already listening to this room
            if (roomId == currentListeningRoomId && listeners.isNotEmpty()) return

            stopListening()
            currentListeningRoomId = roomId

            val roomRef = FirebaseDatabase.getInstance().reference.child("canvas_sessions").child(roomId)

            addListener(roomRef.child("strokes")) { snap ->
                strokes = snap.children.mapNotNull { it.getValue(DrawingStroke::class.java) }
                drawFrame()
            }
            addListener(roomRef.child("stickers")) { snap ->
                stickers = snap.children.mapNotNull { it.getValue(StickerElement::class.java) }
                drawFrame()
            }
            addListener(roomRef.child("texts")) { snap ->
                texts = snap.children.mapNotNull { it.getValue(TextElement::class.java) }
                drawFrame()
            }
            addListener(roomRef.child("images")) { snap ->
                images = snap.children.mapNotNull { it.getValue(ImageElement::class.java) }
                drawFrame()
            }
            addListener(roomRef.child("backgroundColor")) { snap ->
                val colorArgb = snap.getValue(Int::class.java)
                bgColor = colorArgb ?: Color.parseColor("#0D0D1A")
                drawFrame()
            }
        }

        private fun addListener(ref: com.google.firebase.database.DatabaseReference, onData: (DataSnapshot) -> Unit) {
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) = onData(snapshot)
                override fun onCancelled(error: DatabaseError) {
                    Log.e("LiveWallpaper", "Firebase error: ${error.message}")
                }
            }
            ref.addValueEventListener(listener)
            listeners.add(ref to listener)
        }

        private fun stopListening() {
            for ((ref, l) in listeners) ref.removeEventListener(l)
            listeners.clear()
            currentListeningRoomId = null
        }

        private fun drawFrame() {
            val holder = surfaceHolder ?: return
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas != null) {
                    CanvasRenderer.drawOnCanvas(canvas, bgColor, strokes, stickers, texts, images)
                }
            } catch (e: Exception) {
                Log.e("LiveWallpaper", "Draw error", e)
            } finally {
                if (canvas != null) try { holder.unlockCanvasAndPost(canvas) } catch (_: Exception) {}
            }
        }
    }
}
