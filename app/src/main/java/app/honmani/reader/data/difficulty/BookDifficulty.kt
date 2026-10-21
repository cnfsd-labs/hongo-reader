package app.honmani.reader.data.difficulty

import app.honmani.reader.data.sync.RubyText
import app.honmani.reader.lookup.JapaneseText
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.roundToInt

/** How hard a book is to read, from one pass over its text. */
data class BookDifficulty(
    /** [Difficulty.VERSION] the result was computed with; older results are computed again. */
    val version: Int,
    /** 1 (picture books) to 100 (the hardest literature); 0 for text too short to grade. */
    val score: Int,
    /** The frequency rank that covers 95% of the book's words. */
    val r95: Int,
    /** Share of the words written with kanji, 0 to 1; a word whose kanji all have furigana counts half. */
    val kanjiShare: Float,
    /** Characters per sentence, on average. */
    val sentenceLength: Float,
    /** Japanese characters in the book (letters, kana and kanji; no punctuation), for reading time. */
    val characters: Long = 0,
    /** Words counted (text no dictionary knows left out), and how many of them differ. */
    val words: Int,
    val distinctWords: Int,
    /** Share of the text's words no dictionary knew (mostly names), 0 to 1. */
    val unknownShare: Float,
)

/**
 * The distinct words of a book (dictionary forms, [Lexicon.UNRANKED] words included), with their
 * frequency rank and where in the book each first appears, as a share of the text (0 to 1): the
 * words a reader has met up to some progress, for the vocabulary estimate.
 */
class BookWords(val keys: List<String>, val ranks: IntArray, val firstAt: FloatArray) {
    val size: Int get() = keys.size

    /** The words met by [progress] (0 to 1). */
    inline fun forEachMet(progress: Float, action: (key: String, rank: Int) -> Unit) {
        for (i in keys.indices) if (firstAt[i] <= progress) action(keys[i], ranks[i])
    }
}

/** A book's grade and its words, from one pass over its text. */
class Measured(val difficulty: BookDifficulty, val words: BookWords)

/**
 * Bands of the 1–100 scale, each about one JLPT level, at learnnatively.com's boundaries (N5 up to
 * level 12, N4 13–19, N3 20–26, N2 27–33, N1 34–40) converted with [Difficulty.score]'s scale.
 * [highest] is the band's top score.
 */
enum class DifficultyBand(val highest: Int) {
    N5(28),
    N4(43),
    N3(59),
    N2(74),
    N1(90),
    BEYOND_N1(100),
    ;

    companion object {
        fun of(score: Int): DifficultyBand = entries.first { score.coerceIn(1, 100) <= it.highest }
    }
}

/**
 * Book difficulty from three measures of the text, weighted as fitted to how readers grade books
 * on learnnatively.com and to jiten.moe's difficulty: how rare its vocabulary is (the frequency
 * rank that covers 95% of the words), how much of it is written in kanji, and how long its
 * sentences are. Kanji and sentence length tell more than vocabulary alone: on the books
 * compared, each followed the readers' grades better than rarity did. Kanji with furigana count
 * half, as children's books with furigana throughout are graded about that much easier.
 */
object Difficulty {
    /**
     * Raise when the scale or the word splitting changes, so stored results are redone. 2: EPUB
     * paragraphs end sentences (dialogue without 。 had made sentences far too long). 3: the
     * book's characters are kept. 4: the book's words are kept ([BookWords]).
     */
    const val VERSION = 4

    const val COVERAGE = 0.95

    /**
     * Forms of the copula and polite endings that dictionaries don't list and the deinflector
     * doesn't reach after a noun (こと|でした). They are as common as だ and です.
     */
    val GRAMMAR = listOf(
        "でした", "でしょう", "でしょ", "でして", "だった", "だろう", "だろ", "だって", "であった", "であろう",
        "ました", "ません", "ませんでした", "ましょう", "まして", "なかった", "なくて",
    )

    /** Adds [GRAMMAR] to a lexicon being built from the dictionaries. */
    fun addGrammar(builder: Lexicon.Builder): Lexicon.Builder {
        for (form in GRAMMAR) builder.add(form, 0, GRAMMAR_RANK)
        return builder
    }

    private const val GRAMMAR_RANK = 10

    /** The smallest rank N such that words ranked N or better make up [COVERAGE] of [ranks]. */
    fun r95(ranks: IntArray): Int {
        if (ranks.isEmpty()) return 0
        val sorted = ranks.sortedArray()
        val needed = ceil(sorted.size * COVERAGE).toInt().coerceIn(1, sorted.size)
        return sorted[needed - 1]
    }

    /**
     * The grade on learnnatively.com's scale (0 to about 45; JLPT N5 is up to 12, N1 34 to 40),
     * from the fit.
     */
    fun nativelyLevel(r95: Int, kanjiShare: Double, sentenceLength: Double): Double {
        // Honmani splits text a little more coarsely than jiten.moe, which the fit's ranks are from.
        val rank = (r95 / SPLIT_RATIO).coerceIn(MIN_R95, MAX_R95)
        val length = sentenceLength.coerceIn(MIN_SENTENCE, MAX_SENTENCE)
        return INTERCEPT + RANK_WEIGHT * ln(rank) + KANJI_WEIGHT * kanjiShare + SENTENCE_WEIGHT * length
    }

    /** 1–100 from [nativelyLevel]: level [TOP_LEVEL] and above is 100, so a score is about 2.2 levels. */
    fun score(r95: Int, kanjiShare: Double, sentenceLength: Double): Int =
        (1 + 99 * nativelyLevel(r95, kanjiShare, sentenceLength) / TOP_LEVEL).roundToInt().coerceIn(1, 100)

    /** Splits [text] with [segmenter] and grades it; null for text with too few words to tell. */
    fun of(text: Sequence<RubyText>, segmenter: Segmenter): BookDifficulty? = measure(text, segmenter)?.difficulty

    /** [of], with the book's words. */
    fun measure(text: Sequence<RubyText>, segmenter: Segmenter): Measured? {
        val ranks = IntList()
        // Each distinct word's rank and first offset in the whole text, in order of appearance.
        val distinct = LinkedHashMap<String, LongArray>()
        var offset = 0L
        var unknown = 0
        var kanjiWords = 0.0
        var characters = 0L
        var sentences = 0L
        for (chapter in text) {
            val part = chapter.text
            segmenter.segment(part) { token ->
                if (token.rank == Token.UNKNOWN) {
                    unknown++
                } else {
                    ranks.add(token.rank)
                    distinct.getOrPut(token.key) { longArrayOf(token.rank.toLong(), offset + token.start) }
                    val kanji = (token.start until token.start + token.length).filter { JapaneseText.isKanji(part[it].code) }
                    if (kanji.isNotEmpty()) kanjiWords += if (kanji.all { chapter.ruby[it] }) FURIGANA_KANJI else 1.0
                }
            }
            var inSentence = false
            for (c in part) {
                if (Segmenter.isJapanese(c)) {
                    characters++
                    inSentence = true
                } else if (c in SENTENCE_ENDS && inSentence) {
                    sentences++
                    inSentence = false
                }
            }
            if (inSentence) sentences++
            offset += part.length
        }
        if (ranks.size < MIN_WORDS || sentences == 0L) return null
        val r95 = r95(ranks.toArray())
        val kanjiShare = kanjiWords / ranks.size
        val sentenceLength = characters.toDouble() / sentences
        val length = offset.coerceAtLeast(1).toFloat()
        val words = BookWords(
            keys = distinct.keys.toList(),
            ranks = IntArray(distinct.size).also { array -> distinct.values.forEachIndexed { i, v -> array[i] = v[0].toInt() } },
            firstAt = FloatArray(distinct.size).also { array -> distinct.values.forEachIndexed { i, v -> array[i] = v[1] / length } },
        )
        val difficulty = BookDifficulty(
            version = VERSION,
            score = score(r95, kanjiShare, sentenceLength),
            r95 = r95,
            kanjiShare = kanjiShare.toFloat(),
            sentenceLength = sentenceLength.toFloat(),
            characters = characters,
            words = ranks.size,
            distinctWords = distinct.size,
            unknownShare = unknown.toFloat() / (unknown + ranks.size),
        )
        return Measured(difficulty, words)
    }

    /** Short stories count; a page or two doesn't. */
    private const val MIN_WORDS = 500

    private const val SENTENCE_ENDS = "。！？!?\n"

    private const val FURIGANA_KANJI = 0.5

    // The fit (see the class comment); refit and raise VERSION together.
    private const val INTERCEPT = -41.56
    private const val RANK_WEIGHT = 3.071
    private const val KANJI_WEIGHT = 78.49
    private const val SENTENCE_WEIGHT = 0.465
    private const val SPLIT_RATIO = 1.6
    private const val MIN_R95 = 3_000.0
    private const val MAX_R95 = 150_000.0
    private const val MIN_SENTENCE = 6.0
    private const val MAX_SENTENCE = 50.0
    private const val TOP_LEVEL = 45.0

    private class IntList {
        private var values = IntArray(4096)
        var size = 0
            private set

        fun add(value: Int) {
            if (size == values.size) values = values.copyOf(size * 2)
            values[size++] = value
        }

        fun toArray(): IntArray = values.copyOf(size)
    }
}
