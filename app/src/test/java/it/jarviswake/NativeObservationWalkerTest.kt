package it.jarviswake

import org.junit.Assert.*
import org.junit.Test

class NativeObservationWalkerTest {
    private class Ui(val source: Int, var node: NativeComposerSendSelector.Node) {
        val children = ArrayList<Ui?>()
        override fun equals(other: Any?) = other is Ui && source == other.source
        override fun hashCode() = source
    }
    private fun fixture(): List<Ui> {
        val result = ArrayList<Ui>()
        javaClass.getResourceAsStream("/native-composer/galaxy-076.txt")!!.bufferedReader().useLines { lines ->
            lines.filter { !it.startsWith("#") && it.isNotBlank() }.forEach { line ->
                val a = line.split(' '); val index = a[0].toInt();val parent = a[1].toInt()
                val n = NativeComposerSendSelector.Node(parent,
                    NativeComposerSendSelector.Box(a[2].toInt(),a[3].toInt(),a[4].toInt(),a[5].toInt()),
                    editable=a[6]=="E",focused=a[10].toBoolean(),button=a[6]=="B",enabled=a[7].toBoolean(),
                    clickable=a[8].toBoolean(),clickAction=a[9].toBoolean())
                // Simulate equivalent role objects as well as the metadata from the phone.
                val ui = Ui(if (n.button) result[parent].source else index,n)
                result.add(ui)
                if (parent>=0) result[parent].children.add(ui)
            }
        }
        return result
    }
    private fun capture(root: Ui) = NativeObservationWalker.capture(root, { true }, { it.children.size },
        { n,i -> n.children[i] }, { it.node.editable || it.node.button })
    private fun inspect(c: NativeObservationWalker.Capture<Ui>, field: Ui): NativeComposerSendSelector.Inspection {
        val missing = c.graph.missingEdges.groupingBy { it.parent }.eachCount()
        val nodes = c.graph.entries.mapIndexed { i,e -> e.value.node.copy(parent=e.parent,unresolvedChildren=missing[i]?:0) }
        return NativeComposerSendSelector.inspect(nodes,c.graph.indexOfIdentity(field))
    }
    @Test fun replayExportedGalaxyComposerThroughTheProductionWalkerAndSelector() {
        val nodes=fixture();val field=nodes.single { it.node.editable }
        val c=capture(nodes[0]);val result=inspect(c,field)
        assertEquals("candidate",result.reason)
        val chosen=c.graph.entries[result.candidate!!].value
        assertTrue(chosen.node.clickable && chosen.node.clickAction)
        assertEquals(nodes.filter { it.node.clickable && !it.node.editable }.maxBy { it.node.box.right },chosen)
        assertEquals(0,result.invalidPaths)
    }
    @Test fun missingRemoteSiblingAndEditorOrButtonInternalsDoNotBlockCompleteComposer() {
        val nodes=fixture();val field=nodes.single { it.node.editable }
        field.children.add(null)
        nodes.filter { it.node.button }.forEach { it.children.add(null) }
        val outer=Ui(-100,nodes[0].node.copy(parent=-1));outer.children.add(null);outer.children.add(nodes[0])
        val c=capture(outer)
        assertEquals(1,c.graph.missingEdges.size) // The unrelated sibling, not atomic role contents.
        val result=inspect(c,field)
        assertEquals("candidate",result.reason)
        assertEquals(0,result.invalidPaths)
        assertTrue(c.visible.contains(field))
    }
    @Test fun missingUnknownChildInsideComposerStillRejectsTheAction() {
        val nodes=fixture();nodes[0].children.add(null)
        val result=inspect(capture(nodes[0]),nodes.single { it.node.editable })
        assertEquals("composer_incomplete",result.reason)
        assertNull(result.candidate)
    }
    @Test fun missingButtonRoleOrDisabledSendCannotBeWorkedAround() {
        val nodes=fixture();val field=nodes.single { it.node.editable }
        val right=nodes.filter { it.node.clickable && !it.node.editable }.maxBy { it.node.box.right }
        right.node=right.node.copy(enabled=false)
        assertEquals("action_disabled",inspect(capture(nodes[0]),field).reason)
        right.node=right.node.copy(enabled=true)
        right.children.removeAll { it?.node?.button==true }
        assertEquals("action_button_role_missing",inspect(capture(nodes[0]),field).reason)
    }
    @Test fun pruningTheComposerGraphDoesNotRemoveSemanticVoiceDescendants() {
        val nodes=fixture();val button=nodes.first { it.node.button }
        val label=Ui(-200,button.node.copy(parent=-1,button=false))
        button.children.add(label)
        val c=capture(nodes[0])
        assertTrue(c.visible.any { it===label })
        assertEquals(-1,c.graph.indexOfIdentity(label))
    }
    @Test fun incompleteTraversalCannotInventFieldOrControls() {
        val nodes=fixture();val field=nodes.single { it.node.editable }
        val c=NativeObservationWalker.capture(nodes[0],{true},{it.children.size},{n,i->n.children[i]},
            {it.node.editable||it.node.button},maxDepth=1)
        assertNull(inspect(c,field).candidate)
        assertTrue(c.graph.missingEdges.isNotEmpty())
    }
    @Test fun stableCandidateCanDispatchOnlyOnceWhenTheDraftStillLooksUnchanged() {
        val nodes=fixture();val field=nodes.single { it.node.editable }
        val stable=NativeSendStability<Ui>()
        var stage=NativeGreetingPolicy.WRITING;var clicks=0
        for (now in listOf(0L,100L,300L,600L,900L)) {
            val c=capture(nodes[0]);val selected=inspect(c,field).candidate?.let { c.graph.entries[it].value }
            val ready=stable.observe(selected,now)
            val action=NativeGreetingPolicy.decide(stage,now,1,false,true,false,ready)
            if (action==NativeGreetingPolicy.Action.SEND) { stage=NativeGreetingPolicy.DISPATCHED;clicks++ }
        }
        assertEquals(1,clicks)
        assertEquals(NativeGreetingPolicy.DISPATCHED,stage)
    }
}
