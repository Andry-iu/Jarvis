package it.jarviswake

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeScreenPolicyTest {
    @Test fun screenOffSuspendsWakeListening() {
        assertFalse(WakeScreenPolicy.shouldListen(screenInteractive = false, voiceCycleActive = false, stopping = false))
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
