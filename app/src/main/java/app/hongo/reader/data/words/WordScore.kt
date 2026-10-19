package app.hongo.reader.data.words

import kotlin.math.log2
import kotlin.math.pow

/**
 * A word's score is the sum of its lookups, each counting half as much after every
 * [HALF_LIFE_MS]. Words the reader keeps looking up rise; words no longer looked up sink.
 *
 * Instead of the score, which changes with time, a word stores a rank key,
 * `log2(score at t) + t / half-life`, which does not: scores at any common time order the same
 * way as rank keys, so the database can sort by it.
 */
object WordScore {
    const val HALF_LIFE_MS = 14L * 24 * 60 * 60 * 1000

    /** The rank key after one more lookup at [now]; [rankKey] is null for a new word. */
    fun add(rankKey: Double?, now: Long): Double {
        val time = now.toDouble() / HALF_LIFE_MS
        val current = rankKey?.let { 2.0.pow(it - time) } ?: 0.0
        return log2(current + 1.0) + time
    }

    /** The score at [now]: 1 for a single lookup just now. */
    fun score(rankKey: Double, now: Long): Double = 2.0.pow(rankKey - now.toDouble() / HALF_LIFE_MS)
}
