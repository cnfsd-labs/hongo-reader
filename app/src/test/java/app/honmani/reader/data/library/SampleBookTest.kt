package app.honmani.reader.data.library

import java.io.File
import java.util.zip.ZipFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SampleBookTest {
    private val dir = File("src/main/assets/samples")

    @Test
    fun everySampleIsInTheAssetsAndEveryAssetIsOffered() {
        assertEquals(SampleBook.entries.map { it.fileName }.toSet(), dir.list().orEmpty().toSet())
    }

    @Test
    fun everySampleKeepsAozoraBunkosCredits() {
        for (sample in SampleBook.entries) {
            val text = ZipFile(File(dir, sample.fileName)).use { zip ->
                zip.entries().asSequence().filter { it.name.endsWith(".xhtml") }
                    .joinToString("") { zip.getInputStream(it).readBytes().toString(Charsets.UTF_8) }
            }
            // The source edition and the volunteers who typed and proofread it, as Aozora asks.
            for (credit in listOf("底本：", "入力：", "校正：", "青空文庫")) {
                assertTrue("${sample.fileName} lacks $credit", credit in text)
            }
            assertTrue("${sample.fileName} has an unknown character", "〓" !in text)
            assertTrue("${sample.fileName} is not ${sample.title}", "<dc:title>${sample.title}</dc:title>" in
                ZipFile(File(dir, sample.fileName)).use { it.getInputStream(it.getEntry("OEBPS/content.opf")).readBytes().toString(Charsets.UTF_8) })
        }
    }
}
