package app.hongo.reader.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.hongo.reader.data.HongoDatabase
import app.hongo.reader.data.library.BookEntity
import app.hongo.reader.data.library.BookFormat
import app.hongo.reader.data.opds.CredentialCipher
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProgressSyncTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val server = FakeKoSyncServer()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var database: HongoDatabase
    private lateinit var settings: KoSyncSettings
    private lateinit var sync: ProgressSync
    private lateinit var book: BookEntity
    private lateinit var document: String

    /** What the sync wrote into ttu's bookmark. */
    private var written: Pair<Long, Double>? = null

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, HongoDatabase::class.java).allowMainThreadQueries().build()
        val books = folder.newFolder("books")
        val file = testEpub(File(books, "h.epub"))
        document = PartialMd5.of(file)
        val id = database.bookDao().insert(
            BookEntity(contentHash = "h", title = "猫", format = BookFormat.EPUB, fileName = "h.epub", addedAt = 0, ttuId = 5),
        )
        book = database.bookDao().find(id)!!
        settings = KoSyncSettings(context.getSharedPreferences("sync-test", Context.MODE_PRIVATE), PlainCipher)
        settings.signIn(KoSyncAccount(server.url, "reader", KoSyncAccount.userKey("secret")))
        sync = ProgressSync(
            settings = settings,
            client = KoSyncClient(connectTimeoutMs = 2_000, readTimeoutMs = 2_000),
            books = database.bookDao(),
            bookFile = { File(books, it.fileName) },
            scope = scope,
            device = "HonGO! (Test)",
            pushDelayMs = 50,
        )
    }

    @After
    fun tearDown() {
        scope.cancel()
        server.stop()
        database.close()
    }

    private suspend fun pull(local: Long?, savedAtMs: Long) =
        sync.pull(book, 5, bookmark = { local to savedAtMs }, setBookmark = { characters, progress -> written = characters to progress })

    @Test
    fun aNewerPositionFromAnotherDeviceIsTaken_toTheCharacter() = runBlocking {
        server.save(document, "/body/DocFragment[2]/body/p/text().3", 0.58, "Kindle", "K1", timestamp = 2_000)

        val pulled = pull(local = 7, savedAtMs = 1_000_000)

        assertEquals(PulledPosition(58, "Kindle"), pulled)
        assertEquals(18L, written!!.first)
        assertEquals(18.0 / 31, written!!.second, 1e-9)
    }

    @Test
    fun aPositionKoreaderCannotPlaceFallsBackToThePercentage() = runBlocking {
        server.save(document, "/body/DocFragment[9]/body/p/text().3", 0.5, "Kindle", "K1", timestamp = 2_000)
        pull(local = null, savedAtMs = 0)
        assertEquals(16L, written!!.first)
    }

    @Test
    fun olderPositions_andThisPhonesOwn_areLeftAlone() = runBlocking {
        server.save(document, "/body/DocFragment[2]/body/p/text().3", 0.58, "Kindle", "K1", timestamp = 2_000)
        assertNull(pull(local = 7, savedAtMs = 3_000_000))

        server.save(document, "/body/DocFragment[2]/body/p/text().3", 0.58, "HonGO! (Test)", settings.deviceId, timestamp = 9_000)
        assertNull(pull(local = 7, savedAtMs = 1_000))
        assertNull(written)
    }

    @Test
    fun withoutAnAccountNothingIsAsked() = runBlocking {
        settings.signOut()
        assertNull(pull(local = 7, savedAtMs = 0))
        assertTrue(server.requests.isEmpty())
    }

    @Test
    fun pageTurnsAreSentAsKoreaderPositions() = runBlocking {
        sync.onPosition(5, 3)
        sync.onPosition(5, 12)
        val saved = waitForProgress()

        assertEquals("/body/DocFragment[1]/body/div[1]/p[2]/text()[2].1", saved["progress"]!!.jsonPrimitive.content)
        assertEquals(12.0 / 31, saved["percentage"]!!.jsonPrimitive.double, 1e-9)
        assertEquals(settings.deviceId, saved["device_id"]!!.jsonPrimitive.content)
        assertEquals("HonGO! (Test)", saved["device"]!!.jsonPrimitive.content)
        // The two page turns went out together.
        assertEquals(1, server.requests.count { it.method == "PUT" })
    }

    @Test
    fun thePageStartAfterAJumpIsNotSentBack_butTurningBackIs() = runBlocking {
        server.save(document, "/body/DocFragment[2]/body/p/text().3", 0.58, "Kindle", "K1", timestamp = 2_000)
        pull(local = 0, savedAtMs = 0)
        // ttu opens on the page holding character 18 and saves that page's start.
        sync.onPosition(5, 14)
        sync.flush()
        Thread.sleep(300)
        assertEquals(0, server.requests.count { it.method == "PUT" })

        // Turning back a page afterwards is reading, and is sent.
        sync.onPosition(5, 12)
        sync.flush()
        assertEquals("/body/DocFragment[1]/body/div[1]/p[2]/text()[2].1", waitForProgress(device = settings.deviceId)["progress"]!!.jsonPrimitive.content)
    }

    @Test
    fun anUnchangedPositionIsNotSent() = runBlocking {
        pull(local = 7, savedAtMs = 0)
        sync.onPosition(5, 7)
        sync.flush()
        Thread.sleep(300)
        assertEquals(0, server.requests.count { it.method == "PUT" })
    }

    private fun waitForProgress(device: String? = null) = run {
        fun ready() = server.progress[document]?.let { saved ->
            device == null || saved["device_id"]?.jsonPrimitive?.content == device
        } == true
        val deadline = System.currentTimeMillis() + 5_000
        while (!ready() && System.currentTimeMillis() < deadline) Thread.sleep(20)
        server.progress[document]!!
    }

    @Test
    fun aPasswordLoginIsKeptAndForgotten() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("sync-password-test", Context.MODE_PRIVATE)
        val cwa = KoSyncAccount("http://nas:8083/kosync", "reader", KoSyncAccount.userKey("secret"), password = "secret")
        KoSyncSettings(preferences, PlainCipher).signIn(cwa)
        assertEquals(cwa, KoSyncSettings(preferences, PlainCipher).account.value)

        KoSyncSettings(preferences, PlainCipher).signOut()
        assertEquals(emptyMap<String, Any?>(), preferences.all.filterKeys { it != "kosync_device_id" })
    }

    private object PlainCipher : CredentialCipher {
        override fun encrypt(plain: String) = plain

        override fun decrypt(encrypted: String) = encrypted
    }
}
