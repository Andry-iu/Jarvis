package it.jarviswake

import android.content.Context
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import java.io.File
import java.time.Instant

/** Dedicated phase history; closing/exporting must not overwrite the post-write enumeration. */
internal object NativeButtonSnapshot {
    const val FILE_NAME = "native-buttons.log"
    private var fileCycle: String? = null // Accessed only on DiagnosticLog's serial executor.
    private fun clean(value: CharSequence?) = value?.toString().orEmpty()
        .replace(Regex("[\\r\\n\\t\\p{Cntrl}]"), " ").take(240)

    fun describe(nodes: List<AccessibilityNodeInfo>): String = buildString {
        var controls = 0
        var labelNodes = 0
        val entries = ArrayList<String>()
        nodes.forEachIndexed { index, node ->
            if (node.packageName?.toString() != NativeChatSettings.PACKAGE) return@forEachIndexed
            val chain = ArrayList<AccessibilityNodeInfo>()
            var current: AccessibilityNodeInfo? = node
            repeat(41) {
                val candidate = current
                if (candidate != null && candidate.packageName?.toString() == NativeChatSettings.PACKAGE) {
                    chain.add(candidate)
                    current = candidate.parent
                } else current = null
            }
            fun actionable(candidate: AccessibilityNodeInfo) = candidate.isClickable || candidate.isLongClickable ||
                candidate.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK || it.id == AccessibilityNodeInfo.ACTION_LONG_CLICK }
            val owner = chain.firstOrNull(::actionable)
            val buttonRole = chain.any { it.className?.toString()?.endsWith("Button") == true }
            val control = actionable(node) || node.className?.toString()?.endsWith("Button") == true
            val ancestryVerified = chain.any { it == nodes.firstOrNull() }
            val sensitive = !ancestryVerified || chain.any { it.isEditable || it.isPassword }
            // Non-clickable label nodes remain tied to their actionable parent, including Compose.
            val description = if (!sensitive) node.contentDescription?.toString() else null
            val buttonText = if (!sensitive && buttonRole) node.text?.toString() else null
            val labelNode = !control && owner != null && (!description.isNullOrBlank() || !buttonText.isNullOrBlank())
            if (!control && !labelNode) return@forEachIndexed
            if (control) controls++ else labelNodes++
            val bounds = Rect().also { node.getBoundsInScreen(it) }
            val metadata = "kind=${if (control) "control" else "label"} node=$index parent=${nodes.indexOf(node.parent)} owner=${nodes.indexOf(owner)} window=${node.windowId} class=${clean(node.className)} id=${clean(node.viewIdResourceName)} bounds=$bounds visible=${node.isVisibleToUser} enabled=${node.isEnabled} clickable=${node.isClickable} longClickable=${node.isLongClickable} focusable=${node.isFocusable} focused=${node.isFocused} editable=${node.isEditable} password=${node.isPassword} sensitive=$sensitive ancestryVerified=$ancestryVerified actions=${node.actionList.joinToString(",") { it.id.toString() }}"
            entries.add(NativeButtonEntry(metadata, sensitive, buttonRole, description,
                buttonText,
                if (!sensitive) node.actionList.map { "${it.id}:${clean(it.label)}" } else emptyList()).render())
        }
        appendLine("visibleNodes=${nodes.size} controls=$controls labelNodes=$labelNodes; editable/password and generic page text omitted")
        for ((entryIndex, entry) in entries.withIndex()) {
            if (length + entry.length > 64 * 1024) { appendLine("TRUNCATED remainingEntries=${entries.size - entryIndex}"); break }
            appendLine(entry)
        }
    }

    @Suppress("DEPRECATION")
    fun capture(context: Context, token: String, phase: String, nodes: List<AccessibilityNodeInfo>) {
        val app = context.applicationContext
        val requestedAt = Instant.now()
        // Copies preserve this phase without blocking the gesture thread with parent queries or I/O.
        val copies = nodes.map { AccessibilityNodeInfo.obtain(it) }
        DiagnosticLog.afterPendingWrites {
            try {
                runCatching {
                    val text = describe(copies)
                    val info = runCatching { app.packageManager.getPackageInfo(app.packageName, 0) }.getOrNull()
                    val header = "snapshot time=$requestedAt cycle=$token phase=$phase version=${clean(info?.versionName)} versionCode=${info?.longVersionCode ?: -1}\n"
                    val file = File(app.filesDir, "diagnostics/$FILE_NAME")
                    file.parentFile?.mkdirs()
                    if (fileCycle != token || file.length() > 2 * 1024 * 1024) {
                        fileCycle = token
                        file.writeText(header + text)
                    } else file.appendText("\n" + header + text)
                }
            } finally {
                copies.forEach { it.recycle() }
            }
        }
    }
}
