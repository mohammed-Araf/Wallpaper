package com.alancruse.wallpaper.canvas

import android.content.Context
import android.graphics.Path
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.graphics.toArgb
import com.alancruse.wallpaper.ui.theme.DeepNavyStatic
import com.alancruse.wallpaper.wallpaper.CanvasRenderer
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

/**
 * Realtime sync for one canvas room.
 *
 * All element types use incremental child listeners, and local edits are applied to the
 * in-memory lists immediately (listeners are idempotent), so drawing, erasing and undo never
 * wait on a network round trip. With disk persistence enabled (see WallPaperApp), reopening a
 * room renders instantly from cache.
 */
class CanvasSyncManager private constructor(val roomId: String) {

    constructor(currentUid: String, partnerUid: String) : this(
        if (currentUid < partnerUid) "${currentUid}_${partnerUid}" else "${partnerUid}_${currentUid}"
    )

    companion object {
        fun forRoom(roomId: String): CanvasSyncManager = CanvasSyncManager(roomId)
    }

    private val roomRef = FirebaseDatabase.getInstance().reference.child("canvas_sessions").child(roomId)
    private val strokesRef = roomRef.child("strokes")
    private val stickersRef = roomRef.child("stickers")
    private val textsRef = roomRef.child("texts")
    private val imagesRef = roomRef.child("images")
    private val bgColorRef = roomRef.child("backgroundColor")

    val sharedStrokes = mutableStateListOf<DrawingStroke>()
    val sharedStickers = mutableStateListOf<StickerElement>()
    val sharedTexts = mutableStateListOf<TextElement>()
    val sharedImages = mutableStateListOf<ImageElement>()

    val strokeKeys = ArrayList<String>()
    val stickerKeys = ArrayList<String>()
    val textKeys = ArrayList<String>()
    val imageKeys = ArrayList<String>()

    /** Pre-built render paths, parallel to [sharedStrokes]. */
    val strokePaths = ArrayList<Path>()
    var smoothPaths = true

    val sharedBackgroundColor = mutableIntStateOf(DeepNavyStatic.toArgb())
    val isLoaded = mutableStateOf(false)

    val canUndo = mutableStateOf(false)
    val canRedo = mutableStateOf(false)

    private val listeners = mutableListOf<Pair<DatabaseReference, Any>>()
    private val handler = Handler(Looper.getMainLooper())

    // ── Generic idempotent child sync ──

    private inner class ChildSync<T : Any>(
        private val ref: DatabaseReference,
        private val cls: Class<T>,
        private val list: SnapshotStateList<T>,
        private val keys: ArrayList<String>,
        private val onAdd: (Int, T) -> Unit = { _, _ -> },
        private val onSet: (Int, T) -> Unit = { _, _ -> },
        private val onRemove: (Int) -> Unit = {},
    ) : ChildEventListener {
        override fun onChildAdded(s: DataSnapshot, prev: String?) {
            val key = s.key ?: return
            val v = try { s.getValue(cls) } catch (e: Exception) { null } ?: return
            val idx = keys.indexOf(key)
            if (idx < 0 && pendingKeys.contains(key)) return // deleted locally, server catching up
            if (idx >= 0) { list[idx] = v; onSet(idx, v) } else { keys.add(key); list.add(v); onAdd(list.lastIndex, v) }
            refreshUndo()
        }
        override fun onChildChanged(s: DataSnapshot, prev: String?) {
            val key = s.key ?: return
            val v = try { s.getValue(cls) } catch (e: Exception) { null } ?: return
            val idx = keys.indexOf(key)
            if (idx >= 0) {
                // Ignore echoes of our own in-flight transform
                if (pendingKeys.contains(key)) return
                list[idx] = v; onSet(idx, v)
            }
        }
        override fun onChildRemoved(s: DataSnapshot) {
            val idx = keys.indexOf(s.key ?: return)
            if (idx >= 0) { keys.removeAt(idx); list.removeAt(idx); onRemove(idx); refreshUndo() }
        }
        override fun onChildMoved(s: DataSnapshot, prev: String?) {}
        override fun onCancelled(error: DatabaseError) { Log.e("CanvasSync", "${ref.key}: ${error.message}") }

        fun attach() { ref.addChildEventListener(this); listeners.add(ref to this) }
    }

    private val pendingKeys = HashSet<String>()

    init {
        ChildSync(strokesRef, DrawingStroke::class.java, sharedStrokes, strokeKeys,
            onAdd = { _, s -> strokePaths.add(CanvasRenderer.buildPath(s.path, smoothPaths)) },
            onSet = { i, s -> strokePaths[i] = CanvasRenderer.buildPath(s.path, smoothPaths) },
            onRemove = { i -> strokePaths.removeAt(i) }
        ).attach()
        ChildSync(stickersRef, StickerElement::class.java, sharedStickers, stickerKeys).attach()
        ChildSync(textsRef, TextElement::class.java, sharedTexts, textKeys).attach()
        ChildSync(imagesRef, ImageElement::class.java, sharedImages, imageKeys).attach()

        val bg = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                s.getValue(Int::class.java)?.let { sharedBackgroundColor.intValue = it }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        bgColorRef.addValueEventListener(bg); listeners.add(bgColorRef to bg)

        // Mark loaded once the initial snapshot of the room arrives (cache or server)
        roomRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) { isLoaded.value = true }
            override fun onCancelled(error: DatabaseError) { isLoaded.value = true }
        })
    }

    // ── Local-first helpers ──

    private fun addStrokeLocal(key: String, s: DrawingStroke) {
        if (strokeKeys.contains(key)) return
        strokeKeys.add(key); sharedStrokes.add(s); strokePaths.add(CanvasRenderer.buildPath(s.path, smoothPaths))
    }

    private fun removeStrokeLocal(idx: Int) {
        strokeKeys.removeAt(idx); sharedStrokes.removeAt(idx); strokePaths.removeAt(idx)
    }

    private fun refreshUndo() {
        canUndo.value = sharedStrokes.isNotEmpty() || sharedStickers.isNotEmpty() || sharedTexts.isNotEmpty() || sharedImages.isNotEmpty()
        canRedo.value = redoStack.isNotEmpty()
    }

    // ── Push ──

    fun pushStroke(stroke: DrawingStroke) {
        val ref = strokesRef.push(); addStrokeLocal(ref.key!!, stroke); ref.setValue(stroke)
        clearRedoStack(); refreshUndo()
    }

    fun pushSticker(sticker: StickerElement) { stickersRef.push().setValue(sticker); clearRedoStack() }
    fun pushText(textElement: TextElement) { textsRef.push().setValue(textElement); clearRedoStack() }

    /** Compresses the image (max 720px JPEG) and stores it inline as Base64. Runs off the main thread. */
    fun pushImage(context: Context, localUri: Uri, x: Float, y: Float, width: Float, onDone: (Boolean, String?) -> Unit) {
        Thread {
            val result: Pair<ImageElement?, String?> = try {
                val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(localUri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, opts) }
                var sample = 1
                while (opts.outWidth / (sample * 2) >= 720 && opts.outHeight / (sample * 2) >= 720) sample *= 2
                val original = context.contentResolver.openInputStream(localUri)?.use {
                    android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
                }
                if (original == null) null to "Could not read image" else {
                    val max = 720
                    val bmp = if (original.width > max || original.height > max) {
                        val r = minOf(max.toFloat() / original.width, max.toFloat() / original.height)
                        android.graphics.Bitmap.createScaledBitmap(original, (original.width * r).toInt(), (original.height * r).toInt(), true)
                    } else original
                    val baos = java.io.ByteArrayOutputStream()
                    bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, baos)
                    val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                    CanvasRenderer.decodeBitmap(b64) // warm cache
                    ImageElement(b64, x - width / 2f, y - width * bmp.height / bmp.width / 2f, width, width * bmp.height / bmp.width) to null
                }
            } catch (e: Exception) { null to (e.message ?: "Image error") }
            handler.post {
                val el = result.first
                if (el == null) onDone(false, result.second) else {
                    imagesRef.push().setValue(el)
                        .addOnSuccessListener { onDone(true, null) }
                        .addOnFailureListener { onDone(false, it.message) }
                    clearRedoStack()
                }
            }
        }.start()
    }

    // ── Throttled transforms (local immediately, network every 120 ms) ──

    private val throttleMs = 120L
    private val pendingWrites = HashMap<String, Pair<DatabaseReference, Any>>()
    private var flushScheduled = false
    private val flush = Runnable {
        flushScheduled = false
        val writes = pendingWrites.toMap(); pendingWrites.clear()
        writes.forEach { (key, w) ->
            w.first.setValue(w.second).addOnCompleteListener { pendingKeys.remove(key) }
        }
    }

    private fun queue(ref: DatabaseReference, key: String, value: Any) {
        pendingKeys.add(key)
        pendingWrites[key] = ref to value
        if (!flushScheduled) { flushScheduled = true; handler.postDelayed(flush, throttleMs) }
    }

    fun updateSticker(index: Int, s: StickerElement) {
        if (index !in stickerKeys.indices) return
        sharedStickers[index] = s; queue(stickersRef.child(stickerKeys[index]), stickerKeys[index], s)
    }

    fun updateText(index: Int, t: TextElement) {
        if (index !in textKeys.indices) return
        sharedTexts[index] = t; queue(textsRef.child(textKeys[index]), textKeys[index], t)
    }

    fun updateImage(index: Int, img: ImageElement) {
        if (index !in imageKeys.indices) return
        sharedImages[index] = img; queue(imagesRef.child(imageKeys[index]), imageKeys[index], img)
    }

    /**
     * Local-first delete. Drops any queued transform for the element first —
     * otherwise a throttled move written after the delete would bring it back.
     */
    private fun <T> deleteAt(index: Int, ref: DatabaseReference, keys: ArrayList<String>, list: MutableList<T>) {
        if (index !in keys.indices) return
        val key = keys[index]
        pendingWrites.remove(key)
        keys.removeAt(index); list.removeAt(index)
        ref.child(key).removeValue().addOnCompleteListener { pendingKeys.remove(key) }
        refreshUndo()
    }

    fun deleteSticker(index: Int) = deleteAt(index, stickersRef, stickerKeys, sharedStickers)
    fun deleteText(index: Int) = deleteAt(index, textsRef, textKeys, sharedTexts)
    fun deleteImage(index: Int) = deleteAt(index, imagesRef, imageKeys, sharedImages)

    fun setBackgroundColor(argb: Int) { sharedBackgroundColor.intValue = argb; bgColorRef.setValue(argb) }

    // ── Undo / redo (local data, no queries) ──

    private data class UndoEntry(val type: String, val data: Any)
    private val redoStack = ArrayList<UndoEntry>()

    fun undoLast() {
        var best: Triple<String, Int, Long>? = null
        fun consider(type: String, i: Int, ts: Long) { if (best == null || ts >= best!!.third) best = Triple(type, i, ts) }
        sharedStrokes.forEachIndexed { i, s -> consider("strokes", i, s.timestamp) }
        sharedStickers.forEachIndexed { i, s -> consider("stickers", i, s.timestamp) }
        sharedTexts.forEachIndexed { i, s -> consider("texts", i, s.timestamp) }
        sharedImages.forEachIndexed { i, s -> consider("images", i, s.timestamp) }
        val (type, i, _) = best ?: return
        when (type) {
            "strokes" -> { redoStack.add(UndoEntry(type, sharedStrokes[i])); val k = strokeKeys[i]; removeStrokeLocal(i); strokesRef.child(k).removeValue() }
            "stickers" -> { redoStack.add(UndoEntry(type, sharedStickers[i])); stickersRef.child(stickerKeys[i]).removeValue() }
            "texts" -> { redoStack.add(UndoEntry(type, sharedTexts[i])); textsRef.child(textKeys[i]).removeValue() }
            "images" -> { redoStack.add(UndoEntry(type, sharedImages[i])); imagesRef.child(imageKeys[i]).removeValue() }
        }
        refreshUndo()
    }

    fun redoLast() {
        val e = redoStack.removeLastOrNull() ?: return
        when (e.type) {
            "strokes" -> { val r = strokesRef.push(); addStrokeLocal(r.key!!, e.data as DrawingStroke); r.setValue(e.data) }
            "stickers" -> stickersRef.push().setValue(e.data)
            "texts" -> textsRef.push().setValue(e.data)
            "images" -> imagesRef.push().setValue(e.data)
        }
        refreshUndo()
    }

    fun clearRedoStack() { redoStack.clear(); canRedo.value = false }

    // ── Erasers (local, batched) ──

    /** Cuts every stroke under the eraser circle, keeping the pieces outside it. */
    fun erasePointsNear(x: Float, y: Float, radius: Float) {
        val r2 = radius * radius
        val updates = HashMap<String, Any?>()
        var i = sharedStrokes.size - 1
        while (i >= 0) {
            val stroke = sharedStrokes[i]
            val hit = stroke.path.any { val dx = it.x - x; val dy = it.y - y; dx * dx + dy * dy < r2 + stroke.strokeWidth * stroke.strokeWidth / 4 }
            if (hit) {
                val key = strokeKeys[i]
                val segments = ArrayList<List<Point>>()
                var cur = ArrayList<Point>()
                for (p in stroke.path) {
                    val dx = p.x - x; val dy = p.y - y
                    if (dx * dx + dy * dy < r2) { if (cur.size >= 2) segments.add(cur); cur = ArrayList() } else cur.add(p)
                }
                if (cur.size >= 2) segments.add(cur)
                removeStrokeLocal(i)
                updates[key] = null
                segments.forEach { seg ->
                    val k = strokesRef.push().key!!
                    val ns = stroke.copy(path = seg)  // keep original timestamp → layer order preserved
                    addStrokeLocal(k, ns); updates[k] = ns
                }
            }
            i--
        }
        if (updates.isNotEmpty()) { strokesRef.updateChildren(updates); refreshUndo() }
    }

    /** Removes the top-most whole stroke near the point. Returns true if one was removed. */
    fun eraseWholeStrokeNear(x: Float, y: Float, threshold: Float = 40f): Boolean {
        for (i in sharedStrokes.indices.reversed()) {
            val s = sharedStrokes[i]
            val t = threshold + s.strokeWidth / 2
            if (s.path.any { val dx = it.x - x; val dy = it.y - y; dx * dx + dy * dy < t * t }) {
                val k = strokeKeys[i]; removeStrokeLocal(i); strokesRef.child(k).removeValue(); refreshUndo()
                return true
            }
        }
        return false
    }

    fun clearCanvas() {
        roomRef.updateChildren(mapOf<String, Any?>("strokes" to null, "stickers" to null, "texts" to null, "images" to null))
        redoStack.clear(); refreshUndo()
    }

    fun cleanup() {
        handler.removeCallbacks(flush)
        flush.run()
        for ((ref, l) in listeners) {
            when (l) {
                is ChildEventListener -> ref.removeEventListener(l)
                is ValueEventListener -> ref.removeEventListener(l)
            }
        }
        listeners.clear()
    }
}
