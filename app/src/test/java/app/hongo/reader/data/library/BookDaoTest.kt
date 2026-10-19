package app.hongo.reader.data.library

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.hongo.reader.data.HongoDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BookDaoTest {
    private lateinit var database: HongoDatabase
    private lateinit var dao: BookDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            HongoDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.bookDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun book(hash: String, addedAt: Long) = BookEntity(
        contentHash = hash,
        title = hash,
        format = BookFormat.EPUB,
        fileName = "$hash.epub",
        addedAt = addedAt,
    )

    @Test
    fun observeAll_recentlyOpenedFirst_thenRecentlyAdded() = runTest {
        val old = dao.insert(book("old", addedAt = 1))
        val new = dao.insert(book("new", addedAt = 2))
        val opened = dao.insert(book("opened", addedAt = 0))
        dao.setLastOpened(opened, 5)

        assertEquals(listOf(opened, new, old), dao.observeAll().first().map { it.id })
    }

    @Test(expected = SQLiteConstraintException::class)
    fun contentHashIsUnique() = runTest {
        dao.insert(book("same", addedAt = 1))
        dao.insert(book("same", addedAt = 2))
    }

    @Test
    fun progressIsSetByTtuId() = runTest {
        val id = dao.insert(book("a", addedAt = 1))
        dao.setTtuBook(id, ttuId = 7, ttuTitle = "A")

        dao.setProgressByTtuId(7, 0.5f)

        assertEquals(0.5f, dao.find(id)!!.progress)
    }

    @Test
    fun forgetTtuBookExcept_clearsOtherBooksWithThatTtuId() = runTest {
        val a = dao.insert(book("a", addedAt = 1))
        val b = dao.insert(book("b", addedAt = 2))
        dao.setTtuBook(a, ttuId = 3, ttuTitle = "same title")
        dao.setTtuBook(b, ttuId = 3, ttuTitle = "same title")

        dao.forgetTtuBookExcept(ttuId = 3, keepId = b)

        assertNull(dao.find(a)!!.ttuId)
        assertNull(dao.find(a)!!.ttuTitle)
        assertEquals(3L, dao.find(b)!!.ttuId)
    }
}
