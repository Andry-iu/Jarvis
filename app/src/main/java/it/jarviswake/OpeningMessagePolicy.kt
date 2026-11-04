package it.jarviswake

/** The delay starts at message submission, independently of when Voice becomes available. */
internal object OpeningMessagePolicy {
    const val TEXT = "leggi le istruzioni del progetto"
    const val MIN_DELAY_MS = 300L
    fun canStart(sentAt: Long, now: Long, sendObserved: Boolean): Boolean =
        sendObserved && sentAt >= 0L && now >= sentAt && now - sentAt >= MIN_DELAY_MS
}
