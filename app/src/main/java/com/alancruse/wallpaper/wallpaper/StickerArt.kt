package com.alancruse.wallpaper.wallpaper

import android.graphics.*
import com.alancruse.wallpaper.canvas.StickerElement
import com.alancruse.wallpaper.live.LiveSource
import com.alancruse.wallpaper.live.LiveStats
import com.alancruse.wallpaper.live.Presence
import com.alancruse.wallpaper.live.haversineKm
import kotlin.math.*

/**
 * Cartoon "2.5D" stickers: thick ink outlines, an extruded darker underside, a glossy
 * top highlight and a soft ground shadow — flat drawing that reads as 3D.
 * Every sticker is designed in a 100-unit box centred on (0, 0).
 */
object StickerArt {
    private const val INK = 0xFF2A1840.toInt()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND; color = INK
    }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
    }
    private val shape = Path()
    private val under = Path()
    private val aux = Path()
    private val box = RectF()

    // ═════════ Public API ═════════

    /** Half extents (100-unit space) used for hit testing. */
    fun halfSize(kind: String): Pair<Float, Float> = when (kind) {
        "w:steps" -> 78f to 55f
        "w:km" -> 72f to 55f
        "w:speed" -> 72f to 60f
        "w:steps_km", "w:speed_km" -> 98f to 60f
        "w:board" -> 118f to 80f
        "w:dist" -> 112f to 88f
        else -> 50f to 50f
    }

    /** Visual centre: emoji stickers are drawn on a baseline, so keep the same anchor. */
    fun centerY(st: StickerElement) = st.y - st.size * st.scale * 0.35f

    fun draw(c: Canvas, st: StickerElement, timeMs: Long, src: LiveSource?, roomId: String?) {
        val u = st.size * st.scale / 100f
        val s = (timeMs % 3_600_000L) / 1000f
        c.save()
        // Same pivot as emoji stickers (the baseline point), so transforms match.
        c.translate(st.x, st.y); c.rotate(st.rotation); c.translate(0f, -st.size * st.scale * 0.35f); c.scale(u, u)
        try {
            when (st.kind) {
                "a:heart" -> heart(c, s)
                "a:star" -> star(c, s)
                "a:fire" -> fire(c, s)
                "a:sun" -> sun(c, s)
                "a:moon" -> moon(c, s)
                "a:ghost" -> ghost(c, s)
                "a:party" -> party(c, s)
                "a:rainbow" -> rainbow(c, s)
                "a:love" -> love(c, s)
                "w:steps" -> { stepsCard(c, s, src?.stats(st.owner)); plate(c, st.label, -50f) }
                "w:km" -> { kmCard(c, s, src?.stats(st.owner)); plate(c, st.label, -50f) }
                "w:speed" -> { speedCard(c, s, src?.stats(st.owner)); plate(c, st.label, -55f) }
                "w:steps_km" -> { stepsKmCard(c, s, src?.stats(st.owner)); plate(c, st.label, -55f) }
                "w:speed_km" -> { speedKmCard(c, s, src?.stats(st.owner)); plate(c, st.label, -55f) }
                "w:board" -> { billboard(c, s, src?.stats(st.owner)); plate(c, st.label, -74f) }
                "w:dist" -> distance(c, s, src, roomId)
                else -> { fill.color = INK; c.drawCircle(0f, 0f, 20f, fill) }
            }
        } finally { c.restore() }
    }

    // ═════════ Toon primitives ═════════

    private fun shade(color: Int, f: Float): Int {
        fun ch(v: Int) = if (f <= 1f) (v * f).toInt() else (v + (255 - v) * (f - 1f)).toInt().coerceAtMost(255)
        return Color.argb(Color.alpha(color), ch(Color.red(color)), ch(Color.green(color)), ch(Color.blue(color)))
    }

    /** Extruded, outlined, glossy fill of [p]. */
    private fun toon(c: Canvas, p: Path, color: Int, depth: Float = 6f, stroke: Float = 5f, gloss: Boolean = true, round: Float = 0f, shadow: Boolean = true) {
        p.computeBounds(box, true)
        val l = box.left; val t = box.top; val r = box.right; val b = box.bottom
        val pe = if (round > 0f) CornerPathEffect(round) else null
        fill.pathEffect = pe; line.pathEffect = pe
        if (shadow) {
            fill.color = 0x30000000
            c.drawOval(l + (r - l) * 0.12f, b + depth - 3f, r - (r - l) * 0.12f, b + depth + 7f, fill)
        }
        p.offset(0f, depth, under)
        fill.color = shade(color, 0.6f); c.drawPath(under, fill)
        line.strokeWidth = stroke; line.color = INK; c.drawPath(under, line)
        fill.shader = LinearGradient(0f, t, 0f, b, shade(color, 1.3f), color, Shader.TileMode.CLAMP)
        c.drawPath(p, fill); fill.shader = null
        c.drawPath(p, line)
        if (gloss) {
            c.save(); c.clipPath(p)
            fill.pathEffect = null; fill.color = 0x50FFFFFF
            c.drawOval(l + (r - l) * 0.14f, t + (b - t) * 0.06f, r - (r - l) * 0.14f, t + (b - t) * 0.4f, fill)
            c.restore()
        }
        fill.pathEffect = null; line.pathEffect = null
    }

    private fun card(c: Canvas, w: Float, h: Float, color: Int, cy: Float = 0f) {
        shape.reset(); shape.addRoundRect(-w / 2, cy - h / 2, w / 2, cy + h / 2, h * 0.3f, h * 0.3f, Path.Direction.CW)
        toon(c, shape, color, depth = 8f, stroke = 5f)
    }

    private fun circle(c: Canvas, x: Float, y: Float, r: Float, color: Int, depth: Float = 4f) {
        aux.reset(); aux.addCircle(x, y, r, Path.Direction.CW)
        toon(c, aux, color, depth, stroke = max(2.5f, r * 0.18f), shadow = false)
    }

    /** Fat, rounded "comic" lettering with an ink outline and a dropped 3D edge. */
    private fun chunky(c: Canvas, text: String, x: Float, y: Float, size: Float, color: Int = Color.WHITE, align: Paint.Align = Paint.Align.CENTER, alpha: Int = 255, maxW: Float = 0f) {
        var sz = size
        txt.textSize = sz; txt.textAlign = align
        if (maxW > 0f) { val w = txt.measureText(text); if (w > maxW) { sz *= maxW / w; txt.textSize = sz } }
        txt.style = Paint.Style.FILL_AND_STROKE
        txt.strokeWidth = sz * 0.3f; txt.color = INK; txt.alpha = alpha
        c.drawText(text, x, y + sz * 0.1f, txt); c.drawText(text, x, y, txt)
        txt.strokeWidth = sz * 0.07f; txt.color = color; txt.alpha = alpha
        c.drawText(text, x, y, txt)
        txt.alpha = 255
    }

    private fun face(c: Canvas, x: Float, y: Float, k: Float = 1f, blush: Boolean = true, sleepy: Boolean = false) {
        fill.color = INK
        if (sleepy) {
            line.strokeWidth = 3.5f * k; line.color = INK
            c.drawArc(x - 15 * k, y - 6 * k, x - 3 * k, y + 4 * k, 20f, 140f, false, line)
            c.drawArc(x + 3 * k, y - 6 * k, x + 15 * k, y + 4 * k, 20f, 140f, false, line)
        } else {
            c.drawOval(x - 13 * k, y - 8 * k, x - 5 * k, y + 3 * k, fill)
            c.drawOval(x + 5 * k, y - 8 * k, x + 13 * k, y + 3 * k, fill)
            fill.color = Color.WHITE
            c.drawCircle(x - 7.5f * k, y - 5f * k, 1.8f * k, fill); c.drawCircle(x + 10.5f * k, y - 5f * k, 1.8f * k, fill)
        }
        if (blush) { fill.color = 0x66FF6B9A; c.drawOval(x - 21 * k, y + 2 * k, x - 12 * k, y + 8 * k, fill); c.drawOval(x + 12 * k, y + 2 * k, x + 21 * k, y + 8 * k, fill) }
        line.strokeWidth = 3.5f * k; line.color = INK
        c.drawArc(x - 6 * k, y + 1 * k, x + 6 * k, y + 11 * k, 20f, 140f, false, line)
    }

    private fun sparkle(c: Canvas, x: Float, y: Float, r: Float, color: Int = Color.WHITE, alpha: Int = 255) {
        aux.reset()
        aux.moveTo(x, y - r); aux.quadTo(x + r * 0.15f, y - r * 0.15f, x + r, y); aux.quadTo(x + r * 0.15f, y + r * 0.15f, x, y + r)
        aux.quadTo(x - r * 0.15f, y + r * 0.15f, x - r, y); aux.quadTo(x - r * 0.15f, y - r * 0.15f, x, y - r); aux.close()
        fill.color = color; fill.alpha = alpha; c.drawPath(aux, fill); fill.alpha = 255
    }

    private fun bump(x: Float, c: Float, w: Float) = max(0f, 1f - ((x - c) / w).let { it * it })

    private fun heartPath(p: Path, r: Float, cx: Float = 0f, cy: Float = 0f) {
        p.reset(); p.moveTo(cx, cy + r * 0.9f)
        p.cubicTo(cx - r * 1.25f, cy + r * 0.1f, cx - r * 0.95f, cy - r * 1.05f, cx, cy - r * 0.38f)
        p.cubicTo(cx + r * 0.95f, cy - r * 1.05f, cx + r * 1.25f, cy + r * 0.1f, cx, cy + r * 0.9f); p.close()
    }

    // ═════════ Animated pack ═════════

    private fun heart(c: Canvas, s: Float) {
        val ph = s % 1.1f
        val k = 1f + 0.13f * (bump(ph, 0.1f, 0.12f) + 0.6f * bump(ph, 0.36f, 0.12f))
        c.save(); c.scale(k, k)
        heartPath(shape, 40f, 0f, 2f); toon(c, shape, 0xFFFF4D6D.toInt())
        face(c, 0f, 4f, 0.9f)
        c.restore()
    }

    private fun star(c: Canvas, s: Float) {
        for (i in 0..2) {
            val a = ((sin(s * 3f + i * 2.1f) + 1f) / 2f)
            val ang = i * 2.1f + 0.6f
            sparkle(c, cos(ang) * 52f, sin(ang) * 46f, 5f + 6f * a, 0xFFFFF3B0.toInt(), (a * 255).toInt())
        }
        c.save(); c.rotate(sin(s * 2f) * 10f); val k = 1f + 0.06f * sin(s * 4f); c.scale(k, k)
        shape.reset()
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) 44f else 21f
            val a = -PI / 2 + i * PI / 5
            val x = (cos(a) * r).toFloat(); val y = (sin(a) * r).toFloat() + 3f
            if (i == 0) shape.moveTo(x, y) else shape.lineTo(x, y)
        }
        shape.close()
        toon(c, shape, 0xFFFFC93C.toInt(), round = 7f)
        face(c, 0f, 6f, 0.75f)
        c.restore()
    }

    private fun flame(p: Path, w: Float, k: Float) {
        p.reset(); p.moveTo(0f, 40f * k)
        p.cubicTo(-38f * k, 40f * k, -40f * k, 5f * k, -18f * k, -15f * k)
        p.cubicTo(-12f * k, -22f * k, -8f * k, -30f * k, (-2f + w) * k, -48f * k)
        p.cubicTo(10f * k, -25f * k, 31f * k, -15f * k, 31f * k, 8f * k)
        p.cubicTo(32f * k, 30f * k, 18f * k, 40f * k, 0f, 40f * k); p.close()
    }

    private fun fire(c: Canvas, s: Float) {
        c.save(); c.scale(1f + 0.04f * sin(s * 11f), 1f + 0.06f * sin(s * 13f), 0f, 40f)
        flame(shape, sin(s * 9f) * 7f, 1f); toon(c, shape, 0xFFFF7A1A.toInt())
        c.save(); c.translate(0f, 12f)
        flame(aux, sin(s * 11f + 1f) * 6f, 0.55f)
        fill.color = 0xFFFFD23F.toInt(); c.drawPath(aux, fill); line.strokeWidth = 3f; c.drawPath(aux, line)
        c.restore()
        face(c, 0f, 16f, 0.7f, blush = false)
        c.restore()
    }

    private fun sun(c: Canvas, s: Float) {
        c.save(); c.rotate(s * 30f)
        for (i in 0 until 10) {
            c.save(); c.rotate(i * 36f)
            shape.reset(); shape.addRoundRect(-5f, -50f, 5f, -34f, 5f, 5f, Path.Direction.CW)
            fill.color = 0xFFFFA62B.toInt(); c.drawPath(shape, fill); line.strokeWidth = 3.5f; c.drawPath(shape, line)
            c.restore()
        }
        c.restore()
        val k = 1f + 0.03f * sin(s * 3f)
        c.save(); c.scale(k, k)
        shape.reset(); shape.addCircle(0f, 0f, 30f, Path.Direction.CW); toon(c, shape, 0xFFFFD43B.toInt(), depth = 5f)
        face(c, 0f, 2f, 0.85f)
        c.restore()
    }

    private fun moon(c: Canvas, s: Float) {
        c.save(); c.rotate(sin(s * 1.5f) * 6f)
        shape.reset(); shape.addCircle(-4f, 4f, 36f, Path.Direction.CW)
        aux.reset(); aux.addCircle(14f, -8f, 30f, Path.Direction.CW)
        shape.op(aux, Path.Op.DIFFERENCE)
        toon(c, shape, 0xFFFFE08A.toInt())
        face(c, -20f, 10f, 0.6f, sleepy = true)
        c.restore()
        for (i in 0..2) {
            val ph = (s * 0.45f + i / 3f) % 1f
            chunky(c, "z", 22f + ph * 22f, -18f - ph * 38f, 12f + i * 4f, 0xFFB9A6FF.toInt(), alpha = ((1f - ph) * 255).toInt())
        }
    }

    private fun ghost(c: Canvas, s: Float) {
        c.save(); c.translate(0f, sin(s * 2.5f) * 6f); c.rotate(sin(s * 1.7f) * 5f)
        shape.reset(); shape.moveTo(-30f, 28f); shape.lineTo(-30f, -8f)
        shape.cubicTo(-30f, -50f, 30f, -50f, 30f, -8f); shape.lineTo(30f, 28f)
        var x = 30f
        for (k in 0 until 4) { shape.quadTo(x - 7.5f, 28f + 10f * sin(s * 6f + k * 1.6f), x - 15f, 28f); x -= 15f }
        shape.close()
        toon(c, shape, 0xFFF2EEFF.toInt())
        fill.color = INK
        c.drawOval(-15f, -16f, -5f, -2f, fill); c.drawOval(5f, -16f, 15f, -2f, fill)
        fill.color = Color.WHITE; c.drawCircle(-8f, -12f, 2f, fill); c.drawCircle(12f, -12f, 2f, fill)
        fill.color = INK; c.drawOval(-4f, 3f, 4f, 11f, fill)
        fill.color = 0x66FF6B9A; c.drawOval(-24f, -3f, -15f, 3f, fill); c.drawOval(15f, -3f, 24f, 3f, fill)
        c.restore()
    }

    private val confetti = intArrayOf(0xFFFF4D6D.toInt(), 0xFFFFC93C.toInt(), 0xFF4ADE80.toInt(), 0xFF38BDF8.toInt(), 0xFFA78BFA.toInt())

    private fun party(c: Canvas, s: Float) {
        for (i in 0 until 14) {
            val ph = (s * 0.8f + i * 0.071f) % 1f
            val ang = Math.toRadians((-90 + (i * 37 % 110 - 55)).toDouble())
            val d = ph * 58f
            val x = (cos(ang) * d).toFloat(); val y = -14f + (sin(ang) * d).toFloat() + ph * ph * 34f
            c.save(); c.translate(x, y); c.rotate(ph * 720f + i * 40)
            fill.color = confetti[i % confetti.size]; fill.alpha = ((1f - ph) * 255).toInt()
            c.drawRoundRect(-4f, -2f, 4f, 2f, 2f, 2f, fill); fill.alpha = 255
            c.restore()
        }
        c.save(); c.rotate(sin(s * 4f) * 4f, 0f, 40f)
        shape.reset(); shape.moveTo(-17f, -12f); shape.lineTo(17f, -12f); shape.lineTo(0f, 42f); shape.close()
        toon(c, shape, 0xFF8B5CF6.toInt(), round = 6f)
        line.strokeWidth = 4f; line.color = 0xFFFFC93C.toInt()
        c.drawLine(-11f, 6f, 9f, 2f, line); c.drawLine(-6f, 22f, 5f, 20f, line); line.color = INK
        shape.reset(); shape.addOval(-18f, -18f, 18f, -6f, Path.Direction.CW)
        fill.color = 0xFF6D3FD9.toInt(); c.drawPath(shape, fill); line.strokeWidth = 4f; c.drawPath(shape, line)
        c.restore()
    }

    private val rainbowCols = intArrayOf(0xFFFF4D6D.toInt(), 0xFFFF9F1C.toInt(), 0xFFFFD43B.toInt(), 0xFF4ADE80.toInt(), 0xFF38BDF8.toInt(), 0xFFA78BFA.toInt())

    private fun rainbow(c: Canvas, s: Float) {
        c.save(); c.translate(0f, sin(s * 3f) * 3f)
        val cy = 22f
        line.strokeCap = Paint.Cap.BUTT
        line.color = INK; line.strokeWidth = 40f; c.drawArc(-46f, cy - 46f, 46f, cy + 46f, 180f, 180f, false, line)
        rainbowCols.forEachIndexed { i, col ->
            val r = 40f - i * 6f
            line.color = col; line.strokeWidth = 6.5f
            c.drawArc(-r, cy - r, r, cy + r, 180f, 180f, false, line)
        }
        line.strokeCap = Paint.Cap.ROUND; line.color = INK
        for (side in listOf(-1f, 1f)) {
            c.save(); c.translate(side * 36f, cy); c.scale(1f, 1f + 0.07f * sin(s * 4f + side))
            shape.reset()
            shape.addCircle(-9f, 2f, 10f, Path.Direction.CW); shape.addCircle(3f, -4f, 12f, Path.Direction.CW); shape.addCircle(12f, 3f, 9f, Path.Direction.CW)
            shape.op(Path().apply { addRect(-30f, -30f, 30f, 30f, Path.Direction.CW) }, Path.Op.INTERSECT)
            toon(c, shape, 0xFFFFFFFF.toInt(), depth = 4f, stroke = 4f, shadow = false)
            c.restore()
        }
        c.restore()
    }

    private fun love(c: Canvas, s: Float) {
        val letters = "LOVE"
        val cols = intArrayOf(0xFFFF5C8A.toInt(), 0xFFFF4D6D.toInt(), 0xFFFF7AA2.toInt(), 0xFFFF5C8A.toInt())
        letters.forEachIndexed { i, ch ->
            val y = 14f - sin(s * 5f - i * 0.7f).coerceAtLeast(0f) * 12f
            c.save(); c.rotate(sin(s * 5f - i * 0.7f) * 6f, -36f + i * 24f, y)
            chunky(c, ch.toString(), -36f + i * 24f, y, 34f, cols[i])
            c.restore()
        }
        val ph = s % 1.1f
        val k = 1f + 0.18f * bump(ph, 0.1f, 0.12f)
        c.save(); c.translate(0f, -30f); c.scale(k, k)
        heartPath(shape, 12f); toon(c, shape, 0xFFFF4D6D.toInt(), depth = 3f, stroke = 3f, shadow = false)
        c.restore()
    }

    // ═════════ Live widgets ═════════

    private val tierColors = intArrayOf(0xFF4ADE80.toInt(), 0xFF38BDF8.toInt(), 0xFFA78BFA.toInt(), 0xFFFBBF24.toInt(), 0xFFFB7185.toInt())
    private val cheers = arrayOf("", "nice!", "great!", "on fire!", "legend!")
    fun tier(steps: Int) = when { steps >= 15000 -> 4; steps >= 10000 -> 3; steps >= 6000 -> 2; steps >= 3000 -> 1; else -> 0 }

    private fun stepsText(st: LiveStats?) = st?.let { if (it.steps >= 100_000) "${it.steps / 1000}k" else "%,d".format(it.steps) } ?: "--"
    private fun kmText(st: LiveStats?) = st?.let { if (it.km < 10f) "%.1f".format(it.km) else "%.0f".format(it.km) } ?: "--"
    private fun liveSpeed(st: LiveStats?): Float? = st?.let { if (System.currentTimeMillis() - it.speedAt < 5 * 60_000L) it.speed else 0f }

    private fun footprints(c: Canvas, x: Float, y: Float, k: Float, s: Float) {
        for (side in listOf(-1, 1)) {
            val lift = max(0f, sin(s * 5f + if (side < 0) 0f else PI.toFloat())) * 6f
            c.save(); c.translate(x + side * 8f * k, y - lift * k + side * 3f * k); c.rotate(side * 10f); c.scale(k, k)
            shape.reset(); shape.addOval(-6f, -4f, 6f, 13f, Path.Direction.CW)
            fill.color = Color.WHITE; c.drawPath(shape, fill); line.strokeWidth = 3f; line.color = INK; c.drawPath(shape, line)
            for (t in 0..2) { val tx = -4f + t * 4f; fill.color = Color.WHITE; c.drawCircle(tx, -9f + abs(t - 1) * 1.5f, 2.6f, fill); c.drawCircle(tx, -9f + abs(t - 1) * 1.5f, 2.6f, line) }
            c.restore()
        }
    }

    private fun pin(c: Canvas, x: Float, y: Float, k: Float, s: Float) {
        c.save(); c.translate(x, y - abs(sin(s * 3f)) * 5f * k); c.scale(k, k)
        shape.reset(); shape.moveTo(0f, 16f)
        shape.cubicTo(-16f, -2f, -14f, -18f, 0f, -18f); shape.cubicTo(14f, -18f, 16f, -2f, 0f, 16f); shape.close()
        toon(c, shape, 0xFFFF4D6D.toInt(), depth = 3f, stroke = 3f, shadow = false)
        fill.color = Color.WHITE; c.drawCircle(0f, -5f, 5f, fill); line.strokeWidth = 2.5f; c.drawCircle(0f, -5f, 5f, line)
        c.restore()
    }

    private fun gauge(c: Canvas, x: Float, y: Float, r: Float, kmh: Float?, s: Float) {
        line.strokeCap = Paint.Cap.ROUND
        line.color = INK; line.strokeWidth = r * 0.42f; c.drawArc(x - r, y - r, x + r, y + r, 200f, 140f, false, line)
        line.color = Color.WHITE; line.strokeWidth = r * 0.26f; c.drawArc(x - r, y - r, x + r, y + r, 200f, 140f, false, line)
        val frac = ((kmh ?: 0f) / 120f).coerceIn(0f, 1f)
        line.color = if (frac > 0.6f) 0xFFFF4D6D.toInt() else 0xFF4ADE80.toInt()
        c.drawArc(x - r, y - r, x + r, y + r, 200f, 140f * frac, false, line)
        val wob = if ((kmh ?: 0f) > 1f) sin(s * 18f) * 2.5f else 0f
        val a = Math.toRadians((200f + 140f * frac + wob).toDouble())
        line.color = INK; line.strokeWidth = r * 0.14f
        c.drawLine(x, y, x + (cos(a) * r * 0.85f).toFloat(), y + (sin(a) * r * 0.85f).toFloat(), line)
        fill.color = INK; c.drawCircle(x, y, r * 0.16f, fill)
    }

    private fun badge(c: Canvas, x: Float, y: Float, tier: Int, s: Float) {
        if (tier == 0) return
        c.save(); c.translate(x, y); c.rotate(sin(s * 2f) * 8f)
        if (tier >= 4) {
            shape.reset(); shape.moveTo(-16f, 8f); shape.lineTo(-18f, -10f); shape.lineTo(-8f, -2f); shape.lineTo(0f, -14f)
            shape.lineTo(8f, -2f); shape.lineTo(18f, -10f); shape.lineTo(16f, 8f); shape.close()
            toon(c, shape, 0xFFFFD43B.toInt(), depth = 3f, stroke = 3f, round = 3f, shadow = false)
        } else {
            val col = intArrayOf(0, 0xFFD08A4B.toInt(), 0xFFCBD5E1.toInt(), 0xFFFFD43B.toInt())[tier]
            circle(c, 0f, 0f, 14f, col, 3f)
            shape.reset()
            for (i in 0 until 10) {
                val r = if (i % 2 == 0) 8f else 3.6f
                val a = -PI / 2 + i * PI / 5
                val px = (cos(a) * r).toFloat(); val py = (sin(a) * r).toFloat()
                if (i == 0) shape.moveTo(px, py) else shape.lineTo(px, py)
            }
            shape.close(); fill.color = Color.WHITE; c.drawPath(shape, fill)
        }
        c.restore()
    }

    private fun tierFx(c: Canvas, w: Float, h: Float, tier: Int, s: Float, cy: Float = 0f) {
        if (tier >= 3) for (i in 0..3) {
            val a = (sin(s * 3f + i * 1.7f) + 1f) / 2f
            val px = (i - 1.5f) * w * 0.32f; val py = cy + if (i % 2 == 0) -h / 2 - 6f else h / 2 + 4f
            sparkle(c, px, py, 4f + 5f * a, 0xFFFFF3B0.toInt(), (a * 255).toInt())
        }
        if (tier >= 3) { // shimmer sweep across the card
            shape.reset(); shape.addRoundRect(-w / 2, cy - h / 2, w / 2, cy + h / 2, h * 0.3f, h * 0.3f, Path.Direction.CW)
            c.save(); c.clipPath(shape)
            val sx = ((s * 0.5f) % 1.6f - 0.3f) * w - w / 2
            fill.color = 0x40FFFFFF
            aux.reset(); aux.moveTo(sx, cy - h); aux.lineTo(sx + 18f, cy - h); aux.lineTo(sx - 12f, cy + h); aux.lineTo(sx - 30f, cy + h); aux.close()
            c.drawPath(aux, fill); c.restore()
        }
    }

    private fun stepsCard(c: Canvas, s: Float, st: LiveStats?) {
        val t = tier(st?.steps ?: 0)
        card(c, 150f, 100f, tierColors[t])
        tierFx(c, 150f, 100f, t, s)
        footprints(c, -46f, 2f, 1.15f, s)
        chunky(c, stepsText(st), 20f, 8f, 32f, maxW = 88f)
        chunky(c, "steps", 20f, 32f, 13f)
        if (t > 0) { c.save(); c.rotate(-4f); chunky(c, cheers[t], -30f, 66f, 15f, 0xFFFFF3B0.toInt()); c.restore() }
        badge(c, 62f, -44f, t, s)
    }

    private fun kmCard(c: Canvas, s: Float, st: LiveStats?) {
        card(c, 140f, 100f, 0xFF60A5FA.toInt())
        pin(c, -42f, 0f, 1.25f, s)
        chunky(c, kmText(st), 18f, 8f, 32f, maxW = 80f)
        chunky(c, "km today", 18f, 32f, 12f)
    }

    private fun speedBlock(c: Canvas, x: Float, y: Float, r: Float, st: LiveStats?, s: Float, textSize: Float) {
        val v = liveSpeed(st)
        gauge(c, x, y, r, v, s)
        if (v == null) chunky(c, "--", x, y + r * 0.95f, textSize)
        else if (v < 1f) {
            chunky(c, "parked", x, y + r * 0.95f, textSize * 0.75f)
            for (i in 0..1) { val ph = (s * 0.5f + i * 0.5f) % 1f; chunky(c, "z", x + r * 0.8f + ph * 10f, y - r * 0.6f - ph * 18f, 10f, alpha = ((1 - ph) * 255).toInt()) }
        } else chunky(c, "${v.roundToInt()} km/h", x, y + r * 0.95f, textSize, maxW = r * 3f)
    }

    private fun speedCard(c: Canvas, s: Float, st: LiveStats?) {
        card(c, 140f, 110f, 0xFFFF8A3D.toInt())
        speedBlock(c, 0f, 6f, 32f, st, s, 22f)
    }

    private fun stepsKmCard(c: Canvas, s: Float, st: LiveStats?) {
        val t = tier(st?.steps ?: 0)
        card(c, 190f, 110f, tierColors[t])
        tierFx(c, 190f, 110f, t, s)
        footprints(c, -48f, -16f, 1f, s)
        chunky(c, stepsText(st), -48f, 26f, 22f, maxW = 80f)
        chunky(c, "steps", -48f, 44f, 10f)
        line.strokeWidth = 3f; line.color = 0x662A1840; line.pathEffect = DashPathEffect(floatArrayOf(5f, 6f), 0f)
        c.drawLine(0f, -36f, 0f, 36f, line); line.pathEffect = null; line.color = INK
        pin(c, 48f, -16f, 1f, s)
        chunky(c, kmText(st), 48f, 26f, 22f, maxW = 80f)
        chunky(c, "km", 48f, 44f, 10f)
        badge(c, 82f, -48f, t, s)
    }

    private fun speedKmCard(c: Canvas, s: Float, st: LiveStats?) {
        card(c, 190f, 110f, 0xFFFF8A3D.toInt())
        speedBlock(c, -46f, -2f, 26f, st, s, 16f)
        line.strokeWidth = 3f; line.color = 0x662A1840; line.pathEffect = DashPathEffect(floatArrayOf(5f, 6f), 0f)
        c.drawLine(4f, -36f, 4f, 36f, line); line.pathEffect = null; line.color = INK
        pin(c, 50f, -16f, 1f, s)
        chunky(c, kmText(st), 50f, 26f, 22f, maxW = 72f)
        chunky(c, "km", 50f, 44f, 10f)
    }

    private fun billboard(c: Canvas, s: Float, st: LiveStats?) {
        // posts + ground
        fill.color = 0x30000000; c.drawOval(-100f, 70f, 100f, 84f, fill)
        for (px in listOf(-62f, 62f)) {
            shape.reset(); shape.addRoundRect(px - 7f, 10f, px + 7f, 76f, 4f, 4f, Path.Direction.CW)
            toon(c, shape, 0xFF9C6B3C.toInt(), depth = 3f, stroke = 4f, gloss = false, shadow = false)
        }
        val t = tier(st?.steps ?: 0)
        card(c, 232f, 104f, 0xFF3B2A5C.toInt(), cy = -22f)
        // marquee bulbs
        val on = (s * 4f).toInt()
        var i = 0
        var x = -104f
        while (x <= 104f) {
            for (yy in listOf(-68f, 24f)) {
                val lit = (i + on) % 2 == 0
                fill.color = if (lit) 0xFFFFE27A.toInt() else 0xFF7A6A3A.toInt(); c.drawCircle(x, yy, 3.6f, fill)
                if (lit) { fill.color = 0x40FFE27A; c.drawCircle(x, yy, 7f, fill) }
                i++
            }
            x += 16f
        }
        // three columns
        footprints(c, -72f, -38f, 0.85f, s)
        chunky(c, stepsText(st), -72f, -2f, 19f, tierColors[t], maxW = 66f)
        pin(c, 0f, -38f, 0.85f, s)
        chunky(c, kmText(st) + " km", 0f, -2f, 19f, 0xFF93C5FD.toInt(), maxW = 66f)
        val v = liveSpeed(st)
        gauge(c, 72f, -36f, 15f, v, s)
        chunky(c, v?.let { "${it.roundToInt()} km/h" } ?: "--", 72f, -2f, 16f, 0xFFFFB27A.toInt(), maxW = 66f)
        badge(c, 104f, -70f, t, s)
        if (t > 0) chunky(c, cheers[t], 0f, 52f, 14f, 0xFFFFF3B0.toInt())
    }

    // ═════════ Name plate ═════════

    private val plateText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-black", Typeface.BOLD); textAlign = Paint.Align.CENTER; color = INK
    }

    /** Cream "number plate" straddling the top edge, with two bolts. */
    private fun plate(c: Canvas, label: String, edgeY: Float) {
        val text = label.trim().take(12)
        if (text.isEmpty()) return
        plateText.textSize = 13f
        val w = plateText.measureText(text) + 30f
        shape.reset(); shape.addRoundRect(-w / 2, edgeY - 11f, w / 2, edgeY + 11f, 7f, 7f, Path.Direction.CW)
        toon(c, shape, 0xFFFFF1CC.toInt(), depth = 3f, stroke = 3f, shadow = false)
        fill.color = 0xFF9A8A6A.toInt()
        c.drawCircle(-w / 2 + 7f, edgeY, 2.2f, fill); c.drawCircle(w / 2 - 7f, edgeY, 2.2f, fill)
        c.drawText(text, 0f, edgeY + 4.6f, plateText)
    }

    // ═════════ Distance ═════════

    private fun initial(name: String) = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    private fun distText(km: Double) = when {
        km < 1.0 -> "${max(10, ((km * 1000) / 10).roundToInt() * 10)} m"
        km < 10.0 -> "%.1f km".format(km)
        else -> "%,d km".format(km.roundToInt())
    }

    private val avatarCols = intArrayOf(0xFF8B5CF6.toInt(), 0xFF38BDF8.toInt(), 0xFFF97316.toInt(), 0xFF10B981.toInt(), 0xFFEC4899.toInt())

    private fun avatar(c: Canvas, x: Float, y: Float, r: Float, name: String, col: Int) {
        circle(c, x, y, r, col, r * 0.2f)
        chunky(c, initial(name), x, y + r * 0.38f, r * 1.05f)
    }

    private fun distance(c: Canvas, s: Float, src: LiveSource?, roomId: String?) {
        val all = if (src != null && roomId != null) src.presence(roomId) else emptyMap()
        val me = src?.myUid
        val mine = all[me]
        val others = all.filterKeys { it != me }.values.filter { System.currentTimeMillis() - it.at < 12 * 3600_000L }
        val fresh = all.filterValues { System.currentTimeMillis() - it.at < 12 * 3600_000L }
        if (fresh.size >= 3) return distanceMap(c, s, me, fresh)

        val other = others.firstOrNull()
        val d = if (mine != null && other != null) haversineKm(mine, other) else null
        card(c, 200f, 112f, 0xFFFF8FB1.toInt())
        val together = d != null && d < 0.15
        val close = d != null && d < 1.0
        val pull = if (together) 1f else 0f
        val bounce = if (close) abs(sin(s * 4f)) * 6f else 0f
        val lx = -64f + 34f * pull; val rx = 64f - 34f * pull
        if (!together) {
            line.strokeWidth = 4f; line.color = Color.WHITE
            line.pathEffect = DashPathEffect(floatArrayOf(6f, 8f), -s * 30f)
            c.drawLine(lx + 22f, -16f, rx - 22f, -16f, line); line.pathEffect = null; line.color = INK
            if (d != null) { // a little heart travelling between you
                val ph = (s * (if (close) 0.8f else 0.3f)) % 1f
                heartPath(aux, 6f, lx + 22f + (rx - lx - 44f) * ph, -16f)
                fill.color = 0xFFFF4D6D.toInt(); c.drawPath(aux, fill)
            }
        }
        avatar(c, lx, -16f - bounce, 20f, mine?.name ?: "me", avatarCols[0])
        avatar(c, rx, -16f - (if (close) abs(sin(s * 4f + 1.3f)) * 6f else 0f), 20f, other?.name ?: "?", avatarCols[1])
        if (together) {
            val ph = s % 1.1f
            val k = 1f + 0.2f * bump(ph, 0.1f, 0.12f)
            c.save(); c.translate(0f, -24f); c.scale(k, k)
            heartPath(shape, 16f); toon(c, shape, 0xFFFF4D6D.toInt(), depth = 3f, stroke = 3f, shadow = false)
            c.restore()
            for (i in 0..3) { // floating hearts
                val p = (s * 0.5f + i * 0.25f) % 1f
                heartPath(aux, 5f + i, -40f + i * 26f, -40f - p * 40f)
                fill.color = 0xFFFF7AA2.toInt(); fill.alpha = ((1f - p) * 255).toInt(); c.drawPath(aux, fill); fill.alpha = 255
            }
        }
        val main = when { d == null -> "?"; together -> "together!"; else -> distText(d) }
        chunky(c, main, 0f, 28f, if (together) 22f else 24f, maxW = 170f)
        chunky(c, when { mine == null -> "share location"; other == null -> "waiting…"; together -> "♥"; close -> "so close!"; else -> "away" }, 0f, 46f, 11f)
    }

    /**
     * Everyone at once: each person keeps their real direction from the group's centre,
     * with distance log-scaled so metres and kilometres both fit. Links follow the
     * minimum spanning tree (each person to their nearest neighbour; n-1 lines, no clutter),
     * labelled with the real distance — hearts for people standing together.
     */
    private fun distanceMap(c: Canvas, s: Float, me: String?, all: Map<String, Presence>) {
        val people = all.entries.sortedBy { it.key }.take(8)
        val n = people.size
        card(c, 220f, 170f, 0xFFFF8FB1.toInt())
        val lat0 = people.sumOf { it.value.lat } / n; val lng0 = people.sumOf { it.value.lng } / n
        val kx = 111.32 * cos(Math.toRadians(lat0)); val ky = 110.57
        val km = people.map { ((it.value.lng - lng0) * kx).toFloat() to (-(it.value.lat - lat0) * ky).toFloat() }
        val maxR = km.maxOf { hypot(it.first, it.second) }.coerceAtLeast(0.01f)
        val rx = 78f; val ry = 50f; val cy = 4f
        val px = FloatArray(n); val py = FloatArray(n)
        km.forEachIndexed { i, (x, y) ->
            val r = hypot(x, y)
            val f = if (r < 1e-4f) 0f else (ln(1f + r / 0.2f) / ln(1f + maxR / 0.2f))
            px[i] = if (r < 1e-4f) 0f else x / r * f * rx
            py[i] = if (r < 1e-4f) 0f else y / r * f * ry + cy
        }
        // Push overlapping bubbles apart, keep them on the card.
        val minD = 30f
        repeat(40) {
            for (a in 0 until n) for (b in a + 1 until n) {
                var dx = px[b] - px[a]; var dy = py[b] - py[a]
                var d = hypot(dx, dy)
                if (d < 1e-3f) { dx = cos(a + b.toFloat()); dy = sin(a + b.toFloat()); d = 1f }
                if (d < minD) { val push = (minD - d) / 2f; px[a] -= dx / d * push; py[a] -= dy / d * push; px[b] += dx / d * push; py[b] += dy / d * push }
            }
            for (i in 0 until n) { px[i] = px[i].coerceIn(-rx - 8f, rx + 8f); py[i] = py[i].coerceIn(cy - ry - 4f, cy + ry + 4f) }
        }
        // Prim's MST on real distances.
        val dist = Array(n) { a -> DoubleArray(n) { b -> haversineKm(people[a].value, people[b].value) } }
        val inTree = BooleanArray(n).also { it[0] = true }
        val edges = ArrayList<Pair<Int, Int>>()
        repeat(n - 1) {
            var best = Double.MAX_VALUE; var e = 0 to 0
            for (a in 0 until n) if (inTree[a]) for (b in 0 until n) if (!inTree[b] && dist[a][b] < best) { best = dist[a][b]; e = a to b }
            inTree[e.second] = true; edges.add(e)
        }
        line.pathEffect = DashPathEffect(floatArrayOf(5f, 6f), -s * 24f)
        for ((a, b) in edges) { line.color = Color.WHITE; line.strokeWidth = 3f; c.drawLine(px[a], py[a], px[b], py[b], line) }
        line.pathEffect = null; line.color = INK
        for ((a, b) in edges) {
            val mx = (px[a] + px[b]) / 2; val my = (py[a] + py[b]) / 2
            val d = dist[a][b]
            if (d < 0.15) {
                val k = 1f + 0.22f * bump(s % 1.1f, 0.1f, 0.12f)
                c.save(); c.translate(mx, my); c.scale(k, k); heartPath(aux, 7f); fill.color = 0xFFFF4D6D.toInt(); c.drawPath(aux, fill); line.strokeWidth = 2f; c.drawPath(aux, line); c.restore()
            } else chunky(c, distText(d), mx, my + 3f, 9f, if (d < 1.0) 0xFFFFF3B0.toInt() else Color.WHITE)
        }
        people.forEachIndexed { i, (uid, p) ->
            val near = (0 until n).any { j -> j != i && dist[i][j] < 1.0 }
            val y = py[i] - if (near) abs(sin(s * 4f + i)) * 4f else 0f
            if (uid == me) { fill.color = 0xFFFFF3B0.toInt(); c.drawCircle(px[i], y, 16f, fill) }
            avatar(c, px[i], y, 12.5f, p.name, avatarCols[i % avatarCols.size])
            chunky(c, if (uid == me) "you" else p.name.ifBlank { "friend" }.take(7), px[i], y + 24f, 8.5f)
        }
    }

    /** Sample data for sticker previews in the picker. */
    object Preview : LiveSource {
        override val myUid = "me"
        private val st = LiveStats(steps = 10_480, km = 7.6f, speed = 38f, speedAt = Long.MAX_VALUE / 2, name = "You")
        override fun stats(uid: String) = st
        override fun presence(roomId: String) = mapOf(
            "me" to Presence(12.9716, 77.5946, Long.MAX_VALUE / 2, "Me"),
            "p" to Presence(12.9916, 77.6146, Long.MAX_VALUE / 2, "Sam"),
        )
    }
}
