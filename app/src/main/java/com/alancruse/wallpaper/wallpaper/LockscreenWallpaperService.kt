package com.alancruse.wallpaper.wallpaper

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.alancruse.wallpaper.pairing.PairingPrefs

/**
 * Fallback host for [LockSync] when the live wallpaper isn't applied
 * (lives as long as Android keeps the app process). When the live wallpaper
 * is applied, its service hosts LockSync and keeps it alive with no notification.
 */
class LockscreenWallpaperService : Service() {

    companion object {
        fun start(context: Context) {
            try { context.startService(Intent(context, LockscreenWallpaperService::class.java)) } catch (_: Exception) {}
        }
        fun stop(context: Context) {
            context.stopService(Intent(context, LockscreenWallpaperService::class.java))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() { super.onCreate(); LockSync.acquire(this) }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (PairingPrefs(this).lockscreenRoomId == null) { stopSelf(); return START_NOT_STICKY }
        return START_STICKY
    }

    override fun onDestroy() { LockSync.release(); super.onDestroy() }
}
