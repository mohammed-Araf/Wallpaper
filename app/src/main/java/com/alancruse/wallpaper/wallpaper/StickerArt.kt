package com.alancruse.wallpaper.wallpaper

import android.graphics.*
import com.alancruse.wallpaper.canvas.StickerElement
import com.alancruse.wallpaper.live.AvatarLook
import com.alancruse.wallpaper.live.LiveSource
import com.alancruse.wallpaper.live.Status
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
        "w:me" -> 100f to 82f
        "w:clock" -> 82f to 60f
        "w:analog" -> 54f to 56f
        "w:countdown" -> 94f to 64f
        else -> 50f to 50f
    }

    /** Just the avatar's head, centred on (0, 0) in a ~40-unit box — for the look picker. */
    fun avatarHead(c: Canvas, look: AvatarLook, timeMs: Long) {
        head(c, 0f, 3f, look, (timeMs % 3_600_000L) / 1000f, Eyes.OPEN, lookX = 0f)
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
                "w:me" -> me(c, s, st, src)
                "w:clock" -> digitalClock(c)
                "w:analog" -> analogClock(c)
                "w:countdown" -> countdown(c, s, st)
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

    // ═════════ Clocks ("w:clock", "w:analog") ═════════
    //
    // The viewer's own local time, so each phone shows its own clock.

    /** Follows the phone's 12/24-hour setting; set by whoever hosts the canvas. */
    @Volatile var use24h = false
    /** Wall-clock source (swapped in tests). */
    internal var clockNow: () -> Long = { System.currentTimeMillis() }
    private val cal = java.util.Calendar.getInstance()
    private var clockKey = Long.MIN_VALUE
    private var clock24 = false
    private var hourText = ""
    private var minText = ""
    private var dateText = ""
    private val DAYS = arrayOf("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT")
    private val MONTHS = arrayOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")

    /** Milliseconds since local midnight. Labels are rebuilt at most once a minute. */
    private fun localMs(): Long {
        val now = clockNow()
        val tz = java.util.TimeZone.getDefault()
        val local = now + tz.getOffset(now)
        val day = ((local % 86_400_000L) + 86_400_000L) % 86_400_000L
        val minute = Math.floorDiv(local, 60_000L)
        if (minute != clockKey || clock24 != use24h) {
            clockKey = minute; clock24 = use24h
            cal.timeZone = tz; cal.timeInMillis = now
            val h24 = (day / 3_600_000L).toInt(); val m = ((day / 60_000L) % 60).toInt()
            hourText = if (use24h) "%02d".format(h24) else ((h24 + 11) % 12 + 1).toString()
            minText = "%02d".format(m)
            dateText = "${DAYS[cal.get(java.util.Calendar.DAY_OF_WEEK) - 1]} · ${cal.get(java.util.Calendar.DAY_OF_MONTH)} ${MONTHS[cal.get(java.util.Calendar.MONTH)]}"
        }
        return day
    }

    private fun digitalClock(c: Canvas) {
        val ms = localMs()
        val h24 = (ms / 3_600_000L).toInt()
        val night = h24 >= 19 || h24 < 6
        card(c, 160f, 108f, if (night) 0xFF4C3FA8.toInt() else 0xFF38A3F5.toInt())
        // Sun or moon in the corner
        if (night) {
            aux.reset(); aux.addCircle(-58f, -32f, 9f, Path.Direction.CW)
            under.reset(); under.addCircle(-53f, -36f, 8f, Path.Direction.CW)
            aux.op(under, Path.Op.DIFFERENCE)
            toon(c, aux, 0xFFFFE9A8.toInt(), depth = 2f, stroke = 2.5f, gloss = false, shadow = false)
        } else {
            val a0 = (ms % 20_000L) / 20_000f * 360f
            line.strokeWidth = 2.5f; line.color = 0xFFFFD43B.toInt()
            for (i in 0 until 8) {
                val a = Math.toRadians((a0 + i * 45f).toDouble())
                c.drawLine(-58f + cos(a).toFloat() * 10f, -32f + sin(a).toFloat() * 10f, -58f + cos(a).toFloat() * 14f, -32f + sin(a).toFloat() * 14f, line)
            }
            line.color = INK
            circle(c, -58f, -32f, 7f, 0xFFFFD43B.toInt(), depth = 2f)
        }
        if (!use24h) chunky(c, if (h24 < 12) "AM" else "PM", 60f, -26f, 12f, 0xFFFFF3B0.toInt())
        val y = 16f
        // Centre "h:mm" as a group so a one-digit hour doesn't sit off to the right.
        txt.textSize = 46f
        val x0 = (txt.measureText(hourText) - txt.measureText(minText)) / 2f
        chunky(c, hourText, x0 - 7f, y, 46f, align = Paint.Align.RIGHT)
        chunky(c, minText, x0 + 7f, y, 46f, align = Paint.Align.LEFT)
        val on = ms % 1000L < 500L
        chunky(c, ":", x0, y - 3f, 40f, alpha = if (on) 255 else 70)
        chunky(c, dateText, 0f, 42f, 12.5f, 0xFFE8F4FF.toInt())
    }

    private fun analogClock(c: Canvas) {
        val ms = localMs()
        aux.reset(); aux.addCircle(0f, 0f, 46f, Path.Direction.CW)
        toon(c, aux, 0xFFFF7A59.toInt(), depth = 7f, stroke = 5f, gloss = false)
        // Face
        fill.color = 0xFFFFF8EC.toInt(); c.drawCircle(0f, 0f, 37f, fill)
        line.strokeWidth = 3.5f; line.color = INK; c.drawCircle(0f, 0f, 37f, line)
        for (i in 0 until 60) {
            val a = i * PI / 30
            val major = i % 5 == 0
            if (major && i % 15 == 0) continue
            val r0 = if (major) 29f else 32.5f
            line.strokeWidth = if (major) 3f else 1.4f
            c.drawLine(sin(a).toFloat() * r0, -cos(a).toFloat() * r0, sin(a).toFloat() * 34f, -cos(a).toFloat() * 34f, line)
        }
        txt.style = Paint.Style.FILL; txt.color = INK; txt.textSize = 11f; txt.textAlign = Paint.Align.CENTER
        val nr = 27f
        c.drawText("12", 0f, -nr + 8f, txt); c.drawText("6", 0f, nr, txt)
        c.drawText("3", nr - 2f, 4f, txt); c.drawText("9", -nr + 2f, 4f, txt)
        // Hands: hour and minute glide, the second hand sweeps smoothly
        val sec = (ms % 60_000L) / 1000f
        val min = (ms % 3_600_000L) / 60_000f
        val hr = (ms % 43_200_000L) / 3_600_000f
        fun hand(deg: Float, len: Float, w: Float, col: Int, tail: Float = 0f) {
            val a = Math.toRadians(deg.toDouble())
            val dx = sin(a).toFloat(); val dy = -cos(a).toFloat()
            line.color = INK; line.strokeWidth = w + 3.5f
            c.drawLine(-dx * tail, -dy * tail, dx * len, dy * len, line)
            line.color = col; line.strokeWidth = w
            c.drawLine(-dx * tail, -dy * tail, dx * len, dy * len, line)
        }
        hand(hr * 30f, 18f, 4.5f, 0xFF4C3FA8.toInt())
        hand(min * 6f, 27f, 3.5f, 0xFF38A3F5.toInt())
        val sa = Math.toRadians((sec * 6f).toDouble())
        line.color = 0xFFE53950.toInt(); line.strokeWidth = 1.8f
        c.drawLine(-sin(sa).toFloat() * 8f, cos(sa).toFloat() * 8f, sin(sa).toFloat() * 31f, -cos(sa).toFloat() * 31f, line)
        line.color = INK
        circle(c, 0f, 0f, 3.6f, 0xFFE53950.toInt(), depth = 1f)
        // Glass shine
        fill.color = 0x40FFFFFF; c.drawOval(-26f, -34f, 22f, -14f, fill)
    }

    // ═════════ Countdown ("w:countdown") ═════════
    //
    // Counts down to StickerElement.until; the title is the sticker's label and the
    // icon/colour theme is in `look` ("i0".."i3").

    val COUNTDOWN_ICONS = arrayOf("Love", "Party", "Star", "Trip")
    private val countdownColors = intArrayOf(0xFFFF5C8A.toInt(), 0xFFFF9F43.toInt(), 0xFF8B5CF6.toInt(), 0xFF22B8CF.toInt())

    fun countdownIconOf(look: String) = Regex("i(\\d)").find(look)?.groupValues?.get(1)?.toInt()?.coerceIn(0, 3) ?: 0

    /** The theme icon in a ~44-unit box centred on (0, 0) — also used by the picker. */
    fun countdownIcon(c: Canvas, icon: Int, timeMs: Long) {
        val s = (timeMs % 3_600_000L) / 1000f
        val k = when (icon) { 1 -> 0.6f; 3 -> 0.36f; else -> 0.5f }
        c.save(); c.scale(k, k)
        when (icon) { 1 -> party(c, s); 2 -> star(c, s); 3 -> rainbow(c, s); else -> heart(c, s) }
        c.restore()
    }

    private fun countdown(c: Canvas, s: Float, st: StickerElement) {
        val icon = countdownIconOf(st.look)
        val left = st.until - clockNow()
        val here = st.until > 0 && left <= 0 && left > -86_400_000L
        if (here) { c.save(); c.rotate(sin(s * 6f) * 2.5f) }
        card(c, 184f, 120f, countdownColors[icon])
        // Icon on the left, bobbing
        c.save(); c.translate(-58f, 4f + sin(s * 2.2f) * 2f)
        countdownIcon(c, icon, (s * 1000).toLong()); c.restore()
        val tx = 22f
        val title = st.label.trim().ifEmpty { "Countdown" }
        chunky(c, title, tx, -30f, 14f, 0xFFFFF3B0.toInt(), maxW = 112f)
        val d = 86_400_000L; val h = 3_600_000L; val m = 60_000L
        when {
            st.until <= 0L -> chunky(c, "Set a date", tx, 12f, 20f, maxW = 112f)
            here -> {
                chunky(c, "It's here!", tx, 14f, 26f, maxW = 112f)
                for (i in 0 until 10) {
                    val ph = (s * 0.6f + i * 0.1f) % 1f
                    val a = i * 36f * PI.toFloat() / 180f
                    fill.color = countdownColors[(i + 1) % 4]; fill.alpha = ((1f - ph) * 255).toInt()
                    c.drawCircle(tx + cos(a) * (20f + ph * 50f), 6f + sin(a) * (12f + ph * 30f), 3f, fill)
                }
                fill.alpha = 255
            }
            left <= 0L -> {
                val ago = (-left) / d
                chunky(c, "$ago", tx, 16f, 40f, maxW = 112f)
                chunky(c, if (ago == 1L) "day ago" else "days ago", tx, 40f, 13f)
            }
            left >= d -> {
                val days = left / d
                chunky(c, "$days", tx, 16f, 44f, maxW = 112f)
                chunky(c, "${if (days == 1L) "day" else "days"} · ${(left % d) / h}h ${(left % h) / m}m", tx, 42f, 13f, maxW = 112f)
            }
            else -> {
                chunky(c, "%02d:%02d:%02d".format(left / h, (left % h) / m, (left % m) / 1000), tx, 12f, 30f, maxW = 112f)
                chunky(c, "to go", tx, 38f, 13f)
            }
        }
        if (here) c.restore()
    }

    // ═════════ Status avatar ("w:me") ═════════
    //
    // A little cartoon person acting out what the sticker's owner is doing right now.
    // Card is 190 × 150 units; the floor line sits at y = 44 (48 on the road).

    private const val PANTS = 0xFF3D4B7C.toInt()
    private const val SHOE = 0xFF2A2238.toInt()
    private const val WOOD = 0xFFB07A4A.toInt()
    private const val HEAD_R = 15f
    private val p1 = Path()
    private val p2 = Path()
    private val p3 = Path()

    private enum class Eyes { OPEN, CLOSED, HAPPY, SAD }
    private enum class Arms { PHONE, KNEES, MUG }

    private fun wrap(v: Float, m: Float) = ((v % m) + m) % m

    private fun ago(ms: Long): String {
        val m = (ms / 60_000L).toInt()
        return when {
            m < 1 -> "now"
            m < 60 -> "${m}m"
            else -> if (m % 60 == 0 || m >= 600) "${m / 60}h" else "${m / 60}h ${m % 60}m"
        }
    }

    private fun caption(status: String) = when (status) {
        Status.PHONE -> "on the phone"
        Status.RIDE -> "riding"
        Status.CYCLE -> "cycling"
        Status.WALK -> "walking"
        Status.MUSIC -> "vibing"
        Status.CHARGE -> "phone charging"
        Status.SLEEP -> "sleeping"
        Status.AWAY -> "chilling"
        Status.GHOST -> "on a break"
        NUDGE -> "nudged you!"
        else -> "no status yet"
    }

    private fun me(c: Canvas, s: Float, st: StickerElement, src: LiveSource?) {
        val stats = src?.stats(st.owner)
        val lk = AvatarLook.decode(st.look)
        val dark = lk.dark == 1
        val now = System.currentTimeMillis()
        val nudged = src != null && st.owner.isNotEmpty() && now - src.nudgedAt(st.owner) < 60_000L
        val status = if (nudged) NUDGE else Status.effective(stats, now)
        val w = 190f; val h = 150f
        shape.reset(); shape.addRoundRect(-w / 2, -h / 2, w / 2, h / 2, 34f, 34f, Path.Direction.CW)
        toon(c, shape, if (dark) 0xFF2E2A63.toInt() else 0xFFBFE6FF.toInt(), depth = 8f, stroke = 5f, gloss = false)
        c.save(); c.clipPath(shape)
        when (status) {
            Status.PHONE -> scenePhone(c, s, lk, dark)
            Status.RIDE -> sceneRide(c, s, lk, dark)
            Status.CYCLE -> sceneCycle(c, s, lk, dark)
            Status.WALK -> sceneWalk(c, s, lk, dark)
            Status.MUSIC -> sceneMusic(c, s, lk, dark)
            Status.CHARGE -> sceneCharge(c, s, lk, dark)
            Status.SLEEP -> sceneSleep(c, s, lk)
            Status.AWAY -> sceneAway(c, s, lk, dark)
            Status.GHOST -> sceneGhost(c, s, lk, dark)
            NUDGE -> sceneNudge(c, s, lk, dark)
            else -> sceneHello(c, s, lk, dark)
        }
        // caption on the floor
        val cap = caption(status)
        val text = if (status.isEmpty() || status == NUDGE || stats == null || stats.statusAt <= 0L) cap else "$cap · ${ago(now - stats.statusAt)}"
        chunky(c, text, 0f, 66f, 14f, maxW = 170f)
        c.restore()
        line.strokeWidth = 5f; line.color = INK; line.pathEffect = null
        c.drawRoundRect(-w / 2, -h / 2, w / 2, h / 2, 34f, 34f, line)
        if (stats != null && stats.battery >= 0 && status.isNotEmpty() && status != Status.GHOST) batteryChip(c, -84f, -56f, stats.battery, stats.charging, s)
        plate(c, st.label, -75f)
    }

    // ── Body parts ──

    private fun limb(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, w: Float, color: Int) {
        line.color = INK; line.strokeWidth = w + 5f; c.drawLine(x1, y1, x2, y2, line)
        line.color = color; line.strokeWidth = w; c.drawLine(x1, y1, x2, y2, line)
        line.color = INK
    }

    private fun limb3(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, w: Float, color: Int) {
        line.color = INK; line.strokeWidth = w + 5f; c.drawLine(x1, y1, x2, y2, line); c.drawLine(x2, y2, x3, y3, line)
        line.color = color; line.strokeWidth = w; c.drawLine(x1, y1, x2, y2, line); c.drawLine(x2, y2, x3, y3, line)
        line.color = INK
    }

    private fun dot(c: Canvas, x: Float, y: Float, r: Float, color: Int) {
        fill.color = color; c.drawCircle(x, y, r, fill)
        line.color = INK; line.strokeWidth = 2.5f; c.drawCircle(x, y, r, line)
    }

    private fun shoe(c: Canvas, x: Float, y: Float) {
        fill.color = SHOE; c.drawOval(x - 5f, y - 3.5f, x + 7f, y + 3.5f, fill)
        line.color = INK; line.strokeWidth = 2.5f; c.drawOval(x - 5f, y - 3.5f, x + 7f, y + 3.5f, line)
    }

    private fun head(
        c: Canvas, x: Float, y: Float, lk: AvatarLook, s: Float, eyes: Eyes = Eyes.OPEN,
        lookX: Float = 1.5f, tilt: Float = 0f, helmet: Boolean = false, phones: Boolean = false,
    ) {
        val r = HEAD_R
        c.save(); c.translate(x, y); c.rotate(tilt)
        val hc = AvatarLook.HAIR_COLORS[lk.hairColor]
        if (!helmet) backHair(c, lk, hc, s)
        shape.reset(); shape.addCircle(0f, 0f, r, Path.Direction.CW)
        toon(c, shape, AvatarLook.SKINS[lk.skin], depth = 2.5f, stroke = 3.5f, gloss = false, shadow = false)
        val e = if (eyes == Eyes.OPEN && s % 4.3f < 0.13f) Eyes.CLOSED else eyes
        val ex = lookX; val ey = 1.5f
        line.color = INK
        when (e) {
            Eyes.OPEN, Eyes.SAD -> {
                fill.color = INK
                c.drawOval(-7.2f + ex, ey - 3f, -3.8f + ex, ey + 2.6f, fill); c.drawOval(3.8f + ex, ey - 3f, 7.2f + ex, ey + 2.6f, fill)
                fill.color = Color.WHITE
                c.drawCircle(-4.8f + ex, ey - 1.6f, 1f, fill); c.drawCircle(6.2f + ex, ey - 1.6f, 1f, fill)
                if (lk.gender == 1) {
                    line.strokeWidth = 1.6f
                    c.drawLine(-7f + ex, ey - 2.2f, -9.4f + ex, ey - 4.2f, line); c.drawLine(7f + ex, ey - 2.2f, 9.4f + ex, ey - 4.2f, line)
                }
                if (e == Eyes.SAD) {
                    line.strokeWidth = 2f
                    c.drawLine(-8.5f + ex, ey - 5f, -3.5f + ex, ey - 7.5f, line); c.drawLine(3.5f + ex, ey - 7.5f, 8.5f + ex, ey - 5f, line)
                }
            }
            Eyes.CLOSED -> {
                line.strokeWidth = 2.2f
                c.drawArc(-7.5f + ex, ey - 3f, -3.5f + ex, ey + 1.5f, 0f, 180f, false, line)
                c.drawArc(3.5f + ex, ey - 3f, 7.5f + ex, ey + 1.5f, 0f, 180f, false, line)
            }
            Eyes.HAPPY -> {
                line.strokeWidth = 2.2f
                c.drawArc(-7.5f + ex, ey - 2f, -3.5f + ex, ey + 2.5f, 180f, 180f, false, line)
                c.drawArc(3.5f + ex, ey - 2f, 7.5f + ex, ey + 2.5f, 180f, 180f, false, line)
            }
        }
        fill.color = 0x55FF6B9A
        c.drawOval(-11.5f + ex * 0.5f, 5f, -6.5f + ex * 0.5f, 8.5f, fill); c.drawOval(6.5f + ex * 0.5f, 5f, 11.5f + ex * 0.5f, 8.5f, fill)
        line.strokeWidth = 2.2f; line.color = INK
        when (eyes) {
            Eyes.SAD -> c.drawArc(-3.5f + ex, 8.5f, 3.5f + ex, 13f, 200f, 140f, false, line)
            Eyes.CLOSED -> { fill.color = INK; c.drawOval(-1.5f + ex, 7.5f, 1.5f + ex, 10.5f, fill) }
            Eyes.HAPPY -> {
                aux.reset(); aux.arcTo(-4f + ex, 3.5f, 4f + ex, 11.5f, 0f, 180f, true); aux.close()
                fill.color = 0xFFB4324F.toInt(); c.drawPath(aux, fill); c.drawPath(aux, line)
            }
            else -> c.drawArc(-4f + ex, 4f, 4f + ex, 10f, 20f, 140f, false, line)
        }
        if (helmet) helmet(c, lk) else frontHair(c, lk, hc)
        if (phones) headphones(c)
        c.restore()
    }

    private fun backHair(c: Canvas, lk: AvatarLook, hc: Int, s: Float) {
        if (lk.gender == 0) return
        val r = HEAD_R
        p1.reset()
        when (lk.hair) {
            0 -> p1.addRoundRect(-r - 3.5f, -r * 0.7f, r + 3.5f, r * 1.55f, 9f, 9f, Path.Direction.CW)
            1 -> {
                c.save(); c.rotate(sin(s * 3f) * 8f, -r + 2f, -4f)
                p1.addOval(-r - 10f, -7f, -r + 3f, 17f, Path.Direction.CW)
                toon(c, p1, hc, depth = 2f, stroke = 3.5f, gloss = false, shadow = false)
                dot(c, -r + 1f, -4f, 2.6f, 0xFFFF5C7A.toInt())
                c.restore(); return
            }
            2 -> p1.addCircle(0f, -r - 4f, 7.5f, Path.Direction.CW)
            else -> p1.addRoundRect(-r - 3.5f, -r * 0.6f, r + 3.5f, r * 0.85f, 8f, 8f, Path.Direction.CW)
        }
        toon(c, p1, hc, depth = 2f, stroke = 3.5f, gloss = false, shadow = false)
    }

    private fun frontHair(c: Canvas, lk: AvatarLook, hc: Int) {
        val r = HEAD_R
        p1.reset(); p1.addCircle(0f, -1.5f, r + 2.2f, Path.Direction.CW)
        p2.reset(); p2.addRect(-40f, -40f, 40f, -r * 0.45f, Path.Direction.CW)
        p1.op(p2, Path.Op.INTERSECT)
        val boy = lk.gender == 0
        when {
            boy && lk.hair == 1 -> for (deg in floatArrayOf(-150f, -118f, -86f, -54f)) {
                val a = Math.toRadians(deg.toDouble()); val a1 = Math.toRadians(deg - 15.0); val a2 = Math.toRadians(deg + 15.0)
                p2.reset()
                p2.moveTo((cos(a1) * (r + 1)).toFloat(), (sin(a1) * (r + 1)).toFloat() - 1.5f)
                p2.lineTo((cos(a) * (r + 9)).toFloat(), (sin(a) * (r + 9)).toFloat() - 1.5f)
                p2.lineTo((cos(a2) * (r + 1)).toFloat(), (sin(a2) * (r + 1)).toFloat() - 1.5f)
                p2.close(); p1.op(p2, Path.Op.UNION)
            }
            boy && lk.hair == 2 -> for (i in 0..6) {
                val a = Math.toRadians(190.0 + i * 26.7)
                p2.reset(); p2.addCircle((cos(a) * r).toFloat(), (sin(a) * r).toFloat() - 1f, 5.5f, Path.Direction.CW)
                p1.op(p2, Path.Op.UNION)
            }
            (boy && lk.hair == 3) || (!boy && lk.hair != 2) -> {
                p2.reset()
                p2.moveTo(-r - 1.5f, -r * 0.3f)
                p2.quadTo(-r * 0.2f, -r * 0.38f, r * 0.75f, -r * 0.68f)
                p2.lineTo(r * 0.3f, -r - 2f); p2.lineTo(-r, -r * 0.6f); p2.close()
                p1.op(p2, Path.Op.UNION)
            }
        }
        toon(c, p1, hc, depth = 1.2f, stroke = 3f, gloss = true, shadow = false)
    }

    private fun helmet(c: Canvas, lk: AvatarLook) {
        val r = HEAD_R
        p1.reset(); p1.addCircle(-1f, -2f, r + 4.5f, Path.Direction.CW)
        p2.reset(); p2.addRect(-40f, -40f, 40f, r * 0.6f, Path.Direction.CW)
        p1.op(p2, Path.Op.INTERSECT)
        toon(c, p1, AvatarLook.OUTFITS[lk.outfit], depth = 2.5f, stroke = 3.5f, shadow = false)
        line.color = Color.WHITE; line.strokeWidth = 2.5f
        c.drawArc(-r - 2f, -r - 5f, r, r + 1f, 205f, 80f, false, line)
        p3.reset(); p3.addRoundRect(-3f, -7f, r + 4.5f, 5f, 6f, 6f, Path.Direction.CW)
        fill.color = 0xFF1E2A44.toInt(); c.drawPath(p3, fill)
        line.color = INK; line.strokeWidth = 3f; c.drawPath(p3, line)
        fill.color = 0x88FFFFFF.toInt(); c.drawRoundRect(2f, -5f, 13f, -3f, 1f, 1f, fill)
    }

    private fun headphones(c: Canvas) {
        val r = HEAD_R
        line.color = INK; line.strokeWidth = 7f; c.drawArc(-r - 3f, -r - 6f, r + 3f, r + 2f, 195f, 150f, false, line)
        line.color = 0xFF4B5563.toInt(); line.strokeWidth = 3.5f; c.drawArc(-r - 3f, -r - 6f, r + 3f, r + 2f, 195f, 150f, false, line)
        line.color = INK
        for (side in intArrayOf(-1, 1)) {
            val x = side * (r + 1f)
            shape.reset(); shape.addRoundRect(x - 4.5f, -5f, x + 4.5f, 8f, 4f, 4f, Path.Direction.CW)
            toon(c, shape, 0xFFFF5C7A.toInt(), depth = 1.5f, stroke = 3f, gloss = false, shadow = false)
        }
    }

    /** Seated person on a chair at [x], facing right. */
    private fun sitter(c: Canvas, s: Float, lk: AvatarLook, x: Float, arms: Arms, eyes: Eyes, tilt: Float = 0f, bob: Float = 0f) {
        val outfit = AvatarLook.OUTFITS[lk.outfit]; val skin = AvatarLook.SKINS[lk.skin]
        limb(c, x - 14f, 24f, x - 14f, 44f, 3f, shade(WOOD, 0.8f)); limb(c, x + 12f, 24f, x + 12f, 44f, 3f, shade(WOOD, 0.8f))
        shape.reset(); shape.addRoundRect(x - 21f, -14f, x - 13f, 26f, 4f, 4f, Path.Direction.CW)
        toon(c, shape, WOOD, depth = 2f, stroke = 3f, gloss = false, shadow = false)
        shape.reset(); shape.addRoundRect(x - 21f, 19f, x + 16f, 26f, 3f, 3f, Path.Direction.CW)
        toon(c, shape, WOOD, depth = 3f, stroke = 3f, gloss = false, shadow = false)
        val hy = 16f; val sy = -8f + bob
        limb3(c, x - 2f, hy, x + 18f, hy + 1f, x + 18f, 41f, 8f, shade(PANTS, 0.75f)); shoe(c, x + 19f, 42f)
        val back = shade(outfit, 0.75f)
        when (arms) {
            Arms.PHONE -> limb3(c, x, sy + 4f, x + 9f, sy + 16f, x + 17f, sy + 5f, 5.5f, back)
            Arms.KNEES -> limb3(c, x, sy + 4f, x + 8f, sy + 18f, x + 17f, hy, 5.5f, back)
            Arms.MUG -> limb3(c, x, sy + 4f, x + 6f, sy + 18f, x + 14f, hy - 2f, 5.5f, back)
        }
        limb(c, x, hy - 3f, x + 2f, sy, 19f, outfit)
        limb3(c, x + 1f, hy + 1f, x + 22f, hy + 2f, x + 22f, 42f, 8.5f, PANTS); shoe(c, x + 23f, 43f)
        head(c, x + 4f, sy - 18f, lk, s, eyes, lookX = 2f, tilt = tilt)
        when (arms) {
            Arms.PHONE -> {
                limb3(c, x + 3f, sy + 4f, x + 12f, sy + 16f, x + 19f, sy + 4f, 6f, outfit)
                phoneProp(c, x + 21f, sy + 1f, -15f, s, on = true)
                dot(c, x + 19f, sy + 4f, 3.2f, skin)
            }
            Arms.KNEES -> { limb3(c, x + 3f, sy + 4f, x + 11f, sy + 18f, x + 20f, hy + 1f, 6f, outfit); dot(c, x + 20f, hy + 1f, 3.2f, skin) }
            Arms.MUG -> {
                limb3(c, x + 3f, sy + 4f, x + 12f, sy + 16f, x + 17f, sy + 6f, 6f, outfit)
                mug(c, x + 21f, sy + 4f, s)
                dot(c, x + 17f, sy + 6f, 3.2f, skin)
            }
        }
    }

    /** Standing person with feet on y = 44; angles in degrees. */
    private fun stander(
        c: Canvas, s: Float, lk: AvatarLook, x: Float, legA: Float, armA: Float, eyes: Eyes,
        tilt: Float = 0f, lift: Float = 0f, phones: Boolean = false, wave: Boolean = false,
    ) {
        val outfit = AvatarLook.OUTFITS[lk.outfit]; val skin = AvatarLook.SKINS[lk.skin]
        val hx = x; val hy = 14f - lift; val sx = x + 1f; val sy = -8f - lift
        fun foot(a: Float) = Math.toRadians(a.toDouble()).let { hx + (sin(it) * 29f).toFloat() to hy + (cos(it) * 29f).toFloat() }
        fun hand(a: Float) = Math.toRadians(a.toDouble()).let { sx + (sin(it) * 21f).toFloat() to sy + 3f + (cos(it) * 21f).toFloat() }
        val (bfx, bfy) = foot(-legA)
        limb(c, hx, hy, bfx, bfy, 8f, shade(PANTS, 0.75f)); shoe(c, bfx + 1f, bfy + 1f)
        val (bhx, bhy) = hand(-armA)
        limb3(c, sx, sy + 3f, (sx + bhx) / 2 - 1f, (sy + bhy) / 2 + 2f, bhx, bhy, 5.5f, shade(outfit, 0.75f))
        dot(c, bhx, bhy, 3f, shade(skin, 0.9f))
        limb(c, hx, hy - 2f, sx, sy, 19f, outfit)
        val (ffx, ffy) = foot(legA)
        limb(c, hx + 1f, hy, ffx, ffy, 8.5f, PANTS); shoe(c, ffx + 1f, ffy + 1f)
        head(c, x + 2f, sy - 18f, lk, s, eyes, lookX = 1.5f, tilt = tilt, phones = phones)
        if (wave) {
            // elbow out to the side, forearm waving above it
            val a = Math.toRadians((165.0 + sin(s * 7f) * 25.0))
            val exw = sx + 21f; val eyw = sy + 3f
            val hxw = exw + (sin(a) * 15f).toFloat(); val hyw = eyw + (cos(a) * 15f).toFloat()
            limb3(c, sx + 3f, sy + 3f, exw, eyw, hxw, hyw, 6f, outfit); dot(c, hxw, hyw, 3.4f, skin)
        } else {
            val (fhx, fhy) = hand(armA)
            limb3(c, sx + 2f, sy + 3f, (sx + fhx) / 2 + 1f, (sy + fhy) / 2 + 2f, fhx, fhy, 6f, outfit)
            dot(c, fhx, fhy, 3.2f, skin)
        }
    }

    // ── Props ──

    private fun phoneProp(c: Canvas, x: Float, y: Float, rot: Float, s: Float, on: Boolean) {
        c.save(); c.translate(x, y); c.rotate(rot)
        fill.color = INK; c.drawRoundRect(-4.5f, -8f, 4.5f, 8f, 2.5f, 2.5f, fill)
        fill.color = if (on) (if ((s * 3f).toInt() % 3 == 0) 0xFFA5F3FC.toInt() else 0xFF7DD3FC.toInt()) else 0xFF374151.toInt()
        c.drawRoundRect(-3f, -6.3f, 3f, 6.3f, 1.2f, 1.2f, fill)
        c.restore()
    }

    private fun mug(c: Canvas, x: Float, y: Float, s: Float) {
        line.color = INK; line.strokeWidth = 2.5f
        c.drawArc(x + 2f, y - 3f, x + 9f, y + 4f, -90f, 180f, false, line)
        shape.reset(); shape.addRoundRect(x - 5f, y - 6f, x + 5f, y + 6f, 2.5f, 2.5f, Path.Direction.CW)
        toon(c, shape, 0xFFFFF7ED.toInt(), depth = 1.5f, stroke = 2.5f, gloss = false, shadow = false)
        for (i in 0..1) {
            val ph = (s * 0.6f + i * 0.5f) % 1f
            line.color = Color.WHITE; line.alpha = ((1f - ph) * 200).toInt(); line.strokeWidth = 2f
            val sx = x - 2f + i * 4f; val sy = y - 9f - ph * 14f
            c.drawLine(sx + sin(ph * 9f) * 2f, sy, sx + sin(ph * 9f + 1.5f) * 2f, sy - 5f, line)
        }
        line.color = INK
    }

    private fun plant(c: Canvas, x: Float, s: Float) {
        for (i in 0..2) {
            c.save(); c.rotate(-30f + i * 30f + sin(s * 1.5f + i) * 4f, x, 32f)
            shape.reset(); shape.addOval(x - 5f, 6f, x + 5f, 32f, Path.Direction.CW)
            toon(c, shape, 0xFF4ADE80.toInt(), depth = 1.5f, stroke = 2.5f, gloss = false, shadow = false)
            c.restore()
        }
        shape.reset(); shape.moveTo(x - 10f, 28f); shape.lineTo(x + 10f, 28f); shape.lineTo(x + 7f, 44f); shape.lineTo(x - 7f, 44f); shape.close()
        toon(c, shape, 0xFFE07A4A.toInt(), depth = 2f, stroke = 3f, gloss = false, shadow = false)
    }

    private fun table(c: Canvas, x1: Float, x2: Float, top: Float) {
        limb(c, x1 + 6f, top + 4f, x1 + 6f, 44f, 3f, shade(WOOD, 0.8f)); limb(c, x2 - 6f, top + 4f, x2 - 6f, 44f, 3f, shade(WOOD, 0.8f))
        shape.reset(); shape.addRoundRect(x1, top, x2, top + 6f, 3f, 3f, Path.Direction.CW)
        toon(c, shape, WOOD, depth = 3f, stroke = 3f, gloss = false, shadow = false)
    }

    private fun window(c: Canvas, x: Float, y: Float, s: Float, night: Boolean) {
        box.set(x - 20f, y - 16f, x + 20f, y + 16f)
        fill.color = if (night) 0xFF1B1745.toInt() else 0xFF9ED8FF.toInt(); c.drawRoundRect(box, 4f, 4f, fill)
        c.save(); c.clipRect(box)
        if (night) {
            shape.reset(); shape.addCircle(x + 8f, y - 6f, 6f, Path.Direction.CW)
            aux.reset(); aux.addCircle(x + 11f, y - 8f, 5f, Path.Direction.CW); shape.op(aux, Path.Op.DIFFERENCE)
            fill.color = 0xFFFFE08A.toInt(); c.drawPath(shape, fill)
            sparkle(c, x - 10f, y - 6f, 2.5f + sin(s * 3f), 0xFFFFF3B0.toInt()); sparkle(c, x - 2f, y + 7f, 2f + sin(s * 2.3f + 1f), 0xFFFFF3B0.toInt())
        } else {
            fill.color = 0xFFFFD43B.toInt(); c.drawCircle(x + 9f, y - 7f, 6f, fill)
            val cx = x - 26f + wrap(s * 5f, 56f)
            fill.color = Color.WHITE; c.drawCircle(cx, y + 4f, 5f, fill); c.drawCircle(cx + 6f, y + 2f, 6f, fill); c.drawCircle(cx + 12f, y + 4f, 4.5f, fill)
        }
        c.restore()
        line.color = Color.WHITE; line.strokeWidth = 2.5f
        c.drawLine(x, y - 16f, x, y + 16f, line); c.drawLine(x - 20f, y, x + 20f, y, line)
        line.color = INK; line.strokeWidth = 3.5f; c.drawRoundRect(box, 4f, 4f, line)
    }

    private fun room(c: Canvas, s: Float, dark: Boolean) {
        fill.color = if (dark) 0xFF2D2754.toInt() else 0xFFFFEBD2.toInt(); c.drawRect(-100f, -90f, 100f, 90f, fill)
        fill.color = if (dark) 0x12FFFFFF else 0x12A0522D
        for (i in -4..4) c.drawRect(i * 24f - 5f, -90f, i * 24f + 5f, 44f, fill)
        fill.color = if (dark) 0xFF3A2E5E.toInt() else 0xFFE9B97F.toInt(); c.drawRect(-100f, 44f, 100f, 90f, fill)
        line.color = 0x30000000; line.strokeWidth = 1.5f
        for (i in -3..3) c.drawLine(i * 32f, 50f, i * 32f - 8f, 90f, line)
        line.color = INK; line.strokeWidth = 3f; c.drawLine(-100f, 44f, 100f, 44f, line)
        window(c, 58f, -38f, s, dark)
    }

    private fun outdoors(c: Canvas, s: Float, dark: Boolean, scroll: Float, road: Boolean) {
        fill.shader = LinearGradient(0f, -75f, 0f, 44f,
            if (dark) 0xFF1E1A4A.toInt() else 0xFF8FD3FF.toInt(), if (dark) 0xFF43307E.toInt() else 0xFFE6F6FF.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(-100f, -90f, 100f, 90f, fill); fill.shader = null
        if (dark) {
            for (i in 0..5) sparkle(c, -70f + i * 27f, -50f + (i * 37 % 30), 2f + 1.5f * sin(s * 2f + i), 0xFFFFF3B0.toInt())
            shape.reset(); shape.addCircle(62f, -46f, 10f, Path.Direction.CW)
            aux.reset(); aux.addCircle(67f, -50f, 8.5f, Path.Direction.CW); shape.op(aux, Path.Op.DIFFERENCE)
            fill.color = 0xFFFFE08A.toInt(); c.drawPath(shape, fill)
        } else {
            fill.color = 0x55FFE27A; c.drawCircle(62f, -46f, 16f + sin(s * 2f) * 1.5f, fill)
            fill.color = 0xFFFFD43B.toInt(); c.drawCircle(62f, -46f, 10f, fill)
            for (i in 0..1) {
                val cx = 110f - wrap(s * (4f + i * 3f) + i * 120f, 240f)
                val cy = -54f + i * 18f
                fill.color = Color.WHITE; c.drawCircle(cx, cy, 6f, fill); c.drawCircle(cx + 8f, cy - 3f, 8f, fill); c.drawCircle(cx + 16f, cy, 6f, fill)
            }
        }
        // far hills, slowly sliding by
        fill.color = if (dark) 0xFF2E2560.toInt() else 0xFFB8E4C9.toInt()
        for (i in 0..3) c.drawCircle(130f - wrap(scroll * 0.25f + i * 80f, 320f), 52f, 40f, fill)
        val gy = if (road) 48f else 44f
        if (road) {
            fill.color = if (dark) 0xFF4B4470.toInt() else 0xFFD6D3D1.toInt(); c.drawRect(-100f, 40f, 100f, 48f, fill)
            fill.color = if (dark) 0xFF2C3142.toInt() else 0xFF5B6475.toInt(); c.drawRect(-100f, gy, 100f, 90f, fill)
            fill.color = 0xDDFFFFFF.toInt()
            for (i in 0..6) { val x = 110f - wrap(scroll + i * 36f, 252f); c.drawRect(x, 54f, x + 16f, 57f, fill) }
        } else {
            fill.color = if (dark) 0xFF2F6B4F.toInt() else 0xFF7BD389.toInt(); c.drawRect(-100f, gy, 100f, 90f, fill)
        }
        line.color = INK; line.strokeWidth = 3f; c.drawLine(-100f, gy, 100f, gy, line)
    }

    private fun tree(c: Canvas, x: Float, gy: Float, k: Float) {
        limb(c, x, gy, x, gy - 22f * k, 4f * k, 0xFF9C6B3C.toInt())
        shape.reset(); shape.addCircle(x, gy - 30f * k, 13f * k, Path.Direction.CW)
        toon(c, shape, 0xFF34C47C.toInt(), depth = 2.5f, stroke = 3f, shadow = false)
    }

    private fun wheel(c: Canvas, x: Float, y: Float, r: Float, spin: Float, thin: Boolean = false) {
        if (thin) {
            line.color = INK; line.strokeWidth = 5f; c.drawCircle(x, y, r, line)
            line.color = 0xFF9CA3AF.toInt(); line.strokeWidth = 2f; c.drawCircle(x, y, r, line)
        } else {
            fill.color = INK; c.drawCircle(x, y, r, fill)
            fill.color = 0xFF9CA3AF.toInt(); c.drawCircle(x, y, r * 0.58f, fill)
        }
        line.color = INK; line.strokeWidth = if (thin) 1.2f else 2f
        val rr = if (thin) r * 0.92f else r * 0.55f
        for (k in 0..2) {
            val a = spin + k * PI.toFloat() / 3f
            c.drawLine(x - cos(a) * rr, y - sin(a) * rr, x + cos(a) * rr, y + sin(a) * rr, line)
        }
        fill.color = 0xFFE5E7EB.toInt(); c.drawCircle(x, y, r * 0.18f, fill)
    }

    private fun smoke(c: Canvas, x: Float, y: Float, s: Float) {
        for (i in 0..4) {
            val ph = (s * 1.3f + i / 5f) % 1f
            val px = x - ph * 42f; val py = y - ph * 14f + sin(s * 3f + i) * 2f; val r = 3f + ph * 8f
            fill.color = 0xFFE5E7EB.toInt(); fill.alpha = ((1f - ph) * 190).toInt(); c.drawCircle(px, py, r, fill)
            line.color = INK; line.alpha = ((1f - ph) * 110).toInt(); line.strokeWidth = 2f; c.drawCircle(px, py, r, line)
        }
        fill.alpha = 255; line.color = INK
    }

    private fun speedLines(c: Canvas, s: Float) {
        line.strokeWidth = 2.5f
        for (i in 0..2) {
            val ph = (s * 2.2f + i * 0.37f) % 1f
            val x = 20f - ph * 120f; val y = -40f + i * 18f
            line.color = Color.WHITE; line.alpha = (sin(ph * PI.toFloat()) * 200).toInt()
            c.drawLine(x, y, x + 22f, y, line)
        }
        line.color = INK
    }

    private fun batteryChip(c: Canvas, x: Float, y: Float, pct: Int, charging: Boolean, s: Float) {
        val col = when { pct <= 15 -> 0xFFFF4D6D.toInt(); pct <= 35 -> 0xFFFF9F43.toInt(); else -> 0xFF4ADE80.toInt() }
        shape.reset(); shape.addRoundRect(x, y - 7.5f, x + 32f, y + 7.5f, 4f, 4f, Path.Direction.CW)
        fill.color = 0xFF2A2238.toInt(); c.drawPath(shape, fill)
        val blink = pct <= 15 && !charging && (s * 2f).toInt() % 2 == 0
        fill.color = col; fill.alpha = if (blink) 110 else 255
        c.drawRoundRect(x + 2.5f, y - 5f, x + 2.5f + 27f * (pct.coerceIn(4, 100) / 100f), y + 5f, 2.5f, 2.5f, fill); fill.alpha = 255
        line.color = INK; line.strokeWidth = 3f; c.drawPath(shape, line)
        fill.color = INK; c.drawRoundRect(x + 32f, y - 3f, x + 36f, y + 3f, 1.5f, 1.5f, fill)
        chunky(c, if (charging) "⚡$pct%" else "$pct%", x + 16f, y + 3.5f, 9f, maxW = 28f)
    }

    private fun note(c: Canvas, x: Float, y: Float, k: Float, col: Int, alpha: Int) {
        fill.color = col; fill.alpha = alpha; c.drawOval(x - 4f * k, y - 3f * k, x + 4f * k, y + 3f * k, fill)
        line.color = col; line.alpha = alpha; line.strokeWidth = 2f * k
        c.drawLine(x + 3.4f * k, y, x + 3.4f * k, y - 13f * k, line); c.drawLine(x + 3.4f * k, y - 13f * k, x + 8f * k, y - 9f * k, line)
        fill.alpha = 255; line.color = INK
    }

    // ── Scenes ──

    private fun scenePhone(c: Canvas, s: Float, lk: AvatarLook, dark: Boolean) {
        room(c, s, dark)
        plant(c, 72f, s)
        val x = -30f
        sitter(c, s, lk, x, Arms.PHONE, Eyes.OPEN, tilt = 6f + sin(s * 1.3f) * 2f, bob = sin(s * 2f) * 0.6f)
        fill.color = 0x2238E0FF; c.drawOval(x + 2f, -36f, x + 30f, -4f, fill)  // screen glow on the face
        for (i in 0..2) { // little chat bubbles popping out of the phone
            val ph = (s * 0.42f + i / 3f) % 1f
            val bx = x + 26f + ph * 34f; val by = -16f - ph * 40f
            val a = (sin(ph * PI.toFloat()) * 255).toInt()
            fill.color = Color.WHITE; fill.alpha = a; c.drawRoundRect(bx - 8f, by - 5.5f, bx + 8f, by + 5.5f, 5f, 5f, fill)
            line.color = INK; line.alpha = a; line.strokeWidth = 2f; c.drawRoundRect(bx - 8f, by - 5.5f, bx + 8f, by + 5.5f, 5f, 5f, line)
            if (i == 1) { heartPath(aux, 3.4f, bx, by); fill.color = 0xFFFF4D6D.toInt(); fill.alpha = a; c.drawPath(aux, fill) }
            else { fill.color = INK; fill.alpha = a; for (d in -1..1) c.drawCircle(bx + d * 4f, by, 1.3f, fill) }
            fill.alpha = 255; line.color = INK
        }
    }

    private fun sceneAway(c: Canvas, s: Float, lk: AvatarLook, dark: Boolean) {
        room(c, s, dark)
        table(c, 22f, 64f, 14f)
        fill.color = INK; c.drawRoundRect(32f, 8f, 52f, 13f, 2f, 2f, fill)   // phone lying face down
        plant(c, 80f, s)
        sitter(c, s, lk, -36f, Arms.MUG, Eyes.OPEN, tilt = sin(s * 0.8f) * 3f, bob = sin(s * 1.2f) * 0.5f)
    }

    private fun sceneCharge(c: Canvas, s: Float, lk: AvatarLook, dark: Boolean) {
        room(c, s, dark)
        table(c, 12f, 72f, 12f)
        // socket on the wall and the cable to the phone
        shape.reset(); shape.addRoundRect(78f, 14f, 92f, 30f, 3f, 3f, Path.Direction.CW)
        toon(c, shape, 0xFFF3F4F6.toInt(), depth = 1.5f, stroke = 2.5f, gloss = false, shadow = false)
        aux.reset(); aux.moveTo(42f, 10f); aux.quadTo(60f, 14f, 74f, 11f); aux.quadTo(84f, 10f, 85f, 22f)
        line.color = INK; line.strokeWidth = 4f; line.style = Paint.Style.STROKE; c.drawPath(aux, line)
        line.color = Color.WHITE; line.strokeWidth = 2f; c.drawPath(aux, line); line.color = INK
        // phone on a stand, battery filling up
        fill.color = INK; c.drawRoundRect(33f, -22f, 51f, 10f, 4f, 4f, fill)
        fill.color = 0xFF1F2937.toInt(); c.drawRoundRect(35.5f, -19f, 48.5f, 7f, 2f, 2f, fill)
        line.color = Color.WHITE; line.strokeWidth = 1.6f; c.drawRoundRect(37.5f, -11f, 46.5f, -1f, 1.5f, 1.5f, line)
        fill.color = 0xFF4ADE80.toInt(); val lv = (s * 0.3f) % 1f
        c.drawRect(38.5f, -2f - 8f * lv, 45.5f, -2f, fill)
        fill.color = Color.WHITE; c.drawRect(40.5f, -12.5f, 43.5f, -11f, fill)
        aux.reset(); aux.moveTo(43f, -15.5f); aux.lineTo(39.5f, -10f); aux.lineTo(42f, -10f); aux.lineTo(41f, -5f); aux.lineTo(44.5f, -11f); aux.lineTo(42f, -11f); aux.close()
        fill.color = 0xFFFFD43B.toInt(); fill.alpha = (180 + 75 * sin(s * 4f)).toInt(); c.drawPath(aux, fill); fill.alpha = 255
        line.color = INK
        val x = -52f
        sitter(c, s, lk, x, Arms.KNEES, Eyes.SAD, tilt = 7f + sin(s * 0.9f) * 1.5f)
        // sigh: three dots appearing one by one
        val n = ((s * 1.2f) % 4f).toInt()
        for (i in 0 until min(n, 3)) { fill.color = Color.WHITE; c.drawCircle(x + 22f + i * 6f, -50f - i * 3f, 2.2f, fill); line.strokeWidth = 1.5f; c.drawCircle(x + 22f + i * 6f, -50f - i * 3f, 2.2f, line) }
        // sweat drop now and then
        val dp = (s * 0.5f) % 1f
        if (dp < 0.6f) {
            val dy = -34f + dp * 14f
            aux.reset(); aux.moveTo(x + 20f, dy - 5f); aux.quadTo(x + 24f, dy + 1f, x + 20f, dy + 2f); aux.quadTo(x + 16f, dy + 1f, x + 20f, dy - 5f); aux.close()
            fill.color = 0xFF93C5FD.toInt(); c.drawPath(aux, fill); line.strokeWidth = 1.5f; c.drawPath(aux, line)
        }
    }

    private fun sceneSleep(c: Canvas, s: Float, lk: AvatarLook) {
        room(c, s, dark = true)
        shape.reset(); shape.addRoundRect(-88f, -14f, -76f, 44f, 4f, 4f, Path.Direction.CW)
        toon(c, shape, WOOD, depth = 2f, stroke = 3f, gloss = false, shadow = false)
        limb(c, 72f, 34f, 72f, 44f, 3f, shade(WOOD, 0.8f))
        shape.reset(); shape.addRoundRect(-80f, 20f, 80f, 36f, 6f, 6f, Path.Direction.CW)
        toon(c, shape, 0xFFF3F4F6.toInt(), depth = 3f, stroke = 3.5f, gloss = false, shadow = false)
        shape.reset(); shape.addOval(-76f, 4f, -40f, 21f, Path.Direction.CW)
        toon(c, shape, Color.WHITE, depth = 2f, stroke = 3f, gloss = false, shadow = false)
        head(c, -56f, 4f, lk, s, Eyes.CLOSED, lookX = 0f, tilt = -18f)
        val k = 1f + 0.05f * sin(s * 1.7f)
        c.save(); c.scale(1f, k, 0f, 32f)
        p3.reset(); p3.moveTo(-46f, 33f); p3.lineTo(-46f, 14f); p3.quadTo(-42f, 3f, -26f, 5f)
        p3.quadTo(10f, -8f, 42f, 8f); p3.quadTo(72f, 10f, 80f, 22f); p3.lineTo(80f, 33f); p3.close()
        val blanket = shade(AvatarLook.OUTFITS[lk.outfit], 1.1f)
        toon(c, p3, blanket, depth = 3f, stroke = 3.5f, gloss = false, shadow = false)
        c.save(); c.clipPath(p3)
        line.color = 0x40FFFFFF; line.strokeWidth = 5f
        for (i in -2..4) c.drawLine(i * 24f, -10f, i * 24f + 18f, 40f, line)
        c.restore(); c.restore(); line.color = INK
        for (i in 0..2) {
            val ph = (s * 0.4f + i / 3f) % 1f
            chunky(c, "z", -42f + ph * 26f, -16f - ph * 34f, 10f + i * 4f, 0xFFB9A6FF.toInt(), alpha = ((1f - ph) * 255).toInt())
        }
    }

    private fun sceneWalk(c: Canvas, s: Float, lk: AvatarLook, dark: Boolean) {
        val scroll = s * 26f
        outdoors(c, s, dark, scroll, road = false)
        for (i in 0..2) tree(c, 120f - wrap(scroll * 0.7f + i * 90f, 270f), 44f, 1f)
        val ph = s * 6f
        stander(c, s, lk, -4f, legA = sin(ph) * 26f, armA = -sin(ph) * 30f, eyes = Eyes.OPEN, tilt = sin(ph) * 2f, lift = abs(sin(ph)) * 2.2f)
        for (i in 0..1) { // bushes in front
            val bx = 120f - wrap(scroll * 1.3f + i * 130f, 260f)
            shape.reset(); shape.addCircle(bx, 44f, 9f, Path.Direction.CW)
            aux.reset(); aux.addCircle(bx + 10f, 42f, 11f, Path.Direction.CW); shape.op(aux, Path.Op.UNION)
            aux.reset(); aux.addCircle(bx + 21f, 44f, 8f, Path.Direction.CW); shape.op(aux, Path.Op.UNION)
            toon(c, shape, 0xFF22A565.toInt(), depth = 2f, stroke = 3f, shadow = false)
        }
    }

    private fun sceneMusic(c: Canvas, s: Float, lk: AvatarLook, dark: Boolean) {
        room(c, s, dark)
        plant(c, -76f, s)
        val beat = s * 2.2f * PI.toFloat()
        val b = abs(sin(beat))
        stander(c, s, lk, -6f, legA = sin(beat) * 6f, armA = 18f + sin(beat) * 26f, eyes = Eyes.HAPPY, tilt = sin(beat) * 10f, lift = b * 3f, phones = true)
        val cols = intArrayOf(0xFFFF4D6D.toInt(), 0xFFFFD43B.toInt(), 0xFF38BDF8.toInt(), 0xFFA78BFA.toInt())
        for (i in 0..3) {
            val ph = (s * 0.5f + i / 4f) % 1f
            note(c, 14f + ph * 30f + sin(ph * 8f + i) * 5f, -30f - ph * 34f + i * 2f, 1f + (i % 2) * 0.25f, cols[i], (sin(ph * PI.toFloat()) * 255).toInt())
        }
    }

    /** Ghost mode: a sheet-ghost (with the owner's hair poking out) floating in a dim room. */
    private fun sceneGhost(c: Canvas, s: Float, lk: AvatarLook, dark: Boolean) {
        room(c, s, dark)
        fill.color = if (dark) 0x55000000 else 0x33201040; c.drawRect(-100f, -90f, 100f, 90f, fill)
        for (i in 0..4) {
            val ph = (s * 0.35f + i * 0.2f) % 1f
            sparkle(c, -70f + i * 35f, 30f - ph * 90f, 2.5f * (1f - ph) + 0.5f, 0xFFE9E3FF.toInt())
        }
        val by = sin(s * 2.5f) * 6f
        // hair tuft on top of the sheet, in the owner's hair colour
        aux.reset(); aux.moveTo(-6f, -36f + by); aux.quadTo(-4f, -50f + by, 4f, -48f + by); aux.quadTo(0f, -44f + by, 6f, -38f + by)
        line.color = INK; line.strokeWidth = 8f; c.drawPath(aux, line)
        line.color = AvatarLook.HAIR_COLORS[lk.hairColor]; line.strokeWidth = 4f; c.drawPath(aux, line); line.color = INK
        c.save(); c.translate(0f, 2f); c.scale(0.95f, 0.95f); ghost(c, s); c.restore()
        // "shh" bubble
        c.save(); c.translate(-52f, -26f + by * 0.5f)
        shape.reset(); shape.addRoundRect(-17f, -10f, 17f, 10f, 9f, 9f, Path.Direction.CW)
        toon(c, shape, Color.WHITE, depth = 2f, stroke = 2.5f, gloss = false, shadow = false)
        txt.style = Paint.Style.FILL; txt.color = INK; txt.textSize = 10f; txt.textAlign = Paint.Align.CENTER
        c.drawText("shh", 0f, 3.5f, txt)
        c.restore()
    }

    private const val NUDGE = "nudge"   // shown on my screen for a minute after they nudge me

    /** They just nudged me: jumping, waving, with a big "hey!" bubble. */
    private fun sceneNudge(c: Canvas, s: Float, lk: AvatarLook, dark: Boolean) {
        room(c, s, dark)
        val jump = -abs(sin(s * 5f)) * 8f
        c.save(); c.translate(0f, jump)
        stander(c, s * 2f, lk, -14f, legA = 6f, armA = 14f, eyes = Eyes.HAPPY, tilt = sin(s * 6f) * 6f, wave = true)
        c.restore()
        c.save(); c.translate(46f, -34f); c.rotate(sin(s * 7f) * 6f)
        shape.reset(); shape.addRoundRect(-22f, -13f, 22f, 13f, 12f, 12f, Path.Direction.CW)
        shape.moveTo(-10f, 11f); shape.lineTo(-18f, 22f); shape.lineTo(-2f, 12f); shape.close()
        toon(c, shape, 0xFFFFE066.toInt(), depth = 2.5f, stroke = 3f, gloss = false, shadow = false)
        chunky(c, "hey!", 0f, 5f, 15f)
        c.restore()
        for (i in 0..2) {
            val ph = (s * 1.2f + i / 3f) % 1f
            line.color = 0xFFFF6B9A.toInt(); line.strokeWidth = 2.5f; line.alpha = ((1f - ph) * 255).toInt()
            val r = 9f + ph * 9f; val hx = 20f; val hy = -24f + jump
            c.drawArc(hx - r, hy - r, hx + r, hy + r, 250f, 60f, false, line); c.drawArc(hx - r, hy - r, hx + r, hy + r, 150f, 50f, false, line)
            line.alpha = 255; line.color = INK
        }
    }

    private fun sceneHello(c: Canvas, s: Float, lk: AvatarLook, dark: Boolean) {
        room(c, s, dark)
        plant(c, -74f, s)
        stander(c, s, lk, 0f, legA = 4f, armA = 10f, eyes = Eyes.HAPPY, tilt = sin(s * 2f) * 4f, wave = true)
    }

    private fun bikeRider(c: Canvas, s: Float, lk: AvatarLook, hx: Float, hy: Float, sx: Float, sy: Float, kx: Float, ky: Float, fx: Float, fy: Float, ex: Float, ey: Float, gx: Float, gy: Float, helmet: Boolean) {
        val outfit = AvatarLook.OUTFITS[lk.outfit]; val skin = AvatarLook.SKINS[lk.skin]
        limb3(c, sx - 2f, sy + 3f, ex - 2f, ey - 1f, gx - 2f, gy - 1.5f, 5.5f, shade(outfit, 0.75f))
        limb(c, hx, hy, sx, sy, 19f, outfit)
        head(c, sx + 5f, sy - 17f, lk, s, Eyes.OPEN, lookX = 2.5f, tilt = 8f, helmet = helmet)
        limb3(c, hx + 1f, hy, kx, ky, fx, fy, 8.5f, PANTS); shoe(c, fx + 1f, fy + 1f)
        limb3(c, sx + 1f, sy + 3f, ex, ey, gx, gy, 6f, outfit); dot(c, gx, gy, 3.2f, skin)
    }

    private fun sceneRide(c: Canvas, s: Float, lk: AvatarLook, dark: Boolean) {
        val scroll = s * 150f
        outdoors(c, s, dark, scroll * 0.4f, road = true)
        for (i in 0..2) tree(c, 120f - wrap(scroll * 0.5f + i * 95f, 285f), 40f, 0.8f)
        speedLines(c, s)
        val bikeCol = if (lk.outfit in 0..1) 0xFF38BDF8.toInt() else 0xFFFF4D6D.toInt()
        c.save(); c.translate(-4f, sin(s * 46f) * 0.9f); c.rotate(sin(s * 29f) * 0.6f, 0f, 36f)
        // headlight beam
        aux.reset(); aux.moveTo(32f, 2f); aux.lineTo(110f, -16f); aux.lineTo(110f, 26f); aux.close()
        fill.color = if (dark) 0x55FFF3B0 else 0x26FFF3B0; c.drawPath(aux, fill)
        val spin = -s * 14f
        if (lk.ride == 1) {
            // scooter
            smoke(c, -50f, 33f, s)
            limb(c, -30f, 34f, -48f, 34f, 3f, 0xFFB8BCC6.toInt())
            wheel(c, -32f, 38f, 10f, spin); wheel(c, 32f, 38f, 10f, spin)
            limb(c, 24f, 10f, 32f, 38f, 3f, 0xFFCBD5E1.toInt())
            shape.reset(); shape.addRoundRect(-46f, 8f, -4f, 32f, 12f, 12f, Path.Direction.CW)
            toon(c, shape, bikeCol, depth = 3f, stroke = 3.5f, shadow = false)
            shape.reset(); shape.addRoundRect(-8f, 27f, 22f, 33f, 3f, 3f, Path.Direction.CW)
            toon(c, shape, shade(bikeCol, 0.7f), depth = 2f, stroke = 3f, gloss = false, shadow = false)
            shape.reset(); shape.moveTo(14f, 33f); shape.quadTo(28f, 18f, 23f, -8f); shape.lineTo(30f, -9f); shape.quadTo(37f, 16f, 23f, 34f); shape.close()
            toon(c, shape, bikeCol, depth = 2f, stroke = 3.5f, shadow = false)
            fill.color = 0xFF2A2238.toInt(); c.drawRoundRect(-38f, 2f, -10f, 9f, 3f, 3f, fill); line.strokeWidth = 3f; c.drawRoundRect(-38f, 2f, -10f, 9f, 3f, 3f, line)
            limb(c, 22f, -11f, 32f, -13f, 3f, 0xFF4B5563.toInt())
            dot(c, 32f, -1f, 3.8f, 0xFFFFE27A.toInt())
            bikeRider(c, s, lk, -22f, 2f, -13f, -20f, 2f, 6f, 6f, 26f, 4f, -9f, 26f, -12f, helmet = true)
        } else {
            // motorbike
            smoke(c, -46f, 28f, s)
            wheel(c, -34f, 35f, 13f, spin); wheel(c, 36f, 35f, 13f, spin)
            limb(c, -4f, 30f, -42f, 28f, 4f, 0xFFB8BCC6.toInt())
            shape.reset(); shape.addRoundRect(-10f, 16f, 12f, 32f, 4f, 4f, Path.Direction.CW)
            toon(c, shape, 0xFF6B7280.toInt(), depth = 2f, stroke = 3f, gloss = false, shadow = false)
            limb(c, 24f, 0f, 36f, 35f, 3.5f, 0xFFCBD5E1.toInt())
            shape.reset(); shape.moveTo(-44f, 20f); shape.lineTo(-30f, 10f); shape.lineTo(-6f, 10f); shape.lineTo(2f, 2f)
            shape.quadTo(14f, -4f, 25f, 2f); shape.lineTo(20f, 18f); shape.lineTo(-6f, 22f); shape.lineTo(-34f, 24f); shape.close()
            toon(c, shape, bikeCol, depth = 3f, stroke = 3.5f, round = 4f, shadow = false)
            fill.color = 0xFF2A2238.toInt(); c.drawRoundRect(-31f, 4f, -5f, 11f, 3f, 3f, fill); line.strokeWidth = 3f; c.drawRoundRect(-31f, 4f, -5f, 11f, 3f, 3f, line)
            limb(c, 23f, 0f, 22f, -6f, 3f, 0xFF4B5563.toInt()); limb(c, 19f, -6f, 27f, -4f, 3f, 0xFF4B5563.toInt())
            dot(c, 30f, 4f, 4.5f, 0xFFFFE27A.toInt())
            bikeRider(c, s, lk, -17f, 6f, -5f, -16f, 6f, 10f, 2f, 26f, 8f, -6f, 21f, -5f, helmet = true)
        }
        c.restore()
    }

    private fun knee(hx: Float, hy: Float, fx: Float, fy: Float, l: Float): Pair<Float, Float> {
        val dx = fx - hx; val dy = fy - hy
        val d = min(hypot(dx, dy), 2 * l - 0.01f).coerceAtLeast(0.01f)
        val h = sqrt(max(0f, l * l - d * d / 4f))
        var px = -dy / d * h; var py = dx / d * h
        if (px < 0f) { px = -px; py = -py }
        return (hx + fx) / 2 + px to (hy + fy) / 2 + py
    }

    private fun sceneCycle(c: Canvas, s: Float, lk: AvatarLook, dark: Boolean) {
        val scroll = s * 70f
        outdoors(c, s, dark, scroll * 0.4f, road = true)
        for (i in 0..2) tree(c, 120f - wrap(scroll * 0.6f + i * 95f, 285f), 40f, 0.8f)
        val frame = AvatarLook.OUTFITS[(lk.outfit + 4) % AvatarLook.OUTFITS.size]
        val outfit = AvatarLook.OUTFITS[lk.outfit]; val skin = AvatarLook.SKINS[lk.skin]
        c.save(); c.translate(-2f, abs(sin(s * 7f)) * -0.8f)
        val spin = -s * 9f
        wheel(c, -30f, 34f, 14f, spin, thin = true); wheel(c, 30f, 34f, 14f, spin, thin = true)
        val a = s * 7f
        val p1x = cos(a) * 7f; val p1y = 34f + sin(a) * 7f
        val p2x = -p1x; val p2y = 68f - p1y
        val hx = -10f; val hy = 0f
        // far leg
        val (k2x, k2y) = knee(hx, hy, p2x, p2y, 22f)
        limb3(c, hx, hy, k2x, k2y, p2x, p2y, 8f, shade(PANTS, 0.75f)); shoe(c, p2x, p2y)
        for ((x1, y1, x2, y2) in listOf(floatArrayOf(-30f, 34f, 0f, 34f), floatArrayOf(-30f, 34f, -8f, 6f), floatArrayOf(0f, 34f, -8f, 6f),
            floatArrayOf(-8f, 6f, 22f, 4f), floatArrayOf(0f, 34f, 22f, 4f), floatArrayOf(22f, 4f, 30f, 34f), floatArrayOf(22f, 4f, 24f, -6f))) {
            limb(c, x1, y1, x2, y2, 3f, frame)
        }
        limb(c, 20f, -6f, 28f, -7f, 3f, 0xFF4B5563.toInt())
        line.strokeWidth = 2.5f; c.drawLine(p1x, p1y, p2x, p2y, line)
        fill.color = 0xFF2A2238.toInt(); c.drawRoundRect(-17f, 1f, -3f, 6f, 2.5f, 2.5f, fill); line.strokeWidth = 2.5f; c.drawRoundRect(-17f, 1f, -3f, 6f, 2.5f, 2.5f, line)
        val sx = 2f; val sy = -20f
        limb3(c, sx - 2f, sy + 3f, 10f, -12f, 22f, -8f, 5.5f, shade(outfit, 0.75f))
        limb(c, hx, hy - 2f, sx, sy, 19f, outfit)
        head(c, sx + 5f, sy - 17f, lk, s, Eyes.HAPPY, lookX = 2.5f, tilt = 6f)
        val (k1x, k1y) = knee(hx, hy, p1x, p1y, 22f)
        limb3(c, hx + 1f, hy, k1x, k1y, p1x, p1y, 8.5f, PANTS); shoe(c, p1x, p1y)
        limb3(c, sx + 1f, sy + 3f, 12f, -12f, 24f, -7f, 6f, outfit); dot(c, 24f, -7f, 3.2f, skin)
        c.restore()
    }

    /** Sample data for sticker previews in the picker. */
    object Preview : LiveSource {
        override val myUid = "me"
        private val st = LiveStats(steps = 10_480, km = 7.6f, speed = 38f, speedAt = Long.MAX_VALUE / 2, name = "You")
        private val cycle = arrayOf(Status.PHONE, Status.RIDE, Status.WALK, Status.MUSIC, Status.CHARGE, Status.SLEEP, Status.CYCLE, Status.AWAY)
        override fun stats(uid: String): LiveStats {
            val now = System.currentTimeMillis()
            val k = cycle[((now / 2600L) % cycle.size).toInt()]
            return st.copy(status = k, statusAt = now - 17 * 60_000L, updatedAt = now, battery = 64, charging = k == Status.CHARGE)
        }
        override fun presence(roomId: String) = mapOf(
            "me" to Presence(12.9716, 77.5946, Long.MAX_VALUE / 2, "Me"),
            "p" to Presence(12.9916, 77.6146, Long.MAX_VALUE / 2, "Sam"),
        )
    }
}
