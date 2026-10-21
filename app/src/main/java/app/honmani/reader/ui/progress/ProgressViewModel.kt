package app.honmani.reader.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.honmani.reader.HonmaniApplication
import app.honmani.reader.data.library.BookEntity
import app.honmani.reader.data.stats.DayTotal
import app.honmani.reader.data.stats.Milestone
import app.honmani.reader.data.stats.MilestoneStore
import app.honmani.reader.data.stats.Progression
import app.honmani.reader.data.stats.ProgressionData
import app.honmani.reader.data.stats.ReadingDayEntity
import app.honmani.reader.data.stats.ReadingLevel
import app.honmani.reader.data.stats.ReadingStats
import app.honmani.reader.data.stats.StatsPeriod
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

/** Everything the progress screen shows. */
data class ProgressUiState(
    val level: ReadingLevel,
    /** Days in a row with reading, and days read in the last 7. */
    val streak: Int,
    val daysThisWeek: Int,
    /** Every milestone, in [Progression.TIERS] order. */
    val milestones: List<Milestone>,
    val next: List<Milestone>,
    val anyReading: Boolean,
)

class ProgressViewModel(
    private val days: Flow<List<ReadingDayEntity>>,
    private val books: Flow<List<BookEntity>>,
    private val wordsLearned: Flow<Int>,
    private val ankiCards: Flow<Int>,
    private val store: MilestoneStore,
    private val today: () -> LocalDate = LocalDate::now,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) : ViewModel() {
    val uiState: StateFlow<ProgressUiState?> =
        combine(days, books, wordsLearned, ankiCards, store.seen) { rows, books, learned, anki, seen ->
            val today = today()
            val zone = zone()
            val data = ProgressionData(
                days = rows.map { DayTotal(LocalDate.parse(it.day), it.seconds, it.characters) },
                finished = books.filter { it.finishedAt != null || it.progress >= 1f }.map { book ->
                    book.finishedAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
                },
                wordsLearned = learned,
                ankiCards = anki,
            )
            val milestones = Progression.milestones(data, seen, today)
            val week = ReadingStats.period(rows, today, StatsPeriod.WEEK)
            ProgressUiState(
                level = Progression.level(rows.sumOf { it.characters }),
                streak = week.streak,
                daysThisWeek = week.daysRead,
                milestones = milestones,
                next = Progression.next(milestones),
                anyReading = week.anyReading,
            )
        }
            .onEach { state -> store.record(state.milestones.filter { it.reached }.map { it.id }, today()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Milestones reached since the library last showed them, in [Progression.TIERS] order. */
    val pending: StateFlow<List<Milestone>> =
        combine(uiState, store.pending) { state, pending ->
            state?.milestones?.filter { it.id in pending }.orEmpty()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun shown(milestones: List<Milestone>) = store.shown(milestones.map { it.id })

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as HonmaniApplication).container
                ProgressViewModel(
                    days = container.readingDao.observeAll(),
                    books = container.bookDao.observeAll(),
                    wordsLearned = container.wordRepository.counts.map { it.learned },
                    ankiCards = container.wordRepository.ankiCards,
                    store = container.milestoneStore,
                )
            }
        }
    }
}
