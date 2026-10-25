package it.jarviswake

import org.junit.Assert.assertEquals
import org.junit.Test

class CoverTapTargetTest {
    @Test fun centerAndOuterAnimationBelongToMicrophone() {
        assertEquals(CoverTapTarget.CENTER, CoverTapTarget.at(540f, 1032f, 1080, 2400))
        assertEquals(CoverTapTarget.CENTER, CoverTapTarget.at(895f, 1032f, 1080, 2400))
    }

    @Test fun belowAnimationControlsOpacityButSidesDoNot() {
        assertEquals(CoverTapTarget.BELOW, CoverTapTarget.at(540f, 1450f, 1080, 2400))
        assertEquals(CoverTapTarget.NONE, CoverTapTarget.at(30f, 1032f, 1080, 2400))
        assertEquals(CoverTapTarget.NONE, CoverTapTarget.at(540f, 200f, 1080, 2400))
    }

    @Test fun diagnosticCornerKeepsPriorityAndOutsideIsIgnored() {
        assertEquals(CoverTapTarget.EXPORT, CoverTapTarget.at(1000f, 100f, 1080, 2400))
        assertEquals(CoverTapTarget.NONE, CoverTapTarget.at(-1f, 2000f, 1080, 2400))
        assertEquals(CoverTapTarget.NONE, CoverTapTarget.at(100f, 2500f, 1080, 2400))
        assertEquals(CoverTapTarget.NONE, CoverTapTarget.at(0f, 0f, 0, 0))
    }

    @Test fun landscapeUsesActualRingRadius() {
        assertEquals(CoverTapTarget.CENTER, CoverTapTarget.at(1200f, 464f, 2400, 1080))
        assertEquals(CoverTapTarget.BELOW, CoverTapTarget.at(1200f, 900f, 2400, 1080))
    }

    @Test fun opacityEndsBeforeTheUnlockButtonAndItsMargin() {
        assertEquals(CoverTapTarget.BELOW, CoverTapTarget.at(540f, 1450f, 1080, 2400, 1500f))
        assertEquals(CoverTapTarget.NONE, CoverTapTarget.at(540f, 1500f, 1080, 2400, 1500f))
        assertEquals(CoverTapTarget.NONE, CoverTapTarget.at(540f, 1580f, 1080, 2400, 1500f))
        assertEquals(CoverTapTarget.CENTER, CoverTapTarget.at(540f, 1032f, 1080, 2400, 1500f))
    }

    @Test fun bottomOfScreenIsNotAnOpacityControlEvenWithoutUnlockButton() {
        assertEquals(CoverTapTarget.NONE, CoverTapTarget.at(540f, 2100f, 1080, 2400))
    }
}
