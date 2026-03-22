package com.alancruse.wallpaper.ui

import android.net.Uri
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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.alancruse.wallpaper.canvas.*
import com.alancruse.wallpaper.ui.theme.*
import kotlin.math.sqrt

// Two main tools + default transform
private enum class CanvasTool {
    SKETCH, CREATE, TRANSFORM
}

/** Which type of element is selected for transform */
private enum class SelectedType { STICKER, TEXT, IMAGE }

/** Sealed class to unify all canvas elements for chronological layer ordering */
private sealed class CanvasLayer(val timestamp: Long) {
    data class StrokeLayer(val stroke: DrawingStroke, val idx: Int) : CanvasLayer(stroke.timestamp)
    data class StickerLayer(val sticker: StickerElement, val idx: Int) : CanvasLayer(sticker.timestamp)
    data class TextLayer(val text: TextElement, val idx: Int) : CanvasLayer(text.timestamp)
    data class ImageLayer(val image: ImageElement, val idx: Int) : CanvasLayer(image.timestamp)
}

@Composable
fun LiveCanvasScreen(
    syncManager: CanvasSyncManager,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val remoteStrokes = syncManager.sharedStrokes
    val remoteStickers = syncManager.sharedStickers
    val remoteTexts = syncManager.sharedTexts
    val remoteImages = syncManager.sharedImages

    // Cache decoded bitmaps to avoid re-decoding Base64 on every frame
    val bitmapCache = remember { mutableMapOf<String, android.graphics.Bitmap>() }
    // Update cache when images change
    LaunchedEffect(remoteImages.toList()) {
        // Add new bitmaps
        remoteImages.forEach { img ->
            if (img.imageBase64.isNotEmpty() && img.imageBase64 !in bitmapCache) {
                try {
                    val bytes = android.util.Base64.decode(img.imageBase64, android.util.Base64.NO_WRAP)
                    val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bmp != null) bitmapCache[img.imageBase64] = bmp
                } catch (_: Exception) {}
            }
        }
        // Remove stale entries
        val activeKeys = remoteImages.map { it.imageBase64 }.toSet()
        bitmapCache.keys.retainAll(activeKeys)
    }

    var currentPath by remember { mutableStateOf(emptyList<Point>()) }
    // Default tool is TRANSFORM (Move)
    var activeTool by remember { mutableStateOf(CanvasTool.TRANSFORM) }
    // Sub-state: pen or eraser within SKETCH tool
    var isErasing by remember { mutableStateOf(false) }

    // Draw tool state
    val colorOptions = listOf(
        Color.White, ElectricViolet, CyanAccent, SoftPink,
        SuccessGreen, AmberGlow, WarmRose
    )
    var selectedColor by remember { mutableStateOf(Color.White) }
    val widthOptions = listOf(5f, 10f, 18f, 28f)
    var selectedWidth by remember { mutableStateOf(10f) }

    // Eraser width state
    var eraserWidth by remember { mutableStateOf(20f) }

    // Sticker state
    var selectedEmoji by remember { mutableStateOf("❤️") }
    val emojiList = listOf(
        "❤️", "💜", "💙", "💚", "💛", "🧡", "🤍", "🖤",
        "⭐", "✨", "🌟", "💫", "🔥", "🌈", "🦋", "🌸",
        "🌺", "🌻", "🍀", "🌙", "☀️", "❄️", "💎", "👑",
        "🎵", "🎶", "💕", "💗", "💖", "💝", "😊", "😍",
        "🥰", "😘", "🤗", "😎", "🥳", "🎉", "🎈", "🎨"
    )

    // Text tool state
    var showTextDialog by remember { mutableStateOf(false) }
    var pendingText by remember { mutableStateOf("") }
    // Store tap position so text is placed exactly where user tapped
    var textTapX by remember { mutableStateOf(0f) }
    var textTapY by remember { mutableStateOf(0f) }
    // Text editing state — item #1
    var isEditingText by remember { mutableStateOf(false) }
    var editTextIndex by remember { mutableStateOf(-1) }

    // Image state
    var isUploadingImage by remember { mutableStateOf(false) }
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isUploadingImage = true
            syncManager.pushImage(context, uri, 100f, 300f, 300f, 300f) { success, errorMsg ->
                isUploadingImage = false
                if (!success) {
                    Toast.makeText(context, errorMsg ?: "Image upload failed", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ── Transform / selection state ──
    var selectedType by remember { mutableStateOf<SelectedType?>(null) }
    var selectedIndex by remember { mutableStateOf(-1) }

    /** Find the nearest sticker/text/image to a tap point and select it */
    fun selectNearestElement(tapX: Float, tapY: Float) {
        var bestDist = Float.MAX_VALUE
        var bestType: SelectedType? = null
        var bestIdx = -1

        remoteStickers.forEachIndexed { i, s ->
            val d = dist(tapX, tapY, s.x, s.y)
            if (d < 100f && d < bestDist) {
                bestDist = d; bestType = SelectedType.STICKER; bestIdx = i
            }
        }
        remoteTexts.forEachIndexed { i, t ->
            val d = dist(tapX, tapY, t.x, t.y)
            if (d < 120f && d < bestDist) {
                bestDist = d; bestType = SelectedType.TEXT; bestIdx = i
            }
        }
        remoteImages.forEachIndexed { i, img ->
            val cx = img.x + img.width / 2f
            val cy = img.y + img.height / 2f
            val d = dist(tapX, tapY, cx, cy)
            if (d < 200f && d < bestDist) {
                bestDist = d; bestType = SelectedType.IMAGE; bestIdx = i
            }
        }

        selectedType = bestType
        selectedIndex = bestIdx
    }

    /** Find nearest text element to a point */
    fun findNearestText(tapX: Float, tapY: Float): Int {
        var bestDist = Float.MAX_VALUE
        var bestIdx = -1
        remoteTexts.forEachIndexed { i, t ->
            val d = dist(tapX, tapY, t.x, t.y)
            if (d < 120f && d < bestDist) {
                bestDist = d; bestIdx = i
            }
        }
        return bestIdx
    }

    // Back button handler: return to Move if a tool is active, else close canvas
    BackHandler(enabled = true) {
        if (activeTool != CanvasTool.TRANSFORM) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            activeTool = CanvasTool.TRANSFORM
            selectedType = null; selectedIndex = -1
        } else {
            onDone()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(DeepNavy)) {

        // ── THE DRAWING CANVAS ──
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    when (activeTool) {
                        // ─── SKETCH tool: pen (tap+drag) or eraser (drag) depending on isErasing ───
                        CanvasTool.SKETCH -> if (!isErasing) {
                            Modifier
                                .pointerInput("draw_tap", selectedColor, selectedWidth) {
                                    detectTapGestures { offset ->
                                        syncManager.pushStroke(DrawingStroke(
                                            colorArgb = selectedColor.toArgb(),
                                            strokeWidth = selectedWidth,
                                            path = listOf(offset.toPoint(), offset.toPoint())
                                        ))
                                    }
                                }
                                .pointerInput("draw_drag", selectedColor, selectedWidth) {
                                    detectDragGestures(
                                        onDragStart = { offset -> currentPath = listOf(offset.toPoint()) },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            currentPath = currentPath + change.position.toPoint()
                                        },
                                        onDragEnd = {
                                            if (currentPath.isNotEmpty()) {
                                                syncManager.pushStroke(DrawingStroke(
                                                    colorArgb = selectedColor.toArgb(),
                                                    strokeWidth = selectedWidth,
                                                    path = currentPath
                                                ))
                                                currentPath = emptyList()
                                            }
                                        }
                                    )
                                }
                        } else {
                            Modifier.pointerInput(eraserWidth) {
                                detectDragGestures { change, _ ->
                                    change.consume()
                                    syncManager.erasePointsNear(change.position.x, change.position.y, eraserWidth)
                                }
                            }
                        }

                        // ─── CREATE tool: tap to place sticker (stickers handled via tap), text via dialog ───
                        CanvasTool.CREATE -> Modifier.pointerInput(selectedEmoji, selectedColor) {
                            detectTapGestures { offset ->
                                // Check if tap is near an existing text → edit it
                                val nearIdx = findNearestText(offset.x, offset.y)
                                if (nearIdx >= 0) {
                                    isEditingText = true
                                    editTextIndex = nearIdx
                                    pendingText = remoteTexts[nearIdx].text
                                    showTextDialog = true
                                } else {
                                    // Place sticker at tap
                                    syncManager.pushSticker(StickerElement(emoji = selectedEmoji, x = offset.x, y = offset.y, size = 80f))
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                }
                            }
                        }

                        // ─── TRANSFORM (Move) tool: tap to select + pinch/drag to transform ─── (item #4)
                        CanvasTool.TRANSFORM -> Modifier
                            .pointerInput("transform_tap") {
                                detectTapGestures { offset ->
                                    selectNearestElement(offset.x, offset.y)
                                }
                            }
                            .pointerInput("transform_gesture", selectedType, selectedIndex) {
                                detectTransformGestures { _, pan, zoom, rotation ->
                                    if (selectedType == null || selectedIndex < 0) {
                                        return@detectTransformGestures
                                    }
                                    when (selectedType) {
                                        SelectedType.STICKER -> {
                                            if (selectedIndex in remoteStickers.indices) {
                                                val s = remoteStickers[selectedIndex]
                                                val updated = s.copy(
                                                    x = s.x + pan.x,
                                                    y = s.y + pan.y,
                                                    scale = (s.scale * zoom).coerceIn(0.3f, 5f),
                                                    rotation = s.rotation + rotation
                                                )
                                                syncManager.updateSticker(selectedIndex, updated)
                                            }
                                        }
                                        SelectedType.TEXT -> {
                                            if (selectedIndex in remoteTexts.indices) {
                                                val t = remoteTexts[selectedIndex]
                                                val updated = t.copy(
                                                    x = t.x + pan.x,
                                                    y = t.y + pan.y,
                                                    scale = (t.scale * zoom).coerceIn(0.3f, 5f),
                                                    rotation = t.rotation + rotation
                                                )
                                                syncManager.updateText(selectedIndex, updated)
                                            }
                                        }
                                        SelectedType.IMAGE -> {
                                            if (selectedIndex in remoteImages.indices) {
                                                val img = remoteImages[selectedIndex]
                                                val updated = img.copy(
                                                    x = img.x + pan.x,
                                                    y = img.y + pan.y,
                                                    scale = (img.scale * zoom).coerceIn(0.2f, 5f),
                                                    rotation = img.rotation + rotation
                                                )
                                                syncManager.updateImage(selectedIndex, updated)
                                            }
                                        }
                                        else -> {}
                                    }
                                }
                            }
                    }
                )
        ) {
            // ── Unified chronological layer system ── (item #5)
            // Merge all elements into one list sorted by timestamp
            val layers = buildList {
                remoteStrokes.forEachIndexed { i, s -> add(CanvasLayer.StrokeLayer(s, i)) }
                remoteStickers.forEachIndexed { i, s -> add(CanvasLayer.StickerLayer(s, i)) }
                remoteTexts.forEachIndexed { i, t -> add(CanvasLayer.TextLayer(t, i)) }
                remoteImages.forEachIndexed { i, img -> add(CanvasLayer.ImageLayer(img, i)) }
            }.sortedBy { it.timestamp }

            val nativeCanvas = drawContext.canvas.nativeCanvas

            for (layer in layers) {
                when (layer) {
                    is CanvasLayer.StrokeLayer -> {
                        val strokeData = layer.stroke
                        if (strokeData.path.isNotEmpty()) {
                            val composePath = Path().apply {
                                val first = strokeData.path.first()
                                moveTo(first.x, first.y)
                                for (i in 1 until strokeData.path.size) {
                                    lineTo(strokeData.path[i].x, strokeData.path[i].y)
                                }
                            }
                            drawPath(composePath, Color(strokeData.colorArgb),
                                style = Stroke(width = strokeData.strokeWidth, cap = StrokeCap.Round))
                            // Handle single-point strokes (dots) — item #3
                            if (strokeData.path.size <= 2 && strokeData.path.first().x == strokeData.path.last().x
                                && strokeData.path.first().y == strokeData.path.last().y) {
                                drawCircle(
                                    color = Color(strokeData.colorArgb),
                                    radius = strokeData.strokeWidth / 2f,
                                    center = Offset(strokeData.path.first().x, strokeData.path.first().y)
                                )
                            }
                        }
                    }
                    is CanvasLayer.StickerLayer -> {
                        val sticker = layer.sticker
                        val idx = layer.idx
                        val emojiPaint = android.graphics.Paint().apply {
                            textAlign = android.graphics.Paint.Align.CENTER
                            isAntiAlias = true
                            textSize = sticker.size * sticker.scale
                        }
                        nativeCanvas.save()
                        nativeCanvas.rotate(sticker.rotation, sticker.x, sticker.y)
                        nativeCanvas.drawText(sticker.emoji, sticker.x, sticker.y, emojiPaint)
                        nativeCanvas.restore()

                        // Selection ring
                        if (activeTool == CanvasTool.TRANSFORM && selectedType == SelectedType.STICKER && selectedIndex == idx) {
                            drawCircle(
                                color = ElectricViolet.copy(alpha = 0.5f),
                                radius = sticker.size * sticker.scale * 0.7f,
                                center = Offset(sticker.x, sticker.y),
                                style = Stroke(2f)
                            )
                        }
                    }
                    is CanvasLayer.TextLayer -> {
                        val textEl = layer.text
                        val idx = layer.idx
                        val txtPaint = android.graphics.Paint().apply {
                            color = textEl.colorArgb
                            textSize = textEl.fontSize * textEl.scale
                            isAntiAlias = true
                            textAlign = android.graphics.Paint.Align.LEFT
                        }
                        nativeCanvas.save()
                        nativeCanvas.rotate(textEl.rotation, textEl.x, textEl.y)
                        nativeCanvas.drawText(textEl.text, textEl.x, textEl.y, txtPaint)
                        nativeCanvas.restore()

                        if (activeTool == CanvasTool.TRANSFORM && selectedType == SelectedType.TEXT && selectedIndex == idx) {
                            val tw = txtPaint.measureText(textEl.text)
                            drawRect(
                                color = CyanAccent.copy(alpha = 0.4f),
                                topLeft = Offset(textEl.x - 4, textEl.y - textEl.fontSize * textEl.scale),
                                size = androidx.compose.ui.geometry.Size(tw + 8, textEl.fontSize * textEl.scale + 8),
                                style = Stroke(2f)
                            )
                        }
                    }
                    is CanvasLayer.ImageLayer -> {
                        val img = layer.image
                        val idx = layer.idx
                        if (img.imageBase64.isEmpty()) continue
                        val isSelected = activeTool == CanvasTool.TRANSFORM && selectedType == SelectedType.IMAGE && selectedIndex == idx

                        val bitmap = bitmapCache[img.imageBase64]

                        if (bitmap != null) {
                            val w = (img.width * img.scale).toInt().coerceAtLeast(1)
                            val h = (img.height * img.scale).toInt().coerceAtLeast(1)
                            val scaledBitmap = android.graphics.Bitmap.createScaledBitmap(bitmap, w, h, true)
                            val cx = img.x + w / 2f
                            val cy = img.y + h / 2f

                            nativeCanvas.save()
                            nativeCanvas.rotate(img.rotation, cx, cy)
                            nativeCanvas.drawBitmap(scaledBitmap, img.x, img.y, null)
                            nativeCanvas.restore()

                            if (isSelected) {
                                drawRect(
                                    color = ElectricViolet.copy(alpha = 0.6f),
                                    topLeft = Offset(img.x - 2, img.y - 2),
                                    size = androidx.compose.ui.geometry.Size(w + 4f, h + 4f),
                                    style = Stroke(3f)
                                )
                            }
                        }
                    }
                }
            }

            // ── Draw live stroke (always on top) ──
            if (currentPath.isNotEmpty()) {
                val livePath = Path().apply {
                    moveTo(currentPath.first().x, currentPath.first().y)
                    for (i in 1 until currentPath.size) { lineTo(currentPath[i].x, currentPath[i].y) }
                }
                drawPath(livePath, selectedColor, style = Stroke(selectedWidth, cap = StrokeCap.Round))
            }
        }

        // ── TOP BAR: Done + Undo + Clear ──
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = 52.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    if (activeTool != CanvasTool.TRANSFORM) {
                        activeTool = CanvasTool.TRANSFORM
                        selectedType = null; selectedIndex = -1
                    } else {
                        onDone()
                    }
                },
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(CardSurface.copy(alpha = 0.9f))
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = TextPrimary, modifier = Modifier.size(18.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        syncManager.undoLast()
                    },
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(CardSurface.copy(alpha = 0.9f))
                ) { Icon(Icons.AutoMirrored.Rounded.Undo, "Undo", tint = TextPrimary, modifier = Modifier.size(18.dp)) }
                IconButton(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        syncManager.clearCanvas()
                    },
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(CardSurface.copy(alpha = 0.9f))
                ) { Icon(Icons.Rounded.Delete, "Clear", tint = WarmRose, modifier = Modifier.size(18.dp)) }
            }
        }

        // ── FLOATING TOOLBAR ──
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 12.dp, vertical = 14.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(CardSurface.copy(alpha = 0.95f))
                .border(1.dp, GlassBorder, RoundedCornerShape(24.dp))
                .padding(12.dp)
        ) {
            // ── Tool tabs ──
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ToolTab("Sketch", Icons.Rounded.Brush, activeTool == CanvasTool.SKETCH) {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    activeTool = CanvasTool.SKETCH; selectedType = null; selectedIndex = -1
                }
                ToolTab("Create", Icons.Rounded.AddCircleOutline, activeTool == CanvasTool.CREATE) {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    activeTool = CanvasTool.CREATE; selectedType = null; selectedIndex = -1
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ── Tool-specific options with animated content ──
            when (activeTool) {
                CanvasTool.SKETCH -> {
                    // Pen / Eraser toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Pen toggle
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(32.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (!isErasing) ElectricViolet.copy(alpha = 0.2f) else Color.Transparent)
                                .then(if (!isErasing) Modifier.border(1.dp, ElectricViolet.copy(alpha = 0.3f), RoundedCornerShape(10.dp)) else Modifier)
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    isErasing = false
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Edit, null, tint = if (!isErasing) ElectricViolet else TextMuted, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Pen", fontSize = 12.sp, color = if (!isErasing) ElectricViolet else TextMuted, fontWeight = FontWeight.Medium)
                            }
                        }
                        // Eraser toggle
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(32.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isErasing) WarmRose.copy(alpha = 0.2f) else Color.Transparent)
                                .then(if (isErasing) Modifier.border(1.dp, WarmRose.copy(alpha = 0.3f), RoundedCornerShape(10.dp)) else Modifier)
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    isErasing = true
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.AutoFixOff, null, tint = if (isErasing) WarmRose else TextMuted, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Eraser", fontSize = 12.sp, color = if (isErasing) WarmRose else TextMuted, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    if (!isErasing) {
                        // Pen options: color + size
                        DrawToolOptions(colorOptions, selectedColor, widthOptions, selectedWidth,
                            onColorSelect = { selectedColor = it }, onWidthSelect = { selectedWidth = it })
                    } else {
                        // Eraser size selector
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()) {
                            Text("Size", fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Medium)
                            listOf(15f, 20f, 30f, 45f).forEach { width ->
                                val dotSize = (width / 45f * 18f + 10f).dp
                                Box(modifier = Modifier.size(dotSize).clip(CircleShape)
                                    .background(if (eraserWidth == width) WarmRose.copy(alpha = 0.7f) else TextMuted.copy(alpha = 0.4f))
                                    .then(if (eraserWidth == width) Modifier.border(2.dp, Color.White, CircleShape) else Modifier)
                                    .clickable { eraserWidth = width })
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        HintText("Drag across strokes to erase parts")
                    }
                }

                CanvasTool.CREATE -> {
                    // ── Emoji grid (no search bar) ──
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        emojiList.forEach { emoji ->
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selectedEmoji == emoji) ElectricViolet.copy(alpha = 0.3f) else Color.Transparent)
                                    .then(if (selectedEmoji == emoji) Modifier.border(1.dp, ElectricViolet, RoundedCornerShape(10.dp)) else Modifier)
                                    .clickable {
                                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                        selectedEmoji = emoji
                                    },
                                contentAlignment = Alignment.Center
                            ) { Text(emoji, fontSize = 22.sp) }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // ── Text + Image buttons row ──
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Add Text button
                        Button(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                isEditingText = false; editTextIndex = -1
                                textTapX = 540f; textTapY = 960f // center-ish default
                                showTextDialog = true
                            },
                            modifier = Modifier.weight(1f).height(36.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet.copy(alpha = 0.3f))
                        ) {
                            Icon(Icons.Rounded.TextFields, null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Text", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }

                        // Add Image button
                        Button(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                imagePickerLauncher.launch("image/*")
                            },
                            modifier = Modifier.weight(1f).height(36.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = CyanAccent.copy(alpha = 0.3f))
                        ) {
                            Icon(Icons.Rounded.AddPhotoAlternate, null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Image", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    HintText("Tap canvas to place sticker • Tap existing text to edit")
                }

                CanvasTool.TRANSFORM -> {
                    val statusText = when {
                        selectedType == SelectedType.STICKER && selectedIndex >= 0 -> "Sticker selected — pinch to resize, rotate with 2 fingers, drag to move"
                        selectedType == SelectedType.TEXT && selectedIndex >= 0 -> "Text selected — pinch to resize, rotate with 2 fingers, drag to move"
                        selectedType == SelectedType.IMAGE && selectedIndex >= 0 -> "Image selected — pinch to resize, rotate with 2 fingers, drag to move"
                        else -> "Tap to select a sticker, text, or image • Use tools below to create"
                    }
                    Text(statusText, fontSize = 12.sp, color = if (selectedType != null) CyanAccent else TextMuted,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    // Deselect button
                    if (selectedType != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        TextButton(
                            onClick = { selectedType = null; selectedIndex = -1 },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) { Text("Deselect", color = TextMuted, fontSize = 12.sp) }
                    }
                }
            }
        }

        // Upload overlay
        if (isUploadingImage) {
            Box(modifier = Modifier.fillMaxSize().background(DeepNavy.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center) { CircularProgressIndicator(color = CyanAccent) }
        }
    }

    // ── Text input dialog (shared for new + edit) ──
    if (showTextDialog) {
        Dialog(onDismissRequest = { showTextDialog = false; isEditingText = false; editTextIndex = -1 }) {
            Surface(shape = RoundedCornerShape(24.dp), color = CardSurface, tonalElevation = 0.dp,
                modifier = Modifier.border(1.dp, GlassBorder, RoundedCornerShape(24.dp))) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        if (isEditingText) "Edit Text" else "Add Text",
                        fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, letterSpacing = (-0.3).sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = pendingText, onValueChange = { pendingText = it },
                        placeholder = { Text("Type something...", color = TextMuted) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricViolet, cursorColor = ElectricViolet,
                            focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showTextDialog = false; pendingText = ""; isEditingText = false; editTextIndex = -1 }) {
                            Text("Cancel", color = TextMuted)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (pendingText.isNotBlank()) {
                                    if (isEditingText && editTextIndex >= 0 && editTextIndex in remoteTexts.indices) {
                                        // Update existing text element
                                        val existingText = remoteTexts[editTextIndex]
                                        syncManager.updateText(editTextIndex, existingText.copy(text = pendingText))
                                        isEditingText = false; editTextIndex = -1
                                        pendingText = ""; showTextDialog = false
                                    } else {
                                        // New text — place at the stored tap position immediately
                                        syncManager.pushText(TextElement(
                                            text = pendingText, x = textTapX, y = textTapY,
                                            colorArgb = selectedColor.toArgb(), fontSize = 48f
                                        ))
                                        pendingText = ""; showTextDialog = false
                                        // Auto-return to Move
                                        activeTool = CanvasTool.TRANSFORM
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet)
                        ) { Text(if (isEditingText) "Update" else "Place on Canvas") }
                    }
                }
            }
        }
    }
}

// ── Helper composables ──

@Composable
private fun HintText(text: String) {
    Text(text, fontSize = 11.sp, color = TextMuted, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth())
}

@Composable
private fun DrawToolOptions(
    colors: List<Color>, selectedColor: Color,
    widths: List<Float>, selectedWidth: Float,
    onColorSelect: (Color) -> Unit, onWidthSelect: (Float) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()) {
        Text("Color", fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Medium)
        colors.forEach { color ->
            Box(modifier = Modifier.size(if (selectedColor == color) 28.dp else 22.dp)
                .clip(CircleShape).background(color)
                .then(if (selectedColor == color) Modifier.border(2.dp, Color.White, CircleShape)
                else Modifier.border(1.dp, GlassBorder, CircleShape))
                .clickable { onColorSelect(color) })
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()) {
        Text("Size", fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Medium)
        widths.forEach { width ->
            val dotSize = (width / 28f * 18f + 8f).dp
            Box(modifier = Modifier.size(dotSize).clip(CircleShape)
                .background(if (selectedWidth == width) selectedColor else TextMuted.copy(alpha = 0.4f))
                .then(if (selectedWidth == width) Modifier.border(2.dp, Color.White, CircleShape) else Modifier)
                .clickable { onWidthSelect(width) })
        }
    }
}

@Composable
private fun ToolTab(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, isActive: Boolean, onClick: () -> Unit) {
    val bgColor by animateColorAsState(
        targetValue = if (isActive) ElectricViolet.copy(alpha = 0.15f) else Color.Transparent,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f), label = "toolBg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isActive) ElectricViolet else TextMuted,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f), label = "toolColor"
    )
    val tabScale by animateFloatAsState(
        targetValue = if (isActive) 1.08f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 500f), label = "tabScale"
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .scale(tabScale)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .then(if (isActive) Modifier.border(1.dp, ElectricViolet.copy(alpha = 0.2f), RoundedCornerShape(12.dp)) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp)) {
        Icon(icon, label, tint = contentColor, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.height(2.dp))
        Text(label, fontSize = 9.sp, color = contentColor, fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium)
    }
}

private fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float {
    val dx = x1 - x2; val dy = y1 - y2
    return sqrt(dx * dx + dy * dy)
}
