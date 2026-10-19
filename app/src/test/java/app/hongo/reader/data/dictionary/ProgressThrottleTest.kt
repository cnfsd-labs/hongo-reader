package app.hongo.reader.data.dictionary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressThrottleTest {
    private var now = 0L
    private val throttle = ProgressThrottle(intervalMillis = 1_000, clock = { now })

    @Test
    fun firstUpdateIsPosted() {
        assertTrue(throttle.shouldPost("download", 0))
    }

    @Test
    fun fastChangesArePostedAtMostOncePerInterval() {
        throttle.shouldPost("download", 0)
        // A small dictionary going from 1 to 100 % within half a second.
        val posted = (1..100).count { percent ->
            now += 5
            throttle.shouldPost("download", percent)
        }
        assertEquals(0, posted)

        now += 1_000
        assertTrue(throttle.shouldPost("download", 100))
    }

    @Test
    fun unchangedValueIsNotPostedAgain() {
        throttle.shouldPost("download", 40)
        now += 5_000
        assertFalse(throttle.shouldPost("download", 40))
    }

    @Test
    fun newStageIsPostedAtOnce() {
        throttle.shouldPost("download", 100)
        now += 5
        assertTrue(throttle.shouldPost("Jitendex", 0))
        now += 5
        assertFalse(throttle.shouldPost("Jitendex", 1))
    }
}
