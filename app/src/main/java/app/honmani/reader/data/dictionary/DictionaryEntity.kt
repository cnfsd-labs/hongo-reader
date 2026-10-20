package app.honmani.reader.data.dictionary

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * An imported Yomitan-format dictionary, described by its `index.json`.
 *
 * Yomitan identifies dictionaries by title, so installed titles are unique. The database does
 * not enforce it: an update is imported beside the dictionary it replaces, and some publishers
 * keep the title across versions (Jiten).
 */
@Entity(
    tableName = "dictionary",
    indices = [Index(value = ["title"])],
)
data class DictionaryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val revision: String,
    /** `format` (or legacy `version`) from `index.json`: 1, 2 or 3. */
    @ColumnInfo(name = "format_version") val formatVersion: Int,
    val author: String? = null,
    val attribution: String? = null,
    val enabled: Boolean = true,
    /** Lower values are shown first in lookup results. */
    val priority: Int = 0,
    @ColumnInfo(name = "imported_at") val importedAt: Long,
    val status: Status = Status.READY,
    @ColumnInfo(name = "term_count") val termCount: Int = 0,
    /** From index.json: where the publisher offers updates (see [DictionaryUpdateChecker]). */
    @ColumnInfo(name = "is_updatable") val isUpdatable: Boolean = false,
    @ColumnInfo(name = "index_url") val indexUrl: String? = null,
    @ColumnInfo(name = "download_url") val downloadUrl: String? = null,
    /** Frequency and pitch rows ([TermMetaEntity]). */
    @ColumnInfo(name = "meta_count") val metaCount: Int = 0,
    /**
     * From index.json: "rank-based" (lower is more common, the default) or "occurrence-based"
     * (higher is more common).
     */
    @ColumnInfo(name = "frequency_mode") val frequencyMode: String? = null,
    /** Characters of a kanji dictionary ([KanjiEntity]). */
    @ColumnInfo(name = "kanji_count") val kanjiCount: Int = 0,
) {
    companion object {
        const val OCCURRENCE_BASED = "occurrence-based"
    }

    enum class Status {
        /** Rows are still being written; never shown or used for lookups. */
        IMPORTING,
        READY,
    }
}
