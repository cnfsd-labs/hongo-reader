package app.honmani.reader.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.honmani.reader.HonmaniApplication
import app.honmani.reader.data.library.BookEntity
import app.honmani.reader.data.stats.BookReading
import app.honmani.reader.data.stats.ReadingDayEntity
import app.honmani.reader.data.stats.ReadingStats
import app.honmani.reader.data.stats.PeriodSummary
import app.honmani.reader.data.stats.StatsPeriod
import app.honmani.reader.data.words.WordCounts
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Everything the statistics screen shows, for the period picked. */
data class StatisticsUiState(
    val summary: PeriodSummary,
    /** Books finished in the period. */
    val booksFinished: Int = 0,
    /** Lookups in the period. */
    val lookups: Int = 0,
    val words: WordCounts = WordCounts(),
    /** Titles of the books in [PeriodSummary.books], by book id. */
    val titles: Map<Long, String> = emptyMap(),
)

/** One book's statistics, for its sheet in the library. */
data class BookStatistics(
    val reading: BookReading,
    val lookups: Int,
    /** Characters read up to the reading position, and in the whole book if it can be told. */
    val position: Long,
    val bookCharacters: Long?,
    val progress: Float,
    val finishedAt: Long?,
)

@OptIn(ExperimentalCoroutinesApi::class)
class StatisticsViewModel(
    private val days: Flow<List<ReadingDayEntity>>,
    private val bookDays: (bookId: Long) -> Flow<List<ReadingDayEntity>>,
    private val books: Flow<List<BookEntity>>,
    /** Lookups since a time (epoch milliseconds; null: ever). */
    private val lookups: (from: Long?) -> Flow<Int>,
    private val bookLookups: (bookId: Long) -> Flow<Int>,
    private val words: Flow<WordCounts>,
    private val today: () -> LocalDate = LocalDate::now,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) : ViewModel() {
    private val _period = MutableStateFlow(StatsPeriod.TODAY)
    val period: StateFlow<StatsPeriod> = _period.asStateFlow()

    /** The period with its lookups, counted in the database. */
    private val periodLookups: Flow<Pair<StatsPeriod, Int>> = _period.flatMapLatest { period ->
        val from = firstDay(period, today())?.atStartOfDay(zone())?.toInstant()?.toEpochMilli()
        lookups(from).map { period to it }
    }

    val uiState: StateFlow<StatisticsUiState?> =
        combine(days, books, periodLookups, words) { days, books, (period, lookups), words ->
            val today = today()
            val zone = zone()
            val from = firstDay(period, today)
            fun inPeriod(millis: Long) = from == null || !Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().isBefore(from)
            StatisticsUiState(
                summary = ReadingStats.period(days, today, period),
                booksFinished = books.count { book ->
                    val finishedAt = book.finishedAt
                    if (finishedAt != null) inPeriod(finishedAt) else from == null && book.progress >= 1f
                },
                lookups = lookups,
                words = words,
                titles = books.associate { it.id to it.title },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun firstDay(period: StatsPeriod, today: LocalDate): LocalDate? = when (period) {
        StatsPeriod.TODAY -> today
        StatsPeriod.WEEK -> today.minusDays(6)
        StatsPeriod.ALL -> null
    }

    fun setPeriod(period: StatsPeriod) {
        _period.value = period
    }

    /** Statistics of book [bookId]; nothing while the book isn't in the library. */
    fun book(bookId: Long): Flow<BookStatistics?> =
        combine(bookDays(bookId), books, bookLookups(bookId)) { days, books, lookups ->
            val book = books.firstOrNull { it.id == bookId } ?: return@combine null
            BookStatistics(
                reading = ReadingStats.book(days),
                lookups = lookups,
                position = book.characters,
                // ttu's progress is the share of characters read, so the whole book follows.
                bookCharacters = if (book.progress > 0.01f && book.characters > 0) {
                    (book.characters / book.progress).toLong()
                } else {
                    null
                },
                progress = book.progress,
                finishedAt = book.finishedAt,
            )
        }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as HonmaniApplication).container
                StatisticsViewModel(
                    days = container.readingDao.observeAll(),
                    bookDays = container.readingDao::observeBook,
                    books = container.bookDao.observeAll(),
                    lookups = container.wordRepository::lookupCount,
                    bookLookups = container.wordRepository::bookLookupCount,
                    words = container.wordRepository.counts,
                )
            }
        }
    }
}
