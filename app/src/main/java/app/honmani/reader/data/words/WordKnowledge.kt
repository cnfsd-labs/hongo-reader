package app.honmani.reader.data.words

import app.honmani.reader.data.difficulty.BookWords
import app.honmani.reader.data.difficulty.StoredDifficulty
import app.honmani.reader.data.library.BookEntity
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import org.json.JSONArray
import org.json.JSONObject

/** A word as read so far, over all graded books: its frequency rank and how many times it was read. */
data class ReadWord(val rank: Int, val reads: Int)

/** What the reader has read, and how well they know each word ([WordProgress]). */
class WordKnowledge(
    /** Every word read in graded books up to the reader's place, by dictionary form. */
    val read: Map<String, ReadWord>,
    /** Looked-up words' progress, by word id. */
    val progress: Map<Long, WordStep>,
    /** Dictionary forms of looked-up words not known yet. */
    val learning: Set<String>,
) {
    fun steps(word: WordEntity): Int = progress[word.id]?.steps ?: 0

    fun isKnown(word: WordEntity): Boolean = steps(word) >= WordProgress.KNOWN

    /** Words known from reading alone (never looked up), with how often each was read, most read first. */
    fun knownByReading(lookedUp: Set<String>): List<Pair<String, Int>> =
        read.filter { (key, word) -> word.reads >= WordProgress.KNOWN && key !in lookedUp }
            .map { (key, word) -> key to word.reads }
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })

    companion object {
        val EMPTY = WordKnowledge(emptyMap(), emptyMap(), emptySet())
    }
}

/**
 * Keeps looked-up words' progress between runs: it depends on the order of reads and lookups,
 * which the books and the word list alone don't tell. In app storage, like the book grades; lost,
 * it is worked out again from the totals.
 */
class WordProgressStore(private val file: File) {
    fun load(): Map<Long, WordStep> = runCatching {
        val json = JSONObject(file.readText())
        json.keys().asSequence().associate { id ->
            val values = json.getJSONArray(id)
            id.toLong() to WordStep(values.getInt(0), values.getInt(1), values.getInt(2), values.getBoolean(3))
        }
    }.getOrDefault(emptyMap())

    fun save(progress: Map<Long, WordStep>) {
        val json = JSONObject()
        for ((id, step) in progress) {
            json.put(id.toString(), JSONArray().put(step.steps).put(step.reads).put(step.lookups).put(step.learned))
        }
        file.parentFile?.mkdirs()
        val temporary = File(file.path + ".tmp")
        temporary.writeText(json.toString())
        temporary.renameTo(file)
    }
}

/**
 * Works out [WordKnowledge] whenever the books' places, their grades or the word list change:
 * the words read so far from each graded book's words ([BookWords]) up to the reader's progress,
 * and each looked-up word's progress brought up to date ([WordProgress.update]).
 */
class WordKnowledgeTracker(
    books: Flow<List<BookEntity>>,
    /** Book grades by content hash. */
    grades: Flow<Map<String, StoredDifficulty>>,
    /** Every saved word. */
    words: Flow<List<WordEntity>>,
    /** A book's words by content hash ([app.honmani.reader.data.difficulty.DifficultyStore.words]). */
    private val bookWords: (String) -> BookWords?,
    private val store: WordProgressStore,
    context: CoroutineContext = Dispatchers.IO,
) {
    /** Words already read, by content hash and grade signature: they only change when a book is graded again. */
    private val cache = ConcurrentHashMap<String, Pair<String, BookWords?>>()
    private var progress: Map<Long, WordStep>? = null

    val knowledge: Flow<WordKnowledge> = combine(books, grades, words) { books, grades, words ->
        val read = HashMap<String, ReadWord>()
        for (book in books) {
            val grade = grades[book.contentHash]?.takeIf { it.difficulty.score > 0 } ?: continue
            val place = if (book.finishedAt != null) 1f else book.progress.coerceIn(0f, 1f)
            if (place <= 0f) continue
            val inBook = wordsOf(book.contentHash, grade.signature) ?: continue
            inBook.forEachMet(place) { key, rank, reads ->
                val before = read[key]
                read[key] = if (before == null) ReadWord(rank, reads) else ReadWord(minOf(before.rank, rank), before.reads + reads)
            }
        }
        val previous = progress ?: store.load()
        val updated = words.associate { word ->
            word.id to WordProgress.update(
                previous[word.id],
                reads = read[word.expression]?.reads ?: 0,
                lookups = word.lookupCount,
                learned = word.status == WordStatus.LEARNED,
            )
        }
        if (updated != previous) store.save(updated)
        progress = updated
        val learning = words.filter { (updated[it.id]?.steps ?: 0) < WordProgress.KNOWN }.mapTo(HashSet()) { it.expression }
        WordKnowledge(read, updated, learning)
    }.flowOn(context)

    private fun wordsOf(contentHash: String, signature: String): BookWords? {
        cache[contentHash]?.let { (cached, words) -> if (cached == signature) return words }
        return bookWords(contentHash).also { cache[contentHash] = signature to it }
    }
}
