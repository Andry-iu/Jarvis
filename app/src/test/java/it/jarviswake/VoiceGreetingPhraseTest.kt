package it.jarviswake

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceGreetingPhraseTest {
    @Test fun trimsOuterWhitespaceAndPreservesPhraseContent() {
        assertEquals("Buongiorno, Jarvis!", VoiceGreetingPhrase.normalize("  Buongiorno, Jarvis!  "))
    }

    @Test fun rejectsEmptyPhraseSoSavingCannotEraseTheCurrentSetting() {
        assertNull(VoiceGreetingPhrase.normalize(" \n\t "))
    }
}
