package it.jarviswake

/** Explicit local observations are reported; dispatch never means external completion. */
internal object BridgeActionPolicy {
    fun maySend(recipientVerified:Boolean,editorCount:Int,exactText:Boolean,sendCount:Int,stable:Boolean,alreadySent:Boolean)=
        recipientVerified && editorCount==1 && exactText && sendCount==1 && stable && !alreadySent
    fun mayResumeAfterCall(stateIdle:Boolean,sawCall:Boolean,pendingAge:Long,audioFree:Boolean)=
        stateIdle && audioFree && (sawCall || pendingAge>=30_000)
}

/** A crash after consumption cannot turn into another call or send. Used on persisted records. */
internal object BridgeReplayPolicy {
    enum class Decision { RUN, SAVED, UNCERTAIN }
    fun decide(hasRecord:Boolean,hasResult:Boolean)=when {
        hasRecord && hasResult -> Decision.SAVED
        hasRecord -> Decision.UNCERTAIN
        else -> Decision.RUN
    }
}
