package com.alancruse.wallpaper.ui

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.alancruse.wallpaper.pairing.PairingPrefs
import com.alancruse.wallpaper.system.WallpaperSwitchReceiver
import com.alancruse.wallpaper.ui.components.*
import com.alancruse.wallpaper.ui.theme.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

data class ScheduleSlot(val roomId: String, val roomType: String, val label: String, val hour: Int, val minute: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoSwitchDialog(
    availableRooms: List<Triple<String, String, String>>, // (roomId, roomType, displayName)
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val c = LocalWallpaperColors.current
    val prefs = remember { PairingPrefs(context) }
    var enabled by remember { mutableStateOf(prefs.isAutoSwitchEnabled) }
    var slots by remember { mutableStateOf(loadSchedule(prefs)) }
    var step by remember { mutableIntStateOf(0) } // 0 list, 1 time, 2 room
    var pendingH by remember { mutableIntStateOf(9) }
    var pendingM by remember { mutableIntStateOf(0) }

    fun saveAndClose() {
        saveSchedule(prefs, slots)
        prefs.isAutoSwitchEnabled = enabled
        cancelAlarms(context, 20)
        if (enabled && slots.isNotEmpty()) registerAlarms(context, prefs)
        onDismiss()
    }

    Dialog(onDismissRequest = { if (step == 0) saveAndClose() else step = 0 }) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(c.cardSurface).padding(20.dp)) {
            when (step) {
                0 -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Rounded.Schedule, Tints.Teal)
                        Spacer(Modifier.width(12.dp))
                        Text("Schedule", color = c.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        GlassSwitch(enabled) { enabled = it }
                    }
                    Spacer(Modifier.height(16.dp))
                    LazyColumn(Modifier.heightIn(max = 300.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(slots) { i, slot ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.textPrimary.copy(alpha = 0.06f)).padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(String.format("%02d:%02d", slot.hour, slot.minute), color = c.accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(14.dp))
                                Icon(if (slot.roomType == "couple") Icons.Rounded.Favorite else Icons.Rounded.Group, null, tint = c.textMuted, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(slot.label, color = c.textPrimary, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                BareIconButton(Icons.Rounded.Close, { slots = slots.toMutableList().also { it.removeAt(i) } }, size = 38.dp, tint = c.textMuted)
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AccentButton({ step = 1 }, Modifier.weight(1f), icon = Icons.Rounded.Add, color = c.textPrimary.copy(alpha = 0.12f), height = 50.dp)
                        AccentButton({ saveAndClose() }, Modifier.weight(1f), icon = Icons.Rounded.Check, height = 50.dp)
                    }
                }
                1 -> {
                    val state = rememberTimePickerState(pendingH, pendingM)
                    TimePicker(state, colors = TimePickerDefaults.colors(
                        clockDialColor = c.textPrimary.copy(alpha = 0.06f), selectorColor = c.accent,
                        timeSelectorSelectedContainerColor = c.accent.copy(alpha = 0.25f),
                        timeSelectorUnselectedContainerColor = c.textPrimary.copy(alpha = 0.06f),
                        timeSelectorSelectedContentColor = c.textPrimary, timeSelectorUnselectedContentColor = c.textMuted,
                        periodSelectorSelectedContainerColor = c.accent.copy(alpha = 0.25f),
                        clockDialSelectedContentColor = c.onAccent, clockDialUnselectedContentColor = c.textSecondary,
                    ))
                    AccentButton({ pendingH = state.hour; pendingM = state.minute; step = 2 }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Check, height = 50.dp)
                }
                else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    availableRooms.forEach { (roomId, roomType, label) ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.textPrimary.copy(alpha = 0.06f))
                                .clickable {
                                    slots = (slots + ScheduleSlot(roomId, roomType, label, pendingH, pendingM)).sortedWith(compareBy({ it.hour }, { it.minute }))
                                    step = 0
                                }.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(if (roomType == "couple") Icons.Rounded.Favorite else Icons.Rounded.Group, null, tint = c.accent, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(label, color = c.textPrimary, fontSize = 16.sp)
                        }
                    }
                }
            }
        }
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
