package app.honmani.reader.data.dictionary

import android.os.SystemClock

/**
 * Decides which progress updates reach an import's notification. Android drops a package's
 * notification updates beyond about five per second, and the starter dictionaries download at
 * the same time, so each worker posts at most one update per [intervalMillis]. A new stage (a
 * download turning into an import, the next dictionary) is always posted.
 */
internal class ProgressThrottle(
    private val intervalMillis: Long = 1_000,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) {
    private var lastStage: Any? = null
    private var lastValue = 0
    private var lastTime = 0L
    private var posted = false

    fun shouldPost(stage: Any, value: Int): Boolean {
        val now = clock()
        val post = !posted || stage != lastStage ||
            (value != lastValue && now - lastTime >= intervalMillis)
        if (post) {
            posted = true
            lastStage = stage
            lastValue = value
            lastTime = now
        }
        return post
    }
}
