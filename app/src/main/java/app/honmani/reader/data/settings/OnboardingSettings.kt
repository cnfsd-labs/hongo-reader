package app.honmani.reader.data.settings

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Whether the first-start screen was finished (or skipped); it shows only once. */
class OnboardingSettings(private val preferences: SharedPreferences) {
    private val _done = MutableStateFlow(preferences.getBoolean(KEY_DONE, false))
    val done: StateFlow<Boolean> = _done.asStateFlow()

    fun finish() {
        if (_done.value) return
        _done.value = true
        preferences.edit().putBoolean(KEY_DONE, true).apply()
    }

    private companion object {
        const val KEY_DONE = "onboarding_done"
    }
}
