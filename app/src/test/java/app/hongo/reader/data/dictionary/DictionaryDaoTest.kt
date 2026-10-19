package app.hongo.reader.data.dictionary

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.hongo.reader.data.HongoDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DictionaryDaoTest {
    private lateinit var database: HongoDatabase
    private lateinit var dao: DictionaryDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            HongoDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.dictionaryDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun observeReady_ordersByPriorityThenTitle() = runTest {
        dao.insert(dictionary("Jitendex", priority = 1))
        dao.insert(dictionary("JMnedict", priority = 0))
        dao.insert(dictionary("JMdict", priority = 0))

        val titles = dao.observeReady().first().map { it.title }

        assertEquals(listOf("JMdict", "JMnedict", "Jitendex"), titles)
    }

    @Test
    fun setEnabled_updatesOnlyThatDictionary() = runTest {
        val id = dao.insert(dictionary("JMdict"))
        dao.insert(dictionary("Jitendex"))

        dao.setEnabled(id, enabled = false)

        val byTitle = dao.observeReady().first().associateBy { it.title }
        assertFalse(byTitle.getValue("JMdict").enabled)
        assertTrue(byTitle.getValue("Jitendex").enabled)
    }

    @Test
    fun delete_removesDictionary() = runTest {
        val id = dao.insert(dictionary("JMdict"))

        dao.delete(id)

        assertTrue(dao.observeReady().first().isEmpty())
    }

    @Test
    fun observeReady_hidesImportsInProgress_andDeleteIncompleteRemovesThem() = runTest {
        dao.insert(dictionary("JMdict"))
        dao.insert(dictionary("Jitendex").copy(status = DictionaryEntity.Status.IMPORTING))

        assertEquals(listOf("JMdict"), dao.observeReady().first().map { it.title })

        dao.deleteIncomplete()

        assertEquals(null, dao.findByTitle("Jitendex"))
        assertEquals("JMdict", dao.findByTitle("JMdict")?.title)
    }

    @Test
    fun delete_cascadesToTermsAndTags() = runTest {
        val id = dao.insert(dictionary("JMdict"))
        database.termDao().insertAll(
            listOf(
                TermEntity(
                    dictionaryId = id,
                    expression = "猫",
                    reading = "ねこ",
                    definitionTags = "n",
                    rules = "",
                    score = 0.0,
                    glossary = Glossary.plain("[\"cat\"]"),
                    sequence = 1,
                    termTags = null,
                ),
            ),
        )
        database.tagDao().insertAll(listOf(TagEntity(id, "n", "partOfSpeech", 0.0, "noun", 0.0)))
        database.kanjiDao().insertAll(listOf(KanjiEntity(0, id, "猫", "ビョウ", "ねこ", "", "[\"cat\"]", "{}")))
        database.termDao().insertPreset(GlossaryPresetEntity(id, byteArrayOf(1, 2, 3)))
        assertEquals(3, database.termDao().glossaryPreset(id)?.size)

        dao.delete(id)

        assertEquals(null, database.termDao().glossaryPreset(id))

        assertEquals(0, database.termDao().countForDictionary(id))
        assertTrue(database.tagDao().allForDictionary(id).isEmpty())
        assertTrue(database.kanjiDao().allForDictionary(id).isEmpty())
    }

    private fun dictionary(title: String, priority: Int = 0) = DictionaryEntity(
        title = title,
        revision = "1",
        formatVersion = 3,
        priority = priority,
        importedAt = 0,
    )
}
