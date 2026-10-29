package it.jarviswake

/** The editor must explicitly advertise SEND; Enter/Done/Search are not substitutes. */
internal object NativeEditorSendPolicy {
    fun canSend(owner: String?, expectedOwner: String, inputStarted: Boolean, hasConnection: Boolean,
        focused: Boolean, textMatches: Boolean, imeOptions: Int?, actionMask: Int, sendAction: Int): Boolean =
        owner == expectedOwner && inputStarted && hasConnection && focused && textMatches &&
            imeOptions != null && (imeOptions and actionMask) == sendAction
}
