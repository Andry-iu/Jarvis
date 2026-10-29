package it.jarviswake

import org.junit.Assert.assertEquals
import org.junit.Test

class NativeGreetingPolicyTest {
    private fun action(stage: Int, elapsed: Long = 0, count: Int = 1, empty: Boolean = true,
        matches: Boolean = false, write: Boolean = true, send: Boolean = true) =
        NativeGreetingPolicy.decide(stage, elapsed, count, empty, matches, write, send)

    @Test fun existingDraftIsNeverOverwrittenOrSent() {
        assertEquals(NativeGreetingPolicy.Action.ABORT, action(0, empty = false))
        assertEquals(NativeGreetingPolicy.Action.ABORT, action(1, empty = false, matches = false))
    }
    @Test fun absentOrAmbiguousEditorCannotBeWritten() {
        assertEquals(NativeGreetingPolicy.Action.WAIT, action(0, count = 0))
        assertEquals(NativeGreetingPolicy.Action.WAIT, action(0, count = 2))
        assertEquals(NativeGreetingPolicy.Action.WAIT, action(0, write = false))
        assertEquals(NativeGreetingPolicy.Action.ABORT, action(0, elapsed = 10000, count = 0))
    }
    @Test fun textMustMatchExactlyBeforeSingleSend() {
        assertEquals(NativeGreetingPolicy.Action.WRITE, action(0))
        assertEquals(NativeGreetingPolicy.Action.WAIT, action(1))
        assertEquals(NativeGreetingPolicy.Action.WAIT, action(1, empty = false, matches = true, send = false))
        assertEquals(NativeGreetingPolicy.Action.SEND, action(1, empty = false, matches = true))
    }
    @Test fun consumedOrAbortedStateCannotRetryEvenAfterReconnection() {
        assertEquals(NativeGreetingPolicy.Action.NONE, action(2, empty = false, matches = true))
        assertEquals(NativeGreetingPolicy.Action.NONE, action(3))
        assertEquals(NativeGreetingPolicy.Action.ABORT, action(1, elapsed = 10000))
    }
}
