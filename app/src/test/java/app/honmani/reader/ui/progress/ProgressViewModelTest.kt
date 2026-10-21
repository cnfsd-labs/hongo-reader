package app.honmani.reader.ui.progress

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.honmani.reader.data.library.BookEntity
import app.honmani.reader.data.stats.MilestoneStore
import app.honmani.reader.data.stats.ReadingDayEntity
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ProgressViewModelTest {
    private val today = LocalDate.of(2026, 10, 21)
    private val days = MutableStateFlow(listOf(ReadingDayEntity(1, today.minusDays(1).toString(), 3_600, 20_000)))
    private val store = MilestoneStore(
        ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("progress-test", Context.MODE_PRIVATE),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = ProgressViewModel(
        days = days,
        books = flowOf(emptyList<BookEntity>()),
        wordsLearned = flowOf(0),
        ankiCards = flowOf(0),
        store = store,
        today = { today },
        zone = { ZoneId.of("Asia/Tokyo") },
    )

    @Test
    fun onlyMilestonesReachedAfterTheFirstLookAreNews() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.pending.collect {} }

        assertEquals(4, viewModel.uiState.value?.level?.level)
        assertEquals(emptyList<String>(), viewModel.pending.value.map { it.id })

        days.value += ReadingDayEntity(1, today.toString(), 600, 90_000)

        assertEquals(listOf("characters_100000"), viewModel.pending.value.map { it.id })
        viewModel.shown(viewModel.pending.value)
        assertEquals(emptyList<String>(), viewModel.pending.value.map { it.id })
    }
}
