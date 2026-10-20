package it.jarviswake

/** Keeps capture active while interactive and grants a bounded grace period after screen-off. */
internal object WakeScreenPolicy {
    const val SCREEN_OFF_GRACE_MS = 30_000L

    fun shouldListen(
        screenInteractive: Boolean,
        voiceCycleActive: Boolean,
        stopping: Boolean,
        nowElapsedMs: Long = 0L,
        screenOffDeadlineElapsedMs: Long = 0L
    ): Boolean = !voiceCycleActive && !stopping &&
        (screenInteractive || (screenOffDeadlineElapsedMs > 0L && nowElapsedMs < screenOffDeadlineElapsedMs))

    fun shouldPreloadForScreenOn(mainActivityAvailable: Boolean, voiceCycleActive: Boolean): Boolean =
        mainActivityAvailable && !voiceCycleActive
}
