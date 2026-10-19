package app.hongo.reader.data.dictionary.yomitan

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Reads one row of a Yomitan `term_meta_bank`: `[term, "freq", data]` or `[term, "pitch", data]`.
 *
 * A frequency is a number, a text ("395" or "1㋕"), `{value, displayValue}`, or any of these
 * under `{reading, frequency}` when it applies to one reading only. A pitch row is
 * `{reading, pitches: [{position}]}`, where a position is the downstep mora (0 for none) or the
 * level of each mora ("LHHL").
 */
object TermMetaParser {
    /** The rows in [element] (none for kinds HonGO! doesn't use). Throws IllegalArgumentException. */
    fun parse(element: JsonElement): List<YomitanTermMeta> {
        val row = element as? JsonArray ?: invalid("not an array")
        if (row.size < 3) invalid("expected 3 fields, found ${row.size}")
        val expression = row[0].string() ?: invalid("field 1 is not a string")
        if (expression.isEmpty()) invalid("empty term")
        return when (row[1].string()) {
            "freq" -> listOfNotNull(frequency(expression, row[2]))
            "pitch" -> listOfNotNull(pitch(expression, row[2]))
            else -> emptyList()
        }
    }

    private fun frequency(expression: String, data: JsonElement): YomitanTermMeta? {
        val obj = data as? JsonObject
        val reading = obj?.get("reading")?.string()?.ifEmpty { null }
        val value = if (obj != null && "frequency" in obj) obj["frequency"]!! else data
        val (number, display) = frequencyValue(value) ?: return null
        return YomitanTermMeta(expression, reading, TermMetaKind.FREQUENCY, number, display)
    }

    /** (number to compare, text to show) */
    private fun frequencyValue(value: JsonElement): Pair<Double?, String?>? = when {
        value is JsonPrimitive && !value.isString -> value.doubleOrNull?.let { it to null }
        value is JsonPrimitive -> value.content.trim().ifEmpty { null }?.let { leadingNumber(it) to it }
        value is JsonObject -> {
            val number = (value["value"] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull
            val display = value["displayValue"]?.string()?.trim()?.ifEmpty { null }
            if (number == null && display == null) null else (number ?: display?.let(::leadingNumber)) to display
        }
        else -> null
    }

    private fun leadingNumber(text: String): Double? =
        Regex("""^\d+(\.\d+)?""").find(text.replace(",", ""))?.value?.toDoubleOrNull()

    private fun pitch(expression: String, data: JsonElement): YomitanTermMeta? {
        val obj = data as? JsonObject ?: invalid("pitch data is not an object")
        val reading = obj["reading"]?.string()?.ifEmpty { null } ?: invalid("pitch data has no reading")
        val pitches = (obj["pitches"] as? JsonArray).orEmpty().mapNotNull { pitch ->
            when (val position = (pitch as? JsonObject)?.get("position")) {
                is JsonPrimitive -> if (position.isString) downstepOf(position.content) else position.intOrNull
                else -> null
            }
        }.filter { it >= 0 }.distinct()
        if (pitches.isEmpty()) return null
        return YomitanTermMeta(expression, reading, TermMetaKind.PITCH, pitches = pitches)
    }

    /**
     * The downstep of a level pattern such as "LHHL" (3) or "LHHH" (0): the last high mora
     * before the first fall; 0 if it never falls.
     */
    internal fun downstepOf(levels: String): Int? {
        val pattern = levels.uppercase()
        if (pattern.isEmpty() || pattern.any { it != 'H' && it != 'L' }) return null
        val fall = (1 until pattern.length).firstOrNull { pattern[it - 1] == 'H' && pattern[it] == 'L' }
        return fall ?: 0
    }

    private fun JsonElement.string(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun invalid(reason: String): Nothing = throw IllegalArgumentException(reason)
}
