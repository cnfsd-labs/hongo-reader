package app.honmani.reader.data.stats

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SkillTest {
    private val today = LocalDate.of(2026, 10, 21)

    private fun day(daysAgo: Long, book: Long, seconds: Long, characters: Long) =
        ReadingDayEntity(book, today.minusDays(daysAgo).toString(), seconds, characters)

    private val grades = mapOf(1L to 50, 2L to 60)

    @Test
    fun readingAtTheReferenceSpeedCountsAsTheBooksDifficulty_doubleSpeedTenMore() {
        val estimate = Skill.estimate(
            listOf(day(1, 1, 1_000, 2_000), day(2, 1, 800, 2_000)),
            grades::get,
            today,
        )
        assertEquals(50, estimate.current)

        val faster = Skill.estimate(listOf(day(1, 2, 3_600, 16_000)), grades::get, today)
        assertEquals(70, faster.current)
    }

    @Test
    fun recentWeeksCountMore_andTheTrendShowsEachWeek() {
        val estimate = Skill.estimate(
            listOf(
                // This week: level 50 for half an hour.
                day(0, 1, 1_800, 4_000),
                // Two weeks ago: level 70 for an hour, weighed down by its age.
                day(15, 2, 3_600, 16_000),
            ),
            grades::get,
            today,
        )

        assertEquals(62, estimate.current)
        assertEquals(12, estimate.trend.size)
        assertEquals(today, estimate.trend.last().weekEnd)
        assertEquals(listOf(70, 70, 62), estimate.trend.takeLast(3).map { it.skill })
        assertNull(estimate.trend[8].skill)
    }

    @Test
    fun ungradedBooksAndShortReadingDontCount() {
        val estimate = Skill.estimate(
            listOf(day(0, 3, 7_200, 40_000), day(0, 1, 300, 2_000), day(10, 1, 400, 1_000)),
            grades::get,
            today,
        )

        assertEquals(700, estimate.gradedSeconds)
        // Neither week of book 1 reaches ten minutes, and the total is under half an hour.
        assertNull(estimate.current)
        assertEquals(listOf<Int?>(null), estimate.trend.map { it.skill }.distinct())
    }

    @Test
    fun booksThatFitAreAStretch_notTooFar() {
        val books = listOf(10L to 40, 11L to 47, 12L to 52, 13L to 58, 14L to 70)
        assertEquals(listOf(12L, 11L, 13L), Skill.fitting(books, skill = 50))
    }
}
