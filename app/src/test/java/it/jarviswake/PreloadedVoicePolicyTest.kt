package it.jarviswake

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PreloadedVoicePolicyTest {
    @Test fun scalesCssPointToCurrentWebViewSize() {
        val seed = PreloadedVoiceLocation(
            x = 300f, y = 600f, viewportWidth = 400f, viewportHeight = 800f, semantic = true
        )

        val point = PreloadedVoicePolicy.scale(seed, viewWidth = 900, viewHeight = 1800)

        assertEquals(675f, point?.x ?: 0f, 0.01f)
        assertEquals(1350f, point?.y ?: 0f, 0.01f)
    }

    @Test fun rejectsMissingOrInvalidDimensions() {
        val seed = PreloadedVoiceLocation(300f, 600f, 0f, 800f, semantic = true)
        assertNull(PreloadedVoicePolicy.scale(seed, 900, 1800))
        assertNull(PreloadedVoicePolicy.scale(seed.copy(viewportWidth = 400f), 0, 1800))
    }

    @Test fun seedOnlyMatchesNearbyFreshDomCandidate() {
        val seed = PreloadedViewPoint(300f, 600f)
        assertTrue(PreloadedVoicePolicy.matches(seed, 308f, 592f))
        assertFalse(PreloadedVoicePolicy.matches(seed, 309f, 600f))
    }
}
