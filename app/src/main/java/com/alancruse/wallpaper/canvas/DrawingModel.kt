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
    val timestamp: Long = System.currentTimeMillis()
)
