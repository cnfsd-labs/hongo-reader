package app.honmani.reader.data.words

import app.honmani.reader.data.difficulty.Lexicon
import app.honmani.reader.data.difficulty.LexiconSources
import app.honmani.reader.data.difficulty.Segmenter
import app.honmani.reader.data.stats.Vocabulary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Finds the words to mark in the paragraphs the reader is about to read ([WordMarks]). The
 * splitter's lexicon is built from the dictionaries when first needed (a few seconds, as for
 * grading) and kept while they stay the same.
 */
class WordMarker(
    private val sources: suspend () -> LexiconSources?,
    private val lexicon: suspend (LexiconSources) -> Lexicon,
    private val knowledge: suspend () -> WordKnowledge,
) {
    private val lock = Mutex()
    private var segmenter: Pair<String, Segmenter>? = null
    private var vocabulary: Pair<WordKnowledge, Int>? = null

    /** The marks in each of [texts]; null without a word dictionary and a frequency list. */
    suspend fun mark(texts: List<String>, mode: WordMarksMode): List<List<WordMark>>? = withContext(Dispatchers.Default) {
        lock.withLock {
            val wanted = sources() ?: return@withLock null
            val split = segmenter?.takeIf { it.first == wanted.signature }?.second
                ?: Segmenter(lexicon(wanted)).also { segmenter = wanted.signature to it }
            val known = knowledge()
            // The vocabulary estimate without the skill's minimum of reading: a new reader's is small.
            val words = vocabulary?.takeIf { it.first === known }?.second
                ?: Vocabulary.estimate(known.read, known.learning).words.also { vocabulary = known to it }
            texts.map { WordMarks.of(it, split, known, mode, words) }
        }
    }
}
