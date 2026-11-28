package com.alancruse.wallpaper.live

/**
 * How someone's status avatar looks. Stored on the sticker as a short string
 * ("g1s2h0c3o4d0r0") so it syncs with the canvas and survives unknown future keys.
 */
data class AvatarLook(
    /** 0 boy · 1 girl */
    val gender: Int = 0,
    val skin: Int = 2,
    /** Style index within the gender's set (see [HAIR_NAMES]). */
    val hair: Int = 0,
    val hairColor: Int = 0,
    val outfit: Int = 0,
    /** Card background: 0 day · 1 night */
    val dark: Int = 0,
    /** What "riding" shows: 0 motorbike · 1 scooter */
    val ride: Int = 0,
) {
    fun encode() = "g${gender}s${skin}h${hair}c${hairColor}o${outfit}d${dark}r${ride}"

    companion object {
        val SKINS = intArrayOf(0xFFFFE0C7.toInt(), 0xFFF6C9A0.toInt(), 0xFFE0A878.toInt(), 0xFFC28659.toInt(), 0xFF8D5A3B.toInt(), 0xFF5C3A25.toInt())
        val HAIR_COLORS = intArrayOf(0xFF2B1B14.toInt(), 0xFF5A3A22.toInt(), 0xFF9A5B2E.toInt(), 0xFFE8C26A.toInt(), 0xFFB23A48.toInt(), 0xFF6D5BD0.toInt())
        val OUTFITS = intArrayOf(0xFFFF5C7A.toInt(), 0xFFFF9F43.toInt(), 0xFFFFD43B.toInt(), 0xFF4ADE80.toInt(), 0xFF38BDF8.toInt(), 0xFF8B5CF6.toInt(), 0xFFF5F5F5.toInt(), 0xFF374151.toInt())
        val HAIR_NAMES = arrayOf(arrayOf("Short", "Spiky", "Curly", "Swoosh"), arrayOf("Long", "Ponytail", "Bun", "Bob"))

        fun decode(s: String): AvatarLook {
            if (s.isEmpty()) return AvatarLook()
            val m = Regex("([a-z])(\\d+)").findAll(s).associate { it.groupValues[1] to it.groupValues[2].toInt() }
            fun v(k: String, max: Int, d: Int) = (m[k] ?: d).coerceIn(0, max - 1)
            return AvatarLook(
                gender = v("g", 2, 0), skin = v("s", SKINS.size, 2), hair = v("h", 4, 0),
                hairColor = v("c", HAIR_COLORS.size, 0), outfit = v("o", OUTFITS.size, 0),
                dark = v("d", 2, 0), ride = v("r", 2, 0),
            )
        }
    }
}
