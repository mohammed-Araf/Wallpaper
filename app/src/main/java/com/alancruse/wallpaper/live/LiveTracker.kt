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
    private val nudgeListeners = HashMap<String, Pair<DatabaseReference, ValueEventListener>>()
    private data class Needs(val dist: Boolean, val steps: Boolean, val location: Boolean, val status: Boolean)
    private val roomNeeds = HashMap<String, Needs>()
    /** Other people's Me stickers per room (uid → name plate), for the battery buddy. */
    private val roomAvatars = HashMap<String, Map<String, String>>()
    private val distRooms get() = roomNeeds.filterValues { it.dist }.keys
    private val needSteps get() = roomNeeds.values.any { it.steps }
    private val needLocation get() = roomNeeds.values.any { it.dist || it.location }
    private val needStatus get() = roomNeeds.values.any { it.status }

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
        roomListeners.clear(); roomNeeds.clear(); roomAvatars.clear()
        nudgeListeners.values.forEach { (r, l) -> r.removeEventListener(l) }; nudgeListeners.clear()
        distWatched.forEach { LiveBoard.unwatchPresence(it) }; distWatched.clear()
        stopSteps(); stopLocation(); stopStatus()
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
            roomListeners.remove(id)?.let { (r, l) -> r.removeEventListener(l) }; roomNeeds.remove(id); roomAvatars.remove(id)
            nudgeListeners.remove(id)?.let { (r, l) -> r.removeEventListener(l) }
        }
        uid?.let { me ->
            for (id in rooms - nudgeListeners.keys) {
                val ref = db.child("canvas_sessions").child(id).child("nudges").child(me)
                val l = object : ValueEventListener {
                    override fun onDataChange(s: DataSnapshot) { onNudge(s) }
                    override fun onCancelled(e: DatabaseError) {}
                }
                ref.addValueEventListener(l); nudgeListeners[id] = ref to l
            }
        }
        for (id in rooms - roomListeners.keys) {
            val ref = db.child("canvas_sessions").child(id).child("stickers")
            val l = object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) {
                    val me = uid
                    val st = s.children.mapNotNull { it.getValue(StickerElement::class.java) }
                    roomNeeds[id] = Needs(
                        st.any { it.kind == "w:dist" },
                        st.any { it.owner == me && StickerKinds.needsSteps(it.kind) },
                        st.any { it.owner == me && StickerKinds.needsLocation(it.kind) },
                        st.any { it.owner == me && StickerKinds.needsStatus(it.kind) },
                    )
                    roomAvatars[id] = st.filter { StickerKinds.needsStatus(it.kind) && it.owner.isNotEmpty() && it.owner != me }
                        .associate { it.owner to it.label }
                    apply()
                    checkBatteries()
                }
                override fun onCancelled(e: DatabaseError) {}
            }
            ref.addValueEventListener(l); roomListeners[id] = ref to l
        }
    }

    private val distWatched = HashSet<String>()

    private fun apply() {
        val ghost = ghosting()
        handler.removeCallbacks(ghostEnd)
        if (ghost) {
            val left = ghostUntil(app) - System.currentTimeMillis()
            // Re-checked at least every 6 h; "until I turn it off" never expires on its own.
            if (ghostUntil(app) != Long.MAX_VALUE) handler.postDelayed(ghostEnd, left.coerceIn(1_000L, 6 * 3_600_000L))
        }
        if (needSteps && has(Manifest.permission.ACTIVITY_RECOGNITION)) startSteps() else stopSteps()
        // Watch partners' positions in rooms with a distance sticker; drop mine where none is left.
        val want = distRooms
        (want - distWatched).forEach { LiveBoard.watchPresence(it) }
        (distWatched - want).forEach { r -> LiveBoard.unwatchPresence(r); uid?.let { db.child("canvas_sessions").child(r).child("presence").child(it).removeValue() } }
        distWatched.clear(); distWatched.addAll(want)
        // Ghost mode: stop the location and take my position off every canvas.
        if (ghost && !ghostCleared) uid?.let { me -> want.forEach { db.child("canvas_sessions").child(it).child("presence").child(me).removeValue() } }
        ghostCleared = ghost
        if (!ghost && needLocation && hasLocation()) reconfigureLocation() else stopLocation()
        if (!ghost && needStatus) startStatus() else stopStatus()
        if (ghost != lastGhost) { lastGhost = ghost; flushStats(force = true) }
    }

    // ═════════ Nudges ═════════
    //
    // Tapping someone's Me sticker writes canvas_sessions/{room}/nudges/{their uid}.
    // Their phone (app or live wallpaper running) shows a notification and makes my
    // avatar wave on their screen. No server needed; delivery waits if their phone dozes.

    private const val NUDGE_CHANNEL = "nudges"
    private val lastNudgeSent = HashMap<String, Long>()

    /** Nudge [toUid] in [roomId]. Returns false if it was too soon after the last one. */
    fun nudge(roomId: String, toUid: String): Boolean {
        val me = uid ?: return false
        val now = System.currentTimeMillis()
        if (now - (lastNudgeSent[toUid] ?: 0L) < 20_000L) return false
        lastNudgeSent[toUid] = now
        db.child("canvas_sessions").child(roomId).child("nudges").child(toUid)
            .setValue(mapOf("from" to me, "name" to name, "at" to ServerValue.TIMESTAMP))
        return true
    }

    private fun onNudge(s: DataSnapshot) {
        val from = s.child("from").getValue(String::class.java) ?: return
        val at = s.child("at").getValue(Long::class.java) ?: return
        val who = s.child("name").getValue(String::class.java)?.ifBlank { null } ?: "Someone"
        if (from == uid) return
        val seen = prefs.getLong("nudge_seen", 0L)
        if (at <= seen) return
        prefs.edit().putLong("nudge_seen", at).apply()
        // Old ones (phone was off for a while) still mark as seen, but don't ping.
        if (System.currentTimeMillis() - at > 15 * 60_000L) return
        LiveBoard.putNudge(from, System.currentTimeMillis())
        notifyNudge(who)
    }

    private fun notifyNudge(who: String) = notify(
        NUDGE_CHANNEL, "Nudges", "When someone taps your avatar to nudge you",
        "nudge:$who".hashCode(), "👋 $who nudged you", "Tap their avatar to nudge back",
    )

    @SuppressLint("MissingPermission")
    private fun notify(channel: String, channelName: String, desc: String, id: Int, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 && !has(Manifest.permission.POST_NOTIFICATIONS)) return
        val nm = androidx.core.app.NotificationManagerCompat.from(app)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(android.app.NotificationChannel(channel, channelName, android.app.NotificationManager.IMPORTANCE_HIGH)
                .apply { description = desc })
        }
        val open = app.packageManager.getLaunchIntentForPackage(app.packageName)
        val pi = open?.let { android.app.PendingIntent.getActivity(app, 8, it, android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT) }
        val n = androidx.core.app.NotificationCompat.Builder(app, channel)
            .setSmallIcon(com.alancruse.wallpaper.R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_SOCIAL)
            .setAutoCancel(true)
            .apply { if (pi != null) setContentIntent(pi) }
            .build()
        try { nm.notify(id, n) } catch (_: SecurityException) {}
    }

    // ═════════ Battery buddy ═════════
    //
    // When someone whose Me sticker is on one of my canvases drops to 15% or less and isn't
    // charging, ping me once. It re-arms after they charge or get back above 25%.
    // Uses the battery they already share with their avatar (nothing while they're a ghost).

    private const val BATTERY_CHANNEL = "battery"

    private fun checkBatteries() {
        val me = uid ?: return
        val now = System.currentTimeMillis()
        val people = HashMap<String, String>()
        roomAvatars.values.forEach { m -> m.forEach { (u, label) -> if (u != me) people.putIfAbsent(u, label) } }
        for ((u, label) in people) {
            val st = LiveBoard.stats(u) ?: continue
            if (st.battery < 0 || now - st.updatedAt > 30 * 60_000L) continue
            val key = "bat_warned_$u"
            val warned = prefs.getBoolean(key, false)
            when {
                st.charging || st.battery >= 25 -> if (warned) prefs.edit().putBoolean(key, false).apply()
                st.battery <= 15 && !warned -> {
                    prefs.edit().putBoolean(key, true).apply()
                    val who = label.ifBlank { st.name }.ifBlank { "Your partner" }
                    notify(
                        BATTERY_CHANNEL, "Battery buddy", "When someone on your canvas is running low on battery",
                        "battery:$u".hashCode(), "🔋 $who's phone is at ${st.battery}%",
                        "Maybe remind them to plug it in",
                    )
                }
            }
        }
    }

    // ═════════ Ghost mode ═════════
    //
    // Pauses everything that says where I am or what I'm doing (status, battery, speed,
    // position) without removing any stickers. Steps keep counting. Stored per phone.

    private var ghostCleared = false
    private var lastGhost = false
    private val ghostEnd = Runnable { if (refs > 0) apply() }
    private fun ghostPrefs(ctx: Context) = ctx.getSharedPreferences("ghost", Context.MODE_PRIVATE)

    /** When ghost mode ends (epoch ms; Long.MAX_VALUE = until turned off), or 0 when it's off. */
    fun ghostUntil(ctx: Context): Long {
        val u = ghostPrefs(ctx).getLong("until", 0L)
        return if (u > System.currentTimeMillis()) u else 0L
    }

    /** Turn ghost mode on until [until] (Long.MAX_VALUE = until turned off), or off with 0. */
    fun setGhost(ctx: Context, until: Long) {
        ghostPrefs(ctx).edit().putLong("until", until).putLong("since", if (until > 0) System.currentTimeMillis() else 0L).apply()
        if (refs > 0) handler.post { apply() }
    }

    private fun ghosting() = ghostUntil(app) > 0

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
        if (statusOn) evaluate()
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
        if (distRooms.isEmpty() || ghosting()) return
        // ~110 m precision; nothing finer ever leaves the phone.
        val p = Presence(Math.round(loc.latitude * 1000) / 1000.0, Math.round(loc.longitude * 1000) / 1000.0, System.currentTimeMillis(), name)
        distRooms.forEach { db.child("canvas_sessions").child(it).child("presence").child(me).setValue(p) }
    }

    private val onBoard: () -> Unit = {
        if (needLocation) handler.removeCallbacks(reconfig).also { handler.postDelayed(reconfig, 1500) }
        handler.removeCallbacks(batteryCheck); handler.postDelayed(batteryCheck, 500)
    }
    private val batteryCheck = Runnable { if (refs > 0) checkBatteries() }
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

    // ═════════ Status (the avatar sticker) ═════════
    //
    // Everything here is event driven: screen on/off, charger, battery and Google's
    // activity transitions (in vehicle / on bicycle / walking) wake us up; a slow tick
    // only catches music playing with the screen off. Sleep is not detected here: the
    // renderer turns a long night-time rest into sleep, so it works while this phone dozes.

    private const val ACTION_TRANSITION = "com.alancruse.wallpaper.ACTIVITY_TRANSITION"
    private var statusOn = false
    private var interactive = true
    private var plugged = false
    private var battery = -1
    private var activity = ""          // "", ride, cycle, walk
    private var status = Status.NONE
    private var statusAt = 0L
    private var restSince = 0L         // start of the current rest (screen off, not moving)
    private var lastRestSince = 0L     // the rest before a short wake, so a 3 a.m. peek doesn't reset sleep
    private var transitionsPi: android.app.PendingIntent? = null

    private val statusReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(ctx: Context, i: android.content.Intent) {
            when (i.action) {
                android.content.Intent.ACTION_SCREEN_ON, android.content.Intent.ACTION_USER_PRESENT -> interactive = true
                android.content.Intent.ACTION_SCREEN_OFF -> interactive = false
                android.content.Intent.ACTION_BATTERY_CHANGED -> {
                    val lvl = i.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
                    val scale = i.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100)
                    val pct = if (lvl >= 0 && scale > 0) lvl * 100 / scale else -1
                    val p = i.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) != 0
                    // Only bother the network when it's worth showing.
                    val worth = p != plugged || battery < 0 || kotlin.math.abs(pct - battery) >= 5 || (pct <= 15 && pct != battery)
                    battery = pct; plugged = p
                    if (!worth) return
                    evaluate(); flushStats(force = true); return
                }
                ACTION_TRANSITION -> onTransitions(i)
            }
            evaluate()
        }
    }

    private fun onTransitions(i: android.content.Intent) {
        if (!ActivityTransitionResult.hasResult(i)) return
        val r = ActivityTransitionResult.extractResult(i) ?: return
        for (e in r.transitionEvents) {
            val a = when (e.activityType) {
                DetectedActivity.IN_VEHICLE -> Status.RIDE
                DetectedActivity.ON_BICYCLE -> Status.CYCLE
                DetectedActivity.WALKING, DetectedActivity.RUNNING -> Status.WALK
                else -> ""   // STILL
            }
            if (e.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER) activity = a
            else if (activity == a) activity = ""
        }
    }

    private val tick = object : Runnable {
        override fun run() { if (statusOn) { evaluate(); handler.postDelayed(this, 2 * 60_000L) } }
    }
    private val settle = Runnable { if (statusOn) evaluate(allowWake = true) }

    @SuppressLint("MissingPermission", "UnspecifiedRegisterReceiverFlag")
    private fun startStatus() {
        if (statusOn) return
        statusOn = true
        val pm = app.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        interactive = pm.isInteractive
        val f = android.content.IntentFilter().apply {
            addAction(android.content.Intent.ACTION_SCREEN_ON); addAction(android.content.Intent.ACTION_SCREEN_OFF)
            addAction(android.content.Intent.ACTION_USER_PRESENT); addAction(android.content.Intent.ACTION_BATTERY_CHANGED)
            addAction(ACTION_TRANSITION)
        }
        // Sticky: delivers the current battery state straight away.
        ContextCompat.registerReceiver(app, statusReceiver, f, ContextCompat.RECEIVER_NOT_EXPORTED)
        if (has(Manifest.permission.ACTIVITY_RECOGNITION)) try {
            val types = listOf(DetectedActivity.IN_VEHICLE, DetectedActivity.ON_BICYCLE, DetectedActivity.WALKING, DetectedActivity.RUNNING, DetectedActivity.STILL)
            val req = ActivityTransitionRequest(types.flatMap { t ->
                listOf(ActivityTransition.ACTIVITY_TRANSITION_ENTER, ActivityTransition.ACTIVITY_TRANSITION_EXIT).map {
                    ActivityTransition.Builder().setActivityType(t).setActivityTransition(it).build()
                }
            })
            val intent = android.content.Intent(ACTION_TRANSITION).setPackage(app.packageName)
            // Play services fills in the result extras, so the intent must be mutable.
            val flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) android.app.PendingIntent.FLAG_MUTABLE else 0)
            val pi = android.app.PendingIntent.getBroadcast(app, 7, intent, flags)
            ActivityRecognition.getClient(app).requestActivityTransitionUpdates(req, pi)
            transitionsPi = pi
        } catch (e: Exception) { Log.w(TAG, "activity transitions unavailable", e) }
        restSince = System.currentTimeMillis()
        evaluate(allowWake = true)
        handler.postDelayed(tick, 2 * 60_000L)
    }

    @SuppressLint("MissingPermission")
    private fun stopStatus() {
        if (!statusOn) return
        statusOn = false
        handler.removeCallbacks(tick); handler.removeCallbacks(settle)
        try { app.unregisterReceiver(statusReceiver) } catch (_: Exception) {}
        transitionsPi?.let { pi -> try { ActivityRecognition.getClient(app).removeActivityTransitionUpdates(pi) } catch (_: Exception) {} }
        transitionsPi = null
        status = Status.NONE; activity = ""
        flushStats(force = true)
    }

    private fun candidate(): String {
        val now = System.currentTimeMillis()
        val music = try { (app.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager).isMusicActive } catch (_: Exception) { false }
        val fast = now - speedAt < 2 * 60_000L && speed > 15f
        return when {
            activity == Status.RIDE || (fast && activity != Status.CYCLE) -> Status.RIDE
            activity == Status.CYCLE -> Status.CYCLE
            activity == Status.WALK -> Status.WALK
            interactive -> if (music) Status.MUSIC else Status.PHONE
            music -> Status.MUSIC
            plugged -> Status.CHARGE
            else -> Status.AWAY
        }
    }

    /**
     * Picks the status and publishes it when it changes. Waking the screen from a rest only
     * counts after 10 s, so glancing at the clock doesn't flip the avatar out of bed.
     */
    private fun evaluate(allowWake: Boolean = false) {
        val next = candidate()
        if (next == status) return
        val waking = status in Status.REST && (next == Status.PHONE || next == Status.MUSIC)
        if (waking && !allowWake) { handler.removeCallbacks(settle); handler.postDelayed(settle, 10_000L); return }
        val now = System.currentTimeMillis()
        if (next in Status.REST) {
            if (status !in Status.REST) {
                val shortWake = (status == Status.PHONE || status == Status.MUSIC) && now - statusAt < 2 * 60_000L && lastRestSince > 0
                restSince = if (shortWake) lastRestSince else now
            }
            statusAt = restSince
        } else {
            if (status in Status.REST) lastRestSince = restSince
            statusAt = now
        }
        status = next
        Log.d(TAG, "status → $status")
        flushStats(force = true)
    }

    // ═════════ Publishing ═════════

    private var lastWrite = 0L
    private var lastWrittenSteps = -1

    private fun flushStats(force: Boolean = false) {
        val me = uid ?: return
        val now = System.currentTimeMillis()
        val on = statusOn
        val ghost = ghosting()
        val stats = LiveStats(
            stepsToday, kmToday(), if (!ghost && now - speedAt < 5 * 60_000L) speed else 0f, if (ghost) 0L else speedAt, day, name, now,
            status = when { ghost && needStatus -> Status.GHOST; on -> status; else -> "" },
            statusAt = when { ghost && needStatus -> ghostPrefs(app).getLong("since", now); on -> statusAt; else -> 0L },
            battery = if (on && !ghost) battery else -1, charging = on && !ghost && plugged,
            tz = TimeZone.getDefault().getOffset(now) / 60_000,
        )
        LiveBoard.putMyStats(stats)
        val due = force || now - lastWrite > 60_000L || (lastWrittenSteps >= 0 && stepsToday - lastWrittenSteps >= 200)
        if (!due) return
        lastWrite = now; lastWrittenSteps = stepsToday
        db.child("users").child(me).child("stats").setValue(stats)
    }
}
