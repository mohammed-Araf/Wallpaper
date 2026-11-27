package com.alancruse.wallpaper.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Matrix
import android.graphics.BitmapShader
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

    // ── Text looks ──
    const val TEXT_STYLES = 5
    const val TEXT_FONTS = 5
    const val IMAGE_STYLES = 5

    private val typefaces = arrayOf(
        Typeface.create("sans-serif-medium", Typeface.NORMAL),
        Typeface.create(Typeface.SERIF, Typeface.BOLD),
        Typeface.create("casual", Typeface.NORMAL),
        Typeface.create(Typeface.MONOSPACE, Typeface.BOLD),
        Typeface.create("sans-serif-condensed", Typeface.BOLD),
    )
    fun typeface(font: Int): Typeface = typefaces[font.mod(TEXT_FONTS)]

    /** Black or white, whichever reads better on [argb]. */
    fun contrastOn(argb: Int): Int {
        val l = (0.299 * android.graphics.Color.red(argb) + 0.587 * android.graphics.Color.green(argb) + 0.114 * android.graphics.Color.blue(argb)) / 255
        return if (l > 0.6) 0xFF111111.toInt() else android.graphics.Color.WHITE
    }

    /** Extra hit/visual padding around a text block for label styles. */
    fun textPad(t: TextElement): Float = if (t.style.mod(TEXT_STYLES) in 2..3) t.fontSize * t.scale * 0.35f else 0f

    fun textPaintFor(t: TextElement): Paint = textPaint.apply { textSize = t.fontSize * t.scale; typeface = typeface(t.font) }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND }

    private fun drawText(canvas: Canvas, t: TextElement) {
        val size = t.fontSize * t.scale
        textPaint.textSize = size; textPaint.typeface = typeface(t.font)
        val lines = t.text.split('\n')
        val lineH = size * 1.2f
        val style = t.style.mod(TEXT_STYLES)
        canvas.save(); canvas.rotate(t.rotation, t.x, t.y)
        var fill = t.colorArgb
        if (style == 2 || style == 3) {
            val pad = size * 0.35f
            val w = lines.maxOf { textPaint.measureText(it) }
            rect.set(t.x - pad, t.y - size - pad * 0.4f, t.x + w + pad, t.y + lineH * (lines.size - 1) + size * 0.3f + pad * 0.6f)
            labelPaint.color = if (style == 2) t.colorArgb else 0x8C000000.toInt()
            canvas.drawRoundRect(rect, size * 0.4f, size * 0.4f, labelPaint)
            if (style == 2) fill = contrastOn(t.colorArgb)
        }
        if (style == 4) {
            outlinePaint.textSize = size; outlinePaint.typeface = textPaint.typeface
            outlinePaint.strokeWidth = size * 0.14f; outlinePaint.color = contrastOn(t.colorArgb)
            lines.forEachIndexed { i, line -> canvas.drawText(line, t.x, t.y + i * lineH, outlinePaint) }
        }
        textPaint.color = fill
        if (style == 1) textPaint.setShadowLayer(size * 0.14f, 0f, size * 0.06f, 0xAA000000.toInt())
        lines.forEachIndexed { i, line -> canvas.drawText(line, t.x, t.y + i * lineH, textPaint) }
        textPaint.clearShadowLayer()
        canvas.restore()
    }

    // ── Image frames ──
    private val shaderCache = java.util.WeakHashMap<Bitmap, BitmapShader>()
    private val shaderMatrix = Matrix()
    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = android.graphics.Color.WHITE }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = android.graphics.Color.BLACK }

    private fun drawImage(canvas: Canvas, img: ImageElement, bmp: Bitmap) {
        val w = img.width * img.scale; val h = img.height * img.scale
        rect.set(img.x, img.y, img.x + w, img.y + h)
        val m = minOf(w, h)
        canvas.save(); canvas.rotate(img.rotation, rect.centerX(), rect.centerY())
        when (img.style.mod(IMAGE_STYLES)) {
            0 -> canvas.drawBitmap(bmp, null, rect, bmpPaint)
            else -> {
                val style = img.style.mod(IMAGE_STYLES)
                val shader = shaderCache.getOrPut(bmp) { BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
                if (style == 4) {
                    // Circle crop of the centre, scaled so the circle is fully covered.
                    val r = m / 2f; val cx = rect.centerX(); val cy = rect.centerY()
                    val k = maxOf(m / bmp.width, m / bmp.height)
                    shaderMatrix.setScale(k, k); shaderMatrix.postTranslate(cx - bmp.width * k / 2f, cy - bmp.height * k / 2f)
                    shader.setLocalMatrix(shaderMatrix); bmpPaint.shader = shader
                    canvas.drawCircle(cx, cy, r + m * 0.03f, framePaint)
                    canvas.drawCircle(cx, cy, r, bmpPaint)
                } else {
                    shaderMatrix.setScale(w / bmp.width, h / bmp.height); shaderMatrix.postTranslate(rect.left, rect.top)
                    shader.setLocalMatrix(shaderMatrix); bmpPaint.shader = shader
                    val radius = if (style == 2) m * 0.02f else m * 0.08f
                    if (style == 3) {
                        shadowPaint.setShadowLayer(m * 0.06f, 0f, m * 0.03f, 0x99000000.toInt())
                        canvas.drawRoundRect(rect, radius, radius, shadowPaint)
                        shadowPaint.clearShadowLayer()
                    }
                    if (style == 2) {
                        val b = m * 0.045f
                        val fr = RectF(rect.left - b, rect.top - b, rect.right + b, rect.bottom + b * 3.2f)
                        shadowPaint.setShadowLayer(m * 0.04f, 0f, m * 0.02f, 0x66000000)
                        canvas.drawRoundRect(fr, m * 0.025f, m * 0.025f, shadowPaint)
                        shadowPaint.clearShadowLayer()
                        canvas.drawRoundRect(fr, m * 0.025f, m * 0.025f, framePaint)
                    }
                    canvas.drawRoundRect(rect, radius, radius, bmpPaint)
                }
                bmpPaint.shader = null
            }
        }
        canvas.restore()
    }

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
        timeMs: Long = 0L,
        live: com.alancruse.wallpaper.live.LiveSource? = com.alancruse.wallpaper.live.LiveBoard,
        roomId: String? = null,
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
                if (st.kind.isNotEmpty()) { StickerArt.draw(canvas, st, timeMs, live, roomId); continue }
                emojiPaint.textSize = st.size * st.scale
                canvas.save(); canvas.rotate(st.rotation, st.x, st.y)
                canvas.drawText(st.emoji, st.x, st.y, emojiPaint); canvas.restore()
            }
            2 -> {
                val t = texts.getOrNull(layer.index) ?: continue
                if (t.text.isNotEmpty()) drawText(canvas, t)
            }
            3 -> {
                val img = images.getOrNull(layer.index) ?: continue
                if (img.imageBase64.isEmpty()) continue
                val bmp = (if (decode) decodeBitmap(img.imageBase64) else cachedBitmap(img.imageBase64)) ?: continue
                drawImage(canvas, img, bmp)
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
                drawOnCanvas(c, bgColor, strokes, stickers, texts, images, roomId = roomId)
                FileOutputStream(thumbFile(context, roomId)).use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                bmp.recycle()
            } catch (e: Exception) { Log.e("CanvasRenderer", "Thumbnail failed", e) }
        }.start()
    }
}
