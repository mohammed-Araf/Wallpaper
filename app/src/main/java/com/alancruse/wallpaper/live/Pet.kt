package com.alancruse.wallpaper.live

import com.alancruse.wallpaper.canvas.StickerElement
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.floor
import kotlin.math.hypot

/**
 * The shared pet. It roams the canvas, grows up, runs to food you drop and needs petting.
 *
 * It has three bars, 0..1: food ([State.full]), happiness ([State.joy]) and health
 * ([State.hp]). Food and happiness drain over time. Health drops while it's starving,
 * miserable or overfed, and slowly recovers while it's fed and happy. A pet with no
 * happiness left won't eat. When health runs out it dies.
 *
 * Nothing is streamed: the sticker stores a snapshot of the bars ([StickerElement.vitalsAt]),
 * and every phone works out the rest from the time and the food on the canvas, so everyone
 * sees the same pet doing the same thing.
 */
object Pet {
    val SPECIES = arrayOf("Cat", "Dog", "Bunny")
    val COLORS = intArrayOf(0xFFF6B26B.toInt(), 0xFFF3F0EA.toInt(), 0xFF8D8D99.toInt(), 0xFF7A5236.toInt(), 0xFFFFC2D6.toInt())

    const val HOUR = 3_600_000L
    const val DAY = 24 * HOUR

    // ── Rates (per hour) and thresholds ──
    /** Food bar empties in 30 h, happiness in 48 h, overfeeding wears off in 4 h. */
    private const val FULL_DRAIN = 1f / 30f
    private const val JOY_DRAIN = 1f / 48f
    private const val BLOAT_DRAIN = 1f / 4f
    /** Health lost per hour while starving / miserable, and per unit of bloat. */
    private const val STARVE_HURT = 1f / 48f
    private const val GLOOM_HURT = 1f / 72f
    private const val BLOAT_HURT = 0.06f
    /** Health regained per hour while fed and happy. */
    private const val HEAL = 1f / 24f
    /** One bowl fills this much of the food bar; anything over the top is overfeeding. */
    const val MEAL = 0.5f
    /** One petting adds this much happiness. */
    const val CHEER = 0.35f
    const val HUNGRY = 0.4f
    const val SAD = 0.3f
    const val WEAK = 0.3f
    const val STUFFED = 0.5f
    /** Below this it won't eat. */
    const val NO_MOOD = 0.02f
    /** How long a petting lasts (the hand rubs its head). */
    const val PET_FOR = 6_000L
    private const val STEP = 10 * 60_000L

    /**
     * Life stages. [slot] seconds per "go somewhere, then hang around" step, [move] the share
     * of it spent moving, [rx]/[ry] how far from home it wanders (canvas px), [chase] px/s
     * when it heads for food, [eat] seconds to finish a bowl.
     */
    enum class Stage(val fromDay: Int, val scale: Float, val slot: Float, val move: Float, val rx: Float, val ry: Float, val chase: Float, val eat: Float) {
        BABY(0, 0.6f, 9f, 0.45f, 170f, 260f, 110f, 30f),
        YOUNG(5, 0.8f, 2.6f, 0.85f, 420f, 760f, 480f, 20f),
        ADULT(15, 1f, 7f, 0.55f, 360f, 640f, 260f, 22f),
        OLD(30, 1f, 14f, 0.45f, 200f, 320f, 90f, 32f),
    }

    enum class Action { WALK, RUN, IDLE, SNIFF, SULK, EAT, SLEEP, PETTED, STUFFED, WEAK, DEAD }

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

    // ── Streak (stored on the sticker) ──

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

    /** A freshly adopted pet: a bit peckish, happy, healthy. */
    fun adopt(p: StickerElement, who: String, now: Long) =
        fed(p.copy(bornAt = now, full = 0.55f, joy = 0.85f, hp = 1f, bloat = 0f, vitalsAt = now), who, now)

    // ── The bars over time ──

    /** The bars at [t], plus when each "bad" state started (0 = not in it). */
    class State internal constructor(
        var t: Long, var full: Float, var joy: Float, var hp: Float, var bloat: Float,
        /** End of its last meal. */
        var fedAt: Long,
    ) {
        var hungryAt = 0L; var sadAt = 0L; var weakAt = 0L; var stuffedAt = 0L; var deadAt = 0L
        val dead get() = deadAt > 0L
        val hungry get() = full < HUNGRY
        val sad get() = joy < SAD
        val weak get() = hp < WEAK
        val stuffed get() = bloat > STUFFED

        internal fun marks() {
            hungryAt = if (hungry) hungryAt.takeIf { it > 0 } ?: t else 0L
            sadAt = if (sad) sadAt.takeIf { it > 0 } ?: t else 0L
            weakAt = if (weak) weakAt.takeIf { it > 0 } ?: t else 0L
            stuffedAt = if (stuffed) stuffedAt.takeIf { it > 0 } ?: t else 0L
        }

        internal fun run(to: Long) {
            while (t < to && !dead) {
                val dt = minOf(to - t, STEP)
                val h = dt / HOUR.toFloat()
                full = maxOf(0f, full - h * FULL_DRAIN)
                joy = maxOf(0f, joy - h * JOY_DRAIN)
                val b0 = bloat
                bloat = maxOf(0f, bloat - h * BLOAT_DRAIN)
                var d = 0f
                if (full <= 0f) d -= h * STARVE_HURT
                if (joy <= 0f) d -= h * GLOOM_HURT
                if (b0 > 0f) d -= h * BLOAT_HURT * (b0 + bloat) / 2f
                if (d == 0f && full > HUNGRY && joy > SAD) d = h * HEAL
                hp = minOf(1f, hp + d)
                t += dt
                if (hp <= 0f) { hp = 0f; deadAt = t }
                marks()
            }
        }

        internal fun eat(at: Long) {
            val f = full + MEAL
            if (f > 1f) { bloat += f - 1f; full = 1f } else full = f
            joy = minOf(1f, joy + 0.05f)
            fedAt = at
            marks()
        }

        fun copy() = State(t, full, joy, hp, bloat, fedAt).also {
            it.hungryAt = hungryAt; it.sadAt = sadAt; it.weakAt = weakAt; it.stuffedAt = stuffedAt; it.deadAt = deadAt
        }
    }

    private fun snapshot(p: StickerElement): State {
        val at = when { p.vitalsAt > 0 -> p.vitalsAt; p.fedAt > 0 -> p.fedAt; else -> bornAt(p) }
        return State(at, p.full.coerceIn(0f, 1f), p.joy.coerceIn(0f, 1f), p.hp.coerceIn(0f, 1f), maxOf(0f, p.bloat), p.fedAt).also { it.marks() }
    }

    // ── Meals: it heads for each bowl in turn and eats it all ──

    data class Meal(
        val food: StickerElement, val start: Long, val arrive: Long, val end: Long,
        val fromX: Float, val fromY: Float, val toX: Float, val toY: Float,
        /** The bars right after eating (null while it's still on its way or eating). */
        val after: State?,
    )

    /** Everything about the pet at [now]: the bars, and the meals so far / under way. */
    class Life(val now: State, val meals: List<Meal>, val refusing: Boolean)

    private fun unit(p: StickerElement, st: Stage) = p.size * p.scale / 100f * st.scale

    /** Where the pet stands to eat from [f]: beside the bowl, facing it. */
    private fun spot(p: StickerElement, f: StickerElement, fromX: Float, st: Stage): Pair<Float, Float> {
        val dir = if (fromX <= f.x) -1f else 1f
        val fu = f.size * f.scale / 100f
        return f.x + dir * (30f * fu + 30f * unit(p, st)) to f.y + 6f * fu
    }

    /** Last result: the renderer asks several times per frame (pet, bowls, hit testing). */
    @Volatile private var cache: Pair<Any, Life>? = null

    fun life(p: StickerElement, world: List<StickerElement>, now: Long): Life {
        val key = Triple(p, world, now)
        cache?.let { if (it.first == key) return it.second }
        val s = snapshot(p)
        val foods = world.filter { StickerKinds.isFood(it.kind) }.sortedBy { it.timestamp }
        val out = ArrayList<Meal>()
        var prevEnd = p.fedAt
        var refusing = false
        for (f in foods) {
            var start = maxOf(f.timestamp, prevEnd, p.cheerAt)
            // Let a petting finish before it goes for the bowl.
            if (p.playedAt > 0 && start in p.playedAt until p.playedAt + PET_FOR) start = p.playedAt + PET_FOR
            if (start > now) break
            if (start > s.t) s.run(start)
            if (s.dead && s.deadAt <= start) break
            if (s.joy <= NO_MOOD) { refusing = true; break } // too miserable to eat
            val st = stage(p, start)
            val from = rest(p, start, s, world)
            val (tx, ty) = spot(p, f, from.x, st)
            val arrive = start + (hypot(tx - from.x, ty - from.y) / st.chase * 1000f).toLong()
            val end = arrive + (st.eat * 1000f).toLong()
            if (end > now) { out.add(Meal(f, start, arrive, end, from.x, from.y, tx, ty, null)); break }
            s.run(end)
            if (s.dead) break
            s.eat(end)
            out.add(Meal(f, start, arrive, end, from.x, from.y, tx, ty, s.copy()))
            prevEnd = end
        }
        s.run(now)
        return Life(s, out, refusing).also { cache = key to it }
    }

    fun isDead(p: StickerElement, world: List<StickerElement>, now: Long) = life(p, world, now).now.dead

    /**
     * How much of [food] is left: 1 full, 0 licked clean (the empty bowl sits for a moment),
     * then fading out (negative = gone).
     */
    fun foodLeft(food: StickerElement, world: List<StickerElement>, now: Long): Float {
        val pet = world.firstOrNull { StickerKinds.isPet(it.kind) } ?: return 1f
        val m = life(pet, world, now).meals.firstOrNull { it.food === food || it.food == food } ?: return 1f
        return when {
            now < m.arrive -> 1f
            now < m.end -> 1f - (now - m.arrive).toFloat() / (m.end - m.arrive)
            now < m.end + EMPTY_FOR -> 0f
            else -> -((now - m.end - EMPTY_FOR) / 800f)
        }
    }
    private const val EMPTY_FOR = 2_500L
    /** Bowls are cleared from the canvas this long after they were finished. */
    const val CLEAR_AFTER = 4_000L

    /** The pet with every meal finished by [upTo] written in, and the bowls to clear. */
    fun settle(p: StickerElement, world: List<StickerElement>, now: Long, upTo: Long = now - CLEAR_AFTER): Pair<StickerElement, List<StickerElement>>? {
        val done = life(p, world, now).meals.filter { it.after != null && it.end <= upTo }
        val last = done.lastOrNull() ?: return null
        val a = last.after!!
        var q = p
        for (m in done) q = fed(q, m.food.label, m.end)
        q = q.copy(full = a.full, joy = a.joy, hp = a.hp, bloat = a.bloat, vitalsAt = a.t)
        return q to done.map { it.food }
    }

    /**
     * Petting by [who] at [now], while it stands at ([x], [y]). Also writes down finished
     * meals (returned bowls should be cleared). Null if it's dead or already being petted.
     */
    fun pet(p: StickerElement, world: List<StickerElement>, who: String, now: Long, x: Float, y: Float): Pair<StickerElement, List<StickerElement>>? {
        if (p.playedAt > 0 && now - p.playedAt in 0 until PET_FOR) return null
        val life = life(p, world, now)
        val s = life.now
        if (s.dead) return null
        val (base, eaten) = settle(p, world, now, upTo = now) ?: (p to emptyList())
        return base.copy(
            full = s.full, joy = minOf(1f, s.joy + CHEER), hp = s.hp, bloat = s.bloat, vitalsAt = now,
            playedAt = now, petX = x, petY = y, careBy = who.ifBlank { p.careBy },
            cheerAt = if (s.joy <= NO_MOOD) now else p.cheerAt,
        ) to eaten
    }

    /** 0 fine · 1 hungry or sad · 2 weak · 3 dead (for reminders). */
    fun level(p: StickerElement, world: List<StickerElement>, now: Long): Int {
        val s = life(p, world, now).now
        return when { s.dead -> 3; s.weak -> 2; s.hungry || s.sad -> 1; else -> 0 }
    }

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

    /** Its wander path at [t]. Once hungry (since [hungryAt]), dogs/bunnies search from the next step on. */
    private fun leg(p: StickerElement, t: Long, hungryAt: Long, world: List<StickerElement>): Leg {
        val st = stage(p, t)
        val born = bornAt(p)
        val slotMs = (st.slot * 1000f).toLong()
        val f = (t - born).toDouble() / slotMs
        val k = floor(f).toLong()
        val frac = (f - k).toFloat()
        val search = searches(p) && hungryAt > 0
        val hungrySlot = floor((hungryAt - born).toDouble() / slotMs).toLong()
        val a = waypoint(p, k, st, search && k > hungrySlot, world)
        val b = waypoint(p, k + 1, st, search && k + 1 > hungrySlot, world)
        return if (frac < st.move) {
            val e = (frac / st.move).let { it * it * (3f - 2f * it) }
            Leg(a.first + (b.first - a.first) * e, a.second + (b.second - a.second) * e, b.first - a.first, true)
        } else Leg(b.first, b.second, b.first - a.first, false)
    }

    data class Pose(
        val x: Float, val y: Float,
        /** +1 facing right, -1 facing left. */
        val facing: Float,
        val action: Action,
        val stage: Stage,
        val full: Float = 1f,
        val joy: Float = 1f,
        val hp: Float = 1f,
        val moving: Boolean = false,
        /** Food is waiting but it's too sad to eat. */
        val refusing: Boolean = false,
    ) {
        val hungry get() = full < HUNGRY
        val sad get() = joy < SAD
    }

    private fun localHour(t: Long) = ((((t + java.util.TimeZone.getDefault().getOffset(t)) / HOUR) % 24) + 24) % 24

    /** Start of tonight's sleep (23:00 local) if [now] is during it, else 0. */
    private fun sleepStart(now: Long): Long {
        val h = localHour(now)
        if (h in 7..22) return 0L
        val off = java.util.TimeZone.getDefault().getOffset(now)
        val localMidnight = Math.floorDiv(now + off, DAY) * DAY - off
        return if (h >= 23) localMidnight + 23 * HOUR else localMidnight - HOUR
    }

    /** What it does at [t] when it isn't busy with food, given its bars [s]. */
    private fun rest(p: StickerElement, t: Long, s: State, world: List<StickerElement>, refusing: Boolean = false): Pose {
        val st = stage(p, t)
        fun mk(x: Float, y: Float, face: Float, a: Action, moving: Boolean = false) =
            Pose(x, y, face, a, st, s.full, s.joy, s.hp, moving, refusing)
        fun still(at: Long, a: Action): Pose { val l = leg(p, at, s.hungryAt, world); return mk(l.x, l.y, if (l.dx >= 0) 1f else -1f, a) }

        if (s.dead && s.deadAt <= t) return still(if (s.weakAt in 1..s.deadAt) s.weakAt else s.deadAt, Action.DEAD)
        val since = t - p.playedAt
        if (p.playedAt > 0 && since in 0 until PET_FOR) return mk(p.petX, p.petY, -1f, Action.PETTED)
        val night = sleepStart(t)
        val base = when {
            s.weak -> still(s.weakAt, Action.WEAK)
            s.stuffed -> still(s.stuffedAt, Action.STUFFED)
            s.sad -> still(s.sadAt, Action.SULK)
            s.hungry && !searches(p) -> still(s.hungryAt, Action.SULK)
            night > 0 && !s.hungry -> still(maxOf(night, s.fedAt), Action.SLEEP)
            else -> {
                val l = leg(p, t, s.hungryAt, world)
                val a = when {
                    s.hungry -> if (l.moving) Action.WALK else Action.SNIFF
                    !l.moving -> Action.IDLE
                    st == Stage.YOUNG -> Action.RUN
                    else -> Action.WALK
                }
                mk(l.x, l.y, if (l.dx >= 0) 1f else -1f, a, l.moving)
            }
        }
        // Walk off from where it was petted instead of jumping.
        if (p.playedAt > 0 && since in PET_FOR until PET_FOR + 2_000L) {
            val e = (since - PET_FOR) / 2_000f
            return base.copy(x = p.petX + (base.x - p.petX) * e, y = p.petY + (base.y - p.petY) * e,
                facing = if (base.x >= p.petX) 1f else -1f, action = if (base.action == Action.DEAD) Action.DEAD else Action.WALK, moving = true)
        }
        return base
    }

    /**
     * Everything the renderer needs at [now]. With [roam] false (previews) it stays at its
     * sticker position.
     */
    fun pose(p: StickerElement, now: Long, world: List<StickerElement>, roam: Boolean = true): Pose {
        val life = life(p, if (roam) world else emptyList(), now)
        val s = life.now
        val st = stage(p, now)
        if (!roam) {
            val a = when {
                s.dead -> Action.DEAD
                s.weak -> Action.WEAK
                s.sad -> Action.SULK
                s.hungry -> if (searches(p)) Action.SNIFF else Action.SULK
                else -> Action.IDLE
            }
            return Pose(p.x, p.y, -1f, a, st, s.full, s.joy, s.hp)
        }
        if (!(s.dead && s.deadAt <= now)) {
            // Heading to / eating food
            life.meals.firstOrNull { it.after == null && now >= it.start }?.let { m ->
                val face = if (m.toX >= m.fromX) 1f else -1f
                if (now < m.arrive) {
                    val e = (now - m.start).toFloat() / (m.arrive - m.start).coerceAtLeast(1)
                    val fast = (st == Stage.YOUNG || st == Stage.ADULT) && !s.weak
                    return Pose(m.fromX + (m.toX - m.fromX) * e, m.fromY + (m.toY - m.fromY) * e, face, if (fast) Action.RUN else Action.WALK, st, s.full, s.joy, s.hp, true)
                }
                return Pose(m.toX, m.toY, if (m.food.x >= m.toX) 1f else -1f, Action.EAT, st, s.full, s.joy, s.hp)
            }
            // Wander off from the bowl instead of jumping
            life.meals.lastOrNull { it.after != null && now - it.end in 0 until 4_000L }?.let { m ->
                val r = rest(p, now, s, world, life.refusing)
                if (r.action == Action.PETTED) return r
                val e = (now - m.end) / 4_000f
                return r.copy(x = m.toX + (r.x - m.toX) * e, y = m.toY + (r.y - m.toY) * e, facing = if (r.x >= m.toX) 1f else -1f, action = Action.WALK, moving = true)
            }
        }
        return rest(p, now, s, world, life.refusing)
    }
}
