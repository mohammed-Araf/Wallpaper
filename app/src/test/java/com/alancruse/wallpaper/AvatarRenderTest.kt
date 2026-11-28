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
import com.alancruse.wallpaper.live.Pet
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
        val scenes = listOf(Status.PHONE, Status.RIDE, Status.CYCLE, Status.WALK, Status.MUSIC, Status.CHARGE, Status.SLEEP, Status.AWAY, Status.GHOST, "nudge", Status.NONE)
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
                override fun nudgedAt(uid: String) = if (sc == "nudge") System.currentTimeMillis() else 0L
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

    @Test
    fun renderPets() {
        val out = File(System.getProperty("avatar.out") ?: "build/avatar").apply { mkdirs() }
        val cols = 6; val cell = 180
        val rows = listOf(0, 1, 2)
        data class S(val a: Pet.Action, val st: Pet.Stage, val full: Float = 0.8f, val joy: Float = 0.8f, val hp: Float = 1f, val refusing: Boolean = false)
        val states = listOf(
            S(Pet.Action.IDLE, Pet.Stage.BABY), S(Pet.Action.RUN, Pet.Stage.YOUNG),
            S(Pet.Action.WALK, Pet.Stage.ADULT), S(Pet.Action.IDLE, Pet.Stage.OLD),
            S(Pet.Action.EAT, Pet.Stage.ADULT), S(Pet.Action.PETTED, Pet.Stage.ADULT),
            S(Pet.Action.SNIFF, Pet.Stage.ADULT, full = 0.2f), S(Pet.Action.SULK, Pet.Stage.ADULT, joy = 0.01f, refusing = true),
            S(Pet.Action.STUFFED, Pet.Stage.ADULT, full = 1f), S(Pet.Action.WEAK, Pet.Stage.ADULT, full = 0f, hp = 0.2f),
            S(Pet.Action.SLEEP, Pet.Stage.ADULT), S(Pet.Action.DEAD, Pet.Stage.ADULT, hp = 0f),
        )
        val perRow = 12
        val bmp = Bitmap.createBitmap(cols * cell, rows.size * (perRow / cols) * cell + 5 * cell, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); c.drawColor(Color.parseColor("#BFD7EA"))
        for (sp in rows) states.forEachIndexed { i, s ->
            val x = (i % cols) * cell + cell / 2f
            val y = (sp * 2 + i / cols) * cell + cell * 0.62f
            val st = StickerElement(kind = "w:pet", label = listOf("Mochi", "Bruno", "Coco")[sp], look = Pet.Look(sp, listOf(0, 3, 1)[sp]).encode(), x = x, y = y, size = 120f)
            val moving = s.a == Pet.Action.WALK || s.a == Pet.Action.RUN
            StickerArt.drawPetPose(c, st, Pet.Pose(x, y, if (i % 2 == 0) -1f else 1f, s.a, s.st, s.full, s.joy, s.hp, moving, s.refusing), 1700L)
        }
        // food bowl full / half / empty, the pit, the hand
        val fy = rows.size * 2 * cell + cell * 0.6f
        val now = 1795943400000L
        StickerArt.clockNow = { now }
        for ((i, left) in listOf(0, 1, 2).withIndex()) {
            val food = StickerElement(kind = "w:food", x = cell * (i + 0.5f), y = fy, size = 120f, timestamp = now - left * 100_000L)
            StickerArt.draw(c, food, 1700L, null, null)
        }
        c.save(); c.translate(cell * 3.5f, fy); c.scale(1.4f, 1.4f); StickerArt.pit(c, 1700L, true); c.restore()
        c.save(); c.translate(cell * 5f, fy - 20f); c.scale(2f, 2f); StickerArt.hand(c); c.restore()
        // thought cloud over a real pet
        val pet = Pet.adopt(StickerElement(kind = "w:pet", label = "Mochi", look = "s1c3", x = cell * 1.5f, y = fy + cell * 3.2f, size = 150f), "A", now - 10 * Pet.HOUR)
        StickerArt.world = listOf(pet)
        StickerArt.draw(c, pet.copy(x = cell * 1.5f), 1700L, StickerArt.Preview, "room")
        StickerArt.petThought(c, pet.copy(x = cell * 1.5f), 1700L, 1.1f, 1f)
        FileOutputStream(File(out, "pets.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        StickerArt.world = emptyList()
    }
}
