package it.jarviswake

import org.junit.Assert.*
import org.junit.Test

class NativeExitStateTest {
    @Test fun repeatedExitAlwaysDismissesAndDoesNotRepeatPendingStop() {
        val state = NativeExitState(stopRequested = true, returnHome = true)
        assertFalse(state.dismiss(home = false, now = 100))
        assertTrue(state.coverDismissed)
        assertTrue(state.returnHome) // Preserve the first requested destination.
    }
    @Test fun timeoutCannotReacquireScreenAndIsReportedOnlyOnce() {
        val state = NativeExitState()
        assertTrue(state.dismiss(home = false, now = 100))
        assertFalse(state.reportTimeout(5099))
        assertTrue(state.reportTimeout(5100))
        assertTrue(state.coverDismissed)
        assertFalse(state.reportTimeout(9000))
        assertFalse(state.dismiss(home = true, now = 10000))
        assertTrue(state.coverDismissed)
        assertFalse(state.returnHome)
    }
    @Test fun consumedStopRemainsConsumedAcrossStateRecovery() {
        val state = NativeExitState()
        assertTrue(state.dismiss(home = true, now = 0))
        assertTrue(state.consumeStop())
        val restored = state.copy()
        assertFalse(restored.consumeStop())
        assertFalse(restored.dismiss(home = false, now = 1000))
        assertTrue(restored.coverDismissed)
        assertTrue(restored.reportTimeout(5000))
        assertTrue(restored.coverDismissed)
    }
    @Test fun noStopOrTimeoutBeforeRequest() {
        val state = NativeExitState()
        assertFalse(state.consumeStop())
        assertFalse(state.reportTimeout(10000))
        assertFalse(state.coverDismissed)
    }
}
