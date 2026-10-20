package app.honmani.reader.data.dictionary

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Frequency or pitch accent of a word, from a Yomitan `term_meta_bank_*.json` or a converted
 * source such as Kanjium's pitch accents.
 */
@Entity(
    tableName = "term_meta",
    foreignKeys = [
        ForeignKey(
            entity = DictionaryEntity::class,
            parentColumns = ["id"],
            childColumns = ["dictionary_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("dictionary_id"), Index("expression")],
)
data class TermMetaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "dictionary_id") val dictionaryId: Long,
    val expression: String,
    /** The reading this applies to; null for any reading of [expression]. */
    val reading: String?,
    val kind: Kind,
    /**
     * Frequency: the number to compare (a rank or a count, see [DictionaryEntity.frequencyMode]).
     * Null for pitch rows, and for frequencies given only as text.
     */
    val value: Double?,
    /** Frequency as the dictionary wants it shown ("395", "1㋕"); null to show [value]. */
    val display: String?,
    /** Pitch: downstep positions as comma-separated morae counts ("0", "0,2"); null otherwise. */
    val pitches: String?,
) {
    enum class Kind { FREQUENCY, PITCH }
}
