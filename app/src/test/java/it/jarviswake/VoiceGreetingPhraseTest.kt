package it.jarviswake

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceGreetingPhraseTest {
    @Test fun trimsOuterWhitespaceAndPreservesPhraseContent() {
        assertEquals("Buongiorno, Jarvis!", VoiceGreetingPhrase.normalize("  Buongiorno, Jarvis!  "))
    }

    @Test fun whitespaceOnlyPhraseExplicitlyDisablesTheGreeting() {
        assertEquals("", VoiceGreetingPhrase.normalize(" \n\t "))
    }
}
