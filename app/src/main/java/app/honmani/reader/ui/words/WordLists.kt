package app.honmani.reader.ui.words

import app.honmani.reader.data.words.WordEntity
import app.honmani.reader.data.words.WordKnowledge
import app.honmani.reader.data.words.WordProgress
import app.honmani.reader.data.words.WordStatus

/**
 * The Words tab's two lists. [learning]: saved words not known yet, starred first, then the most
 * looked up (recently). [known]: saved words known (read often enough, or marked), and words
 * known from reading alone ([knownByReading], never looked up, most read first).
 */
data class WordLists(
    val learning: List<WordEntity>,
    val known: List<WordEntity>,
    val knownByReading: List<Pair<String, Int>>,
    /** Progress in steps of [WordProgress.KNOWN], by word id. */
    val steps: Map<Long, Int>,
) {
    val knownCount: Int get() = known.size + knownByReading.size

    companion object {
        /** [words] in the database's order; without [knowledge] (still worked out) by the learned mark alone. */
        fun of(words: List<WordEntity>, knowledge: WordKnowledge?): WordLists {
            if (knowledge == null) {
                val (known, learning) = words.partition { it.status == WordStatus.LEARNED }
                return WordLists(learning.sortedByDescending { it.starred }, known, emptyList(), emptyMap())
            }
            val (known, learning) = words.partition(knowledge::isKnown)
            return WordLists(
                learning = learning.sortedWith(compareByDescending<WordEntity> { it.starred }.thenByDescending { it.rankKey }),
                known = known.sortedBy { it.expression },
                knownByReading = knowledge.knownByReading(words.mapTo(HashSet()) { it.expression }),
                steps = words.associate { it.id to knowledge.steps(it) },
            )
        }
    }
}
