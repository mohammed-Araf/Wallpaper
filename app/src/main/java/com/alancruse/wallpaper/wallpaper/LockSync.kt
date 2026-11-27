package com.alancruse.wallpaper.wallpaper

import android.app.KeyguardManager
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
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
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

/**
 * Keeps the lock-screen bitmap in sync with its canvas, cheaply.
 *
 * - One shared instance, reference-counted by its hosts (the live wallpaper service,
 *   which Android keeps alive while it is the wallpaper, and the fallback started service).
 * - Changes only mark the bitmap dirty. It is re-rendered when the lock screen can
 *   actually be seen: while the screen is off / locked, or right after the room changes.
 *   No rendering happens while the user is in other apps.
 */
object LockSync {
    private const val TAG = "LockSync"
    private val handler = Handler(Looper.getMainLooper())
    private var refs = 0
    private lateinit var app: Context

    private val listeners = mutableListOf<Pair<DatabaseReference, ValueEventListener>>()
    private var roomId: String? = null
    private var strokes = listOf<DrawingStroke>()
    private var stickers = listOf<StickerElement>()
    private var texts = listOf<TextElement>()
    private var images = listOf<ImageElement>()
    private var bgColor = Color.BLACK
    private var dirty = false
    private var forceNext = false

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "lockscreen_room_id") handler.post { listen() }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { if (i.action == Intent.ACTION_SCREEN_OFF) schedule(300) }
    }

    @Synchronized
    fun acquire(ctx: Context) {
        if (refs++ > 0) { forceNext = true; schedule(800); return }
        app = ctx.applicationContext
        PairingPrefs(app).registerOnChangeListener(prefsListener)
        app.registerReceiver(screenReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        listen()
    }

    @Synchronized
    fun release() {
        if (refs == 0 || --refs > 0) return
        stop()
        try { app.unregisterReceiver(screenReceiver) } catch (_: Exception) {}
        PairingPrefs(app).unregisterOnChangeListener(prefsListener)
    }

    private fun listen() {
        val id = PairingPrefs(app).lockscreenRoomId
        if (id == null) { stop(); return }
        if (id == roomId && listeners.isNotEmpty()) return
        stop()
        roomId = id
        forceNext = true // new room: show it right away
        val room = FirebaseDatabase.getInstance().reference.child("canvas_sessions").child(id)
        add(room.child("strokes")) { s -> strokes = s.children.mapNotNull { it.getValue(DrawingStroke::class.java) } }
        add(room.child("stickers")) { s -> stickers = s.children.mapNotNull { it.getValue(StickerElement::class.java) } }
        add(room.child("texts")) { s -> texts = s.children.mapNotNull { it.getValue(TextElement::class.java) } }
        add(room.child("images")) { s -> images = s.children.mapNotNull { it.getValue(ImageElement::class.java) } }
        add(room.child("backgroundColor")) { s -> bgColor = s.getValue(Int::class.java) ?: Color.BLACK }
    }

    private fun add(ref: DatabaseReference, onData: (DataSnapshot) -> Unit) {
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) { onData(s); dirty = true; schedule(700) }
            override fun onCancelled(e: DatabaseError) { Log.e(TAG, e.message) }
        }
        ref.addValueEventListener(l); listeners.add(ref to l)
    }

    private fun stop() {
        listeners.forEach { (r, l) -> r.removeEventListener(l) }
        listeners.clear(); roomId = null
        handler.removeCallbacks(render)
    }

    private fun schedule(delay: Long) { handler.removeCallbacks(render); handler.postDelayed(render, delay) }

    private fun lockVisible(): Boolean {
        val pm = app.getSystemService(Context.POWER_SERVICE) as PowerManager
        val km = app.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        return !pm.isInteractive || km.isKeyguardLocked
    }

    private val render = Runnable {
        if (roomId == null || (!dirty && !forceNext)) return@Runnable
        if (!forceNext && !lockVisible()) return@Runnable // wait for SCREEN_OFF
        forceNext = false; dirty = false
        try {
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            (app.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(dm)
            val bmp = Bitmap.createBitmap(dm.widthPixels, dm.heightPixels, Bitmap.Config.ARGB_8888)
            CanvasRenderer.drawOnCanvas(Canvas(bmp), bgColor, strokes, stickers, texts, images)
            WallpaperManager.getInstance(app).setBitmap(bmp, null, true, WallpaperManager.FLAG_LOCK)
            bmp.recycle()
        } catch (e: Exception) { Log.e(TAG, "Lock render failed", e) }
    }
}
