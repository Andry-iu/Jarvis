package it.jarviswake

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceGreetingRetryPolicyTest {
    @Test fun onlyKnownNoSendOutcomesCanBeRetried() {
        listOf(VoiceGreetingResult.COMPOSER_MISSING, VoiceGreetingResult.COMPOSER_WAITING,
            VoiceGreetingResult.COMPOSER_UNAVAILABLE, VoiceGreetingResult.SEND_NOT_READY).forEach {
            assertEquals(VoiceGreetingAction.RETRY, VoiceGreetingRetryPolicy.action(it))
        }
        assertEquals(VoiceGreetingAction.SENT,
            VoiceGreetingRetryPolicy.action(VoiceGreetingResult.SENT))
        assertEquals(VoiceGreetingAction.ABORT,
            VoiceGreetingRetryPolicy.action(VoiceGreetingResult.COMPOSER_OCCUPIED))
        assertEquals(VoiceGreetingAction.ABORT,
            VoiceGreetingRetryPolicy.action(VoiceGreetingResult.UNAVAILABLE))
    }
}
