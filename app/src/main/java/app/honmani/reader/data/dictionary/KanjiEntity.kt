package app.honmani.reader.data.dictionary

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** One character of a Yomitan `kanji_bank_*.json`, such as KANJIDIC's. */
@Entity(
    tableName = "kanji",
    foreignKeys = [
        ForeignKey(
            entity = DictionaryEntity::class,
            parentColumns = ["id"],
            childColumns = ["dictionary_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("dictionary_id"), Index("character")],
)
data class KanjiEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "dictionary_id") val dictionaryId: Long,
    val character: String,
    /** Space-separated on'yomi, in katakana. */
    val onyomi: String,
    /** Space-separated kun'yomi, in hiragana; a dot separates the okurigana ("た.べる"). */
    val kunyomi: String,
    /** Space-separated tag names ("jouyou"), or empty. */
    val tags: String,
    /** The meanings as a JSON array of strings. */
    val meanings: String,
    /** Numbers and codes by tag name as a JSON object of strings ({"strokes":"9","grade":"2"}). */
    val stats: String,
)
