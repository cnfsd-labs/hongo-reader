package app.hongo.reader.lookup

/**
 * Romaji for searching: kana written in Hepburn romaji, and a loose form in which the usual ways
 * of typing the same word meet (Hepburn or kunrei, long vowels written or not, は as wa, ん as
 * n or nn), so "toukyou", "tokyo" and "tôkyô" all find とうきょう.
 */
object Romaji {
    /** [text] with kana in romaji; other characters are kept. */
    fun fromKana(text: String): String {
        val kana = JapaneseText.toHiragana(text)
        val out = StringBuilder()
        var doubleNext = false
        var i = 0
        while (i < kana.length) {
            val c = kana[i]
            val next = kana.getOrNull(i + 1)
            var romaji = BASE[c]
            when {
                c == 'っ' -> {
                    doubleNext = true
                    i++
                    continue
                }
                c == 'ー' -> romaji = "-"
                romaji != null && next != null && next in SMALL_Y && romaji.endsWith("i") && romaji.length > 1 -> {
                    // きゃ kya, しゃ sha, ちゃ cha, じゃ ja
                    val stem = romaji.dropLast(1)
                    val glide = if (stem.endsWith("sh") || stem.endsWith("ch") || stem == "j") "" else "y"
                    romaji = stem + glide + SMALL_Y.getValue(next)
                    i++
                }
                romaji != null && next != null && next in SMALL_VOWEL && romaji.length > 1 -> {
                    // ふぁ fa, てぃ ti, ちぇ che, ゔぁ va
                    romaji = romaji.dropLast(1) + SMALL_VOWEL.getValue(next)
                    i++
                }
                romaji == "u" && next != null && next in SMALL_VOWEL -> {
                    romaji = "w" + SMALL_VOWEL.getValue(next) // うぃ wi
                    i++
                }
                c == 'ん' && next != null && (BASE[next]?.first()?.let { it in "aiueoy" } == true) -> romaji = "n'"
            }
            val piece = romaji ?: c.lowercaseChar().toString()
            if (doubleNext && piece.first().isLetter() && piece.first() !in "aiueon") {
                out.append(if (piece.startsWith("ch")) 't' else piece.first())
            }
            doubleNext = false
            out.append(piece)
            i++
        }
        return out.toString()
    }

    /** True if [query] is typed in Latin letters, so a romaji search makes sense. */
    fun isRomaji(query: String): Boolean =
        query.any { it in 'a'..'z' || it in 'A'..'Z' } && query.all { it.code < 0x250 || it.isWhitespace() }

    /** The loose form of romaji [text], for comparing: apply it to both sides. */
    fun loose(text: String): String {
        var s = buildString {
            for (c in text.lowercase()) {
                val plain = MACRONS[c] ?: c
                if (plain in 'a'..'z' || plain in '0'..'9') append(plain)
            }
        }
        for ((from, to) in LOOSE_RULES) s = s.replace(from, to)
        return s
    }

    private val BASE: Map<Char, String> = buildMap {
        fun row(kana: String, vararg romaji: String) = kana.forEachIndexed { i, c -> put(c, romaji[i]) }
        row("あいうえお", "a", "i", "u", "e", "o")
        row("ぁぃぅぇぉ", "a", "i", "u", "e", "o")
        row("かきくけこ", "ka", "ki", "ku", "ke", "ko")
        row("がぎぐげご", "ga", "gi", "gu", "ge", "go")
        row("さしすせそ", "sa", "shi", "su", "se", "so")
        row("ざじずぜぞ", "za", "ji", "zu", "ze", "zo")
        row("たちつてと", "ta", "chi", "tsu", "te", "to")
        row("だぢづでど", "da", "ji", "zu", "de", "do")
        row("なにぬねの", "na", "ni", "nu", "ne", "no")
        row("はひふへほ", "ha", "hi", "fu", "he", "ho")
        row("ばびぶべぼ", "ba", "bi", "bu", "be", "bo")
        row("ぱぴぷぺぽ", "pa", "pi", "pu", "pe", "po")
        row("まみむめも", "ma", "mi", "mu", "me", "mo")
        row("やゆよ", "ya", "yu", "yo")
        row("ゃゅょ", "ya", "yu", "yo")
        row("らりるれろ", "ra", "ri", "ru", "re", "ro")
        row("わゐゑをんゎゔ", "wa", "i", "e", "o", "n", "wa", "vu")
    }

    private val SMALL_Y = mapOf('ゃ' to "a", 'ゅ' to "u", 'ょ' to "o")
    private val SMALL_VOWEL = mapOf('ぁ' to "a", 'ぃ' to "i", 'ぅ' to "u", 'ぇ' to "e", 'ぉ' to "o")

    private val MACRONS = mapOf(
        'ā' to 'a', 'ī' to 'i', 'ū' to 'u', 'ē' to 'e', 'ō' to 'o',
        'â' to 'a', 'î' to 'i', 'û' to 'u', 'ê' to 'e', 'ô' to 'o',
    )

    /** Applied in this order; the same order on both sides keeps them comparable. */
    private val LOOSE_RULES = listOf(
        Regex("tch") to "cch",
        Regex("sy") to "sh",
        Regex("ty") to "ch",
        Regex("[zj]y") to "j",
        Regex("si") to "shi",
        Regex("ti") to "chi",
        Regex("tu") to "tsu",
        Regex("(?<![cs])hu") to "fu",
        Regex("[zd]i") to "ji",
        Regex("du") to "zu",
        Regex("m(?=[bp])") to "n",
        Regex("nn") to "n",
        // Particles and long vowels, written in many ways.
        Regex("ha") to "wa",
        Regex("wo") to "o",
        Regex("he") to "e",
        Regex("ou") to "o",
        Regex("([aiueo])\\1") to "$1",
    )
}
