package com.alancruse.wallpaper.canvas

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*
import kotlin.random.Random

class ShapeSnapTest {
    private val rnd = Random(7)
    private fun j() = (rnd.nextFloat() - 0.5f) * 8f

    private fun maxRadiusDev(p: List<Point>): Float {
        val cx = (p.minOf { it.x } + p.maxOf { it.x }) / 2; val cy = (p.minOf { it.y } + p.maxOf { it.y }) / 2
        val r = p.map { hypot(it.x - cx, it.y - cy) }
        return r.max() - r.min()
    }

    @Test fun wobblyCircleBecomesRound() {
        val pts = (0..80).map { i -> val t = i / 80f * 2 * PI.toFloat() * 1.03f; Point(300 + 150 * cos(t) + j(), 300 + 150 * sin(t) + j()) }
        val out = ShapeSnap.snap(pts)!!
        assertTrue(maxRadiusDev(out) < 1f)
    }

    @Test fun roughSquareBecomesSquare() {
        val c = listOf(Point(100f, 100f), Point(310f, 104f), Point(305f, 300f), Point(98f, 296f), Point(102f, 108f))
        val pts = (0 until 4).flatMap { e -> (0 until 25).map { k -> val a = c[e]; val b = c[e + 1]; Point(a.x + (b.x - a.x) * k / 25 + j(), a.y + (b.y - a.y) * k / 25 + j()) } }
        val out = ShapeSnap.snap(pts)!!
        val xs = out.map { it.x }; val ys = out.map { it.y }
        assertEquals(xs.max() - xs.min(), ys.max() - ys.min(), 1f)
        assertTrue(out.all { abs(it.x - xs.min()) < 0.5f || abs(it.x - xs.max()) < 0.5f || abs(it.y - ys.min()) < 0.5f || abs(it.y - ys.max()) < 0.5f })
    }

    @Test fun shakyLineBecomesStraight() {
        val pts = (0..60).map { i -> Point(50f + i * 5f + j(), 200f + i * 2f + j()) }
        val out = ShapeSnap.snap(pts)!!
        val a = out.first(); val b = out.last()
        assertTrue(out.all { p -> abs((b.x - a.x) * (a.y - p.y) - (a.x - p.x) * (b.y - a.y)) / hypot(b.x - a.x, b.y - a.y) < 0.5f })
    }

    @Test fun zStrokeKeepsThreeStraightSegments() {
        val c = listOf(Point(0f, 0f), Point(200f, 0f), Point(0f, 200f), Point(200f, 200f))
        val pts = (0 until 3).flatMap { e -> (0 until 30).map { k -> val a = c[e]; val b = c[e + 1]; Point(a.x + (b.x - a.x) * k / 30 + j(), a.y + (b.y - a.y) * k / 30 + j()) } }
        assertNotNull(ShapeSnap.snap(pts))
    }

    @Test fun scribbleIsLeftAlone() {
        val pts = (0..120).map { i -> val t = i / 10f; Point(100 + t * 30 + 40 * sin(t * 2.3f), 200 + 60 * cos(t * 1.7f)) }
        assertNull(ShapeSnap.snap(pts))
    }
}
