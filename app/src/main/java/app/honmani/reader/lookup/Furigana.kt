package app.honmani.reader.lookup

/** Furigana in the looked-up text: [reading] is given for the characters [start] until [end]. */
data class RubySpan(val start: Int, val end: Int, val reading: String)

/** Part of a written form, with the [ruby] to show above it (null for kana, which need none). */
data class RubySegment(val text: String, val ruby: String? = null)

/** Uses the book's furigana to tell which reading of a written form the author meant. */
object Furigana {
    /**
     * [expression] split into kana and the rest, with each non-kana run's part of [reading]
     * as ruby: 取り扱う read とりあつかう gives 取(と) り 扱(あつか) う. If the kana of the expression
     * can't be found in the reading, the whole reading goes over the whole expression.
     */
    fun segments(expression: String, reading: String): List<RubySegment> {
        val runs = mutableListOf<String>()
        for (c in expression) {
            if (runs.isNotEmpty() && isKana(runs.last().last()) == isKana(c)) {
                runs[runs.size - 1] = runs.last() + c
            } else {
                runs += c.toString()
            }
        }
        if (runs.all { isKana(it.first()) } || reading.isEmpty()) return listOf(RubySegment(expression))
        val pattern = runs.joinToString("", "^", "$") {
            if (isKana(it.first())) Regex.escape(JapaneseText.toHiragana(it)) else "(.+?)"
        }
        // toHiragana keeps the length, so match indexes are also indexes into the reading.
        val match = Regex(pattern).find(JapaneseText.toHiragana(reading))
            ?: return listOf(RubySegment(expression, reading))
        var group = 1
        return runs.map { run ->
            if (isKana(run.first())) {
                RubySegment(run)
            } else {
                val range = match.groups[group++]!!.range
                RubySegment(run, reading.substring(range.first, range.last + 1))
            }
        }
    }

    private fun isKana(c: Char) = c in '\u3041'..'\u309F' || c in '\u30A0'..'\u30FF'

    /**
     * The reading of [source] up to the end of its last ruby base, with each base replaced by its
     * furigana, in hiragana; null if [source] holds no whole ruby base. For 食べました with た over
     * 食 that is "た". [ruby] must be sorted and non-overlapping, with indexes into the text that
     * [source] is a prefix of.
     */
    fun readingPrefix(source: String, ruby: List<RubySpan>): String? {
        val covered = ruby.filter { it.end <= source.length }
        if (covered.isEmpty()) return null
        val reading = buildString {
            var index = 0
            for (span in covered) {
                append(source, index, span.start)
                append(span.reading)
                index = span.end
            }
        }
        return JapaneseText.toHiragana(reading)
    }

    /**
     * Whether an entry read [reading] agrees with [readingPrefix]. Inflection only changes the
     * end of a word, after the last ruby base, so comparing the start is enough.
     */
    fun agrees(readingPrefix: String, reading: String): Boolean =
        JapaneseText.toHiragana(reading).startsWith(readingPrefix)
}
