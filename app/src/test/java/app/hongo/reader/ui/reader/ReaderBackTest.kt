package app.hongo.reader.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderBackTest {
    @Test
    fun fromABook_goesStraightToTheBookList() {
        assertEquals(ReaderBack.Step(-1), ReaderBack.from(listOf("/manage", "/b"), currentIndex = 1))
    }

    @Test
    fun fromSettingsOpenedInABook_returnsToTheBook() {
        assertEquals(ReaderBack.Step(-1), ReaderBack.from(listOf("/manage", "/b", "/settings"), currentIndex = 2))
    }

    @Test
    fun fromStatistics_returnsToWhereItWasOpened() {
        assertEquals(ReaderBack.Step(-1), ReaderBack.from(listOf("/manage", "/b", "/statistics"), currentIndex = 2))
    }

    @Test
    fun fromABookReopenedThroughSettings_skipsTheSettingsPage() {
        // ttu's own close button in settings navigates forward to the book again.
        val history = listOf("/manage", "/b", "/settings", "/b")
        assertEquals(ReaderBack.Step(-3), ReaderBack.from(history, currentIndex = 3))
    }

    @Test
    fun withoutTheBookListInHistory_loadsIt() {
        assertEquals(ReaderBack.LoadLibrary, ReaderBack.from(listOf("/b"), currentIndex = 0))
        assertEquals(ReaderBack.LoadLibrary, ReaderBack.from(listOf("/settings"), currentIndex = 0))
    }

    @Test
    fun usesTheLatestBookListEntry() {
        val history = listOf("/manage", "/b", "/manage", "/b", "/settings", "/b")
        assertEquals(ReaderBack.Step(-3), ReaderBack.from(history, currentIndex = 5))
    }
}
