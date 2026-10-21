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
 * The distinct words of a book (dictionary forms, [Lexicon.UNRANKED] words included; names left
 * out), with their frequency rank and every place each occurs, as a share of the text in
 * [POSITIONS] steps, ascending: how often a reader has read each word up to some progress.
 */
class BookWords(val keys: List<String>, val ranks: IntArray, val positions: List<ShortArray>) {
    val size: Int get() = keys.size

    /** Where word [index] first appears, 0 to 1. */
    fun firstAt(index: Int): Float = positions[index][0] / POSITIONS.toFloat()

    /** The words met by [progress] (0 to 1), with how many times each was read by then. */
    inline fun forEachMet(progress: Float, action: (key: String, rank: Int, reads: Int) -> Unit) {
        val limit = (progress * POSITIONS).toInt()
        for (i in keys.indices) {
            val reads = countUpTo(positions[i], limit)
            if (reads > 0) action(keys[i], ranks[i], reads)
        }
    }

    companion object {
        const val POSITIONS = 10_000

        /** How many of the ascending [positions] are at most [limit]. */
        fun countUpTo(positions: ShortArray, limit: Int): Int {
            var low = 0
            var high = positions.size
            while (low < high) {
                val middle = (low + high) ushr 1
                if (positions[middle] <= limit) low = middle + 1 else high = middle
            }
            return low
        }
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
     * book's characters are kept. 4: the book's words are kept ([BookWords]). 5: names left out
     * ([isName]); every place a word occurs is kept.
     */
    const val VERSION = 5

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
        // First the words, in order: which word each is (an index into keys), and how much it
        // counts as written in kanji. Names are only known once the whole book is counted.
        val keys = LinkedHashMap<String, Int>()
        val keyRanks = IntList()
        val occurrences = ArrayList<IntList>()
        val tokens = IntList()
        val kanji = ByteList()
        var offset = 0L
        var unknown = 0
        var characters = 0L
        var sentences = 0L
        for (chapter in text) {
            val part = chapter.text
            segmenter.segment(part) { token ->
                if (token.rank == Token.UNKNOWN) {
                    unknown++
                    return@segment
                }
                val index = keys.getOrPut(token.key) {
                    keyRanks.add(token.rank)
                    occurrences += IntList(4)
                    keys.size
                }
                tokens.add(index)
                occurrences[index].add((offset + token.start).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                val inKanji = (token.start until token.start + token.length).filter { JapaneseText.isKanji(part[it].code) }
                kanji.add(
                    when {
                        inKanji.isEmpty() -> NO_KANJI
                        inKanji.all { chapter.ruby[it] } -> FURIGANA_KANJI
                        else -> WHOLE_KANJI
                    },
                )
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
        val names = BooleanArray(keys.size) { isName(keyRanks[it], occurrences[it].size, tokens.size) }
        val ranks = IntList()
        var kanjiWords = 0.0
        for (i in 0 until tokens.size) {
            val index = tokens[i]
            if (names[index]) {
                unknown++
                continue
            }
            ranks.add(keyRanks[index])
            kanjiWords += kanji[i] / WHOLE_KANJI.toDouble()
        }
        if (ranks.size < MIN_WORDS || sentences == 0L) return null
        val r95 = r95(ranks.toArray())
        val kanjiShare = kanjiWords / ranks.size
        val sentenceLength = characters.toDouble() / sentences
        val length = offset.coerceAtLeast(1).toDouble()
        val kept = keys.keys.filterIndexed { index, _ -> !names[index] }
        val keptIndices = (0 until keys.size).filter { !names[it] }
        val words = BookWords(
            keys = kept,
            ranks = IntArray(keptIndices.size) { keyRanks[keptIndices[it]] },
            positions = keptIndices.map { index ->
                val at = occurrences[index]
                ShortArray(at.size) { (at[it] / length * BookWords.POSITIONS).toInt().coerceAtMost(BookWords.POSITIONS).toShort() }
            },
        )
        val difficulty = BookDifficulty(
            version = VERSION,
            score = score(r95, kanjiShare, sentenceLength),
            r95 = r95,
            kanjiShare = kanjiShare.toFloat(),
            sentenceLength = sentenceLength.toFloat(),
            characters = characters,
            words = ranks.size,
            distinctWords = kept.size,
            unknownShare = unknown.toFloat() / (unknown + ranks.size),
        )
        return Measured(difficulty, words)
    }

    /**
     * A word the book uses far more than its rank says words are used: mostly names of people
     * and places (茅, 八尋, 千早 in a novel), which dictionaries list as rare words. jiten.moe, which
     * the scale was fitted on, leaves names out; counted, a novel's names alone made it read
     * 10–15 points harder. Also a book's own rare terms, which a reader learns within the book.
     * By Zipf's law a word of rank r is about 1 / (r · ln N) of a text's words.
     */
    internal fun isName(rank: Int, count: Int, words: Int): Boolean {
        if (count < NAME_MIN_COUNT || rank < NAME_MIN_RANK) return false
        val expected = words / (rank.toDouble() * ln(Segmenter.RARE_RANK.toDouble()))
        return count >= NAME_EXCESS * expected
    }

    /** Short stories count; a page or two doesn't. */
    private const val MIN_WORDS = 500

    private const val SENTENCE_ENDS = "。！？!?\n"

    // A word written in kanji counts 1, with furigana over all its kanji 0.5 (stored in halves).
    private const val NO_KANJI: Byte = 0
    private const val WHOLE_KANJI: Byte = 2
    private const val FURIGANA_KANJI: Byte = 1

    private const val NAME_MIN_COUNT = 20
    private const val NAME_MIN_RANK = 5_000
    private const val NAME_EXCESS = 1_000.0

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

    private class ByteList {
        private var values = ByteArray(4096)
        var size = 0
            private set

        fun add(value: Byte) {
            if (size == values.size) values = values.copyOf(size * 2)
            values[size++] = value
        }

        operator fun get(index: Int): Byte = values[index]
    }

    private class IntList(capacity: Int = 4096) {
        private var values = IntArray(capacity)
        var size = 0
            private set

        fun add(value: Int) {
            if (size == values.size) values = values.copyOf(size * 2)
            values[size++] = value
        }

        operator fun get(index: Int): Int = values[index]

        fun toArray(): IntArray = values.copyOf(size)
    }
}
