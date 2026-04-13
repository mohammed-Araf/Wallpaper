package com.alancruse.wallpaper.wallpaper

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.Base64
import android.util.Log
import com.alancruse.wallpaper.canvas.DrawingStroke
import com.alancruse.wallpaper.canvas.ImageElement
import com.alancruse.wallpaper.canvas.StickerElement
import com.alancruse.wallpaper.canvas.TextElement
import java.util.concurrent.ConcurrentHashMap

/**
 * Shared rendering logic for drawing canvas elements onto any Android Canvas.
 * Used by both LiveWallpaperService (home screen) and LockscreenWallpaperService (lock screen).
 */
object CanvasRenderer {

    private val bitmapCache = ConcurrentHashMap<Int, Bitmap>()

    fun decodeBitmap(base64: String): Bitmap? {
        val hash = base64.hashCode()
        bitmapCache[hash]?.let { return it }
        return try {
            val bytes = Base64.decode(base64, Base64.NO_WRAP)
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bmp != null) bitmapCache[hash] = bmp
            bmp
        } catch (e: Exception) {
            Log.e("CanvasRenderer", "Failed to decode Base64 image", e)
            null
        }
    }

    fun drawOnCanvas(
        canvas: Canvas,
        bgColor: Int,
        strokes: List<DrawingStroke>,
        stickers: List<StickerElement>,
        texts: List<TextElement>,
        images: List<ImageElement>
    ) {
        canvas.drawColor(bgColor)

        // Chronological layer system — sorted by timestamp
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
}
