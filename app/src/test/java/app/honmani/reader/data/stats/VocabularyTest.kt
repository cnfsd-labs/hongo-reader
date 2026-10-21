package app.honmani.reader.data.stats

import app.honmani.reader.data.difficulty.BookWords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VocabularyTest {
    /** [count] words from rank [from] up, spread evenly over the book. */
    private fun book(vararg bands: Pair<Int, Int>): BookWords {
        val keys = ArrayList<String>()
        val ranks = ArrayList<Int>()
        for ((from, count) in bands) {
            for (i in 0 until count) {
                keys += "w${from + i}"
                ranks += from + i
            }
        }
        return BookWords(keys, ranks.toIntArray(), FloatArray(keys.size) { it.toFloat() / keys.size })
    }

    private val novel = book(1 to 400, 600 to 300, 1_200 to 500, 2_500 to 800, 5_000 to 900, 9_000 to 800, 20_000 to 500, 40_000 to 200)

    @Test
    fun knownUpToWhereOneWordInTenIsBeingLearned() {
        // Every tenth word from rank 9,000 on being learned: the band 8k–16k is where it falls below.
        val learning = novel.keys.filterIndexed { i, _ -> novel.ranks[i] >= 9_000 && i % 5 == 0 }.toSet()
        val estimate = Vocabulary.estimate(listOf(novel to 1f), learning)

        assertTrue(estimate.words in 8_000..16_000)
        assertEquals(novel.size, estimate.met)
        assertEquals(learning.size, estimate.learning)
    }

    @Test
    fun aFewRareWordsWithoutLookupsProveLittle() {
        // Nothing looked up, but only a handful of words past rank 16,000: no credit far beyond.
        val estimate = Vocabulary.estimate(listOf(book(1 to 400, 600 to 300, 1_200 to 500, 2_500 to 800, 5_000 to 900, 9_000 to 800, 20_000 to 5) to 1f), emptySet())
        assertTrue(estimate.words in 16_000..32_000)
    }

    @Test
    fun onlyWordsBeforeTheReadersPositionCount() {
        val half = Vocabulary.estimate(listOf(novel to 0.5f), emptySet())
        val whole = Vocabulary.estimate(listOf(novel to 1f), emptySet())

        assertEquals(novel.size / 2 + 1, half.met)
        assertTrue(half.words <= whole.words)
    }

    @Test
    fun learnedWordsCountAsKnown_andMoreLookupsLower() {
        val many = novel.keys.filterIndexed { i, _ -> novel.ranks[i] >= 2_500 && i % 3 == 0 }.toSet()
        val few = many.take(many.size / 10).toSet()
        assertTrue(Vocabulary.estimate(listOf(novel to 1f), many).words < Vocabulary.estimate(listOf(novel to 1f), few).words)
    }
}
