package app.honmani.reader.data.difficulty

import app.honmani.reader.data.dictionary.DictionaryEntity
import app.honmani.reader.data.sync.RubyText
import java.util.BitSet
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DifficultyTest {
    @Test
    fun r95IsTheRankCoveringNinetyFivePercent() {
        val ranks = IntArray(100) { if (it < 95) it + 1 else 10_000 + it }
        assertEquals(95, Difficulty.r95(ranks))
        assertEquals(10_095, Difficulty.r95(ranks.copyOf(100).also { it[0] = 20_000 }))
        assertEquals(0, Difficulty.r95(IntArray(0)))
    }

    @Test
    fun scoreRisesWithRareWordsKanjiAndLongSentences() {
        val base = Difficulty.score(r95 = 40_000, kanjiShare = 0.35, sentenceLength = 25.0)
        assertTrue(Difficulty.score(80_000, 0.35, 25.0) > base)
        assertTrue(Difficulty.score(40_000, 0.45, 25.0) > base)
        assertTrue(Difficulty.score(40_000, 0.35, 35.0) > base)
        assertEquals(1, Difficulty.score(1_000, 0.0, 5.0))
        assertEquals(100, Difficulty.score(500_000, 0.6, 80.0))
    }

    @Test
    fun theScaleFollowsTheBooksItWasFittedOn() {
        // Measured with Honmani's splitting on the Aozora Bunko texts (2026-10-21); learnnatively.com
        // grades 走れメロス 34 and 人間失格 41, children's books with furigana about 12.
        assertEquals(34.0, Difficulty.nativelyLevel(44_168, 0.441, 18.1), 3.0)
        assertEquals(41.0, Difficulty.nativelyLevel(46_578, 0.398, 39.6), 3.0)
        assertEquals(DifficultyBand.N2, DifficultyBand.of(Difficulty.score(44_168, 0.441, 18.1)))
    }

    @Test
    fun bandsFollowNativelysJlptLevels() {
        assertEquals(DifficultyBand.N5, DifficultyBand.of(1))
        assertEquals(DifficultyBand.N5, DifficultyBand.of(28))
        assertEquals(DifficultyBand.N4, DifficultyBand.of(29))
        assertEquals(DifficultyBand.N3, DifficultyBand.of(59))
        assertEquals(DifficultyBand.N2, DifficultyBand.of(60))
        assertEquals(DifficultyBand.N1, DifficultyBand.of(90))
        assertEquals(DifficultyBand.BEYOND_N1, DifficultyBand.of(91))
        // Natively's level 24 (N3) is a score of about 54.
        assertEquals(DifficultyBand.N3, DifficultyBand.of((1 + 99 * 24 / 45.0).toInt()))
    }

    private val lexicon = Lexicon.Builder()
        .add("猫", 0, 900)
        .add("が", 0, 3)
        .add("好き", 0, 200)
        .add("です", 0, 5)
        .add("ねこ", 0, 900)
        .build()

    @Test
    fun aBookIsMeasuredOverAllItsChapters() {
        val chapter = RubyText("猫が好きです。".repeat(70))
        val difficulty = Difficulty.of(sequenceOf(chapter, chapter), Segmenter(lexicon))!!

        assertEquals(560, difficulty.words)
        assertEquals(4, difficulty.distinctWords)
        assertEquals(900, difficulty.r95)
        assertEquals(0.5f, difficulty.kanjiShare, 0.001f)
        assertEquals(6f, difficulty.sentenceLength, 0.001f)
        assertEquals(840L, difficulty.characters)
        assertEquals(Difficulty.VERSION, difficulty.version)
    }

    @Test
    fun theBooksWordsAreKeptWithWhereTheyFirstAppear() {
        val text = "猫が好きです。".repeat(130) + "ねこです。"
        val words = Difficulty.measure(sequenceOf(RubyText(text)), Segmenter(lexicon))!!.words

        assertEquals(listOf("猫", "が", "好き", "です", "ねこ"), words.keys)
        assertEquals(listOf(900, 3, 200, 5, 900), words.ranks.toList())
        assertEquals(0f, words.firstAt(0), 0.001f)
        assertEquals(910f / text.length, words.firstAt(4), 0.001f)
        val met = LinkedHashMap<String, Int>()
        words.forEachMet(0.5f) { key, _, reads -> met[key] = reads }
        // Half the book: 65 of the 130 sentences, each word once in each.
        assertEquals(listOf("猫", "が", "好き", "です"), met.keys.toList())
        assertEquals(65.0, met.getValue("猫").toDouble(), 1.0)
    }

    @Test
    fun namesTheBookRepeatsAreLeftOut() {
        // 八尋 is a rare word in the dictionaries, but this book's main character.
        val names = Lexicon.Builder().add("猫", 0, 900).add("が", 0, 3).add("好き", 0, 200).add("です", 0, 5).add("八尋", 0, 116_747).build()
        val measured = Difficulty.measure(sequenceOf(RubyText("八尋が猫が好きです。".repeat(150))), Segmenter(names))!!

        assertEquals(listOf("が", "猫", "好き", "です"), measured.words.keys)
        assertEquals(900, measured.difficulty.r95)
        assertTrue(Difficulty.isName(rank = 116_747, count = 488, words = 52_715))
        // A rare word used a few times more than its rank says is still a word (心持 in こころ).
        assertFalse(Difficulty.isName(rank = 63_707, count = 57, words = 70_798))
    }

    @Test
    fun kanjiWithFuriganaCountHalf() {
        val text = "猫が好きです。".repeat(130)
        val ruby = BitSet().apply { for (i in text.indices step 7) set(i) }
        val withFurigana = Difficulty.of(sequenceOf(RubyText(text, ruby)), Segmenter(lexicon))!!

        // 猫 (with furigana) counts half, 好き (without) whole: 1.5 of 4 words.
        assertEquals(0.375f, withFurigana.kanjiShare, 0.001f)
        assertEquals(0.5f, Difficulty.of(sequenceOf(RubyText(text)), Segmenter(lexicon))!!.kanjiShare, 0.001f)
    }

    @Test
    fun lineBreaksEndSentences_dialogueWithoutAFullStopToo() {
        // 「そうか」 on lines of their own: without the breaks this was one 900-character sentence.
        val dialogue = Difficulty.of(sequenceOf(RubyText("猫が好きです\n".repeat(150))), Segmenter(lexicon))!!
        assertEquals(6f, dialogue.sentenceLength, 0.001f)
    }

    @Test
    fun tooLittleTextIsNotGraded() {
        assertNull(Difficulty.of(sequenceOf(RubyText("猫が好きです。")), Segmenter(lexicon)))
    }

    private fun dictionary(id: Long, title: String, terms: Int = 0, meta: Int = 0, mode: String? = null, priority: Int = 0) =
        DictionaryEntity(
            id = id,
            title = title,
            revision = "1",
            formatVersion = 3,
            importedAt = 0,
            termCount = terms,
            metaCount = meta,
            frequencyMode = mode,
            priority = priority,
        )

    @Test
    fun gradingUsesJitenWhenInstalled_andNeedsWordsAndRanks() = runBlocking {
        val jitendex = dictionary(1, "Jitendex", terms = 300_000)
        val jiten = dictionary(2, "Jiten", meta = 500_000, mode = "rank-based", priority = 3)
        // No frequency mode means rank-based.
        val other = dictionary(3, "JPDB", meta = 400_000, priority = 1)
        val counts = dictionary(4, "Counts", meta = 100, mode = DictionaryEntity.OCCURRENCE_BASED)
        val pitch = dictionary(5, "Kanjium", meta = 120_000, priority = 0)
        val ranked: suspend (DictionaryEntity) -> Boolean = { it != pitch }
        suspend fun of(vararg enabled: DictionaryEntity) = LexiconSources.of(enabled.toList(), ranked)

        assertEquals(jiten, of(jitendex, pitch, other, jiten)!!.frequency)
        assertEquals(other, of(jitendex, pitch, other, counts)!!.frequency)
        assertEquals(listOf(jitendex), of(jitendex, jiten)!!.terms)
        assertNull(of(jitendex, counts, pitch))
        assertNull(of(jiten))
        assertNotEquals(of(jitendex, jiten)!!.signature, of(jitendex, jiten.copy(revision = "2"))!!.signature)
    }
}
