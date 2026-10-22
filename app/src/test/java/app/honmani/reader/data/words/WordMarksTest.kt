package app.honmani.reader.data.words

import app.honmani.reader.data.difficulty.Difficulty
import app.honmani.reader.data.difficulty.Lexicon
import app.honmani.reader.data.difficulty.Segmenter
import app.honmani.reader.lookup.WordClass
import org.junit.Assert.assertEquals
import org.junit.Test

class WordMarksTest {
    private val segmenter = Segmenter(
        Difficulty.addGrammar(
            Lexicon.Builder()
                .add("私", 0, 30)
                .add("は", 0, 2)
                .add("を", 0, 4)
                .add("猫", 0, 900)
                .add("鼬", 0, 40_000)
                .add("食べる", WordClass.V1, 187),
        ).build(),
    )

    /** 食べる is being learned, 猫 was read before. */
    private val knowledge = WordKnowledge(
        read = mapOf("猫" to ReadWord(900, 3), "食べる" to ReadWord(187, 2)),
        progress = emptyMap(),
        learning = setOf("食べる"),
    )

    private fun marks(text: String, mode: WordMarksMode, vocabulary: Int = 500) =
        WordMarks.of(text, segmenter, knowledge, mode, vocabulary).map { text.substring(it.start, it.start + it.length) to it.kind }

    @Test
    fun wordsBeingLearnedAreMarked_inflectedAsWritten() {
        assertEquals(listOf("食べなかった" to WordMark.Kind.LEARNING), marks("私は猫を食べなかった", WordMarksMode.LEARNING))
    }

    @Test
    fun newWordsAreThoseNeitherReadNorWithinTheVocabulary_namesLeftOut() {
        // 鼬 is rare and unread; 猫 was read; メロス is no dictionary word.
        assertEquals(
            listOf("鼬" to WordMark.Kind.NEW, "食べた" to WordMark.Kind.LEARNING),
            marks("メロスは猫と鼬を食べた", WordMarksMode.NEW),
        )
        assertEquals(listOf("食べた" to WordMark.Kind.LEARNING), marks("鼬を食べた", WordMarksMode.NEW, vocabulary = 50_000))
    }

    @Test
    fun offMarksNothing() {
        assertEquals(emptyList<Pair<String, WordMark.Kind>>(), marks("私は猫を食べなかった", WordMarksMode.OFF))
    }
}
