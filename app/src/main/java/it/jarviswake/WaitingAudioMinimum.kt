package it.jarviswake

/** Monotonic playback time; readiness polling must never restart this clock. */
internal class WaitingAudioMinimum {
    companion object { const val MAX_SECONDS = 30 }
    private var token: String? = null
    private var minimumMs = 0L
    private var startedAt: Long? = null
    private var pending = false

    fun begin(id: String, seconds: Int) {
        token = id
        minimumMs = seconds.coerceIn(0, MAX_SECONDS) * 1_000L
        startedAt = null
        pending = true
    }
    fun playbackStarted(id: String, now: Long) {
        if (id == token && pending && startedAt == null) startedAt = now
    }
    fun shouldWait(id: String, now: Long): Boolean {
        if (id != token || !pending || minimumMs == 0L) return false
        val start = startedAt ?: return true
        return now - start < minimumMs
    }
    fun stop() { pending = false }
}
