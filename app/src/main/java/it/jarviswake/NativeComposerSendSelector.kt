package it.jarviswake

/** Observed Galaxy composer shape, NOT a documented ChatGPT semantic contract.
 * Bounds compare live nodes only; no screen coordinates or dump node numbers are selectors.
 */
internal object NativeComposerSendSelector {
    data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val valid get() = right > left && bottom > top
        val centerX get() = (left.toLong() + right) / 2
        fun contains(other: Box) = valid && other.valid && left <= other.left && top <= other.top &&
            right >= other.right && bottom >= other.bottom
    }
    data class Node(val parent: Int, val box: Box, val sameAppWindow: Boolean = true,
        val enabled: Boolean = true, val editable: Boolean = false, val focused: Boolean = false,
        val clickable: Boolean = false, val clickAction: Boolean = false,
        val button: Boolean = false, val labelled: Boolean = false, val password: Boolean = false)

    fun select(nodes: List<Node>, field: Int): Int? {
        val editor = nodes.getOrNull(field) ?: return null
        if (!editor.sameAppWindow || !editor.enabled || !editor.editable || !editor.focused || editor.password) return null
        // Full, acyclic parent chains are required inside a small, local composer subtree.
        fun chain(index: Int): List<Int>? {
            val result = ArrayList<Int>()
            var current = index
            while (current >= 0) {
                if (current !in nodes.indices || current in result || result.size >= 40) return null
                if (!nodes[current].sameAppWindow) return null
                result.add(current)
                current = nodes[current].parent
            }
            return result
        }
        val paths = nodes.indices.map { chain(it) }
        val ancestors = paths[field] ?: return null
        for (composer in ancestors.drop(1).take(4)) {
            val subtree = nodes.indices.filter { paths[it]?.contains(composer) == true }
            val controls = subtree.filter { it != field && (nodes[it].clickable || nodes[it].clickAction) }
            if (controls.size < 3) continue
            if (controls.size != 3 || subtree.count { nodes[it].editable } != 1) return null
            if (!nodes[composer].box.contains(editor.box)) return null
            for (index in controls) {
                val n = nodes[index]
                if (!n.enabled || !n.clickable || !n.clickAction || n.password || n.editable ||
                    !nodes[composer].box.contains(n.box) || n.box.top < editor.box.bottom) return null
                val branch = subtree.filter { paths[it]?.contains(index) == true }
                if (branch.any { nodes[it].labelled || nodes[it].password || nodes[it].editable }) return null
                if (branch.none { nodes[it].button && (paths[it]?.indexOf(index) ?: 99) <= 2 }) return null
            }
            val ordered = controls.sortedBy { nodes[it].box.centerX }
            val a = nodes[ordered[0]].box
            val b = nodes[ordered[1]].box
            val c = nodes[ordered[2]].box
            if (a.right > b.left || b.right > c.left || a.centerX >= editor.box.centerX ||
                b.centerX <= editor.box.centerX || maxOf(a.top, b.top, c.top) >= minOf(a.bottom, b.bottom, c.bottom)) return null
            fun branch(index: Int): Int? {
                val path = paths[index] ?: return null
                return path.getOrNull(path.indexOf(composer) - 1)
            }
            val editorBranch = branch(field)
            val leftBranch = branch(ordered[0])
            val rightBranch = branch(ordered[1])
            if (editorBranch == null || leftBranch == null || rightBranch == null ||
                setOf(editorBranch, leftBranch, rightBranch).size != 3 || branch(ordered[2]) != rightBranch) return null
            return ordered[2]
        }
        return null
    }
}

/** A changed/missing candidate must settle again; polling never itself dispatches an action. */
internal class NativeSendStability<T> {
    private var candidate: T? = null
    private var since = 0L
    fun observe(value: T?, now: Long): Boolean {
        if (value == null || value != candidate) { candidate = value; since = now; return false }
        return now - since >= 250L
    }
}
