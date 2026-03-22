package com.alancruse.wallpaper.fcm

import android.content.Intent
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.alancruse.wallpaper.LiveLockscreenActivity

class WallpaperMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        
        Log.d("FCM", "Received wake-up ping from partner.")
        
        // In the future, check if penalty is active before launching
        
        val intent = Intent(this, LiveLockscreenActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "New token received: \$token")
        // TODO: Save token to Firebase User profile
    }
}
