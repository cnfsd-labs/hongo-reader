package app.hongo.reader

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.room.withTransaction
import app.hongo.reader.anki.AnkiDroid
import app.hongo.reader.anki.AnkiExporter
import app.hongo.reader.anki.AnkiSettings
import app.hongo.reader.anki.PhoneVoice
import app.hongo.reader.anki.WordDetails
import app.hongo.reader.data.HongoDatabase
import app.hongo.reader.data.dictionary.DictionaryDownloader
import app.hongo.reader.data.fonts.FontStore
import app.hongo.reader.data.dictionary.DictionaryImportScheduler
import app.hongo.reader.data.dictionary.DictionaryImporter
import app.hongo.reader.data.dictionary.DictionaryRepository
import app.hongo.reader.data.dictionary.DictionaryUpdateChecker
import app.hongo.reader.data.dictionary.WorkManagerDictionaryImportScheduler
import app.hongo.reader.data.library.BookDao
import app.hongo.reader.data.library.BookImporter
import app.hongo.reader.data.opds.KeystoreCredentialCipher
import app.hongo.reader.data.opds.OpdsAutoImport
import app.hongo.reader.data.opds.OpdsClient
import app.hongo.reader.data.opds.OpdsRepository
import app.hongo.reader.data.settings.AppearanceSettings
import app.hongo.reader.data.settings.OnboardingSettings
import app.hongo.reader.data.stats.ReadingDao
import app.hongo.reader.data.stats.ReadingDayEntity
import app.hongo.reader.data.stats.ReadingStore
import app.hongo.reader.data.stats.ReadingTracker
import app.hongo.reader.data.sync.KoSyncClient
import app.hongo.reader.data.sync.KoSyncSettings
import app.hongo.reader.data.sync.ProgressSync
import app.hongo.reader.data.words.WordRepository
import app.hongo.reader.diagnostics.ReaderDiagnostics
import app.hongo.reader.lookup.DictionaryLookup
import app.hongo.reader.lookup.LookupGroups
import app.hongo.reader.ui.reader.TtuLibrary
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Manual dependency container. Objects are created lazily and live as long as the process. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val database: HongoDatabase by lazy { HongoDatabase.create(appContext) }

    /** For work that outlives a screen, such as automatic imports. */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val dictionaryRepository: DictionaryRepository by lazy {
        DictionaryRepository(database.dictionaryDao())
    }

    val dictionaryImporter: DictionaryImporter by lazy { DictionaryImporter(database) }

    val dictionaryDownloader = DictionaryDownloader()

    val dictionaryUpdateChecker: DictionaryUpdateChecker by lazy {
        DictionaryUpdateChecker(
            dao = database.dictionaryDao(),
            fetchText = dictionaryDownloader::fetchText,
            scope = applicationScope,
            log = { Log.i("HonGO.dictionaries", it) },
        )
    }

    val dictionaryLookup: DictionaryLookup by lazy {
        DictionaryLookup(database.dictionaryDao(), database.termDao(), database.termMetaDao(), database.kanjiDao())
    }

    val readerDiagnostics = ReaderDiagnostics()

    /** The reader's choices; the only file cloud backup keeps (`res/xml/data_extraction_rules.xml`). */
    private val settingsPreferences by lazy { appContext.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE) }

    /**
     * What only makes sense on this phone, never backed up: with the database and the books left
     * out of backup, a restored "first start done" would skip the dictionary download.
     */
    private val devicePreferences by lazy { appContext.getSharedPreferences(DEVICE_SETTINGS, Context.MODE_PRIVATE) }

    val appearanceSettings: AppearanceSettings by lazy { AppearanceSettings(settingsPreferences) }

    val onboardingSettings: OnboardingSettings by lazy { OnboardingSettings(devicePreferences) }

    val booksDir = File(appContext.filesDir, "books")

    val fontsDir = File(appContext.filesDir, "fonts")

    val fontStore: FontStore by lazy { FontStore(fontsDir, applicationScope, dictionaryDownloader::download) }

    val bookDao: BookDao by lazy { database.bookDao() }

    val bookImporter: BookImporter by lazy {
        BookImporter(bookDao, booksDir, File(appContext.filesDir, "covers"))
    }

    /** One for the app: catalogue passwords and the Google key share its Keystore key. */
    private val credentialCipher = KeystoreCredentialCipher()

    val wordRepository: WordRepository by lazy { WordRepository(database) }

    val ankiSettings: AnkiSettings by lazy {
        AnkiSettings(settingsPreferences, credentialCipher, devicePreferences)
    }

    val ankiDroid: AnkiDroid by lazy { AnkiDroid(appContext) }

    val koSyncSettings: KoSyncSettings by lazy { KoSyncSettings(devicePreferences, credentialCipher) }

    val koSyncClient: KoSyncClient by lazy { KoSyncClient() }

    val progressSync: ProgressSync by lazy {
        ProgressSync(
            settings = koSyncSettings,
            client = koSyncClient,
            books = bookDao,
            bookFile = bookImporter::bookFile,
            scope = applicationScope,
            device = "HonGO! (${Build.MODEL})",
        )
    }

    /** One phone voice for the app, so its engine stays connected between cards. */
    val phoneVoice: PhoneVoice by lazy { PhoneVoice(appContext, applicationScope) }

    /** Audio for cards waits here until AnkiDroid has copied it; see `res/xml/file_paths.xml`. */
    val ankiAudioDir = File(appContext.cacheDir, "anki-audio")

    val ankiExporter: AnkiExporter by lazy {
        AnkiExporter(
            anki = ankiDroid,
            settings = ankiSettings,
            words = wordRepository,
            phoneVoice = { phoneVoice },
            audioDir = ankiAudioDir,
            // Off the main thread: "Add all" and the text file look up every word.
            details = { expression, reading ->
                withContext(Dispatchers.Default) {
                    val group = LookupGroups.from(dictionaryLookup.lookup(expression))
                        .firstOrNull { it.expression == expression && it.reading == reading }
                    WordDetails(
                        senses = group?.sections?.firstOrNull()?.senses?.map { it.glosses }.orEmpty(),
                        pitches = group?.pitches.orEmpty(),
                        frequency = group?.frequencies?.firstOrNull()?.label,
                    )
                }
            },
        )
    }

    val opdsClient = OpdsClient()

    val opdsRepository: OpdsRepository by lazy {
        OpdsRepository(database.opdsCatalogDao(), credentialCipher, opdsClient)
    }

    /** Catalogue downloads wait here before they are added to the library. */
    val downloadsDir = File(appContext.cacheDir, "downloads")

    val opdsAutoImport: OpdsAutoImport by lazy {
        OpdsAutoImport(
            repository = opdsRepository,
            client = opdsClient,
            importer = bookImporter,
            bookDao = bookDao,
            downloadsDir = downloadsDir,
            scope = applicationScope,
            log = { Log.i("HonGO.opds", it) },
        )
    }

    /** Lives with the process: the reader WebView attaches to it, the library drives it. */
    val ttuLibrary = TtuLibrary()

    val readingDao: ReadingDao by lazy { database.readingDao() }

    /** Counts reading time and characters; fed by the reader screen and ttu's position reports. */
    val readingTracker: ReadingTracker by lazy {
        val store = object : ReadingStore {
            override suspend fun add(ttuId: Long, day: String, seconds: Long, characters: Long) {
                val book = bookDao.findByTtuId(ttuId) ?: return
                database.withTransaction {
                    readingDao.insertIfMissing(ReadingDayEntity(book.id, day))
                    readingDao.add(book.id, day, seconds, characters)
                }
            }

            override suspend fun position(ttuId: Long): Long? = bookDao.findByTtuId(ttuId)?.characters

            override suspend fun setPosition(ttuId: Long, characters: Long) =
                bookDao.setCharactersByTtuId(ttuId, characters)
        }
        ReadingTracker(store, applicationScope, log = { Log.i("HonGO.stats", it) }).also { tracker ->
            applicationScope.launch {
                ttuLibrary.progress.collect { books ->
                    tracker.onPositions(books.mapNotNull { book -> book.characters?.let { book.id to it } }.toMap())
                }
            }
        }
    }

    val dictionaryImportScheduler: DictionaryImportScheduler by lazy {
        WorkManagerDictionaryImportScheduler(appContext)
    }

    internal companion object {
        /** Shared preferences files; the backup rules in `res/xml` name them as `<name>.xml`. */
        const val SETTINGS = "settings"
        const val DEVICE_SETTINGS = "device"
    }
}
