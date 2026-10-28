package it.jarviswake

internal object WebNavigationPolicy {
    fun allowed(url: String, mainFrame: Boolean): Boolean =
        url.startsWith("https://", ignoreCase = true) ||
            (!mainFrame && url in setOf("about:blank", "about:srcdoc"))

    fun challenged(headers: Map<String, String>?): Boolean = headers?.any {
        it.key.equals("cf-mitigated", ignoreCase = true) &&
            it.value.trim().equals("challenge", ignoreCase = true)
    } == true
}
