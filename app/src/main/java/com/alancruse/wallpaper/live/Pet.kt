package com.alancruse.wallpaper.live

import com.alancruse.wallpaper.canvas.StickerElement
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Rules for the shared pet sticker. Everything is worked out from timestamps stored on the
 * sticker, so the pet "lives" without a server: hunger grows from fedAt, cheer from playedAt.
 */
object Pet {
    val SPECIES = arrayOf("Cat", "Dog", "Bunny")
    val COLORS = intArrayOf(0xFFF6B26B.toInt(), 0xFFF3F0EA.toInt(), 0xFF8D8D99.toInt(), 0xFF7A5236.toInt(), 0xFFFFC2D6.toInt())

    const val HOUR = 3_600_000L
    /** Feeding again sooner than this does nothing ("not hungry yet"). */
    const val FULL_FOR = 2 * HOUR
    const val PECKISH_AFTER = 6 * HOUR
    const val HUNGRY_AFTER = 14 * HOUR
    const val SAD_AFTER = 30 * HOUR

    enum class Mood { EATING, PLAYING, SLEEPING, HAPPY, PECKISH, HUNGRY, SAD }

    data class Look(val species: Int = 0, val color: Int = 0) {
        fun encode() = "s${species}c$color"
    }

    fun look(s: String): Look {
        val m = Regex("([a-z])(\\d+)").findAll(s).associate { it.groupValues[1] to it.groupValues[2].toInt() }
        return Look((m["s"] ?: 0).coerceIn(0, SPECIES.size - 1), (m["c"] ?: 0).coerceIn(0, COLORS.size - 1))
    }

    /** The viewer's local hour, for night-time sleep. */
    private fun localHour(now: Long): Int {
        val off = java.util.TimeZone.getDefault().getOffset(now)
        return (((now + off) / HOUR) % 24).toInt()
    }

    fun mood(p: StickerElement, now: Long): Mood {
        val hungry = if (p.fedAt <= 0L) Long.MAX_VALUE else now - p.fedAt
        return when {
            p.fedAt > 0 && now - p.fedAt < 60_000L -> Mood.EATING
            p.playedAt > 0 && now - p.playedAt < 60_000L -> Mood.PLAYING
            hungry >= SAD_AFTER -> Mood.SAD
            hungry >= HUNGRY_AFTER -> Mood.HUNGRY
            localHour(now).let { it >= 23 || it < 7 } -> Mood.SLEEPING
            hungry >= PECKISH_AFTER -> Mood.PECKISH
            else -> Mood.HAPPY
        }
    }

    private fun day(t: Long) = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(t))

    /** The streak as it stands today: it's broken if the last counted feeding was before yesterday. */
    fun streak(p: StickerElement, now: Long): Int =
        if (p.streakDay == day(now) || p.streakDay == day(now - 24 * HOUR)) p.streak else 0

    /** Feed it now as [who]. Returns null when it's still full. */
    fun feed(p: StickerElement, who: String, now: Long): StickerElement? {
        if (p.fedAt > 0 && now - p.fedAt < FULL_FOR) return null
        val today = day(now)
        val streak = when (p.streakDay) {
            today -> p.streak
            day(now - 24 * HOUR) -> p.streak + 1
            else -> 1
        }
        return p.copy(fedAt = now, careBy = who, streak = streak, streakDay = today)
    }

    fun play(p: StickerElement, who: String, now: Long) = p.copy(playedAt = now, careBy = who)

    /** When [p] next becomes hungry (epoch ms), for the reminder. */
    fun hungryAt(p: StickerElement) = if (p.fedAt <= 0L) 0L else p.fedAt + HUNGRY_AFTER
}
