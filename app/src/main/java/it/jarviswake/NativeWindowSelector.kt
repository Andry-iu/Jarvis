package it.jarviswake

/** Select only the foremost application, independently of keyboard/our overlay focus. */
internal object NativeWindowSelector {
    enum class Kind { APP, IME, OVERLAY, SYSTEM, OTHER }
    data class Window(val id: Int, val kind: Kind, val layer: Int, val active: Boolean,
        val focused: Boolean, val packageName: String?, val visible: Boolean)
    fun select(windows: List<Window>, target: String): Int? {
        if (windows.any { it.kind == Kind.SYSTEM && (it.active || it.focused) }) return null
        val apps = windows.filter { it.kind == Kind.APP && it.visible }
        if (apps.any { (it.active || it.focused) && it.packageName != target }) return null
        val topLayer = apps.maxOfOrNull { it.layer }
        val top = apps.filter { it.layer == topLayer }.distinctBy { it.id }
        return top.singleOrNull()?.takeIf { it.packageName == target }?.id
    }
}
