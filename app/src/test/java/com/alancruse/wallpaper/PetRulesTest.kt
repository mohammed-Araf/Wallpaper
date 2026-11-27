package com.alancruse.wallpaper

import com.alancruse.wallpaper.canvas.StickerElement
import com.alancruse.wallpaper.live.Pet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

class PetRulesTest {
    private val h = Pet.HOUR
    private val noon = 1795943400000L // 29 Nov 2026 12:00 IST
    private fun pet(fed: Long, born: Long = noon - 20 * Pet.DAY, species: Int = 1) =
        StickerElement(kind = "w:pet", look = Pet.Look(species, 0).encode(), x = 500f, y = 900f, size = 150f, bornAt = born, fedAt = fed)

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

    @Test fun hungerWeakDead() {
        val p = pet(noon - 15 * h)
        assertEquals(Pet.Action.SULK, Pet.pose(pet(noon - 15 * h, species = 0), noon, emptyList()).action) // cat sulks
        assertTrue(Pet.pose(p, noon, emptyList()).action in setOf(Pet.Action.WALK, Pet.Action.SNIFF))       // dog searches
        assertEquals(Pet.Action.WEAK, Pet.pose(pet(noon - 50 * h), noon, emptyList()).action)
        assertEquals(Pet.Action.DEAD, Pet.pose(pet(noon - 73 * h), noon, emptyList()).action)
        assertTrue(Pet.isDead(pet(noon - 73 * h), emptyList(), noon))
    }

    @Test fun runsToFoodEatsItAndIsFed() {
        val p = pet(noon - 10 * h)
        val food = StickerElement(kind = "w:food", label = "Sneha", x = 900f, y = 1000f, size = 110f, timestamp = noon)
        val world = listOf(p, food)
        val m = Pet.meals(p, world, noon + 3_600_000L).single()
        assertEquals(noon, m.start)
        val mid = (m.start + m.arrive) / 2
        assertEquals(Pet.Action.RUN, Pet.pose(p, mid, world).action)
        assertEquals(1f, Pet.foodLeft(food, world, mid), 0.001f)
        val eating = (m.arrive + m.end) / 2
        assertEquals(Pet.Action.EAT, Pet.pose(p, eating, world).action)
        assertEquals(0.5f, Pet.foodLeft(food, world, eating), 0.05f)
        assertEquals(0f, Pet.foodLeft(food, world, m.end + 1), 0.001f)
        assertEquals(m.end, Pet.fedAt(p, world, m.end + 10))
        assertEquals(0L, Pet.pose(p, m.end + 10, world).hunger / 1000)
    }

    @Test fun ignoresFoodWhileFull() {
        val p = pet(noon - h)
        val food = StickerElement(kind = "w:food", x = 900f, y = 1000f, size = 110f, timestamp = noon)
        assertTrue(Pet.meals(p, listOf(p, food), noon + 30 * 60_000L).isEmpty())
        assertEquals(noon + h, Pet.meals(p, listOf(p, food), noon + 2 * h).single().start)
    }

    @Test fun deadPetDoesntEat() {
        val p = pet(noon - 80 * h)
        val food = StickerElement(kind = "w:food", x = 900f, y = 1000f, size = 110f, timestamp = noon)
        assertTrue(Pet.meals(p, listOf(p, food), noon + h).isEmpty())
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
