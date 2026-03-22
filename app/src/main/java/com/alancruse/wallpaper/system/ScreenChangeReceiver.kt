package com.alancruse.wallpaper.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class ScreenChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_SCREEN_OFF) {
            Log.d("ScreenChangeReceiver", "Screen turned off. Penalty logic goes here.")
            // TODO: Write to Firebase that the partner manually turned off the screen
            // Set 1-hour penalty timestamp
        }
    }
}
