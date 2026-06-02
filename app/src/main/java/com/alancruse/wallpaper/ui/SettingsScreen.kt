package com.alancruse.wallpaper.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.alancruse.wallpaper.ui.theme.*
import com.google.firebase.auth.FirebaseUser

@Composable
fun SettingsScreen(
    currentUser: FirebaseUser,
    isPaired: Boolean,
    partnerUid: String?,
    themeMode: String,
    onThemeModeChange: (String) -> Unit,
    onUnpair: () -> Unit,
    onSignOut: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current

    // Dialog states
    var showUnpairDialog by remember { mutableStateOf(false) }
    var showSignOutDialog by remember { mutableStateOf(false) }
    var policyToShow by remember { mutableStateOf<String?>(null) } // "privacy" or "terms" or null

    Box(modifier = Modifier.fillMaxSize().background(DeepNavy)) {
        // Aesthetic ambient orbs
        Box(
            modifier = Modifier
                .offset(x = (-40).dp, y = (-20).dp)
                .size(180.dp).blur(90.dp)
                .background(ElectricViolet.copy(alpha = 0.08f), CircleShape)
        )
        Box(
            modifier = Modifier
                .offset(x = 180.dp, y = 300.dp)
                .size(150.dp).blur(75.dp)
                .background(CyanAccent.copy(alpha = 0.05f), CircleShape)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 56.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Top Header Bar ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        onBack()
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Settings",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Settings Content List ──
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                // Section 1: Account Info
                item {
                    SettingsCard {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(
                                text = "Account Details",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElectricViolet,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Avatar placeholder
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(ElectricViolet.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val nameChar = currentUser.displayName?.firstOrNull()?.toString() ?: "U"
                                    Text(
                                        text = nameChar.uppercase(),
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ElectricViolet
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = currentUser.displayName ?: "Google User",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = currentUser.email ?: "No email linked",
                                        fontSize = 12.sp,
                                        color = TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider(color = GlassBorder, thickness = 1.dp)
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                    showSignOutDialog = true
                                },
                                modifier = Modifier.fillMaxWidth().height(40.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.4f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed)
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.Logout, null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Sign Out", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                // Section 2: Theme Settings
                item {
                    SettingsCard {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(
                                text = "App Theme",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElectricViolet,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            Text(
                                text = "Customize the interface style to match your preference.",
                                fontSize = 11.sp,
                                color = TextMuted,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(ElevatedSurface)
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val themeOptions = listOf(
                                    "light" to "☀️ Light",
                                    "dark" to "🌙 Dark",
                                    "system" to "⚙️ System"
                                )
                                themeOptions.forEach { (mode, label) ->
                                    val isSelected = themeMode == mode
                                    val bg = if (isSelected) ElectricViolet else Color.Transparent
                                    val textCol = if (isSelected) Color.White else TextSecondary
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(36.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(bg)
                                            .clickable {
                                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                                onThemeModeChange(mode)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = textCol
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Section 3: Connection & Pairing
                item {
                    SettingsCard {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(
                                text = "Connection Status",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElectricViolet,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            if (isPaired) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        Icons.Rounded.Link,
                                        contentDescription = "Connected",
                                        tint = SuccessGreen,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Paired with partner",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = TextPrimary
                                    )
                                }
                                if (!partnerUid.isNullOrEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Partner ID: $partnerUid",
                                        fontSize = 10.sp,
                                        color = TextMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                OutlinedButton(
                                    onClick = {
                                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                        showUnpairDialog = true
                                    },
                                    modifier = Modifier.fillMaxWidth().height(40.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, WarmRose.copy(alpha = 0.4f)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = WarmRose)
                                ) {
                                    Icon(Icons.Rounded.LinkOff, null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Unpair from Partner", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        Icons.Rounded.LinkOff,
                                        contentDescription = "Not Connected",
                                        tint = TextMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "No partner linked",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = TextSecondary
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "To draw on a shared canvas, generate a pairing code on the home page and share it with your partner.",
                                    fontSize = 11.sp,
                                    color = TextMuted,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }

                // Section 4: System Permissions
                item {
                    SettingsCard {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(
                                text = "App Permissions",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElectricViolet,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            Text(
                                text = "Adjust notification visibility, background live wallpaper configurations, or camera and draw overlays in system settings.",
                                fontSize = 11.sp,
                                color = TextMuted,
                                lineHeight = 16.sp,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            Button(
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", context.packageName, null)
                                    }
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.fillMaxWidth().height(40.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ElevatedSurface)
                            ) {
                                Icon(Icons.Rounded.OpenInNew, null, tint = TextPrimary, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Open System App Settings", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            }
                        }
                    }
                }

                // Section 5: Documents & Info
                item {
                    SettingsCard {
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(
                                text = "About & Legal",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElectricViolet,
                                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                            )
                            ListItemRow(
                                title = "Privacy Policy",
                                icon = Icons.Rounded.PrivacyTip,
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                    policyToShow = "privacy"
                                }
                            )
                            HorizontalDivider(color = GlassBorder, thickness = 0.5.dp)
                            ListItemRow(
                                title = "Terms & Conditions",
                                icon = Icons.Rounded.Description,
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                    policyToShow = "terms"
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Footer Version Info
            Text(
                text = "WallPaper v2.3.0 · Made with ❤️",
                fontSize = 11.sp,
                color = TextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            )
        }
    }

    // ── Dialog overlays ──

    // Sign Out Timed Confirm Dialog
    if (showSignOutDialog) {
        TimedConfirmDialog(
            title = "Sign out of your account?",
            message = "You will be signed out from Google. Your canvases will remain in Firebase, but you will need to sign in again to access them.",
            confirmLabel = "Sign Out",
            accentColor = ErrorRed,
            waitSeconds = 5,
            onConfirm = {
                showSignOutDialog = false
                onSignOut()
            },
            onDismiss = { showSignOutDialog = false }
        )
    }

    // Unpair Timed Confirm Dialog
    if (showUnpairDialog) {
        TimedConfirmDialog(
            title = "Unpair from partner?",
            message = "This will delete the link between your accounts. Your shared partner canvas will no longer sync wallpaper updates.",
            confirmLabel = "Unpair",
            accentColor = WarmRose,
            waitSeconds = 5,
            onConfirm = {
                showUnpairDialog = false
                onUnpair()
            },
            onDismiss = { showUnpairDialog = false }
        )
    }

    // Legal Document Modal Dialog
    policyToShow?.let { mode ->
        val title = if (mode == "privacy") "Privacy Policy" else "Terms & Conditions"
        val content = if (mode == "privacy") PRIVACY_POLICY_TEXT else TERMS_AND_CONDITIONS_TEXT

        Dialog(onDismissRequest = { policyToShow = null }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.8f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(CardSurface)
                    .border(1.dp, GlassBorder, RoundedCornerShape(24.dp))
                    .padding(24.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            item {
                                Text(
                                    text = content,
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { policyToShow = null },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet),
                        modifier = Modifier.fillMaxWidth().height(42.dp)
                    ) {
                        Text("Close", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CardSurface)
            .border(1.dp, GlassBorder, RoundedCornerShape(18.dp))
    ) {
        content()
    }
}

@Composable
private fun ListItemRow(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary,
            modifier = Modifier.weight(1f)
        )
        Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(18.dp)
        )
    }
}

// ── Document String Constants ──

private val PRIVACY_POLICY_TEXT = """
At WallPaper, we respect your privacy. This privacy policy explains how we collect, use, and share information when you use our collaborative wallpaper drawing application.

1. **Information We Collect**
- **Account Info**: When you sign in with Google, we collect your Google UID, display name, email, and profile photo URL. This is used solely to identify your account and personalize your profile.
- **Canvas Drawing Data**: We store lines (points, strokes, colors), stickers, and text elements in Firebase Realtime Database. This data is required to sync canvases across your paired partner's device or group member devices.

2. **How We Share Information**
- **With Partners/Groups**: Your drawing data is shared only with your paired partner or the members of groups you choose to join.
- **No Third-Party Sharing**: We do not sell, rent, or share your personal data or drawing content with third-party advertisers or data brokers.

3. **Data Retention**
- We retain your canvas data as long as your rooms or groups exist. If a group is left by its last member, the group's canvas is permanently deleted.

4. **Your Choices**
- You can clear your canvas, unpair from your partner, or leave groups at any time, which deletes your access and associated database keys.
- You can sign out at any time.
""".trimIndent()

private val TERMS_AND_CONDITIONS_TEXT = """
Please read these Terms and Conditions carefully before using the WallPaper collaborative application.

1. **Acceptance of Terms**
- By creating an account or using the app, you agree to comply with these terms. If you do not agree, please do not use the app.

2. **User Conduct & Responsibilities**
- You are solely responsible for the content you draw, type, or place on the canvas.
- You agree not to create, share, or display any content that is abusive, offensive, defamatory, harassing, or violates any laws.
- Remember: Your canvas drawings sync instantly to your partner's home screen and lock screen. Please respect your partner's space.

3. **Service Availability**
- WallPaper is provided "as is" and "as available". We make no warranties regarding service uptime, reliability, or that the application will be completely error-free.

4. **Limitation of Liability**
- In no event shall the developers of WallPaper be liable for any indirect, incidental, or consequential damages arising out of your use or inability to use the application.
""".trimIndent()
