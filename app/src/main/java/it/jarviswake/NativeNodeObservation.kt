package it.jarviswake

/** Edges from one traversal. Framework source-ID equality must not collapse role/virtual nodes. */
internal class NativeNodeObservation<T : Any> {
    data class Entry<T>(val value: T, val parent: Int)
    private val collected = ArrayList<Entry<T>>()
    val entries: List<Entry<T>> get() = collected
    fun add(value: T, parent: Int): Int {
        require(parent == -1 && collected.isEmpty() || parent in collected.indices)
        collected.add(Entry(value, parent))
        return collected.lastIndex
    }
    fun indexOfIdentity(value: T): Int = collected.indices.filter { collected[it].value === value }.singleOrNull() ?: -1
}
