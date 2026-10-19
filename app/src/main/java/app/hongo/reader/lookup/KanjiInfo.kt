package app.hongo.reader.lookup

import app.hongo.reader.data.dictionary.DictionaryEntity
import app.hongo.reader.data.dictionary.KanjiEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** What a kanji dictionary (KANJIDIC) says about one character. */
data class KanjiInfo(
    val character: String,
    val meanings: List<String>,
    /** On'yomi in katakana ("ショク"). */
    val onyomi: List<String>,
    /** Kun'yomi in hiragana; a dot separates the okurigana ("た.べる"), a dash marks affixes. */
    val kunyomi: List<String>,
    val strokes: Int? = null,
    /** School grade 1 to 6, 8 for the rest of the jōyō kanji, 9 and 10 for name kanji. */
    val grade: Int? = null,
    /** Rank among the 2,500 kanji most used in newspapers (1 is the most common). */
    val frequency: Int? = null,
    val dictionaryTitle: String = "",
) {
    /** The kind of kanji by [grade]. */
    val level: Level?
        get() = when (grade) {
            in 1..6 -> Level.SCHOOL
            8 -> Level.JOUYOU
            9, 10 -> Level.NAMES
            else -> null
        }

    enum class Level {
        /** Taught in primary school, in [grade]. */
        SCHOOL,

        /** The other jōyō (everyday use) kanji, taught in secondary school. */
        JOUYOU,

        /** Jinmeiyō: approved for personal names, rare otherwise. */
        NAMES,
    }

    companion object {
        /** Reads a stored [KanjiEntity]; tolerant of what an imported dictionary left out. */
        fun from(entity: KanjiEntity, dictionary: DictionaryEntity): KanjiInfo {
            val meanings = runCatching { Json.parseToJsonElement(entity.meanings) as? JsonArray }.getOrNull()
                ?.mapNotNull { (it as? JsonPrimitive)?.content?.takeIf(String::isNotBlank) }
                .orEmpty()
            val stats = runCatching { Json.parseToJsonElement(entity.stats) as? JsonObject }.getOrNull()
            fun number(key: String) = (stats?.get(key) as? JsonPrimitive)?.content?.trim()?.toIntOrNull()
            return KanjiInfo(
                character = entity.character,
                meanings = meanings,
                onyomi = entity.onyomi.split(' ').filter { it.isNotBlank() },
                kunyomi = entity.kunyomi.split(' ').filter { it.isNotBlank() },
                strokes = number("strokes"),
                grade = number("grade"),
                frequency = number("freq"),
                dictionaryTitle = dictionary.title,
            )
        }
    }
}
