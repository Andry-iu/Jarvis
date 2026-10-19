package it.jarviswake

import org.junit.Assert.*
import org.junit.Test

class WakePhraseTest {
    @Test fun acceptsPhraseAndNormalizesSpacing() {
        assertTrue(WakePhrase.matches("Hey  Jarvis!"))
        assertTrue(WakePhrase.matches("hey jarvis"))
    }
    @Test fun rejectsFragmentsAndExtraSpeech() {
        for (text in listOf("jarvis", "hey", "hey jarvis please", "they jarvis", "", "hey [unk] jarvis"))
            assertFalse(text, WakePhrase.matches(text))
    }
}
