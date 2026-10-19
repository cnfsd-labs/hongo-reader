package app.hongo.reader.data.dictionary

import androidx.room.withTransaction
import app.hongo.reader.data.HongoDatabase
import app.hongo.reader.data.dictionary.yomitan.InvalidDictionaryException
import app.hongo.reader.data.dictionary.yomitan.YomitanDictionaryReader
import app.hongo.reader.data.dictionary.yomitan.TermMetaKind
import app.hongo.reader.data.dictionary.yomitan.YomitanKanji
import app.hongo.reader.data.dictionary.yomitan.YomitanTerm
import app.hongo.reader.data.dictionary.yomitan.YomitanTermMeta
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** A dictionary with the same title is already installed. */
class DictionaryAlreadyInstalledException(val title: String) :
    Exception("A dictionary titled \"$title\" is already installed")

/** The dictionary is valid but has nothing HonGO! can use (e.g. only media files). */
class NoTermsException(val title: String) : Exception("\"$title\" contains no terms")

/** What kind of file a dictionary comes as. */
enum class DictionaryFileFormat {
    /** A Yomitan zip. */
    YOMITAN,

    /** Kanjium's pitch accent list ([KanjiumAccents]). */
    KANJIUM_ACCENTS,
}

data class ImportProgress(
    val title: String,
    /** Share of the dictionary's data written so far, 0 to 100. */
    val percent: Int,
)

/**
 * Turns bytes read into [ImportProgress], reported when the percentage changes. Measured in
 * bytes rather than files: frequency dictionaries such as Jiten keep everything in one file.
 */
internal class ProgressMeter(
    private val title: String,
    private val totalBytes: Long,
    private val onProgress: suspend (ImportProgress) -> Unit,
) {
    private var doneBytes = 0L
    private var reported = -1

    /** Reports the start (0%). */
    suspend fun start() = report(0)

    /** A reader that will report its own bytes read so far, added to what came before it. */
    fun part(): suspend (Long) -> Unit {
        val base = doneBytes
        return { bytes ->
            doneBytes = base + bytes
            report((doneBytes * 100 / totalBytes.coerceAtLeast(1)).toInt().coerceIn(0, 99))
        }
    }

    suspend fun finish() = report(100)

    private suspend fun report(percent: Int) {
        if (percent == reported) return
        reported = percent
        onProgress(ImportProgress(title, percent))
    }
}

/** Imports Yomitan dictionary zips into the database, one at a time. */
class DictionaryImporter(
    private val database: HongoDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val dictionaryDao = database.dictionaryDao()
    private val termDao = database.termDao()
    private val tagDao = database.tagDao()
    private val termMetaDao = database.termMetaDao()
    private val kanjiDao = database.kanjiDao()
    private val mutex = Mutex()

    /**
     * Imports the dictionary in [file], waiting for any import already running. Nothing is left
     * in the database if this fails or is cancelled.
     *
     * With [replaces], the file is an update of that installed dictionary: it may carry the same
     * title, takes over its place (enabled, order) and the old version is deleted once the new
     * one is ready. Until then lookups keep using the old one.
     *
     * @throws InvalidDictionaryException the file is not a valid Yomitan dictionary
     * @throws DictionaryAlreadyInstalledException
     * @throws NoTermsException
     */
    suspend fun import(
        file: File,
        format: DictionaryFileFormat = DictionaryFileFormat.YOMITAN,
        replaces: Long? = null,
        onProgress: suspend (ImportProgress) -> Unit = {},
    ): DictionaryEntity = mutex.withLock {
        withContext(Dispatchers.IO) {
            // Imports are serialized, so any IMPORTING row is from an interrupted one.
            dictionaryDao.deleteIncomplete()
            val replaced = replaces?.let { dictionaryDao.findById(it) }
            when (format) {
                DictionaryFileFormat.YOMITAN -> importYomitan(file, replaced, onProgress)
                DictionaryFileFormat.KANJIUM_ACCENTS -> importKanjium(file, replaced, onProgress)
            }
        }
    }

    /** Throws if a dictionary titled [title] is installed, other than the one being replaced. */
    private suspend fun checkNotInstalled(title: String, replaced: DictionaryEntity?) {
        val installed = dictionaryDao.findByTitle(title) ?: return
        if (installed.id != replaced?.id) throw DictionaryAlreadyInstalledException(title)
    }

    private suspend fun importKanjium(
        file: File,
        replaced: DictionaryEntity?,
        onProgress: suspend (ImportProgress) -> Unit,
    ): DictionaryEntity {
        val title = KanjiumAccents.TITLE
        checkNotInstalled(title, replaced)
        val pending = DictionaryEntity(
            title = title,
            revision = "kanjium",
            formatVersion = 3,
            author = "Uros O. (Kanjium)",
            attribution = KanjiumAccents.ATTRIBUTION,
            importedAt = clock(),
            status = DictionaryEntity.Status.IMPORTING,
        )
        val meter = ProgressMeter(title, file.length(), onProgress)
        return writeDictionary(pending, replaced, meter) { id, count ->
            KanjiumAccents.forEachChunk(file, CHUNK_SIZE, meter.part()) { chunk -> count(insertMeta(id, chunk)) }
        }
    }

    /**
     * Inserts [pending] as IMPORTING, runs [terms], [kanji] and [write] (which report rows
     * written through their second argument) and marks it ready, in place of [replaced] if set;
     * on failure or cancellation nothing is left behind.
     */
    private suspend fun writeDictionary(
        pending: DictionaryEntity,
        replaced: DictionaryEntity?,
        meter: ProgressMeter,
        terms: suspend (id: Long, count: (Int) -> Unit) -> Unit = { _, _ -> },
        kanji: suspend (id: Long, count: (Int) -> Unit) -> Unit = { _, _ -> },
        write: suspend (id: Long, count: (Int) -> Unit) -> Unit,
    ): DictionaryEntity {
        // A new dictionary goes last; an update takes the place of the version it replaces.
        val placed = if (replaced != null) {
            pending.copy(enabled = replaced.enabled, priority = replaced.priority)
        } else {
            pending.copy(priority = dictionaryDao.nextPriority())
        }
        val id = dictionaryDao.insert(placed)
        try {
            var termCount = 0
            var metaCount = 0
            var kanjiCount = 0
            meter.start()
            terms(id) { termCount += it }
            kanji(id) { kanjiCount += it }
            write(id) { metaCount += it }
            if (termCount == 0 && metaCount == 0 && kanjiCount == 0) throw NoTermsException(pending.title)
            database.withTransaction {
                dictionaryDao.markReady(id, termCount, metaCount, kanjiCount)
                if (replaced != null) dictionaryDao.delete(replaced.id)
            }
            meter.finish()
            return placed.copy(
                id = id,
                status = DictionaryEntity.Status.READY,
                termCount = termCount,
                metaCount = metaCount,
                kanjiCount = kanjiCount,
            )
        } catch (e: Throwable) {
            withContext(NonCancellable) { dictionaryDao.delete(id) }
            throw e
        }
    }

    private suspend fun insertMeta(dictionaryId: Long, rows: List<YomitanTermMeta>): Int {
        database.withTransaction { termMetaDao.insertAll(rows.map { it.toEntity(dictionaryId) }) }
        return rows.size
    }

    private suspend fun importYomitan(
        file: File,
        replaced: DictionaryEntity?,
        onProgress: suspend (ImportProgress) -> Unit,
    ): DictionaryEntity {
        return YomitanDictionaryReader.open(file).use { reader ->
            val index = reader.index
            checkNotInstalled(index.title, replaced)
            if (reader.termBanks.isEmpty() && reader.termMetaBanks.isEmpty() && reader.kanjiBanks.isEmpty()) {
                throw NoTermsException(index.title)
            }

            val tags = reader.readTags()
            val pending = DictionaryEntity(
                title = index.title,
                revision = index.revision,
                formatVersion = index.format,
                author = index.author,
                attribution = index.attribution,
                isUpdatable = index.isUpdatable,
                indexUrl = index.indexUrl,
                downloadUrl = index.downloadUrl,
                frequencyMode = index.frequencyMode,
                importedAt = clock(),
                status = DictionaryEntity.Status.IMPORTING,
            )
            val banks = reader.termBanks + reader.kanjiBanks + reader.termMetaBanks
            val meter = ProgressMeter(index.title, banks.sumOf { reader.bankSize(it) }, onProgress)
            writeDictionary(
                pending,
                replaced,
                meter,
                terms = { id, count ->
                    // Glossaries are packed with a preset sampled from the first chunk.
                    var packer: GlossaryPacker? = null
                    try {
                        reader.termBanks.forEach { bank ->
                            reader.forEachTermChunk(bank, CHUNK_SIZE, meter.part()) { chunk ->
                                val chunkPacker = packer ?: GlossaryPacker(GlossaryPacker.preset(chunk.map { it.glossary }))
                                    .also { created ->
                                        packer = created
                                        termDao.insertPreset(GlossaryPresetEntity(id, created.preset))
                                    }
                                val entities = chunk.map { it.toEntity(id, chunkPacker) }
                                database.withTransaction { termDao.insertAll(entities) }
                                count(chunk.size)
                            }
                        }
                    } finally {
                        packer?.close()
                    }
                    tagDao.insertAll(
                        tags.map { TagEntity(id, it.name, it.category, it.order, it.notes, it.score) },
                    )
                },
                kanji = { id, count ->
                    reader.kanjiBanks.forEach { bank ->
                        reader.forEachKanjiChunk(bank, CHUNK_SIZE, meter.part()) { chunk ->
                            database.withTransaction { kanjiDao.insertAll(chunk.map { it.toEntity(id) }) }
                            count(chunk.size)
                        }
                    }
                },
            ) { id, count ->
                reader.termMetaBanks.forEach { bank ->
                    reader.forEachTermMetaChunk(bank, CHUNK_SIZE, meter.part()) { chunk ->
                        count(insertMeta(id, chunk))
                    }
                }
            }
        }
    }

    private fun YomitanTerm.toEntity(dictionaryId: Long, packer: GlossaryPacker) =
        TermEntity(
            dictionaryId = dictionaryId,
            expression = expression,
            reading = reading,
            definitionTags = definitionTags,
            rules = rules,
            score = score,
            glossary = packer.pack(glossary),
            sequence = sequence,
            termTags = termTags,
        )

    private fun YomitanKanji.toEntity(dictionaryId: Long) =
        KanjiEntity(
            dictionaryId = dictionaryId,
            character = character,
            onyomi = onyomi,
            kunyomi = kunyomi,
            tags = tags,
            meanings = JsonArray(meanings.map(::JsonPrimitive)).toString(),
            stats = JsonObject(stats.mapValues { JsonPrimitive(it.value) }).toString(),
        )

    private fun YomitanTermMeta.toEntity(dictionaryId: Long) =
        TermMetaEntity(
            dictionaryId = dictionaryId,
            expression = expression,
            reading = reading,
            kind = when (kind) {
                TermMetaKind.FREQUENCY -> TermMetaEntity.Kind.FREQUENCY
                TermMetaKind.PITCH -> TermMetaEntity.Kind.PITCH
            },
            value = value,
            display = display,
            pitches = pitches.takeIf { it.isNotEmpty() }?.joinToString(","),
        )

    private companion object {
        /** Rows per transaction: large enough to be fast, small enough to keep the WAL small. */
        const val CHUNK_SIZE = 5_000
    }
}
