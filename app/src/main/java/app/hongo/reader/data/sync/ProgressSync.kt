package app.hongo.reader.data.sync

import android.util.Log
import app.hongo.reader.data.library.BookDao
import app.hongo.reader.data.library.BookEntity
import app.hongo.reader.data.library.BookFormat
import java.io.File
import java.io.IOException
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** A position taken from another device when a book opened. */
data class PulledPosition(val percent: Int, val device: String)

/**
 * Reading progress sync with KOReader's sync server (KOReader on an e-reader, Calibre-Web
 * Automated, or another HonGO!). Only EPUBs: the document id is the file's partial MD5, so a book
 * matches when both devices have the same file.
 *
 * - When a book opens, the server's position wins if it is newer than ttu's bookmark and from
 *   another device ([pull], before ttu loads the book).
 * - Page turns are sent a little later, together ([onPosition]), and at once when the reader
 *   leaves the book or the app ([flush]).
 *
 * Failures (no network, server down) only skip a sync: reading never waits more than
 * [PULL_TIMEOUT_MS] for it.
 */
class ProgressSync(
    private val settings: KoSyncSettings,
    private val client: KoSyncClient,
    private val books: BookDao,
    private val bookFile: (BookEntity) -> File,
    private val scope: CoroutineScope,
    /** How this phone is named on the server ("HonGO! (Pixel 8)"). */
    private val device: String,
    private val pushDelayMs: Long = PUSH_DELAY_MS,
) {
    private class Document(val id: String, val positions: EpubPositions)

    private val documents = object : LinkedHashMap<Long, Document>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Document>?) = size > MAX_DOCUMENTS
    }
    private val documentLock = Mutex()
    private val pushLock = Mutex()

    /** Characters at the last position synced per ttu book, so unchanged positions aren't sent. */
    private val synced = HashMap<Long, Long>()

    /**
     * Positions just taken from the server per ttu book: ttu then saves the start of the page
     * that holds it, a little earlier, which must not go back as if the reader had turned back.
     * Only that first save is skipped.
     */
    private val pulledTo = HashMap<Long, Long>()
    private var pending: Pair<Long, Long>? = null
    private var pushJob: Job? = null

    /**
     * Before [book] opens in ttu (as [ttuId]): takes the server's position if it is newer and from
     * another device, writing it with [setBookmark] (characters, progress). Returns what was taken,
     * or null if the book stays where it was.
     */
    suspend fun pull(
        book: BookEntity,
        ttuId: Long,
        bookmark: suspend () -> Pair<Long?, Long>,
        setBookmark: suspend (Long, Double) -> Unit,
    ): PulledPosition? {
        val account = settings.account.value ?: return null
        if (book.format != BookFormat.EPUB) return null
        return try {
            withTimeoutOrNull(PULL_TIMEOUT_MS) { pull(account, book, ttuId, bookmark, setBookmark) }
        } catch (e: IOException) {
            Log.i(TAG, "No progress sync for this book: ${e.message}")
            null
        }
    }

    private suspend fun pull(
        account: KoSyncAccount,
        book: BookEntity,
        ttuId: Long,
        bookmark: suspend () -> Pair<Long?, Long>,
        setBookmark: suspend (Long, Double) -> Unit,
    ): PulledPosition? {
        val document = document(book) ?: return null
        val remote = client.progress(account, document.id)
        val (local, savedAt) = bookmark()
        local?.let { synchronized(synced) { synced[ttuId] = it } }
        if (remote == null || remote.deviceId == settings.deviceId) return null
        val total = document.positions.total.coerceAtLeast(1)
        val characters = (document.positions.charactersAt(remote.progress)?.toLong()
            ?: (remote.percentage * total).roundToLong()).coerceIn(0, total.toLong())
        val remoteAt = remote.timestamp?.let { it * 1000 }
        val newer = when {
            local == null -> true
            remoteAt != null -> remoteAt > savedAt
            // An old server without times: only ever forwards.
            else -> characters > local
        }
        if (!newer || (local != null && abs(characters - local) < MIN_CHANGE)) return null
        setBookmark(characters, characters.toDouble() / total)
        synchronized(synced) {
            synced[ttuId] = characters
            pulledTo[ttuId] = characters
        }
        return PulledPosition((characters * 100.0 / total).roundToInt(), remote.device)
    }

    /** ttu saved a reading position (a page turn); sent after [pushDelayMs], with later ones. */
    fun onPosition(ttuId: Long, characters: Long) {
        if (settings.account.value == null) return
        // The first save after a jump is ttu opening the page that holds the pulled position.
        val pageStart = synchronized(synced) {
            pulledTo.remove(ttuId)?.let { pulled -> characters in (pulled - PAGE_CHARACTERS)..pulled } == true
        }
        if (pageStart) return
        synchronized(this) {
            pending = ttuId to characters
            if (pushJob?.isActive == true) return
            pushJob = scope.launch {
                delay(pushDelayMs)
                push()
            }
        }
    }

    /** Sends the latest position now: the reader left the book or the app. */
    fun flush() {
        synchronized(this) {
            if (pending == null) return
            pushJob?.cancel()
            pushJob = scope.launch { push() }
        }
    }

    private suspend fun push() = pushLock.withLock {
        val (ttuId, characters) = synchronized(this) { pending.also { pending = null } } ?: return@withLock
        if (synchronized(synced) { synced[ttuId] } == characters) return@withLock
        val account = settings.account.value ?: return@withLock
        try {
            val book = books.findByTtuId(ttuId)?.takeIf { it.format == BookFormat.EPUB } ?: return@withLock
            val document = document(book) ?: return@withLock
            val xpointer = document.positions.xpointerAt(characters.toInt()) ?: return@withLock
            val percentage = characters.toDouble() / document.positions.total.coerceAtLeast(1)
            client.update(account, document.id, xpointer, percentage.coerceIn(0.0, 1.0), device, settings.deviceId)
            synchronized(synced) { synced[ttuId] = characters }
        } catch (e: IOException) {
            Log.i(TAG, "Progress not sent: ${e.message}")
        }
    }

    /** The book's sync id and positions, worked out once per book (it reads the whole file). */
    private suspend fun document(book: BookEntity): Document? = documentLock.withLock {
        documents[book.id] ?: withContext(Dispatchers.Default) {
            val file = bookFile(book).takeIf { it.isFile } ?: return@withContext null
            val positions = EpubPositions.read(file) ?: return@withContext null
            Document(PartialMd5.of(file), positions)
        }?.also { documents[book.id] = it }
    }

    private companion object {
        const val TAG = "HonGO.sync"
        const val PULL_TIMEOUT_MS = 4_000L
        const val PUSH_DELAY_MS = 15_000L
        const val MAX_DOCUMENTS = 3

        /** Positions closer than this count as the same (a few characters' rounding). */
        const val MIN_CHANGE = 10

        /** More characters than a phone page holds, even in small print. */
        const val PAGE_CHARACTERS = 1_500L
    }
}
