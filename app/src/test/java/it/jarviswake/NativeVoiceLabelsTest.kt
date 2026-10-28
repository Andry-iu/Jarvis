package it.jarviswake

import org.junit.Assert.*
import org.junit.Test

class NativeVoiceLabelsTest {
    @Test fun excludesDictationSendAndSettingsFromStartCandidates() {
        for (label in listOf("Microphone", "Send", "Voice settings", "Read aloud", "Close", "Settings", "New chat")) {
            assertFalse(NativeVoiceLabels.normalize(label) in NativeVoiceLabels.start)
        }
        assertTrue(NativeVoiceLabels.normalize("  VOICE  ") in NativeVoiceLabels.start)
        assertTrue(NativeVoiceLabels.normalize("Avvia   modalità vocale") in NativeVoiceLabels.start)
    }
    @Test fun stoppingCannotTargetGenericCloseOrStart() {
        for (label in listOf("Close", "Exit", "Back", "Voice", "Microphone", "Send")) {
            assertFalse(NativeVoiceLabels.normalize(label) in NativeVoiceLabels.stop)
        }
        assertTrue("end voice chat" in NativeVoiceLabels.stop)
        assertTrue(NativeVoiceLabels.start.intersect(NativeVoiceLabels.stop).isEmpty())
        assertTrue(NativeVoiceLabels.mute.intersect(NativeVoiceLabels.unmute).isEmpty())
    }
}
