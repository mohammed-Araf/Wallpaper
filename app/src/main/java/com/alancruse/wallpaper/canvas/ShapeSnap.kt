package com.alancruse.wallpaper.canvas

import kotlin.math.*

/**
 * "Draw and hold" shape cleanup, like Notes on iPhone.
 * Returns a cleaned-up point list, or null if the stroke doesn't look like a shape.
 *
 * Open strokes: straight line, or a few straight segments (H, L, Z, N, M, arrows…).
 * Closed strokes: circle / ellipse, triangle, square / rectangle, small polygons.
 */
object ShapeSnap {

    fun snap(raw: List<Point>): List<Point>? {
        val pts = dedupe(raw)
        if (pts.size < 4) return null
        val minX = pts.minOf { it.x }; val maxX = pts.maxOf { it.x }
        val minY = pts.minOf { it.y }; val maxY = pts.maxOf { it.y }
        val w = maxX - minX; val h = maxY - minY
        val diag = hypot(w, h)
        if (diag < 24f) return null
        val len = pathLength(pts)
        val gap = dist(pts.first(), pts.last())
        val closed = gap < max(diag * 0.2f, 22f) && len > diag * 1.6f

        return if (!closed) snapOpen(pts, diag, len) else snapClosed(pts, minX, minY, w, h, diag)
    }

    // ── Open strokes ──

    private fun snapOpen(pts: List<Point>, diag: Float, len: Float): List<Point>? {
        val a = pts.first(); val b = pts.last()
        val chord = dist(a, b)
        // Judge straightness by deviation from the chord, not by length (hand jitter inflates length).
        if (chord > len * 0.5f && pts.maxOf { perp(it, a, b) } < max(chord * 0.08f, 8f)) return densify(listOf(a, b))
        val v = rdp(pts, diag * 0.05f)
        if (v.size in 3..7 && pathLength(v) > len * 0.75f) return densify(v)
        return null
    }

    // ── Closed strokes ──

    private fun snapClosed(pts: List<Point>, minX: Float, minY: Float, w: Float, h: Float, diag: Float): List<Point>? {
        // Ellipse: compare each point's normalized radius against 1.
        val cx = minX + w / 2; val cy = minY + h / 2
        val rx = max(w / 2, 1f); val ry = max(h / 2, 1f)
        val err = pts.sumOf { p ->
            val dx = (p.x - cx) / rx; val dy = (p.y - cy) / ry
            abs(sqrt((dx * dx + dy * dy).toDouble()) - 1.0)
        } / pts.size
        if (err < 0.085) {
            val round = abs(rx - ry) / max(rx, ry) < 0.18f
            val r = (rx + ry) / 2
            val start = atan2(pts.first().y - cy, pts.first().x - cx)
            return (0..96).map { i ->
                val t = start + 2 * PI.toFloat() * i / 96
                Point(cx + (if (round) r else rx) * cos(t), cy + (if (round) r else ry) * sin(t))
            }
        }

        // Polygon: simplify the loop and look at its corners.
        val loop = pts + pts.first()
        var v = rdp(loop, diag * 0.07f).dropLast(1)
        v = mergeCollinear(v)
        if (v.size !in 3..6) return null

        if (v.size == 4) {
            val rightAngles = (0 until 4).all { i ->
                abs(angleAt(v[(i + 3) % 4], v[i], v[(i + 1) % 4]) - 90f) < 22f
            }
            if (rightAngles) {
                val e = v[1].let { atan2(it.y - v[0].y, it.x - v[0].x) } * 180f / PI.toFloat()
                val axisAligned = abs(((e % 90f) + 90f) % 90f).let { min(it, 90f - it) } < 14f
                if (axisAligned) {
                    var rw = w; var rh = h
                    if (abs(rw - rh) / max(rw, rh) < 0.15f) { val s = (rw + rh) / 2; rw = s; rh = s }
                    val l = cx - rw / 2; val t = cy - rh / 2
                    return densify(listOf(Point(l, t), Point(l + rw, t), Point(l + rw, t + rh), Point(l, t + rh), Point(l, t)))
                }
            }
        }
        return densify(v + v.first())
    }

    // ── Geometry helpers ──

    private fun dist(a: Point, b: Point) = hypot(a.x - b.x, a.y - b.y)

    private fun pathLength(p: List<Point>) = (1 until p.size).sumOf { dist(p[it - 1], p[it]).toDouble() }.toFloat()

    private fun perp(p: Point, a: Point, b: Point): Float {
        val l = dist(a, b); if (l < 1e-3f) return dist(p, a)
        return abs((b.x - a.x) * (a.y - p.y) - (a.x - p.x) * (b.y - a.y)) / l
    }

    private fun angleAt(a: Point, b: Point, c: Point): Float {
        val v1x = a.x - b.x; val v1y = a.y - b.y; val v2x = c.x - b.x; val v2y = c.y - b.y
        val d = (v1x * v2x + v1y * v2y) / (hypot(v1x, v1y) * hypot(v2x, v2y) + 1e-6f)
        return acos(d.coerceIn(-1f, 1f)) * 180f / PI.toFloat()
    }

    private fun dedupe(p: List<Point>): List<Point> {
        val out = ArrayList<Point>(p.size)
        for (q in p) if (out.isEmpty() || dist(out.last(), q) > 0.5f) out.add(q)
        return out
    }

    /** Ramer–Douglas–Peucker. */
    private fun rdp(p: List<Point>, eps: Float): List<Point> {
        if (p.size < 3) return p
        var idx = 0; var dmax = 0f
        for (i in 1 until p.size - 1) { val d = perp(p[i], p.first(), p.last()); if (d > dmax) { dmax = d; idx = i } }
        if (dmax <= eps) return listOf(p.first(), p.last())
        val l = rdp(p.subList(0, idx + 1), eps); val r = rdp(p.subList(idx, p.size), eps)
        return l.dropLast(1) + r
    }

    private fun mergeCollinear(v: List<Point>): List<Point> {
        if (v.size < 4) return v
        val out = v.toMutableList()
        var changed = true
        while (changed && out.size > 3) {
            changed = false
            for (i in out.indices) {
                val a = out[(i - 1 + out.size) % out.size]; val b = out[i]; val c = out[(i + 1) % out.size]
                if (angleAt(a, b, c) > 155f) { out.removeAt(i); changed = true; break }
            }
        }
        return out
    }

    /** Dense points keep corners sharp under the renderer's curve smoothing. */
    private fun densify(v: List<Point>, step: Float = 5f): List<Point> {
        val out = ArrayList<Point>()
        for (i in 0 until v.size - 1) {
            val a = v[i]; val b = v[i + 1]
            val n = max(1, (dist(a, b) / step).toInt())
            for (k in 0 until n) out.add(Point(a.x + (b.x - a.x) * k / n, a.y + (b.y - a.y) * k / n))
        }
        out.add(v.last())
        return out
    }
}
