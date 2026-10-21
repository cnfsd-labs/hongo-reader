package app.honmani.reader.ui.reader

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.honmani.reader.HonmaniApplication
import app.honmani.reader.R
import app.honmani.reader.ui.HomeNavigationBar
import app.honmani.reader.ui.HomeTab
import app.honmani.reader.ui.anki.rememberAnkiAccess
import app.honmani.reader.ui.anki.showAnkiResult
import app.honmani.reader.ui.library.LibraryScreen
import app.honmani.reader.ui.library.LibraryViewModel
import app.honmani.reader.ui.onboarding.OnboardingScreen
import app.honmani.reader.ui.progress.ProgressTab
import app.honmani.reader.ui.settings.ReaderSettingsScreen
import app.honmani.reader.ui.settings.SettingsScreen
import app.honmani.reader.ui.words.WordsScreen
import kotlinx.coroutines.flow.first

/**
 * The app's home: the library, words, dictionaries and settings tabs, and ッツ Ebook Reader with
 * the lookup panel once a book is open. The reader WebView stays loaded underneath the tabs (on
 * ttu's own book list, which they cover), so books open quickly and the library can hand new
 * books to ttu.
 */
@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel = viewModel(factory = ReaderViewModel.Factory),
) {
    val supported = remember { isReaderWebViewSupported() }

    Surface(modifier = Modifier.fillMaxSize()) {
        if (!supported) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.reader_webview_unsupported))
            }
        } else {
            ReaderWithTabs(viewModel)
        }
    }
}

@Composable
private fun ReaderWithTabs(viewModel: ReaderViewModel) {
    val panel by viewModel.panel.collectAsStateWithLifecycle()
    var atLibrary by remember { mutableStateOf(true) }
    var tab by rememberSaveable { mutableStateOf(HomeTab.LIBRARY) }
    val context = LocalContext.current
    val shareTitle = stringResource(R.string.action_share_diagnostics)
    val anki = rememberAnkiAccess()
    val container = remember { (context.applicationContext as HonmaniApplication).container }
    val dictionaryUpdates by remember { container.dictionaryUpdateChecker.updates }.collectAsStateWithLifecycle(emptyList())
    // Reading time counts while a book is on screen and the app is in front.
    val tracker = remember { container.readingTracker }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            tracker.setForeground(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
            // Characters read count from ttu's positions; ask before the app may be closed.
            if (event == Lifecycle.Event.ON_STOP) {
                container.ttuLibrary.requestProgress()
                container.progressSync.flush()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            tracker.setForeground(false)
        }
    }
    LaunchedEffect(atLibrary) {
        tracker.setVisible(!atLibrary)
        // Back at the library: the position reached goes to the sync server now.
        if (atLibrary) container.progressSync.flush()
    }
    // The first-start screen, once: not for a library that has books or dictionaries already.
    val onboardingDone by container.onboardingSettings.done.collectAsStateWithLifecycle()
    var onboardingChecked by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!container.onboardingSettings.done.value) {
            val books = container.bookDao.observeAll().first()
            val dictionaries = container.dictionaryRepository.dictionaries.first()
            if (books.isNotEmpty() || dictionaries.isNotEmpty()) container.onboardingSettings.finish()
        }
        onboardingChecked = true
    }
    var addBookOnStart by remember { mutableStateOf(false) }
    // Honmani's reader settings, from the Settings tab or the reader's top bar.
    var readerSettings by rememberSaveable { mutableStateOf(false) }
    // The Dictionaries screen, under Settings; lookups without a dictionary open it directly.
    var dictionariesShown by rememberSaveable { mutableStateOf(false) }
    val library: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory)
    LaunchedEffect(viewModel) {
        viewModel.ankiResults.collect { (expression, result) -> showAnkiResult(context, expression, result) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            ReaderWebView(
                onLookup = viewModel::onTap,
                onDismiss = viewModel::dismiss,
                highlight = when (val shown = panel) {
                    is LookupPanelState.Results -> shown.highlight
                    is LookupPanelState.Kanji -> shown.highlight
                    else -> null
                },
                diagnostics = viewModel.diagnostics,
                ttuLibrary = viewModel.ttuLibrary,
                booksDir = viewModel.booksDir,
                onBookChanged = {
                    viewModel.onBookChanged(it)
                    tracker.setBook(it)
                },
                onActivity = tracker::onActivity,
                onPage = { ttuId, characters ->
                    tracker.onPage(ttuId, characters)
                    container.progressSync.onPosition(ttuId, characters)
                },
                onOpenSettings = {
                    viewModel.dismiss()
                    readerSettings = true
                },
                onAtLibraryChanged = {
                    atLibrary = it
                    if (it) viewModel.dismiss()
                },
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing),
            )
            val state = panel
            if (state != null && !atLibrary) {
                // Composed after the WebView, so back closes the panel first.
                BackHandler { if (!viewModel.back()) viewModel.dismiss() }
                LookupPanel(
                    state = state,
                    onClose = viewModel::dismiss,
                    onAddToAnki = { group -> anki.whenReady { viewModel.addToAnki(group) } },
                    onLookupWord = viewModel::lookupWord,
                    onShowKanji = viewModel::showKanji,
                    onBack = { viewModel.back() },
                    onOpenDictionaries = {
                        // Leave the book for the Dictionaries screen.
                        tab = HomeTab.SETTINGS
                        dictionariesShown = true
                        viewModel.ttuLibrary.show(LIBRARY_PATH)
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .heightIn(max = maxHeight * PANEL_MAX_HEIGHT_FRACTION),
                )
            }
        }
        if (atLibrary && !onboardingDone) {
            if (onboardingChecked) {
                OnboardingScreen(
                    onTrySample = {
                        tab = HomeTab.LIBRARY
                        container.onboardingSettings.finish()
                        library.addSample()
                    },
                    onAddBook = {
                        tab = HomeTab.LIBRARY
                        addBookOnStart = true
                        container.onboardingSettings.finish()
                    },
                    onSkip = container.onboardingSettings::finish,
                )
            } else {
                // A moment while the app checks for books; covers ttu's list like the tabs.
                Surface(modifier = Modifier.fillMaxSize()) {}
            }
        } else if (atLibrary) {
            // Opaque, and on top: the tabs take all touches while ttu's list stays loaded below.
            BackHandler(enabled = tab != HomeTab.LIBRARY) { tab = HomeTab.LIBRARY }
            val bottomBar = @Composable {
                HomeNavigationBar(
                    selected = tab,
                    onSelect = {
                        tab = it
                        // A tab opens at its top level.
                        dictionariesShown = false
                    },
                    badges = if (dictionaryUpdates.isNotEmpty()) setOf(HomeTab.SETTINGS) else emptySet(),
                )
            }
            when (tab) {
                HomeTab.LIBRARY -> LibraryScreen(
                    bottomBar = bottomBar,
                    viewModel = library,
                    addBookOnStart = addBookOnStart,
                    onAddBookStarted = { addBookOnStart = false },
                    onShowProgress = { tab = HomeTab.PROGRESS },
                )
                HomeTab.WORDS -> WordsScreen(bottomBar = bottomBar)
                HomeTab.PROGRESS -> ProgressTab(bottomBar = bottomBar, onOpenBook = library::open)
                HomeTab.SETTINGS -> SettingsScreen(
                    bottomBar = bottomBar,
                    showingDictionaries = dictionariesShown,
                    onShowDictionaries = { dictionariesShown = it },
                    onOpenReaderSettings = { readerSettings = true },
                    onShareDiagnostics = {
                        val share = Intent(Intent.ACTION_SEND)
                            .setType("text/plain")
                            .putExtra(Intent.EXTRA_SUBJECT, "Honmani diagnostics")
                            .putExtra(Intent.EXTRA_TEXT, viewModel.diagnostics.report(context))
                        context.startActivity(Intent.createChooser(share, shareTitle))
                    },
                )
            }
        }
        if (readerSettings) {
            // On top of everything; closing it reloads the reader if anything changed.
            ReaderSettingsScreen(onClose = { readerSettings = false })
        }
    }
}

/** ttu's book list, which the tabs cover. */
private const val LIBRARY_PATH = "/manage"

/** The panel leaves most of the page visible. */
private const val PANEL_MAX_HEIGHT_FRACTION = 0.55f
