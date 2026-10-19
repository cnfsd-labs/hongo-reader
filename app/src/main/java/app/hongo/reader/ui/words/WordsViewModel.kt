package app.hongo.reader.ui.words

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.hongo.reader.HongoApplication
import app.hongo.reader.anki.AnkiResult
import app.hongo.reader.anki.VoiceProblem
import app.hongo.reader.data.words.DeletedWord
import app.hongo.reader.data.words.WordCounts
import app.hongo.reader.data.words.WordEntity
import app.hongo.reader.data.words.WordLookupEntity
import app.hongo.reader.data.words.WordRepository
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class WordFilter {
    /** Words being learned, most looked up (recently) first. */
    LEARNING,

    /** Suggested for Anki: looked up more than once and not in Anki yet. */
    FOR_ANKI,
    STARRED,
    LEARNED,
}

data class WordsUiState(
    val filter: WordFilter = WordFilter.LEARNING,
    /** Null until loaded. */
    val words: List<WordEntity>? = null,
    val counts: WordCounts = WordCounts(),
    /** The word whose details are shown, if any. */
    val expanded: Long? = null,
    /** The expanded word's latest lookups, newest first. */
    val lookups: List<WordLookupEntity> = emptyList(),
    /** Words added so far and how many in all, while "Add all" or a package for Anki runs. */
    val ankiProgress: Pair<Int, Int>? = null,
    /** The progress is that of an Anki package being saved, not of "Add all". */
    val savingPackage: Boolean = false,
    /** Words whose card is being made right now. */
    val addingToAnki: Set<Long> = emptySet(),
)

/** How adding to Anki went: one word, or several from "Add all". */
sealed interface AnkiOutcome {
    data class One(val expression: String, val result: AnkiResult) : AnkiOutcome

    /**
     * "Add all" finished: [added] new cards; [stopped] is the result that ended it early, and
     * [audioProblem] why some cards went in without audio, if so.
     */
    data class Many(val added: Int, val stopped: AnkiResult? = null, val audioProblem: VoiceProblem? = null) :
        AnkiOutcome

    /**
     * [count] words were written to a file for Anki's import: a package with audio if
     * [packaged] ([audioProblem]: why some cards have none), else a text file.
     */
    data class Exported(val count: Int, val packaged: Boolean = false, val audioProblem: VoiceProblem? = null) :
        AnkiOutcome

    data object ExportFailed : AnkiOutcome
}

/** A change the user can undo from a snackbar. */
sealed interface WordChange {
    val expression: String

    data class Learned(val id: Long, override val expression: String) : WordChange

    data class Deleted(val deleted: DeletedWord) : WordChange {
        override val expression: String get() = deleted.word.expression
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class WordsViewModel(
    private val repository: WordRepository,
    /** Adds a word to Anki with the sentence of a lookup (null: the latest). */
    private val addCard: suspend (WordEntity, WordLookupEntity?) -> AnkiResult = { _, _ -> AnkiResult.Refused },
    /** The words as a text file for Anki's import. */
    private val ankiText: suspend (List<WordEntity>) -> String = { "" },
    /** Writes text to a document the reader picked (a content URI). */
    private val writeDocument: suspend (uri: String, text: String) -> Unit = { _, _ -> },
    /**
     * Writes the words as an Anki package with audio to a document the reader picked, telling
     * how many are done; returns why cards have no audio, if so.
     */
    private val writePackage: suspend (uri: String, List<WordEntity>, onProgress: (Int) -> Unit) -> VoiceProblem? =
        { _, _, _ -> null },
) : ViewModel() {
    private val filter = MutableStateFlow(WordFilter.LEARNING)
    private val expanded = MutableStateFlow<Long?>(null)
    private val _changes = Channel<WordChange>(Channel.BUFFERED)
    private val _ankiOutcomes = Channel<AnkiOutcome>(Channel.BUFFERED)
    private val ankiProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    private val savingPackage = MutableStateFlow(false)
    private val addingToAnki = MutableStateFlow<Set<Long>>(emptySet())

    /** Results of adding to Anki, to tell the reader. */
    val ankiOutcomes: Flow<AnkiOutcome> = _ankiOutcomes.receiveAsFlow()

    /** Changes to offer an undo for. */
    val changes: Flow<WordChange> = _changes.receiveAsFlow()

    private val words: Flow<List<WordEntity>> = filter.flatMapLatest {
        when (it) {
            WordFilter.LEARNING -> repository.learning
            WordFilter.FOR_ANKI -> repository.forAnki
            WordFilter.STARRED -> repository.starred
            WordFilter.LEARNED -> repository.learned
        }
    }

    private val lookups: Flow<List<WordLookupEntity>> = expanded.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.lookups(id)
    }

    val uiState: StateFlow<WordsUiState> =
        combine(filter, words, repository.counts, expanded, lookups) { filter, words, counts, expanded, lookups ->
            WordsUiState(filter, words, counts, expanded, if (expanded == null) emptyList() else lookups)
        }.combine(ankiProgress) { state, progress -> state.copy(ankiProgress = progress) }
            .combine(addingToAnki) { state, adding -> state.copy(addingToAnki = adding) }
            .combine(savingPackage) { state, saving -> state.copy(savingPackage = saving) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WordsUiState())

    fun setFilter(value: WordFilter) {
        filter.value = value
        expanded.value = null
    }

    /** Shows or hides the details of a word. */
    fun toggleExpanded(id: Long) {
        expanded.value = if (expanded.value == id) null else id
    }

    fun setStarred(id: Long, starred: Boolean) {
        viewModelScope.launch { repository.setStarred(id, starred) }
    }

    fun markLearned(word: WordEntity) {
        viewModelScope.launch {
            repository.markLearned(word.id)
            _changes.send(WordChange.Learned(word.id, word.expression))
        }
    }

    fun markLearning(id: Long) {
        viewModelScope.launch { repository.markLearning(id) }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            repository.delete(id)?.let { _changes.send(WordChange.Deleted(it)) }
        }
    }

    /** Adds [word] to Anki, with the sentence of [lookup] if given. */
    fun addToAnki(word: WordEntity, lookup: WordLookupEntity? = null) {
        if (word.id in addingToAnki.value) return
        addingToAnki.update { it + word.id }
        viewModelScope.launch {
            try {
                _ankiOutcomes.send(AnkiOutcome.One(word.expression, addCard(word, lookup)))
            } finally {
                addingToAnki.update { it - word.id }
            }
        }
    }

    /** Adds every suggested word to Anki, one after another; stops if AnkiDroid can't take them. */
    fun addAllToAnki() {
        if (ankiProgress.value != null) return
        viewModelScope.launch {
            val suggested = repository.forAnki.first()
            var added = 0
            var stopped: AnkiResult? = null
            var audioProblem: VoiceProblem? = null
            try {
                for ((i, word) in suggested.withIndex()) {
                    ankiProgress.value = i to suggested.size
                    when (val result = addCard(word, null)) {
                        is AnkiResult.Added -> {
                            added++
                            audioProblem = audioProblem ?: result.audioProblem
                        }
                        AnkiResult.AlreadyInAnki -> Unit
                        else -> {
                            stopped = result
                            break
                        }
                    }
                }
            } finally {
                ankiProgress.value = null
            }
            _ankiOutcomes.send(AnkiOutcome.Many(added, stopped, audioProblem))
        }
    }

    /** Writes the words of the current list to [uri] as a file for Anki's import. */
    fun exportToFile(uri: String) {
        viewModelScope.launch {
            val outcome = try {
                val list = words.first()
                writeDocument(uri, ankiText(list))
                AnkiOutcome.Exported(list.size)
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (_: Exception) {
                AnkiOutcome.ExportFailed
            }
            _ankiOutcomes.send(outcome)
        }
    }

    /** Writes the words of the current list to [uri] as an Anki package with audio. */
    fun exportPackage(uri: String) {
        if (ankiProgress.value != null) return
        viewModelScope.launch {
            val outcome = try {
                val list = words.first()
                savingPackage.value = true
                ankiProgress.value = 0 to list.size
                val problem = writePackage(uri, list) { done ->
                    ankiProgress.value = done.coerceAtMost(list.size - 1) to list.size
                }
                AnkiOutcome.Exported(list.size, packaged = true, audioProblem = problem)
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (_: Exception) {
                AnkiOutcome.ExportFailed
            } finally {
                ankiProgress.value = null
                savingPackage.value = false
            }
            _ankiOutcomes.send(outcome)
        }
    }

    fun undo(change: WordChange) {
        viewModelScope.launch {
            when (change) {
                is WordChange.Learned -> repository.markLearning(change.id)
                is WordChange.Deleted -> repository.restore(change.deleted)
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as HongoApplication).container
                val application = this[APPLICATION_KEY] as HongoApplication
                WordsViewModel(
                    repository = container.wordRepository,
                    addCard = { word, lookup -> container.ankiExporter.add(word, lookup) },
                    ankiText = { list -> container.ankiExporter.textFile(list) },
                    writeDocument = { uri, text ->
                        withContext(Dispatchers.IO) {
                            val out = application.contentResolver.openOutputStream(Uri.parse(uri), "wt")
                                ?: throw IOException("Cannot write $uri")
                            out.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                        }
                    },
                    writePackage = { uri, list, onProgress ->
                        val out = withContext(Dispatchers.IO) {
                            application.contentResolver.openOutputStream(Uri.parse(uri), "wt")
                        } ?: throw IOException("Cannot write $uri")
                        out.use { container.ankiExporter.ankiPackage(list, it, onProgress) }
                    },
                )
            }
        }
    }
}
