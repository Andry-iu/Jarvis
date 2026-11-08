package it.jarviswake

import org.junit.Assert.*
import org.junit.Test

class CarCubeProbeStateTest {
    @Test fun noAutomaticPermissionAtStartup() {
        val s = CarCubeProbeState()
        assertEquals("not_armed", s.consume("cycle", 100, true, true, true))
        assertFalse(s.accepts("cycle", 0, 1, 100))
    }

    @Test fun temporaryArmExpiresExactlyAndRejectsClockRollback() {
        val s = CarCubeProbeState(); s.arm(2, 100)
        assertFalse(s.armed(99))
        assertTrue(s.armed(60_099))
        assertFalse(s.armed(60_100))
        assertEquals("not_armed", s.consume("c", 60_100, true, true, true))
    }

    @Test fun rejectedLaunchCannotBeReplayedOnReconnectOrUnlock() {
        for (reason in listOf("no_projection", "phone_locked", "display_denied")) {
            val s = CarCubeProbeState(); s.arm(2, 100)
            assertEquals(reason, s.consume("c", 101, reason != "no_projection",
                reason != "phone_locked", reason != "display_denied"))
            assertEquals("already_attempted", s.consume("c", 102, true, true, true))
            assertFalse(s.accepts("c", s.generation, 2, 102))
        }
    }

    @Test fun dispatchIsNotAFrameAndOnlyCorrectDisplayCanAcknowledge() {
        val s = CarCubeProbeState(); s.arm(2, 100)
        assertEquals("dispatch", s.consume("c", 101, true, true, true))
        assertFalse(s.rendered)
        assertFalse(s.frame("c", s.generation, 0, 102)) // Phone fallback never counts.
        assertFalse(s.frame("other", s.generation, 2, 102))
        assertFalse(s.frame("c", s.generation - 1, 2, 102))
        assertTrue(s.frame("c", s.generation, 2, 102))
        assertFalse(s.frame("c", s.generation, 2, 103))
    }

    @Test fun userExitAndPhoneCoverExitNeverReopenSameCycle() {
        val s = CarCubeProbeState(); s.arm(2, 100)
        s.consume("c", 101, true, true, true); s.closeCycle()
        assertFalse(s.frame("c", s.generation, 2, 102))
        assertEquals("already_attempted", s.consume("c", 103, true, true, true))
        assertEquals("dispatch", s.consume("new-cycle", 104, true, true, true))
    }

    @Test fun disarmOrRearmInvalidatesLateActivityAndFrame() {
        val s = CarCubeProbeState(); s.arm(2, 100)
        s.consume("c", 101, true, true, true)
        val old = s.generation
        s.disarm()
        assertFalse(s.accepts("c", old, 2, 102))
        s.arm(3, 103); s.consume("c", 104, true, true, true)
        assertFalse(s.accepts("c", old, 2, 105))
        assertTrue(s.accepts("c", s.generation, 3, 105))
        assertFalse(s.frame("c", s.generation, 3, 60_103))
    }

    @Test(expected = IllegalArgumentException::class) fun phoneDisplayCannotBeArmed() {
        CarCubeProbeState().arm(0, 100)
    }
}
