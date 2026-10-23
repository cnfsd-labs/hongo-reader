package app.honmani.reader.data.words

import app.honmani.reader.data.difficulty.Segmenter
import app.honmani.reader.data.difficulty.Token

/** Which words the reader marks (reader setting, `assets/honmani/word-marks.js`). */
enum class WordMarksMode(val storage: String) {
    OFF("off"),

    /** Words looked up and not known yet. */
    LEARNING("learning"),

    /** Those, and words the reader has never met ([WordMark.Kind.NEW]). */
    NEW("new"),
    ;

    companion object {
        val DEFAULT = LEARNING

        fun fromStorage(value: String?): WordMarksMode = entries.firstOrNull { it.storage == value } ?: DEFAULT
    }
}

/** A word to mark in a paragraph: [start] and [length] in its text. */
data class WordMark(val start: Int, val length: Int, val kind: Kind) {
    enum class Kind {
        /** Looked up and not known yet (`WordProgress`). */
        LEARNING,

        /** Never met in reading. */
        NEW,
    }
}

/**
 * The words to mark in the reader, split as books are graded ([Segmenter]). A new word is one the
 * reader has never met, not one the vocabulary estimate guesses they don't know: a mark is a claim
 * about one word, which a guess about frequency bands can't make. A new reader's first pages are
 * mostly marked; the particles and other common words lose their marks within a few pages.
 */
object WordMarks {
    fun of(text: String, segmenter: Segmenter, knowledge: WordKnowledge, mode: WordMarksMode): List<WordMark> {
        if (mode == WordMarksMode.OFF) return emptyList()
        val marks = ArrayList<WordMark>()
        // Where a word written as it was looked up ends ([WordKnowledge.learningForms]).
        var formEnd = 0
        segmenter.segment(text) { token ->
            if (token.start < formEnd) return@segment
            knowledge.learningFormAt(text, token.start)?.let { form ->
                marks += WordMark(token.start, form.length, WordMark.Kind.LEARNING)
                formEnd = token.start + form.length
                return@segment
            }
            // Names and other text no dictionary knows stay unmarked.
            if (token.rank == Token.UNKNOWN) return@segment
            val kind = when {
                token.key in knowledge.learning -> WordMark.Kind.LEARNING
                mode == WordMarksMode.NEW && token.key !in knowledge.read -> WordMark.Kind.NEW
                else -> return@segment
            }
            marks += WordMark(token.start, token.length, kind)
        }
        return marks
    }
}
