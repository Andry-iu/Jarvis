package it.jarviswake

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceViewportPointTest {
    @Test fun capturesTheFreshPointAsViewportFractions() {
        val point = VoiceViewportPoint.capture(340f, 740f, 400f, 800f)!!

        assertEquals(0.85f, point.xFraction, 0.0001f)
        assertEquals(0.925f, point.yFraction, 0.0001f)
    }

    @Test fun rejectsCoordinatesOutsideTheViewport() {
        assertNull(VoiceViewportPoint.capture(401f, 740f, 400f, 800f))
        assertNull(VoiceViewportPoint.capture(340f, 740f, 0f, 800f))
    }

    @Test fun mapsTheFreshPointToTheCurrentWebViewSize() {
        val point = VoiceViewportPoint.capture(340f, 740f, 400f, 800f)!!
            .toViewPoint(800, 1600)!!

        assertEquals(680f, point.x, 0.0001f)
        assertEquals(1480f, point.y, 0.0001f)
    }
}
