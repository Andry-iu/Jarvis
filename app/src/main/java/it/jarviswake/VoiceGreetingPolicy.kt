package it.jarviswake

/** Classifies results by whether the WebView definitely did not submit the greeting. */
internal enum class VoiceGreetingAction { RETRY, SENT, ABORT }

internal object VoiceGreetingRetryPolicy {
    fun action(result: VoiceGreetingResult): VoiceGreetingAction = when (result) {
        VoiceGreetingResult.COMPOSER_MISSING,
        VoiceGreetingResult.COMPOSER_WAITING,
        VoiceGreetingResult.COMPOSER_UNAVAILABLE,
        VoiceGreetingResult.SEND_NOT_READY -> VoiceGreetingAction.RETRY
        VoiceGreetingResult.SENT -> VoiceGreetingAction.SENT
        // These outcomes can be ambiguous after a document change. Stop to preserve at-most-once sending.
        VoiceGreetingResult.COMPOSER_OCCUPIED, VoiceGreetingResult.UNAVAILABLE -> VoiceGreetingAction.ABORT
    }
}
