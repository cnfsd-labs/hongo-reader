package app.honmani.reader.data.stats

import android.content.SharedPreferences
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Milestones the app has seen reached, with the day it first saw each (it dates those the data
 * can't), and those still to be shown in the library. Kept in the `device` preferences, like
 * everything tied to this phone.
 */
class MilestoneStore(private val preferences: SharedPreferences) {
    private val _seen = MutableStateFlow(
        preferences.getStringSet(KEY_SEEN, null).orEmpty().mapNotNull { entry ->
            val id = entry.substringBefore(SEPARATOR)
            runCatching { id to LocalDate.parse(entry.substringAfter(SEPARATOR)) }.getOrNull()
        }.toMap(),
    )
    val seen: StateFlow<Map<String, LocalDate>> = _seen.asStateFlow()

    private val _pending = MutableStateFlow(preferences.getStringSet(KEY_PENDING, null).orEmpty().toSet())

    /** Reached since the library last showed them. */
    val pending: StateFlow<Set<String>> = _pending.asStateFlow()

    /**
     * Records [reached] milestones not seen before as seen on [today], and as pending unless this
     * is the first time: milestones reached before this screen existed aren't news.
     */
    fun record(reached: Collection<String>, today: LocalDate) {
        val first = !preferences.contains(KEY_SEEN)
        val new = reached.filterNot { it in _seen.value }
        if (new.isEmpty() && !first) return
        _seen.value += new.associateWith { today }
        if (!first) _pending.value += new
        preferences.edit()
            .putStringSet(KEY_SEEN, _seen.value.map { (id, day) -> "$id$SEPARATOR$day" }.toSet())
            .putStringSet(KEY_PENDING, _pending.value)
            .apply()
    }

    fun shown(ids: Collection<String>) {
        if (ids.none { it in _pending.value }) return
        _pending.value -= ids.toSet()
        preferences.edit().putStringSet(KEY_PENDING, _pending.value).apply()
    }

    private companion object {
        const val KEY_SEEN = "milestones_seen"
        const val KEY_PENDING = "milestones_pending"
        const val SEPARATOR = '@'
    }
}
