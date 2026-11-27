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
)

/** Rounded (~110 m) position, only written to canvases that hold a distance sticker. */
data class Presence(
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val at: Long = 0L,
    val name: String = "",
)

object StickerKinds {
    val animated = listOf("a:heart", "a:star", "a:fire", "a:sun", "a:moon", "a:ghost", "a:party", "a:rainbow", "a:love")
    val widgets = listOf("w:dist", "w:steps", "w:km", "w:speed", "w:steps_km", "w:speed_km", "w:board")

    fun isWidget(k: String) = k.startsWith("w:")
    fun needsStats(k: String) = isWidget(k) && k != "w:dist"
    fun needsSteps(k: String) = k in setOf("w:steps", "w:km", "w:steps_km", "w:board")
    fun needsLocation(k: String) = k in setOf("w:dist", "w:km", "w:speed", "w:speed_km", "w:board")
}

/** Read side used by the renderer. */
interface LiveSource {
    val myUid: String?
    fun stats(uid: String): LiveStats?
    fun presence(roomId: String): Map<String, Presence>
}

fun haversineKm(a: Presence, b: Presence): Double {
    val r = 6371.0
    val dLat = Math.toRadians(b.lat - a.lat); val dLng = Math.toRadians(b.lng - a.lng)
    val h = Math.sin(dLat / 2).let { it * it } +
        Math.cos(Math.toRadians(a.lat)) * Math.cos(Math.toRadians(b.lat)) * Math.sin(dLng / 2).let { it * it }
    return 2 * r * Math.asin(Math.sqrt(h))
}
