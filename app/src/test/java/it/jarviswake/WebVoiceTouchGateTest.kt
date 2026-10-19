package it.jarviswake

import org.junit.Assert.*
import org.junit.Test

class WebVoiceTouchGateTest {
    @Test fun dispatchedTouchCannotBeRepeatedDuringSlowOpening() {
        val gate = WebVoiceTouchGate()
        assertTrue(gate.canDispatch)
        gate.block()
        repeat(1000) { assertFalse(gate.canDispatch) }
        gate.block()
        assertFalse(gate.canDispatch)
    }

    @Test fun newCycleHasIndependentGate() {
        val old = WebVoiceTouchGate()
        old.block()
        assertTrue(WebVoiceTouchGate().canDispatch)
        assertFalse(old.canDispatch)
    }
}
