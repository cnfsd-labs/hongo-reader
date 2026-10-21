package app.honmani.reader.data.difficulty

import app.honmani.reader.data.dictionary.DictionaryEntity
import app.honmani.reader.lookup.WordClass

/**
 * Which dictionaries grading uses: the enabled word dictionaries, and one frequency list, Jiten
 * if installed (the scale is calibrated on it), else the first other list that ranks words.
 */
data class LexiconSources(val terms: List<DictionaryEntity>, val frequency: DictionaryEntity) {
    /** Changes when grading would give other results, so stored ones are redone. */
    val signature: String
        get() = buildString {
            append("v").append(Difficulty.VERSION)
            append("|").append(frequency.title).append("@").append(frequency.revision)
            for (dictionary in terms.sortedBy { it.title }) append("|").append(dictionary.title).append("@").append(dictionary.revision)
        }

    companion object {
        /**
         * Null when there is nothing to grade with: no word dictionary or no frequency list.
         * [hasFrequencies] tells the dictionaries with frequency rows from pitch-accent ones.
         */
        suspend fun of(enabled: List<DictionaryEntity>, hasFrequencies: suspend (DictionaryEntity) -> Boolean): LexiconSources? {
            val terms = enabled.filter { it.termCount > 0 }
            val frequencies = enabled
                .filter { it.metaCount > 0 && it.frequencyMode != DictionaryEntity.OCCURRENCE_BASED }
                .filter { hasFrequencies(it) }
            val frequency = frequencies.firstOrNull { it.title == CALIBRATED_ON }
                ?: frequencies.minByOrNull { it.priority }
                ?: return null
            if (terms.isEmpty()) return null
            return LexiconSources(terms, frequency)
        }

        /** The frequency list [Difficulty]'s scale was calibrated with. */
        const val CALIBRATED_ON = "Jiten"
    }
}

/** Builds the [Lexicon] for [LexiconSources] from the database. */
class LexiconLoader(private val dao: DifficultyDao) {
    suspend fun load(sources: LexiconSources): Lexicon {
        val builder = Lexicon.Builder()
        val termIds = sources.terms.map { it.id }
        var after = 0L
        while (true) {
            val page = dao.terms(termIds, after, PAGE)
            if (page.isEmpty()) break
            for (term in page) {
                val classes = WordClass.fromRules(term.rules)
                builder.add(term.expression, classes, 0)
                if (term.reading.isNotEmpty() && term.reading != term.expression) builder.add(term.reading, classes, 0)
            }
            after = page.last().id
        }
        // A rank counts for the written form, and for the reading the word is written in kana.
        after = 0L
        while (true) {
            val page = dao.frequencies(sources.frequency.id, after, PAGE)
            if (page.isEmpty()) break
            for (row in page) {
                val rank = row.rank.toInt()
                builder.add(row.expression, 0, rank)
                row.reading?.let { if (it != row.expression) builder.add(it, 0, rank) }
            }
            after = page.last().id
        }
        return Difficulty.addGrammar(builder).build()
    }

    private companion object {
        const val PAGE = 20_000
    }
}
