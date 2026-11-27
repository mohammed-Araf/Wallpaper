package com.alancruse.wallpaper

import android.app.Application
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener

class WallPaperApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Disk cache: canvases open instantly (and offline) after the first visit.
        try {
            FirebaseDatabase.getInstance().apply {
                setPersistenceCacheSizeBytes(64L * 1024 * 1024)
                setPersistenceEnabled(true)
            }
        } catch (_: Exception) { /* already initialised */ }
    }
}

/** One-shot read that prefers the server (avoids stale disk-cache results for membership/pairing checks). */
fun Query.addFreshSingleValueEvent(listener: ValueEventListener) {
    get().addOnSuccessListener { listener.onDataChange(it) }
        .addOnFailureListener { listener.onCancelled(DatabaseError.fromException(it)) }
}
