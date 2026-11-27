package com.alancruse.wallpaper.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

// ── Strokes ──

data class DrawingStroke(
    val colorArgb: Int = Color.White.toArgb(),
    val strokeWidth: Float = 10f,
    val path: List<Point> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

data class Point(val x: Float = 0f, val y: Float = 0f) {
    fun toOffset() = Offset(x, y)
}

fun Offset.toPoint() = Point(this.x, this.y)

// ── Stickers (emoji placed on canvas) ──

data class StickerElement(
    val emoji: String = "❤️",
    val x: Float = 0f,
    val y: Float = 0f,
    val size: Float = 80f,
    val rotation: Float = 0f,
    val scale: Float = 1f,
    /** "" = emoji · "a:*" = animated cartoon · "w:*" = live widget (see StickerKinds). */
    val kind: String = "",
    /** For stat widgets: whose numbers it shows. */
    val owner: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

// ── Text elements ──

data class TextElement(
    val text: String = "",
    val x: Float = 0f,
    val y: Float = 0f,
    val colorArgb: Int = Color.White.toArgb(),
    val fontSize: Float = 48f,
    val rotation: Float = 0f,
    val scale: Float = 1f,
    /** Look preset: 0 plain · 1 shadow · 2 solid label · 3 dark label · 4 outline. */
    val style: Int = 0,
    /** 0 sans · 1 serif · 2 handwriting · 3 mono · 4 condensed. */
    val font: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)

// ── Image elements (Base64 encoded, stored in RTDB — no Firebase Storage needed) ──

data class ImageElement(
    val imageBase64: String = "",
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 300f,
    val height: Float = 300f,
    val rotation: Float = 0f,
    val scale: Float = 1f,
    /** Frame preset: 0 plain · 1 rounded · 2 white border · 3 rounded + shadow · 4 circle. */
    val style: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)
