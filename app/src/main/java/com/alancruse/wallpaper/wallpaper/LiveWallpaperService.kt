package com.alancruse.wallpaper.wallpaper

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.util.Base64
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
import java.util.concurrent.ConcurrentHashMap

class LiveWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = WallpaperEngine()

    inner class WallpaperEngine : Engine() {

        private val handler = Handler(Looper.getMainLooper())
        private val listeners = mutableListOf<Pair<com.google.firebase.database.DatabaseReference, ValueEventListener>>()

        private var strokes = listOf<DrawingStroke>()
        private var stickers = listOf<StickerElement>()
        private var texts = listOf<TextElement>()
        private var images = listOf<ImageElement>()

        // Cache decoded bitmaps keyed by Base64 hash to avoid re-decoding
        private val bitmapCache = ConcurrentHashMap<Int, Bitmap>()

        private var surfaceWidth = 0
        private var surfaceHeight = 0

        private val bgColor = Color.parseColor("#0D0D1A")
        private val emptyTitlePaint = Paint().apply {
            color = Color.parseColor("#64748B"); textSize = 42f
            textAlign = Paint.Align.CENTER; isAntiAlias = true
        }
        private val emptySubPaint = Paint().apply {
            color = Color.parseColor("#334155"); textSize = 28f
            textAlign = Paint.Align.CENTER; isAntiAlias = true
        }

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            startListening()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            surfaceWidth = width; surfaceHeight = height; drawFrame()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            if (visible) { startListening(); drawFrame() }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder?) {
            super.onSurfaceDestroyed(holder); stopListening()
        }

        override fun onDestroy() {
            super.onDestroy(); stopListening()
        }

        private fun startListening() {
            stopListening()
            val prefs = PairingPrefs(this@LiveWallpaperService)
            if (!prefs.isPaired) {
                strokes = emptyList(); stickers = emptyList()
                texts = emptyList(); images = emptyList()
                drawFrame(); return
            }

            val currentUid = prefs.currentUid!!
            val partnerUid = prefs.partnerUid!!
            val roomId = if (currentUid < partnerUid) "${currentUid}_${partnerUid}"
            else "${partnerUid}_${currentUid}"

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
        }

        /** Decode a Base64 string to a Bitmap, caching by hash */
        private fun decodeBitmap(base64: String): Bitmap? {
            val hash = base64.hashCode()
            bitmapCache[hash]?.let { return it }
            return try {
                val bytes = Base64.decode(base64, Base64.NO_WRAP)
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bmp != null) bitmapCache[hash] = bmp
                bmp
            } catch (e: Exception) {
                Log.e("LiveWallpaper", "Failed to decode Base64 image", e)
                null
            }
        }

        private fun drawFrame() {
            val holder = surfaceHolder ?: return
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas != null) drawOnCanvas(canvas)
            } catch (e: Exception) {
                Log.e("LiveWallpaper", "Draw error", e)
            } finally {
                if (canvas != null) try { holder.unlockCanvasAndPost(canvas) } catch (_: Exception) {}
            }
        }

        private fun drawOnCanvas(canvas: Canvas) {
            canvas.drawColor(bgColor)

            if (strokes.isEmpty() && stickers.isEmpty() && texts.isEmpty() && images.isEmpty()) {
                drawEmptyState(canvas); return
            }

            // ── Chronological layer system — same as canvas editor ──
            // Build a unified list sorted by timestamp so first-created = bottom layer
            data class Layer(val timestamp: Long, val type: Int, val index: Int)
            val layers = mutableListOf<Layer>()
            strokes.forEachIndexed { i, s -> layers.add(Layer(s.timestamp, 0, i)) }
            stickers.forEachIndexed { i, s -> layers.add(Layer(s.timestamp, 1, i)) }
            texts.forEachIndexed { i, t -> layers.add(Layer(t.timestamp, 2, i)) }
            images.forEachIndexed { i, img -> layers.add(Layer(img.timestamp, 3, i)) }
            layers.sortBy { it.timestamp }

            val strokePaint = Paint().apply {
                isAntiAlias = true; style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
            }
            val emojiPaint = Paint().apply { isAntiAlias = true; textAlign = Paint.Align.CENTER }
            val txtPaint = Paint().apply { isAntiAlias = true; textAlign = Paint.Align.LEFT }

            for (layer in layers) {
                when (layer.type) {
                    0 -> { // Stroke
                        val stroke = strokes[layer.index]
                        if (stroke.path.isEmpty()) continue
                        strokePaint.color = stroke.colorArgb
                        strokePaint.strokeWidth = stroke.strokeWidth
                        val path = Path().apply {
                            moveTo(stroke.path.first().x, stroke.path.first().y)
                            for (i in 1 until stroke.path.size) lineTo(stroke.path[i].x, stroke.path[i].y)
                        }
                        canvas.drawPath(path, strokePaint)
                        // Draw dot for single-point strokes
                        if (stroke.path.size <= 2) {
                            val dotPaint = Paint().apply {
                                isAntiAlias = true; style = Paint.Style.FILL
                                color = stroke.colorArgb
                            }
                            canvas.drawCircle(stroke.path.first().x, stroke.path.first().y, stroke.strokeWidth / 2f, dotPaint)
                        }
                    }
                    1 -> { // Sticker
                        val sticker = stickers[layer.index]
                        emojiPaint.textSize = sticker.size * sticker.scale
                        canvas.save()
                        canvas.rotate(sticker.rotation, sticker.x, sticker.y)
                        canvas.drawText(sticker.emoji, sticker.x, sticker.y, emojiPaint)
                        canvas.restore()
                    }
                    2 -> { // Text
                        val textEl = texts[layer.index]
                        txtPaint.color = textEl.colorArgb
                        txtPaint.textSize = textEl.fontSize * textEl.scale
                        canvas.save()
                        canvas.rotate(textEl.rotation, textEl.x, textEl.y)
                        canvas.drawText(textEl.text, textEl.x, textEl.y, txtPaint)
                        canvas.restore()
                    }
                    3 -> { // Image
                        val img = images[layer.index]
                        if (img.imageBase64.isEmpty()) continue
                        val bitmap = decodeBitmap(img.imageBase64) ?: continue
                        val w = (img.width * img.scale).toInt().coerceAtLeast(1)
                        val h = (img.height * img.scale).toInt().coerceAtLeast(1)
                        val scaled = Bitmap.createScaledBitmap(bitmap, w, h, true)
                        val cx = img.x + w / 2f
                        val cy = img.y + h / 2f
                        canvas.save()
                        canvas.rotate(img.rotation, cx, cy)
                        canvas.drawBitmap(scaled, img.x, img.y, null)
                        canvas.restore()
                    }
                }
            }
        }

        private fun drawEmptyState(canvas: Canvas) {
            val spacing = 60f
            val dotPaint = Paint().apply { color = Color.parseColor("#1A1A2E"); isAntiAlias = true }
            var x = spacing
            while (x < surfaceWidth) {
                var y = spacing
                while (y < surfaceHeight) { canvas.drawCircle(x, y, 1.5f, dotPaint); y += spacing }
                x += spacing
            }
            val cx = surfaceWidth / 2f; val cy = surfaceHeight / 2f
            canvas.drawText("WallPaper ✨", cx, cy - 30, emptyTitlePaint)
            canvas.drawText("Draw in the app to see it here", cx, cy + 20, emptySubPaint)
        }
    }
}
