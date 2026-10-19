package app.hongo.reader.data.library

import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AozoraEpubTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val book = AozoraBook(
        title = "吾輩は猫である",
        author = "夏目漱石",
        chapters = listOf(
            AozoraChapter(
                "一",
                listOf(
                    AozoraBlock.Heading(2, listOf(AozoraInline.Text("一"))),
                    AozoraBlock.Paragraph(
                        listOf(AozoraInline.Ruby("吾輩", "わがはい"), AozoraInline.Text("は<猫>である。")),
                    ),
                    AozoraBlock.Blank,
                    AozoraBlock.Paragraph(listOf(AozoraInline.Emphasis("へっつい"))),
                ),
            ),
            AozoraChapter("二", listOf(AozoraBlock.Paragraph(listOf(AozoraInline.Text("二章。"))))),
        ),
    )

    @Test
    fun writesAnEpubTheLibraryCanRead() {
        val file = folder.newFile("neko.epub")
        AozoraEpub.write(book, "urn:sha256:abc", file)

        val metadata = EpubMetadata.read(file)
        assertEquals("吾輩は猫である", metadata.title)
        assertEquals("夏目漱石", metadata.author)

        ZipFile(file).use { zip ->
            val first = zip.entries().nextElement()
            assertEquals("mimetype", first.name)
            assertEquals(ZipEntry.STORED, first.method)
            fun text(name: String) = zip.getInputStream(zip.getEntry(name)).readBytes().toString(Charsets.UTF_8)
            val chapter = text("OEBPS/chapter-1.xhtml")
            assertTrue(chapter, "<h3>一</h3>" in chapter)
            assertTrue(chapter, "<p><ruby>吾輩<rt>わがはい</rt></ruby>は&lt;猫&gt;である。</p>" in chapter)
            assertTrue(chapter, "<em class=\"sesame\">へっつい</em>" in chapter)
            assertTrue(text("OEBPS/chapter-2.xhtml"), "二章。" in text("OEBPS/chapter-2.xhtml"))
            val nav = text("OEBPS/nav.xhtml")
            assertTrue(nav, ">一</a>" in nav && ">二</a>" in nav)
            assertTrue("page-progression-direction=\"rtl\"" in text("OEBPS/content.opf"))
        }
    }
}
