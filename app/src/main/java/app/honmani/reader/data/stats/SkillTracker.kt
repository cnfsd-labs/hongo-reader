package app.honmani.reader.data.stats

import app.honmani.reader.data.difficulty.BookWords
import app.honmani.reader.data.difficulty.Difficulty
import app.honmani.reader.data.difficulty.StoredDifficulty
import app.honmani.reader.data.library.BookEntity
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn

/**
 * The reader's skill as Progress, the book sheet and the library show it: fluency from reading
 * speed in graded books ([Skill.estimate]) and the vocabulary from the words met in them
 * ([Vocabulary]), combined ([Skill.combine]).
 */
class SkillTracker(
    days: Flow<List<ReadingDayEntity>>,
    books: Flow<List<BookEntity>>,
    /** Book grades by content hash. */
    grades: Flow<Map<String, StoredDifficulty>>,
    /** Dictionary forms of the words being learned (looked up, not marked Learned). */
    learning: Flow<Set<String>>,
    /** A book's words by content hash ([app.honmani.reader.data.difficulty.DifficultyStore.words]). */
    private val words: (String) -> BookWords?,
    private val today: () -> LocalDate = LocalDate::now,
    /** Where the words are read; nothing to switch to when [words] reads no files. */
    context: CoroutineContext = Dispatchers.IO,
) {
    /** Words already read, by content hash and grade signature: they only change when a book is graded again. */
    private val cache = ConcurrentHashMap<String, Pair<String, BookWords?>>()

    val estimate: Flow<SkillEstimate> = combine(days, books, grades, learning) { rows, books, grades, learning ->
        val graded = books.mapNotNull { book ->
            grades[book.contentHash]?.takeIf { it.difficulty.score > 0 }?.let { book to it }
        }
        val scores = graded.associate { (book, grade) -> book.id to grade.difficulty.score }
        Skill.combine(Skill.estimate(rows, scores::get, today()), vocabulary(graded, learning))
    }.flowOn(context)

    private fun vocabulary(graded: List<Pair<BookEntity, StoredDifficulty>>, learning: Set<String>): VocabularySkill? {
        val read = graded.mapNotNull { (book, grade) ->
            val progress = if (book.finishedAt != null) 1f else book.progress.coerceIn(0f, 1f)
            if (progress <= 0f) return@mapNotNull null
            val bookWords = wordsOf(book.contentHash, grade.signature) ?: return@mapNotNull null
            Triple(bookWords, progress, grade.difficulty)
        }
        if (read.isEmpty()) return null
        val estimate = Vocabulary.estimate(read.map { (bookWords, progress, _) -> bookWords to progress }, learning)
        // The kanji and sentences of the books read, weighted by how much of each was read: the
        // score is the difficulty of such a book with the reader's vocabulary as its R95.
        var weights = 0.0
        var kanji = 0.0
        var sentence = 0.0
        for ((_, progress, difficulty) in read) {
            val weight = progress * difficulty.characters.coerceAtLeast(1).toDouble()
            weights += weight
            kanji += difficulty.kanjiShare * weight
            sentence += difficulty.sentenceLength * weight
        }
        val score = Difficulty.score(estimate.words, kanji / weights, sentence / weights)
        return VocabularySkill(score, estimate)
    }

    private fun wordsOf(contentHash: String, signature: String): BookWords? {
        cache[contentHash]?.let { (cached, bookWords) -> if (cached == signature) return bookWords }
        return words(contentHash).also { cache[contentHash] = signature to it }
    }
}
