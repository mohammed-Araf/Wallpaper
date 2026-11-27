package com.alancruse.wallpaper.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alancruse.wallpaper.ui.components.*
import com.alancruse.wallpaper.ui.theme.*

@Composable
fun AuthScreen(onSignInClick: () -> Unit) {
    val c = LocalWallpaperColors.current
    val breathe by rememberInfiniteTransition(label = "b").animateFloat(0.96f, 1.04f, infiniteRepeatable(tween(2800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "s")
    Box(Modifier.fillMaxSize()) {
        AmbientBackground()
        Column(Modifier.fillMaxSize().padding(24.dp).navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(1f))
            Box(Modifier.scale(breathe).size(120.dp).glass(RoundedCornerShape(38.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Draw, null, tint = c.accent, modifier = Modifier.size(58.dp))
            }
            Spacer(Modifier.height(28.dp))
            Text("WallPaper", color = c.textPrimary, fontSize = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
            Spacer(Modifier.weight(1.2f))
            AccentButton(onSignInClick, Modifier.fillMaxWidth(), text = "Continue with Google", icon = Icons.Rounded.Login)
            Spacer(Modifier.height(16.dp))
        }
    }
}
