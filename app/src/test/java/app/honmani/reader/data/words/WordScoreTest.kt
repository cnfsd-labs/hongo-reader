package app.honmani.reader.data.words

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WordScoreTest {
    private val now = 1_800_000_000_000L
    private val halfLife = WordScore.HALF_LIFE_MS

    private fun keyAfter(vararg times: Long): Double =
        times.fold(null as Double?) { key, time -> WordScore.add(key, time) }!!

    @Test
    fun aSingleLookupScoresOne_andHalvesEachHalfLife() {
        val key = keyAfter(now)

        assertEquals(1.0, WordScore.score(key, now), 1e-9)
        assertEquals(0.5, WordScore.score(key, now + halfLife), 1e-9)
        assertEquals(0.25, WordScore.score(key, now + 2 * halfLife), 1e-9)
    }

    @Test
    fun lookupsAddUp() {
        val key = keyAfter(now, now, now)

        assertEquals(3.0, WordScore.score(key, now), 1e-9)
    }

    @Test
    fun olderLookupsCountLess() {
        val key = keyAfter(now - halfLife, now)

        assertEquals(1.5, WordScore.score(key, now), 1e-9)
    }

    @Test
    fun rankKeysOrderWordsLikeTheirCurrentScores() {
        // Looked up four times a month ago, versus once today and once yesterday.
        val oftenButLongAgo = keyAfter(*LongArray(4) { now - 2 * halfLife })
        val recently = keyAfter(now - 86_400_000L, now)

        assertTrue(WordScore.score(recently, now) > WordScore.score(oftenButLongAgo, now))
        assertTrue(recently > oftenButLongAgo)
        // The order holds at any later time.
        val later = now + 10 * halfLife
        assertTrue(WordScore.score(recently, later) > WordScore.score(oftenButLongAgo, later))
    }
}
