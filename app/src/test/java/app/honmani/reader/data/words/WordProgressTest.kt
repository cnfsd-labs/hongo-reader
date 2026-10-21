package app.honmani.reader.data.words

import org.junit.Assert.assertEquals
import org.junit.Test

class WordProgressTest {
    @Test
    fun nineReadsWithoutALookupMakeAWordKnown() {
        assertEquals(4, WordProgress.steps(4))
        assertEquals(WordProgress.KNOWN, WordProgress.steps(30))
        assertEquals(44, WordProgress.percent(4))
        assertEquals(100, WordProgress.percent(9))
    }

    @Test
    fun aLookupTakesTheReadItWasInAndTwoMoreBack() {
        // First seen: looked up once in 5 reads.
        var step = WordProgress.update(null, reads = 5, lookups = 1, learned = false)
        assertEquals(2, step.steps)
        // Read 7 more times: known.
        step = WordProgress.update(step, reads = 12, lookups = 1, learned = false)
        assertEquals(9, step.steps)
        // Read 20 more times: still known, no more than that.
        step = WordProgress.update(step, reads = 32, lookups = 1, learned = false)
        assertEquals(9, step.steps)
        // Looked up again (on its 33rd read): forgotten a little.
        step = WordProgress.update(step, reads = 33, lookups = 2, learned = false)
        assertEquals(6, step.steps)
    }

    @Test
    fun markingLearnedMakesAWordKnown_stillLearningTakesItBack() {
        var step = WordProgress.update(null, reads = 1, lookups = 1, learned = false)
        assertEquals(0, step.steps)
        step = WordProgress.update(step, reads = 1, lookups = 1, learned = true)
        assertEquals(9, step.steps)
        step = WordProgress.update(step, reads = 1, lookups = 1, learned = false)
        assertEquals(6, step.steps)
        // A learned word looked up again: the lookup clears the mark and takes its three steps only.
        step = WordProgress.update(step, reads = 1, lookups = 1, learned = true)
        step = WordProgress.update(step, reads = 2, lookups = 2, learned = false)
        assertEquals(6, step.steps)
    }

    @Test
    fun fewerReadsThanBeforeAddNothing() {
        val step = WordProgress.update(WordStep(5, 20, 0, false), reads = 3, lookups = 0, learned = false)
        assertEquals(5, step.steps)
        assertEquals(3, step.reads)
    }
}
