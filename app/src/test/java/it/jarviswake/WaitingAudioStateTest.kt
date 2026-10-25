package it.jarviswake

import org.junit.Assert.*
import org.junit.Test

class WaitingAudioStateTest {
    @Test fun disabledOrMissingFileDoesNotStart() {
        val state = WaitingAudioState().apply { begin("wake") }
        assertFalse(state.request("wake", false, true))
        assertFalse(state.request("wake", true, false))
        assertTrue(state.request("wake", true, true))
    }
    @Test fun coverHandoffDoesNotStartAnotherPlayer() {
        val state = WaitingAudioState().apply { begin("manual") }
        assertTrue(state.request("manual", true, true))
        assertFalse(state.request("manual", true, true))
        assertTrue(state.mayPlay("manual"))
    }
    @Test fun voiceConfirmedBeforePreparedCallbackPreventsPlayback() {
        val state = WaitingAudioState().apply { begin("wake") }
        assertTrue(state.request("wake", true, true))
        assertTrue(state.finish("wake"))
        assertFalse(state.mayPlay("wake"))
        assertFalse(state.request("wake", true, true))
    }
    @Test fun voiceAlreadyActiveBeforeCoverPreventsPlayback() {
        val state = WaitingAudioState().apply { begin("wake"); finish("wake") }
        assertFalse(state.request("wake", true, true))
    }
    @Test fun staleCycleCannotStartOrStopCurrentPlayback() {
        val state = WaitingAudioState().apply { begin("new") }
        assertFalse(state.request("old", true, true))
        assertTrue(state.request("new", true, true))
        assertFalse(state.finish("old"))
        assertTrue(state.mayPlay("new"))
        assertFalse(state.mayPlay("old"))
    }
    @Test fun nextCycleCanPlayAgain() {
        val state = WaitingAudioState().apply { begin("first"); request("first", true, true); finish("first") }
        state.begin("second")
        assertTrue(state.request("second", true, true))
    }
    @Test fun wakeResumingHiddenCoverCanPlayAgainWithSameToken() {
        val state = WaitingAudioState().apply { begin("same"); request("same", true, true); finish("same") }
        state.begin("same")
        assertTrue(state.request("same", true, true))
    }
}
