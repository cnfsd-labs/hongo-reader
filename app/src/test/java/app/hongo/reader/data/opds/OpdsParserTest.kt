package app.hongo.reader.data.opds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpdsParserTest {
    private val base = "https://books.example.com/opds"

    @Test
    fun navigationFeed_likeCalibreWeb() {
        val feed = OpdsParser.parseFeed(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <id>urn:uuid:root</id>
              <title>Calibre-Web</title>
              <link rel="search" href="/opds/osd" type="application/opensearchdescription+xml"/>
              <link rel="search" href="/opds/search/{searchTerms}" type="application/atom+xml"/>
              <entry>
                <title>Recently added Books</title>
                <link href="/opds/new" type="application/atom+xml;profile=opds-catalog"/>
                <id>/opds/new</id>
                <content type="text">The latest Books</content>
              </entry>
              <entry>
                <title>Authors</title>
                <link rel="subsection" href="authors" type="application/atom+xml;profile=opds-catalog;kind=navigation"/>
                <id>/opds/author</id>
              </entry>
            </feed>
            """.trimIndent().toByteArray(),
            base,
        )
        assertEquals("Calibre-Web", feed.title)
        assertEquals(listOf("Recently added Books", "Authors"), feed.entries.map { it.title })
        assertEquals("https://books.example.com/opds/new", feed.entries[0].feedUrl)
        assertEquals("The latest Books", feed.entries[0].summary)
        assertEquals("https://books.example.com/authors", feed.entries[1].feedUrl)
        assertEquals(OpdsSearch.Template("https://books.example.com/opds/search/{searchTerms}"), feed.search)
        assertNull(feed.nextUrl)
    }

    @Test
    fun acquisitionFeed_booksFormatsThumbnailsAndPaging() {
        val feed = OpdsParser.parseFeed(
            """
            <feed xmlns="http://www.w3.org/2005/Atom" xmlns:opds="http://opds-spec.org/2010/catalog">
              <title>Recently added Books</title>
              <link rel="next" href="/opds/new?offset=30" type="application/atom+xml;profile=opds-catalog"/>
              <entry>
                <title>吾輩は猫である</title>
                <id>urn:uuid:neko</id>
                <author><name>夏目 漱石</name></author>
                <content type="html">&lt;p&gt;猫の&lt;b&gt;話&lt;/b&gt;&lt;/p&gt;&lt;p&gt;二&amp;amp;三&lt;/p&gt;</content>
                <link rel="http://opds-spec.org/image/thumbnail" href="/opds/cover_240_240/1" type="image/jpeg"/>
                <link rel="http://opds-spec.org/image" href="/opds/cover/1" type="image/jpeg"/>
                <link rel="http://opds-spec.org/acquisition" href="/opds/download/1/pdf/" type="application/pdf"/>
                <link rel="http://opds-spec.org/acquisition" href="/opds/download/1/epub/" type="application/epub+zip" length="1000"/>
              </entry>
              <entry>
                <title>Manual</title>
                <id>urn:uuid:manual</id>
                <link rel="http://opds-spec.org/acquisition" href="/opds/download/2/pdf/" type="application/pdf"/>
              </entry>
              <entry>
                <title>銀河鉄道の夜</title>
                <id>urn:uuid:ginga</id>
                <author><name>宮沢 賢治</name></author>
                <link rel="http://opds-spec.org/acquisition/open-access" href="https://aozora.example/ginga.txt" type="text/plain; charset=utf-8"/>
              </entry>
            </feed>
            """.trimIndent().toByteArray(),
            base,
        )
        assertEquals("https://books.example.com/opds/new?offset=30", feed.nextUrl)
        val (neko, manual, ginga) = feed.entries
        assertEquals("urn:uuid:neko", neko.id)
        assertEquals(listOf("夏目 漱石"), neko.authors)
        assertEquals("猫の話 二&三", neko.summary)
        assertNull(neko.feedUrl)
        assertEquals(OpdsAcquisition("https://books.example.com/opds/download/1/epub/", EPUB_TYPE), neko.book)
        assertEquals("https://books.example.com/opds/cover_240_240/1", neko.thumbnailUrl)

        assertNull(manual.book)
        assertTrue(manual.hasOnlyOtherFormats)

        assertEquals(OpdsAcquisition("https://aozora.example/ginga.txt", TEXT_TYPE), ginga.book)
        assertFalse(ginga.hasOnlyOtherFormats)
    }

    @Test
    fun htmlIsNotAFeed() {
        val html = "<!DOCTYPE html><html><body>Login</body></html>".toByteArray()
        assertThrows { OpdsParser.parseFeed(html, base) }
        assertThrows { OpdsParser.parseFeed("<html><body/></html>".toByteArray(), base) }
    }

    @Test
    fun openSearchDescription() {
        val search = OpdsParser.parseSearchDescription(
            """
            <OpenSearchDescription xmlns="http://a9.com/-/spec/opensearch/1.1/">
              <Url type="text/html" template="/search?q={searchTerms}"/>
              <Url type="application/atom+xml" template="/opds/search?query={searchTerms}&amp;page={startPage?}"/>
            </OpenSearchDescription>
            """.trimIndent().toByteArray(),
            "https://books.example.com/opds/osd",
        )
        assertEquals(
            "https://books.example.com/opds/search?query=%E7%8C%AB%20%E6%BC%B1%E7%9F%B3&page=",
            search!!.urlFor(" 猫 漱石 "),
        )
    }

    @Test
    fun resolve_keepsTemplatesAndRejectsOtherSchemes() {
        assertEquals("https://a.example/s/{searchTerms}", OpdsParser.resolve("https://a.example/opds", "/s/{searchTerms}"))
        assertEquals("http://b.example/x", OpdsParser.resolve("https://a.example/opds", "http://b.example/x"))
        assertNull(OpdsParser.resolve("https://a.example/opds", "javascript:alert(1)"))
        assertNull(OpdsParser.resolve("https://a.example/opds", "file:///etc/passwd"))
        assertNull(OpdsParser.resolve("https://a.example/opds", ""))
    }

    private fun assertThrows(block: () -> Unit) {
        try {
            block()
        } catch (_: NotAFeedException) {
            return
        }
        throw AssertionError("Expected NotAFeedException")
    }

    @Test
    fun languagesAndTheNewestBooksLink() {
        val feed = OpdsParser.parseFeed(
            """
            <feed xmlns="http://www.w3.org/2005/Atom" xmlns:dcterms="http://purl.org/dc/terms/"
                  xmlns:dc="http://purl.org/dc/elements/1.1/">
              <title>Books</title>
              <link rel="http://opds-spec.org/sort/new" href="/opds/latest" type="application/atom+xml"/>
              <entry><title>こころ</title><id>a</id><dcterms:language>jpn</dcterms:language></entry>
              <entry><title>Kokoro</title><id>b</id><dc:language>en</dc:language></entry>
              <entry><title>羅生門</title><id>c</id></entry>
              <entry><title>坊っちゃん</title><id>d</id></entry>
            </feed>
            """.trimIndent().toByteArray(),
            base,
        )
        assertEquals("https://books.example.com/opds/latest", feed.newestUrl)
        assertEquals(listOf("jpn"), feed.entries[0].languages)
        assertEquals(listOf(true, false), feed.entries.take(2).map { it.isJapanese })
        // Without a language, kana in the title decide: kanji alone could be Chinese.
        assertEquals(listOf(false, true), feed.entries.drop(2).map { it.isJapanese })
    }
}
