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
        val button: Boolean = false, val labelled: Boolean = false, val password: Boolean = false, val unresolvedChildren: Int = 0)

    /** Numeric indices describe only this observation; they are never persisted as selectors. */
    data class Inspection(val candidate: Int? = null, val reason: String, val field: Int,
        val composer: Int = -1, val controls: Int = -1, val editors: Int = -1,
        val rejected: Int = -1, val invalidPaths: Int = 0, val foreignNodes: Int = 0)

    fun select(nodes: List<Node>, field: Int): Int? = inspect(nodes, field).candidate

    fun inspect(nodes: List<Node>, field: Int): Inspection {
        fun early(reason: String, rejected: Int = field) = Inspection(reason = reason, field = field, rejected = rejected)
        val editor = nodes.getOrNull(field) ?: return early("field_missing")
        if (!editor.sameAppWindow) return early("field_context")
        if (!editor.enabled) return early("field_disabled")
        if (!editor.editable) return early("field_not_editable")
        if (!editor.focused) return early("field_not_focused")
        if (editor.password) return early("field_password")
        // Full, acyclic parent chains are required inside a small, local composer subtree.
        var fieldChainReason = ""
        var fieldChainRejected = field
        fun chain(index: Int): List<Int>? {
            val result = ArrayList<Int>()
            var current = index
            fun invalid(reason: String): List<Int>? {
                if (index == field) { fieldChainReason = reason; fieldChainRejected = current }
                return null
            }
            while (current >= 0) {
                if (current !in nodes.indices) return invalid("parent_missing")
                if (current in result) return invalid("parent_cycle")
                if (result.size >= 40) return invalid("parent_depth")
                if (!nodes[current].sameAppWindow) return invalid("parent_context")
                result.add(current)
                current = nodes[current].parent
            }
            return result
        }
        val paths = nodes.indices.map { chain(it) }
        val invalidPaths = paths.count { it == null }
        val foreignNodes = nodes.count { !it.sameAppWindow }
        val ancestors = paths[field] ?: return early(fieldChainReason, fieldChainRejected).copy(
            invalidPaths = invalidPaths, foreignNodes = foreignNodes)
        var composer = -1
        var controlCount = 0
        var editorCount = 0
        fun report(reason: String, rejected: Int = -1, candidate: Int? = null) = Inspection(
            candidate, reason, field, composer, controlCount, editorCount, rejected, invalidPaths, foreignNodes)
        for (ancestor in ancestors.drop(1).take(4)) {
            composer = ancestor
            val subtree = nodes.indices.filter { paths[it]?.contains(composer) == true }
            val controls = subtree.filter { it != field && (nodes[it].clickable || nodes[it].clickAction) }
            controlCount = controls.size
            editorCount = subtree.count { nodes[it].editable }
            // Only the local subtree containing the editor matters. Missing siblings in other
            // parts of ChatGPT must not invalidate this complete, observed composer.
            subtree.firstOrNull { nodes[it].unresolvedChildren > 0 }?.let { return report("composer_incomplete", it) }
            if (controls.size < 3) continue
            if (controls.size != 3) return report("action_count")
            if (editorCount != 1) return report("editor_count")
            if (!nodes[composer].box.contains(editor.box)) return report("field_outside_composer", field)
            for (index in controls) {
                val n = nodes[index]
                if (!n.enabled) return report("action_disabled", index)
                if (!n.clickable) return report("action_not_clickable", index)
                if (!n.clickAction) return report("action_no_click", index)
                if (n.password || n.editable) return report("action_sensitive", index)
                if (!nodes[composer].box.contains(n.box)) return report("action_outside_composer", index)
                if (n.box.top < editor.box.bottom) return report("action_above_field_bottom", index)
                val branch = subtree.filter { paths[it]?.contains(index) == true }
                branch.firstOrNull { nodes[it].labelled }?.let { return report("action_branch_labelled", it) }
                branch.firstOrNull { nodes[it].password || nodes[it].editable }?.let {
                    return report("action_branch_sensitive", it)
                }
                if (branch.none { nodes[it].button && (paths[it]?.indexOf(index) ?: 99) <= 2 })
                    return report("action_button_role_missing", index)
            }
            val ordered = controls.sortedBy { nodes[it].box.centerX }
            val a = nodes[ordered[0]].box
            val b = nodes[ordered[1]].box
            val c = nodes[ordered[2]].box
            if (a.right > b.left || b.right > c.left) return report("actions_overlap")
            if (a.centerX >= editor.box.centerX || b.centerX <= editor.box.centerX) return report("actions_side_order")
            if (maxOf(a.top, b.top, c.top) >= minOf(a.bottom, b.bottom, c.bottom)) return report("actions_different_rows")
            fun branch(index: Int): Int? {
                val path = paths[index] ?: return null
                return path.getOrNull(path.indexOf(composer) - 1)
            }
            val editorBranch = branch(field)
            val leftBranch = branch(ordered[0])
            val rightBranch = branch(ordered[1])
            if (editorBranch == null || leftBranch == null || rightBranch == null ||
                setOf(editorBranch, leftBranch, rightBranch).size != 3 || branch(ordered[2]) != rightBranch)
                return report("composer_branch_topology")
            return report("candidate", candidate = ordered[2])
        }
        return report("too_few_local_actions")
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
