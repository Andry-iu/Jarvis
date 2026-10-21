package it.jarviswake

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceToggleTargetTest {
    @Test fun storedControlPointScalesWithTheCurrentWebViewSize() {
        val target = VoiceToggleTarget.capture(360f, 720f, 400, 800)!!

        assertArrayEquals(floatArrayOf(360f, 720f), target.forSize(400, 800)!!, 0.01f)
        assertArrayEquals(floatArrayOf(540f, 1080f), target.forSize(600, 1200)!!, 0.01f)
    }

    @Test fun rejectsInvalidCaptureAndUnlaidOutWebView() {
        assertNull(VoiceToggleTarget.capture(20f, 20f, 0, 800))
        assertNull(VoiceToggleTarget.capture(-1f, 20f, 400, 800))
        val target = VoiceToggleTarget.capture(20f, 20f, 400, 800)!!
        assertNull(target.forSize(400, 0))
    }
}
