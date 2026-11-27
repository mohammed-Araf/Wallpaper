package com.alancruse.wallpaper.live

/** Today's activity numbers a user shares with their canvases (no location). */
data class LiveStats(
    val steps: Int = 0,
    val km: Float = 0f,
    val speed: Float = 0f,     // km/h
    val speedAt: Long = 0L,    // when speed was measured
    val day: String = "",
    val name: String = "",
    val updatedAt: Long = 0L,
    /** What I'm doing right now (see [Status]); only filled while I have a status sticker. */
    val status: String = "",
    /** When the current status (or rest period) began. */
    val statusAt: Long = 0L,
    /** Battery percent, -1 when unknown. */
    val battery: Int = -1,
    val charging: Boolean = false,
    /** My UTC offset in minutes, so others can tell when it's night for me. */
    val tz: Int = 0,
)

/** Raw states published by the owner's phone; the renderer derives sleep from long rests. */
object Status {
    const val PHONE = "phone"
    const val RIDE = "ride"
    const val CYCLE = "cycle"
    const val WALK = "walk"
    const val MUSIC = "music"
    const val CHARGE = "charge"
    const val AWAY = "away"
    const val SLEEP = "sleep"   // derived only
    /** Ghost mode: the owner paused sharing for a while. */
    const val GHOST = "ghost"
    const val NONE = ""

    val REST = setOf(AWAY, CHARGE)
    private fun night(st: LiveStats, now: Long): Boolean {
        val h = (((now + st.tz * 60_000L) / 3_600_000L) % 24).toInt()
        return h >= 22 || h < 9
    }

    /** What to show for [st] at [now]: long rests become sleep, stale activities fade to away. */
    fun effective(st: LiveStats?, now: Long = System.currentTimeMillis()): String {
        if (st == null || st.status.isEmpty()) return NONE
        val since = now - st.statusAt
        val silent = now - st.updatedAt
        return when (st.status) {
            AWAY, CHARGE -> if ((since > 30 * 60_000L && night(st, now)) || since > 4 * 3_600_000L) SLEEP else st.status
            // The owner's phone stopped reporting: don't leave them "riding" forever.
            RIDE, CYCLE, WALK -> if (silent > 90 * 60_000L) AWAY else st.status
            PHONE, MUSIC -> if (silent > 3 * 3_600_000L) (if (night(st, now)) SLEEP else AWAY) else st.status
            else -> st.status
        }
    }
}

/** Rounded (~110 m) position, only written to canvases that hold a distance sticker. */
data class Presence(
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val at: Long = 0L,
    val name: String = "",
)

object StickerKinds {
    val animated = listOf("a:heart", "a:star", "a:fire", "a:sun", "a:moon", "a:ghost", "a:party", "a:rainbow", "a:love")
    val widgets = listOf("w:me", "w:clock", "w:analog", "w:pet", "w:food", "w:countdown", "w:dist", "w:steps", "w:km", "w:speed", "w:steps_km", "w:speed_km", "w:board")

    fun isWidget(k: String) = k.startsWith("w:")
    /** Plain clocks: the viewer's own local time, no data shared. */
    fun isClock(k: String) = k == "w:clock" || k == "w:analog"
    /** Counts down to a date everyone in the room shares (StickerElement.until). */
    fun isCountdown(k: String) = k == "w:countdown"
    /** Drawn from the viewer's clock, so a still lock-screen image would go stale. */
    fun ticksLocally(k: String) = isClock(k) || isCountdown(k)
    /** A pet everyone in the canvas looks after together. */
    fun isPet(k: String) = k == "w:pet"
    /** Food you drop for the pet; it runs over and eats it. */
    fun isFood(k: String) = k == "w:food"
    fun needsStats(k: String) = isWidget(k) && k != "w:dist" && !ticksLocally(k) && !isPet(k) && !isFood(k)
    /** The avatar that acts out what its owner is doing. */
    fun needsStatus(k: String) = k == "w:me"
    fun needsActivity(k: String) = needsSteps(k) || needsStatus(k)
    fun needsSteps(k: String) = k in setOf("w:steps", "w:km", "w:steps_km", "w:board")
    fun needsLocation(k: String) = k in setOf("w:dist", "w:km", "w:speed", "w:speed_km", "w:board")
}

/** Read side used by the renderer. */
interface LiveSource {
    val myUid: String?
    fun stats(uid: String): LiveStats?
    fun presence(roomId: String): Map<String, Presence>
    /** When [uid] last nudged me (epoch ms), so their avatar can wave on my screen. */
    fun nudgedAt(uid: String): Long = 0L
}

fun haversineKm(a: Presence, b: Presence): Double {
    val r = 6371.0
    val dLat = Math.toRadians(b.lat - a.lat); val dLng = Math.toRadians(b.lng - a.lng)
    val h = Math.sin(dLat / 2).let { it * it } +
        Math.cos(Math.toRadians(a.lat)) * Math.cos(Math.toRadians(b.lat)) * Math.sin(dLng / 2).let { it * it }
    return 2 * r * Math.asin(Math.sqrt(h))
}
