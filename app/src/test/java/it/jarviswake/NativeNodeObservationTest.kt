package it.jarviswake

import org.junit.Assert.*
import org.junit.Test

class NativeNodeObservationTest {
    private class FrameworkNode(val source: Int, val description: NativeComposerSendSelector.Node) {
        override fun equals(other: Any?) = other is FrameworkNode && other.source == source
        override fun hashCode() = source
    }
    private fun fixture(): List<FrameworkNode> {
        fun n(id: Int, parent: Int, l: Int, t: Int, r: Int, b: Int, edit: Boolean = false,
            click: Boolean = false, button: Boolean = false) = FrameworkNode(id, NativeComposerSendSelector.Node(
                parent, NativeComposerSendSelector.Box(l,t,r,b), editable=edit,focused=edit,
                clickable=click,clickAction=click,button=button))
        return listOf(
            n(0,-1,48,1047,1392,1704), n(1,0,112,1111,1328,1496), n(2,1,112,1111,1328,1496,edit=true),
            n(3,0,48,1512,240,1704), n(4,3,48,1512,240,1704),
            n(5,4,48,1512,240,1704,click=true), n(5,5,48,1512,240,1704,button=true),
            n(7,0,816,1512,1392,1704), n(8,7,816,1512,1392,1704),
            n(9,8,816,1512,1000,1704), n(10,9,816,1512,1000,1704,click=true),
            n(10,10,816,1512,1000,1704,button=true), n(12,8,1192,1512,1384,1704),
            n(13,12,1192,1512,1384,1704,click=true), n(13,13,1192,1512,1384,1704,button=true))
    }
    @Test fun reproducesMissingRoleWithLastEqualNodeMapAndFixesItWithTraversalEdges() {
        val nodes = fixture()
        val oldIndices = nodes.withIndex().associate { it.value to it.index }
        val old = nodes.map { n -> n.description.copy(parent = n.description.parent.takeIf { it >= 0 }?.let { oldIndices[nodes[it]] } ?: -1) }
        val rejected = NativeComposerSendSelector.inspect(old,2)
        assertEquals("action_button_role_missing",rejected.reason)
        assertTrue(rejected.invalidPaths > 0)
        val graph = NativeNodeObservation<FrameworkNode>()
        for (n in nodes) graph.add(n,n.description.parent)
        val coherent = graph.entries.map { it.value.description.copy(parent=it.parent) }
        val fixed = NativeComposerSendSelector.inspect(coherent,graph.indexOfIdentity(nodes[2]))
        assertEquals("candidate",fixed.reason)
        assertEquals(13,fixed.candidate)
        assertEquals(0,fixed.invalidPaths)
        assertSame(nodes[13], graph.entries[fixed.candidate!!].value)
        assertEquals(15,graph.entries.size)
    }
    @Test fun fieldLookupDoesNotSelectAnEqualButDifferentObject() {
        val original = fixture()[2]
        val equalOther = FrameworkNode(original.source,original.description)
        val graph = NativeNodeObservation<FrameworkNode>()
        graph.add(original,-1)
        assertEquals(0,graph.indexOfIdentity(original))
        assertEquals(-1,graph.indexOfIdentity(equalOther))
    }
    @Test fun reusedExactObjectIsAmbiguousRatherThanSilentlySelectingLastOccurrence() {
        val node = Any()
        val graph = NativeNodeObservation<Any>()
        graph.add(node,-1);graph.add(node,0)
        assertEquals(-1,graph.indexOfIdentity(node))
    }
    @Test fun invalidParentCannotCreateCycleOrDisconnectedRoot() {
        val graph = NativeNodeObservation<Any>()
        assertThrows(IllegalArgumentException::class.java) { graph.add(Any(),0) }
        graph.add(Any(),-1)
        assertThrows(IllegalArgumentException::class.java) { graph.add(Any(),1) }
        assertThrows(IllegalArgumentException::class.java) { graph.add(Any(),-1) }
    }
}
