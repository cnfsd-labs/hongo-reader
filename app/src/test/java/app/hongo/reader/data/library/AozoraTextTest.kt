package app.hongo.reader.data.library

import app.hongo.reader.data.library.AozoraBlock.Blank
import app.hongo.reader.data.library.AozoraBlock.Heading
import app.hongo.reader.data.library.AozoraBlock.Paragraph
import app.hongo.reader.data.library.AozoraInline.Emphasis
import app.hongo.reader.data.library.AozoraInline.Ruby
import app.hongo.reader.data.library.AozoraInline.Text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AozoraTextTest {
    private val dashes = "-".repeat(55)

    private fun aozora(body: String, header: String = "吾輩は猫である\n夏目漱石") =
        "$header\n\n$dashes\n【テキスト中に現れる記号について】\n\n《》：ルビ\n（例）吾輩《わがはい》\n$dashes\n\n$body\n\n" +
            "底本：「夏目漱石全集1」ちくま文庫、筑摩書房\n"

    private fun paragraphs(text: String) = AozoraText.parse(text, "fallback").chapters.flatMap { it.blocks }

    @Test
    fun recognisesAozoraTexts() {
        assertTrue(AozoraText.isAozora(aozora("本文")))
        assertTrue(AozoraText.isAozora("吾輩《わがはい》は猫である。\n\n底本：「全集」\n"))
        assertFalse(AozoraText.isAozora("吾輩は猫である。名前はまだ無い。"))
    }

    @Test
    fun otherTextsWithAozoraFuriganaCountToo() {
        val text = "　村瀬《むらせ》のおいしい話に乗った。\nその村瀬｜勝也《かつや》は、小峰《こみね》に背を向けた。\n"
        assertTrue(AozoraText.isAozora(text))
        // One reading in brackets is not enough to tell.
        assertFalse(AozoraText.isAozora("彼は「本《ほん》」と言った。"))

        val book = AozoraText.parse(text, "ひかり町")
        assertEquals("ひかり町", book.title)
        assertNull(book.author)
        // No title block: the first line is book text.
        assertEquals(
            Paragraph(listOf(Text("　"), Ruby("村瀬", "むらせ"), Text("のおいしい話に乗った。"))),
            book.chapters.single().blocks.first(),
        )
        assertEquals(2, book.chapters.single().blocks.size)
    }

    @Test
    fun readsTitleAndAuthorFromTheHeader() {
        val book = AozoraText.parse(aozora("本文"), "fallback")
        assertEquals("吾輩は猫である", book.title)
        assertEquals("夏目漱石", book.author)

        val translated = AozoraText.parse(aozora("本文", header = "変身\nフランツ・カフカ\n原田義人訳"), "fallback")
        assertEquals("フランツ・カフカ", translated.author)
    }

    @Test
    fun skipsTheNotationGuide() {
        val text = paragraphs(aozora("　本文。")).filterIsInstance<Paragraph>().first()
        assertEquals(Paragraph(listOf(Text("　本文。"))), text)
    }

    @Test
    fun rubyCoversTheKanjiBeforeIt() {
        assertEquals(
            Paragraph(listOf(Text("　"), Ruby("吾輩", "わがはい"), Text("は猫である。"))),
            paragraphs(aozora("　吾輩《わがはい》は猫である。")).first(),
        )
    }

    @Test
    fun aBarMarksWhereTheRubyBaseStarts() {
        assertEquals(
            Paragraph(listOf(Text("一番"), Ruby("獰悪", "どうあく"), Text("な種族"))),
            paragraphs(aozora("一番｜獰悪《どうあく》な種族")).first(),
        )
        assertEquals(
            Paragraph(listOf(Text("その上"), Ruby("今", "いま"), Text("まで"))),
            paragraphs(aozora("その上｜今《いま》まで")).first(),
        )
    }

    @Test
    fun rubyOverKanaNeedsABar_otherwiseTheBracketsStay() {
        assertEquals(Paragraph(listOf(Text("ねこ《ネコ》"))), paragraphs(aozora("ねこ《ネコ》")).first())
    }

    @Test
    fun headingsStartChaptersAndNameThem() {
        val book = AozoraText.parse(aozora("［＃８字下げ］一［＃「一」は中見出し］\n　本文一。\n［＃８字下げ］二［＃「二」は中見出し］\n　本文二。"), "f")
        assertEquals(listOf("一", "二"), book.chapters.map { it.title })
        assertEquals(Heading(2, listOf(Text("一"))), book.chapters[0].blocks[0])
        assertEquals(Paragraph(listOf(Text("　本文二。"))), book.chapters[1].blocks[1])
    }

    @Test
    fun pageBreaksStartChapters() {
        val book = AozoraText.parse(aozora("前。\n［＃改ページ］\n後。"), "f")
        assertEquals(2, book.chapters.size)
        assertNull(book.chapters[1].title)
    }

    @Test
    fun emphasisDotsMarkTheTextBeforeTheNote() {
        assertEquals(
            Paragraph(listOf(Text("その"), Emphasis("へっつい"), Text("の中"))),
            paragraphs(aozora("そのへっつい［＃「へっつい」に傍点］の中")).first(),
        )
    }

    @Test
    fun dropsEditorNotesAndLinesOfOnlyNotes() {
        val blocks = paragraphs(aozora("［＃ここから２字下げ］\n本文［＃「本文」は底本では「本分」］。\n［＃ここで字下げ終わり］"))
        assertEquals(Paragraph(listOf(Text("本文。"))), blocks.first())
    }

    @Test
    fun restoresCharactersGivenAsUnicode() {
        assertEquals(
            Paragraph(listOf(Text("𩸽を食う"))),
            paragraphs(aozora("※［＃「魚＋花」、U+29E3D、1-2-3］を食う")).first(),
        )
        assertEquals(
            Paragraph(listOf(Text("※を食う"))),
            paragraphs(aozora("※［＃「言＋墟のつくり」、第4水準2-88-74］を食う")).first(),
        )
    }

    @Test
    fun keepsBlankLinesInsideAChapter() {
        assertEquals(
            listOf(Paragraph(listOf(Text("前。"))), Blank, Paragraph(listOf(Text("後。")))),
            paragraphs(aozora("前。\n\n後。")).take(3),
        )
    }
}
