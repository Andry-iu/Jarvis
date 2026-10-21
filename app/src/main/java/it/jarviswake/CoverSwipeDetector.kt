package it.jarviswake

import kotlin.math.abs
import kotlin.math.max

/** Recognizes a downward drag in screen coordinates, independent of the cover's window origin. */
internal class CoverSwipeDetector(
    density: Float,
    touchSlop: Float,
    private val startZoneFraction: Float = 0.48f,
    private val minDistanceDp: Float = 72f
) {
    private val density = density.coerceAtLeast(0.1f)
    private val touchSlop = touchSlop.coerceAtLeast(0f)
    private var startX = 0f
    private var startY = 0f
    private var eligible = false
    var wasTriggered: Boolean = false
        private set

    fun onDown(x: Float, y: Float, viewHeight: Int, statusBarInset: Int, viewScreenY: Int) {
        startX = x
        startY = y
        wasTriggered = false
        val screenY = y + viewScreenY
        val safeTop = max(statusBarInset + 8f * density, 8f * density)
        val maxStartY = statusBarInset + minDistanceDp * density * 4f
        val maxStartOnView = viewHeight > 0 && y <= viewHeight * startZoneFraction
        eligible = maxStartOnView && screenY >= safeTop && screenY <= maxStartY
    }

    fun onMove(x: Float, y: Float): Boolean {
        if (!eligible || wasTriggered) return false
        val dx = x - startX
        val dy = y - startY
        if (dy <= touchSlop) return false
        if (abs(dx) > max(80f * density, abs(dy) * 1.20f)) {
            eligible = false
            return false
        }
        if (dy < minDistanceDp * density) return false
        wasTriggered = true
        return true
    }

    fun reset() {
        eligible = false
        wasTriggered = false
    }
}
