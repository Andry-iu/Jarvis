package it.jarviswake

/** Preserve the installed Chromium engine version; do not hard-code a browser release. */
internal object DesktopUserAgent {
    fun from(agent: String): String = agent
        .replace(Regex("\\([^)]*Android[^)]*\\)"), "(X11; Linux x86_64)")
        .replace(Regex(" Version/[0-9.]+"), "")
        .replace(" Mobile", "")
}
