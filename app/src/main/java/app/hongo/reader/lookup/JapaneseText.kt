package app.hongo.reader.lookup

/** Small helpers for Japanese text. */
object JapaneseText {
    private const val KATAKANA_FIRST = 'ァ'
    private const val KATAKANA_LAST = 'ヶ'
    private const val KATAKANA_TO_HIRAGANA = 'ァ' - 'ぁ'

    /** Katakana converted to hiragana; long-vowel marks and other characters are kept. */
    fun toHiragana(text: String): String = buildString(text.length) {
        for (c in text) append(if (c in KATAKANA_FIRST..KATAKANA_LAST) c - KATAKANA_TO_HIRAGANA else c)
    }

    fun containsKatakana(text: String): Boolean = text.any { it in KATAKANA_FIRST..KATAKANA_LAST }

    /** Whether [codePoint] is a kanji (a Han character, but not the repeat mark 々). */
    fun isKanji(codePoint: Int): Boolean =
        Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN && codePoint != '々'.code

    /** The distinct kanji of [text], in order of appearance. */
    fun kanjiIn(text: String): List<String> =
        text.codePoints().toArray().filter(::isKanji).distinct().map { String(Character.toChars(it)) }
}
