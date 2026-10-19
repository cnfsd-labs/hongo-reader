package app.hongo.reader.lookup

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.URLDecoder

/**
 * Pointer entries, which only name another entry of the same dictionary:
 * - Yomitan's "form of" entries, whose glossary is only `["生れる", ["past …"]]` items: the
 *   Japanese Wiktionary (wty) has one for most conjugated forms (生れた, 食べた).
 * - Jitendex and JMdict for Yomitan list rare spellings (生れる) as entries whose sequence number
 *   is the usual spelling's, negated, and whose glossary is only a link to it ("⟶ 生まれる";
 *   Jitendex adds a form-of item).
 */
object Redirect {
    private const val QUERY = "?query="

    /** The written form an entry with [sequence] and [glossary] (JSON) points to; null if it is a real entry. */
    fun target(sequence: Long?, glossary: String): String? {
        val items = try {
            Json.parseToJsonElement(glossary) as? JsonArray
        } catch (_: SerializationException) {
            null
        } ?: return null
        val formOf = items.map(::formOfTarget)
        if (items.isNotEmpty() && formOf.all { it != null }) return formOf.first()
        if (sequence == null || sequence >= 0) return null
        return formOf.firstNotNullOfOrNull { it } ?: items.firstNotNullOfOrNull(::linkTarget)
    }

    /** The term of a form-of item: [term, [rules or notes]]. */
    private fun formOfTarget(item: JsonElement): String? =
        (item as? JsonArray)?.takeIf { it.size == 2 && it[1] is JsonArray }
            ?.let { (it[0] as? JsonPrimitive)?.takeIf { term -> term.isString }?.content }
            ?.takeIf { it.isNotBlank() }

    /** The query of the first `?query=` link in structured content. */
    private fun linkTarget(element: JsonElement): String? = when (element) {
        is JsonArray -> element.firstNotNullOfOrNull(::linkTarget)
        is JsonObject -> {
            val href = (element["href"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            if ((element["tag"] as? JsonPrimitive)?.content == "a" && href?.startsWith(QUERY) == true) {
                URLDecoder.decode(href.removePrefix(QUERY).substringBefore('&'), "UTF-8").takeIf { it.isNotBlank() }
            } else {
                element["content"]?.let(::linkTarget)
            }
        }
        else -> null
    }
}
