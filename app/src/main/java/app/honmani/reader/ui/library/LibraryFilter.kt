package app.honmani.reader.ui.library

import app.honmani.reader.data.stats.Skill
import app.honmani.reader.lookup.JapaneseText
import app.honmani.reader.lookup.Romaji
import kotlin.math.abs

/** Where the reader is with a book, for the library's quick filters. */
enum class ReadingStatus {
    NOT_STARTED,
    READING,
    FINISHED,
    ;

    companion object {
        /**
         * ttu's progress is where the last page starts, so a book read to the end stays just
         * below 100%; from [FINISHED_PROGRESS] on it counts as finished. Books can also be marked.
         */
        const val FINISHED_PROGRESS = 0.98f

        fun of(progress: Float, finishedAt: Long?): ReadingStatus = when {
            finishedAt != null || progress >= FINISHED_PROGRESS -> FINISHED
            progress > 0f -> READING
            else -> NOT_STARTED
        }
    }
}

/** The library's order. */
enum class LibrarySort {
    /** Last opened first, then last added: the order the database gives. */
    RECENT,

    /** Closest to a little above the reader's skill first, as "Books that fit you" in Progress. */
    BEST_FIT,
    EASIEST,
    HARDEST,
    TITLE,
    ;

    /**
     * [books] in this order; by difficulty, books without one come last, in their recent order.
     * Without a [skill] yet, [BEST_FIT] is [EASIEST].
     */
    fun apply(books: List<LibraryBook>, skill: Int? = null, difficulty: (LibraryBook) -> Int?): List<LibraryBook> = when (this) {
        RECENT -> books
        BEST_FIT -> if (skill == null) {
            EASIEST.apply(books, null, difficulty)
        } else {
            books.sortedWith(compareBy(nullsLast()) { book -> difficulty(book)?.let { abs(it - (skill + Skill.STRETCH)) } })
        }
        EASIEST -> books.sortedWith(compareBy(nullsLast()) { difficulty(it) })
        HARDEST -> books.sortedWith(compareBy(nullsLast(reverseOrder())) { difficulty(it) })
        TITLE -> books.sortedBy { it.title }
    }

    companion object {
        fun fromName(name: String?): LibrarySort = entries.firstOrNull { it.name == name } ?: RECENT
    }
}

/**
 * Search in the library: by title or author, ignoring case, spaces and hiragana/katakana. A query
 * in Latin letters is also compared as romaji with the books' readings (`Romaji.loose`).
 */
object LibrarySearch {
    /** [reading] is the book's title and author read in kana, if known. */
    fun matches(book: LibraryBook, query: String, reading: String? = null): Boolean {
        val wanted = normalize(query)
        if (wanted.isEmpty()) return true
        if (normalize(book.title).contains(wanted) || book.author?.let { normalize(it).contains(wanted) } == true) {
            return true
        }
        if (!Romaji.isRomaji(query)) return false
        val romaji = Romaji.loose(query)
        if (romaji.isEmpty()) return false
        val text = reading ?: listOfNotNull(book.title, book.author).joinToString(" ")
        return Romaji.loose(Romaji.fromKana(text)).contains(romaji)
    }

    private fun normalize(text: String): String =
        JapaneseText.toHiragana(text).lowercase().filterNot { it.isWhitespace() || it == '　' }
}
