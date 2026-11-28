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

    // The wallpaper service stays alive while it's the wallpaper — the cheapest
    // place to keep the lock screen in sync, with no foreground notification.
    override fun onCreate() { super.onCreate(); LockSync.acquire(this); com.alancruse.wallpaper.live.LiveTracker.acquire(this) }
    override fun onDestroy() { com.alancruse.wallpaper.live.LiveTracker.release(); LockSync.release(); super.onDestroy() }

    inner class WallpaperEngine : Engine() {

        private val handler = Handler(Looper.getMainLooper())
        private val listeners = mutableListOf<Pair<com.google.firebase.database.DatabaseReference, ValueEventListener>>()

        private var strokes = listOf<DrawingStroke>()
        private var stickers = listOf<StickerElement>()
        private var texts = listOf<TextElement>()
        private var images = listOf<ImageElement>()

        private var bgColor = Color.BLACK
        private var currentListeningRoomId: String? = null
        private var visible = false
        private var dirty = true
        private val drawNow = Runnable { if (visible) { dirty = false; drawFrame() } }
        // Live / animated stickers: ~20 fps, only while the home screen is visible.
        private var animated = false
        private var watcher: com.alancruse.wallpaper.live.LiveBoard.Watcher? = null
        private val anim = object : Runnable {
            override fun run() { if (visible && animated) { drawFrame(); handler.postDelayed(this, 50) } }
        }
        private fun startAnim() { handler.removeCallbacks(anim); if (visible && animated) handler.post(anim) }
        private val boardObserver: () -> Unit = { requestDraw() }
        // Drop the network listener after a while off-screen; re-attach (from cache) when shown.
        private val idleStop = Runnable { if (!visible) stopListening() }

        /** Coalesce bursts of changes into one frame, and never draw while hidden. */
        private fun requestDraw() {
            dirty = true
            if (!visible) return
            handler.removeCallbacks(drawNow); handler.postDelayed(drawNow, 32)
        }

        // Listen for wallpaper room changes from the app
        private val prefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "active_room_id") {
                handler.post { startListening() }
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            PairingPrefs(this@LiveWallpaperService).registerOnChangeListener(prefsListener)
            com.alancruse.wallpaper.live.LiveBoard.observe(boardObserver)
            startListening()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            drawFrame()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            this.visible = visible
            if (visible) {
                StickerArt.use24h = android.text.format.DateFormat.is24HourFormat(this@LiveWallpaperService)
                handler.removeCallbacks(idleStop)
                startListening()
                if (dirty) { dirty = false; drawFrame() }
                startAnim()
            } else {
                handler.removeCallbacks(drawNow); handler.removeCallbacks(anim)
                handler.postDelayed(idleStop, 60_000)
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder?) {
            super.onSurfaceDestroyed(holder); stopListening()
        }

        override fun onDestroy() {
            super.onDestroy()
            handler.removeCallbacksAndMessages(null)
            com.alancruse.wallpaper.live.LiveBoard.unobserve(boardObserver)
            stopListening()
            PairingPrefs(this@LiveWallpaperService).unregisterOnChangeListener(prefsListener)
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
            watcher = com.alancruse.wallpaper.live.LiveBoard.Watcher(roomId)

            val roomRef = FirebaseDatabase.getInstance().reference.child("canvas_sessions").child(roomId)

            addListener(roomRef.child("strokes")) { snap ->
                strokes = snap.children.mapNotNull { it.getValue(DrawingStroke::class.java) }
                requestDraw()
            }
            addListener(roomRef.child("stickers")) { snap ->
                stickers = snap.children.mapNotNull { it.getValue(StickerElement::class.java) }
                watcher?.update(stickers)
                animated = stickers.any { it.kind.isNotEmpty() }
                startAnim()
                requestDraw()
            }
            addListener(roomRef.child("texts")) { snap ->
                texts = snap.children.mapNotNull { it.getValue(TextElement::class.java) }
                requestDraw()
            }
            addListener(roomRef.child("images")) { snap ->
                images = snap.children.mapNotNull { it.getValue(ImageElement::class.java) }
                requestDraw()
            }
            addListener(roomRef.child("backgroundColor")) { snap ->
                val colorArgb = snap.getValue(Int::class.java)
                bgColor = colorArgb ?: Color.BLACK
                requestDraw()
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
            watcher?.close(); watcher = null
            listeners.clear()
            currentListeningRoomId = null
        }

        private fun drawFrame() {
            val holder = surfaceHolder ?: return
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas != null) {
                    CanvasRenderer.drawOnCanvas(
                        canvas, bgColor, strokes, stickers, texts, images,
                        timeMs = android.os.SystemClock.uptimeMillis(), roomId = currentListeningRoomId
                    )
                }
            } catch (e: Exception) {
                Log.e("LiveWallpaper", "Draw error", e)
            } finally {
                if (canvas != null) try { holder.unlockCanvasAndPost(canvas) } catch (_: Exception) {}
            }
        }
    }
}
