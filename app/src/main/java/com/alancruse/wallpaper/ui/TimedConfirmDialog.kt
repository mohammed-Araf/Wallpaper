package com.alancruse.wallpaper.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.alancruse.wallpaper.ui.theme.*
import kotlinx.coroutines.delay

/** Destructive confirmation; the confirm button unlocks after [waitSeconds]. */
@Composable
fun TimedConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String = "Confirm",
    accentColor: Color = ErrorRed,
    waitSeconds: Int = 3,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val c = LocalWallpaperColors.current
    var remaining by remember { mutableIntStateOf(waitSeconds) }
    LaunchedEffect(Unit) { while (remaining > 0) { delay(1000); remaining-- } }
    val progress by animateFloatAsState(
        if (remaining <= 0) 1f else (waitSeconds - remaining).toFloat() / waitSeconds, tween(900), label = "p"
    )
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(c.cardSurface).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(accentColor.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.WarningAmber, null, tint = accentColor, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(title, color = c.textPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            if (message.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(message, color = c.textSecondary, fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 19.sp)
            }
            Spacer(Modifier.height(22.dp))
            Box(
                Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp)).background(accentColor.copy(alpha = 0.15f))
                    .clickable(enabled = remaining <= 0) { onConfirm() },
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(progress).align(Alignment.CenterStart).background(accentColor))
                Text(
                    if (remaining > 0) "$confirmLabel · $remaining" else confirmLabel,
                    color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp
                )
            }
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center
            ) { Text("Cancel", color = c.textSecondary, fontSize = 16.sp, fontWeight = FontWeight.Medium) }
        }
    }
}
