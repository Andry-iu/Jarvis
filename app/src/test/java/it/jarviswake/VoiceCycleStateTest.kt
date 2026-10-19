package it.jarviswake

import org.junit.Assert.*
import org.junit.Test

class VoiceCycleStateTest {
    private fun waiting() = VoiceCycleState().apply { begin("wake-A", 1_000L) }

    @Test fun visibleLockScreenSurfaceCanStartVoiceWithoutAuthentication() {
        val state = waiting()
        assertTrue(state.openWeb("wake-A", 1_100L, true, true, true, true,
            canShowWhileLocked = true))
        assertEquals(VoiceCycleState.Phase.WEB, state.phase)
        assertFalse(state.openWeb("wake-A", 1_200L, true, true, true, true,
            canShowWhileLocked = true))
        state.voiceDispatched("wake-A")
        assertFalse(state.touchGate.canDispatch)
    }

    @Test fun lockScreenCapabilityStillRequiresResumedAndFocusedSurface() {
        val state = waiting()
        assertFalse(state.openWeb("wake-A", 1_100L, false, true, true, true, true))
        assertFalse(state.openWeb("wake-A", 1_200L, true, false, true, true, true))
        assertTrue(state.openWeb("wake-A", 1_300L, true, true, true, true, true))
    }

    @Test fun screenOffDuringLockedCycleRejectsLateCallbacks() {
        val state = waiting()
        assertTrue(state.openWeb("wake-A", 1_100L, true, true, true, true, true))
        state.voiceDispatched("wake-A")
        assertTrue(state.cancel("wake-A"))
        assertFalse(state.openWeb("wake-A", 1_200L, true, true, true, true, true))
        state.voiceDispatched("wake-A")
        assertNull(state.id)
    }

    @Test fun lockScreenCapabilityDoesNotReviveExpiredLaunch() {
        val state = waiting()
        assertFalse(state.openWeb("wake-A", 46_000L, true, true, true, true, true))
    }

    @Test fun trustedDeviceStillShowingKeyguardDoesNotLaunch() {
        val state = waiting()
        assertFalse(state.openWeb("wake-A", 1_100L, true, true, false, true))
        assertEquals(VoiceCycleState.Phase.UNLOCK, state.phase)
        assertTrue(state.openWeb("wake-A", 1_200L, true, true, false, false))
    }

    @Test fun successfulBiometricWaitsForResumedFocusedActivity() {
        val state = waiting()
        assertFalse(state.openWeb("wake-A", 1_100L, false, false, false, false))
        assertFalse(state.openWeb("wake-A", 1_200L, true, false, false, false))
        assertTrue(state.openWeb("wake-A", 1_300L, true, true, false, false))
        assertFalse(state.openWeb("wake-A", 1_400L, true, true, false, false))
    }

    @Test fun screenOffOrXInvalidatesLateUnlockAndVoiceCallbacks() {
        val state = waiting()
        assertTrue(state.cancel("wake-A"))
        assertFalse(state.openWeb("wake-A", 1_200L, true, true, false, false))
        state.voiceDispatched("wake-A")
        assertNull(state.id)
        assertFalse(state.cancel("wake-A"))
    }

    @Test fun expiredUnlockCannotLaunchEvenBeforeWatchdogRuns() {
        val state = waiting()
        assertTrue(state.expired(46_000L))
        assertFalse(state.openWeb("wake-A", 46_001L, true, true, false, false))
    }

    @Test fun stalledPageExpiresWithoutAnyWebViewReadyEvent() {
        val state = waiting()
        assertTrue(state.openWeb("wake-A", 2_000L, true, true, false, false))
        assertFalse(state.expired(41_999L))
        assertTrue(state.expired(42_000L))
    }

    @Test fun touchLatchBelongsToCycleAndSurvivesActivityReplacement() {
        val state = waiting()
        state.openWeb("wake-A", 2_000L, true, true, false, false)
        val firstActivityGate = state.touchGate
        state.voiceDispatched("wake-A")
        val recreatedActivityGate = state.touchGate
        assertSame(firstActivityGate, recreatedActivityGate)
        assertFalse(recreatedActivityGate.canDispatch)
        assertFalse(state.expired(500_000L))
    }

    @Test fun duplicateStartAndStaleCancellationDoNotReplaceCurrentAttempt() {
        val state = waiting()
        assertFalse(state.begin("wake-B", 2_000L))
        assertFalse(state.cancel("wake-B"))
        assertTrue(state.matches("wake-A"))
    }
    @Test fun oldCallbacksCannotAffectANewCycleAfterCancellation() {
        val state = waiting()
        state.openWeb("wake-A", 2_000L, true, true, false, false)
        state.voiceDispatched("wake-A")
        state.cancel("wake-A")
        assertTrue(state.begin("wake-B", 3_000L))
        state.voiceDispatched("wake-A")
        assertFalse(state.cancel("wake-A"))
        assertTrue(state.matches("wake-B"))
        assertTrue(state.touchGate.canDispatch)
        assertEquals(VoiceCycleState.Phase.UNLOCK, state.phase)
    }

}
