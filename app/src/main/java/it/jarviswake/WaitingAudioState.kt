package it.jarviswake

/** One playback attempt per cover opening, independent of Activity handoffs. */
internal class WaitingAudioState {
    private var token: String? = null
    private var attempted = false
    private var finished = false

    fun begin(id: String) { token = id; attempted = false; finished = false }
    fun request(id: String, enabled: Boolean, fileAvailable: Boolean): Boolean {
        if (id != token || attempted || finished || !enabled || !fileAvailable) return false
        attempted = true
        return true
    }
    fun finish(id: String): Boolean {
        if (id != token) return false
        finished = true
        return true
    }
    fun mayPlay(id: String) = token == id && attempted && !finished
}
