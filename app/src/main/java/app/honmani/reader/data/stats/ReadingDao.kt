package app.honmani.reader.data.stats

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingDao {
    /** Creates the row for a book and day if it doesn't exist yet. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfMissing(day: ReadingDayEntity)

    @Query(
        "UPDATE reading_day SET seconds = seconds + :seconds, characters = characters + :characters " +
            "WHERE book_id = :bookId AND day = :day",
    )
    suspend fun add(bookId: Long, day: String, seconds: Long, characters: Long)

    @Query("SELECT * FROM reading_day ORDER BY day")
    fun observeAll(): Flow<List<ReadingDayEntity>>

    @Query("SELECT * FROM reading_day WHERE book_id = :bookId ORDER BY day")
    fun observeBook(bookId: Long): Flow<List<ReadingDayEntity>>
}
