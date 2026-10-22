package app.honmani.reader.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import app.honmani.reader.data.backup.BackupDao
import app.honmani.reader.data.difficulty.DifficultyDao
import app.honmani.reader.data.dictionary.DictionaryDao
import app.honmani.reader.data.dictionary.DictionaryEntity
import app.honmani.reader.data.dictionary.GlossaryConverters
import app.honmani.reader.data.dictionary.GlossaryPresetEntity
import app.honmani.reader.data.dictionary.KanjiDao
import app.honmani.reader.data.dictionary.KanjiEntity
import app.honmani.reader.data.dictionary.TagDao
import app.honmani.reader.data.dictionary.TagEntity
import app.honmani.reader.data.dictionary.TermMetaDao
import app.honmani.reader.data.dictionary.TermMetaEntity
import app.honmani.reader.data.dictionary.TermDao
import app.honmani.reader.data.dictionary.TermEntity
import app.honmani.reader.data.library.BookDao
import app.honmani.reader.data.library.BookEntity
import app.honmani.reader.data.library.RemovedBookEntity
import app.honmani.reader.data.opds.OpdsCatalogDao
import app.honmani.reader.data.opds.OpdsCatalogEntity
import app.honmani.reader.data.stats.ReadingDao
import app.honmani.reader.data.stats.ReadingDayEntity
import app.honmani.reader.data.words.WordDao
import app.honmani.reader.data.words.WordEntity
import app.honmani.reader.data.words.LookupEventEntity
import app.honmani.reader.data.words.WordLookupEntity

// Frozen since 2026-10-19 (Play closed test): every change to the schema needs a new version, a
// migration in MIGRATIONS and a test in DatabaseMigrationTest. Users' data must survive updates.
@Database(
    entities = [
        DictionaryEntity::class,
        TermEntity::class,
        TagEntity::class,
        TermMetaEntity::class,
        KanjiEntity::class,
        BookEntity::class,
        WordEntity::class,
        WordLookupEntity::class,
        LookupEventEntity::class,
        OpdsCatalogEntity::class,
        RemovedBookEntity::class,
        ReadingDayEntity::class,
        GlossaryPresetEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(GlossaryConverters::class)
abstract class HonmaniDatabase : RoomDatabase() {
    abstract fun dictionaryDao(): DictionaryDao

    abstract fun termDao(): TermDao

    abstract fun tagDao(): TagDao

    abstract fun termMetaDao(): TermMetaDao

    abstract fun kanjiDao(): KanjiDao

    abstract fun bookDao(): BookDao

    abstract fun wordDao(): WordDao

    abstract fun opdsCatalogDao(): OpdsCatalogDao

    abstract fun readingDao(): ReadingDao

    /** Reads the dictionary tables for book grading; no tables of its own. */
    abstract fun difficultyDao(): DifficultyDao

    /** Whole tables for backups; no tables of its own. */
    abstract fun backupDao(): BackupDao

    companion object {
        internal const val NAME = "honmani.db"

        /** From one schema version to the next, oldest first; see `app/schemas` for each version. */
        val MIGRATIONS: Array<Migration> = emptyArray()

        fun create(context: Context): HonmaniDatabase =
            Room.databaseBuilder(context.applicationContext, HonmaniDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
