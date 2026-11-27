package com.alancruse.wallpaper.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

enum class ThemeMode(val key: String) { SYSTEM("system"), LIGHT("light"), DARK("dark"), OLED("oled") }

enum class AccentOption(val key: String, val color: Color) {
    VIOLET("violet", Color(0xFF8B5CF6)),
    BLUE("blue", Color(0xFF0A84FF)),
    TEAL("teal", Color(0xFF2DD4BF)),
    GREEN("green", Color(0xFF30D158)),
    PINK("pink", Color(0xFFFF375F)),
    ORANGE("orange", Color(0xFFFF9F0A)),
    MONO("mono", Color(0xFFE5E5EA)),
}

/**
 * Observable, persisted app settings. Single instance shared by all activities so
 * changes apply live everywhere.
 */
class AppSettings private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("wallpaper_pairing", Context.MODE_PRIVATE)

    private fun bool(key: String, def: Boolean) = mutableStateOf(prefs.getBoolean(key, def))
    private fun save(key: String, v: Boolean) = prefs.edit().putBoolean(key, v).apply()

    private var _theme by mutableStateOf(
        ThemeMode.entries.firstOrNull { it.key == prefs.getString("theme_mode", "oled") } ?: ThemeMode.OLED
    )
    var themeMode: ThemeMode
        get() = _theme
        set(v) { _theme = v; prefs.edit().putString("theme_mode", v.key).apply() }

    private var _accent by mutableStateOf(
        AccentOption.entries.firstOrNull { it.key == prefs.getString("accent", "violet") } ?: AccentOption.VIOLET
    )
    var accent: AccentOption
        get() = _accent
        set(v) { _accent = v; prefs.edit().putString("accent", v.key).apply() }

    private val _blur = bool("glass_blur", true)
    var glassBlur: Boolean get() = _blur.value; set(v) { _blur.value = v; save("glass_blur", v) }

    private val _motion = bool("ambient_motion", true)
    var ambientMotion: Boolean get() = _motion.value; set(v) { _motion.value = v; save("ambient_motion", v) }

    private val _haptics = bool("haptics", true)
    var haptics: Boolean get() = _haptics.value; set(v) { _haptics.value = v; save("haptics", v) }

    private val _screenOn = bool("keep_screen_on", true)
    var keepScreenOn: Boolean get() = _screenOn.value; set(v) { _screenOn.value = v; save("keep_screen_on", v) }

    private val _smooth = bool("smooth_strokes", true)
    var smoothStrokes: Boolean get() = _smooth.value; set(v) { _smooth.value = v; save("smooth_strokes", v) }

    private val _twoTap = bool("two_finger_undo", true)
    var twoFingerUndo: Boolean get() = _twoTap.value; set(v) { _twoTap.value = v; save("two_finger_undo", v) }

    private val _threeTap = bool("three_finger_redo", true)
    var threeFingerRedo: Boolean get() = _threeTap.value; set(v) { _threeTap.value = v; save("three_finger_redo", v) }

    private val _threeSwipe = bool("three_finger_focus", true)
    var threeFingerFocus: Boolean get() = _threeSwipe.value; set(v) { _threeSwipe.value = v; save("three_finger_focus", v) }

    private val _pinchZoom = bool("pinch_zoom", true)
    var pinchZoom: Boolean get() = _pinchZoom.value; set(v) { _pinchZoom.value = v; save("pinch_zoom", v) }

    private var _brush by mutableStateOf(prefs.getFloat("default_brush", 8f))
    var defaultBrush: Float
        get() = _brush
        set(v) { _brush = v; prefs.edit().putFloat("default_brush", v).apply() }

    companion object {
        @Volatile private var instance: AppSettings? = null
        fun get(context: Context): AppSettings =
            instance ?: synchronized(this) { instance ?: AppSettings(context).also { instance = it } }
    }
}

val LocalAppSettings = staticCompositionLocalOf<AppSettings> { error("AppSettings not provided") }
