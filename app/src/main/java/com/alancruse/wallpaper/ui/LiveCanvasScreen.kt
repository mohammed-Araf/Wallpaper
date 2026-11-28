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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import com.alancruse.wallpaper.canvas.ShapeSnap
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.alancruse.wallpaper.live.AvatarLook
import com.alancruse.wallpaper.live.LiveBoard
import com.alancruse.wallpaper.live.LiveSource
import com.alancruse.wallpaper.live.LiveStats
import com.alancruse.wallpaper.live.Pet
import com.alancruse.wallpaper.live.Presence
import com.alancruse.wallpaper.live.Status
import androidx.compose.foundation.verticalScroll
import com.alancruse.wallpaper.live.LiveTracker
import com.alancruse.wallpaper.live.StickerKinds
import com.alancruse.wallpaper.wallpaper.StickerArt
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private enum class Tool { MOVE, PEN, ERASER, TEXT }
private enum class ElType { STICKER, TEXT, IMAGE }
private data class TextEdit(
    val canvasX: Float, val canvasY: Float, val index: Int?, val value: String,
    val style: Int = 0, val font: Int = 0, val color: Int? = null,
)

private fun TextEdit(t: TextElement, index: Int) = TextEdit(t.x, t.y, index, t.text, t.style, t.font, t.colorArgb)

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
    // Name plate being edited on a live sticker: sticker index + draft text
    var plateEdit by remember { mutableStateOf<Pair<Int, String>?>(null) }
    // Status avatar look being edited: sticker index (null = about to add one) + look
    var avatarEdit by remember { mutableStateOf<Pair<Int?, AvatarLook>?>(null) }
    // Countdown being set up: index (null = new) and its draft.
    var countdownEdit by remember { mutableStateOf<Pair<Int?, StickerElement>?>(null) }
    // Pet sheet: index (null = adopting a new one) and the draft.
    var petEdit by remember { mutableStateOf<Pair<Int?, StickerElement>?>(null) }
    // Where a sticker that needs setting up (pet, countdown, avatar) was dropped; placed on save.
    var placeAt by remember { mutableStateOf<Offset?>(null) }
    // Live / animated sticker being dragged out of the market
    var dragKind by remember { mutableStateOf<String?>(null) }
    // Tapped pet (sticker key): shows its thought cloud and the food / hand tools
    var petFocus by remember { mutableStateOf<String?>(null) }
    var petShown by remember { mutableStateOf<String?>(null) }
    // "food" or "hand" being dragged out from the pet tools, and where the finger is
    var toolDrag by remember { mutableStateOf<String?>(null) }
    var toolPos by remember { mutableStateOf(Offset.Zero) }
    val petAppear by animateFloatAsState(if (petFocus != null) 1f else 0f, spring(0.55f, 260f), label = "petThought",
        finishedListener = { if (it == 0f) petShown = null })
    val avatarPrefs = remember { context.getSharedPreferences("avatar", android.content.Context.MODE_PRIVATE) }
    var uploading by remember { mutableStateOf(false) }

    // Element drag → dustbin
    var draggingEl by remember { mutableStateOf(false) }
    var overBin by remember { mutableStateOf(false) }
    // Dragging a pet that has died: the bin becomes a pit, and dropping it there buries it.
    var pitMode by remember { mutableStateOf(false) }

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

    // Animated / live stickers: frame clock only while some are on the canvas.
    var frameMs by remember { mutableLongStateOf(0L) }
    val hasLive = sync.sharedStickers.any { it.kind.isNotEmpty() } || dragKind != null || placeAt != null
    LaunchedEffect(Unit) { StickerArt.use24h = android.text.format.DateFormat.is24HourFormat(context) }
    LaunchedEffect(hasLive) { if (hasLive) while (true) withFrameMillis { frameMs = it } }
    val liveWatcher = remember(sync) { LiveBoard.Watcher(sync.roomId) }
    LaunchedEffect(sync) { snapshotFlow { sync.sharedStickers.toList() }.collect { liveWatcher.update(it) } }
    DisposableEffect(sync) { onDispose { liveWatcher.close() } }

    // Permissions are asked when a live sticker is placed, not up front.
    var pendingKind by remember { mutableStateOf<String?>(null) }
    val bgLocLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { LiveTracker.refresh() }
    fun maybeBackground(k: String) {
        if (StickerKinds.needsLocation(k) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        ) bgLocLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    }
    val livePermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        LiveTracker.refresh(); pendingKind?.let { maybeBackground(it) }; pendingKind = null
    }
    fun requestLivePerms(k: String) {
        val perms = buildList {
            if (StickerKinds.needsActivity(k) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(Manifest.permission.ACTIVITY_RECOGNITION)
            if (StickerKinds.needsLocation(k)) { add(Manifest.permission.ACCESS_FINE_LOCATION); add(Manifest.permission.ACCESS_COARSE_LOCATION) }
        }.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        if (perms.isNotEmpty()) { pendingKind = k; livePermLauncher.launch(perms.toTypedArray()) } else { maybeBackground(k); LiveTracker.refresh() }
    }

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
        val col = e.color ?: penColor.copy(alpha = penAlpha).toArgb()
        if (e.index != null && e.index in sync.sharedTexts.indices) {
            if (txt.isBlank()) sync.deleteText(e.index)
            else sync.updateText(e.index, sync.sharedTexts[e.index].copy(text = txt, style = e.style, font = e.font, colorArgb = col))
        } else if (txt.isNotBlank()) {
            sync.pushText(TextElement(text = txt, x = e.canvasX, y = e.canvasY, colorArgb = col, fontSize = textSize, style = e.style, font = e.font))
            haptic(Haptic.Confirm)
            // Like a story: once the text is placed, the next touch moves it.
            tool = Tool.MOVE; trayOpen = false
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
            val pet = StickerKinds.isPet(s.kind)
            val size = s.size * s.scale * (if (pet) StickerArt.petScale(s) else 1f)
            val (ax, ay) = if (pet) StickerArt.petAnchor(s) else s.x to s.y
            val l = local(ax, ay, s.rotation)
            // Pets draw from their feet up; their box is centred a bit lower.
            val dx = l.x; val dy = l.y + size * 0.35f - (if (pet) size * 0.2f else 0f)
            if (s.kind.isNotEmpty()) {
                val (hw, hh) = StickerArt.halfSize(s.kind); val u = size / 100f
                if (abs(dx) < hw * u + slack && abs(dy) < hh * u + slack) take(ElType.STICKER, i, s.timestamp)
            } else if (sqrt(dx * dx + dy * dy) < size * 0.6f + slack) take(ElType.STICKER, i, s.timestamp)
        }
        sync.sharedTexts.forEachIndexed { i, t ->
            val paint = CanvasRenderer.textPaintFor(t)
            val lines = t.text.split('\n')
            val w = lines.maxOf { paint.measureText(it) }
            val l = local(t.x, t.y, t.rotation)
            val sl = slack + CanvasRenderer.textPad(t)
            if (l.x in -sl..(w + sl) && l.y in (-paint.textSize - sl)..(paint.textSize * 1.2f * (lines.size - 1) + paint.textSize * 0.3f + sl))
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

    fun isDeadPet(el: Pair<ElType, Int>?) = el != null && el.first == ElType.STICKER &&
        sync.sharedStickers.getOrNull(el.second)?.let { StickerKinds.isPet(it.kind) && Pet.isDead(it, sync.sharedStickers, System.currentTimeMillis()) } == true

    fun deleteEl(el: Pair<ElType, Int>) = when (el.first) {
        ElType.STICKER -> sync.deleteSticker(el.second)
        ElType.TEXT -> sync.deleteText(el.second)
        ElType.IMAGE -> sync.deleteImage(el.second)
    }

    fun resetView() { viewScale = 1f; viewOffset = Offset.Zero }

    fun myName() = FirebaseAuth.getInstance().currentUser?.displayName?.trim()?.split(' ')?.firstOrNull()?.take(12) ?: ""
    fun myUid() = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    /** Writes a pet update and clears the bowls it has eaten. */
    fun applyPet(key: String, update: Pair<StickerElement, List<StickerElement>>) {
        val i = sync.stickerKeys.indexOf(key)
        if (i >= 0) sync.updateSticker(i, update.first)
        for (f in update.second) {
            val fi = sync.sharedStickers.indexOfFirst { it === f || it == f }
            if (fi >= 0) sync.deleteSticker(fi)
        }
    }

    fun focusedPet(key: String? = petFocus): Pair<String, StickerElement>? {
        val i = key?.let { sync.stickerKeys.indexOf(it) } ?: return null
        val p = sync.sharedStickers.getOrNull(i)?.takeIf { StickerKinds.isPet(it.kind) } ?: return null
        return key to p
    }

    /**
     * Put a market sticker on the canvas at [at] (canvas point; null = middle of the view).
     * Stickers that need setting up open their sheet first and are placed there on save.
     */
    fun placeKind(k: String, at: Offset?) {
        showStickers = false
        if (StickerKinds.needsStatus(k)) {
            placeAt = at ?: visibleCenter()
            avatarEdit = null to AvatarLook.decode(avatarPrefs.getString("look", "") ?: "")
            return
        }
        if (StickerKinds.isPet(k)) {
            placeAt = at ?: visibleCenter()
            petEdit = null to StickerElement(kind = k, look = Pet.Look().encode(), label = "Mochi")
            return
        }
        if (StickerKinds.isCountdown(k)) {
            placeAt = at ?: visibleCenter()
            val cal = java.util.Calendar.getInstance().apply {
                add(java.util.Calendar.DAY_OF_YEAR, 7); set(java.util.Calendar.HOUR_OF_DAY, 9)
                set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
            }
            countdownEdit = null to StickerElement(kind = k, until = cal.timeInMillis, look = "i0")
            return
        }
        val p = at ?: visibleCenter()
        if (StickerKinds.isFood(k)) {
            sync.pushSticker(StickerElement(emoji = FallbackEmoji[k] ?: "🥣", kind = k, label = myName(), x = p.x, y = p.y, size = 110f / viewScale))
            haptic(Haptic.Confirm); tool = Tool.MOVE
            return
        }
        val widget = StickerKinds.isWidget(k)
        sync.pushSticker(StickerElement(
            emoji = FallbackEmoji[k] ?: "✨", kind = k,
            owner = if (widget) myUid() else "",
            label = if (StickerKinds.needsStats(k)) myName() else "",
            x = p.x, y = p.y, size = (if (widget) 170f else 140f) / viewScale
        ))
        haptic(Haptic.Confirm); tool = Tool.MOVE
        if (widget) requestLivePerms(k)
    }

    /** Size (canvas px) a market sticker is placed at. */
    fun placeSize(k: String) = when {
        StickerKinds.isFood(k) -> 110f
        StickerKinds.isPet(k) -> 150f
        StickerKinds.isWidget(k) -> 170f
        else -> 140f
    } / viewScale

    // Meals finish on screen by themselves; write them down and clear the empty bowls.
    LaunchedEffect(sync) {
        while (true) {
            kotlinx.coroutines.delay(1_500L)
            if (sync.sharedStickers.none { StickerKinds.isFood(it.kind) }) continue
            val world = sync.sharedStickers.toList()
            val now = System.currentTimeMillis()
            world.forEachIndexed { i, p ->
                if (!StickerKinds.isPet(p.kind)) return@forEachIndexed
                val key = sync.stickerKeys.getOrNull(i) ?: return@forEachIndexed
                Pet.settle(p, world, now)?.let { applyPet(key, it) }
            }
        }
    }

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
            plateEdit != null -> plateEdit = null
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
                        plateEdit?.let { (i, t) ->
                            sync.sharedStickers.getOrNull(i)?.let { sync.updateSticker(i, it.copy(label = t.trim().take(12))) }
                            plateEdit = null; keyboard?.hide()
                        }
                        if (trayOpen) trayOpen = false

                        // ── one-finger start ──
                        var grabbed: Pair<ElType, Int>? = null
                        val startCanvas = toCanvas(down.position)
                        when (activeTool) {
                            Tool.PEN -> if (canEdit) { livePoints.clear(); livePoints.add(startCanvas.toPoint()); redraw++ }
                            Tool.ERASER -> if (canEdit && !eraseWhole) { eraserPos = startCanvas; sync.erasePointsNear(startCanvas.x, startCanvas.y, eraserWidth / viewScale) }
                            Tool.MOVE, Tool.TEXT -> if (canEdit) {
                                grabbed = hitTest(startCanvas)
                                pitMode = isDeadPet(grabbed)
                            }
                        }
                        grabbed.let { g -> if (g == null || g.first != ElType.STICKER || sync.stickerKeys.getOrNull(g.second) != petFocus) petFocus = null }

                        // ── multi-finger tracking ──
                        var prevCount = 0
                        var prevCentroid = Offset.Zero
                        var prevSpan = 0f
                        var prevAngle = 0f
                        var multiTravel = 0f
                        var multiScale = 1f
                        var threeStart: Offset? = null
                        var threeTravel = Offset.Zero

                        // Draw-and-hold shape cleanup
                        var holdAnchor = down.position
                        var holdSince = t0
                        var snapped = false
                        var snapTried = false

                        while (true) {
                            val holdable = activeTool == Tool.PEN && canEdit && settings.shapeSnap && !snapped && !snapTried && maxPtr == 1 && livePoints.size > 3
                            val ev = if (holdable) withTimeoutOrNull(90L) { awaitPointerEvent() } else awaitPointerEvent()
                            if (ev == null) {
                                if (android.os.SystemClock.uptimeMillis() - holdSince > 520) {
                                    val clean = ShapeSnap.snap(livePoints)
                                    if (clean != null) { livePoints.clear(); livePoints.addAll(clean); snapped = true; haptic(Haptic.Confirm); redraw++ }
                                    else snapTried = true
                                }
                                continue
                            }
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
                                    Tool.PEN -> if (canEdit && livePoints.isNotEmpty() && !snapped) {
                                        if ((ch.position - holdAnchor).getDistance() > 7f) { holdAnchor = ch.position; holdSince = ch.uptimeMillis; snapTried = false }
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
                                            val canDrag = activeTool == Tool.MOVE || g.first == ElType.TEXT
                                            if (canDrag && !draggingEl && travel > 10f) { draggingEl = true; haptic(Haptic.Tick) }
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
                                livePoints.clear(); eraserPos = null; overBin = false; redraw++
                                // Two fingers on an item grab it in any tool (Instagram-story style).
                                if (canEdit && grabbed == null && pressed.size == 2) {
                                    grabbed = hitTest(toCanvas(centroid))
                                        ?: pressed.firstNotNullOfOrNull { hitTest(toCanvas(it.position)) }
                                    pitMode = isDeadPet(grabbed)
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
                                        if (draggingEl && overBin && pitMode) {
                                            val name = sync.sharedStickers.getOrNull(g.second)?.label?.ifBlank { null } ?: "little one"
                                            deleteEl(g); haptic(Haptic.Reject)
                                            Toast.makeText(context, "Rest in peace, $name 🐾", Toast.LENGTH_LONG).show()
                                        } else if (draggingEl && overBin) { deleteEl(g); haptic(Haptic.Reject) }
                                        else if (!draggingEl && g.first == ElType.TEXT) {
                                            sync.sharedTexts.getOrNull(g.second)?.let { t -> editing = TextEdit(t, g.second) }
                                        } else if (!draggingEl && g.first == ElType.STICKER && activeTool == Tool.TEXT &&
                                            sync.sharedStickers.getOrNull(g.second)?.kind?.let { StickerKinds.needsStats(it) } == true
                                        ) {
                                            // Text tool + tap on a live sticker → edit its name plate
                                            plateEdit = g.second to sync.sharedStickers[g.second].label
                                        } else if (!draggingEl && g.first == ElType.STICKER && activeTool == Tool.MOVE &&
                                            sync.sharedStickers.getOrNull(g.second)?.let { StickerKinds.needsStatus(it.kind) && it.owner == FirebaseAuth.getInstance().currentUser?.uid } == true
                                        ) {
                                            // Tap my own status avatar → change how it looks
                                            avatarEdit = g.second to AvatarLook.decode(sync.sharedStickers[g.second].look)
                                        } else if (!draggingEl && g.first == ElType.STICKER && activeTool == Tool.MOVE &&
                                            sync.sharedStickers.getOrNull(g.second)?.let { StickerKinds.needsStatus(it.kind) && it.owner.isNotEmpty() } == true
                                        ) {
                                            // Tap someone else's avatar → nudge them
                                            val other = sync.sharedStickers[g.second]
                                            val who = other.label.ifBlank { "them" }
                                            if (LiveTracker.nudge(sync.roomId, other.owner)) {
                                                haptic(Haptic.Confirm); Toast.makeText(context, "Nudged $who 👋", Toast.LENGTH_SHORT).show()
                                            } else Toast.makeText(context, "You just nudged $who. Give it a moment", Toast.LENGTH_SHORT).show()
                                        } else if (!draggingEl && g.first == ElType.STICKER && activeTool == Tool.MOVE &&
                                            sync.sharedStickers.getOrNull(g.second)?.let { StickerKinds.isCountdown(it.kind) } == true
                                        ) {
                                            // Tap a countdown → change its title, date or icon
                                            countdownEdit = g.second to sync.sharedStickers[g.second]
                                        } else if (!draggingEl && g.first == ElType.STICKER && activeTool == Tool.MOVE &&
                                            sync.sharedStickers.getOrNull(g.second)?.let { StickerKinds.isPet(it.kind) } == true
                                        ) {
                                            // Tap the pet → its thought cloud (food, health, happiness) and the food / hand tools
                                            val key = sync.stickerKeys.getOrNull(g.second)
                                            petFocus = if (petFocus == key) null else key
                                            if (petFocus != null) { petShown = petFocus; haptic(Haptic.Tick) }
                                        } else if (!draggingEl && g.first == ElType.IMAGE) {
                                            sync.sharedImages.getOrNull(g.second)?.let { img ->
                                                sync.updateImage(g.second, img.copy(style = (img.style + 1).mod(CanvasRenderer.IMAGE_STYLES)))
                                                haptic(Haptic.Tick); redraw++
                                            }
                                        }
                                    } else if (activeTool == Tool.TEXT && canEdit && isTap) {
                                        editing = TextEdit(startCanvas.x, startCanvas.y, null, "")
                                    }
                                }
                            }
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
                        draggingEl = false; overBin = false; pitMode = false
                    }
                }
        ) {
            redraw // subscribe
            frameMs; LiveBoard.version.intValue
            val nc = drawContext.canvas.nativeCanvas
            nc.save()
            nc.translate(viewOffset.x, viewOffset.y)
            nc.scale(viewScale, viewScale)
            CanvasRenderer.drawOnCanvas(
                nc, null, sync.sharedStrokes, sync.sharedStickers,
                editing?.index?.let { ei -> sync.sharedTexts.mapIndexed { i, t -> if (i == ei) t.copy(text = "") else t } } ?: sync.sharedTexts,
                sync.sharedImages,
                strokePaths = sync.strokePaths, decode = false, timeMs = frameMs, roomId = sync.roomId
            )
            // Sticker waiting for its setup sheet, shown where it was dropped
            placeAt?.let { at ->
                val proto = when {
                    petEdit?.first == null && petEdit != null -> petEdit!!.second
                    countdownEdit?.first == null && countdownEdit != null -> countdownEdit!!.second
                    avatarEdit?.first == null && avatarEdit != null -> StickerElement(kind = "w:me", owner = "me", look = avatarEdit!!.second.encode(), label = myName())
                    else -> null
                }
                if (proto != null) StickerArt.draw(nc, proto.copy(x = at.x, y = at.y, size = placeSize(proto.kind), fedAt = System.currentTimeMillis()), frameMs, StickerArt.Preview, "preview")
            }
            focusedPet(petShown)?.let { (_, pet) -> StickerArt.petThought(nc, pet, frameMs, screenW / 400f / viewScale, petAppear) }
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
            val colArgb = e.color ?: penColor.copy(alpha = penAlpha).toArgb()
            val look = textLook(e.style, colArgb, fontPx * viewScale, density)
            BasicTextField(
                value = e.value,
                onValueChange = { editing = e.copy(value = it) },
                textStyle = TextStyle(
                    color = look.fg, fontSize = with(density) { (fontPx * viewScale).toSp() },
                    fontFamily = fontFamilyFor(e.font), shadow = look.shadow,
                ),
                cursorBrush = SolidColor(c.accent),
                modifier = Modifier.offset { IntOffset(sx.toInt(), sy.toInt()) }
                    .widthIn(min = 40.dp, max = 320.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(look.bg)
                    .border(1.dp, c.accent.copy(alpha = if (look.bg == Color.Transparent) 0.6f else 0f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
                    .animateContentSize(Motion.snappy())
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

                AnimatedVisibility(chrome && !draggingEl && editing == null && plateEdit == null, enter = fadeIn() + slideInVertically { it }, exit = fadeOut() + slideOutVertically { it }) {
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

            // Name plate editor for live stickers
            AnimatedVisibility(
                plateEdit != null, modifier = Modifier.align(Alignment.BottomCenter).imePadding().navigationBarsPadding().padding(bottom = 10.dp),
                enter = fadeIn() + slideInVertically(Motion.bouncy()) { it / 2 }, exit = fadeOut() + slideOutVertically { it / 2 }
            ) {
                val pe = plateEdit
                if (pe != null) {
                    val fr = remember { FocusRequester() }
                    fun save() {
                        sync.sharedStickers.getOrNull(pe.first)?.let { sync.updateSticker(pe.first, it.copy(label = pe.second.trim().take(12))) }
                        haptic(Haptic.Confirm); plateEdit = null; keyboard?.hide()
                    }
                    Row(
                        Modifier.padding(horizontal = 12.dp).widthIn(max = 460.dp).glass(RoundedCornerShape(28.dp), strong = true).padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFFFF1CC)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Badge, null, tint = Color(0xFF2A1840), modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        BasicTextField(
                            pe.second, { if (it.length <= 12) plateEdit = pe.first to it }, singleLine = true,
                            textStyle = TextStyle(color = c.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold),
                            cursorBrush = SolidColor(c.accent),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { save() }),
                            modifier = Modifier.weight(1f).focusRequester(fr)
                        )
                        BareIconButton(Icons.Rounded.Check, { save() }, active = true, size = 44.dp, contentDescription = "Done")
                    }
                    LaunchedEffect(pe.first) { fr.requestFocus(); keyboard?.show() }
                }
            }

            // Story-style text toolbar, floating above the keyboard
            AnimatedVisibility(
                editing != null, modifier = Modifier.align(Alignment.BottomCenter).imePadding().navigationBarsPadding().padding(bottom = 10.dp),
                enter = fadeIn() + slideInVertically(Motion.bouncy()) { it / 2 }, exit = fadeOut() + slideOutVertically { it / 2 }
            ) {
                val e = editing
                if (e != null) Column(
                    Modifier.padding(horizontal = 12.dp).widthIn(max = 460.dp).glass(RoundedCornerShape(28.dp), strong = true).padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val colArgb = e.color ?: penColor.copy(alpha = penAlpha).toArgb()
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Look: cycles plain → shadow → label → dark label → outline
                        val look = textLook(e.style, colArgb, with(density) { 18.sp.toPx() }, density)
                        val src1 = remember { MutableInteractionSource() }
                        Box(
                            Modifier.pressScale(src1).size(44.dp).clip(RoundedCornerShape(14.dp)).background(c.textPrimary.copy(alpha = 0.08f))
                                .clickable(src1, null) { haptic(Haptic.Tick); editing = e.copy(style = (e.style + 1).mod(CanvasRenderer.TEXT_STYLES)) },
                            contentAlignment = Alignment.Center
                        ) {
                            AnimatedContent(e.style, transitionSpec = { (scaleIn(Motion.bouncy()) + fadeIn()) togetherWith (scaleOut() + fadeOut()) }, label = "look") { _ ->
                                Text("A", color = look.fg, fontSize = 18.sp, fontWeight = FontWeight.Bold, style = TextStyle(shadow = look.shadow),
                                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(look.bg).padding(horizontal = 6.dp))
                            }
                        }
                        // Font: cycles typefaces
                        val src2 = remember { MutableInteractionSource() }
                        Box(
                            Modifier.pressScale(src2).height(44.dp).widthIn(min = 56.dp).clip(RoundedCornerShape(14.dp)).background(c.textPrimary.copy(alpha = 0.08f))
                                .clickable(src2, null) { haptic(Haptic.Tick); editing = e.copy(font = (e.font + 1).mod(CanvasRenderer.TEXT_FONTS)) }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            AnimatedContent(e.font, transitionSpec = { (slideInVertically(Motion.bouncy()) { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut()) }, label = "font") { f ->
                                Text("Aa", color = c.textPrimary, fontSize = 18.sp, fontFamily = fontFamilyFor(f))
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        BareIconButton(Icons.Rounded.Check, { commitText() }, active = true, size = 44.dp, contentDescription = "Done")
                    }
                    ColorRow(Color(colArgb).copy(alpha = 1f)) { editing = e.copy(color = it.toArgb()); haptic(Haptic.Tick) }
                }
            }

            // Dustbin while dragging an element
            AnimatedVisibility(
                draggingEl, modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 28.dp),
                enter = fadeIn() + scaleIn(spring(0.5f, 400f)), exit = fadeOut() + scaleOut()
            ) {
                val s by animateFloatAsState(if (overBin) 1.25f else 1f, spring(0.5f, 500f), label = "bin")
                if (pitMode) {
                    // Easter egg: a dead pet gets a little pit instead of the bin.
                    Canvas(Modifier.size((150 * s).dp, (96 * s).dp)) {
                        val k = size.width / 110f
                        val nc = drawContext.canvas.nativeCanvas
                        nc.save(); nc.translate(size.width / 2, size.height * 0.62f); nc.scale(k, k)
                        StickerArt.pit(nc, frameMs, overBin)
                        nc.restore()
                    }
                } else Box(
                    Modifier.size((64 * s).dp).glass(CircleShape, strong = true, tint = if (overBin) ErrorRed.copy(alpha = 0.6f) else null),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Rounded.DeleteOutline, "Delete", tint = if (overBin) Color.White else c.textPrimary, modifier = Modifier.size((28 * s).dp)) }
            }

            if (uploading) Box(Modifier.align(Alignment.Center).size(72.dp).glass(RoundedCornerShape(24.dp), strong = true), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(28.dp), color = c.accent, strokeWidth = 2.5.dp)
            }

            // ═════════ Sheets ═════════
            GlassSheet(showStickers, { showStickers = false; dragEmoji = null; dragKind = null }, faded = dragKind != null || dragEmoji != null) {
                var tab by remember { mutableIntStateOf(0) }
                GlassSegmented(
                    listOf(Icons.Rounded.EmojiEmotions to null, Icons.Rounded.AutoAwesome to null, Icons.Rounded.Sensors to null),
                    tab, onSelect = { tab = it }, modifier = Modifier.padding(bottom = 12.dp)
                )
                AnimatedContent(
                    tab, label = "stickerTab",
                    transitionSpec = { (fadeIn() + slideInHorizontally(Motion.snappy()) { if (targetState > initialState) it / 5 else -it / 5 }) togetherWith fadeOut() }
                ) { t ->
                    when (t) {
                        0 -> {
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
                        else -> StickerMarket(
                            if (t == 1) StickerKinds.animated else StickerKinds.widgets, if (t == 1) 4 else 2,
                            dimmed = dragKind != null,
                            onDragStart = { k, pos -> dragKind = k; dragEmojiPos = pos; haptic(Haptic.Tick) },
                            onDrag = { dragEmojiPos += it },
                            onDragEnd = {
                                val k = dragKind
                                dragKind = null
                                if (k != null) {
                                    // Drop it where the floating sticker is (just above the finger).
                                    val p = toCanvas(dragEmojiPos + Offset(0f, -DRAG_LIFT))
                                    placeKind(k, Offset(p.x, p.y + placeSize(k) * 0.3f))
                                }
                            },
                            onDragCancel = { dragKind = null },
                        ) { k -> placeKind(k, null) }
                    }
                }
            }

            GlassSheet(avatarEdit != null, { avatarEdit = null; placeAt = null }) {
                val ae = avatarEdit
                if (ae != null) AvatarBuilder(ae.second, isNew = ae.first == null) { look ->
                    avatarPrefs.edit().putString("look", look.encode()).apply()
                    val idx = ae.first
                    if (idx == null) {
                        val p = placeAt ?: visibleCenter()
                        placeAt = null
                        sync.pushSticker(StickerElement(
                            emoji = FallbackEmoji["w:me"] ?: "🧍", kind = "w:me",
                            owner = FirebaseAuth.getInstance().currentUser?.uid ?: "",
                            label = FirebaseAuth.getInstance().currentUser?.displayName?.trim()?.split(' ')?.firstOrNull()?.take(12) ?: "",
                            look = look.encode(), x = p.x, y = p.y, size = 170f / viewScale
                        ))
                        tool = Tool.MOVE
                        requestLivePerms("w:me")
                    } else sync.sharedStickers.getOrNull(idx)?.let { sync.updateSticker(idx, it.copy(look = look.encode())) }
                    haptic(Haptic.Confirm); avatarEdit = null
                }
            }

            GlassSheet(petEdit != null, { petEdit = null; placeAt = null }) {
                val pe = petEdit
                if (pe != null) PetSheet(pe.second, onDraft = { name, look -> petEdit = pe.copy(second = pe.second.copy(label = name, look = look)) }) { name, look ->
                    val p = placeAt ?: visibleCenter()
                    placeAt = null
                    val now = System.currentTimeMillis()
                    val pet = StickerElement(
                        emoji = FallbackEmoji["w:pet"] ?: "🐾", kind = "w:pet", owner = myUid(),
                        label = name, look = look, x = p.x, y = p.y, size = 150f / viewScale
                    )
                    sync.pushSticker(Pet.adopt(pet, myName(), now))
                    tool = Tool.MOVE
                    haptic(Haptic.Confirm); petEdit = null
                }
            }

            GlassSheet(countdownEdit != null, { countdownEdit = null; placeAt = null }) {
                val ce = countdownEdit
                if (ce != null) CountdownBuilder(ce.second, isNew = ce.first == null) { title, until, icon ->
                    val idx = ce.first
                    if (idx == null) {
                        val p = placeAt ?: visibleCenter()
                        placeAt = null
                        sync.pushSticker(StickerElement(
                            emoji = FallbackEmoji["w:countdown"] ?: "⏳", kind = "w:countdown",
                            owner = FirebaseAuth.getInstance().currentUser?.uid ?: "",
                            label = title, look = "i$icon", until = until, x = p.x, y = p.y, size = 170f / viewScale
                        ))
                        tool = Tool.MOVE
                    } else sync.sharedStickers.getOrNull(idx)?.let { sync.updateSticker(idx, it.copy(label = title, look = "i$icon", until = until)) }
                    haptic(Haptic.Confirm); countdownEdit = null
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

        // ═════════ Pet tools: drag food anywhere, drag the hand onto the pet ═════════
        val focused = focusedPet()
        val petAlive = focused != null && !Pet.isDead(focused.second, sync.sharedStickers, System.currentTimeMillis())
        val toolsOn = petAlive && !readOnly && !draggingEl
        @Composable
        fun PetTool(which: String, label: String, align: Alignment, fromLeft: Boolean) {
            AnimatedVisibility(
                toolsOn, modifier = Modifier.align(align).navigationBarsPadding().padding(bottom = 96.dp, start = 18.dp, end = 18.dp),
                enter = slideInHorizontally(Motion.bouncy()) { if (fromLeft) -it * 2 else it * 2 } + fadeIn(),
                exit = slideOutHorizontally { if (fromLeft) -it * 2 else it * 2 } + fadeOut()
            ) {
                var chipPos by remember { mutableStateOf(Offset.Zero) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(78.dp).glass(CircleShape, strong = true)
                            .onGloballyPositioned { chipPos = it.positionInRoot() }
                            .pointerInput(which) {
                                detectDragGestures(
                                    onDragStart = { o -> toolDrag = which; toolPos = chipPos + o; haptic(Haptic.Tick) },
                                    onDrag = { ch, amt -> ch.consume(); toolPos += amt },
                                    onDragEnd = {
                                        val drop = toCanvas(toolPos + Offset(0f, -DRAG_LIFT))
                                        val now = System.currentTimeMillis()
                                        val fp = focusedPet()
                                        if (which == "food") {
                                            val sz = placeSize("w:food")
                                            sync.pushSticker(StickerElement(emoji = FallbackEmoji["w:food"] ?: "🥣", kind = "w:food", label = myName(),
                                                x = drop.x, y = drop.y + sz * 0.3f, size = sz))
                                            haptic(Haptic.Confirm)
                                        } else if (fp != null) {
                                            val (key, pet) = fp
                                            val pose = Pet.pose(pet, now, sync.sharedStickers)
                                            val reach = pet.size * pet.scale * pose.stage.scale * 0.75f + 40f / viewScale
                                            val cy = pose.y - pet.size * pet.scale * 0.35f
                                            val hit = kotlin.math.hypot(drop.x - pose.x, drop.y - cy) < reach
                                            val r = if (hit) Pet.pet(pet, sync.sharedStickers, myName(), now, pose.x, pose.y) else null
                                            if (r != null) { applyPet(key, r); haptic(Haptic.Confirm) } else haptic(Haptic.Reject)
                                        }
                                        toolDrag = null
                                    },
                                    onDragCancel = { toolDrag = null }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        val t = frameMs
                        Canvas(Modifier.size(56.dp).alpha(if (toolDrag == which) 0.25f else 1f)) {
                            val k = size.minDimension / 60f
                            val nc = drawContext.canvas.nativeCanvas
                            nc.save(); nc.translate(size.width / 2, size.height / 2); nc.scale(k, k)
                            if (which == "food") StickerArt.bowlIcon(nc, t) else { nc.translate(0f, 4f); StickerArt.hand(nc) }
                            nc.restore()
                        }
                    }
                    Text(label, color = c.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 6.dp).glass(CircleShape, strong = true).padding(horizontal = 10.dp, vertical = 3.dp))
                }
            }
        }
        PetTool("food", "Food", Alignment.BottomStart, fromLeft = true)
        PetTool("hand", "Pet", Alignment.BottomEnd, fromLeft = false)

        // ═════════ Things being dragged out (on top of everything) ═════════
        toolDrag?.let { which ->
            Canvas(Modifier.fillMaxSize()) {
                val nc = drawContext.canvas.nativeCanvas
                val k = 72.dp.toPx() / 60f
                nc.save(); nc.translate(toolPos.x, toolPos.y - DRAG_LIFT); nc.scale(k, k)
                if (which == "food") StickerArt.bowlIcon(nc, frameMs) else { nc.rotate(sin(frameMs / 120f) * 8f); StickerArt.hand(nc) }
                nc.restore()
            }
        }
        dragKind?.let { k ->
            Canvas(Modifier.fillMaxSize()) {
                val sz = placeSize(k) * viewScale
                val y = dragEmojiPos.y - DRAG_LIFT + sz * 0.3f
                val proto = StickerElement(
                    kind = k, owner = "me", x = dragEmojiPos.x, y = y, size = sz,
                    label = when { StickerKinds.isCountdown(k) -> "Until we meet"; StickerKinds.isPet(k) -> "Mochi"; StickerKinds.needsStats(k) || StickerKinds.needsStatus(k) -> myName(); else -> "" },
                    until = if (StickerKinds.isCountdown(k)) System.currentTimeMillis() + 12 * Pet.DAY else 0L,
                    fedAt = System.currentTimeMillis(),
                )
                val nc = drawContext.canvas.nativeCanvas
                nc.save(); nc.scale(1.06f, 1.06f, dragEmojiPos.x, dragEmojiPos.y - DRAG_LIFT) // lifted
                StickerArt.draw(nc, proto, frameMs, StickerArt.Preview, "preview")
                nc.restore()
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

private data class TextLook(val fg: Color, val bg: Color, val shadow: androidx.compose.ui.graphics.Shadow?)

/** Compose preview of the renderer's text looks (see CanvasRenderer.drawText). */
private fun textLook(style: Int, argb: Int, sizePx: Float, density: androidx.compose.ui.unit.Density): TextLook {
    val col = Color(argb)
    val contrast = Color(CanvasRenderer.contrastOn(argb))
    return when (style.mod(CanvasRenderer.TEXT_STYLES)) {
        1 -> TextLook(col, Color.Transparent, androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.66f), Offset(0f, sizePx * 0.06f), sizePx * 0.14f))
        2 -> TextLook(contrast, col, null)
        3 -> TextLook(col, Color.Black.copy(alpha = 0.55f), null)
        4 -> TextLook(col, Color.Transparent, androidx.compose.ui.graphics.Shadow(contrast, Offset.Zero, sizePx * 0.08f))
        else -> TextLook(col, Color.Transparent, null)
    }
}

private val fontFamilies = HashMap<Int, androidx.compose.ui.text.font.FontFamily>()
private fun fontFamilyFor(font: Int) = fontFamilies.getOrPut(font.mod(CanvasRenderer.TEXT_FONTS)) {
    androidx.compose.ui.text.font.FontFamily(androidx.compose.ui.text.font.Typeface(CanvasRenderer.typeface(font)))
}

/** Shown by older app versions that don't know animated / live stickers. */
/** Things dragged with a finger float this far (px) above it, so the finger doesn't hide them. */
private const val DRAG_LIFT = 140f

private val FallbackEmoji = mapOf(
    "a:heart" to "❤️", "a:star" to "⭐", "a:fire" to "🔥", "a:sun" to "☀️", "a:moon" to "🌙", "a:ghost" to "👻",
    "a:party" to "🎉", "a:rainbow" to "🌈", "a:love" to "💕", "w:dist" to "📍", "w:steps" to "👣", "w:km" to "🗺️",
    "w:speed" to "🏍️", "w:steps_km" to "👣", "w:speed_km" to "🏍️", "w:board" to "🏆", "w:me" to "🧍",
    "w:clock" to "🕒", "w:analog" to "🕰️", "w:countdown" to "⏳", "w:pet" to "🐾", "w:food" to "🥣",
)

/**
 * Adopt the shared pet: name, species, colour. Only shown once, when it's placed; after that
 * you look after it on the canvas (tap it), and it can't be changed into another animal.
 */
@Composable
private fun PetSheet(pet: StickerElement, onDraft: (name: String, look: String) -> Unit, onAdopt: (name: String, look: String) -> Unit) {
    val c = LocalWallpaperColors.current
    val haptic = rememberHaptics()
    var name by remember { mutableStateOf(pet.label.ifBlank { "Mochi" }) }
    var look by remember { mutableStateOf(Pet.look(pet.look)) }
    var t by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) withFrameMillis { t = it } }
    LaunchedEffect(name, look) { onDraft(name.trim(), look.encode()) }

    @Composable
    fun Label(text: String) = Text(text, color = c.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp))

    Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(bottom = 8.dp)) {
        Text("Adopt a pet", color = c.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Label("Pet")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pet.SPECIES.forEachIndexed { i, sp ->
                val sel = i == look.species
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                        .background(if (sel) c.accent.copy(alpha = 0.18f) else c.textPrimary.copy(alpha = 0.05f))
                        .border(if (sel) 2.dp else 0.dp, if (sel) c.accent else Color.Transparent, RoundedCornerShape(16.dp))
                        .clickable { haptic(Haptic.Tick); look = look.copy(species = i) }.padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Canvas(Modifier.size(60.dp)) {
                        val k = size.minDimension / 92f
                        val nc = drawContext.canvas.nativeCanvas
                        nc.save(); nc.translate(size.width / 2, size.height / 2); nc.scale(k, k)
                        StickerArt.petPreview(nc, look.copy(species = i).encode(), t, Pet.Stage.BABY)
                        nc.restore()
                    }
                    Text(sp, color = c.textSecondary, fontSize = 11.sp, maxLines = 1)
                }
            }
        }
        Label("Colour")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Pet.COLORS.forEachIndexed { i, col ->
                val sel = i == look.color
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(Color(col))
                        .border(if (sel) 3.dp else 1.dp, if (sel) c.accent else c.glassBorder, CircleShape)
                        .clickable { haptic(Haptic.Tick); look = look.copy(color = i) }
                )
            }
        }
        Label("Name")
        GlassTextField(name, { if (it.length <= 12) name = it }, placeholder = "Mochi", leadingIcon = Icons.Rounded.Pets)
        Text(
            "Everyone in this canvas shares it. Tap it to see how it's doing, drag food out for it and pet it with the hand. " +
                "It needs food and love: an unhappy pet won't eat, and too much food makes it sick. You can't change the animal later.",
            color = c.textMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp)
        )
        AccentButton({ onAdopt(name.trim().ifBlank { "Mochi" }, look.encode()) }, Modifier.fillMaxWidth().padding(top = 14.dp), text = "Adopt")
    }
}

/** Set up a countdown: title, date and time, and an icon/colour theme. */
@Composable
private fun CountdownBuilder(initial: StickerElement, isNew: Boolean, onSave: (title: String, until: Long, icon: Int) -> Unit) {
    val c = LocalWallpaperColors.current
    val haptic = rememberHaptics()
    val context = LocalContext.current
    var title by remember { mutableStateOf(initial.label) }
    var until by remember { mutableLongStateOf(initial.until) }
    var icon by remember { mutableIntStateOf(StickerArt.countdownIconOf(initial.look)) }
    var t by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) withFrameMillis { t = it } }
    val cal = remember(until) { java.util.Calendar.getInstance().apply { timeInMillis = until } }
    val is24 = remember { android.text.format.DateFormat.is24HourFormat(context) }
    val dateText = remember(until) { java.text.SimpleDateFormat("EEE, d MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(until)) }
    val timeText = remember(until, is24) { java.text.SimpleDateFormat(if (is24) "HH:mm" else "h:mm a", java.util.Locale.getDefault()).format(java.util.Date(until)) }

    @Composable
    fun Label(text: String) = Text(text, color = c.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp))

    @Composable
    fun Pick(modifier: Modifier, iconV: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
        Row(
            modifier.height(52.dp).clip(RoundedCornerShape(16.dp)).background(c.textPrimary.copy(alpha = 0.06f))
                .clickable { haptic(Haptic.Tick); onClick() }.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(iconV, null, tint = c.accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(text, color = c.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }

    Column(Modifier.fillMaxWidth().heightIn(max = 600.dp).verticalScroll(rememberScrollState()).padding(bottom = 8.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.6f).clip(RoundedCornerShape(22.dp)).background(c.textPrimary.copy(alpha = 0.05f))) {
            Canvas(Modifier.fillMaxSize()) {
                val (hw, hh) = StickerArt.halfSize("w:countdown")
                val u = minOf(size.width * 0.86f / (2 * hw), size.height * 0.8f / (2 * hh))
                val sz = u * 100f
                val st = StickerElement(kind = "w:countdown", label = title, look = "i$icon", until = until,
                    x = size.width / 2, y = size.height / 2 + sz * 0.35f, size = sz)
                StickerArt.draw(drawContext.canvas.nativeCanvas, st, t, null, null)
            }
        }
        Label("What are you counting down to?")
        GlassTextField(title, { if (it.length <= 20) title = it }, placeholder = "Until we meet", leadingIcon = Icons.Rounded.Edit)
        Label("When")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Pick(Modifier.weight(1.4f), Icons.Rounded.CalendarMonth, dateText) {
                android.app.DatePickerDialog(context, { _, y, mo, d ->
                    until = (cal.clone() as java.util.Calendar).apply { set(y, mo, d) }.timeInMillis
                }, cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH), cal.get(java.util.Calendar.DAY_OF_MONTH)).show()
            }
            Pick(Modifier.weight(1f), Icons.Rounded.Schedule, timeText) {
                android.app.TimePickerDialog(context, { _, h, mi ->
                    until = (cal.clone() as java.util.Calendar).apply { set(java.util.Calendar.HOUR_OF_DAY, h); set(java.util.Calendar.MINUTE, mi); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0) }.timeInMillis
                }, cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE), is24).show()
            }
        }
        Label("Icon")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StickerArt.COUNTDOWN_ICONS.forEachIndexed { i, name ->
                val sel = i == icon
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                        .background(if (sel) c.accent.copy(alpha = 0.18f) else c.textPrimary.copy(alpha = 0.05f))
                        .border(if (sel) 2.dp else 0.dp, if (sel) c.accent else Color.Transparent, RoundedCornerShape(16.dp))
                        .clickable { haptic(Haptic.Tick); icon = i }.padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Canvas(Modifier.size(44.dp)) {
                        val k = size.minDimension / 50f
                        val nc = drawContext.canvas.nativeCanvas
                        nc.save(); nc.translate(size.width / 2, size.height / 2); nc.scale(k, k)
                        StickerArt.countdownIcon(nc, i, t)
                        nc.restore()
                    }
                    Text(name, color = c.textSecondary, fontSize = 11.sp, maxLines = 1)
                }
            }
        }
        Text(
            "Everyone in this canvas sees the same countdown and can tap it to change it.",
            color = c.textMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp)
        )
        AccentButton({ onSave(title.trim(), until, icon) }, Modifier.fillMaxWidth().padding(top = 14.dp), text = if (isNew) "Add sticker" else "Save")
    }
}

/** Dress-up sheet for the status avatar, with a live preview of every scene. */
@Composable
private fun AvatarBuilder(initial: AvatarLook, isNew: Boolean, onSave: (AvatarLook) -> Unit) {
    val c = LocalWallpaperColors.current
    val haptic = rememberHaptics()
    var look by remember { mutableStateOf(initial) }
    var t by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) withFrameMillis { t = it } }
    val scenes = listOf(
        Status.PHONE to "Phone", Status.RIDE to "Riding", Status.WALK to "Walking", Status.MUSIC to "Music",
        Status.CHARGE to "Charging", Status.SLEEP to "Sleeping", Status.CYCLE to "Cycling", Status.AWAY to "Chilling", Status.GHOST to "Ghost",
    )
    var scene by remember { mutableStateOf(Status.PHONE) }
    val src = remember(scene) {
        object : LiveSource {
            override val myUid = "me"
            override fun stats(uid: String): LiveStats {
                val now = System.currentTimeMillis()
                return LiveStats(status = scene, statusAt = now - 17 * 60_000L, updatedAt = now,
                    battery = if (scene == Status.CHARGE) 41 else 78, charging = scene == Status.CHARGE)
            }
            override fun presence(roomId: String) = emptyMap<String, Presence>()
        }
    }
    val name = remember { FirebaseAuth.getInstance().currentUser?.displayName?.trim()?.split(' ')?.firstOrNull()?.take(12) ?: "" }

    @Composable
    fun Swatches(colors: IntArray, selected: Int, onPick: (Int) -> Unit) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            colors.forEachIndexed { i, col ->
                val sel = i == selected
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(Color(col))
                        .border(if (sel) 3.dp else 1.dp, if (sel) c.accent else c.glassBorder, CircleShape)
                        .clickable { haptic(Haptic.Tick); onPick(i) }
                )
            }
        }
    }

    @Composable
    fun Label(text: String) = Text(text, color = c.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp))

    Column(Modifier.fillMaxWidth().heightIn(max = 600.dp).verticalScroll(rememberScrollState()).padding(bottom = 8.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.3f).clip(RoundedCornerShape(22.dp)).background(c.textPrimary.copy(alpha = 0.05f))) {
            Canvas(Modifier.fillMaxSize()) {
                val (hw, hh) = StickerArt.halfSize("w:me")
                val u = minOf(size.width * 0.9f / (2 * hw), size.height * 0.86f / (2 * hh))
                val sz = u * 100f
                val st = StickerElement(kind = "w:me", owner = "me", label = name, look = look.encode(),
                    x = size.width / 2, y = size.height / 2 + sz * 0.35f + 6f, size = sz)
                StickerArt.draw(drawContext.canvas.nativeCanvas, st, t, src, "preview")
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            scenes.forEach { (k, label) ->
                val sel = k == scene
                Text(
                    label, color = if (sel) c.onAccent else c.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.clip(CircleShape).background(if (sel) c.accent else c.textPrimary.copy(alpha = 0.07f))
                        .clickable { haptic(Haptic.Tick); scene = k }.padding(horizontal = 12.dp, vertical = 7.dp)
                )
            }
        }

        if (!isNew) GhostControls { on -> scene = if (on) Status.GHOST else Status.PHONE }

        Label("Avatar")
        GlassSegmented(
            listOf(Icons.Rounded.Male to "Boy", Icons.Rounded.Female to "Girl"), look.gender,
            onSelect = { look = look.copy(gender = it) }
        )
        Label("Skin")
        Swatches(AvatarLook.SKINS, look.skin) { look = look.copy(skin = it) }
        Label("Hair")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AvatarLook.HAIR_NAMES[look.gender].forEachIndexed { i, hairName ->
                val sel = i == look.hair
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                        .background(if (sel) c.accent.copy(alpha = 0.18f) else c.textPrimary.copy(alpha = 0.05f))
                        .border(if (sel) 2.dp else 0.dp, if (sel) c.accent else Color.Transparent, RoundedCornerShape(16.dp))
                        .clickable { haptic(Haptic.Tick); look = look.copy(hair = i) }.padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Canvas(Modifier.size(48.dp)) {
                        val k = size.minDimension / 46f
                        val nc = drawContext.canvas.nativeCanvas
                        nc.save(); nc.translate(size.width / 2, size.height / 2); nc.scale(k, k)
                        StickerArt.avatarHead(nc, look.copy(hair = i), t)
                        nc.restore()
                    }
                    Text(hairName, color = c.textSecondary, fontSize = 11.sp, maxLines = 1)
                }
            }
        }
        Label("Hair colour")
        Swatches(AvatarLook.HAIR_COLORS, look.hairColor) { look = look.copy(hairColor = it) }
        Label("Outfit")
        Swatches(AvatarLook.OUTFITS, look.outfit) { look = look.copy(outfit = it) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Label("Card")
                GlassSegmented(listOf(Icons.Rounded.LightMode to "Day", Icons.Rounded.DarkMode to "Night"), look.dark, onSelect = { look = look.copy(dark = it) })
            }
            Column(Modifier.weight(1f)) {
                Label("Rides a")
                GlassSegmented(listOf(Icons.Rounded.TwoWheeler to "Bike", Icons.Rounded.Moped to "Scooter"), look.ride,
                    onSelect = { look = look.copy(ride = it); scene = Status.RIDE })
            }
        }
        Text(
            "Shows what you're up to — on the phone, riding, walking, music, charging or asleep — from your screen, charger and motion. Use ghost mode to pause, or delete the sticker to stop sharing.",
            color = c.textMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 14.dp)
        )
        AccentButton({ onSave(look) }, Modifier.fillMaxWidth().padding(top = 14.dp), text = if (isNew) "Add sticker" else "Save")
    }
}

/**
 * Ghost mode: pause sharing status, battery and location for a while, without deleting
 * stickers. Others see a ghost "on a break". [onChange] is told when it's turned on/off.
 */
@Composable
private fun GhostControls(onChange: (Boolean) -> Unit) {
    val c = LocalWallpaperColors.current
    val haptic = rememberHaptics()
    val context = LocalContext.current
    var until by remember { mutableLongStateOf(LiveTracker.ghostUntil(context)) }
    fun set(u: Long) { LiveTracker.setGhost(context, u); until = LiveTracker.ghostUntil(context); haptic(Haptic.Confirm); onChange(u > 0) }
    val nextMorning = remember {
        java.util.Calendar.getInstance().apply {
            if (get(java.util.Calendar.HOUR_OF_DAY) >= 9) add(java.util.Calendar.DAY_OF_YEAR, 1)
            set(java.util.Calendar.HOUR_OF_DAY, 9); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val on = until > 0
    val untilText = when {
        !on -> ""
        until == Long.MAX_VALUE -> "Paused until you turn it off"
        else -> {
            val is24 = android.text.format.DateFormat.is24HourFormat(context)
            val sameDay = android.text.format.DateUtils.isToday(until)
            "Paused until " + java.text.SimpleDateFormat((if (sameDay) "" else "EEE ") + (if (is24) "HH:mm" else "h:mm a"), java.util.Locale.getDefault()).format(java.util.Date(until))
        }
    }

    @Composable
    fun Chip(text: String, onClick: () -> Unit) = Text(
        text, color = c.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.clip(CircleShape).background(c.textPrimary.copy(alpha = 0.08f))
            .clickable { onClick() }.padding(horizontal = 12.dp, vertical = 8.dp)
    )

    Column(
        Modifier.fillMaxWidth().padding(top = 14.dp).clip(RoundedCornerShape(18.dp))
            .background(if (on) c.accent.copy(alpha = 0.16f) else c.textPrimary.copy(alpha = 0.05f)).padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (on) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, null, tint = if (on) c.accent else c.textSecondary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Ghost mode", color = c.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (on) untilText else "Pause sharing your status, battery and location",
                    color = c.textSecondary, fontSize = 12.sp
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (on) Chip("Turn off") { set(0L) }
            else {
                Chip("1 hour") { set(System.currentTimeMillis() + 3_600_000L) }
                Chip("Until morning") { set(nextMorning) }
                Chip("Until I turn it off") { set(Long.MAX_VALUE) }
            }
        }
    }
}

/** Grid of animated / live stickers, each previewed live with sample data. */
@Composable
private fun StickerMarket(
    kinds: List<String>, columns: Int, dimmed: Boolean = false,
    onDragStart: (String, Offset) -> Unit = { _, _ -> }, onDrag: (Offset) -> Unit = {}, onDragEnd: () -> Unit = {}, onDragCancel: () -> Unit = {},
    onPick: (String) -> Unit,
) {
    val c = LocalWallpaperColors.current
    var t by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) withFrameMillis { t = it } }
    val sampleUntil = remember { System.currentTimeMillis() + 12 * 86_400_000L + 4 * 3_600_000L + 22 * 60_000L }
    LazyVerticalGrid(
        GridCells.Fixed(columns), Modifier.fillMaxWidth().heightIn(max = 380.dp),
        contentPadding = PaddingValues(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        itemsIndexed(kinds) { i, k ->
            val src = remember { MutableInteractionSource() }
            var cellPos by remember { mutableStateOf(Offset.Zero) }
            Box(
                Modifier.enterAnim(i).aspectRatio(if (StickerKinds.isWidget(k)) 1.45f else 1f).pressScale(src, 0.9f)
                    .alpha(if (dimmed) 0.15f else 1f)
                    .clip(RoundedCornerShape(18.dp)).background(c.textPrimary.copy(alpha = 0.06f))
                    .onGloballyPositioned { cellPos = it.positionInRoot() }
                    // Hold to pick it up and drag it onto the canvas; tap drops it in the middle.
                    .pointerInput(k) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { o -> onDragStart(k, cellPos + o) },
                            onDrag = { ch, amt -> ch.consume(); onDrag(amt) },
                            onDragEnd = onDragEnd,
                            onDragCancel = onDragCancel,
                        )
                    }
                    .clickable(src, null) { onPick(k) }
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val (hw, hh) = StickerArt.halfSize(k)
                    val u = minOf(size.width * 0.86f / (2 * hw), size.height * 0.8f / (2 * hh))
                    val sz = u * 100f
                    val cd = StickerKinds.isCountdown(k)
                    val st = StickerElement(kind = k, owner = "me", x = size.width / 2, y = size.height / 2 + sz * 0.35f, size = sz,
                        label = if (cd) "Until we meet" else if (StickerKinds.isPet(k)) "Mochi" else "",
                        until = if (cd) sampleUntil else 0L,
                        fedAt = if (StickerKinds.isPet(k)) System.currentTimeMillis() else 0L)
                    StickerArt.draw(drawContext.canvas.nativeCanvas, st, t, StickerArt.Preview, "preview")
                }
            }
        }
    }
}
