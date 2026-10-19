package app.hongo.reader.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Users' data must survive updates: a database made from each exported schema (`app/schemas`)
 * opens with the current code, through [HongoDatabase.MIGRATIONS], and keeps its rows.
 *
 * Changing the schema: raise the version in [HongoDatabase], build once to export the new
 * schema, add the migration to [HongoDatabase.MIGRATIONS] and a test here that creates the old
 * version, fills it, runs `helper.runMigrationsAndValidate` and reads the rows back.
 */
@RunWith(RobolectricTestRunner::class)
class DatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), HongoDatabase::class.java)

    @Test
    fun aVersionOneDatabaseOpensWithTheCurrentCode_andKeepsItsData() = runTest {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                "INSERT INTO word (expression, reading, gloss, status, starred, lookup_count, first_looked_up_at, " +
                    "last_looked_up_at, rank_key, last_sentence, last_surface_start, last_surface_end) " +
                    "VALUES ('猫', 'ねこ', 'cat', 'LEARNING', 1, 3, 1000, 2000, 1.5, '猫がいる。', 0, 1)",
            )
            db.execSQL("INSERT INTO lookup_event (looked_up_at, book_id) VALUES (2000, NULL)")
        }

        val database = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            HongoDatabase::class.java,
            TEST_DB,
        ).addMigrations(*HongoDatabase.MIGRATIONS).build()
        try {
            val word = database.wordDao().find("猫", "ねこ")!!
            assertEquals(3, word.lookupCount)
            assertEquals(true, word.starred)
            assertEquals(1, database.wordDao().countLookups().first())
        } finally {
            database.close()
        }
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
