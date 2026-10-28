package it.jarviswake

import android.content.Context
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import java.io.File
import java.time.Instant

/** On-device UI-control metadata only; never editable values or generic page text. */
internal object NativeControlSnapshot {
    const val FILE_NAME = "native-controls.log"
    private fun clean(value: CharSequence?): String = value?.toString().orEmpty()
        .replace(Regex("[\\r\\n\\t\\p{Cntrl}]"), " ").take(240)

    fun describe(nodes: List<AccessibilityNodeInfo>): String = buildString {
        appendLine("visibleNodes=${nodes.size}; generic text/editable values omitted")
        nodes.forEachIndexed { index, node ->
            if (node.packageName?.toString() != NativeChatSettings.PACKAGE) return@forEachIndexed
            val chain = ArrayList<AccessibilityNodeInfo>()
            var current: AccessibilityNodeInfo? = node
            for (depth in 0..3) {
                val candidate = current ?: break
                if (candidate.packageName?.toString() != NativeChatSettings.PACKAGE) break
                chain.add(candidate)
                current = candidate.parent
            }
            if (chain.any { it.isEditable || it.isPassword }) return@forEachIndexed
            val control = chain.firstOrNull { candidate -> candidate.isClickable || candidate.actionList.any {
                it.id == AccessibilityNodeInfo.ACTION_CLICK || it.id == AccessibilityNodeInfo.ACTION_LONG_CLICK
            } }
            // Include every node's structural properties; content labels only on controls/descendants.
            val button = chain.any { it.className?.toString()?.endsWith("Button") == true }
            val bounds = Rect().also { node.getBoundsInScreen(it) }
            val ownerBounds = Rect().also { control?.getBoundsInScreen(it) }
            append("node=$index parent=${nodes.indexOf(node.parent)} class=${clean(node.className)} id=${clean(node.viewIdResourceName)} ")
            append("bounds=$bounds clickable=${node.isClickable} enabled=${node.isEnabled} ")
            append("actions=${node.actionList.joinToString(",") { it.id.toString() }} ")
            append("ownerClass=${clean(control?.className)} ownerBounds=$ownerBounds ")
            append("description=${if (control != null) clean(node.contentDescription) else "[omitted]"} ")
            appendLine("buttonText=${if (button) clean(node.text) else "[omitted]"}")
        }
    }.take(48 * 1024)

    fun save(context: Context, token: String, number: Int, text: String) {
        val app = context.applicationContext
        val header = "snapshot=$number time=${Instant.now()} cycle=$token\n"
        DiagnosticLog.afterPendingWrites {
            runCatching {
                val directory = File(app.filesDir, "diagnostics").apply { mkdirs() }
                val file = File(directory, FILE_NAME)
                if (number == 1 || file.length() > 192 * 1024) file.writeText(header + text)
                else file.appendText("\n" + header + text)
            }
        }
    }
}
