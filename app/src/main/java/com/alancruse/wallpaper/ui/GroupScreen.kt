package com.alancruse.wallpaper.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alancruse.wallpaper.pairing.GroupManager
import com.alancruse.wallpaper.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun GroupScreen(
    currentUid: String,
    displayName: String,
    onGroupCreated: (groupId: String, roomId: String) -> Unit,
    onGroupJoined: (groupId: String, roomId: String) -> Unit,
    onBack: () -> Unit
) {
    val view = LocalView.current
    val clipboardManager = LocalClipboardManager.current
    val groupManager = remember { GroupManager(currentUid) }

    var groupName by remember { mutableStateOf("") }
    var joinCode by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var createdCode by remember { mutableStateOf<String?>(null) }
    var createdGroupId by remember { mutableStateOf<String?>(null) }

    // Staggered entrance
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(80); visible = true }

    val headerAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(500, easing = FastOutSlowInEasing), label = "headerAlpha"
    )
    val card1Offset by animateFloatAsState(
        targetValue = if (visible) 0f else 50f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 200f), label = "card1"
    )
    val card2Offset by animateFloatAsState(
        targetValue = if (visible) 0f else 50f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 150f), label = "card2"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "grp")
    val orbFloat by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 12f,
        animationSpec = infiniteRepeatable(
            animation = tween(3500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "orbFloat"
    )

    Box(modifier = Modifier.fillMaxSize().background(DeepNavy)) {
        // Ambient orbs
        Box(
            modifier = Modifier
                .offset(x = (-40).dp, y = (-50 + orbFloat).dp)
                .size(180.dp).blur(90.dp)
                .background(ElectricViolet.copy(alpha = 0.1f), CircleShape)
        )
        Box(
            modifier = Modifier
                .offset(x = 150.dp, y = (400 - orbFloat).dp)
                .size(160.dp).blur(80.dp)
                .background(CyanAccent.copy(alpha = 0.08f), CircleShape)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Back button + Header
            Row(
                modifier = Modifier.fillMaxWidth().alpha(headerAlpha),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    onBack()
                }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = TextMuted)
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Groups",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    letterSpacing = (-0.5).sp
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Create or join a shared canvas group",
                fontSize = 13.sp, color = TextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(headerAlpha)
            )

            Spacer(modifier = Modifier.height(28.dp))

            // ── Card 1: Create Group ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { translationY = card1Offset; alpha = if (visible) 1f else 0f }
                    .clip(RoundedCornerShape(20.dp))
                    .background(CardSurface)
                    .border(1.dp, GlassBorder, RoundedCornerShape(20.dp))
                    .padding(20.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.GroupAdd, null, tint = ElectricViolet, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Create a Group", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (createdCode == null) {
                        OutlinedTextField(
                            value = groupName,
                            onValueChange = { if (it.length <= 20) groupName = it },
                            label = { Text("Group Name", color = TextMuted, fontSize = 12.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElectricViolet,
                                unfocusedBorderColor = GlassBorder,
                                cursorColor = CyanAccent,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                if (groupName.isNotBlank()) {
                                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                    isLoading = true
                                    groupManager.createGroup(groupName.trim(), displayName) { success, gid, code, err ->
                                        isLoading = false
                                        if (success && gid != null && code != null) {
                                            createdCode = code
                                            createdGroupId = gid
                                            statusMessage = "Group created!"
                                            isError = false
                                        } else {
                                            statusMessage = err ?: "Failed to create group"
                                            isError = true
                                            view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                                        }
                                    }
                                }
                            },
                            enabled = groupName.isNotBlank() && !isLoading,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize()
                                    .background(
                                        if (groupName.isNotBlank() && !isLoading)
                                            Brush.horizontalGradient(listOf(ElectricViolet, VividPurple))
                                        else Brush.horizontalGradient(listOf(ElevatedSurface, ElevatedSurface)),
                                        RoundedCornerShape(14.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                                } else {
                                    Text("Create Group", fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                                        color = if (groupName.isNotBlank()) Color.White else TextMuted)
                                }
                            }
                        }
                    } else {
                        // Show created join code
                        Text("Share this code with your group:", fontSize = 13.sp, color = TextMuted)
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            createdCode!!.forEach { ch ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(max = 52.dp)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(ElevatedSurface)
                                        .border(1.dp, ElectricViolet.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(ch.toString(), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                    clipboardManager.setText(AnnotatedString(createdCode!!))
                                    statusMessage = "Code copied!"
                                    isError = false
                                },
                                modifier = Modifier.weight(1f).height(40.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder)
                            ) {
                                Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(14.dp), tint = TextMuted)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copy", fontSize = 12.sp, color = TextMuted)
                            }

                            Button(
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                    val gid = createdGroupId ?: return@Button
                                    onGroupCreated(gid, "group_$gid")
                                },
                                modifier = Modifier.weight(1f).height(40.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize()
                                        .background(Brush.horizontalGradient(listOf(ElectricViolet, VividPurple)), RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Open Canvas", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Divider
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().alpha(headerAlpha)) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = GlassBorder)
                Text("  OR  ", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                HorizontalDivider(modifier = Modifier.weight(1f), color = GlassBorder)
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Card 2: Join Group ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { translationY = card2Offset; alpha = if (visible) 1f else 0f }
                    .clip(RoundedCornerShape(20.dp))
                    .background(CardSurface)
                    .border(1.dp, GlassBorder, RoundedCornerShape(20.dp))
                    .padding(20.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Link, null, tint = CyanAccent, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Join a Group", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = joinCode,
                        onValueChange = { if (it.length <= 6) joinCode = it.uppercase() },
                        label = { Text("6-character code", color = TextMuted, fontSize = 12.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanAccent,
                            unfocusedBorderColor = GlassBorder,
                            cursorColor = CyanAccent,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        textStyle = LocalTextStyle.current.copy(
                            fontSize = 18.sp, letterSpacing = 6.sp,
                            textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (joinCode.length == 6) {
                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                isLoading = true; isError = false; statusMessage = ""
                                groupManager.joinGroup(joinCode, displayName) { success, gid, gName, err ->
                                    isLoading = false
                                    if (success && gid != null) {
                                        statusMessage = "Joined \"$gName\"!"
                                        isError = false
                                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                        onGroupJoined(gid, "group_$gid")
                                    } else {
                                        statusMessage = err ?: "Failed to join"
                                        isError = true
                                        view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                                    }
                                }
                            }
                        },
                        enabled = joinCode.length == 6 && !isLoading,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize()
                                .background(
                                    if (joinCode.length == 6 && !isLoading)
                                        Brush.horizontalGradient(listOf(CyanAccent, TealGlow))
                                    else Brush.horizontalGradient(listOf(ElevatedSurface, ElevatedSurface)),
                                    RoundedCornerShape(14.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                            } else {
                                Text("Join Group", fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                                    color = if (joinCode.length == 6) DeepNavy else TextMuted)
                            }
                        }
                    }
                }
            }

            // Status message
            AnimatedVisibility(
                visible = statusMessage.isNotEmpty(),
                enter = slideInVertically(spring(dampingRatio = 0.7f)) + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isError) ErrorRed.copy(alpha = 0.1f) else SuccessGreen.copy(alpha = 0.1f))
                        .border(1.dp,
                            if (isError) ErrorRed.copy(alpha = 0.2f) else SuccessGreen.copy(alpha = 0.2f),
                            RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = statusMessage,
                        color = if (isError) ErrorRed else SuccessGreen,
                        fontSize = 13.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
