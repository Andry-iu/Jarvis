package it.jarviswake

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverSwipeDetectorTest {
    @Test fun downwardDragFromSafeTopAreaTriggersBeforeFingerLiftAndOnlyOnce() {
        val detector = CoverSwipeDetector(density = 1f, touchSlop = 8f)
        detector.onDown(180f, 64f, 2_400, statusBarInset = 48, viewScreenY = 0)
        assertFalse(detector.onMove(180f, 120f))
        assertTrue(detector.onMove(180f, 138f))
        assertTrue(detector.wasTriggered)
        assertFalse(detector.onMove(180f, 190f))
    }

    @Test fun statusBarEdgeAndLowerScreenStartsAreIgnored() {
        val detector = CoverSwipeDetector(density = 1f, touchSlop = 8f)
        detector.onDown(180f, 20f, 2_400, statusBarInset = 48, viewScreenY = 0)
        assertFalse(detector.onMove(180f, 150f))
        detector.onDown(180f, 1_400f, 2_400, statusBarInset = 48, viewScreenY = 0)
        assertFalse(detector.onMove(180f, 1_600f))
    }

    @Test fun innerCoverUsesScreenPositionWhenViewStartsBelowStatusBar() {
        val detector = CoverSwipeDetector(density = 1f, touchSlop = 8f)
        detector.onDown(180f, 8f, 2_200, statusBarInset = 48, viewScreenY = 48)
        assertTrue(detector.onMove(180f, 88f))
    }

    @Test fun innerCoverRejectsStartsInsideTheStatusBarEdge() {
        val detector = CoverSwipeDetector(density = 1f, touchSlop = 8f)
        detector.onDown(180f, 2f, 2_200, statusBarInset = 48, viewScreenY = 48)
        assertFalse(detector.onMove(180f, 100f))
    }

    @Test fun horizontalAndUpwardDragsDoNotHideTheCover() {
        val detector = CoverSwipeDetector(density = 1f, touchSlop = 8f)
        detector.onDown(100f, 64f, 2_400, statusBarInset = 48, viewScreenY = 0)
        assertFalse(detector.onMove(210f, 95f))
        detector.onDown(100f, 64f, 2_400, statusBarInset = 48, viewScreenY = 0)
        assertFalse(detector.onMove(100f, 10f))
    }

    @Test fun diagonalDownwardDragStillCountsWhenVerticalTravelDominates() {
        val detector = CoverSwipeDetector(density = 1f, touchSlop = 8f)
        detector.onDown(100f, 64f, 2_400, statusBarInset = 48, viewScreenY = 0)
        assertTrue(detector.onMove(170f, 145f))
    }

    @Test fun resetAllowsANewGestureAfterAnInterruptedTouch() {
        val detector = CoverSwipeDetector(density = 1f, touchSlop = 8f)
        detector.onDown(100f, 64f, 2_400, statusBarInset = 48, viewScreenY = 0)
        detector.reset()
        assertFalse(detector.onMove(100f, 180f))
        detector.onDown(100f, 64f, 2_400, statusBarInset = 48, viewScreenY = 0)
        assertTrue(detector.onMove(100f, 140f))
    }
}
