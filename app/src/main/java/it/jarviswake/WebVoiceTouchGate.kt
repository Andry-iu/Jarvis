package it.jarviswake

/** One dispatch per voice cycle, shared across WebView lifetimes. Navigation and delayed callbacks cannot unlock it. */
internal class WebVoiceTouchGate {
    var canDispatch: Boolean = true
        private set

    fun block() { canDispatch = false }
}
