package app.honmani.reader.lookup

import org.junit.Assert.assertEquals
import org.junit.Test

class PitchAccentTest {
    @Test
    fun smallKanaJoinTheMoraBefore() {
        assertEquals(listOf("きょ", "う"), PitchAccent.morae("きょう"))
        assertEquals(listOf("が", "っ", "こ", "う"), PitchAccent.morae("がっこう"))
        assertEquals(listOf("コ", "ー", "ヒ", "ー"), PitchAccent.morae("コーヒー"))
    }

    @Test
    fun patternsFollowTheDownstep() {
        fun levels(reading: String, downstep: Int) =
            PitchAccent.pattern(reading, downstep).joinToString("") { (if (it.high) "H" else "L") + if (it.dropsAfter) "ꜜ" else "" }

        assertEquals("LHH", levels("さくら", 0)) // 平板
        assertEquals("HꜜLL", levels("いのち", 1)) // 頭高
        assertEquals("LHꜜL", levels("たまご", 2)) // 中高
        assertEquals("LHHꜜ", levels("おとこ", 3)) // 尾高
        assertEquals("H", levels("き", 0))
        assertEquals("Hꜜ", levels("き", 1))
        assertEquals("", levels("ねこ", 3))
    }

    @Test
    fun htmlMarksHighMoraeAndTheFall() {
        assertEquals(
            """た<span class="h d">ま</span>ご""",
            PitchAccent.html("たまご", listOf(2)),
        )
        assertEquals(
            """<span class="h d">ひ</span>と・ひ<span class="h">と</span>""",
            PitchAccent.html("ひと", listOf(1, 0)),
        )
    }
}
