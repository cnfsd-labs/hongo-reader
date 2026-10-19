package app.hongo.reader.data.stats

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.hongo.reader.data.HongoDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReadingDaoTest {
    private lateinit var database: HongoDatabase
    private lateinit var dao: ReadingDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            HongoDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.readingDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun add(book: Long, day: String, seconds: Long, characters: Long) {
        dao.insertIfMissing(ReadingDayEntity(book, day))
        dao.add(book, day, seconds, characters)
    }

    @Test
    fun readingAddsUpPerBookAndDay() = runTest {
        add(1, "2026-10-17", 60, 0)
        add(1, "2026-10-17", 30, 500)
        add(1, "2026-10-16", 120, 1_000)
        add(2, "2026-10-17", 10, 0)

        assertEquals(
            listOf(ReadingDayEntity(1, "2026-10-16", 120, 1_000), ReadingDayEntity(1, "2026-10-17", 90, 500)),
            dao.observeBook(1).first(),
        )
        assertEquals(3, dao.observeAll().first().size)
    }
}
