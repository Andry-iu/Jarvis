package it.jarviswake

/** Serialize native semantic clicks until their requested state is actually observed. */
internal class ConfirmedMicrophoneTarget {
    private var desired: Boolean? = null
    private var dispatched: Boolean? = null
    val busy get() = desired != null || dispatched != null
    fun request(muted: Boolean) { desired = muted }
    fun next(observedMuted: Boolean): Boolean? {
        // A release requesting mute must survive a still-pending unmute observation.
        if (dispatched != null && dispatched != observedMuted) return null
        dispatched = null
        val target = desired ?: return null
        if (target == observedMuted) { desired = null; return null }
        dispatched = target
        return target
    }
    fun rejected() { desired = null; dispatched = null }
}
