package it.jarviswake

import org.junit.Assert.*
import org.junit.Test

class NativeWindowSelectorTest {
    private val target = "com.openai.chatgpt"
    private fun app(id: Int = 1, layer: Int = 1, owner: String? = target, active: Boolean = false,
        visible: Boolean = true) = NativeWindowSelector.Window(id, NativeWindowSelector.Kind.APP,
        layer, active, active, owner, visible)

    @Test fun keyboardAndCoverFocusDoNotHideForemostChatGPT() {
        val keyboard = NativeWindowSelector.Window(2, NativeWindowSelector.Kind.IME, 10, true, true, "keyboard", true)
        val cover = NativeWindowSelector.Window(3, NativeWindowSelector.Kind.OVERLAY, 11, true, false, "it.jarviswake", true)
        assertEquals(1, NativeWindowSelector.select(listOf(app(), keyboard, cover), target))
    }
    @Test fun cannotAutomateChatGPTBehindAnotherApplication() {
        assertNull(NativeWindowSelector.select(listOf(app(), app(2, 2, "other")), target))
        assertNull(NativeWindowSelector.select(listOf(app(layer = 3), app(2, 2, "other", active = true)), target))
    }
    @Test fun applicationDialogsUseUniqueTopWindowAndTiesRemainAmbiguous() {
        assertEquals(2, NativeWindowSelector.select(listOf(app(), app(2, 2)), target))
        assertNull(NativeWindowSelector.select(listOf(app(), app(2, 1)), target))
    }
    @Test fun systemDialogOrUnknownFocusedApplicationBlocksActions() {
        val system = NativeWindowSelector.Window(2, NativeWindowSelector.Kind.SYSTEM, 10, true, true, "system", true)
        assertNull(NativeWindowSelector.select(listOf(app(), system), target))
        assertNull(NativeWindowSelector.select(listOf(app(), app(2, 2, null, active = true)), target))
    }
    @Test fun missingAndHiddenChatGPTCannotBeSelected() {
        assertNull(NativeWindowSelector.select(emptyList(), target))
        assertNull(NativeWindowSelector.select(listOf(app(visible = false)), target))
        assertEquals(1, NativeWindowSelector.select(listOf(app(), app(2, 2, "other", visible = false)), target))
    }
}
