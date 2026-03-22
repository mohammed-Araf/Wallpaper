package com.alancruse.wallpaper.pairing

import android.content.Context
import android.content.SharedPreferences

/**
 * Persists pairing UIDs so the live wallpaper service can access them
 * without the main app being open.
 */
class PairingPrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("wallpaper_pairing", Context.MODE_PRIVATE)

    var currentUid: String?
        get() = prefs.getString(KEY_CURRENT_UID, null)
        set(value) = prefs.edit().putString(KEY_CURRENT_UID, value).apply()

    var partnerUid: String?
        get() = prefs.getString(KEY_PARTNER_UID, null)
        set(value) = prefs.edit().putString(KEY_PARTNER_UID, value).apply()

    fun savePairing(currentUid: String, partnerUid: String) {
        prefs.edit()
            .putString(KEY_CURRENT_UID, currentUid)
            .putString(KEY_PARTNER_UID, partnerUid)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    val isPaired: Boolean
        get() = !currentUid.isNullOrEmpty() && !partnerUid.isNullOrEmpty()

    companion object {
        private const val KEY_CURRENT_UID = "current_uid"
        private const val KEY_PARTNER_UID = "partner_uid"
    }
}
