package com.alancruse.wallpaper.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alancruse.wallpaper.R
import com.alancruse.wallpaper.ui.theme.LocalWallpaperColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val HEART =
    "M37,69 C44,69 50,68.5 56,67 C56,67 44,60 44,52 C44,46 48,43 52,44 C54.5,44.6 56,47 56,49 " +
        "C56,47 57.5,44.6 60,44 C64,43 68,46 68,52 C68,60 58,66 56,67"
private const val SPARKLE = "M71,31 Q72,35.5 76,36.5 Q72,37.5 71,42 Q70,37.5 66,36.5 Q70,35.5 71,31 Z"

/** Cold-start animation: the logo assembles itself, then zooms away to reveal the app. */
@Composable
fun IntroOverlay(onFinished: () -> Unit) {
    val c = LocalWallpaperColors.current
    val logoIn = remember { Animatable(0f) }
    val panes = remember { Animatable(0f) }
    val heart = remember { Animatable(0f) }
    val spark = remember { Animatable(0f) }
    val title = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch { logoIn.animateTo(1f, spring(0.55f, 260f)) }
        delay(120); launch { panes.animateTo(1f, spring(0.6f, 220f)) }
        delay(200); launch { heart.animateTo(1f, tween(620, easing = FastOutSlowInEasing)) }
        delay(520); launch { spark.animateTo(1f, spring(0.4f, 400f)) }
        launch { title.animateTo(1f, tween(380)) }
        delay(560)
        exit.animateTo(1f, tween(380, easing = FastOutLinearInEasing))
        onFinished()
    }

    val heartPath = remember { PathParser().parsePathString(HEART).toPath() }
    val sparkPath = remember { PathParser().parsePathString(SPARKLE).toPath() }
    val measure = remember { PathMeasure().apply { setPath(heartPath, false) } }
    val partial = remember { Path() }

    Box(
        Modifier.fillMaxSize()
            .graphicsLayer { alpha = 1f - exit.value }
            .background(c.background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val size = 128.dp
            Box(
                Modifier.size(size)
                    .graphicsLayer {
                        val s = (0.6f + 0.4f * logoIn.value) * (1f + 0.5f * exit.value)
                        scaleX = s; scaleY = s; alpha = logoIn.value.coerceIn(0f, 1f)
                    }
                    .clip(RoundedCornerShape(size * 0.3f))
            ) {
                Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                Canvas(Modifier.matchParentSize()) {
                    // Map the 108-unit adaptive-icon viewport onto the visible 72-unit area.
                    val u = this.size.width * 1.5f / 108f
                    val o = -this.size.width * 0.25f
                    withTransform({ translate(o, o); scale(u, u, Offset.Zero) }) {
                        val p = panes.value
                        rotate(-12f * p, Offset(50f, 50f)) {
                            drawRoundRect(Color.White.copy(alpha = 0.15f * p), Offset(32f - 14f * (1 - p), 30f), Size(36f, 42f), CornerRadius(7f))
                        }
                        rotate(8f * p, Offset(57f, 57f)) {
                            drawRoundRect(Color.White.copy(alpha = 0.22f * p), Offset(40f + 14f * (1 - p), 36f), Size(34f, 42f), CornerRadius(7f))
                            drawRoundRect(Color.White.copy(alpha = 0.55f * p), Offset(40f + 14f * (1 - p), 36f), Size(34f, 42f), CornerRadius(7f), style = Stroke(1f))
                        }
                        partial.reset()
                        measure.getSegment(0f, measure.length * heart.value, partial, true)
                        drawPath(partial, Color.White, style = Stroke(4.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        val sp = spark.value
                        if (sp > 0f) withTransform({ scale(sp, sp, Offset(71f, 36.5f)) }) { drawPath(sparkPath, Color.White) }
                    }
                }
            }
            Spacer(Modifier.height(22.dp))
            Text(
                "WallPaper", color = c.textPrimary, fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp,
                modifier = Modifier.graphicsLayer { alpha = title.value; translationY = (1f - title.value) * 30f }
            )
        }
    }
}
