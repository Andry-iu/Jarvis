package it.jarviswake

/** Never overwrite a draft or repeat insertion/send after an uncertain native action. */
internal object NativeGreetingPolicy {
    const val WAITING = 0
    const val WRITING = 1
    const val DISPATCHED = 2
    const val ABORTED = 3
    enum class Action { WAIT, WRITE, SEND, ABORT, NONE }
    fun decide(stage: Int, elapsedMs: Long, editableCount: Int, empty: Boolean,
        matchesGreeting: Boolean, canWrite: Boolean, canSend: Boolean): Action {
        if (stage == DISPATCHED || stage == ABORTED) return Action.NONE
        if (elapsedMs >= 10_000L) return Action.ABORT
        if (editableCount != 1) return Action.WAIT
        return when (stage) {
            WAITING -> if (!empty) Action.ABORT else if (canWrite) Action.WRITE else Action.WAIT
            WRITING -> if (matchesGreeting && canSend) Action.SEND
                else if (!empty && !matchesGreeting) Action.ABORT else Action.WAIT
            else -> Action.ABORT
        }
    }
}
