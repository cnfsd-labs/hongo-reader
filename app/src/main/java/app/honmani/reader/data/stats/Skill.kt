package app.honmani.reader.data.stats

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/** The reader's skill on a week's last day, on the books' 1–100 difficulty scale; null before any measurement. */
data class SkillPoint(val weekEnd: LocalDate, val skill: Int?)

data class SkillEstimate(
    /** Today's skill; null while there is too little reading in graded books ([Skill.MIN_SECONDS]). */
    val current: Int?,
    /** One point per week, oldest first, the last ending today. */
    val trend: List<SkillPoint>,
    /** Time read in books that have a difficulty. */
    val gradedSeconds: Long,
)

/**
 * How hard a book the reader reads comfortably, from their reading speed in books of known
 * difficulty: a week in a book counts as the book's difficulty, plus [POINTS_PER_DOUBLING] for
 * every doubling of speed above [REFERENCE_SPEED] (minus below it). Reading a level-50 book at
 * 8,000 characters an hour counts as 50, at 16,000 as 60. Recent weeks count more (half-life
 * [HALF_LIFE_DAYS] days), and longer reading more than shorter. Speed depends on the screen and
 * the text direction, so this compares a reader only with themselves.
 */
object Skill {
    const val REFERENCE_SPEED = 8_000.0
    const val POINTS_PER_DOUBLING = 10.0
    const val HALF_LIFE_DAYS = 30.0

    /** Half an hour in graded books before a skill is shown. */
    const val MIN_SECONDS = 1_800L

    /** A book-week counts from ten minutes, like the reading speed in the statistics. */
    private const val MIN_WEEK_SECONDS = 600L

    private class Measurement(val weeksAgo: Int, val level: Double, val seconds: Long)

    /** [difficulty] gives a book's score (1–100), or null for books not graded. */
    fun estimate(rows: List<ReadingDayEntity>, difficulty: (bookId: Long) -> Int?, today: LocalDate, weeks: Int = 12): SkillEstimate {
        var gradedSeconds = 0L
        val measurements = rows
            .mapNotNull { row ->
                val score = difficulty(row.bookId) ?: return@mapNotNull null
                val daysAgo = ChronoUnit.DAYS.between(LocalDate.parse(row.day), today)
                if (daysAgo < 0) return@mapNotNull null
                gradedSeconds += row.seconds
                Triple(row.bookId to (daysAgo / 7).toInt(), score, row)
            }
            .groupBy({ it.first }, { it.second to it.third })
            .mapNotNull { (key, list) ->
                val seconds = list.sumOf { it.second.seconds }
                val characters = list.sumOf { it.second.characters }
                if (seconds < MIN_WEEK_SECONDS || characters <= 0) return@mapNotNull null
                val speed = characters * 3_600.0 / seconds
                val level = list.first().first + POINTS_PER_DOUBLING * ln(speed / REFERENCE_SPEED) / ln(2.0)
                Measurement(key.second, level.coerceIn(1.0, 100.0), seconds)
            }

        fun skillAt(weeksAgo: Int): Int? {
            var weighted = 0.0
            var weights = 0.0
            for (m in measurements) {
                if (m.weeksAgo < weeksAgo) continue
                val weight = m.seconds * 0.5.pow((m.weeksAgo - weeksAgo) * 7 / HALF_LIFE_DAYS)
                weighted += m.level * weight
                weights += weight
            }
            return if (weights > 0) (weighted / weights).roundToInt() else null
        }

        return SkillEstimate(
            current = if (gradedSeconds >= MIN_SECONDS) skillAt(0) else null,
            trend = (weeks - 1 downTo 0).map { SkillPoint(today.minusDays(7L * it), skillAt(it)) },
            gradedSeconds = gradedSeconds,
        )
    }

    /**
     * Up to [count] of [books] (id to difficulty) that fit a reader of [skill]: from a little
     * easier to a little harder, the best stretch first.
     */
    fun fitting(books: List<Pair<Long, Int>>, skill: Int, count: Int = 3): List<Long> =
        books.filter { (_, score) -> score in skill - 5..skill + 8 }
            .sortedBy { (_, score) -> kotlin.math.abs(score - (skill + 2)) }
            .take(count)
            .map { it.first }
}
