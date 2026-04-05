package com.alancruse.wallpaper.canvas

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.toArgb
import com.alancruse.wallpaper.ui.theme.DeepNavy
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class CanvasSyncManager private constructor(
    private val roomId: String
) {
    private val db = FirebaseDatabase.getInstance().reference

    private val roomRef = db.child("canvas_sessions").child(roomId)

    /** Couple constructor — computes room ID from two UIDs */
    constructor(currentUid: String, partnerUid: String) : this(
        if (currentUid < partnerUid) "${currentUid}_${partnerUid}"
        else "${partnerUid}_${currentUid}"
    )

    companion object {
        /** Group constructor — uses room ID directly */
        fun forRoom(roomId: String): CanvasSyncManager = CanvasSyncManager(roomId)
    }
    private val strokesRef = roomRef.child("strokes")
    private val stickersRef = roomRef.child("stickers")
    private val textsRef = roomRef.child("texts")
    private val imagesRef = roomRef.child("images")
    private val bgColorRef = roomRef.child("backgroundColor")

    // Observable lists — element data
    val sharedStrokes = mutableStateListOf<DrawingStroke>()
    val sharedStickers = mutableStateListOf<StickerElement>()
    val sharedTexts = mutableStateListOf<TextElement>()
    val sharedImages = mutableStateListOf<ImageElement>()

    // Firebase keys for each element (parallel indices)
    val strokeKeys = mutableStateListOf<String>()
    val stickerKeys = mutableStateListOf<String>()
    val textKeys = mutableStateListOf<String>()
    val imageKeys = mutableStateListOf<String>()

    // Background color (ARGB int, synced across devices)
    val sharedBackgroundColor = mutableIntStateOf(DeepNavy.toArgb())

    // Listeners for cleanup
    private var strokesListener: ChildEventListener? = null
    private var stickersListener: ValueEventListener? = null
    private var textsListener: ValueEventListener? = null
    private var imagesListener: ValueEventListener? = null
    private var bgColorListener: ValueEventListener? = null

    // Throttle state for transform updates
    private val handler = Handler(Looper.getMainLooper())
    private val throttleMs = 150L
    private var pendingStickerUpdate: Pair<Int, StickerElement>? = null
    private var pendingTextUpdate: Pair<Int, TextElement>? = null
    private var pendingImageUpdate: Pair<Int, ImageElement>? = null
    private var stickerThrottleRunnable: Runnable? = null
    private var textThrottleRunnable: Runnable? = null
    private var imageThrottleRunnable: Runnable? = null

    init {
        Log.d("CanvasSync", "Room ID: $roomId")

        // ── Strokes: use ChildEventListener for incremental updates ──
        strokesListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val key = snapshot.key ?: return
                val stroke = snapshot.getValue(DrawingStroke::class.java) ?: return
                strokeKeys.add(key)
                sharedStrokes.add(stroke)
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                val key = snapshot.key ?: return
                val stroke = snapshot.getValue(DrawingStroke::class.java) ?: return
                val idx = strokeKeys.indexOf(key)
                if (idx >= 0) {
                    sharedStrokes[idx] = stroke
                }
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {
                val key = snapshot.key ?: return
                val idx = strokeKeys.indexOf(key)
                if (idx >= 0) {
                    strokeKeys.removeAt(idx)
                    sharedStrokes.removeAt(idx)
                }
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {
                Log.e("CanvasSync", "Strokes error: ${error.message}")
            }
        }
        strokesRef.addChildEventListener(strokesListener!!)

        // ── Stickers: keep ValueEventListener (small count, transforms need full sync) ──
        stickersListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                sharedStickers.clear()
                stickerKeys.clear()
                for (child in snapshot.children) {
                    child.getValue(StickerElement::class.java)?.let {
                        sharedStickers.add(it)
                        stickerKeys.add(child.key!!)
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("CanvasSync", "Stickers error: ${error.message}")
            }
        }
        stickersRef.addValueEventListener(stickersListener!!)

        textsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                sharedTexts.clear()
                textKeys.clear()
                for (child in snapshot.children) {
                    child.getValue(TextElement::class.java)?.let {
                        sharedTexts.add(it)
                        textKeys.add(child.key!!)
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("CanvasSync", "Texts error: ${error.message}")
            }
        }
        textsRef.addValueEventListener(textsListener!!)

        imagesListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                sharedImages.clear()
                imageKeys.clear()
                for (child in snapshot.children) {
                    child.getValue(ImageElement::class.java)?.let {
                        sharedImages.add(it)
                        imageKeys.add(child.key!!)
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("CanvasSync", "Images error: ${error.message}")
            }
        }
        imagesRef.addValueEventListener(imagesListener!!)

        // ── Background color listener ──
        bgColorListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val argb = snapshot.getValue(Int::class.java)
                if (argb != null) {
                    sharedBackgroundColor.intValue = argb
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("CanvasSync", "BgColor error: ${error.message}")
            }
        }
        bgColorRef.addValueEventListener(bgColorListener!!)
    }

    // ── Push methods ──

    fun pushStroke(stroke: DrawingStroke) {
        strokesRef.push().setValue(stroke)
    }

    fun pushSticker(sticker: StickerElement) {
        stickersRef.push().setValue(sticker)
    }

    fun pushText(textElement: TextElement) {
        textsRef.push().setValue(textElement)
    }

    /**
     * Encode image as Base64 and store directly in RTDB — no Firebase Storage needed.
     * Compresses to JPEG (max 600px, 50% quality) to stay within RTDB limits.
     */
    fun pushImage(context: Context, localUri: Uri, x: Float, y: Float, width: Float, height: Float, onDone: (Boolean, String?) -> Unit) {
        try {
            val inputStream = context.contentResolver.openInputStream(localUri)
            if (inputStream == null) {
                onDone(false, "Could not read image")
                return
            }

            val originalBitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            if (originalBitmap == null) {
                onDone(false, "Could not decode image")
                return
            }

            // Resize to max 600px to keep Base64 string small for RTDB
            val maxDim = 600
            val bitmap = if (originalBitmap.width > maxDim || originalBitmap.height > maxDim) {
                val ratio = minOf(maxDim.toFloat() / originalBitmap.width, maxDim.toFloat() / originalBitmap.height)
                val newW = (originalBitmap.width * ratio).toInt()
                val newH = (originalBitmap.height * ratio).toInt()
                android.graphics.Bitmap.createScaledBitmap(originalBitmap, newW, newH, true)
            } else {
                originalBitmap
            }

            // Compress to JPEG at 50% quality
            val baos = java.io.ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 50, baos)
            val compressedBytes = baos.toByteArray()
            val base64String = Base64.encodeToString(compressedBytes, Base64.NO_WRAP)
            Log.d("CanvasSync", "Image Base64 size: ${base64String.length / 1024}KB")

            // Calculate display dimensions preserving aspect ratio
            val aspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
            val displayWidth = width
            val displayHeight = width / aspectRatio

            val element = ImageElement(
                imageBase64 = base64String,
                x = x, y = y,
                width = displayWidth, height = displayHeight
            )
            imagesRef.push().setValue(element)
                .addOnSuccessListener { onDone(true, null) }
                .addOnFailureListener { e ->
                    Log.e("CanvasSync", "RTDB write failed: ${e.message}")
                    onDone(false, "Save failed: ${e.message}")
                }
        } catch (e: Exception) {
            Log.e("CanvasSync", "Image processing error: ${e.message}", e)
            onDone(false, "Error: ${e.message}")
        }
    }

    // ── Throttled update methods for transforms ──

    fun updateSticker(index: Int, sticker: StickerElement) {
        if (index !in stickerKeys.indices) return
        // Update local state immediately for responsiveness
        if (index in sharedStickers.indices) {
            sharedStickers[index] = sticker
        }
        pendingStickerUpdate = index to sticker
        if (stickerThrottleRunnable == null) {
            stickerThrottleRunnable = Runnable {
                pendingStickerUpdate?.let { (idx, s) ->
                    if (idx in stickerKeys.indices) {
                        stickersRef.child(stickerKeys[idx]).setValue(s)
                    }
                }
                pendingStickerUpdate = null
                stickerThrottleRunnable = null
            }
            handler.postDelayed(stickerThrottleRunnable!!, throttleMs)
        }
    }

    fun updateText(index: Int, textElement: TextElement) {
        if (index !in textKeys.indices) return
        if (index in sharedTexts.indices) {
            sharedTexts[index] = textElement
        }
        pendingTextUpdate = index to textElement
        if (textThrottleRunnable == null) {
            textThrottleRunnable = Runnable {
                pendingTextUpdate?.let { (idx, t) ->
                    if (idx in textKeys.indices) {
                        textsRef.child(textKeys[idx]).setValue(t)
                    }
                }
                pendingTextUpdate = null
                textThrottleRunnable = null
            }
            handler.postDelayed(textThrottleRunnable!!, throttleMs)
        }
    }

    fun updateImage(index: Int, imageElement: ImageElement) {
        if (index !in imageKeys.indices) return
        if (index in sharedImages.indices) {
            sharedImages[index] = imageElement
        }
        pendingImageUpdate = index to imageElement
        if (imageThrottleRunnable == null) {
            imageThrottleRunnable = Runnable {
                pendingImageUpdate?.let { (idx, img) ->
                    if (idx in imageKeys.indices) {
                        imagesRef.child(imageKeys[idx]).setValue(img)
                    }
                }
                pendingImageUpdate = null
                imageThrottleRunnable = null
            }
            handler.postDelayed(imageThrottleRunnable!!, throttleMs)
        }
    }

    // ── Delete individual elements ──

    fun deleteSticker(index: Int) {
        if (index in stickerKeys.indices) {
            stickersRef.child(stickerKeys[index]).removeValue()
        }
    }

    fun deleteText(index: Int) {
        if (index in textKeys.indices) {
            textsRef.child(textKeys[index]).removeValue()
        }
    }

    fun deleteImage(index: Int) {
        if (index in imageKeys.indices) {
            imagesRef.child(imageKeys[index]).removeValue()
        }
    }

    // ── Background color ──

    fun setBackgroundColor(argb: Int) {
        sharedBackgroundColor.intValue = argb
        bgColorRef.setValue(argb)
    }

    // ── Undo: remove the most recently added element across all types ──

    fun undoLast() {
        var remaining = 4
        data class Candidate(val ref: String, val key: String, val ts: Long)
        val results = mutableListOf<Candidate>()

        fun checkAndRemove() {
            remaining--
            if (remaining == 0 && results.isNotEmpty()) {
                val latest = results.maxByOrNull { it.ts }
                if (latest != null) {
                    roomRef.child(latest.ref).child(latest.key).removeValue()
                }
            }
        }

        strokesRef.orderByChild("timestamp").limitToLast(1)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (child in snapshot.children) {
                        val ts = child.child("timestamp").getValue(Long::class.java) ?: 0L
                        results.add(Candidate("strokes", child.key!!, ts))
                    }
                    checkAndRemove()
                }
                override fun onCancelled(error: DatabaseError) { checkAndRemove() }
            })

        stickersRef.orderByChild("timestamp").limitToLast(1)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (child in snapshot.children) {
                        val ts = child.child("timestamp").getValue(Long::class.java) ?: 0L
                        results.add(Candidate("stickers", child.key!!, ts))
                    }
                    checkAndRemove()
                }
                override fun onCancelled(error: DatabaseError) { checkAndRemove() }
            })

        textsRef.orderByChild("timestamp").limitToLast(1)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (child in snapshot.children) {
                        val ts = child.child("timestamp").getValue(Long::class.java) ?: 0L
                        results.add(Candidate("texts", child.key!!, ts))
                    }
                    checkAndRemove()
                }
                override fun onCancelled(error: DatabaseError) { checkAndRemove() }
            })

        imagesRef.orderByChild("timestamp").limitToLast(1)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (child in snapshot.children) {
                        val ts = child.child("timestamp").getValue(Long::class.java) ?: 0L
                        results.add(Candidate("images", child.key!!, ts))
                    }
                    checkAndRemove()
                }
                override fun onCancelled(error: DatabaseError) { checkAndRemove() }
            })
    }

    fun erasePointsNear(tapX: Float, tapY: Float, threshold: Float = 30f) {
        strokesRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (child in snapshot.children) {
                    val stroke = child.getValue(DrawingStroke::class.java) ?: continue
                    // Check if any point in this stroke is within the eraser radius
                    val hasHit = stroke.path.any { point ->
                        val dx = point.x - tapX
                        val dy = point.y - tapY
                        (dx * dx + dy * dy) < threshold * threshold
                    }
                    if (!hasHit) continue

                    // Split the stroke: keep segments outside the eraser radius
                    val segments = mutableListOf<List<Point>>()
                    var currentSegment = mutableListOf<Point>()

                    for (point in stroke.path) {
                        val dx = point.x - tapX
                        val dy = point.y - tapY
                        val isInEraser = (dx * dx + dy * dy) < threshold * threshold
                        if (isInEraser) {
                            // End current segment if it has enough points
                            if (currentSegment.size >= 2) {
                                segments.add(currentSegment.toList())
                            }
                            currentSegment = mutableListOf()
                        } else {
                            currentSegment.add(point)
                        }
                    }
                    if (currentSegment.size >= 2) {
                        segments.add(currentSegment.toList())
                    }

                    // Remove the original stroke
                    child.ref.removeValue()

                    // Push back remaining segments as new strokes
                    for (seg in segments) {
                        strokesRef.push().setValue(
                            stroke.copy(path = seg, timestamp = System.currentTimeMillis())
                        )
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    /** Erase entire stroke nearest to tap point (no splitting) */
    fun eraseWholeStrokeNear(tapX: Float, tapY: Float, threshold: Float = 40f) {
        strokesRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (child in snapshot.children) {
                    val stroke = child.getValue(DrawingStroke::class.java) ?: continue
                    val isNear = stroke.path.any { point ->
                        val dx = point.x - tapX
                        val dy = point.y - tapY
                        (dx * dx + dy * dy) < threshold * threshold
                    }
                    if (isNear) {
                        child.ref.removeValue()
                        return
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    fun clearCanvas() {
        strokesRef.removeValue()
        stickersRef.removeValue()
        textsRef.removeValue()
        imagesRef.removeValue()
    }

    /** Remove all listeners and cancel pending throttle callbacks */
    fun cleanup() {
        strokesListener?.let { strokesRef.removeEventListener(it) }
        stickersListener?.let { stickersRef.removeEventListener(it) }
        textsListener?.let { textsRef.removeEventListener(it) }
        imagesListener?.let { imagesRef.removeEventListener(it) }
        bgColorListener?.let { bgColorRef.removeEventListener(it) }
        stickerThrottleRunnable?.let { handler.removeCallbacks(it) }
        textThrottleRunnable?.let { handler.removeCallbacks(it) }
        imageThrottleRunnable?.let { handler.removeCallbacks(it) }
    }
}
