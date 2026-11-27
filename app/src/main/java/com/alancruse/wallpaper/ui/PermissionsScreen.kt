package com.alancruse.wallpaper.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.alancruse.wallpaper.ui.components.*
import com.alancruse.wallpaper.ui.theme.*
import kotlinx.coroutines.delay

private data class Perm(
    val icon: ImageVector,
    val color: Color,
    val title: String,
    val granted: (Context) -> Boolean,
    val request: () -> Unit,
)

object AppPermissions {
    fun notifications(ctx: Context) = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun exactAlarms(ctx: Context) = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()

    fun battery(ctx: Context) = (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(ctx.packageName)

    fun allGranted(ctx: Context) = notifications(ctx) && exactAlarms(ctx) && battery(ctx)

    private fun granted(ctx: Context, p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
    fun activity(ctx: Context) = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || granted(ctx, Manifest.permission.ACTIVITY_RECOGNITION)
    fun locationForeground(ctx: Context) = granted(ctx, Manifest.permission.ACCESS_COARSE_LOCATION)
    /** "All the time" — needed so the wallpaper can update distance while the app is closed. */
    fun location(ctx: Context) = locationForeground(ctx) &&
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || granted(ctx, Manifest.permission.ACCESS_BACKGROUND_LOCATION))
}

@SuppressLint("BatteryLife")
@Composable
fun PermissionsScreen(onDone: () -> Unit, onBack: (() -> Unit)? = null) {
    val c = LocalWallpaperColors.current
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    val multiLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { tick++; com.alancruse.wallpaper.live.LiveTracker.refresh() }

    fun open(intent: Intent) {
        try { ctx.startActivity(intent) } catch (_: Exception) {
            ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null)))
        }
    }
    val pkg = Uri.fromParts("package", ctx.packageName, null)

    val perms = remember {
        buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(
                Perm(Icons.Rounded.Notifications, Tints.Red, "Notifications", AppPermissions::notifications) {
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(
                Perm(Icons.Rounded.Schedule, Tints.Indigo, "Scheduled switching", AppPermissions::exactAlarms) {
                    open(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg))
                }
            )
            add(Perm(Icons.Rounded.BatteryChargingFull, Tints.Green, "Background sync", AppPermissions::battery) {
                open(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg))
            })
            // Optional — only used by live stickers
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(
                Perm(Icons.Rounded.DirectionsWalk, Tints.Teal, "Steps", AppPermissions::activity) {
                    multiLauncher.launch(arrayOf(Manifest.permission.ACTIVITY_RECOGNITION))
                }
            )
            add(Perm(Icons.Rounded.NearMe, Tints.Blue, "Location", AppPermissions::location) {
                if (!AppPermissions.locationForeground(ctx)) multiLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) multiLauncher.launch(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
            })
        }
    }

    // Settings screens return without a callback — re-check while visible.
    LaunchedEffect(Unit) { while (true) { delay(800); tick++ } }
    val granted = remember(tick) { perms.map { it.granted(ctx) } }

    Box(Modifier.fillMaxSize()) {
        AmbientBackground()
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            GlassTopBar(null, onBack)
            Column(
                Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Spacer(Modifier.height(if (onBack == null) 24.dp else 0.dp))
                AppLogo(84.dp, Modifier.enterAnim(0))
                Text("Permissions", color = c.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
                Spacer(Modifier.height(4.dp))
                GlassGroup(Modifier.enterAnim(2)) {
                    perms.forEachIndexed { i, p ->
                        val ok = granted[i]
                        SettingRow(p.icon, p.color, p.title, onClick = if (ok) null else p.request, trailing = {
                            Crossfade(ok, label = "ok") { g ->
                                if (g) Icon(Icons.Rounded.CheckCircle, null, tint = SuccessGreen)
                                else Icon(Icons.Rounded.AddCircleOutline, null, tint = c.accent)
                            }
                        })
                    }
                }
            }
            val all = remember(tick) { AppPermissions.allGranted(ctx) } // steps & location are optional
            AccentButton(
                onDone, Modifier.fillMaxWidth().padding(16.dp),
                text = if (all || onBack != null) "Done" else "Later",
                icon = if (all) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.ArrowForward,
                color = if (all) null else Tints.Gray,
            )
        }
    }
}
