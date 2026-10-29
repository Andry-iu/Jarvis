package it.jarviswake

import org.junit.Assert.assertEquals
import org.junit.Test

class CoverAnimationClockTest {
    @Test fun muteFreezesEveryAnimationPhaseAndResumeHasNoTimeJump() {
        val clock = CoverAnimationClock(1000)
        assertEquals(1f, clock.seconds(2000), 0f)
        clock.setMuted(true, 2500)
        assertEquals(1.5f, clock.seconds(9000), 0f)
        clock.setMuted(true, 9500) // Repeated confirmed state must not move the frozen frame.
        assertEquals(1.5f, clock.seconds(10000), 0f)
        clock.setMuted(false, 11000)
        assertEquals(1.5f, clock.seconds(11000), 0f)
        assertEquals(2f, clock.seconds(11500), 0f)
    }
    @Test fun repeatedUnmuteAndMultipleMuteIntervalsDoNotChangeElapsedTime() {
        val clock = CoverAnimationClock(0)
        clock.setMuted(false, 500)
        clock.setMuted(true, 1000)
        clock.setMuted(false, 3000)
        clock.setMuted(false, 3500)
        clock.setMuted(true, 4000)
        assertEquals(2f, clock.seconds(8000), 0f)
        clock.setMuted(false, 9000)
        assertEquals(3f, clock.seconds(10000), 0f)
    }
}
