package com.alancruse.wallpaper

import com.alancruse.wallpaper.canvas.StickerElement
import com.alancruse.wallpaper.live.Pet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

class PetRulesTest {
    private val h = Pet.HOUR
    private val noon = 1795943400000L // 29 Nov 2026 12:00 IST

    /** A pet whose bars were ([full], [joy], [hp]) at [at]. */
    private fun pet(at: Long, full: Float = 1f, joy: Float = 1f, hp: Float = 1f, born: Long = noon - 20 * Pet.DAY, species: Int = 1) =
        StickerElement(kind = "w:pet", look = Pet.Look(species, 0).encode(), x = 500f, y = 900f, size = 150f, bornAt = born,
            fedAt = at, full = full, joy = joy, hp = hp, vitalsAt = at)

    private fun food(at: Long, x: Float = 900f) = StickerElement(kind = "w:food", label = "Sneha", x = x, y = 1000f, size = 110f, timestamp = at)

    @Before fun tz() { TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata")) }

    @Test fun streakGrowsDailyAndBreaksAfterAGap() {
        var p = Pet.fed(pet(0), "A", noon)
        assertEquals(1, p.streak)
        p = Pet.fed(p, "B", noon + 4 * h); assertEquals(1, p.streak)
        p = Pet.fed(p, "A", noon + 24 * h); assertEquals(2, p.streak)
        assertEquals(2, Pet.streak(p, noon + 47 * h))
        assertEquals(0, Pet.streak(p, noon + 72 * h))
    }

    @Test fun growsUp() {
        assertEquals(Pet.Stage.BABY, Pet.stage(pet(noon, born = noon - 2 * Pet.DAY), noon))
        assertEquals(Pet.Stage.YOUNG, Pet.stage(pet(noon, born = noon - 6 * Pet.DAY), noon))
        assertEquals(Pet.Stage.ADULT, Pet.stage(pet(noon, born = noon - 20 * Pet.DAY), noon))
        assertEquals(Pet.Stage.OLD, Pet.stage(pet(noon, born = noon - 31 * Pet.DAY), noon))
    }

    @Test fun barsDrainAndNeglectKills() {
        val p = pet(noon - 20 * h, species = 1)
        val s = Pet.life(p, emptyList(), noon).now
        assertTrue(s.full < Pet.HUNGRY)
        assertFalse(s.sad)
        assertEquals(Pet.Action.SULK, Pet.pose(pet(noon - 20 * h, species = 0), noon, emptyList()).action) // hungry cat sulks
        assertTrue(Pet.pose(p, noon, emptyList()).action in setOf(Pet.Action.WALK, Pet.Action.SNIFF))     // hungry dog searches
        // Nobody feeds or pets it: weak after ~2.4 days, dead before 3.
        assertEquals(Pet.Action.WEAK, Pet.pose(pet(noon - 60 * h), noon, emptyList()).action)
        assertTrue(Pet.isDead(pet(noon - 70 * h), emptyList(), noon))
        assertFalse(Pet.isDead(pet(noon - 50 * h), emptyList(), noon))
        assertEquals(Pet.Action.DEAD, Pet.pose(pet(noon - 70 * h), noon, emptyList()).action)
    }

    @Test fun runsToFoodWhereverItIsAndEatsItAll() {
        val p = pet(noon - 10 * h, full = 0.6f)
        val f = food(noon, x = 2000f) // far away
        val world = listOf(p, f)
        val m = Pet.life(p, world, noon + h).meals.single()
        assertEquals(noon, m.start) // no waiting, even though it ate 10 h ago
        val mid = (m.start + m.arrive) / 2
        assertEquals(Pet.Action.RUN, Pet.pose(p, mid, world).action)
        assertEquals(1f, Pet.foodLeft(f, world, mid), 0.001f)
        val eating = (m.arrive + m.end) / 2
        assertEquals(Pet.Action.EAT, Pet.pose(p, eating, world).action)
        assertEquals(0.5f, Pet.foodLeft(f, world, eating), 0.05f)
        assertEquals(0f, Pet.foodLeft(f, world, m.end + 1), 0.001f)      // empty bowl sits for a moment
        assertTrue(Pet.foodLeft(f, world, m.end + 5_000L) <= -1f)        // then it's gone
        val after = Pet.life(p, world, m.end + 10).now
        assertTrue(after.full > Pet.life(p, emptyList(), m.end + 10).now.full + 0.4f)
        // Written down once the bowl is cleared
        val (q, eaten) = Pet.settle(p, world, m.end + Pet.CLEAR_AFTER + 1)!!
        assertEquals(listOf(f), eaten)
        assertEquals(m.end, q.fedAt)
        assertEquals(after.full, Pet.life(q, emptyList(), m.end + 10).now.full, 0.01f)
    }

    @Test fun eatsBowlsOneAfterAnother() {
        val p = pet(noon, full = 0.2f)
        val world = listOf(p, food(noon, 700f), food(noon + 1000, 300f))
        val meals = Pet.life(p, world, noon + h).meals
        assertEquals(2, meals.size)
        assertTrue(meals[1].start >= meals[0].end)
    }

    @Test fun overfeedingHurtsHealth() {
        val p = pet(noon, full = 0.9f)
        val world = listOf(p) + (0 until 5).map { food(noon + it * 1000L, 700f + it * 60f) }
        val later = Pet.life(p, world, noon + 10 * h).now
        assertTrue("health ${later.hp}", later.hp < 0.6f)
        assertEquals(0f, later.bloat, 0.001f)
        // One bowl when peckish is fine
        val ok = Pet.life(pet(noon, full = 0.4f), listOf(p, food(noon)), noon + 10 * h).now
        assertTrue(ok.hp > 0.95f)
    }

    @Test fun miserablePetWontEatUntilPetted() {
        val p = pet(noon, full = 0.3f, joy = 0f)
        val f = food(noon)
        val world = listOf(p, f)
        val life = Pet.life(p, world, noon + h)
        assertTrue(life.meals.isEmpty())
        assertTrue(life.refusing)
        assertTrue(Pet.pose(p, noon + h, world).action == Pet.Action.SULK)
        // Pet it: happier, and now it goes for the food (after the petting)
        val (q, _) = Pet.pet(p, world, "A", noon + h, 500f, 900f)!!
        assertTrue(q.joy >= Pet.CHEER)
        assertEquals(Pet.Action.PETTED, Pet.pose(q, noon + h + 1000, listOf(q, f)).action)
        val m = Pet.life(q, listOf(q, f), noon + 2 * h).meals.single()
        assertTrue(m.start >= noon + h)
        // Can't pet again while it's still being petted
        assertNull(Pet.pet(q, listOf(q, f), "B", noon + h + 2000, 500f, 900f))
    }

    @Test fun pettingKeepsItHappyAndHealthy() {
        var p = pet(noon)
        var t = noon
        // Fed and petted twice a day for a week: stays healthy.
        repeat(14) {
            t += 12 * h
            val f = food(t)
            val (q, _) = Pet.pet(p, listOf(p), "A", t, p.x, p.y)!!
            p = q
            val m = Pet.life(p, listOf(p, f), t + h).meals.single()
            p = Pet.settle(p, listOf(p, f), m.end + Pet.CLEAR_AFTER + 1)!!.first
        }
        val s = Pet.life(p, emptyList(), t + h).now
        assertTrue("hp ${s.hp}", s.hp > 0.9f)
        assertFalse(s.dead)
    }

    @Test fun deadPetDoesntEatOrGetPetted() {
        val p = pet(noon - 80 * h)
        val world = listOf(p, food(noon))
        assertTrue(Pet.life(p, world, noon + h).meals.isEmpty())
        assertNull(Pet.pet(p, world, "A", noon + h, 0f, 0f))
    }

    @Test fun oldPetsWithoutVitalsStartFull() {
        val legacy = StickerElement(kind = "w:pet", look = "s1c0", x = 500f, y = 900f, size = 150f, bornAt = noon - Pet.DAY, fedAt = noon - h)
        val s = Pet.life(legacy, emptyList(), noon).now
        assertTrue(s.full > 0.9f && s.hp == 1f)
        assertNotNull(Pet.pose(legacy, noon, emptyList()))
    }

    @Test fun youngRunsFarAndFast() {
        val young = pet(noon, born = noon - 7 * Pet.DAY)
        val old = pet(noon, born = noon - 35 * Pet.DAY)
        fun travel(p: StickerElement): Float {
            var d = 0f; var last = Pet.pose(p, noon + h, emptyList())
            for (i in 1..60) { val q = Pet.pose(p, noon + h + i * 1000L, emptyList()); d += kotlin.math.hypot(q.x - last.x, q.y - last.y); last = q }
            return d
        }
        assertTrue(travel(young) > 4 * travel(old))
    }
}
