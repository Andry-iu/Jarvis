package it.jarviswake

/** Collect normal visible controls and a separate, bounded composer graph in the same traversal.
 * Text editor contents and Button internals are leaves only in the composer graph; semantic
 * Voice control collection still traverses them as before.
 */
internal object NativeObservationWalker {
    data class Capture<T : Any>(val visible: List<T>, val graph: NativeNodeObservation<T>)
    fun <T : Any> capture(root: T, visible: (T) -> Boolean, childCount: (T) -> Int,
        child: (T, Int) -> T?, composerLeaf: (T) -> Boolean,
        maxDepth: Int = 40, maxVisible: Int = 1500, maxGraph: Int = 2500): Capture<T> {
        val result = ArrayList<T>()
        val graph = NativeNodeObservation<T>()
        fun visit(node: T, depth: Int, parent: Int?) {
            if (depth > maxDepth || result.size >= maxVisible) {
                parent?.takeIf { it >= 0 }?.let { graph.markMissing(it, "traversal_limit") }
                return
            }
            val index = if (parent != null && graph.entries.size < maxGraph) graph.add(node, parent) else null
            if (parent != null && index == null && parent >= 0) graph.markMissing(parent, "graph_limit")
            if (visible(node)) result.add(node)
            val keepEdges = index != null && !composerLeaf(node)
            for (i in 0 until childCount(node)) {
                val next = child(node, i)
                if (next == null) {
                    if (keepEdges) graph.markMissing(index!!, "null_child")
                } else visit(next, depth + 1, if (keepEdges) index else null)
            }
        }
        visit(root, 0, -1)
        return Capture(result, graph)
    }
}
