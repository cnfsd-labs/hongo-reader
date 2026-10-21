package app.honmani.reader.data.stats

import app.honmani.reader.data.words.ReadWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VocabularyTest {
    /** [count] words from rank [from] up, each read once. */
    private fun read(vararg bands: Pair<Int, Int>): Map<String, ReadWord> = buildMap {
        for ((from, count) in bands) for (i in 0 until count) put("w${from + i}", ReadWord(from + i, 1))
    }

    private val novel = read(1 to 400, 600 to 300, 1_200 to 500, 2_500 to 800, 5_000 to 900, 9_000 to 800, 20_000 to 500, 40_000 to 200)

    @Test
    fun knownUpToWhereOneWordInTenIsBeingLearned() {
        // Every fifth word from rank 9,000 on being learned: the band 8k–16k is where it falls below.
        val learning = novel.filter { (key, word) -> word.rank >= 9_000 && key.hashCode() % 5 == 0 }.keys
        val estimate = Vocabulary.estimate(novel, learning)

        assertTrue(estimate.words in 8_000..16_000)
        assertEquals(novel.size, estimate.met)
        assertEquals(learning.size, estimate.learning)
    }

    @Test
    fun aFewRareWordsWithoutLookupsProveLittle() {
        // Nothing looked up, but only a handful of words past rank 16,000: no credit far beyond.
        val estimate = Vocabulary.estimate(read(1 to 400, 600 to 300, 1_200 to 500, 2_500 to 800, 5_000 to 900, 9_000 to 800, 20_000 to 5), emptySet())
        assertTrue(estimate.words in 16_000..32_000)
    }

    @Test
    fun moreWordsBeingLearnedMeansFewerKnown() {
        val many = novel.filter { (key, word) -> word.rank >= 2_500 && key.hashCode() % 3 == 0 }.keys
        val few = many.take(many.size / 10).toSet()
        assertTrue(Vocabulary.estimate(novel, many).words < Vocabulary.estimate(novel, few).words)
    }
}
