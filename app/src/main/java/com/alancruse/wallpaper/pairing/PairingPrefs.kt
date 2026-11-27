package com.alancruse.wallpaper.pairing

import android.content.Context
import android.content.SharedPreferences

/**
 * Persists pairing UIDs, group memberships, active wallpaper room,
 * and auto-switch schedule so the live wallpaper service can access
 * them without the main app being open.
 */
class PairingPrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("wallpaper_pairing", Context.MODE_PRIVATE)

    // ── Couple pairing (existing) ──

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
        // Auto-set the couple room as active wallpaper if none set
        if (activeRoomId == null) {
            val coupleRoom = computeCoupleRoomId(currentUid, partnerUid)
            setActiveWallpaper(coupleRoom, "couple")
        }
    }

    val isPaired: Boolean
        get() = !currentUid.isNullOrEmpty() && !partnerUid.isNullOrEmpty()

    /** Couple room ID computed from UIDs */
    fun getCoupleRoomId(): String? {
        val c = currentUid ?: return null
        val p = partnerUid ?: return null
        return computeCoupleRoomId(c, p)
    }

    private fun computeCoupleRoomId(uid1: String, uid2: String): String {
        return if (uid1 < uid2) "${uid1}_${uid2}" else "${uid2}_${uid1}"
    }

    // ── Active wallpaper room ──

    var activeRoomId: String?
        get() = prefs.getString(KEY_ACTIVE_ROOM_ID, null)
        private set(value) = prefs.edit().putString(KEY_ACTIVE_ROOM_ID, value).apply()

    var activeRoomType: String?
        get() = prefs.getString(KEY_ACTIVE_ROOM_TYPE, null)
        private set(value) = prefs.edit().putString(KEY_ACTIVE_ROOM_TYPE, value).apply()

    fun setActiveWallpaper(roomId: String, roomType: String) {
        prefs.edit()
            .putString(KEY_ACTIVE_ROOM_ID, roomId)
            .putString(KEY_ACTIVE_ROOM_TYPE, roomType)
            .apply()
    }

    /**
     * Returns the room ID the home screen wallpaper should display.
     * Falls back to couple room if no explicit selection.
     */
    fun getWallpaperRoomId(): String? {
        return activeRoomId ?: getCoupleRoomId()
    }

    // ── Lockscreen wallpaper room (separate from home screen) ──

    var lockscreenRoomId: String?
        get() = prefs.getString(KEY_LOCKSCREEN_ROOM_ID, null)
        private set(value) = prefs.edit().putString(KEY_LOCKSCREEN_ROOM_ID, value).apply()

    var lockscreenRoomType: String?
        get() = prefs.getString(KEY_LOCKSCREEN_ROOM_TYPE, null)
        private set(value) = prefs.edit().putString(KEY_LOCKSCREEN_ROOM_TYPE, value).apply()

    fun setLockscreenWallpaper(roomId: String, roomType: String) {
        prefs.edit()
            .putString(KEY_LOCKSCREEN_ROOM_ID, roomId)
            .putString(KEY_LOCKSCREEN_ROOM_TYPE, roomType)
            .apply()
    }

    fun clearLockscreenWallpaper() {
        prefs.edit().remove(KEY_LOCKSCREEN_ROOM_ID).remove(KEY_LOCKSCREEN_ROOM_TYPE).apply()
    }

    fun resolvedLockscreenRoomId(): String? {
        return lockscreenRoomId ?: getWallpaperRoomId()
    }

    // ── Group memberships ──

    fun getGroupRoomIds(): Set<String> {
        return prefs.getStringSet(KEY_GROUP_ROOM_IDS, emptySet()) ?: emptySet()
    }

    fun addGroupRoomId(roomId: String) {
        val current = getGroupRoomIds().toMutableSet()
        current.add(roomId)
        prefs.edit().putStringSet(KEY_GROUP_ROOM_IDS, current).apply()
    }

    fun removeGroupRoomId(roomId: String) {
        val current = getGroupRoomIds().toMutableSet()
        current.remove(roomId)
        prefs.edit().putStringSet(KEY_GROUP_ROOM_IDS, current).apply()
        // If the removed group was the active wallpaper, fall back to couple
        if (activeRoomId == roomId) {
            val coupleRoom = getCoupleRoomId()
            if (coupleRoom != null) {
                setActiveWallpaper(coupleRoom, "couple")
            } else {
                prefs.edit().remove(KEY_ACTIVE_ROOM_ID).remove(KEY_ACTIVE_ROOM_TYPE).apply()
            }
        }
    }

    // ── Auto-switch schedule ──

    var isAutoSwitchEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SWITCH_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_SWITCH_ENABLED, value).apply()

    /** JSON string: [{"roomId":"...","roomType":"...","hour":9,"minute":0,"label":"Study"},...] */
    var autoSwitchScheduleJson: String?
        get() = prefs.getString(KEY_AUTO_SWITCH_SCHEDULE, null)
        set(value) = prefs.edit().putString(KEY_AUTO_SWITCH_SCHEDULE, value).apply()

    // ── Prefs change listener for wallpaper service ──

    fun registerOnChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterOnChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    // ── Theme settings ──

    var themeMode: String
        get() = prefs.getString(KEY_THEME_MODE, "oled") ?: "oled"
        set(value) = prefs.edit().putString(KEY_THEME_MODE, value).apply()

    // ── Clear ──

    fun clear() {
        prefs.edit().clear().apply()
    }

    fun clearCoupleOnly() {
        prefs.edit()
            .remove(KEY_CURRENT_UID)
            .remove(KEY_PARTNER_UID)
            .apply()
        // If couple was the active wallpaper, switch to first group or clear
        if (activeRoomType == "couple") {
            val groups = getGroupRoomIds()
            if (groups.isNotEmpty()) {
                setActiveWallpaper(groups.first(), "group")
            } else {
                prefs.edit().remove(KEY_ACTIVE_ROOM_ID).remove(KEY_ACTIVE_ROOM_TYPE).apply()
            }
        }
    }

    companion object {
        private const val KEY_CURRENT_UID = "current_uid"
        private const val KEY_PARTNER_UID = "partner_uid"
        private const val KEY_ACTIVE_ROOM_ID = "active_room_id"
        private const val KEY_ACTIVE_ROOM_TYPE = "active_room_type"
        private const val KEY_LOCKSCREEN_ROOM_ID = "lockscreen_room_id"
        private const val KEY_LOCKSCREEN_ROOM_TYPE = "lockscreen_room_type"
        private const val KEY_GROUP_ROOM_IDS = "group_room_ids"
        private const val KEY_AUTO_SWITCH_ENABLED = "auto_switch_enabled"
        private const val KEY_AUTO_SWITCH_SCHEDULE = "auto_switch_schedule"
        private const val KEY_THEME_MODE = "theme_mode"
    }
}
