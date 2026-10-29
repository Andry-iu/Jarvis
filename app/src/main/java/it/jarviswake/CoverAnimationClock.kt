package it.jarviswake

/** Animation time excludes confirmed microphone-mute intervals. */
internal class CoverAnimationClock(private val startedAt: Long) {
    private var pausedAt: Long? = null
    private var pausedDuration = 0L
    fun setMuted(muted: Boolean, now: Long) {
        if (muted && pausedAt == null) pausedAt = now
        if (!muted) pausedAt?.let { pausedDuration += now - it; pausedAt = null }
    }
    fun seconds(now: Long): Float = ((pausedAt ?: now) - startedAt - pausedDuration) / 1000f
}
