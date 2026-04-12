package com.alancruse.wallpaper.ui

import android.net.Uri
import android.os.Build
import android.view.HapticFeedbackConstants
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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alancruse.wallpaper.canvas.*
import com.alancruse.wallpaper.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.sqrt

// Tools: NONE = default seamless drag mode
private enum class CanvasTool { NONE, SKETCH, ERASER, STICKER, TEXT_TOOL, IMAGE_TOOL }
private enum class SelectedType { STICKER, TEXT, IMAGE }
private enum class EraserMode { POINT, STROKE }

private sealed class CanvasLayer(val timestamp: Long) {
    data class StrokeLayer(val stroke: DrawingStroke, val idx: Int) : CanvasLayer(stroke.timestamp)
    data class StickerLayer(val sticker: StickerElement, val idx: Int) : CanvasLayer(sticker.timestamp)
    data class TextLayer(val text: TextElement, val idx: Int) : CanvasLayer(text.timestamp)
    data class ImageLayer(val image: ImageElement, val idx: Int) : CanvasLayer(image.timestamp)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveCanvasScreen(
    syncManager: CanvasSyncManager,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val screenHeightPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val keyboardController = LocalSoftwareKeyboardController.current

    val remoteStrokes = syncManager.sharedStrokes
    val remoteStickers = syncManager.sharedStickers
    val remoteTexts = syncManager.sharedTexts
    val remoteImages = syncManager.sharedImages
    val bgColorArgb by syncManager.sharedBackgroundColor

    // Bitmap cache
    val bitmapCache = remember { mutableMapOf<String, android.graphics.Bitmap>() }
    LaunchedEffect(remoteImages.toList()) {
        remoteImages.forEach { img ->
            if (img.imageBase64.isNotEmpty() && img.imageBase64 !in bitmapCache) {
                try {
                    val bytes = android.util.Base64.decode(img.imageBase64, android.util.Base64.NO_WRAP)
                    val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bmp != null) bitmapCache[img.imageBase64] = bmp
                } catch (_: Exception) {}
            }
        }
        bitmapCache.keys.retainAll(remoteImages.map { it.imageBase64 }.toSet())
    }

    // ── Tool state ──
    var activeTool by remember { mutableStateOf(CanvasTool.NONE) }
    var currentPath by remember { mutableStateOf(emptyList<Point>()) }
    var eraserMode by remember { mutableStateOf(EraserMode.POINT) }

    // Color & size
    val presetColors = listOf(
        Color.White, Color.Black, Color(0xFFFF0000), Color(0xFFFF6B00),
        Color(0xFFFFD600), Color(0xFF00E676), Color(0xFF2979FF), Color(0xFF7C4DFF),
        Color(0xFFE040FB), Color(0xFFFF4081), ElectricViolet, CyanAccent
    )
    var selectedColor by remember { mutableStateOf(Color.White) }
    var selectedOpacity by remember { mutableStateOf(1f) }
    var selectedWidth by remember { mutableStateOf(8f) }
    var eraserWidth by remember { mutableStateOf(25f) }
    var showColorPicker by remember { mutableStateOf(false) }

    // Eraser preview
    var eraserPreviewPos by remember { mutableStateOf(Offset(screenWidthPx / 2, screenHeightPx / 2)) }

    // Sticker — drag-from-panel
    var showEmojiPanel by remember { mutableStateOf(false) }
    var draggingEmoji by remember { mutableStateOf<String?>(null) }
    var emojiDragPos by remember { mutableStateOf(Offset.Zero) }
    val emojiList = listOf(
        "❤️", "💜", "💙", "💚", "💛", "🧡", "🤍", "🖤",
        "⭐", "✨", "🌟", "💫", "🔥", "🌈", "🦋", "🌸",
        "🌺", "🌻", "🍀", "🌙", "☀️", "❄️", "💎", "👑",
        "🎵", "🎶", "💕", "💗", "💖", "💝", "😊", "😍",
        "🥰", "😘", "🤗", "😎", "🥳", "🎉", "🎈", "🎨"
    )

    // Inline text editing
    var isEditingText by remember { mutableStateOf(false) }
    var inlineText by remember { mutableStateOf("") }
    var textEditX by remember { mutableStateOf(200f) }
    var textEditY by remember { mutableStateOf(500f) }
    val textFocusRequester = remember { FocusRequester() }

    // Background color
    var showBgColorPicker by remember { mutableStateOf(false) }
    val bgCyclePresets = remember {
        listOf(
            DeepNavy, DarkSurface, Color.Black,
            Color(0xFF1A1A2E), Color(0xFF16213E), Color(0xFF0F3460),
            Color(0xFF2C003E), Color(0xFF1B1B2F), Color(0xFF162447),
            Color(0xFF1F4068), Color(0xFF203040), Color(0xFF2D4059),
            Color.White, Color(0xFFF5F5F5), Color(0xFFE8E8E8),
            Color(0xFFFFE6E6), Color(0xFFE6F0FF), Color(0xFFE6FFE6)
        )
    }
    var bgCycleIndex by remember { mutableStateOf(0) }

    // Image
    var isUploadingImage by remember { mutableStateOf(false) }
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isUploadingImage = true
            syncManager.pushImage(context, uri, 100f, 300f, 300f, 300f) { success, errorMsg ->
                isUploadingImage = false
                if (!success) Toast.makeText(context, errorMsg ?: "Upload failed", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ── Seamless drag state (default NONE mode) ──
    var dragType by remember { mutableStateOf<SelectedType?>(null) }
    var dragIndex by remember { mutableStateOf(-1) }
    var isDraggingElement by remember { mutableStateOf(false) }
    var dragCurrentY by remember { mutableStateOf(0f) }
    var isInDustbin by remember { mutableStateOf(false) }
    val dustbinThreshold = screenHeightPx * 0.82f
    val grabRadius = 120f // reasonable range — must be close to element

    // Dustbin animation
    val dustbinVisible = isDraggingElement && dragCurrentY > screenHeightPx * 0.65f
    val dustbinScale by animateFloatAsState(
        targetValue = if (isInDustbin) 1.4f else if (dustbinVisible) 1f else 0.3f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f), label = "ds"
    )
    val dustbinColor by animateColorAsState(
        targetValue = if (isInDustbin) WarmRose else TextMuted,
        animationSpec = spring(stiffness = 600f), label = "dc"
    )

    fun haptic(constant: Int = HapticFeedbackConstants.CLOCK_TICK) {
        view.performHapticFeedback(constant)
    }

    /** Find the nearest element within grabRadius. Returns (type, index) or null. */
    fun findNearest(x: Float, y: Float): Pair<SelectedType, Int>? {
        var bestDist = Float.MAX_VALUE; var bestType: SelectedType? = null; var bestIdx = -1
        remoteStickers.forEachIndexed { i, s ->
            val d = dist(x, y, s.x, s.y)
            if (d < grabRadius && d < bestDist) { bestDist = d; bestType = SelectedType.STICKER; bestIdx = i }
        }
        remoteTexts.forEachIndexed { i, t ->
            val d = dist(x, y, t.x, t.y)
            if (d < grabRadius && d < bestDist) { bestDist = d; bestType = SelectedType.TEXT; bestIdx = i }
        }
        remoteImages.forEachIndexed { i, img ->
            val cx = img.x + img.width / 2f; val cy = img.y + img.height / 2f
            val d = dist(x, y, cx, cy)
            if (d < 200f && d < bestDist) { bestDist = d; bestType = SelectedType.IMAGE; bestIdx = i }
        }
        return if (bestType != null) bestType!! to bestIdx else null
    }

    /** Commit inline text to firebase and exit edit mode */
    fun commitInlineText() {
        if (inlineText.isNotBlank()) {
            syncManager.pushText(TextElement(
                text = inlineText, x = textEditX, y = textEditY,
                colorArgb = selectedColor.copy(alpha = selectedOpacity).toArgb(), fontSize = 48f
            ))
            haptic(HapticFeedbackConstants.CONFIRM)
        }
        inlineText = ""
        isEditingText = false
        keyboardController?.hide()
    }

    BackHandler {
        when {
            isEditingText -> commitInlineText()
            showEmojiPanel -> { showEmojiPanel = false; haptic() }
            activeTool != CanvasTool.NONE -> {
                haptic(); activeTool = CanvasTool.NONE
                showColorPicker = false; showEmojiPanel = false
            }
            else -> onDone()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(bgColorArgb))) {

        // ═══════════════════════════════════════════
        // ██  CANVAS  ██
        // ═══════════════════════════════════════════
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    when (activeTool) {
                        CanvasTool.SKETCH -> Modifier
                            .pointerInput("sketch_tap", selectedColor, selectedWidth, selectedOpacity) {
                                detectTapGestures { offset ->
                                    if (isEditingText) { commitInlineText(); return@detectTapGestures }
                                    haptic()
                                    syncManager.pushStroke(DrawingStroke(
                                        colorArgb = selectedColor.copy(alpha = selectedOpacity).toArgb(),
                                        strokeWidth = selectedWidth,
                                        path = listOf(offset.toPoint(), offset.toPoint())
                                    ))
                                }
                            }
                            .pointerInput("sketch_drag", selectedColor, selectedWidth, selectedOpacity) {
                                detectDragGestures(
                                    onDragStart = { offset -> currentPath = listOf(offset.toPoint()) },
                                    onDrag = { change, _ -> change.consume(); currentPath = currentPath + change.position.toPoint() },
                                    onDragEnd = {
                                        if (currentPath.isNotEmpty()) {
                                            syncManager.pushStroke(DrawingStroke(
                                                colorArgb = selectedColor.copy(alpha = selectedOpacity).toArgb(),
                                                strokeWidth = selectedWidth, path = currentPath
                                            ))
                                            currentPath = emptyList()
                                        }
                                    }
                                )
                            }

                        CanvasTool.ERASER -> when (eraserMode) {
                            EraserMode.POINT -> Modifier.pointerInput(eraserWidth) {
                                detectDragGestures { change, _ ->
                                    change.consume()
                                    eraserPreviewPos = change.position
                                    syncManager.erasePointsNear(change.position.x, change.position.y, eraserWidth)
                                }
                            }
                            EraserMode.STROKE -> Modifier.pointerInput("stroke_eraser") {
                                detectTapGestures { offset ->
                                    eraserPreviewPos = offset
                                    syncManager.eraseWholeStrokeNear(offset.x, offset.y, 40f)
                                    haptic(HapticFeedbackConstants.CONFIRM)
                                }
                            }
                        }

                        CanvasTool.STICKER -> Modifier // Gestures handled by emoji panel overlay
                        CanvasTool.TEXT_TOOL -> Modifier.pointerInput("text_tap") {
                            detectTapGestures { offset ->
                                if (isEditingText) {
                                    commitInlineText()
                                } else {
                                    textEditX = offset.x
                                    textEditY = offset.y
                                    isEditingText = true
                                    inlineText = ""
                                    haptic()
                                }
                            }
                        }
                        CanvasTool.IMAGE_TOOL -> Modifier
                        // ── DEFAULT (NONE): drag + pinch-to-zoom/rotate on elements ──
                        CanvasTool.NONE -> Modifier
                            .pointerInput("none_move") {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    if (isEditingText) { commitInlineText() }
                                    val startPos = down.position
                                    val found = findNearest(startPos.x, startPos.y)

                                    if (found != null) {
                                        // ── Drag + pinch/rotate element ──
                                        dragType = found.first
                                        dragIndex = found.second
                                        isDraggingElement = true
                                        haptic()

                                        var prevCentroid = startPos
                                        var prevSpan = 0f
                                        var prevAngle = 0f
                                        var isMultiTouch = false

                                        do {
                                            val event = awaitPointerEvent()
                                            val activePointers = event.changes.filter { it.pressed }
                                            if (activePointers.isEmpty()) break

                                            // Consume all changes
                                            activePointers.forEach { it.consume() }

                                            // Calculate centroid
                                            val centroid = Offset(
                                                activePointers.map { it.position.x }.average().toFloat(),
                                                activePointers.map { it.position.y }.average().toFloat()
                                            )

                                            if (activePointers.size >= 2) {
                                                // Multi-touch: pinch + rotate
                                                val dx = activePointers[1].position.x - activePointers[0].position.x
                                                val dy = activePointers[1].position.y - activePointers[0].position.y
                                                val span = kotlin.math.sqrt(dx * dx + dy * dy)
                                                val angle = kotlin.math.atan2(dy, dx) * (180f / Math.PI.toFloat())

                                                if (!isMultiTouch) {
                                                    // First frame of multi-touch — initialize
                                                    isMultiTouch = true
                                                    prevSpan = span
                                                    prevAngle = angle
                                                    prevCentroid = centroid
                                                } else {
                                                    // Calculate deltas
                                                    val scaleFactor = if (prevSpan > 10f) span / prevSpan else 1f
                                                    val rotationDelta = angle - prevAngle
                                                    val panDelta = centroid - prevCentroid

                                                    when (dragType) {
                                                        SelectedType.STICKER -> {
                                                            if (dragIndex in remoteStickers.indices) {
                                                                val s = remoteStickers[dragIndex]
                                                                syncManager.updateSticker(dragIndex, s.copy(
                                                                    x = s.x + panDelta.x,
                                                                    y = s.y + panDelta.y,
                                                                    scale = (s.scale * scaleFactor).coerceIn(0.2f, 5f),
                                                                    rotation = s.rotation + rotationDelta
                                                                ))
                                                            }
                                                        }
                                                        SelectedType.TEXT -> {
                                                            if (dragIndex in remoteTexts.indices) {
                                                                val t = remoteTexts[dragIndex]
                                                                syncManager.updateText(dragIndex, t.copy(
                                                                    x = t.x + panDelta.x,
                                                                    y = t.y + panDelta.y,
                                                                    scale = (t.scale * scaleFactor).coerceIn(0.2f, 5f),
                                                                    rotation = t.rotation + rotationDelta
                                                                ))
                                                            }
                                                        }
                                                        SelectedType.IMAGE -> {
                                                            if (dragIndex in remoteImages.indices) {
                                                                val img = remoteImages[dragIndex]
                                                                syncManager.updateImage(dragIndex, img.copy(
                                                                    x = img.x + panDelta.x,
                                                                    y = img.y + panDelta.y,
                                                                    scale = (img.scale * scaleFactor).coerceIn(0.2f, 5f),
                                                                    rotation = img.rotation + rotationDelta
                                                                ))
                                                            }
                                                        }
                                                        else -> {}
                                                    }

                                                    prevSpan = span
                                                    prevAngle = angle
                                                    prevCentroid = centroid
                                                }
                                            } else {
                                                // Single touch: just drag
                                                if (isMultiTouch) {
                                                    // Transitioned back to single finger, reset
                                                    isMultiTouch = false
                                                    prevCentroid = centroid
                                                }
                                                val delta = centroid - prevCentroid
                                                prevCentroid = centroid

                                                var newY = 0f
                                                when (dragType) {
                                                    SelectedType.STICKER -> {
                                                        if (dragIndex in remoteStickers.indices) {
                                                            val s = remoteStickers[dragIndex]
                                                            syncManager.updateSticker(dragIndex, s.copy(x = s.x + delta.x, y = s.y + delta.y))
                                                            newY = s.y + delta.y
                                                        }
                                                    }
                                                    SelectedType.TEXT -> {
                                                        if (dragIndex in remoteTexts.indices) {
                                                            val t = remoteTexts[dragIndex]
                                                            syncManager.updateText(dragIndex, t.copy(x = t.x + delta.x, y = t.y + delta.y))
                                                            newY = t.y + delta.y
                                                        }
                                                    }
                                                    SelectedType.IMAGE -> {
                                                        if (dragIndex in remoteImages.indices) {
                                                            val img = remoteImages[dragIndex]
                                                            syncManager.updateImage(dragIndex, img.copy(x = img.x + delta.x, y = img.y + delta.y))
                                                            newY = img.y + delta.y + img.height / 2f
                                                        }
                                                    }
                                                    else -> {}
                                                }
                                                dragCurrentY = newY
                                                val wasInDustbin = isInDustbin
                                                isInDustbin = newY > dustbinThreshold
                                                if (isInDustbin && !wasInDustbin) haptic(HapticFeedbackConstants.CONFIRM)
                                                if (!isInDustbin && wasInDustbin) haptic(HapticFeedbackConstants.CLOCK_TICK)
                                            }
                                        } while (event.changes.any { it.pressed })

                                        // Gesture ended — dustbin check
                                        if (isInDustbin && dragType != null && dragIndex >= 0) {
                                            haptic(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS)
                                            when (dragType) {
                                                SelectedType.STICKER -> syncManager.deleteSticker(dragIndex)
                                                SelectedType.TEXT -> syncManager.deleteText(dragIndex)
                                                SelectedType.IMAGE -> syncManager.deleteImage(dragIndex)
                                                else -> {}
                                            }
                                        }
                                        isDraggingElement = false; isInDustbin = false; dragType = null; dragIndex = -1
                                    } else {
                                        // ── Empty area: long-press to cycle background ──
                                        val startTime = System.currentTimeMillis()
                                        var bgChanged = false
                                        do {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull() ?: break
                                            if (change.pressed) {
                                                val dx = change.position.x - startPos.x
                                                val dy = change.position.y - startPos.y
                                                if (dx * dx + dy * dy > 900f) break // finger moved too far
                                                if (!bgChanged && System.currentTimeMillis() - startTime >= 3000L) {
                                                    bgCycleIndex = (bgCycleIndex + 1) % bgCyclePresets.size
                                                    syncManager.setBackgroundColor(bgCyclePresets[bgCycleIndex].toArgb())
                                                    haptic(HapticFeedbackConstants.CONFIRM)
                                                    bgChanged = true
                                                }
                                            }
                                        } while (event.changes.any { it.pressed })
                                    }
                                }
                            }
                    }
                )
        ) {
            // ── Render layers chronologically ──
            val layers = buildList {
                remoteStrokes.forEachIndexed { i, s -> add(CanvasLayer.StrokeLayer(s, i)) }
                remoteStickers.forEachIndexed { i, s -> add(CanvasLayer.StickerLayer(s, i)) }
                remoteTexts.forEachIndexed { i, t -> add(CanvasLayer.TextLayer(t, i)) }
                remoteImages.forEachIndexed { i, img -> add(CanvasLayer.ImageLayer(img, i)) }
            }.sortedBy { it.timestamp }

            val nc = drawContext.canvas.nativeCanvas

            for (layer in layers) {
                when (layer) {
                    is CanvasLayer.StrokeLayer -> {
                        val s = layer.stroke
                        if (s.path.isNotEmpty()) {
                            val p = Path().apply {
                                moveTo(s.path.first().x, s.path.first().y)
                                for (i in 1 until s.path.size) lineTo(s.path[i].x, s.path[i].y)
                            }
                            drawPath(p, Color(s.colorArgb), style = Stroke(width = s.strokeWidth, cap = StrokeCap.Round))
                            if (s.path.size <= 2 && s.path.first().x == s.path.last().x && s.path.first().y == s.path.last().y) {
                                drawCircle(Color(s.colorArgb), radius = s.strokeWidth / 2f, center = Offset(s.path.first().x, s.path.first().y))
                            }
                        }
                    }
                    is CanvasLayer.StickerLayer -> {
                        val st = layer.sticker
                        val paint = android.graphics.Paint().apply {
                            textAlign = android.graphics.Paint.Align.CENTER; isAntiAlias = true; textSize = st.size * st.scale
                        }
                        nc.save(); nc.rotate(st.rotation, st.x, st.y)
                        nc.drawText(st.emoji, st.x, st.y, paint); nc.restore()
                    }
                    is CanvasLayer.TextLayer -> {
                        val t = layer.text
                        val paint = android.graphics.Paint().apply {
                            color = t.colorArgb; textSize = t.fontSize * t.scale; isAntiAlias = true
                            textAlign = android.graphics.Paint.Align.LEFT
                        }
                        nc.save(); nc.rotate(t.rotation, t.x, t.y)
                        nc.drawText(t.text, t.x, t.y, paint); nc.restore()
                    }
                    is CanvasLayer.ImageLayer -> {
                        val img = layer.image
                        if (img.imageBase64.isEmpty()) continue
                        val bitmap = bitmapCache[img.imageBase64] ?: continue
                        val w = (img.width * img.scale).toInt().coerceAtLeast(1)
                        val h = (img.height * img.scale).toInt().coerceAtLeast(1)
                        val scaled = android.graphics.Bitmap.createScaledBitmap(bitmap, w, h, true)
                        nc.save(); nc.rotate(img.rotation, img.x + w / 2f, img.y + h / 2f)
                        nc.drawBitmap(scaled, img.x, img.y, null); nc.restore()
                    }
                }
            }

            // Live stroke preview
            if (currentPath.isNotEmpty()) {
                val lp = Path().apply {
                    moveTo(currentPath.first().x, currentPath.first().y)
                    for (i in 1 until currentPath.size) lineTo(currentPath[i].x, currentPath[i].y)
                }
                drawPath(lp, selectedColor.copy(alpha = selectedOpacity), style = Stroke(selectedWidth, cap = StrokeCap.Round))
            }

            // Eraser circle preview
            if (activeTool == CanvasTool.ERASER && eraserMode == EraserMode.POINT) {
                drawCircle(Color.White.copy(alpha = 0.3f), radius = eraserWidth, center = eraserPreviewPos, style = Stroke(2f))
            }
        }

        // ═══════════════════════════════════════════
        // ██  INLINE TEXT EDITOR (overlay on canvas) ██
        // ═══════════════════════════════════════════
        if (isEditingText) {
            val xDp = with(density) { textEditX.toDp() }
            val yDp = with(density) { textEditY.toDp() }

            Box(
                modifier = Modifier
                    .offset(x = xDp - 4.dp, y = yDp - 24.dp)
                    .widthIn(min = 80.dp, max = 260.dp)
            ) {
                BasicTextField(
                    value = inlineText,
                    onValueChange = { inlineText = it },
                    textStyle = TextStyle(
                        color = selectedColor.copy(alpha = selectedOpacity),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    cursorBrush = SolidColor(CyanAccent),
                    singleLine = false,
                    modifier = Modifier
                        .focusRequester(textFocusRequester)
                        .background(Color.Black.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    decorationBox = { innerTextField ->
                        Box {
                            if (inlineText.isEmpty()) {
                                Text("Type here...", color = TextMuted.copy(alpha = 0.5f), fontSize = 24.sp)
                            }
                            innerTextField()
                        }
                    }
                )
            }

            LaunchedEffect(isEditingText) {
                if (isEditingText) {
                    textFocusRequester.requestFocus()
                    keyboardController?.show()
                }
            }
        }

        // ═══════════════════════════════════════════
        // ██  FLOATING EMOJI BEING DRAGGED  ██
        // ═══════════════════════════════════════════
        if (draggingEmoji != null) {
            val xDp = with(density) { emojiDragPos.x.toDp() }
            val yDp = with(density) { emojiDragPos.y.toDp() }
            Text(
                draggingEmoji!!,
                fontSize = 40.sp,
                modifier = Modifier.offset(x = xDp - 20.dp, y = yDp - 20.dp)
            )
        }

        // ═══════════════════════════════════════════
        // ██  TOP-LEFT: Back button  ██
        // ═══════════════════════════════════════════
        SkeuButton(
            icon = Icons.AutoMirrored.Rounded.ArrowBack,
            isActive = false,
            tintColor = TextPrimary,
            modifier = Modifier.statusBarsPadding().padding(start = 12.dp, top = 8.dp).align(Alignment.TopStart),
            onClick = {
                haptic()
                when {
                    isEditingText -> commitInlineText()
                    showEmojiPanel -> { showEmojiPanel = false }
                    activeTool != CanvasTool.NONE -> {
                        activeTool = CanvasTool.NONE
                        showColorPicker = false; showEmojiPanel = false
                    }
                    else -> onDone()
                }
            }
        )

        // ═══════════════════════════════════════════
        // ██  TOP-RIGHT: Undo + Redo + BG Color  ██
        // ═══════════════════════════════════════════
        Row(
            modifier = Modifier.statusBarsPadding().padding(end = 12.dp, top = 8.dp).align(Alignment.TopEnd),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SkeuButton(Icons.Rounded.Palette, isActive = false, tintColor = CyanAccent) {
                haptic(); showBgColorPicker = true
            }
            val hasUndo by syncManager.canUndo
            val hasRedo by syncManager.canRedo
            AnimatedVisibility(visible = hasUndo, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                SkeuButton(Icons.AutoMirrored.Rounded.Undo, isActive = false, tintColor = TextPrimary) {
                    haptic(); syncManager.undoLast()
                }
            }
            AnimatedVisibility(visible = hasRedo, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                SkeuButton(Icons.AutoMirrored.Rounded.Redo, isActive = false, tintColor = TextPrimary) {
                    haptic(); syncManager.redoLast()
                }
            }
        }

        // ═══════════════════════════════════════════
        // ██  LEFT EDGE: 4 Tool buttons (no Move!)  ██
        // ═══════════════════════════════════════════
        Column(
            modifier = Modifier.statusBarsPadding().padding(start = 10.dp, top = 56.dp).align(Alignment.TopStart),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Pencil — toggles sketch mode, tap again to show/hide color picker
            SkeuButton(Icons.Rounded.Edit, activeTool == CanvasTool.SKETCH, ElectricViolet) {
                haptic(HapticFeedbackConstants.CONFIRM)
                if (isEditingText) commitInlineText()
                if (activeTool == CanvasTool.SKETCH) { showColorPicker = !showColorPicker }
                else { activeTool = CanvasTool.SKETCH; showColorPicker = false; showEmojiPanel = false }
            }

            // Eraser
            SkeuButton(Icons.Rounded.AutoFixOff, activeTool == CanvasTool.ERASER, WarmRose) {
                haptic(HapticFeedbackConstants.CONFIRM)
                if (isEditingText) commitInlineText()
                if (activeTool != CanvasTool.ERASER) {
                    activeTool = CanvasTool.ERASER; showColorPicker = false; showEmojiPanel = false
                }
            }

            // Sticker — shows panel
            SkeuButton(Icons.Rounded.EmojiEmotions, activeTool == CanvasTool.STICKER || showEmojiPanel, AmberGlow) {
                haptic(HapticFeedbackConstants.CONFIRM)
                if (isEditingText) commitInlineText()
                showEmojiPanel = !showEmojiPanel
                activeTool = if (showEmojiPanel) CanvasTool.STICKER else CanvasTool.NONE
                showColorPicker = false
            }

            // Text — tap canvas to type inline
            SkeuButton(Icons.Rounded.TextFields, activeTool == CanvasTool.TEXT_TOOL, CyanAccent) {
                haptic(HapticFeedbackConstants.CONFIRM)
                if (isEditingText) commitInlineText()
                activeTool = CanvasTool.TEXT_TOOL; showColorPicker = false; showEmojiPanel = false
            }

            // Image
            SkeuButton(Icons.Rounded.AddPhotoAlternate, activeTool == CanvasTool.IMAGE_TOOL, SoftPink) {
                haptic(HapticFeedbackConstants.CONFIRM)
                if (isEditingText) commitInlineText()
                activeTool = CanvasTool.IMAGE_TOOL
                imagePickerLauncher.launch("image/*")
                showColorPicker = false; showEmojiPanel = false
            }
        }

        // ═══════════════════════════════════════════
        // ██  SKETCH: Color palette popup (only visible in sketch mode) ██
        // ═══════════════════════════════════════════
        AnimatedVisibility(
            visible = activeTool == CanvasTool.SKETCH && showColorPicker,
            enter = fadeIn(tween(150)) + scaleIn(spring(dampingRatio = 0.6f), initialScale = 0.5f),
            exit = fadeOut(tween(100)) + scaleOut(targetScale = 0.5f),
            modifier = Modifier.statusBarsPadding().padding(start = 52.dp, top = 56.dp).align(Alignment.TopStart)
        ) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardSurface.copy(alpha = 0.95f))
                    .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (row in presetColors.chunked(6)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        row.forEach { color ->
                            val isSel = selectedColor == color
                            Box(modifier = Modifier
                                .size(if (isSel) 22.dp else 18.dp).clip(CircleShape).background(color)
                                .then(if (isSel) Modifier.border(2.dp, Color.White, CircleShape) else Modifier.border(1.dp, GlassBorder, CircleShape))
                                .clickable { selectedColor = color; haptic() })
                        }
                    }
                }
                // Opacity
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${(selectedOpacity * 100).toInt()}%", fontSize = 9.sp, color = TextMuted, modifier = Modifier.width(26.dp))
                    Slider(
                        value = selectedOpacity, onValueChange = { selectedOpacity = it },
                        valueRange = 0.05f..1f,
                        modifier = Modifier.width(100.dp).height(16.dp),
                        colors = SliderDefaults.colors(thumbColor = selectedColor, activeTrackColor = selectedColor.copy(alpha = 0.5f), inactiveTrackColor = GlassBorder)
                    )
                }
                // Size dots
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf(4f, 8f, 16f, 26f).forEach { w ->
                        val dotSize = (w / 26f * 12f + 6f).dp
                        Box(modifier = Modifier.size(dotSize).clip(CircleShape)
                            .background(if (selectedWidth == w) selectedColor else TextMuted.copy(alpha = 0.4f))
                            .then(if (selectedWidth == w) Modifier.border(2.dp, Color.White, CircleShape) else Modifier)
                            .clickable { selectedWidth = w; haptic() })
                    }
                }
            }
        }

        // ═══════════════════════════════════════════
        // ██  ERASER: Mode toggle + size (center-top) ██
        // ═══════════════════════════════════════════
        AnimatedVisibility(
            visible = activeTool == CanvasTool.ERASER,
            enter = fadeIn(tween(200)) + slideInVertically(spring(dampingRatio = 0.7f)) { -it },
            exit = fadeOut(tween(150)) + slideOutVertically { -it },
            modifier = Modifier.statusBarsPadding().padding(top = 8.dp).align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(CardSurface.copy(alpha = 0.95f))
                    .border(1.dp, GlassBorder, RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val pointActive = eraserMode == EraserMode.POINT
                Box(modifier = Modifier.size(28.dp).clip(CircleShape)
                    .background(if (pointActive) WarmRose.copy(alpha = 0.2f) else Color.Transparent)
                    .then(if (pointActive) Modifier.border(1.dp, WarmRose, CircleShape) else Modifier)
                    .clickable { eraserMode = EraserMode.POINT; haptic() }, contentAlignment = Alignment.Center
                ) { Icon(Icons.Rounded.TouchApp, null, tint = if (pointActive) WarmRose else TextMuted, modifier = Modifier.size(14.dp)) }

                val strokeActive = eraserMode == EraserMode.STROKE
                Box(modifier = Modifier.size(28.dp).clip(CircleShape)
                    .background(if (strokeActive) WarmRose.copy(alpha = 0.2f) else Color.Transparent)
                    .then(if (strokeActive) Modifier.border(1.dp, WarmRose, CircleShape) else Modifier)
                    .clickable { eraserMode = EraserMode.STROKE; haptic() }, contentAlignment = Alignment.Center
                ) { Icon(Icons.Rounded.DeleteSweep, null, tint = if (strokeActive) WarmRose else TextMuted, modifier = Modifier.size(14.dp)) }

                Box(modifier = Modifier.width(1.dp).height(20.dp).background(GlassBorder))

                if (eraserMode == EraserMode.POINT) {
                    Slider(
                        value = eraserWidth, onValueChange = { eraserWidth = it },
                        valueRange = 10f..60f,
                        modifier = Modifier.width(80.dp).height(16.dp),
                        colors = SliderDefaults.colors(thumbColor = WarmRose, activeTrackColor = WarmRose.copy(alpha = 0.4f), inactiveTrackColor = GlassBorder)
                    )
                } else { Text("Tap stroke", fontSize = 10.sp, color = TextMuted) }
            }
        }

        // ═══════════════════════════════════════════
        // ██  EMOJI PANEL — drag emoji from here to canvas ██
        // ═══════════════════════════════════════════
        AnimatedVisibility(
            visible = showEmojiPanel,
            enter = fadeIn(tween(200)) + slideInVertically(spring(dampingRatio = 0.6f)) { it / 2 },
            exit = fadeOut(tween(150)) + slideOutVertically { it / 2 },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(CardSurface.copy(alpha = 0.95f))
                    .border(1.dp, GlassBorder, RoundedCornerShape(20.dp))
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Drag to place", fontSize = 10.sp, color = TextMuted, modifier = Modifier.padding(bottom = 6.dp))
                for (row in emojiList.chunked(10)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(vertical = 2.dp)) {
                        row.forEach { emoji ->
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .pointerInput(emoji) {
                                        detectDragGestures(
                                            onDragStart = { offset ->
                                                draggingEmoji = emoji
                                                haptic()
                                            },
                                            onDrag = { change, _ ->
                                                change.consume()
                                                // Convert local position to screen position
                                                emojiDragPos = change.position
                                            },
                                            onDragEnd = {
                                                // Place the emoji at drag position
                                                if (draggingEmoji != null) {
                                                    // The drag position is relative to the emoji box, 
                                                    // we need window-level coordinates. Use a fallback: 
                                                    // place at center of screen if position seems off
                                                    val placeX = if (emojiDragPos.x in 0f..screenWidthPx) emojiDragPos.x else screenWidthPx / 2
                                                    val placeY = if (emojiDragPos.y in 0f..screenHeightPx) emojiDragPos.y else screenHeightPx / 2
                                                    syncManager.pushSticker(StickerElement(
                                                        emoji = draggingEmoji!!, x = placeX, y = placeY, size = 80f
                                                    ))
                                                    haptic(HapticFeedbackConstants.CONFIRM)
                                                    showEmojiPanel = false
                                                    activeTool = CanvasTool.NONE
                                                }
                                                draggingEmoji = null
                                            },
                                            onDragCancel = { draggingEmoji = null }
                                        )
                                    }
                                    .clickable {
                                        // Fallback: tap to place at center
                                        syncManager.pushSticker(StickerElement(
                                            emoji = emoji, x = screenWidthPx / 2, y = screenHeightPx / 3, size = 80f
                                        ))
                                        haptic(HapticFeedbackConstants.CONFIRM)
                                        showEmojiPanel = false
                                        activeTool = CanvasTool.NONE
                                    },
                                contentAlignment = Alignment.Center
                            ) { Text(emoji, fontSize = 20.sp) }
                        }
                    }
                }
            }
        }

        // ═══════════════════════════════════════════
        // ██  DUSTBIN (animated, bottom-center)  ██
        // ═══════════════════════════════════════════
        AnimatedVisibility(
            visible = dustbinVisible,
            enter = fadeIn(tween(200)) + scaleIn(spring(dampingRatio = 0.5f, stiffness = 300f), initialScale = 0f),
            exit = fadeOut(tween(200)) + scaleOut(spring(dampingRatio = 0.5f), targetScale = 0f),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp)
        ) {
            Box(
                modifier = Modifier.scale(dustbinScale).size(56.dp).clip(CircleShape)
                    .background(
                        if (isInDustbin) Brush.radialGradient(listOf(WarmRose.copy(alpha = 0.4f), WarmRose.copy(alpha = 0.1f)))
                        else Brush.radialGradient(listOf(CardSurface.copy(alpha = 0.9f), CardSurface.copy(alpha = 0.6f)))
                    )
                    .border(if (isInDustbin) 2.dp else 1.dp, if (isInDustbin) WarmRose else GlassBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Delete, "Delete", tint = dustbinColor,
                    modifier = Modifier.size(if (isInDustbin) 28.dp else 22.dp))
            }
        }

        // Upload overlay
        if (isUploadingImage) {
            Box(modifier = Modifier.fillMaxSize().background(DeepNavy.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center) { CircularProgressIndicator(color = CyanAccent, strokeWidth = 3.dp) }
        }
    }

    // ═══════════════════════════════════════════
    // ██  BACKGROUND COLOR DIALOG (only popup left)  ██
    // ═══════════════════════════════════════════
    if (showBgColorPicker) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showBgColorPicker = false }) {
            Surface(shape = RoundedCornerShape(24.dp), color = CardSurface,
                modifier = Modifier.border(1.dp, GlassBorder, RoundedCornerShape(24.dp))) {
                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Background", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Spacer(modifier = Modifier.height(12.dp))
                    for (row in bgCyclePresets.chunked(6)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                            row.forEach { color ->
                                val isSel = color.toArgb() == bgColorArgb
                                Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(color)
                                    .then(if (isSel) Modifier.border(3.dp, ElectricViolet, CircleShape) else Modifier.border(1.dp, GlassBorder, CircleShape))
                                    .clickable { syncManager.setBackgroundColor(color.toArgb()); haptic(HapticFeedbackConstants.CONFIRM) })
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(onClick = { showBgColorPicker = false }) { Text("Done", color = CyanAccent) }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════
// ██  SKEUOMORPHIC BUTTON  ██
// ═══════════════════════════════════════════
@Composable
private fun SkeuButton(
    icon: ImageVector, isActive: Boolean, tintColor: Color,
    modifier: Modifier = Modifier, onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val buttonScale by animateFloatAsState(
        targetValue = when { isPressed -> 0.85f; isActive -> 1.1f; else -> 1f },
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 800f), label = "bs"
    )
    val bgBrush = if (isActive) Brush.verticalGradient(listOf(tintColor.copy(alpha = 0.25f), tintColor.copy(alpha = 0.08f)))
    else Brush.verticalGradient(listOf(ElevatedSurface.copy(alpha = 0.95f), CardSurface.copy(alpha = 0.95f)))
    val borderColor by animateColorAsState(
        targetValue = if (isActive) tintColor.copy(alpha = 0.5f) else GlassBorder, animationSpec = tween(200), label = "bc"
    )
    val iconTint by animateColorAsState(
        targetValue = if (isActive) tintColor else TextSecondary, animationSpec = tween(150), label = "it"
    )
    Box(
        modifier = modifier.scale(buttonScale).size(36.dp)
            .shadow(if (isActive) 4.dp else 2.dp, CircleShape, spotColor = if (isActive) tintColor.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.3f))
            .clip(CircleShape).background(bgBrush).border(1.dp, borderColor, CircleShape)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = iconTint, modifier = Modifier.size(16.dp)) }
}

private fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float {
    val dx = x1 - x2; val dy = y1 - y2; return sqrt(dx * dx + dy * dy)
}
