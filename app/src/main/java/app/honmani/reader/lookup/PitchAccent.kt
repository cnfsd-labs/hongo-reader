package app.honmani.reader.lookup

/** One mora of a reading and its pitch, for drawing the accent over the kana. */
data class PitchMora(
    val text: String,
    val high: Boolean,
    /** The pitch falls right after this mora (the downstep, ꜜ). */
    val dropsAfter: Boolean,
)

/**
 * Tokyo pitch accent, written as the downstep position: 0 for none (平板: low, then high to the
 * end and on a following particle), 1 for a fall after the first mora (頭高), n for a fall after
 * the n-th (中高; 尾高 when n is the last mora, so a following particle is low).
 */
object PitchAccent {
    private const val SMALL_KANA = "ゃゅょぁぃぅぇぉゎャュョァィゥェォヮヵヶ"

    /** [reading] split into morae: a small kana joins the one before it; っ and ー count alone. */
    fun morae(reading: String): List<String> {
        val morae = mutableListOf<String>()
        for (char in reading) {
            if (char in SMALL_KANA && morae.isNotEmpty()) {
                morae[morae.lastIndex] = morae.last() + char
            } else {
                morae += char.toString()
            }
        }
        return morae
    }

    /** The morae of [reading] with their pitch for [downstep]; empty if it doesn't fit the word. */
    fun pattern(reading: String, downstep: Int): List<PitchMora> {
        val morae = morae(reading)
        if (morae.isEmpty() || downstep !in 0..morae.size) return emptyList()
        return morae.mapIndexed { i, mora ->
            val high = when (downstep) {
                0 -> i > 0 || morae.size == 1
                1 -> i == 0
                else -> i in 1 until downstep
            }
            PitchMora(mora, high, dropsAfter = downstep > 0 && i == downstep - 1)
        }
    }

    /**
     * [reading] as HTML for Anki, one pattern per downstep: high morae in `<span class="h">`
     * (drawn with a line above), the mora before a fall also with class `d` (a line at its end).
     */
    fun html(reading: String, downsteps: List<Int>): String =
        downsteps.map { pattern(reading, it) }.filter { it.isNotEmpty() }.joinToString("・") { morae ->
            morae.joinToString("") { mora ->
                val classes = listOfNotNull("h".takeIf { mora.high }, "d".takeIf { mora.dropsAfter })
                val text = escape(mora.text)
                if (classes.isEmpty()) text else """<span class="${classes.joinToString(" ")}">$text</span>"""
            }
        }

    private fun escape(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
