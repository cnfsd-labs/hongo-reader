package app.hongo.reader.data.opds

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.hongo.reader.data.HongoDatabase
import app.hongo.reader.data.library.BookImporter
import app.hongo.reader.data.library.BookSource
import app.hongo.reader.data.library.RemovedBookEntity
import app.hongo.reader.data.library.TestBooks
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Automatic imports against a small Calibre-Web-like server. */
@RunWith(RobolectricTestRunner::class)
class OpdsAutoImportTest {
    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var database: HongoDatabase
    private lateinit var repository: OpdsRepository
    private lateinit var autoImport: OpdsAutoImport
    private lateinit var server: HeaderServer

    /** Entries of the "Recently added" feed, newest first, per page. */
    private var pages = listOf<List<String>>()
    private val requests = mutableListOf<String>()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            HongoDatabase::class.java,
        ).allowMainThreadQueries().build()
        val client = OpdsClient(connectTimeoutMs = 2_000, readTimeoutMs = 2_000)
        val plain = object : CredentialCipher {
            override fun encrypt(plain: String) = plain

            override fun decrypt(encrypted: String) = encrypted
        }
        repository = OpdsRepository(database.opdsCatalogDao(), plain, client)
        autoImport = OpdsAutoImport(
            repository = repository,
            client = client,
            importer = BookImporter(database.bookDao(), File(folder.root, "books"), File(folder.root, "covers")),
            bookDao = database.bookDao(),
            downloadsDir = File(folder.root, "downloads"),
            scope = CoroutineScope(Job()),
        )
        server = HeaderServer { path, out ->
            synchronized(requests) { requests += path }
            when {
                path == "/opds" -> out.respond("200 OK", ROOT, "Content-Type: application/atom+xml")
                path.startsWith("/opds/new") -> {
                    val page = path.substringAfter("page=", "1").toInt()
                    out.respond("200 OK", newFeed(page), "Content-Type: application/atom+xml")
                }
                path.startsWith("/download/") -> {
                    val name = path.substringAfter("/download/")
                    val bytes = if (name.endsWith(".txt")) "テキストの本。".toByteArray() else TestBooks.epub(title = name)
                    out.head("200 OK", "Content-Length: ${bytes.size}")
                    out.write(bytes)
                }
                else -> out.respond("404 Not Found", "")
            }
        }
    }

    @After
    fun tearDown() {
        server.stop()
        database.close()
    }

    private fun newFeed(page: Int): String {
        val entries = pages.getOrElse(page - 1) { emptyList() }.joinToString("")
        val next = if (page < pages.size) """<link rel="next" href="/opds/new?page=${page + 1}" type="$NAV"/>""" else ""
        return """<feed xmlns="http://www.w3.org/2005/Atom"><title>Recently added</title>$next$entries</feed>"""
    }

    private suspend fun addCatalog(autoImport: Boolean = true): Long =
        repository.save(null, "Calibre", "http://127.0.0.1:${server.port}/opds", null, null, autoImport)

    private suspend fun libraryIds() = database.bookDao().sourceIds(BookSource.OPDS).toSet()

    @Test
    fun addsJapaneseEpubAndTextBooks_fromAllPages() = runBlocking {
        pages = listOf(
            listOf(book("a", "jpn"), book("b", "eng"), book("c", "jpn", format = "pdf"), book("d", "jpn", format = "txt")),
            listOf(book("e", "ja")),
        )
        val id = addCatalog()

        assertEquals(3, autoImport.run())

        assertEquals(setOf("urn:a", "urn:d", "urn:e"), libraryIds())
        assertTrue(database.opdsCatalogDao().find(id)!!.autoImportComplete)
        assertNull(autoImport.status.value)
    }

    @Test
    fun laterRunsAddOnlyNewBooks_andSkipRemovedOnes() = runBlocking {
        pages = listOf(listOf(book("a", "jpn"), book("b", "jpn")), listOf(book("c", "jpn")))
        addCatalog()
        autoImport.run()
        // The reader removes b; a new book f arrives at the top.
        val b = database.bookDao().observeAll().first().single { it.sourceId == "urn:b" }
        database.bookDao().delete(b.id)
        database.bookDao().insertRemoved(RemovedBookEntity(BookSource.OPDS, "urn:b", 1L))
        pages = listOf(listOf(book("f", "jpn"), book("a", "jpn"), book("b", "jpn")), listOf(book("c", "jpn")))
        synchronized(requests) { requests.clear() }

        assertEquals(1, autoImport.run())

        assertEquals(setOf("urn:a", "urn:c", "urn:f"), libraryIds())
        // Page 1 brought f, so page 2 was read; it brought nothing, so the run stopped there.
        assertEquals(1, requests.count { it.startsWith("/opds/new?page=2") })
    }

    @Test
    fun afterACompleteRun_aPageWithoutNewBooksEndsTheRun() = runBlocking {
        pages = listOf(listOf(book("a", "jpn")), listOf(book("b", "jpn")))
        addCatalog()
        autoImport.run()
        synchronized(requests) { requests.clear() }

        assertEquals(0, autoImport.run())

        assertFalse(requests.any { it.startsWith("/opds/new?page=2") })
    }

    @Test
    fun cataloguesWithAutomaticImportOffAreLeftAlone() = runBlocking {
        pages = listOf(listOf(book("a", "jpn")))
        addCatalog(autoImport = false)

        assertEquals(0, autoImport.run())

        assertTrue(libraryIds().isEmpty())
        assertTrue(requests.isEmpty())
    }

    @Test
    fun findsTheNewestBooksFeed() {
        val withRel = OpdsFeed(
            title = "",
            entries = listOf(OpdsEntry("x", "Recently added", feedUrl = "https://h/opds/new")),
            newestUrl = "https://h/opds/latest",
        )
        assertEquals("https://h/opds/latest", OpdsAutoImport.newestBooksUrl(withRel, "https://h/opds"))
        assertEquals(
            "https://h/opds/new",
            OpdsAutoImport.newestBooksUrl(withRel.copy(newestUrl = null), "https://h/opds"),
        )
        val flat = OpdsFeed("", listOf(OpdsEntry("b", "本", acquisitions = listOf(OpdsAcquisition("https://h/b.epub", EPUB_TYPE)))))
        assertEquals("https://h/opds", OpdsAutoImport.newestBooksUrl(flat, "https://h/opds"))
        assertNull(OpdsAutoImport.newestBooksUrl(OpdsFeed("", emptyList()), "https://h/opds"))
    }

    private fun book(name: String, language: String, format: String = "epub"): String {
        val type = when (format) {
            "epub" -> EPUB_TYPE
            "txt" -> TEXT_TYPE
            else -> "application/pdf"
        }
        return """<entry><title>本$name</title><id>urn:$name</id>""" +
            """<dcterms:language xmlns:dcterms="http://purl.org/dc/terms/">$language</dcterms:language>""" +
            """<link rel="http://opds-spec.org/acquisition" href="/download/$name.$format" type="$type"/></entry>"""
    }

    private companion object {
        const val NAV = "application/atom+xml;profile=opds-catalog"
        const val ROOT = """<feed xmlns="http://www.w3.org/2005/Atom"><title>Calibre</title>""" +
            """<entry><title>Recently added Books</title><id>/opds/new</id>""" +
            """<link href="/opds/new" type="$NAV"/></entry></feed>"""
    }
}
