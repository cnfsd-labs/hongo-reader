package app.hongo.reader.ui.words

import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.hongo.reader.ui.ProgressBar
import app.hongo.reader.R
import app.hongo.reader.data.words.WordCounts
import app.hongo.reader.data.words.WordEntity
import app.hongo.reader.data.words.WordLookupEntity
import app.hongo.reader.data.words.WordStatus
import app.hongo.reader.ui.HongoTopAppBar
import app.hongo.reader.ui.anki.label
import app.hongo.reader.ui.anki.rememberAnkiAccess
import app.hongo.reader.ui.anki.showAnkiResult
import app.hongo.reader.ui.theme.HongoTheme
import kotlinx.coroutines.flow.first

/** The words looked up while reading, most looked up (recently) first. */
@Composable
fun WordsScreen(
    bottomBar: @Composable () -> Unit,
    viewModel: WordsViewModel = viewModel(factory = WordsViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Each change gets a snackbar with an undo; the next waits until it is gone.
    var change by remember { mutableStateOf<WordChange?>(null) }
    LaunchedEffect(viewModel) {
        viewModel.changes.collect {
            change = it
            snapshotFlow { change }.first { current -> current == null }
        }
    }
    val current = change
    val message = current?.let {
        when (it) {
            is WordChange.Learned -> stringResource(R.string.word_moved_to_learned, it.expression)
            is WordChange.Deleted -> stringResource(R.string.word_deleted, it.expression)
        }
    }
    val undoLabel = stringResource(R.string.action_undo)
    val context = LocalContext.current
    val anki = rememberAnkiAccess()
    val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) viewModel.exportToFile(uri.toString())
    }
    LaunchedEffect(viewModel) {
        viewModel.ankiOutcomes.collect { outcome ->
            when (outcome) {
                is AnkiOutcome.One -> showAnkiResult(context, outcome.expression, outcome.result)
                is AnkiOutcome.Many -> {
                    val stopped = outcome.stopped
                    if (stopped != null && outcome.added == 0) {
                        showAnkiResult(context, "", stopped)
                    } else {
                        val added = context.resources.getQuantityString(
                            R.plurals.anki_added_many,
                            outcome.added,
                            outcome.added,
                        )
                        val audio = outcome.audioProblem?.let { " – " + context.getString(it.label()) }.orEmpty()
                        Toast.makeText(context, added + audio, Toast.LENGTH_LONG).show()
                        if (stopped != null) showAnkiResult(context, "", stopped)
                    }
                }
                is AnkiOutcome.Exported -> Toast.makeText(
                    context,
                    context.resources.getQuantityString(R.plurals.anki_exported, outcome.count, outcome.count),
                    Toast.LENGTH_LONG,
                ).show()
                AnkiOutcome.ExportFailed ->
                    Toast.makeText(context, R.string.anki_export_failed, Toast.LENGTH_LONG).show()
            }
        }
    }
    LaunchedEffect(current) {
        if (current != null && message != null) {
            val result = snackbarHostState.showSnackbar(message, undoLabel, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) viewModel.undo(current)
            change = null
        }
    }

    WordsContent(
        uiState = uiState,
        now = remember(uiState.words) { System.currentTimeMillis() },
        snackbarHostState = snackbarHostState,
        bottomBar = bottomBar,
        onFilter = viewModel::setFilter,
        onToggleExpanded = viewModel::toggleExpanded,
        onSetStarred = viewModel::setStarred,
        onMarkLearned = viewModel::markLearned,
        onMarkLearning = viewModel::markLearning,
        onDelete = viewModel::delete,
        onAddToAnki = { word, lookup -> anki.whenReady { viewModel.addToAnki(word, lookup) } },
        onAddAllToAnki = { anki.whenReady(viewModel::addAllToAnki) },
        onExport = { exportFile.launch(context.getString(R.string.anki_export_file_name)) },
    )
}

@Composable
private fun WordsContent(
    uiState: WordsUiState,
    now: Long,
    snackbarHostState: SnackbarHostState,
    bottomBar: @Composable () -> Unit,
    onFilter: (WordFilter) -> Unit,
    onToggleExpanded: (id: Long) -> Unit,
    onSetStarred: (id: Long, starred: Boolean) -> Unit,
    onMarkLearned: (WordEntity) -> Unit,
    onMarkLearning: (id: Long) -> Unit,
    onDelete: (id: Long) -> Unit,
    onAddToAnki: (WordEntity, WordLookupEntity?) -> Unit,
    onAddAllToAnki: () -> Unit,
    onExport: () -> Unit,
) {
    Scaffold(
        topBar = {
            HongoTopAppBar(
                title = stringResource(R.string.words_title),
                actions = { if (!uiState.words.isNullOrEmpty()) WordsMenu(onExport) },
            )
        },
        bottomBar = bottomBar,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                for (filter in WordFilter.entries) {
                    FilterChip(
                        selected = uiState.filter == filter,
                        onClick = { onFilter(filter) },
                        label = {
                            Text(stringResource(filterLabel(filter), count(uiState.counts, filter)))
                        },
                    )
                }
            }
            val words = uiState.words
            val progress = uiState.ankiProgress
            if (progress != null) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        text = stringResource(R.string.anki_adding_progress, progress.first + 1, progress.second),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    ProgressBar(
                        progress = { progress.first.toFloat() / progress.second.coerceAtLeast(1) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                    )
                }
            } else if (uiState.filter == WordFilter.FOR_ANKI && !words.isNullOrEmpty()) {
                Button(
                    onClick = onAddAllToAnki,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(stringResource(R.string.action_add_all_to_anki, words.size))
                }
            }
            when {
                words == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                words.isEmpty() -> Text(
                    text = stringResource(emptyText(uiState.filter)),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                )
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(words, key = { it.id }) { word ->
                        val expanded = word.id == uiState.expanded
                        WordRow(
                            word = word,
                            now = now,
                            // Learned words are listed by name only; details on a tap.
                            compact = uiState.filter == WordFilter.LEARNED && !expanded,
                            expanded = expanded,
                            lookups = if (expanded) uiState.lookups else emptyList(),
                            onToggleExpanded = { onToggleExpanded(word.id) },
                            onSetStarred = { onSetStarred(word.id, it) },
                            onMarkLearned = { onMarkLearned(word) },
                            onMarkLearning = { onMarkLearning(word.id) },
                            onDelete = { onDelete(word.id) },
                            onAddToAnki = { lookup -> onAddToAnki(word, lookup) },
                            adding = word.id in uiState.addingToAnki,
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

/** Exports the list shown as a file for Anki on a computer. */
@Composable
private fun WordsMenu(onExport: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.words_menu))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_export_anki_file)) },
                onClick = {
                    open = false
                    onExport()
                },
            )
        }
    }
}

private fun filterLabel(filter: WordFilter) = when (filter) {
    WordFilter.LEARNING -> R.string.words_filter_learning
    WordFilter.FOR_ANKI -> R.string.words_filter_for_anki
    WordFilter.STARRED -> R.string.words_filter_starred
    WordFilter.LEARNED -> R.string.words_filter_learned
}

private fun count(counts: WordCounts, filter: WordFilter) = when (filter) {
    WordFilter.LEARNING -> counts.learning
    WordFilter.FOR_ANKI -> counts.forAnki
    WordFilter.STARRED -> counts.starred
    WordFilter.LEARNED -> counts.learned
}

private fun emptyText(filter: WordFilter) = when (filter) {
    WordFilter.LEARNING -> R.string.words_empty_learning
    WordFilter.FOR_ANKI -> R.string.words_empty_for_anki
    WordFilter.STARRED -> R.string.words_empty_starred
    WordFilter.LEARNED -> R.string.words_empty_learned
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordRow(
    word: WordEntity,
    now: Long,
    compact: Boolean,
    expanded: Boolean,
    lookups: List<WordLookupEntity>,
    onToggleExpanded: () -> Unit,
    onSetStarred: (Boolean) -> Unit,
    onMarkLearned: () -> Unit,
    onMarkLearning: () -> Unit,
    onDelete: () -> Unit,
    onAddToAnki: (WordLookupEntity?) -> Unit,
    adding: Boolean = false,
) {
    val highlight = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleExpanded)
            .padding(start = 16.dp, top = 4.dp, bottom = if (compact) 4.dp else 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
            // The reading follows the word, or moves below it when both don't fit on one line.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(word.expression, style = MaterialTheme.typography.titleLarge)
                if (word.reading.isNotEmpty() && word.reading != word.expression) {
                    Text(
                        text = word.reading,
                        style = MaterialTheme.typography.bodyMedium,
                        color = muted,
                        modifier = Modifier
                            .align(Alignment.Bottom)
                            .padding(bottom = 3.dp),
                    )
                }
            }
            AnkiButton(inAnki = word.ankiNoteId != null, adding = adding, onClick = { onAddToAnki(null) })
            IconToggleButton(
                checked = word.starred,
                onCheckedChange = onSetStarred,
                modifier = Modifier.size(ACTION_SIZE),
            ) {
                Icon(
                    imageVector = if (word.starred) Icons.Filled.Star else Icons.Outlined.Star,
                    contentDescription = stringResource(
                        if (word.starred) R.string.action_unstar_word else R.string.action_star_word,
                    ),
                    tint = if (word.starred) highlight else muted,
                )
            }
            if (word.status == WordStatus.LEARNED) {
                IconButton(onClick = onMarkLearning, modifier = Modifier.size(ACTION_SIZE)) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = stringResource(R.string.action_mark_learning),
                        tint = muted,
                    )
                }
            } else {
                IconButton(onClick = onMarkLearned, modifier = Modifier.size(ACTION_SIZE)) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = stringResource(R.string.action_mark_learned),
                        tint = muted,
                    )
                }
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(ACTION_SIZE)) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete), tint = muted)
            }
        }
        if (!compact) {
            Column(modifier = Modifier.padding(end = 16.dp)) {
                if (word.gloss.isNotEmpty()) {
                    Text(
                        text = word.gloss,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = if (expanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (expanded && lookups.isNotEmpty()) {
                    for (lookup in lookups) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.weight(1f)) {
                                Sentence(
                                    sentence = sentenceText(
                                        lookup.sentence,
                                        lookup.surfaceStart,
                                        lookup.surfaceEnd,
                                        highlight,
                                    ),
                                    source = lookup.bookTitle,
                                    maxLines = Int.MAX_VALUE,
                                )
                            }
                            // A card with this sentence instead of the latest one.
                            if (word.ankiNoteId == null && lookups.size > 1) {
                                IconButton(
                                    onClick = { onAddToAnki(lookup) },
                                    enabled = !adding,
                                    modifier = Modifier.size(ACTION_SIZE),
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_add_card),
                                        contentDescription = stringResource(R.string.action_add_to_anki_with_sentence),
                                        tint = muted,
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Sentence(
                        sentence = sentenceText(word.lastSentence, word.lastSurfaceStart, word.lastSurfaceEnd, highlight),
                        source = null,
                        maxLines = 2,
                    )
                }
                Text(
                    text = details(word, now),
                    style = MaterialTheme.typography.labelSmall,
                    color = muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

/**
 * Adds the word to Anki: a spinner while the card is made (audio takes a few seconds), then a
 * check once it is there (tapping again says so).
 */
@Composable
private fun AnkiButton(inAnki: Boolean, adding: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = !adding, modifier = Modifier.size(ACTION_SIZE)) {
        if (adding) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Icon(
                painter = painterResource(if (inAnki) R.drawable.ic_in_anki else R.drawable.ic_add_card),
                contentDescription = stringResource(if (inAnki) R.string.word_in_anki else R.string.action_add_to_anki),
                tint = if (inAnki) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Word actions are a little smaller than the usual 48 dp so the four fit beside long words. */
private val ACTION_SIZE = 40.dp

@Composable
private fun Sentence(sentence: AnnotatedString, source: String?, maxLines: Int) {
    Column(modifier = Modifier.padding(top = 6.dp)) {
        Text(
            text = sentence,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
        source?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "3 lookups · 2 days ago · 吾輩は猫である" */
@Composable
private fun details(word: WordEntity, now: Long): String {
    val count = pluralStringResource(R.plurals.words_lookup_count, word.lookupCount, word.lookupCount)
    val time = DateUtils.getRelativeTimeSpanString(
        word.lastLookedUpAt,
        now,
        DateUtils.MINUTE_IN_MILLIS,
    ).toString()
    return listOfNotNull(count, time, word.lastBookTitle).joinToString(" · ")
}

/** The sentence with the looked-up text emphasised. */
private fun sentenceText(sentence: String, start: Int, end: Int, color: Color): AnnotatedString {
    val from = start.coerceIn(0, sentence.length)
    val to = end.coerceIn(from, sentence.length)
    return buildAnnotatedString {
        append(sentence.substring(0, from))
        withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold)) {
            append(sentence.substring(from, to))
        }
        append(sentence.substring(to))
    }
}

@Preview
@Composable
private fun WordsPreview() {
    val now = 1_800_000_000_000L
    fun word(id: Long, expression: String, reading: String, gloss: String, count: Int, starred: Boolean = false) =
        WordEntity(
            id = id,
            expression = expression,
            reading = reading,
            gloss = gloss,
            starred = starred,
            lookupCount = count,
            firstLookedUpAt = now - 86_400_000L * 3,
            lastLookedUpAt = now - 3_600_000L,
            rankKey = 0.0,
            lastSentence = "吾輩は猫である。名前はまだ無い。",
            lastSurfaceStart = 8,
            lastSurfaceEnd = 10,
            lastBookTitle = "吾輩は猫である",
        )
    HongoTheme {
        WordsContent(
            uiState = WordsUiState(
                words = listOf(
                    word(1, "名前", "なまえ", "name; full name", 4, starred = true),
                    word(2, "見当", "けんとう", "estimate; guess; aim", 2),
                ),
                counts = WordCounts(learning = 2, starred = 1, learned = 40),
            ),
            now = now,
            snackbarHostState = remember { SnackbarHostState() },
            bottomBar = {},
            onFilter = {},
            onToggleExpanded = {},
            onSetStarred = { _, _ -> },
            onMarkLearned = {},
            onMarkLearning = {},
            onDelete = {},
            onAddToAnki = { _, _ -> },
            onAddAllToAnki = {},
            onExport = {},
        )
    }
}
