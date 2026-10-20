package app.honmani.reader.data.dictionary

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey

/** A tag definition from a Yomitan `tag_bank_*.json` (or `tagMeta` in a format 1 index). */
@Entity(
    tableName = "tag",
    primaryKeys = ["dictionary_id", "name"],
    foreignKeys = [
        ForeignKey(
            entity = DictionaryEntity::class,
            parentColumns = ["id"],
            childColumns = ["dictionary_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class TagEntity(
    @ColumnInfo(name = "dictionary_id") val dictionaryId: Long,
    val name: String,
    val category: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Double,
    val notes: String,
    val score: Double,
)
