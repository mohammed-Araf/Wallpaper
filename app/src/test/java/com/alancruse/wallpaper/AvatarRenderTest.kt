package com.alancruse.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

import com.alancruse.wallpaper.canvas.StickerElement
import com.alancruse.wallpaper.live.AvatarLook
import com.alancruse.wallpaper.live.LiveSource
import com.alancruse.wallpaper.live.LiveStats
import com.alancruse.wallpaper.live.Presence
import com.alancruse.wallpaper.live.Status
import com.alancruse.wallpaper.wallpaper.StickerArt
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/** Renders every status-avatar scene to PNGs (app files dir / avatar) for a visual check. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class)
class AvatarRenderTest {
    @Test
    fun renderScenes() {
        val outDir = System.getProperty("avatar.out") ?: "build/avatar"
        val out = File(outDir).apply { mkdirs() }
        val scenes = listOf(Status.PHONE, Status.RIDE, Status.CYCLE, Status.WALK, Status.MUSIC, Status.CHARGE, Status.SLEEP, Status.AWAY, Status.NONE)
        val looks = listOf(
            "boy" to AvatarLook(gender = 0, skin = 2, hair = 1, hairColor = 0, outfit = 4, dark = 0, ride = 0),
            "girl" to AvatarLook(gender = 1, skin = 1, hair = 1, hairColor = 1, outfit = 0, dark = 1, ride = 1),
        )
        for ((tag, look) in looks) for (sc in scenes) {
            val bmp = Bitmap.createBitmap(640, 540, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp); c.drawColor(Color.parseColor("#444444"))
            val src = object : LiveSource {
                override val myUid = "me"
                override fun stats(uid: String): LiveStats? {
                    if (sc.isEmpty()) return null
                    val now = System.currentTimeMillis()
                    return LiveStats(status = sc, statusAt = now - 17 * 60_000L, updatedAt = now, battery = if (sc == Status.CHARGE) 12 else 78, charging = sc == Status.CHARGE)
                }
                override fun presence(roomId: String) = emptyMap<String, Presence>()
            }
            val sz = 300f
            val st = StickerElement(kind = "w:me", owner = "me", label = if (tag == "boy") "Araf" else "Sneha", look = look.encode(), x = 320f, y = 270f + sz * 0.35f, size = sz)
            StickerArt.draw(c, st, 1234L, src, "test")
            FileOutputStream(File(out, "${tag}_${sc.ifEmpty { "none" }}.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        // hair picker heads
        val bmp = Bitmap.createBitmap(8 * 90, 90, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); c.drawColor(Color.WHITE)
        for (g in 0..1) for (h in 0..3) {
            c.save(); c.translate(45f + (g * 4 + h) * 90f, 45f); c.scale(1.8f, 1.8f)
            StickerArt.avatarHead(c, AvatarLook(gender = g, hair = h, hairColor = 2), 1000L); c.restore()
        }
        FileOutputStream(File(out, "heads.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun renderClocks() {
        val out = File(System.getProperty("avatar.out") ?: "build/avatar").apply { mkdirs() }
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Kolkata"))
        // 29 Nov 2026: 10:09:30 and 21:47:12 IST
        val times = listOf("day" to 1795927170000L, "night" to 1795969032000L)
        val bmp = Bitmap.createBitmap(4 * 300, 2 * 240, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); c.drawColor(Color.parseColor("#444444"))
        for ((row, h24) in listOf(false, true).withIndex()) for ((col, kt) in listOf("w:clock", "w:analog").flatMap { k -> times.map { k to it } }.withIndex()) {
            StickerArt.use24h = h24
            StickerArt.clockNow = { kt.second.second }
            val sz = if (kt.first == "w:clock") 160f else 190f
            StickerArt.draw(c, StickerElement(kind = kt.first, x = 150f + col * 300f, y = 120f + row * 240f + sz * 0.35f, size = sz), 0L, null, null)
        }
        FileOutputStream(File(out, "clocks.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun renderCountdowns() {
        val out = File(System.getProperty("avatar.out") ?: "build/avatar").apply { mkdirs() }
        val now = 1795927170000L
        StickerArt.clockNow = { now }
        val d = 86_400_000L
        val cases = listOf(
            Triple("Until we meet", 0, now + 12 * d + 4 * 3_600_000L + 22 * 60_000L),
            Triple("Sneha's birthday", 1, now + d),
            Triple("Exams over", 2, now + 5 * 3_600_000L + 7 * 60_000L + 9_000L),
            Triple("Goa trip", 3, now - 3 * 3_600_000L),
            Triple("Anniversary", 0, now - 3 * d),
            Triple("", 1, 0L),
        )
        val bmp = Bitmap.createBitmap(3 * 320, 2 * 240, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); c.drawColor(Color.parseColor("#444444"))
        cases.forEachIndexed { i, (title, icon, until) ->
            val sz = 150f
            StickerArt.draw(c, StickerElement(kind = "w:countdown", label = title, look = "i$icon", until = until,
                x = 160f + (i % 3) * 320f, y = 120f + (i / 3) * 240f + sz * 0.35f, size = sz), 700L, null, null)
        }
        FileOutputStream(File(out, "countdowns.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
