package app.honmani.reader.data.words

import app.honmani.reader.data.difficulty.BookDifficulty
import app.honmani.reader.data.difficulty.BookWords
import app.honmani.reader.data.difficulty.Difficulty
import app.honmani.reader.data.difficulty.StoredDifficulty
import app.honmani.reader.data.library.BookEntity
import app.honmani.reader.data.library.BookFormat
import app.honmani.reader.ui.words.WordLists
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Robolectric for org.json, which the progress store writes with.
@RunWith(RobolectricTestRunner::class)
class WordKnowledgeTrackerTest {
    private val book = BookEntity(contentHash = "h", title = "Book", format = BookFormat.EPUB, fileName = "h.epub", addedAt = 0)
    private val grade = StoredDifficulty(
        BookDifficulty(Difficulty.VERSION, score = 50, r95 = 0, kanjiShare = 0f, sentenceLength = 0f, words = 0, distinctWords = 0, unknownShare = 0f),
        signature = "s",
    )

    /** The book uses わかる ten times and 分かる twice, spread over the whole of it. */
    private val bookWords = BookWords(
        keys = listOf("わかる", "分かる"),
        ranks = intArrayOf(100, 100),
        positions = listOf(shortArrayOf(500, 1000, 1500, 2000, 2500, 3000, 3500, 4000, 4500, 5000), shortArrayOf(7000, 8000)),
    )

    private fun word(sentence: String, start: Int, end: Int) = WordEntity(
        id = 1,
        expression = "分かる",
        reading = "わかる",
        gloss = "to understand",
        status = WordStatus.LEARNING,
        starred = false,
        lookupCount = 1,
        firstLookedUpAt = 0,
        lastLookedUpAt = 0,
        rankKey = 1.0,
        lastSentence = sentence,
        lastSurfaceStart = start,
        lastSurfaceEnd = end,
    )

    private fun tracker(words: List<WordEntity>, store: WordProgressStore) = WordKnowledgeTracker(
        books = flowOf(listOf(book)),
        stretches = flowOf(mapOf("h" to intArrayOf(0, BookWords.POSITIONS))),
        grades = flowOf(mapOf("h" to grade)),
        words = MutableStateFlow(words),
        bookWords = { bookWords },
        store = store,
        context = Dispatchers.Unconfined,
    )

    private fun store() = WordProgressStore(File(Files.createTempDirectory("progress").toFile(), "progress.json"))

    @Test
    fun aWordLookedUpInKanaIsAlsoReadAsItsReading() = runBlocking {
        // Looked up where the book wrote わかった: the twelve reads of both spellings count.
        val words = listOf(word("よくわかった。", 2, 5))
        val inKana = tracker(words, store()).knowledge.first()
        assertEquals(WordStep(steps = 9, reads = 12, lookups = 1, learned = false, kana = true), inKana.progress[1])
        // Known now, and わかる is not a word known from reading of its own.
        val lists = WordLists.of(words, inKana)
        assertEquals(listOf("分かる"), lists.known.map { it.expression })
        assertEquals(emptyList<Pair<String, Int>>(), lists.knownByReading)

        // Looked up in kanji: only 分かる counts.
        val inKanji = tracker(listOf(word("よく分かった。", 2, 5)), store()).knowledge.first()
        assertEquals(WordStep(steps = 0, reads = 2, lookups = 1, learned = false, kana = false), inKanji.progress[1])
    }

    @Test
    fun onceLookedUpInKanaItStaysSo() = runBlocking {
        val store = store()
        tracker(listOf(word("よくわかった。", 2, 5)), store).knowledge.first()
        // Looked up again where the book wrote 分かった: the reads of わかる still count, so none
        // are counted twice later.
        val again = tracker(listOf(word("よく分かった。", 2, 5)), store).knowledge.first()
        assertEquals(true, again.progress[1]?.kana)
        assertEquals(12, again.progress[1]?.reads)
    }
}
