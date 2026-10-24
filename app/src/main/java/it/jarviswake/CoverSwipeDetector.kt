package it.jarviswake

import kotlin.math.abs
import kotlin.math.max

/** Recognizes the down-to-Home and left-to-chat gestures. */
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
    private var chatEligible = false
    var wasTriggered: Boolean = false
        private set
    var triggeredAction: Action? = null
        private set

    enum class Action { HOME, CHAT }

    fun onDown(x: Float, y: Float, viewHeight: Int, statusBarInset: Int, viewScreenY: Int, viewWidth: Int = 0) {
        startX = x
        startY = y
        wasTriggered = false
        triggeredAction = null
        val screenY = y + viewScreenY
        val safeTop = max(statusBarInset + 8f * density, 8f * density)
        val maxStartY = statusBarInset + minDistanceDp * density * 4f
        val maxStartOnView = viewHeight > 0 && y <= viewHeight * startZoneFraction
        eligible = maxStartOnView && screenY >= safeTop && screenY <= maxStartY
        chatEligible = viewWidth > 0 && x >= viewWidth * 0.4f &&
            x <= viewWidth - 48f * density && screenY >= safeTop &&
            (viewHeight <= 0 || y <= viewHeight)
    }

    fun onMove(x: Float, y: Float): Boolean {
        if ((!eligible && !chatEligible) || wasTriggered) return false
        val dx = x - startX
        val dy = y - startY
        if (chatEligible && dx <= -minDistanceDp * density &&
            abs(dy) <= max(touchSlop * 2f, abs(dx) * 0.65f)) {
            wasTriggered = true
            triggeredAction = Action.CHAT
            return true
        }
        if (chatEligible && dx < -touchSlop && abs(dx) > abs(dy) * 1.2f) {
            eligible = false
            return false
        }
        if (!eligible) return false
        if (dy <= touchSlop) return false
        if (abs(dx) > max(80f * density, abs(dy) * 1.20f)) {
            eligible = false
            return false
        }
        if (dy < minDistanceDp * density) return false
        wasTriggered = true
        triggeredAction = Action.HOME
        return true
    }

    fun reset() {
        eligible = false
        chatEligible = false
        wasTriggered = false
        triggeredAction = null
    }
}
