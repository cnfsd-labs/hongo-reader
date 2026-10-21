package app.honmani.reader.data.stats

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressionTest {
    private val today = LocalDate.of(2026, 10, 21)

    private fun data(
        days: List<DayTotal> = emptyList(),
        finished: List<LocalDate?> = emptyList(),
        wordsLearned: Int = 0,
        ankiCards: Int = 0,
    ) = ProgressionData(days, finished, wordsLearned, ankiCards)

    private fun List<Milestone>.of(kind: MilestoneKind, goal: Long) = single { it.kind == kind && it.goal == goal }

    @Test
    fun levelFollowsTheCurve() {
        assertEquals(1, Progression.level(0).level)
        assertEquals(1, Progression.level(1_999).level)
        assertEquals(2, Progression.level(2_000).level)
        assertEquals(10, Progression.level(162_000).level)
        assertEquals(9, Progression.level(161_999).level)
        assertEquals(25, Progression.level(1_152_000).level)
        assertEquals(50, Progression.level(4_802_000).level)
        assertEquals(100, Progression.level(19_602_000).level)
    }

    @Test
    fun levelKnowsHowFarToTheNext() {
        val level = Progression.level(312_450)

        assertEquals(13, level.level)
        assertEquals(288_000, level.levelStart)
        assertEquals(338_000, level.nextLevelAt)
        assertEquals(0.489f, level.fraction, 0.001f)
    }

    @Test
    fun charactersAreDatedByTheDayTheTotalPassedTheGoal() {
        val days = listOf(
            DayTotal(today.minusDays(3), 3_600, 6_000),
            // Two books on one day come as two entries.
            DayTotal(today.minusDays(1), 600, 3_000),
            DayTotal(today.minusDays(1), 600, 2_000),
        )

        val milestones = Progression.milestones(data(days), emptyMap(), today)

        val first = milestones.of(MilestoneKind.CHARACTERS, 10_000)
        assertTrue(first.reached)
        assertEquals(today.minusDays(1), first.reachedOn)
        assertEquals(11_000, first.current)
        assertFalse(milestones.of(MilestoneKind.CHARACTERS, 100_000).reached)
        assertNull(milestones.of(MilestoneKind.CHARACTERS, 100_000).reachedOn)
        assertEquals(today.minusDays(3), milestones.of(MilestoneKind.HOURS, 1).reachedOn)
    }

    @Test
    fun daysReadCountOnlyRealReadingDays() {
        val days = (0L until 8).map { DayTotal(today.minusDays(10 - it), 600, 0) } +
            // Less than a minute and no characters: not a reading day.
            DayTotal(today.minusDays(1), 30, 0)

        val week = Progression.milestones(data(days), emptyMap(), today).of(MilestoneKind.DAYS, 7)

        assertEquals(8, week.current)
        assertEquals(today.minusDays(4), week.reachedOn)
    }

    @Test
    fun booksAreDatedOnlyWhenEveryFinishedBookHasADate() {
        val dated = Progression.milestones(data(finished = listOf(today.minusDays(2), today.minusDays(9))), emptyMap(), today)
        assertEquals(today.minusDays(9), dated.of(MilestoneKind.BOOKS, 1).reachedOn)

        val seen = mapOf("books_1" to today.minusDays(5))
        val undated = Progression.milestones(data(finished = listOf(today.minusDays(2), null)), seen, today)
        assertEquals(2, undated.of(MilestoneKind.BOOKS, 1).current)
        assertEquals(today.minusDays(5), undated.of(MilestoneKind.BOOKS, 1).reachedOn)
    }

    @Test
    fun undatedMilestonesTakeTheDayFirstSeen() {
        val seen = mapOf("words_learned_10" to today.minusDays(3))

        val milestones = Progression.milestones(data(wordsLearned = 120, ankiCards = 1), seen, today)

        assertEquals(today.minusDays(3), milestones.of(MilestoneKind.WORDS_LEARNED, 10).reachedOn)
        // Not seen yet: reached today.
        assertEquals(today, milestones.of(MilestoneKind.WORDS_LEARNED, 100).reachedOn)
        assertEquals(today, milestones.of(MilestoneKind.ANKI, 1).reachedOn)
        assertFalse(milestones.of(MilestoneKind.WORDS_LEARNED, 500).reached)
    }

    @Test
    fun speedCountsTwoWeeksAndNeedsTenMinutes() {
        val start = today.minusDays(30)
        val days = listOf(
            // 5 minutes at 12,000 characters/hour: too little to tell on its own.
            DayTotal(start, 300, 1_000),
            // Over the 14 days ending here: 1,800 s and 5,000 characters, 10,000 per hour.
            DayTotal(start.plusDays(10), 1_500, 4_000),
            // Both earlier days have left the window: 900 s and 4,000 characters, 16,000 per hour.
            DayTotal(start.plusDays(25), 900, 4_000),
        )

        val milestones = Progression.milestones(data(days), emptyMap(), today)

        assertEquals(16_000, milestones.of(MilestoneKind.SPEED, 5_000).current)
        assertEquals(start.plusDays(10), milestones.of(MilestoneKind.SPEED, 5_000).reachedOn)
        assertEquals(start.plusDays(10), milestones.of(MilestoneKind.SPEED, 10_000).reachedOn)
        assertEquals(start.plusDays(25), milestones.of(MilestoneKind.SPEED, 15_000).reachedOn)
        assertFalse(milestones.of(MilestoneKind.SPEED, 20_000).reached)
    }

    @Test
    fun nextTakesTheLowestOpenGoalOfEachKind_closestFirst() {
        val days = listOf(DayTotal(today, 3 * 3_600, 90_000))

        val next = Progression.next(Progression.milestones(data(days, wordsLearned = 2), emptyMap(), today))

        assertEquals(
            listOf(MilestoneKind.CHARACTERS to 100_000L, MilestoneKind.HOURS to 10L, MilestoneKind.WORDS_LEARNED to 10L),
            next.map { it.kind to it.goal },
        )
    }
}
