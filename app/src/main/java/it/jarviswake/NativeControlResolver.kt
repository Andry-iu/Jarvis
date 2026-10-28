package it.jarviswake

/** Resolve labelled descendants to their nearest enabled clickable ancestor. */
internal object NativeControlResolver {
    fun <T> targets(nodes: List<T>, vocabulary: Set<String>, labels: (T) -> List<String>,
                    parent: (T) -> T?, visible: (T) -> Boolean, enabled: (T) -> Boolean,
                    clickable: (T) -> Boolean): List<T> {
        return nodes.filter { visible(it) && labels(it).any(vocabulary::contains) }.mapNotNull { labelled ->
            var current: T? = labelled
            var found: T? = null
            for (depth in 0..3) {
                val candidate = current ?: break
                if (!visible(candidate) || !enabled(candidate)) break
                if (clickable(candidate)) { found = candidate; break }
                current = parent(candidate)
            }
            found
        }.distinct()
    }
}
