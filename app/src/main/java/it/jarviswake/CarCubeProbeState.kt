package it.jarviswake

/** One bounded, opt-in display experiment. Never persists display IDs or retries a cycle. */
internal class CarCubeProbeState {
    companion object { const val WINDOW_MS = 60_000L }
    var displayId: Int? = null
        private set
    var armedAt = -1L
        private set
    var generation = 0L
        private set
    var cycle: String? = null
        private set
    var attempted = false
        private set
    var closed = false
        private set
    var rendered = false
        private set

    fun arm(target: Int, now: Long) {
        require(target > 0 && now >= 0)
        generation++
        displayId = target
        armedAt = now
        cycle = null
        attempted = false
        closed = false
        rendered = false
    }

    fun armed(now: Long) = displayId != null && armedAt >= 0 &&
        now >= armedAt && now - armedAt < WINDOW_MS

    fun consume(token: String, now: Long, projection: Boolean, unlocked: Boolean, allowed: Boolean): String {
        if (!armed(now)) return "not_armed"
        if (cycle == token && attempted) return "already_attempted"
        if (token.isBlank()) return "no_cycle"
        cycle = token
        attempted = true // Consume failures too: reconnection must not silently replay a launch.
        closed = false
        rendered = false
        val result = when {
            !projection -> "no_projection"
            !unlocked -> "phone_locked"
            !allowed -> "display_denied"
            else -> "dispatch"
        }
        closed = result != "dispatch"
        return result
    }

    fun accepts(token: String?, expectedGeneration: Long, actualDisplay: Int, now: Long) =
        armed(now) && attempted && !closed && token != null && token == cycle &&
            expectedGeneration == generation && actualDisplay == displayId

    fun frame(token: String?, expectedGeneration: Long, actualDisplay: Int, now: Long): Boolean {
        if (!accepts(token, expectedGeneration, actualDisplay, now) || rendered) return false
        rendered = true
        return true
    }

    fun closeCycle() { closed = true }
    fun disarm() { displayId = null; armedAt = -1L; generation++; closed = true }
}
