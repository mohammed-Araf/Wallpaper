package com.alancruse.wallpaper

import com.alancruse.wallpaper.canvas.StickerElement
import com.alancruse.wallpaper.live.Pet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

class PetRulesTest {
    private val h = Pet.HOUR
    private val noon = 1795943400000L // 29 Nov 2026 12:00 IST

    @Before fun tz() { TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata")) }

    @Test fun streakGrowsDailyAndBreaksAfterAGap() {
        var p = Pet.feed(StickerElement(kind = "w:pet"), "A", noon)!!
        assertEquals(1, p.streak)
        p = Pet.feed(p, "B", noon + 4 * h)!!            // same day
        assertEquals(1, p.streak)
        p = Pet.feed(p, "A", noon + 24 * h)!!           // next day
        assertEquals(2, p.streak)
        assertEquals(2, Pet.streak(p, noon + 47 * h))   // still alive the day after
        assertEquals(0, Pet.streak(p, noon + 72 * h))   // missed a whole day
        p = Pet.feed(p, "A", noon + 72 * h)!!
        assertEquals(1, p.streak)
    }

    @Test fun cantOverfeed() {
        val p = Pet.feed(StickerElement(kind = "w:pet"), "A", noon)!!
        assertNull(Pet.feed(p, "B", noon + h))
    }

    @Test fun moodFollowsHunger() {
        val p = StickerElement(kind = "w:pet", fedAt = noon)
        assertEquals(Pet.Mood.EATING, Pet.mood(p, noon + 10_000))
        assertEquals(Pet.Mood.HAPPY, Pet.mood(p, noon + h))
        assertEquals(Pet.Mood.PECKISH, Pet.mood(p, noon + 8 * h))
        assertEquals(Pet.Mood.HUNGRY, Pet.mood(p, noon + 15 * h))
        assertEquals(Pet.Mood.SAD, Pet.mood(p, noon + 31 * h))
        assertEquals(Pet.Mood.SLEEPING, Pet.mood(p, noon + 12 * h)) // midnight, not hungry yet
    }
}
