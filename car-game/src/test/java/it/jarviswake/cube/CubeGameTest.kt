package it.jarviswake.cube

import kotlin.math.PI
import org.junit.Assert.*
import org.junit.Test

class CubeGameTest {
    @Test fun allSixFacesCanBeAlignedAndScoredExactlyOnce() {
        val orientations = listOf(PI to 0.0, 0.0 to 0.0, PI/2 to 0.0, -PI/2 to 0.0, 0.0 to -PI/2, 0.0 to PI/2)
        for ((i, angles) in orientations.withIndex()) {
            val g = CubeGame(); g.restore(angles.first, angles.second, 0, i+1, true)
            assertTrue(g.aligned()); assertTrue(g.confirm()); assertEquals(1, g.score)
            assertFalse(g.confirm()); assertEquals(1, g.score)
        }
    }
    @Test fun wrongFaceAndObliqueFaceDoNotScore() {
        val g = CubeGame(); g.restore(0.0, 0.0, 0, 4, true)
        assertFalse(g.confirm()); assertEquals(0, g.score)
        g.restore(0.7, 0.0, 0, 2, true); assertFalse(g.confirm())
    }
    @Test fun playerRotationStopsAutomaticSpinUntilShuffle() {
        val g = CubeGame(); g.rotate(0.2, -0.1); val angle = g.yaw
        g.spin(20.0); assertEquals(angle, g.yaw, 0.0)
        g.shuffle(); val shuffled = g.yaw; g.spin(0.03); assertNotEquals(shuffled, g.yaw)
    }
    @Test fun longFrameGapCannotJumpTheCube() {
        val g = CubeGame(); val y = g.yaw; g.spin(500.0)
        assertEquals(0.019, g.yaw - y, 0.000001)
    }
    @Test fun damagedSavedStateAndNonFiniteInputStayBounded() {
        val g = CubeGame(); g.restore(Double.NaN, Double.POSITIVE_INFINITY, -10, 100, true)
        g.rotate(Double.NEGATIVE_INFINITY, Double.NaN)
        assertEquals(0, g.score); assertEquals(6, g.target)
        CubeGame.vertices.map(g::transformed).forEach { assertTrue(it.x.isFinite() && it.y.isFinite() && it.z.isFinite()) }
    }
    @Test fun rotationPreservesLengthAcrossManyRounds() {
        val g = CubeGame()
        repeat(1000) { g.rotate(0.17, -0.11) }
        CubeGame.vertices.map(g::transformed).forEach { assertEquals(3.0, it.x*it.x + it.y*it.y + it.z*it.z, 0.000001) }
    }
}
