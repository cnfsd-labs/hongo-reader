package app.honmani.reader.data.words

import app.honmani.reader.data.difficulty.BookWords

/**
 * How much of a book's text the reader already knows: the share of its running words (names left
 * out, as in grading) that [WordKnowledge.knows], and how many distinct words would be new.
 */
data class BookCoverage(val known: Float, val newWords: Int) {
    companion object {
        /** [vocabulary]: the reader's estimated vocabulary as a frequency rank (`VocabularyEstimate.words`). */
        fun of(words: BookWords, knowledge: WordKnowledge, vocabulary: Int): BookCoverage? {
            var all = 0L
            var known = 0L
            var newWords = 0
            for (i in 0 until words.size) {
                val uses = words.positions[i].size
                all += uses
                if (knowledge.knows(words.keys[i], words.ranks[i], vocabulary)) known += uses else newWords++
            }
            if (all == 0L) return null
            return BookCoverage(known.toFloat() / all, newWords)
        }
    }
}
