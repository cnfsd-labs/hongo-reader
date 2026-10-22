package app.honmani.reader.data.stats

import app.honmani.reader.data.difficulty.Difficulty
import app.honmani.reader.data.difficulty.StoredDifficulty
import app.honmani.reader.data.library.BookEntity
import app.honmani.reader.data.words.WordKnowledge
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * The reader's skill as Progress, the book sheet and the library show it: fluency from reading
 * speed in graded books ([Skill.estimate]) and the vocabulary from the words read in them
 * ([Vocabulary], from [WordKnowledge]), combined ([Skill.combine]).
 */
class SkillTracker(
    days: Flow<List<ReadingDayEntity>>,
    books: Flow<List<BookEntity>>,
    /** Book grades by content hash. */
    grades: Flow<Map<String, StoredDifficulty>>,
    knowledge: Flow<WordKnowledge>,
    private val today: () -> LocalDate = LocalDate::now,
) {
    val estimate: Flow<SkillEstimate> = combine(days, books, grades, knowledge) { rows, books, grades, knowledge ->
        val graded = books.mapNotNull { book ->
            grades[book.contentHash]?.takeIf { it.difficulty.score > 0 }?.let { book to it }
        }
        val scores = graded.associate { (book, grade) -> book.id to grade.difficulty.score }
        Skill.combine(Skill.estimate(rows, scores::get, today()), vocabulary(graded, knowledge))
    }

    private fun vocabulary(graded: List<Pair<BookEntity, StoredDifficulty>>, knowledge: WordKnowledge): VocabularySkill? {
        if (knowledge.read.isEmpty()) return null
        val estimate = Vocabulary.estimate(knowledge.read, knowledge.learning)
        // The kanji and sentences of the books read, weighted by how much of each was read: the
        // score is the difficulty of such a book with the reader's vocabulary as its R95.
        var weights = 0.0
        var kanji = 0.0
        var sentence = 0.0
        for ((book, grade) in graded) {
            val share = knowledge.readShares[book.contentHash] ?: continue
            val difficulty = grade.difficulty
            val weight = share * difficulty.characters.coerceAtLeast(1).toDouble()
            weights += weight
            kanji += difficulty.kanjiShare * weight
            sentence += difficulty.sentenceLength * weight
        }
        if (weights <= 0.0) return null
        val score = Difficulty.scoreOfRank(estimate.words, kanji / weights, sentence / weights)
        return VocabularySkill(score, estimate)
    }
}
