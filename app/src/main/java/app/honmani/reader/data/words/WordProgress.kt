package app.honmani.reader.data.words

import kotlin.math.roundToInt

/** A looked-up word's progress, with what it was worked out from (to tell what is new next time). */
data class WordStep(val steps: Int, val reads: Int, val lookups: Int, val learned: Boolean)

/**
 * How well the reader knows a word, in steps from 0 to [KNOWN]: each time the word is read is a
 * step up, and each lookup takes [LOOKUP_COST] steps back (the read it happened in and two more:
 * a word looked up again is being forgotten). A word read [KNOWN] times without a lookup is known.
 * Marking a word as learned makes it known at once; a lookup afterwards counts as usual.
 *
 * Reads come from the books' word positions up to the reader's place, lookups from the word list;
 * between two updates the reads are counted before the lookups.
 */
object WordProgress {
    const val KNOWN = 9
    const val LOOKUP_COST = 3

    fun percent(steps: Int): Int = (steps * 100.0 / KNOWN).roundToInt()

    /** Words never looked up: known once read [KNOWN] times. */
    fun steps(reads: Int): Int = reads.coerceIn(0, KNOWN)

    /** [previous] (null: first seen) brought up to [reads], [lookups] and the learned mark. */
    fun update(previous: WordStep?, reads: Int, lookups: Int, learned: Boolean): WordStep {
        if (previous == null) {
            val steps = if (learned) KNOWN else (reads - LOOKUP_COST * lookups).coerceIn(0, KNOWN)
            return WordStep(steps, reads, lookups, learned)
        }
        // Fewer reads than before (a book removed or started over): nothing to add.
        var steps = (previous.steps + (reads - previous.reads).coerceAtLeast(0)).coerceAtMost(KNOWN)
        val newLookups = (lookups - previous.lookups).coerceAtLeast(0)
        steps = (steps - LOOKUP_COST * newLookups).coerceAtLeast(0)
        if (learned && !previous.learned) steps = KNOWN
        // "Still learning" by hand: one lookup's worth below known. A lookup also clears the mark;
        // it already took its steps.
        if (!learned && previous.learned && newLookups == 0) steps = steps.coerceAtMost(KNOWN - LOOKUP_COST)
        return WordStep(steps, reads, lookups, learned)
    }
}
