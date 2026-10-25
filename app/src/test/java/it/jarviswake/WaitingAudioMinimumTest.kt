package it.jarviswake

import org.junit.Assert.*
import org.junit.Test

class WaitingAudioMinimumTest {
    @Test fun noAudioOrZeroMinimumNeverDelaysVoice() {
        val gate = WaitingAudioMinimum()
        assertFalse(gate.shouldWait("cycle", 0))
        gate.begin("cycle", 0)
        assertFalse(gate.shouldWait("cycle", 500))
    }
    @Test fun preparationDoesNotCountAsPlaybackTime() {
        val gate = WaitingAudioMinimum().apply { begin("cycle", 3) }
        assertTrue(gate.shouldWait("cycle", 10_000))
        gate.playbackStarted("cycle", 10_000)
        assertTrue(gate.shouldWait("cycle", 12_999))
        assertFalse(gate.shouldWait("cycle", 13_000))
    }
    @Test fun lateVoiceReadinessDoesNotAddAnotherDelay() {
        val gate = WaitingAudioMinimum().apply { begin("cycle", 4); playbackStarted("cycle", 1_000) }
        assertFalse(gate.shouldWait("cycle", 8_000))
    }
    @Test fun repeatedCallbacksAndPollingDoNotRestartTheMinimum() {
        val gate = WaitingAudioMinimum().apply { begin("cycle", 2); playbackStarted("cycle", 100) }
        assertTrue(gate.shouldWait("cycle", 1_000))
        gate.playbackStarted("cycle", 1_900)
        assertFalse(gate.shouldWait("cycle", 2_100))
    }
    @Test fun failureCancellationOrFocusLossReleasesTheGate() {
        val gate = WaitingAudioMinimum().apply { begin("cycle", 30) }
        gate.stop()
        gate.playbackStarted("cycle", 100)
        assertFalse(gate.shouldWait("cycle", 100))
        gate.begin("cycle", 30)
        gate.playbackStarted("cycle", 200)
        gate.stop()
        assertFalse(gate.shouldWait("cycle", 201))
    }
    @Test fun staleCycleCannotStartOrDelayTheCurrentCycle() {
        val gate = WaitingAudioMinimum().apply { begin("new", 1) }
        gate.playbackStarted("old", 0)
        assertFalse(gate.shouldWait("old", 10_000))
        assertTrue(gate.shouldWait("new", 10_000))
    }
    @Test fun wakeResumingTheSameCycleStartsANewMinimum() {
        val gate = WaitingAudioMinimum().apply { begin("cycle", 2); playbackStarted("cycle", 0) }
        assertFalse(gate.shouldWait("cycle", 2_000))
        gate.begin("cycle", 2)
        assertTrue(gate.shouldWait("cycle", 20_000))
        gate.playbackStarted("cycle", 20_000)
        assertTrue(gate.shouldWait("cycle", 21_999))
        assertFalse(gate.shouldWait("cycle", 22_000))
    }
    @Test fun durationIsLimitedToSafeBounds() {
        val gate = WaitingAudioMinimum().apply { begin("cycle", -5) }
        assertFalse(gate.shouldWait("cycle", 0))
        gate.begin("cycle", Int.MAX_VALUE)
        gate.playbackStarted("cycle", 0)
        assertTrue(gate.shouldWait("cycle", 29_999))
        assertFalse(gate.shouldWait("cycle", 30_000))
    }
}
