package com.alancruse.wallpaper.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alancruse.wallpaper.settings.AccentOption
import com.alancruse.wallpaper.settings.LocalAppSettings
import com.alancruse.wallpaper.settings.ThemeMode
import com.alancruse.wallpaper.ui.components.*
import com.alancruse.wallpaper.ui.theme.*
import com.alancruse.wallpaper.wallpaper.CanvasRenderer
import com.google.firebase.auth.FirebaseUser
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze

@Composable
fun SettingsScreen(
    currentUser: FirebaseUser,
    isPaired: Boolean,
    hasLockscreen: Boolean,
    onPair: () -> Unit,
    onUnpair: () -> Unit,
    onSignOut: () -> Unit,
    onSchedule: () -> Unit,
    onApplyWallpaper: () -> Unit,
    onClearLockscreen: () -> Unit,
    onPermissions: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val s = LocalAppSettings.current
    val c = LocalWallpaperColors.current
    val haze = remember { HazeState() }
    var confirmUnpair by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }
    var legal by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().haze(haze)) {
            AmbientBackground()
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .statusBarsPadding().padding(top = 70.dp).padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 32.dp)
            ) {
                // Account
                Row(
                    Modifier.fillMaxWidth().glass(RoundedCornerShape(26.dp)).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(56.dp).clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
                        Text((currentUser.displayName?.firstOrNull() ?: 'U').uppercase(), color = c.onAccent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(currentUser.displayName ?: "You", color = c.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(currentUser.email ?: "", color = c.textMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    BareIconButton(Icons.AutoMirrored.Rounded.Logout, { confirmSignOut = true }, tint = ErrorRed, contentDescription = "Sign out")
                }

                // Appearance
                SectionLabel("Appearance")
                GlassGroup {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        val modes = listOf(
                            ThemeMode.SYSTEM to (Icons.Rounded.BrightnessAuto to "Auto"),
                            ThemeMode.LIGHT to (Icons.Rounded.LightMode to "Light"),
                            ThemeMode.DARK to (Icons.Rounded.DarkMode to "Dark"),
                            ThemeMode.OLED to (Icons.Rounded.Contrast to "OLED"),
                        )
                        GlassSegmented(modes.map { it.second.first to it.second.second }, modes.indexOfFirst { it.first == s.themeMode }, onSelect = { s.themeMode = modes[it].first })
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            AccentOption.entries.forEach { a ->
                                val sel = a == s.accent
                                Box(
                                    Modifier.size(38.dp).clip(CircleShape).background(a.color)
                                        .border(if (sel) 3.dp else 0.dp, c.textPrimary, CircleShape)
                                        .clickable { s.accent = a },
                                    contentAlignment = Alignment.Center
                                ) { if (sel) Icon(Icons.Rounded.Check, null, tint = if (a == AccentOption.MONO) c.background else androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(18.dp)) }
                            }
                        }
                    }
                    SettingRow(Icons.Rounded.BlurOn, Tints.Teal, "Glass blur", trailing = { GlassSwitch(s.glassBlur) { s.glassBlur = it } })
                    SettingRow(Icons.Rounded.Animation, Tints.Purple, "Ambient motion", trailing = { GlassSwitch(s.ambientMotion) { s.ambientMotion = it } })
                }

                // Canvas
                SectionLabel("Canvas")
                GlassGroup {
                    SettingRow(Icons.Rounded.Gesture, Tints.Blue, "Smooth strokes", trailing = { GlassSwitch(s.smoothStrokes) { s.smoothStrokes = it } })
                    SettingRow(Icons.Rounded.Vibration, Tints.Orange, "Haptics", trailing = { GlassSwitch(s.haptics) { s.haptics = it } })
                    SettingRow(Icons.Rounded.StayCurrentPortrait, Tints.Green, "Keep screen on", trailing = { GlassSwitch(s.keepScreenOn) { s.keepScreenOn = it } })
                }

                // Gestures
                SectionLabel("Gestures")
                GlassGroup {
                    SettingRow(Icons.Rounded.Pinch, Tints.Indigo, "Pinch to zoom", trailing = { GlassSwitch(s.pinchZoom) { s.pinchZoom = it } })
                    SettingRow(Icons.Rounded.TouchApp, Tints.Blue, "2-finger tap · Undo", trailing = { GlassSwitch(s.twoFingerUndo) { s.twoFingerUndo = it } })
                    SettingRow(Icons.Rounded.TouchApp, Tints.Teal, "3-finger tap · Redo", trailing = { GlassSwitch(s.threeFingerRedo) { s.threeFingerRedo = it } })
                    SettingRow(Icons.Rounded.SwipeVertical, Tints.Purple, "3-finger swipe · Focus", trailing = { GlassSwitch(s.threeFingerFocus) { s.threeFingerFocus = it } })
                }

                // Wallpaper
                SectionLabel("Wallpaper")
                GlassGroup {
                    SettingRow(Icons.Rounded.Wallpaper, Tints.Indigo, "Apply live wallpaper", onApplyWallpaper)
                    SettingRow(Icons.Rounded.Schedule, Tints.Teal, "Schedule", onSchedule)
                    if (hasLockscreen) SettingRow(Icons.Rounded.ScreenLockPortrait, Tints.Gray, "Stop lock screen sync", onClearLockscreen)
                }

                // Partner
                SectionLabel("Partner")
                GlassGroup {
                    if (isPaired) SettingRow(Icons.Rounded.LinkOff, Tints.Pink, "Unpair", { confirmUnpair = true }, titleColor = ErrorRed)
                    else SettingRow(Icons.Rounded.Favorite, Tints.Pink, "Pair with partner", onPair)
                }

                // System
                SectionLabel("System")
                GlassGroup {
                    SettingRow(Icons.Rounded.Notifications, Tints.Red, "Permissions") {
                        onPermissions()
                    }
                    SettingRow(Icons.Rounded.CleaningServices, Tints.Gray, "Clear previews") {
                        CanvasRenderer.thumbFile(context, "x").parentFile?.listFiles()?.forEach { it.delete() }
                    }
                    SettingRow(Icons.Rounded.PrivacyTip, Tints.Blue, "Privacy") { legal = "privacy" }
                    SettingRow(Icons.Rounded.Gavel, Tints.Gray, "Terms") { legal = "terms" }
                }

                Text(
                    "WallPaper 3.0", color = c.textMuted, fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }

        CompositionLocalProvider(LocalHazeState provides haze) {
            Box(Modifier.fillMaxWidth().glass(RoundedCornerShape(0.dp), strong = true)) {
                GlassTopBar("Settings", onBack)
            }
            GlassSheet(legal != null, { legal = null }) {
                Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()).padding(bottom = 12.dp)) {
                    Text(
                        if (legal == "privacy") PRIVACY_POLICY_TEXT else TERMS_AND_CONDITIONS_TEXT,
                        color = c.textSecondary, fontSize = 14.sp, lineHeight = 20.sp
                    )
                }
            }
        }
    }

    if (confirmSignOut) TimedConfirmDialog(
        "Sign out?", "", "Sign out", ErrorRed, 3,
        onConfirm = { confirmSignOut = false; onSignOut() }, onDismiss = { confirmSignOut = false }
    )
    if (confirmUnpair) TimedConfirmDialog(
        "Unpair?", "", "Unpair", ErrorRed, 5,
        onConfirm = { confirmUnpair = false; onUnpair() }, onDismiss = { confirmUnpair = false }
    )
}

// ── Document String Constants ──

private val PRIVACY_POLICY_TEXT = """
At WallPaper, we respect your privacy. This privacy policy explains how we collect, use, and share information when you use our collaborative wallpaper drawing application.

1. Information We Collect
- Account Info: When you sign in with Google, we collect your Google UID, display name, email, and profile photo URL. This is used solely to identify your account and personalize your profile.
- Canvas Drawing Data: We store lines (points, strokes, colors), stickers, and text elements in Firebase Realtime Database. This data is required to sync canvases across your paired partner's device or group member devices.

2. How We Share Information
- With Partners/Groups: Your drawing data is shared only with your paired partner or the members of groups you choose to join.
- No Third-Party Sharing: We do not sell, rent, or share your personal data or drawing content with third-party advertisers or data brokers.

3. Data Retention
- We retain your canvas data as long as your rooms or groups exist. If a group is left by its last member, the group's canvas is permanently deleted.

4. Your Choices
- You can clear your canvas, unpair from your partner, or leave groups at any time, which deletes your access and associated database keys.
- You can sign out at any time.
""".trimIndent()

private val TERMS_AND_CONDITIONS_TEXT = """
Please read these Terms and Conditions carefully before using the WallPaper collaborative application.

1. Acceptance of Terms
- By creating an account or using the app, you agree to comply with these terms. If you do not agree, please do not use the app.

2. User Conduct & Responsibilities
- You are solely responsible for the content you draw, type, or place on the canvas.
- You agree not to create, share, or display any content that is abusive, offensive, defamatory, harassing, or violates any laws.
- Remember: Your canvas drawings sync instantly to your partner's home screen and lock screen. Please respect your partner's space.

3. Service Availability
- WallPaper is provided "as is" and "as available". We make no warranties regarding service uptime, reliability, or that the application will be completely error-free.

4. Limitation of Liability
- In no event shall the developers of WallPaper be liable for any indirect, incidental, or consequential damages arising out of your use or inability to use the application.
""".trimIndent()
