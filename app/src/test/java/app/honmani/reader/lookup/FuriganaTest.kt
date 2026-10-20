package app.honmani.reader.lookup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FuriganaTest {
    @Test
    fun replacesRubyBasesUpToTheLastOne() {
        val ruby = listOf(RubySpan(0, 1, "た"))
        assertEquals("た", Furigana.readingPrefix("食べました", ruby))
    }

    @Test
    fun keepsKanaBetweenBases() {
        // 取り扱い with furigana over 取 and 扱.
        val ruby = listOf(RubySpan(0, 1, "と"), RubySpan(2, 3, "あつか"))
        assertEquals("とりあつか", Furigana.readingPrefix("取り扱い", ruby))
    }

    @Test
    fun ignoresBasesBeyondTheSource() {
        val ruby = listOf(RubySpan(0, 2, "きょう"), RubySpan(3, 5, "てんき"))
        assertEquals("きょうはてんき", Furigana.readingPrefix("今日は天気", ruby))
        assertEquals("きょう", Furigana.readingPrefix("今日は天", ruby))
        assertNull(Furigana.readingPrefix("今", ruby))
    }

    @Test
    fun noFuriganaGivesNoReading() {
        assertNull(Furigana.readingPrefix("今日", emptyList()))
    }

    @Test
    fun katakanaFuriganaCountsAsHiragana() {
        val prefix = Furigana.readingPrefix("煙草", listOf(RubySpan(0, 2, "タバコ")))!!
        assertTrue(Furigana.agrees(prefix, "たばこ"))
    }

    @Test
    fun agreesComparesTheStartOfTheReading() {
        assertTrue(Furigana.agrees("おこな", "おこなう"))
        assertFalse(Furigana.agrees("おこな", "いく"))
    }

    @Test
    fun segmentsPutTheReadingOverEachKanjiRun() {
        assertEquals(
            listOf(RubySegment("取", "と"), RubySegment("り"), RubySegment("扱", "あつか"), RubySegment("う")),
            Furigana.segments("取り扱う", "とりあつかう"),
        )
        assertEquals(listOf(RubySegment("食", "た"), RubySegment("べる")), Furigana.segments("食べる", "たべる"))
        assertEquals(listOf(RubySegment("大人", "おとな"), RubySegment("しい")), Furigana.segments("大人しい", "おとなしい"))
        assertEquals(listOf(RubySegment("今日", "きょう")), Furigana.segments("今日", "きょう"))
        assertEquals(listOf(RubySegment("お"), RubySegment("茶", "ちゃ")), Furigana.segments("お茶", "おちゃ"))
    }

    @Test
    fun segmentsLeaveKanaWithoutRuby() {
        assertEquals(listOf(RubySegment("たべる")), Furigana.segments("たべる", "たべる"))
        assertEquals(listOf(RubySegment("コーヒー")), Furigana.segments("コーヒー", "こーひー"))
    }

    @Test
    fun segmentsMatchKatakanaInTheExpressionAgainstAHiraganaReading() {
        assertEquals(listOf(RubySegment("バス"), RubySegment("停", "てい")), Furigana.segments("バス停", "ばすてい"))
    }

    @Test
    fun segmentsFallBackToTheWholeReading() {
        // The kana of the expression is not in the reading (an irregular or wrong reading).
        assertEquals(listOf(RubySegment("明日る", "あした")), Furigana.segments("明日る", "あした"))
    }
}
