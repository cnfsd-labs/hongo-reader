package app.hongo.reader.data.dictionary

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Writes a zip with [files] (path to UTF-8 content) into [dir] and returns it. */
fun writeZip(dir: File, files: Map<String, String>, name: String = "dictionary.zip"): File {
    val zip = File(dir, name)
    ZipOutputStream(zip.outputStream()).use { out ->
        for ((path, content) in files) {
            out.putNextEntry(ZipEntry(path))
            out.write(content.toByteArray())
            out.closeEntry()
        }
    }
    return zip
}

fun index(title: String = "Test Dictionary", format: Int = 3, extra: String = "") =
    """{"title":"$title","revision":"2026.10","format":$format,"author":"HonGO!"$extra}"""

/** A small format 3 dictionary: two term banks (4 terms) and a tag bank. */
fun sampleDictionary(title: String = "Test Dictionary") = mapOf(
    "index.json" to index(title),
    "term_bank_1.json" to """[
        ["猫","ねこ","n","",100,["cat"],1,"P"],
        ["食べる","たべる","v1","v1",50,["to eat"],2,""]
    ]""",
    "term_bank_2.json" to """[
        ["ねこ","","n","",10,[{"type":"structured-content","content":"cat (kana)"}],3,""],
        ["読む","よむ",null,"v5",0,["to read"],4,"P"]
    ]""",
    "tag_bank_1.json" to """[["n","partOfSpeech",-3,"noun",0],["P","popular",-10,"popular term",10]]""",
)
