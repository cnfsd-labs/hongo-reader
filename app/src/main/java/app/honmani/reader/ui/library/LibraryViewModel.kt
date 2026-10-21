package app.honmani.reader.ui.library

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.honmani.reader.HonmaniApplication
import app.honmani.reader.R
import app.honmani.reader.data.difficulty.StoredDifficulty
import app.honmani.reader.data.library.BookDao
import app.honmani.reader.data.library.BookEntity
import app.honmani.reader.data.library.BookImporter
import app.honmani.reader.data.library.BookSource
import app.honmani.reader.data.library.RemovedBookEntity
import app.honmani.reader.data.opds.AutoImportStatus
import app.honmani.reader.lookup.Romaji
import app.honmani.reader.lookup.TextReading
import app.honmani.reader.ui.reader.TtuBook
import app.honmani.reader.ui.reader.TtuLibrary
import app.honmani.reader.ui.reader.TtuLibraryException
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class LibraryBook(
    val id: Long,
    val title: String,
    val author: String?,
    val cover: File?,
    /** Reading progress, 0 to 1. */
    val progress: Float,
    val status: ReadingStatus = ReadingStatus.of(progress, finishedAt = null),
    val hidden: Boolean = false,
    /** Identifies the book's file, and with it its difficulty grade. */
    val contentHash: String = "",
)

data class LibraryUiState(
    /** The books shown (after filter and search); null until the library has loaded. */
    val books: List<LibraryBook>? = null,
    /** All books in the library, before filter and search. */
    val totalBooks: Int = 0,
    /** How many books (not hidden) have each status, for the filter buttons. */
    val counts: Map<ReadingStatus, Int> = emptyMap(),
    /** Only books with this status; null for all. */
    val filter: ReadingStatus? = null,
    /** Showing the hidden books (and only those) instead of the library. */
    val showHidden: Boolean = false,
    val hiddenCount: Int = 0,
    /** The book read last, if the reader is still reading it: offered as "Continue reading". */
    val continueBook: LibraryBook? = null,
    val query: String = "",
    val sort: LibrarySort = LibrarySort.RECENT,
    /** Name of the file being added, if any. */
    val adding: String? = null,
    /** The book being opened, if any. */
    val opening: Long? = null,
)

/** Something to tell the user once, as a snackbar. */
sealed interface LibraryNotice {
    data class AlreadyInLibrary(val title: String) : LibraryNotice

    data class Unsupported(val fileName: String) : LibraryNotice

    data class Unreadable(val fileName: String) : LibraryNotice

    data class ResetFailed(val title: String) : LibraryNotice

    /** ッツ Ebook Reader could not import or open the book. */
    data class ReaderFailed(val title: String) : LibraryNotice
}

/** Which books the library shows: one reading status (or all), or the hidden ones. */
private data class Shelf(val status: ReadingStatus? = null, val hidden: Boolean = false)

/** A file the user picked: its display name and contents. */
class PickedFile(val name: String, val input: InputStream)

class LibraryViewModel(
    private val dao: BookDao,
    private val importer: BookImporter,
    private val ttu: TtuLibrary,
    private val openFile: (Uri) -> PickedFile?,
    private val now: () -> Long = System::currentTimeMillis,
    /** What the automatic import from book servers is doing, if it runs. */
    val autoImport: StateFlow<AutoImportStatus?> = MutableStateFlow(null),
    /** How a title reads in kana (with the dictionary), for romaji search. */
    private val reading: suspend (String) -> String = { it },
    /** The sample book offered on the first-start screen. */
    private val openSample: () -> PickedFile? = { null },
    /** Takes a newer reading position from progress sync before the book (ttu id) opens. */
    private val syncPosition: suspend (BookEntity, Long) -> Unit = { _, _ -> },
    /** Book grades by content hash, for sorting by difficulty. */
    private val grades: Flow<Map<String, StoredDifficulty>> = flowOf(emptyMap()),
    /** The chosen order (a [LibrarySort] name) and where a new choice is kept. */
    private val sortName: Flow<String?> = flowOf(null),
    private val saveSort: (String) -> Unit = {},
) : ViewModel() {
    private val adding = MutableStateFlow<String?>(null)
    private val opening = MutableStateFlow<Long?>(null)
    private val shelf = MutableStateFlow(Shelf())

    private val query = MutableStateFlow("")

    /** Readings of title and author by book id, worked out when a romaji search starts. */
    private val readings = MutableStateFlow<Map<Long, String>>(emptyMap())
    private val searchTerms = combine(query, readings) { query, readings -> query to readings }
    private val addMutex = Mutex()
    private val adder = BookAdder(dao, importer, ttu)
    private val _notices = Channel<LibraryNotice>(Channel.BUFFERED)
    val notices: Flow<LibraryNotice> = _notices.receiveAsFlow()

    val uiState: StateFlow<LibraryUiState> =
        combine(
            combine(dao.observeAll(), grades, sortName) { entities, grades, sort -> Triple(entities, grades, LibrarySort.fromName(sort)) },
            adding,
            opening,
            shelf,
            searchTerms,
        ) { (entities, grades, sort), adding, opening, shelf, terms ->
            val (query, readings) = terms
            val books = sort.apply(entities.map(::toLibraryBook)) { book ->
                grades[book.contentHash]?.difficulty?.score?.takeIf { it > 0 }
            }
            val (hidden, shown) = books.partition { it.hidden }
            val onShelf = when {
                shelf.hidden -> hidden
                shelf.status == null -> shown
                else -> shown.filter { it.status == shelf.status }
            }
            LibraryUiState(
                books = onShelf.filter { LibrarySearch.matches(it, query, readings[it.id]) },
                totalBooks = books.size,
                counts = shown.groupingBy { it.status }.eachCount(),
                filter = shelf.status,
                showHidden = shelf.hidden,
                hiddenCount = hidden.size,
                continueBook = entities
                    .filter { !it.hidden && it.lastOpenedAt != null }
                    .maxByOrNull { it.lastOpenedAt!! }
                    ?.let(::toLibraryBook)
                    ?.takeIf { it.status == ReadingStatus.READING },
                query = query,
                sort = sort,
                adding = adding,
                opening = opening,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    init {
        // The hidden shelf closes when its last book is unhidden or removed.
        viewModelScope.launch {
            dao.observeAll().collect { books ->
                if (shelf.value.hidden && books.none { it.hidden }) shelf.value = Shelf()
            }
        }
        viewModelScope.launch {
            ttu.progress.collect { books ->
                books.forEach { dao.setProgressByTtuId(it.id, it.progress) }
            }
        }
        viewModelScope.launch {
            ttu.finished.collect { ttuId -> dao.setFinishedByTtuId(ttuId, now()) }
        }
    }

    /** Adds the picked files one after another, and hands each new book to the reader. */
    fun add(uris: List<Uri>) {
        viewModelScope.launch {
            for (uri in uris) addMutex.withLock { addOne(uri) }
        }
    }

    /** Adds the sample book and opens it (also if it is in the library already). */
    fun addSample() {
        viewModelScope.launch {
            val book = addMutex.withLock {
                val picked = withContext(Dispatchers.IO) { openSample() } ?: return@launch
                add(picked)
            }
            book?.let { open(it.id) }
        }
    }

    private suspend fun addOne(uri: Uri) {
        val fallbackName = uri.lastPathSegment.orEmpty()
        val picked = withContext(Dispatchers.IO) { openFile(uri) }
        if (picked == null) {
            _notices.send(LibraryNotice.Unreadable(fallbackName))
            return
        }
        add(picked)
    }

    /** Adds [picked]; returns the book if it is in the library now. */
    private suspend fun add(picked: PickedFile): BookEntity? {
        adding.value = picked.name
        try {
            val result = picked.input.use { adder.add(it, picked.name) }
            val notice = when (result) {
                is BookAdder.Result.Added -> null
                is BookAdder.Result.AlreadyInLibrary -> LibraryNotice.AlreadyInLibrary(result.book.title)
                BookAdder.Result.Unsupported -> LibraryNotice.Unsupported(picked.name)
                BookAdder.Result.Unreadable -> LibraryNotice.Unreadable(picked.name)
                is BookAdder.Result.ReaderFailed -> LibraryNotice.ReaderFailed(result.title)
            }
            notice?.let { _notices.send(it) }
            return when (result) {
                is BookAdder.Result.Added -> result.book
                is BookAdder.Result.AlreadyInLibrary -> result.book
                else -> null
            }
        } finally {
            adding.value = null
        }
    }

    fun open(id: Long) {
        if (opening.value != null) return
        opening.value = id
        viewModelScope.launch {
            try {
                val book = dao.find(id) ?: return@launch
                val known = book.ttuId?.let { ttuId -> book.ttuTitle?.let { TtuBook(ttuId, it) } }
                val ttuBook = try {
                    ttu.ensure(known, fileUrl(book), readerFileName(book))
                } catch (_: TtuLibraryException) {
                    _notices.send(LibraryNotice.ReaderFailed(book.title))
                    return@launch
                }
                if (ttuBook != known) adder.link(book, ttuBook)
                syncPosition(book, ttuBook.id)
                ttu.open(ttuBook.id)
                dao.setLastOpened(book.id, now())
            } catch (_: TtuLibraryException) {
                _notices.send(LibraryNotice.ReaderFailed(dao.find(id)?.title.orEmpty()))
            } finally {
                opening.value = null
            }
        }
    }

    /** Shows only books with [status]; null shows all. */
    fun setFilter(status: ReadingStatus?) {
        shelf.value = Shelf(status = status)
    }

    /** Shows the hidden books instead of the library, or goes back to all books. */
    fun showHidden(show: Boolean) {
        shelf.value = Shelf(hidden = show)
    }

    fun setHidden(id: Long, hidden: Boolean) {
        viewModelScope.launch { dao.setHidden(id, hidden) }
    }

    fun setSort(sort: LibrarySort) = saveSort(sort.name)

    fun search(text: String) {
        query.value = text
        if (Romaji.isRomaji(text)) readTitles()
    }

    private var readingJob: Job? = null

    /** Works out the readings of titles that don't have one yet (once per book). */
    private fun readTitles() {
        if (readingJob?.isActive == true) return
        readingJob = viewModelScope.launch {
            val books = dao.observeAll().first().filter { it.id !in readings.value }
            for (book in books) {
                val text = listOfNotNull(book.title, book.author).joinToString(" ")
                val kana = try {
                    reading(text)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    text
                }
                readings.update { it + (book.id to kana) }
            }
        }
    }

    fun markFinished(id: Long) {
        viewModelScope.launch { dao.setFinished(id, now()) }
    }

    /** Starts the book over: ttu forgets its reading position (and that it was finished). */
    fun resetProgress(id: Long) {
        viewModelScope.launch {
            val book = dao.find(id) ?: return@launch
            try {
                book.ttuId?.let { ttu.resetProgress(it) }
                dao.clearProgress(book.id)
            } catch (_: TtuLibraryException) {
                _notices.send(LibraryNotice.ResetFailed(book.title))
            }
        }
    }

    fun remove(id: Long) {
        viewModelScope.launch {
            val book = dao.find(id) ?: return@launch
            book.ttuId?.let { ttuId ->
                try {
                    ttu.remove(ttuId)
                } catch (_: TtuLibraryException) {
                    // The reader keeps an orphaned copy; harmless, and gone if it is reset.
                }
            }
            dao.delete(book.id)
            // A book from a catalogue stays removed: automatic imports skip it from now on.
            book.sourceId?.takeIf { book.source == BookSource.OPDS }?.let {
                dao.insertRemoved(RemovedBookEntity(book.source, it, now()))
            }
            withContext(Dispatchers.IO) { importer.deleteFiles(book) }
        }
    }

    private fun toLibraryBook(book: BookEntity) = LibraryBook(
        id = book.id,
        title = book.title,
        author = book.author,
        cover = importer.coverFile(book),
        progress = book.progress,
        status = ReadingStatus.of(book.progress, book.finishedAt),
        hidden = book.hidden,
        contentHash = book.contentHash,
    )

    companion object {
        fun fileUrl(book: BookEntity) = TtuLibrary.BOOK_FILES_PATH + book.fileName

        /** ttu takes a text file's title from its name, and picks its importer by extension. */
        fun readerFileName(book: BookEntity): String {
            val safeTitle = book.title.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").take(100)
            return "${safeTitle.ifBlank { "book" }}.${book.format.extension}"
        }

        val Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as HonmaniApplication
                val container = application.container
                LibraryViewModel(
                    dao = container.bookDao,
                    importer = container.bookImporter,
                    ttu = container.ttuLibrary,
                    openFile = { uri -> openPickedFile(application.contentResolver, uri) },
                    openSample = {
                        runCatching { PickedFile(SAMPLE_FILE_NAME, application.assets.open(SAMPLE_ASSET)) }.getOrNull()
                    },
                    autoImport = container.opdsAutoImport.status,
                    grades = container.difficultyStore.grades,
                    sortName = container.appearanceSettings.librarySort,
                    saveSort = container.appearanceSettings::setLibrarySort,
                    syncPosition = { book, ttuId ->
                        val ttu = container.ttuLibrary
                        // Sync is a bonus: the book opens where it was if the reader page can't answer.
                        val pulled = try {
                            container.progressSync.pull(
                                book,
                                ttuId,
                                bookmark = { ttu.bookmark(ttuId) },
                                setBookmark = { characters, progress -> ttu.setBookmark(ttuId, characters, progress) },
                            )
                        } catch (_: TtuLibraryException) {
                            null
                        }
                        if (pulled != null) {
                            val text = application.getString(R.string.sync_continued, pulled.percent, pulled.device)
                            Toast.makeText(application, text, Toast.LENGTH_LONG).show()
                        }
                    },
                    // Lookups off the main thread: a romaji search reads every title in the library.
                    reading = { text ->
                        val lookup = container.dictionaryLookup
                        withContext(Dispatchers.Default) {
                            TextReading.of(text) { part ->
                                lookup.lookup(part).entries.firstOrNull()?.let { entry ->
                                    entry.source.length to TextReading.readingOf(
                                        entry.source,
                                        entry.term.expression,
                                        entry.term.reading,
                                        entry.matchedExpression,
                                    )
                                }
                            }
                        }
                    },
                )
            }
        }

        private const val SAMPLE_ASSET = "samples/wagahai-neko-1.epub"
        private const val SAMPLE_FILE_NAME = "wagahai-neko-1.epub"

        /** Opens a file the user picked; null if its app won't give it (its errors must not close Honmani). */
        internal fun openPickedFile(resolver: ContentResolver, uri: Uri): PickedFile? = try {
            val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                ?: uri.lastPathSegment
                ?: "book"
            resolver.openInputStream(uri)?.let { PickedFile(name, it) }
        } catch (_: Exception) {
            // SecurityException, FileNotFoundException, or whatever the other app's provider throws
            // (IllegalArgumentException for an address it doesn't know, say).
            null
        }
    }
}
