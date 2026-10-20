package it.jarviswake

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeScreenPolicyTest {
    @Test fun screenOffSuspendsWakeListening() {
        assertFalse(WakeScreenPolicy.shouldListen(screenInteractive = false, voiceCycleActive = false, stopping = false))
    }

    @Test fun screenOffKeepsCaptureDuringThirtySecondGrace() {
        assertTrue(WakeScreenPolicy.shouldListen(
            screenInteractive = false, voiceCycleActive = false, stopping = false,
            nowElapsedMs = 129_999L, screenOffDeadlineElapsedMs = 130_000L
        ))
    }

    @Test fun screenOffGraceEndsAtTheDeadline() {
        assertFalse(WakeScreenPolicy.shouldListen(
            screenInteractive = false, voiceCycleActive = false, stopping = false,
            nowElapsedMs = 130_000L, screenOffDeadlineElapsedMs = 130_000L
        ))
    }

    @Test fun voiceCycleSuspendsWakeCaptureEvenDuringScreenOffGrace() {
        assertFalse(WakeScreenPolicy.shouldListen(
            screenInteractive = false, voiceCycleActive = true, stopping = false,
            nowElapsedMs = 110_000L, screenOffDeadlineElapsedMs = 130_000L
        ))
    }

    @Test fun screenOnAllowsListeningRegardlessOfKeyguardState() {
        // Keyguard is intentionally not an input: all screen-on causes re-arm Vosk.
        assertTrue(WakeScreenPolicy.shouldListen(screenInteractive = true, voiceCycleActive = false, stopping = false))
    }

    @Test fun activeVoiceCycleKeepsVoskPausedAfterScreenOn() {
        assertFalse(WakeScreenPolicy.shouldListen(screenInteractive = true, voiceCycleActive = true, stopping = false))
    }

    @Test fun stoppedServiceDoesNotRestartListeningOnScreenOn() {
        assertFalse(WakeScreenPolicy.shouldListen(screenInteractive = true, voiceCycleActive = false, stopping = true))
    }

    @Test fun screenOnRequestsPreloadWhenMainActivityIsAvailable() {
        assertTrue(WakeScreenPolicy.shouldPreloadForScreenOn(mainActivityAvailable = true, voiceCycleActive = false))
    }

    @Test fun screenOnDoesNotStartASecondPreloadDuringVoice() {
        assertFalse(WakeScreenPolicy.shouldPreloadForScreenOn(mainActivityAvailable = true, voiceCycleActive = true))
    }
}
