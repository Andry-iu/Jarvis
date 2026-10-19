package it.jarviswake

/** Screen interactivity gates idle wake listening; keyguard state deliberately does not. */
internal object WakeScreenPolicy {
    fun shouldListen(screenInteractive: Boolean, voiceCycleActive: Boolean, stopping: Boolean): Boolean =
        screenInteractive && !voiceCycleActive && !stopping

    fun shouldPreloadForScreenOn(mainActivityAvailable: Boolean, voiceCycleActive: Boolean): Boolean =
        mainActivityAvailable && !voiceCycleActive
}
