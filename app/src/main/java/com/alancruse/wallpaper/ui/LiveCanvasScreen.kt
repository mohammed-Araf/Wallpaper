package com.alancruse.wallpaper.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alancruse.wallpaper.canvas.*
import com.alancruse.wallpaper.settings.LocalAppSettings
import com.alancruse.wallpaper.ui.components.*
import com.alancruse.wallpaper.ui.theme.*
import com.alancruse.wallpaper.wallpaper.CanvasRenderer
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private enum class Tool { MOVE, PEN, ERASER, TEXT }
private enum class ElType { STICKER, TEXT, IMAGE }
private data class TextEdit(val canvasX: Float, val canvasY: Float, val index: Int?, val value: String)

private val PenColors = listOf(
    Color.White, Color(0xFF1C1C1E), Color(0xFFFF453A), Color(0xFFFF9F0A), Color(0xFFFFD60A),
    Color(0xFF30D158), Color(0xFF40C8E0), Color(0xFF0A84FF), Color(0xFF5E5CE6), Color(0xFFBF5AF2),
    Color(0xFFFF375F), Color(0xFFAC8E68),
)
private val BgColors = listOf(
    Color.Black, Color(0xFF0A0A0F), Color(0xFF1C1C1E), Color(0xFF16213E), Color(0xFF0F3460), Color(0xFF2C003E),
    Color(0xFF1B4332), Color(0xFF3D0C11), Color(0xFF2D4059), Color(0xFF5E5CE6), Color(0xFFFF375F), Color(0xFFFF9F0A),
    Color.White, Color(0xFFF2F2F7), Color(0xFFFFF4E6), Color(0xFFFFE6EE), Color(0xFFE6F0FF), Color(0xFFE6FFEF),
)
private val Emojis = listOf(
    "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "💕", "💖", "💗", "💘", "💝", "💞", "💫", "✨",
    "⭐", "🌟", "🔥", "🌈", "☀️", "🌙", "❄️", "⚡", "🌸", "🌺", "🌻", "🌹", "🌷", "🍀", "🦋", "🐝",
    "😊", "😍", "🥰", "😘", "😎", "🥳", "🤗", "😂", "🥹", "😴", "🤍", "👑", "💎", "🎵", "🎉", "🎈",
    "🎨", "📸", "☕", "🍓", "🍕", "🍩", "🐶", "🐱", "🐼", "🦊", "🌊", "🏔️", "✈️", "🏠", "💌", "🔒",
)
private val WidthSteps = listOf(4f, 8f, 16f, 28f)
private val TextSteps = listOf(40f, 56f, 80f, 120f)

@Composable
fun LiveCanvasScreen(
    syncManager: CanvasSyncManager,
    rooms: List<CanvasRoom>,
    readOnly: Boolean,
    homeRoomId: String?,
    lockRoomId: String?,
    onSwitchRoom: (CanvasRoom) -> Unit,
    onSetHome: () -> Unit,
    onSetLock: () -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val settings = LocalAppSettings.current
    val c = LocalWallpaperColors.current
    val haptic = rememberHaptics()
    val keyboard = LocalSoftwareKeyboardController.current
    val sync = syncManager
    val hazeState = remember { HazeState() }

    LaunchedEffect(settings.smoothStrokes) { sync.smoothPaths = settings.smoothStrokes }

    // ── View state ──
    var screenW by remember { mutableFloatStateOf(1080f) }
    var screenH by remember { mutableFloatStateOf(2400f) }
    var viewScale by remember { mutableFloatStateOf(1f) }
    var viewOffset by remember { mutableStateOf(Offset.Zero) }
    var chrome by remember { mutableStateOf(true) }
    var redraw by remember { mutableIntStateOf(0) }

    // ── Tool state ──
    var tool by remember { mutableStateOf(if (readOnly) Tool.MOVE else Tool.PEN) }
    var trayOpen by remember { mutableStateOf(false) }
    var penColor by remember { mutableStateOf(Color.White) }
    var penAlpha by remember { mutableFloatStateOf(1f) }
    var penWidth by remember { mutableFloatStateOf(settings.defaultBrush) }
    var eraserWidth by remember { mutableFloatStateOf(28f) }
    var eraseWhole by remember { mutableStateOf(false) }
    var textSize by remember { mutableFloatStateOf(56f) }
    var eraserPos by remember { mutableStateOf<Offset?>(null) }

    // ── Overlays ──
    var showStickers by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var showRooms by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var dragEmoji by remember { mutableStateOf<String?>(null) }
    var dragEmojiPos by remember { mutableStateOf(Offset.Zero) }
    var editing by remember { mutableStateOf<TextEdit?>(null) }
    var uploading by remember { mutableStateOf(false) }

    // Element drag → dustbin
    var draggingEl by remember { mutableStateOf(false) }
    var overBin by remember { mutableStateOf(false) }

    // Live stroke
    val livePoints = remember { ArrayList<Point>() }
    val livePaint = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            strokeCap = android.graphics.Paint.Cap.ROUND; strokeJoin = android.graphics.Paint.Join.ROUND
        }
    }
    val guidePaint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { style = android.graphics.Paint.Style.STROKE } }

    fun toCanvas(o: Offset) = (o - viewOffset) / viewScale
    fun visibleCenter() = toCanvas(Offset(screenW / 2f, screenH / 2.4f))

    // Decode images off the main thread, then redraw
    LaunchedEffect(sync) {
        snapshotFlow { sync.sharedImages.map { it.imageBase64 } }.collect { list ->
            val missing = list.filter { it.isNotEmpty() && CanvasRenderer.cachedBitmap(it) == null }
            if (missing.isNotEmpty()) {
                withContext(Dispatchers.Default) { missing.forEach { CanvasRenderer.decodeBitmap(it) } }
                redraw++
            }
        }
    }

    // Save a thumbnail for the home screen when leaving this room
    DisposableEffect(sync) {
        onDispose {
            CanvasRenderer.saveThumbnail(
                context, sync.roomId, sync.sharedBackgroundColor.intValue, screenW.toInt(), screenH.toInt(),
                sync.sharedStrokes.toList(), sync.sharedStickers.toList(), sync.sharedTexts.toList(), sync.sharedImages.toList()
            )
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            uploading = true
            val center = visibleCenter()
            sync.pushImage(context, uri, center.x, center.y, screenW * 0.5f) { ok, err ->
                uploading = false
                if (!ok) Toast.makeText(context, err ?: "Upload failed", Toast.LENGTH_SHORT).show()
                else { tool = Tool.MOVE; haptic(Haptic.Confirm) }
            }
        }
    }

    fun commitText() {
        val e = editing ?: return
        val txt = e.value.trimEnd()
        if (e.index != null && e.index in sync.sharedTexts.indices) {
            if (txt.isBlank()) sync.deleteText(e.index) else sync.updateText(e.index, sync.sharedTexts[e.index].copy(text = txt))
        } else if (txt.isNotBlank()) {
            sync.pushText(TextElement(text = txt, x = e.canvasX, y = e.canvasY, colorArgb = penColor.copy(alpha = penAlpha).toArgb(), fontSize = textSize))
            haptic(Haptic.Confirm)
        }
        editing = null
        keyboard?.hide()
    }

    /** Top-most element under a canvas point. */
    fun hitTest(p: Offset): Pair<ElType, Int>? {
        val slack = 18f / viewScale
        var best: Triple<ElType, Int, Long>? = null
        fun take(t: ElType, i: Int, ts: Long) { if (best == null || ts > best!!.third) best = Triple(t, i, ts) }
        fun local(px: Float, py: Float, rot: Float): Offset {
            val r = Math.toRadians(-rot.toDouble()); val dx = p.x - px; val dy = p.y - py
            return Offset((dx * cos(r) - dy * sin(r)).toFloat(), (dx * sin(r) + dy * cos(r)).toFloat())
        }
        sync.sharedStickers.forEachIndexed { i, s ->
            val size = s.size * s.scale
            val l = local(s.x, s.y, s.rotation)
            val dx = l.x; val dy = l.y + size * 0.35f
            if (sqrt(dx * dx + dy * dy) < size * 0.6f + slack) take(ElType.STICKER, i, s.timestamp)
        }
        sync.sharedTexts.forEachIndexed { i, t ->
            val paint = CanvasRenderer.textPaintFor(t)
            val lines = t.text.split('\n')
            val w = lines.maxOf { paint.measureText(it) }
            val l = local(t.x, t.y, t.rotation)
            if (l.x in -slack..(w + slack) && l.y in (-paint.textSize - slack)..(paint.textSize * 1.2f * (lines.size - 1) + paint.textSize * 0.3f + slack))
                take(ElType.TEXT, i, t.timestamp)
        }
        sync.sharedImages.forEachIndexed { i, img ->
            val w = img.width * img.scale; val h = img.height * img.scale
            val l = local(img.x + w / 2, img.y + h / 2, img.rotation)
            if (abs(l.x) < w / 2 + slack && abs(l.y) < h / 2 + slack) take(ElType.IMAGE, i, img.timestamp)
        }
        return best?.let { it.first to it.second }
    }

    fun transformEl(el: Pair<ElType, Int>, pan: Offset, scale: Float, rot: Float) {
        val (type, i) = el
        when (type) {
            ElType.STICKER -> sync.sharedStickers.getOrNull(i)?.let {
                sync.updateSticker(i, it.copy(x = it.x + pan.x, y = it.y + pan.y, scale = (it.scale * scale).coerceIn(0.2f, 8f), rotation = it.rotation + rot))
            }
            ElType.TEXT -> sync.sharedTexts.getOrNull(i)?.let {
                sync.updateText(i, it.copy(x = it.x + pan.x, y = it.y + pan.y, scale = (it.scale * scale).coerceIn(0.2f, 8f), rotation = it.rotation + rot))
            }
            ElType.IMAGE -> sync.sharedImages.getOrNull(i)?.let {
                val ns = (it.scale * scale).coerceIn(0.1f, 8f)
                val cx = it.x + it.width * it.scale / 2 + pan.x; val cy = it.y + it.height * it.scale / 2 + pan.y
                sync.updateImage(i, it.copy(x = cx - it.width * ns / 2, y = cy - it.height * ns / 2, scale = ns, rotation = it.rotation + rot))
            }
        }
    }

    fun deleteEl(el: Pair<ElType, Int>) = when (el.first) {
        ElType.STICKER -> sync.deleteSticker(el.second)
        ElType.TEXT -> sync.deleteText(el.second)
        ElType.IMAGE -> sync.deleteImage(el.second)
    }

    fun resetView() { viewScale = 1f; viewOffset = Offset.Zero }

    // Generous bin zone with hysteresis so small finger jitter never cancels a delete.
    val binEnterPx = with(density) { 190.dp.toPx() }
    val binExitPx = with(density) { 250.dp.toPx() }
    fun updateBin(y: Float) {
        val fromBottom = screenH - y
        val now = if (overBin) fromBottom < binExitPx else fromBottom < binEnterPx
        if (now && !overBin) haptic(Haptic.Confirm)
        overBin = now
    }

    BackHandler {
        when {
            editing != null -> commitText()
            trayOpen -> trayOpen = false
            !chrome -> chrome = true
            else -> onDone()
        }
    }

    val bgColor = Color(sync.sharedBackgroundColor.intValue)
    val bgIsLight = bgColor.red * 0.3f + bgColor.green * 0.59f + bgColor.blue * 0.11f > 0.6f

    Box(Modifier.fillMaxSize().background(bgColor).onGloballyPositioned { screenW = it.size.width.toFloat(); screenH = it.size.height.toFloat() }) {

        // ═════════ Drawing surface (blur source for the glass chrome) ═════════
        Canvas(
            Modifier.fillMaxSize().haze(hazeState).background(bgColor)
                .pointerInput(sync, readOnly) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val activeTool = tool
                        val t0 = down.uptimeMillis
                        var tEnd = t0
                        var maxPtr = 1
                        var multi = false
                        var lastPos = down.position
                        var travel = 0f
                        val canEdit = !readOnly

                        if (editing != null) { commitText() }
                        if (trayOpen) trayOpen = false

                        // ── one-finger start ──
                        var grabbed: Pair<ElType, Int>? = null
                        val startCanvas = toCanvas(down.position)
                        when (activeTool) {
                            Tool.PEN -> if (canEdit) { livePoints.clear(); livePoints.add(startCanvas.toPoint()); redraw++ }
                            Tool.ERASER -> if (canEdit && !eraseWhole) { eraserPos = startCanvas; sync.erasePointsNear(startCanvas.x, startCanvas.y, eraserWidth / viewScale) }
                            Tool.MOVE, Tool.TEXT -> if (canEdit) {
                                grabbed = hitTest(startCanvas)
                            }
                        }

                        // ── multi-finger tracking ──
                        var prevCount = 0
                        var prevCentroid = Offset.Zero
                        var prevSpan = 0f
                        var prevAngle = 0f
                        var multiTravel = 0f
                        var multiScale = 1f
                        var threeStart: Offset? = null
                        var threeTravel = Offset.Zero

                        while (true) {
                            val ev = awaitPointerEvent()
                            tEnd = ev.changes.maxOf { it.uptimeMillis }
                            val pressed = ev.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break
                            maxPtr = maxOf(maxPtr, pressed.size)

                            if (maxPtr == 1) {
                                val ch = pressed[0]
                                val d = ch.position - lastPos
                                travel += d.getDistance(); lastPos = ch.position
                                val cp = toCanvas(ch.position)
                                when (activeTool) {
                                    Tool.PEN -> if (canEdit && livePoints.isNotEmpty()) {
                                        val lp = livePoints.last()
                                        val dx = cp.x - lp.x; val dy = cp.y - lp.y
                                        if (dx * dx + dy * dy > (1.5f / viewScale).let { it * it }) { livePoints.add(cp.toPoint()); redraw++ }
                                    }
                                    Tool.ERASER -> if (canEdit && !eraseWhole) {
                                        eraserPos = cp; sync.erasePointsNear(cp.x, cp.y, eraserWidth / viewScale)
                                    }
                                    Tool.MOVE, Tool.TEXT -> {
                                        val g = grabbed
                                        if (g != null) {
                                            // Small slop before a press becomes a drag, so taps stay taps.
                                            if (!draggingEl && travel > 10f) { draggingEl = true; haptic(Haptic.Tick) }
                                            if (draggingEl) {
                                                transformEl(g, d / viewScale, 1f, 0f)
                                                updateBin(ch.position.y)
                                            }
                                        } else if (activeTool == Tool.MOVE && settings.pinchZoom && viewScale != 1f) {
                                            viewOffset += d
                                        }
                                    }
                                }
                                ch.consume()
                                continue
                            }

                            // ── became a multi-finger gesture: cancel the single-finger action ──
                            val centroid = Offset(pressed.map { it.position.x }.average().toFloat(), pressed.map { it.position.y }.average().toFloat())
                            if (!multi) {
                                multi = true
                                livePoints.clear(); eraserPos = null; redraw++
                                // Two fingers on an item grab it in any tool (Instagram-story style).
                                if (canEdit && grabbed == null && pressed.size == 2) {
                                    grabbed = hitTest(toCanvas(centroid))
                                        ?: pressed.firstNotNullOfOrNull { hitTest(toCanvas(it.position)) }
                                }
                                if (grabbed != null && !draggingEl) { draggingEl = true; haptic(Haptic.Tick) }
                            }
                            if (pressed.size >= 3) {
                                if (threeStart == null) threeStart = centroid
                                threeTravel = centroid - threeStart!!
                            }
                            if (pressed.size >= 2) {
                                val a = pressed[0].position; val b = pressed[1].position
                                val span = (b - a).getDistance()
                                val angle = Math.toDegrees(atan2((b.y - a.y).toDouble(), (b.x - a.x).toDouble())).toFloat()
                                if (prevCount == pressed.size && maxPtr == 2) {
                                    val sf = if (prevSpan > 10f) span / prevSpan else 1f
                                    var rot = angle - prevAngle
                                    if (rot > 180) rot -= 360; if (rot < -180) rot += 360
                                    val pan = centroid - prevCentroid
                                    multiTravel += pan.getDistance(); multiScale *= sf
                                    val g = grabbed
                                    if (g != null) {
                                        transformEl(g, pan / viewScale, sf, rot)
                                        updateBin(centroid.y)
                                    } else if (settings.pinchZoom) {
                                        val ns = (viewScale * sf).coerceIn(0.4f, 5f)
                                        viewOffset = centroid - (centroid - viewOffset) * (ns / viewScale) + pan
                                        viewScale = ns
                                    }
                                }
                                prevSpan = span; prevAngle = angle
                            }
                            prevCentroid = centroid; prevCount = pressed.size
                            pressed.forEach { it.consume() }
                        }

                        val duration = tEnd - t0
                        if (!multi) {
                            val isTap = travel < 12f
                            when (activeTool) {
                                Tool.PEN -> if (canEdit && livePoints.isNotEmpty()) {
                                    val pts = if (isTap || livePoints.size == 1) listOf(livePoints.first(), livePoints.first()) else livePoints.toList()
                                    sync.pushStroke(DrawingStroke(colorArgb = penColor.copy(alpha = penAlpha).toArgb(), strokeWidth = penWidth / viewScale, path = pts))
                                    livePoints.clear(); redraw++
                                }
                                Tool.ERASER -> {
                                    if (canEdit && eraseWhole && isTap) {
                                        if (sync.eraseWholeStrokeNear(startCanvas.x, startCanvas.y, 36f / viewScale)) haptic(Haptic.Confirm)
                                    }
                                    eraserPos = null
                                }
                                Tool.MOVE, Tool.TEXT -> {
                                    val g = grabbed
                                    if (g != null) {
                                        if (draggingEl && overBin) { deleteEl(g); haptic(Haptic.Reject) }
                                        else if (!draggingEl && g.first == ElType.TEXT) {
                                            sync.sharedTexts.getOrNull(g.second)?.let { t ->
                                                editing = TextEdit(t.x, t.y, g.second, t.text)
                                            }
                                        }
                                    } else if (activeTool == Tool.TEXT && canEdit && isTap) {
                                        editing = TextEdit(startCanvas.x, startCanvas.y, null, "")
                                    }
                                }
                            }
                        } else if (grabbed != null && draggingEl && overBin) {
                            deleteEl(grabbed!!); haptic(Haptic.Reject)
                        } else {
                            val tap2 = maxPtr == 2 && duration < 320 && multiTravel < 30f && abs(multiScale - 1f) < 0.08f
                            if (tap2 && settings.twoFingerUndo && canEdit && grabbed == null) {
                                if (sync.canUndo.value) { sync.undoLast(); haptic(Haptic.Confirm) } else haptic(Haptic.Reject)
                            } else if (maxPtr >= 3) {
                                if (settings.threeFingerFocus && abs(threeTravel.y) > 120f) {
                                    chrome = threeTravel.y < 0; haptic(Haptic.Confirm)
                                } else if (settings.threeFingerRedo && canEdit && duration < 450 && threeTravel.getDistance() < 40f) {
                                    if (sync.canRedo.value) { sync.redoLast(); haptic(Haptic.Confirm) } else haptic(Haptic.Reject)
                                }
                            }
                        }
                        draggingEl = false; overBin = false
                    }
                }
        ) {
            redraw // subscribe
            val nc = drawContext.canvas.nativeCanvas
            nc.save()
            nc.translate(viewOffset.x, viewOffset.y)
            nc.scale(viewScale, viewScale)
            CanvasRenderer.drawOnCanvas(
                nc, null, sync.sharedStrokes, sync.sharedStickers, sync.sharedTexts, sync.sharedImages,
                strokePaths = sync.strokePaths, decode = false
            )
            if (livePoints.isNotEmpty()) {
                livePaint.color = penColor.copy(alpha = penAlpha).toArgb()
                livePaint.strokeWidth = penWidth / viewScale
                if (livePoints.size == 1) {
                    livePaint.style = android.graphics.Paint.Style.FILL
                    nc.drawCircle(livePoints[0].x, livePoints[0].y, livePaint.strokeWidth / 2, livePaint)
                    livePaint.style = android.graphics.Paint.Style.STROKE
                } else nc.drawPath(CanvasRenderer.buildPath(livePoints, settings.smoothStrokes), livePaint)
            }
            eraserPos?.let {
                guidePaint.color = (if (bgIsLight) Color.Black else Color.White).copy(alpha = 0.5f).toArgb()
                guidePaint.strokeWidth = 2f / viewScale
                nc.drawCircle(it.x, it.y, eraserWidth / viewScale, guidePaint)
            }
            if (viewScale != 1f || viewOffset != Offset.Zero) {
                // Outline of the actual wallpaper area
                guidePaint.color = (if (bgIsLight) Color.Black else Color.White).copy(alpha = 0.25f).toArgb()
                guidePaint.strokeWidth = 2f / viewScale
                nc.drawRoundRect(0f, 0f, size.width, size.height, 40f, 40f, guidePaint)
            }
            nc.restore()
        }

        // ═════════ Inline text editor ═════════
        editing?.let { e ->
            val fr = remember { FocusRequester() }
            val fontPx = (if (e.index != null) sync.sharedTexts.getOrNull(e.index)?.let { it.fontSize * it.scale } else null) ?: textSize
            val sx = e.canvasX * viewScale + viewOffset.x
            val sy = e.canvasY * viewScale + viewOffset.y - fontPx * viewScale
            val color = if (e.index != null) sync.sharedTexts.getOrNull(e.index)?.let { Color(it.colorArgb) } ?: penColor else penColor.copy(alpha = penAlpha)
            BasicTextField(
                value = e.value,
                onValueChange = { editing = e.copy(value = it) },
                textStyle = TextStyle(color = color, fontSize = with(density) { (fontPx * viewScale).toSp() }, fontWeight = FontWeight.Medium),
                cursorBrush = SolidColor(c.accent),
                modifier = Modifier.offset { IntOffset(sx.toInt(), sy.toInt()) }
                    .widthIn(min = 40.dp, max = 320.dp)
                    .border(1.dp, c.accent.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 4.dp)
                    .focusRequester(fr)
            )
            LaunchedEffect(e.index, e.canvasX, e.canvasY) { fr.requestFocus(); keyboard?.show() }
        }

        // Floating emoji while dragging from the sheet
        dragEmoji?.let {
            Text(it, fontSize = 48.sp, modifier = Modifier.offset { IntOffset(dragEmojiPos.x.toInt() - 70, dragEmojiPos.y.toInt() - 90) })
        }

        // ═════════ Glass chrome ═════════
        CompositionLocalProvider(LocalHazeState provides hazeState) {
            AnimatedVisibility(
                chrome && !draggingEl, enter = fadeIn() + slideInVertically { -it }, exit = fadeOut() + slideOutVertically { -it },
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, { if (editing != null) commitText(); onDone() }, strong = true, contentDescription = "Back")
                    Spacer(Modifier.width(10.dp))
                    val room = rooms.firstOrNull { it.roomId == sync.roomId }
                    Row(
                        Modifier.weight(1f, fill = false).height(46.dp).glass(CircleShape, strong = true)
                            .clickable(enabled = rooms.size > 1) { haptic(Haptic.Tick); showRooms = true }
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(if (room?.roomType == "group") Icons.Rounded.Group else Icons.Rounded.Favorite, null, tint = c.accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(room?.displayName ?: "Canvas", color = c.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (readOnly) { Spacer(Modifier.width(6.dp)); Icon(Icons.Rounded.Visibility, null, tint = c.textMuted, modifier = Modifier.size(16.dp)) }
                        if (rooms.size > 1) Icon(Icons.Rounded.UnfoldMore, null, tint = c.textMuted, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.weight(0.01f).widthIn(min = 10.dp))
                    Row(Modifier.glass(CircleShape, strong = true).padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (!readOnly) {
                            BareIconButton(Icons.AutoMirrored.Rounded.Undo, { sync.undoLast() }, enabled = sync.canUndo.value, contentDescription = "Undo")
                            BareIconButton(Icons.AutoMirrored.Rounded.Redo, { sync.redoLast() }, enabled = sync.canRedo.value, contentDescription = "Redo")
                        }
                        BareIconButton(Icons.Rounded.MoreHoriz, { showMore = true }, contentDescription = "More")
                    }
                }
            }

            // Zoom chip + loading
            Column(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 66.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedVisibility(!sync.isLoaded.value, enter = fadeIn(), exit = fadeOut()) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = c.accent, strokeWidth = 2.dp)
                }
                AnimatedVisibility(chrome && (viewScale != 1f || viewOffset != Offset.Zero), enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                    Row(
                        Modifier.glass(CircleShape, strong = true).clickable { haptic(Haptic.Tick); resetView() }.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.ZoomOutMap, null, tint = c.textPrimary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("${(viewScale * 100).toInt()}%", color = c.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Bottom: tray + dock
            if (!readOnly) Column(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AnimatedVisibility(
                    chrome && trayOpen && !draggingEl,
                    enter = fadeIn(tween(160)) + scaleIn(spring(0.7f, 500f), 0.85f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)),
                    exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.9f)
                ) {
                    Column(
                        Modifier.padding(horizontal = 14.dp).padding(bottom = 10.dp).widthIn(max = 420.dp)
                            .glass(RoundedCornerShape(28.dp), strong = true).padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        when (tool) {
                            Tool.PEN, Tool.TEXT -> {
                                ColorRow(penColor) { penColor = it; haptic(Haptic.Tick) }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (tool == Tool.PEN) SizeDots(WidthSteps, penWidth, penColor) { penWidth = it; settings.defaultBrush = it }
                                    else SizeDots(TextSteps, textSize, penColor, icon = Icons.Rounded.FormatSize) { textSize = it }
                                    Spacer(Modifier.width(12.dp))
                                    Icon(Icons.Rounded.Opacity, null, tint = c.textMuted, modifier = Modifier.size(18.dp))
                                    Slider(
                                        penAlpha, { penAlpha = it }, valueRange = 0.1f..1f, modifier = Modifier.weight(1f).height(28.dp),
                                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = c.accent, inactiveTrackColor = c.textPrimary.copy(alpha = 0.15f))
                                    )
                                }
                            }
                            Tool.ERASER -> Row(verticalAlignment = Alignment.CenterVertically) {
                                BareIconButton(Icons.Rounded.Gesture, { eraseWhole = false }, active = !eraseWhole, size = 40.dp)
                                Spacer(Modifier.width(6.dp))
                                BareIconButton(Icons.Rounded.TouchApp, { eraseWhole = true }, active = eraseWhole, size = 40.dp)
                                Spacer(Modifier.width(14.dp))
                                if (!eraseWhole) SizeDots(listOf(14f, 28f, 48f, 72f), eraserWidth, c.textPrimary) { eraserWidth = it }
                            }
                            Tool.MOVE -> {}
                        }
                    }
                }

                AnimatedVisibility(chrome && !draggingEl, enter = fadeIn() + slideInVertically { it }, exit = fadeOut() + slideOutVertically { it }) {
                    Row(
                        Modifier.glass(CircleShape, strong = true).padding(5.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically
                    ) {
                        @Composable
                        fun ToolBtn(t: Tool, icon: ImageVector, desc: String) = BareIconButton(icon, {
                            if (editing != null) commitText()
                            if (tool == t) trayOpen = !trayOpen && t != Tool.MOVE else { tool = t; trayOpen = false }
                        }, active = tool == t, size = 48.dp, contentDescription = desc)
                        ToolBtn(Tool.MOVE, Icons.Rounded.PanTool, "Move")
                        ToolBtn(Tool.PEN, Icons.Rounded.Brush, "Draw")
                        ToolBtn(Tool.ERASER, Icons.Rounded.AutoFixNormal, "Erase")
                        ToolBtn(Tool.TEXT, Icons.Rounded.Title, "Text")
                        BareIconButton(Icons.Rounded.AddReaction, { if (editing != null) commitText(); showStickers = true }, size = 48.dp, contentDescription = "Sticker")
                        BareIconButton(Icons.Rounded.AddPhotoAlternate, { if (editing != null) commitText(); imagePicker.launch("image/*") }, size = 48.dp, contentDescription = "Image")
                    }
                }
            }

            // Dustbin while dragging an element
            AnimatedVisibility(
                draggingEl, modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 28.dp),
                enter = fadeIn() + scaleIn(spring(0.5f, 400f)), exit = fadeOut() + scaleOut()
            ) {
                val s by animateFloatAsState(if (overBin) 1.25f else 1f, spring(0.5f, 500f), label = "bin")
                Box(
                    Modifier.size((64 * s).dp).glass(CircleShape, strong = true, tint = if (overBin) ErrorRed.copy(alpha = 0.6f) else null),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Rounded.DeleteOutline, "Delete", tint = if (overBin) Color.White else c.textPrimary, modifier = Modifier.size((28 * s).dp)) }
            }

            if (uploading) Box(Modifier.align(Alignment.Center).size(72.dp).glass(RoundedCornerShape(24.dp), strong = true), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(28.dp), color = c.accent, strokeWidth = 2.5.dp)
            }

            // ═════════ Sheets ═════════
            GlassSheet(showStickers, { showStickers = false; dragEmoji = null }) {
                LazyVerticalGrid(
                    GridCells.Fixed(8), Modifier.fillMaxWidth().heightIn(max = 300.dp).alpha(if (dragEmoji != null) 0.15f else 1f),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    items(Emojis) { e ->
                        var cellPos by remember { mutableStateOf(Offset.Zero) }
                        Box(
                            Modifier.aspectRatio(1f).clip(RoundedCornerShape(12.dp))
                                .onGloballyPositioned { cellPos = it.positionInRoot() }
                                .pointerInput(e) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { o -> dragEmoji = e; dragEmojiPos = cellPos + o; haptic(Haptic.Tick) },
                                        onDrag = { ch, amt -> ch.consume(); dragEmojiPos += amt },
                                        onDragEnd = {
                                            val p = toCanvas(dragEmojiPos)
                                            sync.pushSticker(StickerElement(emoji = e, x = p.x, y = p.y + 30f, size = 110f / viewScale))
                                            haptic(Haptic.Confirm); dragEmoji = null; showStickers = false; tool = Tool.MOVE
                                        },
                                        onDragCancel = { dragEmoji = null }
                                    )
                                }
                                .clickable {
                                    val p = visibleCenter()
                                    sync.pushSticker(StickerElement(emoji = e, x = p.x, y = p.y, size = 110f / viewScale))
                                    haptic(Haptic.Confirm); showStickers = false; tool = Tool.MOVE
                                },
                            contentAlignment = Alignment.Center
                        ) { Text(e, fontSize = 26.sp) }
                    }
                }
            }

            GlassSheet(showRooms, { showRooms = false }) {
                Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    rooms.forEach { r ->
                        val current = r.roomId == sync.roomId
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                                .background(if (current) c.accent.copy(alpha = 0.16f) else c.textPrimary.copy(alpha = 0.05f))
                                .clickable { haptic(Haptic.Confirm); showRooms = false; if (!current) { if (editing != null) commitText(); resetView(); onSwitchRoom(r) } }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(if (r.roomType == "group") Icons.Rounded.Group else Icons.Rounded.Favorite, null, tint = if (current) c.accent else c.textSecondary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(14.dp))
                            Text(r.displayName, color = c.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (r.roomId == homeRoomId) Icon(Icons.Rounded.Home, null, tint = c.textMuted, modifier = Modifier.size(16.dp))
                            if (r.roomId == lockRoomId) Icon(Icons.Rounded.Lock, null, tint = c.textMuted, modifier = Modifier.padding(start = 6.dp).size(16.dp))
                            if (current) Icon(Icons.Rounded.Check, null, tint = c.accent, modifier = Modifier.padding(start = 8.dp).size(20.dp))
                        }
                    }
                }
            }

            GlassSheet(showMore, { showMore = false }) {
                Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Background swatches
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        BgColors.chunked(6).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                row.forEach { col ->
                                    val sel = col.toArgb() == sync.sharedBackgroundColor.intValue
                                    Box(
                                        Modifier.size(42.dp).clip(CircleShape).background(col)
                                            .border(if (sel) 3.dp else 1.dp, if (sel) c.accent else c.glassBorder, CircleShape)
                                            .clickable(enabled = !readOnly) { haptic(Haptic.Tick); sync.setBackgroundColor(col.toArgb()) }
                                    )
                                }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SheetAction(Icons.Rounded.Home, "Home", { onSetHome(); showMore = false }, Modifier.weight(1f), active = sync.roomId == homeRoomId)
                        SheetAction(Icons.Rounded.Lock, "Lock", { onSetLock(); showMore = false }, Modifier.weight(1f), active = sync.roomId == lockRoomId)
                        SheetAction(Icons.Rounded.CenterFocusStrong, "Fit", { resetView(); showMore = false }, Modifier.weight(1f))
                        if (!readOnly) SheetAction(Icons.Rounded.DeleteSweep, "Clear", { showMore = false; confirmClear = true }, Modifier.weight(1f), color = ErrorRed)
                    }
                }
            }
        }
    }

    if (confirmClear) TimedConfirmDialog(
        title = "Clear canvas?", message = "", confirmLabel = "Clear", waitSeconds = 3,
        onConfirm = { confirmClear = false; sync.clearCanvas(); haptic(Haptic.Reject) },
        onDismiss = { confirmClear = false }
    )
}

@Composable
private fun ColorRow(selected: Color, onPick: (Color) -> Unit) {
    val c = LocalWallpaperColors.current
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PenColors.forEach { col ->
            val sel = col == selected
            val s by animateDpAsState(if (sel) 34.dp else 28.dp, spring(0.6f, 600f), label = "cs")
            Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(s).clip(CircleShape).background(col)
                        .border(if (sel) 3.dp else 1.dp, if (sel) c.textPrimary else c.glassBorder, CircleShape)
                        .clickable { onPick(col) }
                )
            }
        }
    }
}

@Composable
private fun SizeDots(steps: List<Float>, value: Float, color: Color, icon: ImageVector? = null, onPick: (Float) -> Unit) {
    val c = LocalWallpaperColors.current
    val haptic = rememberHaptics()
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) Icon(icon, null, tint = c.textMuted, modifier = Modifier.size(18.dp))
        steps.forEachIndexed { i, w ->
            val sel = abs(value - w) < 0.5f
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(if (sel) c.accent.copy(alpha = 0.2f) else Color.Transparent)
                    .clickable { haptic(Haptic.Tick); onPick(w) },
                contentAlignment = Alignment.Center
            ) { Box(Modifier.size((6 + i * 5).dp).clip(CircleShape).background(if (sel) color else c.textMuted)) }
        }
    }
}
