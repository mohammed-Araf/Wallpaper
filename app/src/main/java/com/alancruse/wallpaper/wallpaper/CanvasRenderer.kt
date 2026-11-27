package com.alancruse.wallpaper.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.Base64
import android.util.Log
import android.util.LruCache
import com.alancruse.wallpaper.canvas.DrawingStroke
import com.alancruse.wallpaper.canvas.ImageElement
import com.alancruse.wallpaper.canvas.Point
import com.alancruse.wallpaper.canvas.StickerElement
import com.alancruse.wallpaper.canvas.TextElement
import java.io.File
import java.io.FileOutputStream

/**
 * Shared rendering for the editor, the live wallpaper, the lock screen bitmap and thumbnails.
 * Allocation-free per frame: paints are reused, images are drawn into a destination rect
 * (no per-frame scaled copies) and stroke paths can be supplied pre-built.
 */
object CanvasRenderer {

    // ~48 MB of decoded images, keyed by base64 hash
    private val bitmapCache = object : LruCache<Int, Bitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: Int, value: Bitmap) = value.byteCount
    }

    fun cachedBitmap(base64: String): Bitmap? = bitmapCache.get(base64.hashCode())

    fun decodeBitmap(base64: String): Bitmap? {
        val hash = base64.hashCode()
        bitmapCache.get(hash)?.let { return it }
        return try {
            val bytes = Base64.decode(base64, Base64.NO_WRAP)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.also { bitmapCache.put(hash, it) }
        } catch (e: Exception) {
            Log.e("CanvasRenderer", "Failed to decode image", e); null
        }
    }

    /** Smooth path through the points using quadratic curves between midpoints. */
    fun buildPath(points: List<Point>, smooth: Boolean = true): Path {
        val p = Path()
        if (points.isEmpty()) return p
        p.moveTo(points[0].x, points[0].y)
        if (points.size < 3 || !smooth) {
            for (i in 1 until points.size) p.lineTo(points[i].x, points[i].y)
            return p
        }
        for (i in 1 until points.size - 1) {
            val a = points[i]; val b = points[i + 1]
            p.quadTo(a.x, a.y, (a.x + b.x) / 2f, (a.y + b.y) / 2f)
        }
        val last = points.last(); p.lineTo(last.x, last.y)
        return p
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val rect = RectF()

    fun textPaintFor(t: TextElement): Paint = textPaint.apply { textSize = t.fontSize * t.scale }

    private class Layer(val ts: Long, val type: Int, val index: Int)

    /**
     * @param strokePaths optional pre-built paths parallel to [strokes].
     * @param decode decode images that are not cached yet (false in the editor, which decodes off-thread).
     */
    @Synchronized
    fun drawOnCanvas(
        canvas: Canvas,
        bgColor: Int?,
        strokes: List<DrawingStroke>,
        stickers: List<StickerElement>,
        texts: List<TextElement>,
        images: List<ImageElement>,
        strokePaths: List<Path>? = null,
        decode: Boolean = true,
    ) {
        if (bgColor != null) canvas.drawColor(bgColor)
        val layers = ArrayList<Layer>(strokes.size + stickers.size + texts.size + images.size)
        strokes.forEachIndexed { i, s -> layers.add(Layer(s.timestamp, 0, i)) }
        stickers.forEachIndexed { i, s -> layers.add(Layer(s.timestamp, 1, i)) }
        texts.forEachIndexed { i, t -> layers.add(Layer(t.timestamp, 2, i)) }
        images.forEachIndexed { i, img -> layers.add(Layer(img.timestamp, 3, i)) }
        layers.sortBy { it.ts }

        for (layer in layers) when (layer.type) {
            0 -> {
                val s = strokes.getOrNull(layer.index) ?: continue
                if (s.path.isEmpty()) continue
                val first = s.path.first()
                if (s.path.size <= 2 && s.path.last().let { it.x == first.x && it.y == first.y }) {
                    dotPaint.color = s.colorArgb
                    canvas.drawCircle(first.x, first.y, s.strokeWidth / 2f, dotPaint)
                } else {
                    strokePaint.color = s.colorArgb
                    strokePaint.strokeWidth = s.strokeWidth
                    canvas.drawPath(strokePaths?.getOrNull(layer.index) ?: buildPath(s.path), strokePaint)
                }
            }
            1 -> {
                val st = stickers.getOrNull(layer.index) ?: continue
                emojiPaint.textSize = st.size * st.scale
                canvas.save(); canvas.rotate(st.rotation, st.x, st.y)
                canvas.drawText(st.emoji, st.x, st.y, emojiPaint); canvas.restore()
            }
            2 -> {
                val t = texts.getOrNull(layer.index) ?: continue
                textPaint.color = t.colorArgb
                textPaint.textSize = t.fontSize * t.scale
                val lineH = textPaint.textSize * 1.2f
                canvas.save(); canvas.rotate(t.rotation, t.x, t.y)
                t.text.split('\n').forEachIndexed { i, line -> canvas.drawText(line, t.x, t.y + i * lineH, textPaint) }
                canvas.restore()
            }
            3 -> {
                val img = images.getOrNull(layer.index) ?: continue
                if (img.imageBase64.isEmpty()) continue
                val bmp = (if (decode) decodeBitmap(img.imageBase64) else cachedBitmap(img.imageBase64)) ?: continue
                val w = img.width * img.scale; val h = img.height * img.scale
                rect.set(img.x, img.y, img.x + w, img.y + h)
                canvas.save(); canvas.rotate(img.rotation, rect.centerX(), rect.centerY())
                canvas.drawBitmap(bmp, null, rect, bmpPaint); canvas.restore()
            }
        }
    }

    // ── Thumbnails (shown on the home screen) ──

    fun thumbFile(context: Context, roomId: String) = File(File(context.cacheDir, "thumbs").apply { mkdirs() }, "$roomId.jpg")

    fun saveThumbnail(
        context: Context, roomId: String, bgColor: Int, screenW: Int, screenH: Int,
        strokes: List<DrawingStroke>, stickers: List<StickerElement>, texts: List<TextElement>, images: List<ImageElement>,
    ) {
        Thread {
            try {
                val scale = 0.3f
                val bmp = Bitmap.createBitmap((screenW * scale).toInt().coerceAtLeast(1), (screenH * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                val c = Canvas(bmp); c.scale(scale, scale)
                drawOnCanvas(c, bgColor, strokes, stickers, texts, images)
                FileOutputStream(thumbFile(context, roomId)).use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                bmp.recycle()
            } catch (e: Exception) { Log.e("CanvasRenderer", "Thumbnail failed", e) }
        }.start()
    }
}
