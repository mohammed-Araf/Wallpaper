package com.alancruse.wallpaper.live

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.alancruse.wallpaper.canvas.StickerElement
import com.alancruse.wallpaper.pairing.PairingPrefs
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max

/**
 * Collects my steps / distance / speed and (when a canvas has a distance sticker)
 * my rounded position — only while one of my stickers actually needs it.
 *
 * Battery strategy
 * - Steps: hardware step counter, batched (the CPU is not woken per step).
 * - Location: fused provider at "balanced" power; the interval adapts:
 *     riding (> 12 km/h)          → 5 s, high accuracy
 *     nobody close / unknown      → 15–30 min
 *     getting closer              → 3 min → 1 min → 30 s as the gap shrinks
 *     close but both settled      → 10 min (same campus / office: no constant polling)
 *   "Settled" = I haven't left a 250 m circle for 15 minutes.
 * - Writes are throttled; other users only ever receive numbers / a rounded position.
 */
object LiveTracker {
    private const val TAG = "LiveTracker"
    private val handler = Handler(Looper.getMainLooper())
    private var refs = 0
    private lateinit var app: Context
    private val db get() = FirebaseDatabase.getInstance().reference
    private val uid get() = FirebaseAuth.getInstance().currentUser?.uid
    private val name get() = FirebaseAuth.getInstance().currentUser?.displayName?.split(' ')?.firstOrNull() ?: ""

    // ── What my stickers need, per room ──
    private val roomListeners = HashMap<String, Pair<DatabaseReference, ValueEventListener>>()
    private val roomNeeds = HashMap<String, Triple<Boolean, Boolean, Boolean>>() // dist, steps, location
    private val distRooms get() = roomNeeds.filterValues { it.first }.keys
    private val needSteps get() = roomNeeds.values.any { it.second }
    private val needLocation get() = roomNeeds.values.any { it.first || it.third }

    @Synchronized
    fun acquire(ctx: Context) {
        if (refs++ > 0) return
        app = ctx.applicationContext
        loadDay()
        watchRooms()
        LiveBoard.observe(onBoard)
    }

    @Synchronized
    fun release() {
        if (refs == 0 || --refs > 0) return
        LiveBoard.unobserve(onBoard)
        roomListeners.values.forEach { (r, l) -> r.removeEventListener(l) }
        roomListeners.clear(); roomNeeds.clear()
        distWatched.forEach { LiveBoard.unwatchPresence(it) }; distWatched.clear()
        stopSteps(); stopLocation()
        flushStats(force = true)
    }

    /** Call after permissions change or rooms are joined/left. */
    fun refresh() { if (refs > 0) handler.post { watchRooms(); apply() } }

    private fun myRooms(): Set<String> {
        val p = PairingPrefs(app)
        return buildSet { p.getCoupleRoomId()?.let { add(it) }; addAll(p.getGroupRoomIds()) }
    }

    private fun watchRooms() {
        val rooms = myRooms()
        (roomListeners.keys - rooms).forEach { id ->
            roomListeners.remove(id)?.let { (r, l) -> r.removeEventListener(l) }; roomNeeds.remove(id)
        }
        for (id in rooms - roomListeners.keys) {
            val ref = db.child("canvas_sessions").child(id).child("stickers")
            val l = object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) {
                    val me = uid
                    val st = s.children.mapNotNull { it.getValue(StickerElement::class.java) }
                    roomNeeds[id] = Triple(
                        st.any { it.kind == "w:dist" },
                        st.any { it.owner == me && StickerKinds.needsSteps(it.kind) },
                        st.any { it.owner == me && StickerKinds.needsLocation(it.kind) },
                    )
                    apply()
                }
                override fun onCancelled(e: DatabaseError) {}
            }
            ref.addValueEventListener(l); roomListeners[id] = ref to l
        }
    }

    private val distWatched = HashSet<String>()

    private fun apply() {
        if (needSteps && has(Manifest.permission.ACTIVITY_RECOGNITION)) startSteps() else stopSteps()
        // Watch partners' positions in rooms with a distance sticker; drop mine where none is left.
        val want = distRooms
        (want - distWatched).forEach { LiveBoard.watchPresence(it) }
        (distWatched - want).forEach { r -> LiveBoard.unwatchPresence(r); uid?.let { db.child("canvas_sessions").child(r).child("presence").child(it).removeValue() } }
        distWatched.clear(); distWatched.addAll(want)
        if (needLocation && hasLocation()) reconfigureLocation() else stopLocation()
    }

    private fun has(p: String) = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && p == Manifest.permission.ACTIVITY_RECOGNITION ||
        ContextCompat.checkSelfPermission(app, p) == PackageManager.PERMISSION_GRANTED
    private fun hasLocation() = has(Manifest.permission.ACCESS_FINE_LOCATION) || has(Manifest.permission.ACCESS_COARSE_LOCATION)

    // ═════════ Steps ═════════

    private var stepsToday = 0
    private var vehicleKm = 0f
    private var lastCounter = -1f
    private var day = ""
    private var speed = 0f
    private var speedAt = 0L
    private var stepListening = false
    private val prefs by lazy { app.getSharedPreferences("live_stats", Context.MODE_PRIVATE) }

    private fun today() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun loadDay() {
        day = prefs.getString("day", today()) ?: today()
        stepsToday = prefs.getInt("steps", 0); vehicleKm = prefs.getFloat("vkm", 0f)
        lastCounter = prefs.getFloat("counter", -1f)
        rollDay()
    }

    private fun rollDay() {
        val t = today()
        if (t != day) { day = t; stepsToday = 0; vehicleKm = 0f }
    }

    private fun saveDay() = prefs.edit().putString("day", day).putInt("steps", stepsToday).putFloat("vkm", vehicleKm).putFloat("counter", lastCounter).apply()

    private val stepListener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            val v = e.values[0]
            rollDay()
            val delta = if (lastCounter < 0 || v < lastCounter) 0f else v - lastCounter // reboot resets the counter
            lastCounter = v
            if (delta > 0) { stepsToday += delta.toInt(); saveDay(); flushStats() }
        }
        override fun onAccuracyChanged(s: Sensor?, a: Int) {}
    }

    private fun startSteps() {
        if (stepListening) return
        val sm = app.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val s = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return
        // Batch up to a minute of steps in the sensor hub.
        stepListening = sm.registerListener(stepListener, s, SensorManager.SENSOR_DELAY_NORMAL, 60_000_000)
    }

    private fun stopSteps() {
        if (!stepListening) return
        (app.getSystemService(Context.SENSOR_SERVICE) as SensorManager).unregisterListener(stepListener)
        stepListening = false
    }

    private val strideKm = 0.00075f
    private fun kmToday() = stepsToday * strideKm + vehicleKm

    // ═════════ Location ═════════

    private var fused: FusedLocationProviderClient? = null
    private var currentInterval = 0L
    private var currentPriority = 0
    private var last: Location? = null
    private var anchor: Location? = null
    private var anchorSince = 0L
    private var lastFast = 0L
    private var prevNearestKm: Double? = null
    private var approaching = false

    private val locCallback = object : LocationCallback() {
        override fun onLocationResult(r: LocationResult) { r.lastLocation?.let { onLocation(it) } }
    }

    private fun onLocation(loc: Location) {
        rollDay()
        val now = System.currentTimeMillis()
        val prev = last
        if (loc.hasSpeed()) { speed = loc.speed * 3.6f; speedAt = now }
        else if (prev != null) {
            val dt = (loc.time - prev.time) / 1000f
            if (dt in 1f..120f) { speed = prev.distanceTo(loc) / dt * 3.6f; speedAt = now }
        }
        if (speed > 12f) lastFast = now
        // Distance travelled in a vehicle (walking is already counted by steps).
        if (prev != null && speed > 10f && loc.accuracy < 50f) vehicleKm += prev.distanceTo(loc) / 1000f
        last = loc

        val a = anchor
        if (a == null || a.distanceTo(loc) > 250f) { anchor = loc; anchorSince = now }

        publishPresence(loc)
        saveDay(); flushStats(force = speed > 12f)
        reconfigureLocation()
    }

    private fun publishPresence(loc: Location) {
        val me = uid ?: return
        if (distRooms.isEmpty()) return
        // ~110 m precision; nothing finer ever leaves the phone.
        val p = Presence(Math.round(loc.latitude * 1000) / 1000.0, Math.round(loc.longitude * 1000) / 1000.0, System.currentTimeMillis(), name)
        distRooms.forEach { db.child("canvas_sessions").child(it).child("presence").child(me).setValue(p) }
    }

    private val onBoard: () -> Unit = { if (needLocation) handler.removeCallbacks(reconfig).also { handler.postDelayed(reconfig, 1500) } }
    private val reconfig = Runnable { if (needLocation && hasLocation()) reconfigureLocation() }

    /** Nearest other person (km) across my distance canvases, from the rounded positions. */
    private fun nearestKm(): Double? {
        val me = uid ?: return null
        val mine = last?.let { Presence(it.latitude, it.longitude) } ?: return null
        return distRooms.flatMap { r -> LiveBoard.presence(r).filterKeys { it != me }.values }
            .filter { System.currentTimeMillis() - it.at < 6 * 3600_000L }
            .minOfOrNull { haversineKm(mine, it) }
    }

    private fun policy(): Pair<Int, Long> {
        val now = System.currentTimeMillis()
        if (now - lastFast < 120_000) return Priority.PRIORITY_HIGH_ACCURACY to 5_000L
        val d = nearestKm()
        val p = prevNearestKm
        if (d != null && p != null) approaching = p - d > max(0.1, p * 0.15) else if (d == null) approaching = false
        if (d != null && (p == null || kotlin.math.abs(p - d) > 0.05)) prevNearestKm = d
        val settled = now - anchorSince > 15 * 60_000L
        val bal = Priority.PRIORITY_BALANCED_POWER_ACCURACY
        return when {
            d == null -> bal to 15 * 60_000L
            d < 0.3 -> if (settled) bal to 10 * 60_000L else Priority.PRIORITY_HIGH_ACCURACY to 60_000L
            d < 1.0 -> if (approaching) Priority.PRIORITY_HIGH_ACCURACY to 30_000L else if (settled) bal to 10 * 60_000L else bal to 2 * 60_000L
            d < 5.0 -> bal to if (approaching) 60_000L else 5 * 60_000L
            d < 20.0 -> bal to if (approaching) 3 * 60_000L else 15 * 60_000L
            else -> Priority.PRIORITY_LOW_POWER to 30 * 60_000L
        }
    }

    @SuppressLint("MissingPermission")
    private fun reconfigureLocation() {
        val (prio, interval) = policy()
        if (fused != null && prio == currentPriority && interval == currentInterval) return
        val fresh = fused == null
        val client = fused ?: LocationServices.getFusedLocationProviderClient(app).also { fused = it }
        // Start from the last known fix instead of waiting a whole interval.
        if (fresh) try { client.lastLocation.addOnSuccessListener { it?.let(::onLocation) } } catch (_: SecurityException) {}
        currentPriority = prio; currentInterval = interval
        try {
            client.removeLocationUpdates(locCallback)
            val req = LocationRequest.Builder(prio, interval)
                .setMinUpdateIntervalMillis(interval / 2)
                .setMaxUpdateDelayMillis(if (interval >= 60_000L) interval * 2 else 0L) // batch when far
                .build()
            client.requestLocationUpdates(req, locCallback, Looper.getMainLooper())
            Log.d(TAG, "location every ${interval / 1000}s prio=$prio")
        } catch (e: SecurityException) { Log.w(TAG, "no location permission") }
    }

    private fun stopLocation() {
        fused?.removeLocationUpdates(locCallback)
        fused = null; currentInterval = 0L; currentPriority = 0
    }

    // ═════════ Publishing ═════════

    private var lastWrite = 0L
    private var lastWrittenSteps = -1

    private fun flushStats(force: Boolean = false) {
        val me = uid ?: return
        val now = System.currentTimeMillis()
        val stats = LiveStats(stepsToday, kmToday(), if (now - speedAt < 5 * 60_000L) speed else 0f, speedAt, day, name, now)
        LiveBoard.putMyStats(stats)
        val due = force || now - lastWrite > 60_000L || (lastWrittenSteps >= 0 && stepsToday - lastWrittenSteps >= 200)
        if (!due) return
        lastWrite = now; lastWrittenSteps = stepsToday
        db.child("users").child(me).child("stats").setValue(stats)
    }
}
