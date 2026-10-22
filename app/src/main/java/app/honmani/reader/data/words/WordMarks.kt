package app.honmani.reader.data.words

import app.honmani.reader.data.difficulty.Segmenter
import app.honmani.reader.data.difficulty.Token

/** Which words the reader marks (reader setting, `assets/honmani/word-marks.js`). */
enum class WordMarksMode(val storage: String) {
    OFF("off"),

    /** Words looked up and not known yet. */
    LEARNING("learning"),

    /** Those, and words probably new to the reader. */
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

        /** Never met in reading and beyond the reader's vocabulary ([WordKnowledge.knows]). */
        NEW,
    }
}

/** The words to mark in the reader, split as books are graded ([Segmenter]). */
object WordMarks {
    /** [vocabulary]: the reader's vocabulary as a frequency rank, for [WordMarksMode.NEW]. */
    fun of(text: String, segmenter: Segmenter, knowledge: WordKnowledge, mode: WordMarksMode, vocabulary: Int): List<WordMark> {
        if (mode == WordMarksMode.OFF) return emptyList()
        val marks = ArrayList<WordMark>()
        segmenter.segment(text) { token ->
            // Names and other text no dictionary knows stay unmarked.
            if (token.rank == Token.UNKNOWN) return@segment
            val kind = when {
                token.key in knowledge.learning -> WordMark.Kind.LEARNING
                mode == WordMarksMode.NEW && !knowledge.knows(token.key, token.rank, vocabulary) -> WordMark.Kind.NEW
                else -> return@segment
            }
            marks += WordMark(token.start, token.length, kind)
        }
        return marks
    }
}
