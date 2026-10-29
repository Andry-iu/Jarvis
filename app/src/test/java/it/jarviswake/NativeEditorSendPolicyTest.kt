package it.jarviswake

import org.junit.Assert.*
import org.junit.Test

class NativeEditorSendPolicyTest {
    private fun allowed(owner: String? = "chatgpt", started: Boolean = true, connection: Boolean = true,
        focused: Boolean = true, matches: Boolean = true, options: Int? = 4) =
        NativeEditorSendPolicy.canSend(owner, "chatgpt", started, connection, focused, matches, options, 0xff, 4)

    @Test fun explicitSendWithMultilineAndOtherFlagsIsAccepted() {
        assertTrue(allowed())
        assertTrue(allowed(options = 4 or 0x40000000))
    }
    @Test fun genericEnterDoneSearchNextAndMissingActionAreRejected() {
        for (action in listOf(null, 0, 1, 2, 3, 5, 6, 7)) assertFalse(allowed(options = action))
    }
    @Test fun otherEditorOrUncertainConnectionCannotSend() {
        assertFalse(allowed(owner = "other"))
        assertFalse(allowed(owner = null))
        assertFalse(allowed(started = false))
        assertFalse(allowed(connection = false))
    }
    @Test fun focusAndExactGreetingAreMandatory() {
        assertFalse(allowed(focused = false))
        assertFalse(allowed(matches = false))
    }
}
