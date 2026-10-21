package app.honmani.reader.ui.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverTitleTest {
    @Test
    fun japaneseTitlesAreVertical() {
        assertTrue(CoverTitle.isVertical("吾輩は猫である"))
        assertTrue(CoverTitle.isVertical("ソードアート・オンライン 1"))
    }

    @Test
    fun latinTitlesAreHorizontal() {
        assertFalse(CoverTitle.isVertical("Kokoro"))
        assertFalse(CoverTitle.isVertical("Re:Zero 1"))
        assertFalse(CoverTitle.isVertical(" "))
    }

    @Test
    fun shortTitlesTakeOneColumn() {
        assertEquals(listOf("銀河鉄道の夜"), CoverTitle.columns("銀河鉄道の夜", maxColumns = 3))
    }

    @Test
    fun longerTitlesSplitEvenly() {
        assertEquals(listOf("ソ︱ドア︱ト", "・オンライン"), CoverTitle.columns("ソードアート・オンライン", maxColumns = 3))
    }

    @Test
    fun punctuationTurnsAndSpacesGo() {
        assertEquals(listOf("﹁猫﹂︑犬︒"), CoverTitle.columns("「猫」、 犬。", maxColumns = 3))
    }

    @Test
    fun overlongTitlesEndInAnEllipsis() {
        val columns = CoverTitle.columns("あ".repeat(40), maxColumns = 3)
        assertEquals(3, columns.size)
        assertEquals(10, columns.last().length)
        assertTrue(columns.last().endsWith("︙"))
    }

    @Test
    fun theSameTitleKeepsItsColour() {
        assertEquals(CoverTitle.bindingIndex("こころ"), CoverTitle.bindingIndex("こころ"))
    }
}
