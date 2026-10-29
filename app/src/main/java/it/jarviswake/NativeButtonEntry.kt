package it.jarviswake

/** Button labels only: editable/password values and generic page text never enter the log. */
internal data class NativeButtonEntry(
    val metadata: String,
    val sensitive: Boolean,
    val buttonRole: Boolean,
    val description: String?,
    val text: String?,
    val actionLabels: List<String>
) {
    private fun clean(value: String) = value.replace(Regex("[\\r\\n\\t\\p{Cntrl}]"), " ").take(240)
    fun render(): String {
        val desc = if (sensitive) "[redacted]" else clean(description.orEmpty())
        val label = when { sensitive -> "[redacted]"; buttonRole -> clean(text.orEmpty()); else -> "[not-button-text]" }
        val actions = if (sensitive) "[redacted]" else actionLabels.joinToString("|") { clean(it) }
        return "$metadata description=[$desc] buttonText=[$label] actionLabels=[$actions]"
    }
}
