package it.jarviswake

import org.junit.Assert.*
import org.junit.Test

class ConfirmedMicrophoneTargetTest {
    @Test fun releaseBeforeUnmuteObservationRestoresMuteWithoutAnotherUnmuteClick() {
        val target=ConfirmedMicrophoneTarget()
        target.request(false)
        assertEquals(false,target.next(true))
        target.request(true) // The finger is already up, but the UI still reports the old mute.
        repeat(5) { assertNull(target.next(true)) }
        assertEquals(true,target.next(false)) // The delayed unmute is finally observed: restore once.
        repeat(5) { assertNull(target.next(false)) }
        assertNull(target.next(true))
        assertFalse(target.busy)
    }
    @Test fun releaseBeforeAnyClickNeedsNoAction() {
        val target=ConfirmedMicrophoneTarget()
        target.request(false);target.request(true)
        assertNull(target.next(true));assertFalse(target.busy)
    }
    @Test fun failedClickIsNotRepeatedAndConfirmedSameStateNeedsNoToggle() {
        val target=ConfirmedMicrophoneTarget()
        target.request(false);assertEquals(false,target.next(true))
        target.rejected();assertNull(target.next(true));assertFalse(target.busy)
        target.request(true);assertNull(target.next(true));assertFalse(target.busy)
    }
}
