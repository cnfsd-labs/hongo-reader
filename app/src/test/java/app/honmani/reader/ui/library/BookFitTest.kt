package app.honmani.reader.ui.library

import org.junit.Assert.assertEquals
import org.junit.Test

class BookFitTest {
    @Test
    fun aBookFitsFromALittleEasierToALittleHarder() {
        assertEquals(BookFit.EASY, BookFit.of(score = 44, skill = 50))
        assertEquals(BookFit.FITS, BookFit.of(45, 50))
        assertEquals(BookFit.FITS, BookFit.of(58, 50))
        assertEquals(BookFit.CHALLENGE, BookFit.of(59, 50))
        assertEquals(BookFit.CHALLENGE, BookFit.of(65, 50))
        assertEquals(BookFit.HARD, BookFit.of(66, 50))
    }
}
