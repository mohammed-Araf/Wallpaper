package com.alancruse.wallpaper.ui

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alancruse.wallpaper.pairing.PairingPrefs
import com.alancruse.wallpaper.system.WallpaperSwitchReceiver
import com.alancruse.wallpaper.ui.theme.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

data class ScheduleSlot(
    val roomId: String,
    val roomType: String,
    val label: String,
    val hour: Int,
    val minute: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoSwitchDialog(
    availableRooms: List<Triple<String, String, String>>, // (roomId, roomType, displayName)
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val prefs = remember { PairingPrefs(context) }

    var isEnabled by remember { mutableStateOf(prefs.isAutoSwitchEnabled) }
    var slots by remember { mutableStateOf(loadSchedule(prefs)) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showRoomPicker by remember { mutableStateOf(false) }
    var pendingHour by remember { mutableIntStateOf(9) }
    var pendingMinute by remember { mutableIntStateOf(0) }

    fun saveAndApply() {
        saveSchedule(prefs, slots)
        prefs.isAutoSwitchEnabled = isEnabled
        if (isEnabled && slots.isNotEmpty()) {
            registerAlarms(context, prefs)
        } else {
            cancelAlarms(context, slots.size)
        }
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = { saveAndApply(); onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = CardSurface,
            modifier = Modifier.border(1.dp, GlassBorder, RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .animateContentSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Schedule, null, tint = CyanAccent, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Auto-Switch", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = {
                            isEnabled = it
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = ElectricViolet,
                            checkedThumbColor = Color.White,
                            uncheckedTrackColor = ElevatedSurface,
                            uncheckedThumbColor = TextMuted
                        )
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Wallpaper changes automatically at scheduled times",
                    fontSize = 11.sp, color = TextMuted
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Slot list
                if (slots.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(ElevatedSurface),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No time slots yet", fontSize = 13.sp, color = TextMuted)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 250.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(slots) { index, slot ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(ElevatedSurface)
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Time
                                Text(
                                    text = String.format("%02d:%02d", slot.hour, slot.minute),
                                    fontSize = 16.sp, fontWeight = FontWeight.Bold,
                                    color = CyanAccent, letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                // Room name
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(slot.label, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                        color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        if (slot.roomType == "couple") "❤️ Couple" else "👥 Group",
                                        fontSize = 10.sp, color = TextMuted
                                    )
                                }
                                // Remove button
                                IconButton(
                                    onClick = {
                                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                        slots = slots.toMutableList().also { it.removeAt(index) }
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Rounded.Close, "Remove", tint = WarmRose, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Add slot button
                OutlinedButton(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        showTimePicker = true
                    },
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder)
                ) {
                    Icon(Icons.Rounded.Add, null, modifier = Modifier.size(16.dp), tint = CyanAccent)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Time Slot", fontSize = 13.sp, color = TextPrimary)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Done button
                Button(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        saveAndApply()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize()
                            .background(Brush.horizontalGradient(listOf(CyanAccent, TealGlow)), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Done", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = DeepNavy)
                    }
                }
            }
        }
    }

    // Time picker dialog
    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(initialHour = pendingHour, initialMinute = pendingMinute)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            containerColor = CardSurface,
            title = { Text("Select Time", color = TextPrimary, fontWeight = FontWeight.SemiBold) },
            text = {
                TimePicker(state = timePickerState, colors = TimePickerDefaults.colors(
                    clockDialColor = ElevatedSurface,
                    selectorColor = ElectricViolet,
                    timeSelectorSelectedContainerColor = ElectricViolet.copy(alpha = 0.2f),
                    timeSelectorUnselectedContainerColor = ElevatedSurface,
                    timeSelectorSelectedContentColor = TextPrimary,
                    timeSelectorUnselectedContentColor = TextMuted
                ))
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingHour = timePickerState.hour
                    pendingMinute = timePickerState.minute
                    showTimePicker = false
                    showRoomPicker = true
                }) { Text("Next", color = CyanAccent) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel", color = TextMuted) }
            }
        )
    }

    // Room picker dialog
    if (showRoomPicker) {
        AlertDialog(
            onDismissRequest = { showRoomPicker = false },
            containerColor = CardSurface,
            title = { Text("Select Canvas", color = TextPrimary, fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    availableRooms.forEach { (roomId, roomType, label) ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(ElevatedSurface)
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                    slots = (slots + ScheduleSlot(roomId, roomType, label, pendingHour, pendingMinute))
                                        .sortedWith(compareBy({ it.hour }, { it.minute }))
                                    showRoomPicker = false
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (roomType == "couple") "❤️" else "👥",
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(label, fontSize = 14.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showRoomPicker = false }) { Text("Cancel", color = TextMuted) }
            }
        )
    }
}

// ── Persistence helpers ──

private fun loadSchedule(prefs: PairingPrefs): List<ScheduleSlot> {
    val json = prefs.autoSwitchScheduleJson ?: return emptyList()
    return try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            ScheduleSlot(
                roomId = obj.getString("roomId"),
                roomType = obj.optString("roomType", "group"),
                label = obj.optString("label", "Canvas"),
                hour = obj.getInt("hour"),
                minute = obj.getInt("minute")
            )
        }
    } catch (e: Exception) { emptyList() }
}

private fun saveSchedule(prefs: PairingPrefs, slots: List<ScheduleSlot>) {
    val arr = JSONArray()
    slots.forEach { slot ->
        arr.put(JSONObject().apply {
            put("roomId", slot.roomId)
            put("roomType", slot.roomType)
            put("label", slot.label)
            put("hour", slot.hour)
            put("minute", slot.minute)
        })
    }
    prefs.autoSwitchScheduleJson = arr.toString()
}

// ── Alarm registration ──

fun registerAlarms(context: Context, prefs: PairingPrefs) {
    val slots = loadSchedule(prefs)
    if (slots.isEmpty() || !prefs.isAutoSwitchEnabled) return

    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    slots.forEachIndexed { index, slot ->
        val intent = Intent(context, WallpaperSwitchReceiver::class.java).apply {
            putExtra("room_id", slot.roomId)
            putExtra("room_type", slot.roomType)
            putExtra("slot_index", index)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, 1000 + index, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, slot.hour)
            set(Calendar.MINUTE, slot.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, AlarmManager.INTERVAL_DAY, pendingIntent)
                } else {
                    alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, AlarmManager.INTERVAL_DAY, pendingIntent)
                }
            } else {
                alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, AlarmManager.INTERVAL_DAY, pendingIntent)
            }
        } catch (e: SecurityException) {
            alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, AlarmManager.INTERVAL_DAY, pendingIntent)
        }
    }
}

fun cancelAlarms(context: Context, count: Int) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    for (i in 0 until maxOf(count, 20)) {
        val intent = Intent(context, WallpaperSwitchReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            context, 1000 + i, intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pi?.let { alarmManager.cancel(it) }
    }
}
