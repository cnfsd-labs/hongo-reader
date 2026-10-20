package app.honmani.reader.data.dictionary

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** One entry of a Yomitan `term_bank_*.json`. */
@Entity(
    tableName = "term",
    foreignKeys = [
        ForeignKey(
            entity = DictionaryEntity::class,
            parentColumns = ["id"],
            childColumns = ["dictionary_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("dictionary_id"),
        Index("expression"),
        Index("reading"),
    ],
)
data class TermEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "dictionary_id") val dictionaryId: Long,
    val expression: String,
    /** Never empty: Yomitan's "same as expression" (empty string) is stored as the expression. */
    val reading: String,
    /** Space-separated tag names for the definitions, or null. */
    @ColumnInfo(name = "definition_tags") val definitionTags: String?,
    /** Space-separated deinflection rule ids (`v1`, `v5`, `adj-i`, ...); empty if none. */
    val rules: String,
    /** Popularity; higher is more common. */
    val score: Double,
    /**
     * The definitions as a JSON array (strings, structured content or other glossary objects),
     * packed: [DictionaryLookup][app.honmani.reader.lookup.DictionaryLookup] unpacks them.
     */
    val glossary: Glossary,
    /** Entries with the same sequence number in one dictionary belong together. */
    val sequence: Long?,
    /** Space-separated tag names for the term itself, or null. */
    @ColumnInfo(name = "term_tags") val termTags: String?,
)
