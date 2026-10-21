package app.honmani.reader.data.stats

import app.honmani.reader.data.difficulty.BookWords
import kotlin.math.pow
import kotlin.math.roundToInt

/** How many words the reader knows, estimated from what they read and looked up. */
data class VocabularyEstimate(
    /**
     * The frequency rank up to which the reader knows most words ([Vocabulary.KNOWN_SHARE]): about
     * how many of the most common words they know. Comparable with a book's R95.
     */
    val words: Int,
    /** Distinct words met in graded books up to the reader's position, and how many of them are being learned. */
    val met: Int,
    val learning: Int,
)

/**
 * The vocabulary estimate. Every word met in a graded book (before the reader's position) counts
 * as known, unless it is in the word list as being learned (looked up and not marked Learned).
 * Words are grouped in frequency bands that double in size (the 500 most common, the next 500,
 * the next 1,000, …), and the estimate is the rank where the share of known words falls below
 * [KNOWN_SHARE], between band edges on a log scale. A band with few words met leans on the band
 * before it, half as well known: a handful of words met, none looked up, is no proof of knowing a
 * band, so the estimate never runs far ahead of what was read. Readers don't look up every word
 * they don't know, which [KNOWN_SHARE] allows for in part; it stays an upper estimate.
 *
 * (Summing each band's size times its known share, as first planned, let a few rare words without
 * lookups add 100,000 words.)
 */
object Vocabulary {
    /** Band edges by frequency rank; rarer words are left out. */
    val BAND_EDGES = intArrayOf(0, 500, 1_000, 2_000, 4_000, 8_000, 16_000, 32_000, 64_000, 128_000, 256_000)

    /** How many met words make a band's own share count as much as the guess from the band before. */
    const val PRIOR_WORDS = 20.0

    /** A band is guessed half as well known as the one before it. */
    const val DECAY = 0.5

    /** Known up to where at most one met word in ten is being learned. */
    const val KNOWN_SHARE = 0.9

    /** [books]: each book's words with how far the reader is in it (0 to 1). [learning]: dictionary forms being learned. */
    fun estimate(books: List<Pair<BookWords, Float>>, learning: Set<String>): VocabularyEstimate {
        val met = HashMap<String, Int>()
        for ((words, progress) in books) {
            words.forEachMet(progress) { key, rank -> met.merge(key, rank, ::minOf) }
        }
        val bands = BAND_EDGES.size - 1
        val seen = IntArray(bands)
        val unknown = IntArray(bands)
        var learningMet = 0
        for ((key, rank) in met) {
            val band = band(rank) ?: continue
            seen[band]++
            if (key in learning) {
                unknown[band]++
                learningMet++
            }
        }
        var previous = 1.0
        var words = BAND_EDGES.last().toDouble()
        for (band in 0 until bands) {
            val guess = if (band == 0) 1.0 else previous * DECAY
            val share = (seen[band] - unknown[band] + PRIOR_WORDS * guess) / (seen[band] + PRIOR_WORDS)
            if (share < KNOWN_SHARE) {
                // Where between this band's edges the share, falling from the last band's, crosses the line.
                val low = BAND_EDGES[band].coerceAtLeast(1).toDouble()
                val high = BAND_EDGES[band + 1].toDouble()
                val part = ((previous - KNOWN_SHARE) / (previous - share)).coerceIn(0.0, 1.0)
                words = low * (high / low).pow(part)
                break
            }
            previous = share
        }
        return VocabularyEstimate(words = words.roundToInt(), met = met.size, learning = learningMet)
    }

    private fun band(rank: Int): Int? {
        for (band in 0 until BAND_EDGES.size - 1) if (rank <= BAND_EDGES[band + 1]) return band
        return null
    }
}
