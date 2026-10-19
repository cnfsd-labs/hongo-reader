package app.hongo.reader.lookup

import app.hongo.reader.data.dictionary.DictionaryDao
import app.hongo.reader.data.dictionary.DictionaryEntity
import app.hongo.reader.data.dictionary.Glossary
import app.hongo.reader.data.dictionary.GlossaryPacker
import app.hongo.reader.data.dictionary.KanjiDao
import app.hongo.reader.data.dictionary.MAX_LOOKUP_TEXTS
import app.hongo.reader.data.dictionary.TermDao
import app.hongo.reader.data.dictionary.TermEntity
import app.hongo.reader.data.dictionary.TermMetaDao
import app.hongo.reader.data.dictionary.TermMetaEntity

/** A dictionary entry that matches the start of the looked-up text. */
data class LookupEntry(
    val term: TermEntity,
    val dictionary: DictionaryEntity,
    /** The part of the looked-up text this entry covers, as written there. */
    val source: String,
    /** Grammar undone to get from [source] to the entry, from the entry outwards. */
    val inflections: List<Inflection>,
    /** True if [source] matched the entry's written form, false if only its reading. */
    val matchedExpression: Boolean,
    /** True if the book's furigana over [source] gives this entry's reading. */
    val matchesFurigana: Boolean = false,
)

/** How common a word is according to one frequency dictionary. */
data class Frequency(
    val dictionaryTitle: String,
    /** As the dictionary shows it ("395", "1㋕"). */
    val display: String,
    /** Lower is more common; null if the dictionary counts occurrences or gives only text. */
    val rank: Double?,
) {
    /** "Jiten #395", as on cards. */
    val label: String get() = "$dictionaryTitle #$display"
}

/** Frequency and pitch accent of one word (written form and reading). */
data class WordMeta(
    /** One per frequency dictionary, in dictionary order. */
    val frequencies: List<Frequency> = emptyList(),
    /** Downstep positions in morae (0: no downstep), from the first pitch dictionary that has them. */
    val pitches: List<Int> = emptyList(),
) {
    /** The best rank among the frequency dictionaries, for ordering; null if none ranks the word. */
    val rank: Double? get() = frequencies.mapNotNull { it.rank }.minOrNull()

    companion object {
        val NONE = WordMeta()
    }
}

data class LookupResult(
    /** Best first. */
    val entries: List<LookupEntry>,
    /** False if no dictionary is installed and enabled, so nothing could match. */
    val dictionariesAvailable: Boolean = true,
    /** Frequency and pitch of the entries' words, by [metaKey]. */
    val meta: Map<String, WordMeta> = emptyMap(),
) {
    fun metaOf(expression: String, reading: String): WordMeta = meta[metaKey(expression, reading)] ?: WordMeta.NONE

    /** Length of the longest match, for highlighting it in the book; 0 if nothing matched. */
    val matchedLength: Int get() = entries.firstOrNull()?.source?.length ?: 0
}

/** Key of a word's frequency and pitch in [LookupResult.meta]. */
fun metaKey(expression: String, reading: String): String = "$expression\u0000$reading"

/**
 * Finds dictionary entries for the text after a tap, Yomitan-style: every prefix of the text is
 * deinflected, candidates are looked up by written form and reading, entries whose word class
 * does not fit the deinflection are dropped, and the rest are ranked. Furigana in the book, if
 * any, ranks the entry with that reading first among matches of the same length.
 */
class DictionaryLookup(
    private val dictionaryDao: DictionaryDao,
    private val termDao: TermDao,
    private val termMetaDao: TermMetaDao? = null,
    private val kanjiDao: KanjiDao? = null,
) {
    private data class Candidate(val source: String, val deinflection: Deinflection)

    /**
     * Presets of the dictionaries seen, by id and import time (an id can come back after a
     * delete); an empty array for a dictionary without one.
     */
    private val presets = HashMap<Pair<Long, Long>, ByteArray>()

    private suspend fun presetOf(dictionary: DictionaryEntity): ByteArray? {
        val key = dictionary.id to dictionary.importedAt
        val preset = synchronized(presets) { presets[key] }
            ?: (termDao.glossaryPreset(dictionary.id) ?: ByteArray(0)).also { synchronized(presets) { presets[key] = it } }
        return preset.takeIf { it.isNotEmpty() }
    }

    /** [term] with its glossary unpacked, so [Glossary.json] reads it. */
    private suspend fun unpacked(term: TermEntity, dictionary: DictionaryEntity): TermEntity =
        if (term.glossary.isPacked) {
            term.copy(glossary = Glossary.plain(GlossaryPacker.unpack(term.glossary, presetOf(dictionary))))
        } else {
            term
        }

    suspend fun lookup(
        text: String,
        ruby: List<RubySpan> = emptyList(),
        maxLength: Int = MAX_SOURCE_LENGTH,
    ): LookupResult {
        val dictionaries = dictionaryDao.enabledReady().associateBy { it.id }
        // Frequency, pitch and kanji dictionaries alone can't find words.
        if (dictionaries.values.none { it.termCount > 0 }) {
            return LookupResult(emptyList(), dictionariesAvailable = false)
        }

        val candidates = candidates(text, maxLength)
        val candidatesByText = candidates.groupBy { it.deinflection.text }
        val terms = candidatesByText.keys.chunked(MAX_LOOKUP_TEXTS)
            .flatMap { termDao.findByExpressionOrReading(it) }
            .distinctBy { it.id }

        val readingPrefixes = HashMap<String, String?>()
        fun readingPrefix(source: String) =
            readingPrefixes.getOrPut(source) { Furigana.readingPrefix(source, ruby) }

        val matched = terms.mapNotNull { term ->
            val dictionary = dictionaries[term.dictionaryId] ?: return@mapNotNull null
            val termClasses = WordClass.fromRules(term.rules)
            val matches = (candidatesByText[term.expression].orEmpty().map { it to true }) +
                (candidatesByText[term.reading].orEmpty().map { it to false })
            matches
                .filter { (candidate, _) -> candidate.deinflection.fits(termClasses) }
                .maxWithOrNull(matchOrder)
                ?.let { (candidate, matchedExpression) ->
                    LookupEntry(
                        term = term,
                        dictionary = dictionary,
                        source = candidate.source,
                        inflections = candidate.deinflection.inflections,
                        matchedExpression = matchedExpression,
                        matchesFurigana = readingPrefix(candidate.source)?.let { prefix ->
                            Furigana.agrees(prefix, term.reading.ifEmpty { term.expression })
                        } ?: false,
                    )
                }
        }
        // Only the entries that matched are unpacked.
        val entries = matched.map { it.copy(term = unpacked(it.term, it.dictionary)) }
        val meta = meta(entries, dictionaries)
        val order = compareByDescending<LookupEntry> { it.source.length }
            .thenByDescending { it.matchesFurigana }
            .thenBy { it.inflections.size }
            .thenByDescending { it.matchedExpression }
            // The more common word first (by the frequency dictionaries, if any are installed).
            .thenBy { meta[metaKey(it.term.expression, it.term.reading)]?.rank ?: Double.MAX_VALUE }
            .then(entryOrder)
        return LookupResult(entries.sortedWith(order), meta = meta)
    }

    /**
     * What the enabled kanji dictionaries say about the kanji in [text], by character; the
     * first dictionary (by order) that has a character wins.
     */
    suspend fun kanji(text: String): Map<String, KanjiInfo> {
        val dao = kanjiDao ?: return emptyMap()
        val characters = JapaneseText.kanjiIn(text).take(MAX_LOOKUP_TEXTS)
        if (characters.isEmpty()) return emptyMap()
        val dictionaries = dictionaryDao.enabledReady().filter { it.kanjiCount > 0 }.associateBy { it.id }
        if (dictionaries.isEmpty()) return emptyMap()
        val dictionaryOrder = compareBy<DictionaryEntity>({ it.priority }, { it.title })
        return dao.find(characters)
            .filter { it.dictionaryId in dictionaries }
            .groupBy { it.character }
            .mapValues { (_, rows) ->
                val row = rows.minWith(compareBy(dictionaryOrder) { dictionaries.getValue(it.dictionaryId) })
                KanjiInfo.from(row, dictionaries.getValue(row.dictionaryId))
            }
    }

    /** Frequency and pitch of each word among [entries], from the enabled meta dictionaries. */
    private suspend fun meta(
        entries: List<LookupEntry>,
        dictionaries: Map<Long, DictionaryEntity>,
    ): Map<String, WordMeta> {
        val dao = termMetaDao ?: return emptyMap()
        if (entries.isEmpty() || dictionaries.values.none { it.metaCount > 0 }) return emptyMap()
        val words = entries.map { it.term.expression to it.term.reading }.distinct()
        val rows = words.map { it.first }.distinct().chunked(MAX_LOOKUP_TEXTS)
            .flatMap { dao.find(it) }
            .filter { it.dictionaryId in dictionaries }
        if (rows.isEmpty()) return emptyMap()
        val byExpression = rows.groupBy { it.expression }
        val dictionaryOrder = compareBy<DictionaryEntity>({ it.priority }, { it.title })
        return words.associate { (expression, reading) ->
            val matching = byExpression[expression].orEmpty().filter { it.reading == null || it.reading == reading }
            metaKey(expression, reading) to wordMeta(matching, dictionaries, dictionaryOrder)
        }.filterValues { it != WordMeta.NONE }
    }

    private fun wordMeta(
        rows: List<TermMetaEntity>,
        dictionaries: Map<Long, DictionaryEntity>,
        dictionaryOrder: Comparator<DictionaryEntity>,
    ): WordMeta {
        if (rows.isEmpty()) return WordMeta.NONE
        val byDictionary = rows.groupBy { dictionaries.getValue(it.dictionaryId) }.toSortedMap(dictionaryOrder)
        val frequencies = byDictionary.mapNotNull { (dictionary, dictionaryRows) ->
            val frequency = dictionaryRows.filter { it.kind == TermMetaEntity.Kind.FREQUENCY }
                // A row for this reading says more than one for any reading; then the most common.
                .sortedWith(
                    compareBy<TermMetaEntity> { it.reading == null }.thenBy {
                        if (dictionary.ranksFrequency) it.value ?: Double.MAX_VALUE else -(it.value ?: 0.0)
                    },
                )
                .firstOrNull() ?: return@mapNotNull null
            val display = frequency.display ?: frequency.value?.let(::formatNumber) ?: return@mapNotNull null
            Frequency(dictionary.title, display, frequency.value.takeIf { dictionary.ranksFrequency })
        }
        val pitches = byDictionary.values.firstNotNullOfOrNull { dictionaryRows ->
            dictionaryRows.filter { it.kind == TermMetaEntity.Kind.PITCH }
                .flatMap { row -> row.pitches.orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() } }
                .distinct()
                .ifEmpty { null }
        }.orEmpty()
        return WordMeta(frequencies, pitches)
    }

    /** Whether lower frequency values of this dictionary mean more common words. */
    private val DictionaryEntity.ranksFrequency: Boolean
        get() = frequencyMode != DictionaryEntity.OCCURRENCE_BASED

    private fun formatNumber(value: Double): String =
        if (value == Math.floor(value) && value < 1e15) value.toLong().toString() else value.toString()

    /** Prefixes of [text] (whole code points, longest first) with their deinflections. */
    private fun candidates(text: String, maxLength: Int): List<Candidate> {
        val codePoints = text.codePoints().toArray()
        val ends = (minOf(codePoints.size, maxLength) downTo 1)
            .map { String(codePoints, 0, it) }
            .filter { it.isNotBlank() }
        return ends.flatMap { source ->
            val variants = buildSet {
                add(source)
                if (JapaneseText.containsKatakana(source)) add(JapaneseText.toHiragana(source))
            }
            variants.flatMap { variant -> Deinflector.deinflect(variant).map { Candidate(source, it) } }
        }
    }

    private fun Deinflection.fits(termClasses: Int): Boolean =
        conditions == WordClass.ANY || conditions and termClasses != 0

    private companion object {
        /** Matches the tap bridge's scan length. */
        const val MAX_SOURCE_LENGTH = 20

        /** Better match for one term: longer source, then fewer inflections, then written form. */
        val matchOrder: Comparator<Pair<Candidate, Boolean>> =
            compareBy<Pair<Candidate, Boolean>> { it.first.source.length }
                .thenByDescending { it.first.deinflection.inflections.size }
                .thenBy { it.second }

        val entryOrder: Comparator<LookupEntry> =
            compareByDescending<LookupEntry> { it.source.length }
                .thenByDescending { it.matchesFurigana }
                .thenBy { it.inflections.size }
                .thenByDescending { it.matchedExpression }
                .thenBy { it.dictionary.priority }
                .thenBy { it.dictionary.title }
                .thenByDescending { it.term.score }
                .thenBy { it.term.sequence ?: Long.MAX_VALUE }
                .thenBy { it.term.id }
    }
}
