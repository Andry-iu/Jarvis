package it.jarviswake

/** UI dismissal is monotonic; an uncertain stop never acquires the screen again. */
internal data class NativeExitState(
    var coverDismissed: Boolean = false,
    var stopRequested: Boolean = false,
    var stopSent: Boolean = false,
    var returnHome: Boolean = false,
    var requestedAt: Long? = null,
    var errorReported: Boolean = false
) {
    fun dismiss(home: Boolean, now: Long): Boolean {
        coverDismissed = true
        if (requestedAt == null) requestedAt = now
        if (stopRequested) return false
        returnHome = home
        stopRequested = true
        return true
    }
    fun consumeStop(): Boolean {
        if (!stopRequested || stopSent) return false
        stopSent = true
        return true
    }
    fun reportTimeout(now: Long): Boolean {
        val start = requestedAt ?: return false
        if (!coverDismissed || !stopRequested || errorReported || now - start < 5000L) return false
        errorReported = true
        return true
    }
}
