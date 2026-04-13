package com.alancruse.wallpaper.wallpaper

import android.app.Service
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import com.alancruse.wallpaper.canvas.DrawingStroke
import com.alancruse.wallpaper.canvas.ImageElement
import com.alancruse.wallpaper.canvas.StickerElement
import com.alancruse.wallpaper.canvas.TextElement
import com.alancruse.wallpaper.pairing.PairingPrefs
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

/**
 * Background service that renders a canvas room to a bitmap
 * and sets it as the lock screen wallpaper via WallpaperManager.
 *
 * Listens to Firebase changes on the lockscreen room and updates
 * the lock screen wallpaper whenever the canvas changes.
 */
class LockscreenWallpaperService : Service() {

    companion object {
        private const val TAG = "LockscreenWPService"

        fun start(context: Context) {
            context.startService(Intent(context, LockscreenWallpaperService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LockscreenWallpaperService::class.java))
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val listeners = mutableListOf<Pair<com.google.firebase.database.DatabaseReference, ValueEventListener>>()
    private var currentListeningRoomId: String? = null

    private var strokes = listOf<DrawingStroke>()
    private var stickers = listOf<StickerElement>()
    private var texts = listOf<TextElement>()
    private var images = listOf<ImageElement>()
    private var bgColor = Color.parseColor("#0D0D1A")

    private var screenWidth = 1080
    private var screenHeight = 2400

    // Debounce rendering to avoid excessive bitmap updates
    private var renderRunnable: Runnable? = null
    private val renderDebounceMs = 500L

    private val prefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "lockscreen_room_id") {
            handler.post { startListening() }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // Get screen dimensions
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels

        PairingPrefs(this).registerOnChangeListener(prefsListener)
        startListening()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopListening()
        PairingPrefs(this).unregisterOnChangeListener(prefsListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startListening()
        // Force an immediate render to override lock screen NOW
        // (e.g., after user sets live wallpaper which overrides both screens)
        handler.postDelayed({ renderAndSetLockscreen() }, 1000L)
        return START_STICKY
    }

    private fun startListening() {
        val prefs = PairingPrefs(this)
        val roomId = prefs.lockscreenRoomId

        if (roomId == null) {
            // No separate lock screen set — nothing to do
            stopListening()
            stopSelf()
            return
        }

        // If already listening to this room, don't re-register but still allow forced renders
        if (roomId == currentListeningRoomId && listeners.isNotEmpty()) return

        stopListening()
        currentListeningRoomId = roomId

        val roomRef = FirebaseDatabase.getInstance().reference.child("canvas_sessions").child(roomId)

        addListener(roomRef.child("strokes")) { snap ->
            strokes = snap.children.mapNotNull { it.getValue(DrawingStroke::class.java) }
            scheduleRender()
        }
        addListener(roomRef.child("stickers")) { snap ->
            stickers = snap.children.mapNotNull { it.getValue(StickerElement::class.java) }
            scheduleRender()
        }
        addListener(roomRef.child("texts")) { snap ->
            texts = snap.children.mapNotNull { it.getValue(TextElement::class.java) }
            scheduleRender()
        }
        addListener(roomRef.child("images")) { snap ->
            images = snap.children.mapNotNull { it.getValue(ImageElement::class.java) }
            scheduleRender()
        }
        addListener(roomRef.child("backgroundColor")) { snap ->
            val colorArgb = snap.getValue(Int::class.java)
            bgColor = colorArgb ?: Color.parseColor("#0D0D1A")
            scheduleRender()
        }

        Log.d(TAG, "Started listening to lock screen room: $roomId")
    }

    private fun addListener(ref: com.google.firebase.database.DatabaseReference, onData: (DataSnapshot) -> Unit) {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) = onData(snapshot)
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Firebase error: ${error.message}")
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

    private fun scheduleRender() {
        renderRunnable?.let { handler.removeCallbacks(it) }
        renderRunnable = Runnable { renderAndSetLockscreen() }
        handler.postDelayed(renderRunnable!!, renderDebounceMs)
    }

    private fun renderAndSetLockscreen() {
        try {
            // Create bitmap at screen resolution
            val bitmap = Bitmap.createBitmap(screenWidth, screenHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            // Use shared renderer
            CanvasRenderer.drawOnCanvas(canvas, bgColor, strokes, stickers, texts, images)

            // Set as lock screen wallpaper
            val wm = WallpaperManager.getInstance(this)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                wm.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK)
                Log.d(TAG, "Lock screen wallpaper updated")
            } else {
                // Pre-Nougat: can't set lock screen separately
                Log.w(TAG, "Separate lock screen wallpaper requires Android N+")
            }

            bitmap.recycle()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to render lock screen wallpaper", e)
        }
    }
}
