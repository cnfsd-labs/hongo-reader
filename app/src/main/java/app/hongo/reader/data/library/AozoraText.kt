package app.hongo.reader.data.library

/** A book read from an Aozora Bunko text file, ready to be written as an EPUB. */
data class AozoraBook(
    val title: String,
    val author: String?,
    val chapters: List<AozoraChapter>,
)

/** A part of the book that starts on a new page: at a heading or a page break. */
data class AozoraChapter(val title: String?, val blocks: List<AozoraBlock>)

sealed interface AozoraBlock {
    data class Paragraph(val inlines: List<AozoraInline>) : AozoraBlock

    /** [level] 1 for 大見出し, 2 for 中見出し, 3 for 小見出し. */
    data class Heading(val level: Int, val inlines: List<AozoraInline>) : AozoraBlock

    /** An empty line, which Aozora texts use for spacing. */
    data object Blank : AozoraBlock
}

sealed interface AozoraInline {
    data class Text(val text: String) : AozoraInline

    /** Furigana: [reading] over [base]. */
    data class Ruby(val base: String, val reading: String) : AozoraInline

    /** Emphasis dots (傍点) beside [text]. */
    data class Emphasis(val text: String) : AozoraInline
}

/**
 * Reads Aozora Bunko's plain-text format: furigana (漢字《かんじ》, ｜で始まる《よみ》), headings, page
 * breaks and emphasis dots. Other editor notes (［＃…］), the notation guide and indentation marks
 * are dropped; characters given as Unicode in a note (※［＃「…」、U+29E3D、…］) are restored.
 */
object AozoraText {
    /**
     * Whether [text] uses Aozora Bunko's notation: an Aozora file (its notation guide or its
     * colophon), or any text with Aozora-style furigana (漢字《かんじ》), which many other
     * Japanese text files use too.
     */
    fun isAozora(text: String): Boolean =
        hasAozoraHeader(text) || RUBY_MARKUP.findAll(text).take(MIN_RUBY_MARKS).count() >= MIN_RUBY_MARKS

    /** An Aozora Bunko file proper starts with title and author lines. */
    private fun hasAozoraHeader(text: String) =
        NOTATION_GUIDE in text || ('《' in text && '》' in text && COLOPHON in text)

    fun parse(text: String, fallbackTitle: String): AozoraBook {
        val lines = text.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val dashes = lines.indices.filter { DASHES.matches(lines[it]) }.take(2)
        // Other texts with Aozora furigana have no title block: everything is book text.
        val headerEnd = if (hasAozoraHeader(text)) {
            dashes.firstOrNull() ?: lines.indexOfFirst { it.isBlank() }.takeIf { it >= 0 } ?: 0
        } else {
            0
        }
        val header = lines.subList(0, headerEnd).map { it.trim() }.filter { it.isNotEmpty() }
        val bodyStart = if (headerEnd > 0 && dashes.size == 2) dashes[1] + 1 else headerEnd

        val chapters = mutableListOf<AozoraChapter>()
        var title: String? = null
        var blocks = mutableListOf<AozoraBlock>()
        fun finishChapter() {
            // Drop blank lines around the chapter; they only separated it from the one before.
            val trimmed = blocks.dropWhile { it == AozoraBlock.Blank }.dropLastWhile { it == AozoraBlock.Blank }
            if (trimmed.isNotEmpty()) chapters += AozoraChapter(title, trimmed)
            title = null
            blocks = mutableListOf()
        }

        for (line in lines.subList(bodyStart.coerceAtMost(lines.size), lines.size)) {
            if (PAGE_BREAK.matches(line.trim())) {
                finishChapter()
                continue
            }
            // A line holding only an editor's note (such as an indentation mark) is dropped.
            val block = parseLine(line) ?: continue
            if (block is AozoraBlock.Heading && block.level <= 2) {
                finishChapter()
                title = block.inlines.plainText()
            }
            blocks += block
        }
        finishChapter()

        return AozoraBook(
            title = header.firstOrNull() ?: fallbackTitle,
            author = author(header),
            chapters = chapters,
        )
    }

    /** The author line of the header: title, (subtitle), author, (translator). */
    private fun author(header: List<String>): String? {
        val rest = header.drop(1)
        if (rest.isEmpty()) return null
        if (rest.size == 1) return rest[0]
        val names = rest.filterNot { it.endsWith("訳") || it.endsWith("編") || it.endsWith("校訂") }
        return names.lastOrNull() ?: rest[0]
    }

    private fun parseLine(raw: String): AozoraBlock? {
        if (raw.isBlank()) return AozoraBlock.Blank
        var line = raw.replace(GAIJI) { match ->
            UNICODE.find(match.value)?.let { String(Character.toChars(it.groupValues[1].toInt(16))) } ?: "※"
        }

        var level = 0
        BLOCK_HEADING.find(line)?.let { match ->
            level = headingLevel(match.groupValues[1])
            line = line.replace(BLOCK_HEADING_MARK, "")
        }
        val inlines = mutableListOf<AozoraInline>()
        val text = StringBuilder()
        var rubyStart = -1
        var index = 0

        fun flushText(end: Int = text.length) {
            if (end > 0) inlines += AozoraInline.Text(text.substring(0, end))
            text.delete(0, end)
        }

        while (index < line.length) {
            val c = line[index]
            when {
                c == '｜' -> {
                    flushText()
                    rubyStart = 0
                    index++
                }
                c == '《' && line.indexOf('》', index) > index -> {
                    val close = line.indexOf('》', index)
                    val reading = line.substring(index + 1, close)
                    val start = if (rubyStart >= 0) rubyStart else kanjiRunStart(text)
                    if (start < text.length && reading.isNotBlank()) {
                        val base = text.substring(start)
                        flushText(start)
                        text.clear()
                        inlines += AozoraInline.Ruby(base, reading)
                    } else {
                        text.append(line, index, close + 1)
                    }
                    rubyStart = -1
                    index = close + 1
                }
                c == '［' && line.startsWith("［＃", index) && line.indexOf('］', index) > index -> {
                    val close = line.indexOf('］', index)
                    val note = line.substring(index, close + 1)
                    HEADING.find(note)?.let { level = headingLevel(it.groupValues[2]) }
                    EMPHASIS.find(note)?.let { emphasise(inlines, text, it.groupValues[1]) }
                    index = close + 1
                }
                else -> {
                    text.append(c)
                    index++
                }
            }
        }
        flushText()
        val merged = mergeText(inlines)
        if (merged.isEmpty()) return null
        // Indentation is done with full-width spaces; a heading doesn't need them.
        return if (level > 0) AozoraBlock.Heading(level, trimStart(merged)) else AozoraBlock.Paragraph(merged)
    }

    /** Marks [target], the text just before the note, with emphasis dots. */
    private fun emphasise(inlines: MutableList<AozoraInline>, text: StringBuilder, target: String) {
        if (target.isEmpty() || !text.endsWith(target)) return
        val start = text.length - target.length
        if (start > 0) inlines += AozoraInline.Text(text.substring(0, start))
        text.clear()
        inlines += AozoraInline.Emphasis(target)
    }

    /** Where the run of kanji at the end of [text] starts (its length if there is none). */
    private fun kanjiRunStart(text: CharSequence): Int {
        var start = text.length
        while (start > 0) {
            val c = text[start - 1]
            val isKanji = c in '\u3400'..'\u9FFF' || c in '\uF900'..'\uFAFF' || c in "々〆ヶ〇" ||
                Character.isSurrogate(c)
            if (!isKanji) break
            start--
        }
        return start
    }

    private fun headingLevel(size: String) = when (size) {
        "大" -> 1
        "中" -> 2
        else -> 3
    }

    private fun mergeText(inlines: List<AozoraInline>): List<AozoraInline> {
        val merged = mutableListOf<AozoraInline>()
        for (inline in inlines) {
            val last = merged.lastOrNull()
            if (inline is AozoraInline.Text && last is AozoraInline.Text) {
                merged[merged.size - 1] = AozoraInline.Text(last.text + inline.text)
            } else if (inline !is AozoraInline.Text || inline.text.isNotEmpty()) {
                merged += inline
            }
        }
        return merged
    }

    private fun trimStart(inlines: List<AozoraInline>): List<AozoraInline> {
        val first = inlines.firstOrNull() as? AozoraInline.Text ?: return inlines
        val trimmed = first.text.trimStart(' ', '　')
        return if (trimmed.isEmpty()) inlines.drop(1) else listOf(AozoraInline.Text(trimmed)) + inlines.drop(1)
    }

    private fun List<AozoraInline>.plainText(): String = joinToString("") {
        when (it) {
            is AozoraInline.Text -> it.text
            is AozoraInline.Ruby -> it.base
            is AozoraInline.Emphasis -> it.text
        }
    }.trim()

    private const val NOTATION_GUIDE = "【テキスト中に現れる記号について】"
    private const val MIN_RUBY_MARKS = 3
    private val RUBY_MARKUP = Regex("(｜[^《》\\n]+|[\\u3400-\\u9FFF\\uF900-\\uFAFF々〆ヶ])《[\\u3041-\\u30FF・ー]+》")
    private const val COLOPHON = "底本："
    private val DASHES = Regex("^-{20,}\\s*$")
    private val PAGE_BREAK = Regex("^［＃改(ページ|丁|段|見開き)］$")
    private val GAIJI = Regex("※［＃[^］]*］")
    private val UNICODE = Regex("U\\+([0-9A-Fa-f]{4,6})")
    private val HEADING = Regex("^［＃「(.+)」は(大|中|小)見出し］$")
    private val BLOCK_HEADING = Regex("［＃(大|中|小)見出し］")
    private val BLOCK_HEADING_MARK = Regex("［＃(大|中|小)見出し(終わり)?］")
    private val EMPHASIS = Regex("^［＃「(.+)」に傍点］$")
}
