package app.honmani.reader.ui.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryFilterTest {
    private fun book(title: String, author: String? = null) = LibraryBook(1, title, author, null, 0f)

    @Test
    fun statusFollowsProgressOrTheFinishedMark() {
        assertEquals(ReadingStatus.NOT_STARTED, ReadingStatus.of(0f, finishedAt = null))
        assertEquals(ReadingStatus.READING, ReadingStatus.of(0.4f, finishedAt = null))
        // ttu's progress stops at the start of the last page.
        assertEquals(ReadingStatus.FINISHED, ReadingStatus.of(0.985f, finishedAt = null))
        assertEquals(ReadingStatus.FINISHED, ReadingStatus.of(0.3f, finishedAt = 1L))
    }

    @Test
    fun searchFindsTitleOrAuthor() {
        assertTrue(LibrarySearch.matches(book("吾輩は猫である", "夏目漱石"), "猫"))
        assertTrue(LibrarySearch.matches(book("吾輩は猫である", "夏目漱石"), "漱石"))
        assertFalse(LibrarySearch.matches(book("吾輩は猫である", "夏目漱石"), "鉄道"))
        assertTrue(LibrarySearch.matches(book("anything"), "  "))
    }

    @Test
    fun searchIgnoresCaseSpacesAndKanaType() {
        assertTrue(LibrarySearch.matches(book("Norwegian Wood"), "norwegianwood"))
        assertTrue(LibrarySearch.matches(book("ノルウェイの森"), "のるうぇい"))
        assertTrue(LibrarySearch.matches(book("こころ"), "ココロ"))
        assertTrue(LibrarySearch.matches(book("銀河鉄道の夜", "宮沢 賢治"), "宮沢賢治"))
    }

    @Test
    fun romajiFindsTitlesByTheirReading() {
        val neko = book("吾輩は猫である", "夏目漱石")
        val reading = "わがはいはねこである なつめそうせき"
        assertTrue(LibrarySearch.matches(neko, "wagahai", reading))
        assertTrue(LibrarySearch.matches(neko, "Neko de aru", reading))
        assertTrue(LibrarySearch.matches(neko, "natsume soseki", reading))
        assertFalse(LibrarySearch.matches(neko, "kokoro", reading))
        // Kana titles need no dictionary.
        assertTrue(LibrarySearch.matches(book("こころ"), "kokoro"))
        assertTrue(LibrarySearch.matches(book("ノルウェイの森"), "noruwei"))
        // Without a reading, kanji can't match romaji.
        assertFalse(LibrarySearch.matches(neko, "wagahai"))
    }

    @Test
    fun sortsByDifficulty_ungradedBooksLast() {
        val books = listOf(book("A"), book("B"), book("C"), book("D")).mapIndexed { i, it -> it.copy(id = i.toLong()) }
        val scores = mapOf(0L to 60, 1L to 30, 3L to 45)
        fun order(sort: LibrarySort) = sort.apply(books) { scores[it.id] }.map { it.title }

        assertEquals(listOf("A", "B", "C", "D"), order(LibrarySort.RECENT))
        assertEquals(listOf("B", "D", "A", "C"), order(LibrarySort.EASIEST))
        assertEquals(listOf("A", "D", "B", "C"), order(LibrarySort.HARDEST))
        assertEquals(LibrarySort.RECENT, LibrarySort.fromName("nonsense"))
    }
}
