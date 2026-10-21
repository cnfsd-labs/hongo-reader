package app.honmani.reader.data.difficulty

import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/** A book's grade, with the [LexiconSources.signature] it was computed with. */
data class StoredDifficulty(val difficulty: BookDifficulty, val signature: String)

/**
 * Book grades as small JSON files named by the book's content hash, like the books themselves. A
 * cache: deleting the directory only means the books are graded again.
 */
class DifficultyStore(private val directory: File) {
    private val _grades = MutableStateFlow<Map<String, StoredDifficulty>>(emptyMap())

    /** By content hash; empty until [load]. */
    val grades: StateFlow<Map<String, StoredDifficulty>> = _grades.asStateFlow()

    /** Reads the stored grades once; call off the main thread. */
    fun load() {
        val files = directory.listFiles { file -> file.name.endsWith(SUFFIX) }.orEmpty()
        _grades.value = files.mapNotNull { file ->
            runCatching { file.name.removeSuffix(SUFFIX) to decode(JSONObject(file.readText())) }.getOrNull()
        }.toMap()
    }

    fun save(contentHash: String, grade: StoredDifficulty) {
        directory.mkdirs()
        val file = File(directory, contentHash + SUFFIX)
        val temporary = File(directory, "$contentHash$SUFFIX.tmp")
        temporary.writeText(encode(grade).toString())
        temporary.renameTo(file)
        _grades.value += contentHash to grade
    }

    /** Drops grades of books no longer in the library. */
    fun keepOnly(contentHashes: Set<String>) {
        val gone = _grades.value.keys - contentHashes
        if (gone.isEmpty()) return
        for (hash in gone) File(directory, hash + SUFFIX).delete()
        _grades.value -= gone
    }

    internal companion object {
        private const val SUFFIX = ".json"

        fun encode(grade: StoredDifficulty): JSONObject = with(grade.difficulty) {
            JSONObject()
                .put("signature", grade.signature)
                .put("version", version)
                .put("score", score)
                .put("r95", r95)
                .put("kanjiShare", kanjiShare.toDouble())
                .put("sentenceLength", sentenceLength.toDouble())
                .put("characters", characters)
                .put("words", words)
                .put("distinctWords", distinctWords)
                .put("unknownShare", unknownShare.toDouble())
        }

        fun decode(json: JSONObject): StoredDifficulty = StoredDifficulty(
            difficulty = BookDifficulty(
                version = json.getInt("version"),
                score = json.getInt("score"),
                r95 = json.getInt("r95"),
                kanjiShare = json.getDouble("kanjiShare").toFloat(),
                sentenceLength = json.getDouble("sentenceLength").toFloat(),
                characters = json.optLong("characters"),
                words = json.getInt("words"),
                distinctWords = json.getInt("distinctWords"),
                unknownShare = json.getDouble("unknownShare").toFloat(),
            ),
            signature = json.getString("signature"),
        )
    }
}
