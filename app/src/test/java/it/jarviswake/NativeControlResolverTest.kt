package it.jarviswake

import org.junit.Assert.*
import org.junit.Test

class NativeControlResolverTest {
    private class Node(val label: String = "", val clickable: Boolean = false, val parent: Node? = null,
                       val visible: Boolean = true, val enabled: Boolean = true)
    private fun resolve(vararg nodes: Node) = NativeControlResolver.targets(nodes.toList(), NativeVoiceLabels.start,
        labels = { listOf(NativeVoiceLabels.normalize(it.label)) }, parent = { it.parent },
        visible = { it.visible }, enabled = { it.enabled }, clickable = { it.clickable })

    @Test fun resolvesExactGalaxyChatGptLabelFromDiagnosticSnapshot() {
        val button = Node(clickable = true)
        val icon = Node("Avvia una conversazione vocale", parent = button)
        assertEquals(listOf(button), resolve(icon, button))
        assertTrue(resolve(Node("Menu", clickable = true), Node("Microphone", clickable = true)).isEmpty())
    }

    @Test fun labelledChildResolvesToItsButtonOnce() {
        val button = Node("Voice", clickable = true)
        val icon = Node("Voice", parent = button)
        assertEquals(listOf(button), resolve(button, icon))
    }
    @Test fun duplicateControlsRemainAmbiguous() {
        val a = Node(clickable = true); val b = Node(clickable = true)
        assertEquals(2, resolve(Node("Voice", parent = a), Node("Start voice", parent = b)).size)
    }
    @Test fun microphoneAndDisabledOrHiddenControlsAreExcluded() {
        val mic = Node("Microphone", clickable = true)
        val disabled = Node(clickable = true, enabled = false)
        assertTrue(resolve(mic, Node("Voice", parent = disabled), Node("Voice", clickable = true, visible = false)).isEmpty())
    }
    @Test fun doesNotClimbArbitrarilyToScreenContainer() {
        var node = Node(clickable = true)
        repeat(4) { node = Node(parent = node) }
        assertTrue(resolve(Node("Voice", parent = node)).isEmpty())
    }
}
