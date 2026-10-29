package it.jarviswake

import org.junit.Assert.*
import org.junit.Test

class NativeComposerSendSelectorTest {
    private fun box(l: Int, t: Int, r: Int, b: Int) = NativeComposerSendSelector.Box(l, t, r, b)
    // Anonymized .73 Galaxy topology: editor branch, left action branch, two actions in right group.
    private fun fixture(dy: Int = 0): List<NativeComposerSendSelector.Node> {
        fun n(parent: Int, l: Int, t: Int, r: Int, b: Int, edit: Boolean = false,
            click: Boolean = false, button: Boolean = false) = NativeComposerSendSelector.Node(parent,
                box(l, t + dy, r, b + dy), editable = edit, focused = edit,
                clickable = click, clickAction = click, button = button)
        return listOf(
            n(-1,48,1047,1392,1704), // composer
            n(0,112,1111,1328,1496), n(1,112,1111,1328,1496,edit=true),
            n(0,48,1512,240,1704), n(3,48,1512,240,1704),
            n(4,48,1512,240,1704,click=true), n(5,48,1512,240,1704,button=true),
            n(0,816,1512,1392,1704), n(7,816,1512,1392,1704),
            n(8,816,1512,1000,1704), n(9,816,1512,1000,1704,click=true),
            n(10,816,1512,1000,1704,button=true),
            n(8,1192,1512,1384,1704), n(12,1192,1512,1384,1704,click=true),
            n(13,1192,1512,1384,1704,button=true))
    }
    private fun select(nodes: List<NativeComposerSendSelector.Node>) = NativeComposerSendSelector.select(nodes, 2)
    private fun changed(index: Int, f: (NativeComposerSendSelector.Node) -> NativeComposerSendSelector.Node) =
        fixture().mapIndexed { i, n -> if (i == index) f(n) else n }

    @Test fun observedComposerAndKeyboardMovementChooseLiveWrapper() {
        assertEquals(13, select(fixture()))
        assertEquals(13, select(fixture(1176)))
        // Renumbering all nodes preserves the selection; log indices are not selectors.
        val reversed = fixture().reversed().map { it.copy(parent = if (it.parent < 0) -1 else 14 - it.parent) }
        assertEquals(1, NativeComposerSendSelector.select(reversed, 12))
    }
    @Test fun disabledOrUnclickableCandidateIsRejected() {
        assertNull(select(changed(13) { it.copy(enabled=false) }))
        assertNull(select(changed(13) { it.copy(clickAction=false) }))
        assertNull(select(changed(13) { it.copy(clickable=false) }))
    }
    @Test fun labelledOrUnknownButtonShapeDoesNotUseHeuristic() {
        assertNull(select(changed(14) { it.copy(labelled=true) }))
        assertNull(select(changed(14) { it.copy(button=false) }))
    }
    @Test fun noFallbackForAnotherWindowUnfocusedOrPasswordField() {
        assertNull(select(changed(2) { it.copy(sameAppWindow=false) }))
        assertNull(select(changed(2) { it.copy(focused=false) }))
        assertNull(select(changed(2) { it.copy(password=true) }))
        assertNull(select(changed(13) { it.copy(sameAppWindow=false) }))
    }
    @Test fun missingOrExtraControlsAndEditorsAreRejected() {
        assertNull(select(fixture().filterIndexed { i, _ -> i != 14 }))
        assertNull(select(fixture() + NativeComposerSendSelector.Node(0,box(300,1512,400,1704),clickable=true,clickAction=true)))
        assertNull(select(changed(4) { it.copy(editable=true) }))
    }
    @Test fun brokenParentOrCycleCannotSelect() {
        assertNull(select(changed(13) { it.copy(parent=-1) }))
        assertNull(select(changed(13) { it.copy(parent=13) }))
        assertNull(select(changed(12) { it.copy(parent=0) })) // loses shared right branch
    }
    @Test fun differentRowsOverlapsAndOutsideComposerAreRejected() {
        assertNull(select(changed(13) { it.copy(box=box(1192,1300,1384,1400)) }))
        assertNull(select(changed(13) { it.copy(box=box(900,1512,1384,1704)) }))
        assertNull(select(changed(13) { it.copy(box=box(1192,1512,1500,1704)) }))
    }
    @Test fun stabilityRequiresTimeAndResetsForMissingOrChangedCandidate() {
        val stable = NativeSendStability<String>()
        assertFalse(stable.observe("a",1000))
        assertFalse(stable.observe("a",1249))
        assertTrue(stable.observe("a",1250))
        assertFalse(stable.observe(null,1300))
        assertFalse(stable.observe("a",1400))
        assertFalse(stable.observe("b",1700))
        assertTrue(stable.observe("b",1950))
    }
}
