package com.alancruse.wallpaper.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.alancruse.wallpaper.pairing.PairingPrefs
import com.alancruse.wallpaper.ui.registerAlarms

/**
 * Receives AlarmManager broadcasts to switch the active wallpaper canvas
 * at scheduled times. Also handles BOOT_COMPLETED to re-register alarms.
 */
class WallpaperSwitchReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = PairingPrefs(context)

        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Re-register alarms after reboot
            Log.d("WallpaperSwitch", "Boot completed — re-registering alarms")
            if (prefs.isAutoSwitchEnabled) {
                registerAlarms(context, prefs)
            }
            return
        }

        // Scheduled auto-switch
        val roomId = intent.getStringExtra("room_id") ?: return
        val roomType = intent.getStringExtra("room_type") ?: "group"
        val slotIndex = intent.getIntExtra("slot_index", -1)

        Log.d("WallpaperSwitch", "Switching wallpaper to room=$roomId type=$roomType (slot $slotIndex)")

        prefs.setActiveWallpaper(roomId, roomType)
        // The LiveWallpaperService's SharedPreferences listener will pick this up
    }
}
