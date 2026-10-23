package app.honmani.reader.ui.words

import app.honmani.reader.data.words.ReadWord
import app.honmani.reader.data.words.WordEntity
import app.honmani.reader.data.words.WordKnowledge
import app.honmani.reader.data.words.WordStatus
import app.honmani.reader.data.words.WordStep
import app.honmani.reader.lookup.Inflection
import app.honmani.reader.lookup.LookupGroup
import org.junit.Assert.assertEquals
import org.junit.Test

class WordListsTest {
    private fun word(id: Long, expression: String, rankKey: Double, starred: Boolean = false, status: WordStatus = WordStatus.LEARNING) =
        WordEntity(
            id = id,
            expression = expression,
            reading = "",
            gloss = "",
            status = status,
            starred = starred,
            lookupCount = 1,
            firstLookedUpAt = 0,
            lastLookedUpAt = 0,
            rankKey = rankKey,
            lastSentence = "",
            lastSurfaceStart = 0,
            lastSurfaceEnd = 0,
        )

    private val words = listOf(word(1, "猫", 3.0), word(2, "犬", 2.0), word(3, "鳥", 1.0, starred = true), word(4, "魚", 0.5))

    @Test
    fun knownWordsLeaveTheLearningList_starredFirst() {
        val knowledge = WordKnowledge(
            read = mapOf("猫" to ReadWord(900, 12), "犬" to ReadWord(800, 2), "本" to ReadWord(50, 30), "雨" to ReadWord(700, 3)),
            progress = mapOf(1L to WordStep(9, 12, 1, false), 2L to WordStep(2, 2, 0, false), 3L to WordStep(0, 0, 1, false)),
            learning = setOf("犬", "鳥", "魚"),
        )
        val lists = WordLists.of(words, knowledge)

        assertEquals(listOf("鳥", "犬", "魚"), lists.learning.map { it.expression })
        assertEquals(listOf("猫"), lists.known.map { it.expression })
        // 本 known from reading alone; 雨 read only three times.
        assertEquals(listOf("本" to 30), lists.knownByReading)
        assertEquals(2, lists.knownCount)
        assertEquals(2, lists.steps[2L])
    }

    @Test
    fun whileWorkedOutTheLearnedMarkDecides() {
        val lists = WordLists.of(words + word(5, "空", 0.1, status = WordStatus.LEARNED), knowledge = null)
        assertEquals(listOf("空"), lists.known.map { it.expression })
        assertEquals("鳥", lists.learning.first().expression)
    }

    @Test
    fun aWordKnownFromReadingOpensItsOwnEntry() {
        fun group(expression: String, reading: String, source: String, inflections: List<Inflection> = emptyList()) =
            LookupGroup(expression, reading, source, inflections, sections = emptyList())
        // Looking up 見る also finds 見 (a prefix); and 見る read as an inflected form comes after
        // the word itself.
        val groups = listOf(group("見", "み", "見"), group("見る", "みる", "見る", listOf(Inflection.STEM)), group("見る", "みる", "見る"))
        assertEquals(emptyList<Inflection>(), ReadWordDetails.entryFor("見る", groups)?.inflections)
        // A word the book writes in kana: the entry read that way.
        assertEquals("猫", ReadWordDetails.entryFor("ねこ", listOf(group("猫", "ねこ", "ねこ")))?.expression)
        // Only a shorter match: no entry for this word.
        assertEquals(null, ReadWordDetails.entryFor("見る", listOf(group("見", "み", "見"))))
    }
}
