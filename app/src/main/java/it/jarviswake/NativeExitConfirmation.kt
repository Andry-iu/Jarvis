package it.jarviswake

/** Confirm the ordinary composer has returned, without relying on a mutable chat title. */
internal class NativeExitConfirmation {
    private var candidate: String? = null
    private var since = 0L
    fun reset() { candidate = null; since = 0L }
    fun observe(now: Long, startKey: String?, voiceControlsPresent: Boolean): Boolean {
        if (startKey == null || voiceControlsPresent) { reset(); return false }
        if (candidate != startKey) { candidate = startKey; since = now; return false }
        return now - since >= 250L
    }
}
