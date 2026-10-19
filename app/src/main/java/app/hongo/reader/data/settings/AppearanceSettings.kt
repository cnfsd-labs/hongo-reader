package app.hongo.reader.data.settings

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The app's accent colour. [SYSTEM] takes Material You colours from the wallpaper (Android 12+). */
enum class Accent(val color: Long?) {
    // Obsidian's default accent.
    PURPLE(0xFF8A5CF5),
    BLUE(0xFF3B82F6),
    TEAL(0xFF14B8A6),
    GREEN(0xFF22C55E),
    ORANGE(0xFFF97316),
    PINK(0xFFEC4899),
    SYSTEM(null),
    ;

    companion object {
        val DEFAULT = PURPLE

        fun fromName(name: String?): Accent = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/** How the app looks. Stored in shared preferences; read once at start, then kept in memory. */
class AppearanceSettings(private val preferences: SharedPreferences) {
    private val _accent = MutableStateFlow(Accent.fromName(preferences.getString(KEY_ACCENT, null)))
    val accent: StateFlow<Accent> = _accent.asStateFlow()

    fun setAccent(accent: Accent) {
        _accent.value = accent
        preferences.edit().putString(KEY_ACCENT, accent.name).apply()
    }

    private companion object {
        const val KEY_ACCENT = "accent"
    }
}
