package app.hongo.reader.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import app.hongo.reader.data.dictionary.DictionaryDao
import app.hongo.reader.data.dictionary.DictionaryEntity
import app.hongo.reader.data.dictionary.GlossaryConverters
import app.hongo.reader.data.dictionary.GlossaryPresetEntity
import app.hongo.reader.data.dictionary.KanjiDao
import app.hongo.reader.data.dictionary.KanjiEntity
import app.hongo.reader.data.dictionary.TagDao
import app.hongo.reader.data.dictionary.TagEntity
import app.hongo.reader.data.dictionary.TermMetaDao
import app.hongo.reader.data.dictionary.TermMetaEntity
import app.hongo.reader.data.dictionary.TermDao
import app.hongo.reader.data.dictionary.TermEntity
import app.hongo.reader.data.library.BookDao
import app.hongo.reader.data.library.BookEntity
import app.hongo.reader.data.library.RemovedBookEntity
import app.hongo.reader.data.opds.OpdsCatalogDao
import app.hongo.reader.data.opds.OpdsCatalogEntity
import app.hongo.reader.data.stats.ReadingDao
import app.hongo.reader.data.stats.ReadingDayEntity
import app.hongo.reader.data.words.WordDao
import app.hongo.reader.data.words.WordEntity
import app.hongo.reader.data.words.LookupEventEntity
import app.hongo.reader.data.words.WordLookupEntity

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
abstract class HongoDatabase : RoomDatabase() {
    abstract fun dictionaryDao(): DictionaryDao

    abstract fun termDao(): TermDao

    abstract fun tagDao(): TagDao

    abstract fun termMetaDao(): TermMetaDao

    abstract fun kanjiDao(): KanjiDao

    abstract fun bookDao(): BookDao

    abstract fun wordDao(): WordDao

    abstract fun opdsCatalogDao(): OpdsCatalogDao

    abstract fun readingDao(): ReadingDao

    companion object {
        internal const val NAME = "hongo.db"

        /** From one schema version to the next, oldest first; see `app/schemas` for each version. */
        val MIGRATIONS: Array<Migration> = emptyArray()

        fun create(context: Context): HongoDatabase =
            Room.databaseBuilder(context.applicationContext, HongoDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
