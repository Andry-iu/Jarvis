package it.jarviswake

/** One attempt across Activity recreation. All transitions run on the main thread. */
internal class VoiceCycleState {
    enum class Phase { UNLOCK, WEB, VOICE }
    var id: String? = null
        private set
    var phase = Phase.UNLOCK
        private set
    var deadline = 0L
        private set
    var touchGate = WebVoiceTouchGate()
        private set
    var hiddenForWake = false
        private set

    fun begin(token: String, now: Long): Boolean {
        if (id != null) return false
        id = token
        touchGate = WebVoiceTouchGate()
        phase = Phase.UNLOCK
        deadline = now + 45_000L
        return true
    }

    fun restoreNativeExit(token: String): Boolean {
        if (id!=null || token.isBlank()) return false
        id=token
        phase=Phase.VOICE
        hiddenForWake=false
        touchGate.block()
        return true
    }

    fun matches(token: String?) = token != null && token == id

    fun openWeb(token: String, now: Long, resumed: Boolean, focused: Boolean,
                deviceLocked: Boolean, keyguardShowing: Boolean,
                canShowWhileLocked: Boolean = false): Boolean {
        if (!matches(token) || phase != Phase.UNLOCK || expired(now) ||
            !resumed || !focused ||
            ((deviceLocked || keyguardShowing) && !canShowWhileLocked)) return false
        phase = Phase.WEB
        deadline = now + 40_000L
        return true
    }

    fun voiceDispatched(token: String) {
        if (!matches(token) || phase == Phase.UNLOCK) return
        touchGate.block()
        phase = Phase.VOICE
        hiddenForWake = false
    }

    fun hideForWake(token: String): Boolean {
        if (!matches(token) || phase != Phase.VOICE || hiddenForWake) return false
        hiddenForWake = true
        return true
    }

    fun resumeFromWake(token: String, now: Long): Boolean {
        if (!matches(token) || !hiddenForWake) return false
        hiddenForWake = false
        phase = Phase.WEB
        deadline = now + 40_000L
        touchGate = WebVoiceTouchGate()
        return true
    }

    fun expired(now: Long) = id != null && phase != Phase.VOICE && now >= deadline

    fun cancel(token: String): Boolean {
        if (!matches(token)) return false
        id = null
        hiddenForWake = false
        return true
    }
}
