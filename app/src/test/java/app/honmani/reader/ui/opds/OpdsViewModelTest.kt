package app.honmani.reader.ui.opds

import app.honmani.reader.data.opds.EPUB_TYPE
import app.honmani.reader.data.opds.TEXT_TYPE
import org.junit.Assert.assertEquals
import org.junit.Test

class OpdsViewModelTest {
    @Test
    fun downloadsAreNamedAfterTheEntry() {
        assertEquals("吾輩は猫である.epub", OpdsViewModel.fileName("吾輩は猫である", EPUB_TYPE))
        assertEquals("銀河鉄道の夜.txt", OpdsViewModel.fileName(" 銀河鉄道の夜 ", TEXT_TYPE))
        assertEquals("a_b_c.epub", OpdsViewModel.fileName("a/b:c", EPUB_TYPE))
        assertEquals("book.txt", OpdsViewModel.fileName("  ", TEXT_TYPE))
    }
}
