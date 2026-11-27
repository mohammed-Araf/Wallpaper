package com.alancruse.wallpaper.live

import com.alancruse.wallpaper.canvas.StickerElement
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.floor
import kotlin.math.hypot

/**
 * The shared pet. It roams the canvas, grows up, gets hungry, chases food you drop and can
 * die. Nothing is streamed: where it is and what it's doing is worked out from the time,
 * its birth time and the stickers on the canvas, so every phone sees the same pet.
 */
object Pet {
    val SPECIES = arrayOf("Cat", "Dog", "Bunny")
    val COLORS = intArrayOf(0xFFF6B26B.toInt(), 0xFFF3F0EA.toInt(), 0xFF8D8D99.toInt(), 0xFF7A5236.toInt(), 0xFFFFC2D6.toInt())

    const val HOUR = 3_600_000L
    const val DAY = 24 * HOUR
    /** It won't touch food sooner than this after a meal. */
    const val FULL_FOR = 2 * HOUR
    const val PECKISH_AFTER = 6 * HOUR
    const val HUNGRY_AFTER = 14 * HOUR
    const val SAD_AFTER = 30 * HOUR
    const val WEAK_AFTER = 48 * HOUR
    const val DEAD_AFTER = 72 * HOUR

    /**
     * Life stages. [slot] seconds per "go somewhere, then hang around" step, [move] the share
     * of it spent moving, [rx]/[ry] how far from home it wanders (canvas px), [chase] px/s
     * when it runs for food, [eat] seconds to finish a bowl.
     */
    enum class Stage(val fromDay: Int, val scale: Float, val slot: Float, val move: Float, val rx: Float, val ry: Float, val chase: Float, val eat: Float) {
        BABY(0, 0.6f, 9f, 0.45f, 170f, 260f, 70f, 35f),
        YOUNG(5, 0.8f, 2.6f, 0.85f, 420f, 760f, 480f, 22f),
        ADULT(15, 1f, 7f, 0.55f, 360f, 640f, 170f, 25f),
        OLD(30, 1f, 14f, 0.45f, 200f, 320f, 45f, 40f),
    }

    enum class Action { WALK, RUN, IDLE, SNIFF, SULK, EAT, SLEEP, PLAY, WEAK, DEAD }

    data class Look(val species: Int = 0, val color: Int = 0) {
        fun encode() = "s${species}c$color"
    }

    fun look(s: String): Look {
        val m = Regex("([a-z])(\\d+)").findAll(s).associate { it.groupValues[1] to it.groupValues[2].toInt() }
        return Look((m["s"] ?: 0).coerceIn(0, SPECIES.size - 1), (m["c"] ?: 0).coerceIn(0, COLORS.size - 1))
    }

    fun bornAt(p: StickerElement) = if (p.bornAt > 0) p.bornAt else p.timestamp

    fun stage(p: StickerElement, now: Long): Stage {
        val days = (now - bornAt(p)) / DAY
        return Stage.entries.last { days >= it.fromDay }
    }

    fun ageDays(p: StickerElement, now: Long) = ((now - bornAt(p)) / DAY).toInt()

    // ── Streak / care (stored on the sticker) ──

    private fun day(t: Long) = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(t))

    /** The streak as it stands today: broken if the last counted meal was before yesterday. */
    fun streak(p: StickerElement, now: Long): Int =
        if (p.streakDay == day(now) || p.streakDay == day(now - DAY)) p.streak else 0

    /** Record a meal that finished at [at], eaten from food [who] dropped. */
    fun fed(p: StickerElement, who: String, at: Long): StickerElement {
        val today = day(at)
        val streak = when (p.streakDay) { today -> p.streak; day(at - DAY) -> p.streak + 1; else -> 1 }
        return p.copy(fedAt = at, careBy = who.ifBlank { p.careBy }, streak = streak, streakDay = today)
    }

    fun play(p: StickerElement, who: String, now: Long) = p.copy(playedAt = now, careBy = who)

    // ── Meals: the pet runs to food, eats it, and is fed when the bowl is empty ──

    data class Meal(val food: StickerElement, val start: Long, val arrive: Long, val end: Long, val fromX: Float, val fromY: Float, val toX: Float, val toY: Float)

    private fun unit(p: StickerElement, st: Stage) = p.size * p.scale / 100f * st.scale

    /** Where the pet stands to eat from [f]: beside the bowl, facing it. */
    private fun spot(p: StickerElement, f: StickerElement, fromX: Float, st: Stage): Pair<Float, Float> {
        val dir = if (fromX <= f.x) -1f else 1f
        val fu = f.size * f.scale / 100f
        return f.x + dir * (30f * fu + 30f * unit(p, st)) to f.y + 6f * fu
    }

    fun meals(p: StickerElement, world: List<StickerElement>, now: Long): List<Meal> {
        val foods = world.filter { StickerKinds.isFood(it.kind) }.sortedBy { it.timestamp }
        if (foods.isEmpty()) return emptyList()
        val out = ArrayList<Meal>()
        var fed = p.fedAt
        for (f in foods) {
            val start = maxOf(f.timestamp, fed + FULL_FOR)
            if (start - fed >= DEAD_AFTER) break // died before it got there
            if (start > now) break
            val st = stage(p, start)
            val (fx, fy) = roam(p, start, fed, world)
            val (tx, ty) = spot(p, f, fx, st)
            val arrive = start + (hypot(tx - fx, ty - fy) / st.chase * 1000f).toLong()
            val end = arrive + (st.eat * 1000f).toLong()
            out.add(Meal(f, start, arrive, end, fx, fy, tx, ty))
            if (end > now) break
            fed = end
        }
        return out
    }

    /** Last time it was fed, counting meals that have finished but aren't written yet. */
    fun fedAt(p: StickerElement, world: List<StickerElement>, now: Long): Long =
        meals(p, world, now).lastOrNull { it.end <= now }?.end ?: p.fedAt

    /** How much of [food] is left (1 = full bowl, 0 = licked clean). */
    fun foodLeft(food: StickerElement, world: List<StickerElement>, now: Long): Float {
        val pet = world.firstOrNull { StickerKinds.isPet(it.kind) && !isDead(it, world, now) } ?: return 1f
        val m = meals(pet, world, now).firstOrNull { it.food.timestamp == food.timestamp && it.food.x == food.x && it.food.y == food.y } ?: return 1f
        return when {
            now < m.arrive -> 1f
            now >= m.end -> 0f
            else -> 1f - (now - m.arrive).toFloat() / (m.end - m.arrive)
        }
    }

    fun isDead(p: StickerElement, world: List<StickerElement>, now: Long) =
        p.fedAt > 0 && now - fedAt(p, world, now) >= DEAD_AFTER

    // ── Roaming ──

    private fun hash(seed: Long, k: Long, salt: Int): Float {
        var z = seed * 31 + k * -0x61c8864680b583ebL + salt * 0x632BE59BD9B4E019L
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        z = z xor (z ushr 31)
        return ((z ushr 11).toDouble() / (1L shl 53).toDouble()).toFloat()
    }

    private fun searches(p: StickerElement) = look(p.look).species != 0 // dogs and bunnies hunt for food; cats sulk

    /** The k-th place it heads for. Hungry dogs and bunnies go and sniff around other stickers. */
    private fun waypoint(p: StickerElement, k: Long, st: Stage, search: Boolean, world: List<StickerElement>): Pair<Float, Float> {
        val seed = bornAt(p)
        if (search) {
            val spots = world.filter { !StickerKinds.isPet(it.kind) && !StickerKinds.isFood(it.kind) }
            if (spots.isNotEmpty()) {
                val t = spots[(hash(seed, k, 7) * spots.size).toInt().coerceIn(0, spots.size - 1)]
                val r = t.size * t.scale * 0.6f
                val cx = if (hash(seed, k, 8) < 0.5f) -1f else 1f
                val cy = if (hash(seed, k, 9) < 0.5f) -0.6f else 0.6f
                return t.x + cx * (r + 30f) to t.y + cy * r
            }
        }
        return p.x + (hash(seed, k, 1) * 2f - 1f) * st.rx to p.y + (hash(seed, k, 2) * 2f - 1f) * st.ry
    }

    private data class Leg(val x: Float, val y: Float, val dx: Float, val moving: Boolean)

    private fun leg(p: StickerElement, t: Long, fed: Long, world: List<StickerElement>): Leg {
        val st = stage(p, t)
        val born = bornAt(p)
        val slotMs = (st.slot * 1000f).toLong()
        val f = (t - born).toDouble() / slotMs
        val k = floor(f).toLong()
        val frac = (f - k).toFloat()
        // Once hungry, dogs/bunnies switch to searching from the next step on (no jump).
        val hungrySlot = floor((fed + HUNGRY_AFTER - born).toDouble() / slotMs).toLong()
        val search = searches(p) && fed > 0
        val a = waypoint(p, k, st, search && k > hungrySlot, world)
        val b = waypoint(p, k + 1, st, search && k + 1 > hungrySlot, world)
        return if (frac < st.move) {
            val e = (frac / st.move).let { it * it * (3f - 2f * it) }
            Leg(a.first + (b.first - a.first) * e, a.second + (b.second - a.second) * e, b.first - a.first, true)
        } else Leg(b.first, b.second, b.first - a.first, false)
    }

    /** Where it would be at [t] just wandering (no food involved). */
    fun roam(p: StickerElement, t: Long, fed: Long, world: List<StickerElement>): Pair<Float, Float> =
        leg(p, t, fed, world).let { it.x to it.y }

    data class Pose(
        val x: Float, val y: Float,
        /** +1 facing right, -1 facing left. */
        val facing: Float,
        val action: Action,
        val stage: Stage,
        /** Time since its last meal. */
        val hunger: Long,
        val moving: Boolean,
    )

    private fun localHour(t: Long) = ((((t + java.util.TimeZone.getDefault().getOffset(t)) / HOUR) % 24) + 24) % 24

    /** Start of tonight's sleep (23:00 local) if [now] is during it, else 0. */
    private fun sleepStart(now: Long): Long {
        val h = localHour(now)
        if (h in 7..22) return 0L
        val off = java.util.TimeZone.getDefault().getOffset(now)
        val localMidnight = Math.floorDiv(now + off, DAY) * DAY - off
        return if (h >= 23) localMidnight + 23 * HOUR else localMidnight - HOUR
    }

    /**
     * Everything the renderer needs at [now]. With [roam] false (previews) it stays at its
     * sticker position, idling.
     */
    fun pose(p: StickerElement, now: Long, world: List<StickerElement>, roam: Boolean = true): Pose {
        val meals = if (roam) meals(p, world, now) else emptyList()
        val fed = meals.lastOrNull { it.end <= now }?.end ?: p.fedAt
        val hunger = if (fed <= 0L) 0L else now - fed
        val st = stage(p, now)
        if (!roam) {
            val a = when {
                hunger >= DEAD_AFTER -> Action.DEAD
                hunger >= WEAK_AFTER -> Action.WEAK
                hunger >= HUNGRY_AFTER -> if (searches(p)) Action.SNIFF else Action.SULK
                else -> Action.IDLE
            }
            return Pose(p.x, p.y, -1f, a, st, hunger, false)
        }
        fun at(t: Long) = leg(p, t, fed, world)
        fun still(t: Long, a: Action): Pose { val l = at(t); return Pose(l.x, l.y, if (l.dx >= 0) 1f else -1f, a, st, hunger, false) }

        if (hunger >= DEAD_AFTER) return still(fed + DEAD_AFTER, Action.DEAD)
        if (hunger >= WEAK_AFTER) return still(fed + WEAK_AFTER, Action.WEAK)

        // Running to / eating food
        meals.firstOrNull { now in it.start until it.end }?.let { m ->
            val face = if (m.toX >= m.fromX) 1f else -1f
            if (now < m.arrive) {
                val e = (now - m.start).toFloat() / (m.arrive - m.start).coerceAtLeast(1)
                val fast = st == Stage.YOUNG || st == Stage.ADULT
                return Pose(m.fromX + (m.toX - m.fromX) * e, m.fromY + (m.toY - m.fromY) * e, face, if (fast) Action.RUN else Action.WALK, st, hunger, true)
            }
            val bowlDir = if (m.food.x >= m.toX) 1f else -1f
            return Pose(m.toX, m.toY, bowlDir, Action.EAT, st, hunger, false)
        }
        // Wander back from the bowl instead of jumping
        meals.lastOrNull { it.end <= now && now - it.end < 4_000L }?.let { m ->
            val l = at(now); val e = (now - m.end) / 4_000f
            return Pose(m.toX + (l.x - m.toX) * e, m.toY + (l.y - m.toY) * e, if (l.x >= m.toX) 1f else -1f, Action.WALK, st, hunger, true)
        }
        if (p.playedAt > 0 && now - p.playedAt < 60_000L) {
            val l = at(now); return Pose(l.x, l.y, if (l.dx >= 0) 1f else -1f, Action.PLAY, st, hunger, l.moving)
        }
        if (hunger >= HUNGRY_AFTER && !searches(p)) return still(fed + HUNGRY_AFTER, Action.SULK)
        val night = sleepStart(now)
        if (night > 0 && hunger < HUNGRY_AFTER) return still(maxOf(night, fed), Action.SLEEP)
        val l = at(now)
        val face = if (l.dx >= 0) 1f else -1f
        val a = when {
            hunger >= HUNGRY_AFTER -> if (l.moving) Action.WALK else Action.SNIFF
            !l.moving -> Action.IDLE
            st == Stage.YOUNG -> Action.RUN
            else -> Action.WALK
        }
        return Pose(l.x, l.y, face, a, st, hunger, l.moving)
    }

    /** When [p] next crosses a hunger line (for reminders): hungry, weak, dead. */
    fun hungerMarks(p: StickerElement) = if (p.fedAt <= 0L) emptyList() else
        listOf(p.fedAt + HUNGRY_AFTER, p.fedAt + WEAK_AFTER, p.fedAt + DEAD_AFTER)
}
