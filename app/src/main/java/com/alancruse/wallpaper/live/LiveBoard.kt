package com.alancruse.wallpaper.live

import androidx.compose.runtime.mutableIntStateOf
import com.alancruse.wallpaper.canvas.StickerElement
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory board of live sticker data (stats per user, presence per room),
 * shared by the editor, the live wallpaper and the lock screen in this process.
 * Firebase listeners are reference-counted, so each path is watched once.
 */
object LiveBoard : LiveSource {
    private val db get() = FirebaseDatabase.getInstance().reference
    private val stats = ConcurrentHashMap<String, LiveStats>()
    private val presence = ConcurrentHashMap<String, Map<String, Presence>>()
    private val watches = HashMap<String, Pair<Int, Pair<DatabaseReference, ValueEventListener>>>()
    private val observers = LinkedHashSet<() -> Unit>()

    /** Bumped on every change — Compose screens read it to redraw. */
    val version = mutableIntStateOf(0)

    override val myUid: String? get() = FirebaseAuth.getInstance().currentUser?.uid
    override fun stats(uid: String) = stats[uid]
    override fun presence(roomId: String) = presence[roomId] ?: emptyMap()

    fun observe(f: () -> Unit) { observers.add(f) }
    fun unobserve(f: () -> Unit) { observers.remove(f) }

    /** Local write-through so my own sticker updates instantly. */
    fun putMyStats(s: LiveStats) { myUid?.let { stats[it] = s; changed() } }

    private fun changed() { version.intValue++; observers.toList().forEach { it() } }

    private fun watch(key: String, ref: DatabaseReference, onData: (DataSnapshot) -> Unit) {
        val cur = watches[key]
        if (cur != null) { watches[key] = (cur.first + 1) to cur.second; return }
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) { onData(s); changed() }
            override fun onCancelled(e: DatabaseError) {}
        }
        ref.addValueEventListener(l)
        watches[key] = 1 to (ref to l)
    }

    private fun unwatch(key: String) {
        val cur = watches[key] ?: return
        if (cur.first > 1) { watches[key] = (cur.first - 1) to cur.second; return }
        cur.second.first.removeEventListener(cur.second.second); watches.remove(key)
    }

    fun watchStats(uid: String) = watch("s:$uid", db.child("users").child(uid).child("stats")) { s ->
        s.getValue(LiveStats::class.java)?.let { stats[uid] = it }
    }
    fun unwatchStats(uid: String) = unwatch("s:$uid")

    fun watchPresence(roomId: String) = watch("p:$roomId", db.child("canvas_sessions").child(roomId).child("presence")) { s ->
        presence[roomId] = s.children.mapNotNull { c -> c.key?.let { k -> c.getValue(Presence::class.java)?.let { k to it } } }.toMap()
    }
    fun unwatchPresence(roomId: String) = unwatch("p:$roomId")

    /** Keeps exactly the subscriptions a room's stickers need. */
    class Watcher(private val roomId: String) {
        private var owners = emptySet<String>()
        private var dist = false

        fun update(stickers: List<StickerElement>) {
            val need = stickers.filter { StickerKinds.needsStats(it.kind) && it.owner.isNotEmpty() }.map { it.owner }.toSet()
            (need - owners).forEach { watchStats(it) }
            (owners - need).forEach { unwatchStats(it) }
            owners = need
            val d = stickers.any { it.kind == "w:dist" }
            if (d && !dist) watchPresence(roomId)
            if (!d && dist) unwatchPresence(roomId)
            dist = d
        }

        fun close() { update(emptyList()) }
    }
}
