package app.honmani.reader.data.dictionary

import app.honmani.reader.data.dictionary.yomitan.CountingInputStream
import app.honmani.reader.data.dictionary.yomitan.TermMetaKind
import app.honmani.reader.data.dictionary.yomitan.YomitanTermMeta
import java.io.File

/**
 * Kanjium's pitch accent list (`accents.txt`, CC BY-SA 4.0, by Uros O.): one word per line,
 * `word<TAB>reading<TAB>positions`. The reading is empty for words written in kana. Positions
 * are downstep morae separated by commas, sometimes with a part of speech: `0,2` or
 * `(副)0,(名)3`.
 */
object KanjiumAccents {
    const val TITLE = "Kanjium pitch accents"
    const val ATTRIBUTION = "The pitch accent notation, verb particle data, phonetics, homonyms and other " +
        "additions or modifications to EDICT, KANJIDIC or KRADFILE were provided by Uros O. through his " +
        "free database (Kanjium, CC BY-SA 4.0)."

    /** Whether [file] looks like the list rather than a zip (zips start with "PK"). */
    fun looksLike(file: File): Boolean {
        val head = ByteArray(2)
        val read = file.inputStream().use { it.read(head) }
        return read == 2 && !(head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte())
    }

    /** Reads [file] line by line, handing rows to [action] in lists of up to [chunkSize]. */
    suspend fun forEachChunk(
        file: File,
        chunkSize: Int,
        onRead: suspend (bytes: Long) -> Unit = {},
        action: suspend (List<YomitanTermMeta>) -> Unit,
    ) {
        val chunk = ArrayList<YomitanTermMeta>(chunkSize)
        val counting = CountingInputStream(file.inputStream())
        counting.bufferedReader(Charsets.UTF_8).use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                parseLine(line)?.let { chunk += it }
                if (chunk.size >= chunkSize) {
                    action(chunk.toList())
                    chunk.clear()
                    onRead(counting.count)
                }
            }
            if (chunk.isNotEmpty()) action(chunk.toList())
            onRead(counting.count)
        }
    }

    internal fun parseLine(line: String): YomitanTermMeta? {
        val fields = line.removePrefix("\uFEFF").split('\t')
        if (fields.size < 3) return null
        val expression = fields[0].trim().ifEmpty { return null }
        val reading = fields[1].trim().ifEmpty { expression }
        val pitches = fields[2].split(',')
            .mapNotNull { it.substringAfterLast(')').trim().toIntOrNull() }
            .filter { it >= 0 }
            .distinct()
        if (pitches.isEmpty()) return null
        return YomitanTermMeta(expression, reading, TermMetaKind.PITCH, pitches = pitches)
    }
}
