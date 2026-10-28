package it.jarviswake

import org.junit.Assert.*
import org.junit.Test

class NativeExitConfirmationTest {
    @Test fun requiresStableComposerAndNoVoiceControls() {
        val state = NativeExitConfirmation()
        assertFalse(state.observe(0L, "composer", true))
        assertFalse(state.observe(100L, "composer", false))
        assertFalse(state.observe(349L, "composer", false))
        assertTrue(state.observe(350L, "composer", false))
    }
    @Test fun voiceOrWindowLossResetsConfirmation() {
        val state = NativeExitConfirmation()
        state.observe(0, "a", false)
        assertFalse(state.observe(500, "a", true))
        assertFalse(state.observe(600, "a", false))
        assertFalse(state.observe(900, null, false))
        assertFalse(state.observe(1000, "a", false))
        assertFalse(state.observe(1300, "b", false))
        assertTrue(state.observe(1550, "b", false))
    }
    @Test fun explicitResetRequiresNewStableObservation() {
        val state = NativeExitConfirmation()
        state.observe(0, "a", false)
        state.reset()
        assertFalse(state.observe(500, "a", false))
        assertTrue(state.observe(750, "a", false))
    }
}
