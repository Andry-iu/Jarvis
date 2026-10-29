package it.jarviswake

import java.lang.ref.WeakReference
import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

class VoiceAccessibilityService : AccessibilityService() {
    companion object {
        private const val PREFS = "voice_accessibility"
        private const val CYCLE = "cycle"
        private const val CLICKED_AT = "clicked_at"
        private const val OBSERVED = "active_observed"
        private const val INACTIVE_SINCE = "inactive_since"
        private const val GREETING_STARTED = "greeting_started"
        private const val GREETING_DONE = "greeting_done"
        private var instance = WeakReference<VoiceAccessibilityService>(null)

        fun restoreNativeAfterExport(token: String?) {
            instance.get()?.nativeController?.takeIf { it.token == token }?.restoreAfterExport()
        }

        fun launchNative(token: String): Boolean {
            val service = instance.get() ?: return false
            val controller = NativeVoiceController(service, token)
            service.nativeController = controller
            service.preferences.edit().putString("native_token", token).apply()
            controller.launch()
            return true
        }

        fun endNative(context: Context, token: String, message: String): Boolean {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (prefs.getString("native_token", null) != token) return false
            val controller = instance.get()?.nativeController?.takeIf { it.token == token }
            val mustWait = controller?.requestEnd(message) ?: !VoiceCycle.touchGate.canDispatch
            if (mustWait) {
                prefs.edit().putBoolean("native_stop", true).apply()
                State.setAttempt(context, "Arresto Voice nativa da confermare; microfono wake sospeso.")
            }
            return mustWait
        }

        fun isEnabled(context: Context): Boolean {
            val expected = ComponentName(context, VoiceAccessibilityService::class.java)
            val enabled = Settings.Secure.getString(context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == expected }
        }

        fun armWeb(context: Context, token: String): Boolean {
            if (!VoiceCycle.matches(token) || !isEnabled(context)) return false
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().putString(CYCLE, token).apply()
            instance.get()?.startPolling()
            return true
        }

        fun disarm(context: Context) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
            instance.get()?.stopPolling()
        }

        fun coverHidden(token: String, returnHome: Boolean = true) {
            instance.get()?.takeIf { VoiceCycle.matches(token) }?.hideCover(returnHome)
        }

        fun showMicrophoneMuted(token: String, muted: Boolean) {
            instance.get()?.takeIf { VoiceCycle.matches(token) }?.overlay?.setMicrophoneMuted(muted)
            WebChatActivity.showMicrophoneMuted(token, muted)
        }

        fun showCoverStatus(token: String, message: String) {
            instance.get()?.takeIf { VoiceCycle.matches(token) }?.overlay?.setGestureMessage(message)
            WebChatActivity.showCoverStatus(token, message)
        }

        fun resumeWeb(token: String) {
            val service = instance.get()?.takeIf { VoiceCycle.matches(token) } ?: return
            service.preferences.edit().putString(CYCLE, token).putBoolean(OBSERVED, false)
                .remove(INACTIVE_SINCE).remove(CLICKED_AT).apply()
            service.startPolling()
        }

        fun openDiagnosticsExport() {
            val service = instance.get() ?: return
            service.hideOverlay()
            DiagnosticExportActivity.open(service)
        }
    }

    private val main = Handler(Looper.getMainLooper())
    private var nativeController: NativeVoiceController? = null

    private fun nativeExitState() = NativeExitState(
        coverDismissed = preferences.getBoolean("native_cover_dismissed", false),
        stopRequested = preferences.getBoolean("native_stop", false),
        stopSent = preferences.getBoolean("native_stop_sent", false),
        returnHome = preferences.getBoolean("native_return_home", false),
        requestedAt = if (preferences.contains("native_exit_at")) preferences.getLong("native_exit_at", 0L) else null,
        errorReported = preferences.getBoolean("native_exit_error", false)
    )

    private fun saveNativeExit(state: NativeExitState) {
        preferences.edit().putBoolean("native_cover_dismissed", state.coverDismissed)
            .putBoolean("native_stop", state.stopRequested).putBoolean("native_stop_sent", state.stopSent)
            .putBoolean("native_return_home", state.returnHome).putBoolean("native_exit_error", state.errorReported)
            .apply { state.requestedAt?.let { putLong("native_exit_at", it) } }.apply()
    }

    internal fun requestNativeExit(token: String, returnHome: Boolean, emergency: Boolean = false) {
        // Release this HUD before checking cancellation/stop state. A repeated exit must still dismiss it.
        nativeController?.takeIf { it.token == token }?.dismissForExit()
        if (!VoiceCycle.matches(token) || preferences.getString("native_token", null) != token) return
        val state = nativeExitState()
        val requestStop = state.dismiss(returnHome, SystemClock.elapsedRealtime())
        saveNativeExit(state)
        DiagnosticLog.record(this, when {
            emergency -> DiagnosticLog.Event.NATIVE_EMERGENCY_CLOSE
            returnHome -> DiagnosticLog.Event.HIDE_GESTURE_RECOGNIZED
            else -> DiagnosticLog.Event.CHAT_REVEAL_GESTURE_RECOGNIZED
        }, token)
        if (!requestStop) return // Already stopping: UI stays dismissed, never repeat stop or change destination.
        val message = "Cover chiusa; arresto Voice da confermare."
        if (!endNative(this, token, message)) finishNative(token, "Ciclo chiuso; ascolto riattivato.")
        else {
            State.setAttempt(this, "Cover chiusa. Se Voice resta attiva, terminala in ChatGPT; wake sospesa.")
            // Let the touch callback finish first. Accessibility actions cannot hold the cover on screen.
            main.post {
                if (VoiceCycle.matches(token)) runCatching { nativeController?.takeIf { it.token == token }?.poll() }
                    .onFailure { State.setAttempt(this, "Cover chiusa; Voice da terminare in ChatGPT. Wake sospesa.") }
            }
        }
    }

    internal fun nativeCoverDismissed(token: String): Boolean =
        preferences.getString("native_token", null) == token && preferences.getBoolean("native_cover_dismissed", false)

    internal fun nativeStopSent(token: String): Boolean =
        preferences.getString("native_token", null) == token && preferences.getBoolean("native_stop_sent", false)

    internal fun consumeNativeStop(token: String): Boolean {
        if (!VoiceCycle.matches(token) || preferences.getString("native_token", null) != token) return false
        val state = nativeExitState()
        if (!state.consumeStop()) return false
        saveNativeExit(state) // Persist before dispatch, including uncertain/rejected actions.
        return true
    }

    internal fun reportNativeExitTimeout(token: String): Boolean {
        if (!VoiceCycle.matches(token) || preferences.getString("native_token", null) != token) return false
        val state = nativeExitState()
        if (!state.reportTimeout(SystemClock.elapsedRealtime())) return false
        saveNativeExit(state)
        DiagnosticLog.record(this, DiagnosticLog.Event.NATIVE_STOP_WAIT_TIMEOUT, token)
        State.setAttempt(this, "Cover chiusa; arresto Voice incerto. Termina Voice in ChatGPT; wake sospesa.")
        android.widget.Toast.makeText(this, "Arresto Voice da verificare in ChatGPT. Cover chiusa; wake sospesa.",
            android.widget.Toast.LENGTH_LONG).show()
        return true
    }

    internal fun nativeGreetingStarted(token: String): Boolean =
        VoiceCycle.matches(token) && preferences.contains("native_greeting_at")

    internal fun nativeGreetingWindow(token: String): Int =
        if (VoiceCycle.matches(token)) preferences.getInt("native_greeting_window", -1) else -1

    internal fun setNativeGreetingWindow(token: String, window: Int) {
        if (VoiceCycle.matches(token)) preferences.edit().putInt("native_greeting_window", window).apply()
    }

    internal fun nativeGreetingStage(token: String): Int =
        if (preferences.getString("native_token", null) == token) preferences.getInt("native_greeting_stage", 0)
        else NativeGreetingPolicy.ABORTED

    internal fun nativeGreetingText(token: String): String {
        if (!VoiceCycle.matches(token)) return ""
        return preferences.getString("native_greeting_text", null) ?: VoiceGreetingSettings.get(this).also {
            preferences.edit().putString("native_greeting_text", it).apply()
        }
    }

    internal fun nativeGreetingElapsed(token: String): Long {
        if (!VoiceCycle.matches(token)) return Long.MAX_VALUE
        val now = SystemClock.elapsedRealtime()
        if (!preferences.contains("native_greeting_at")) preferences.edit().putLong("native_greeting_at", now).apply()
        return now - preferences.getLong("native_greeting_at", now)
    }

    internal fun setNativeGreetingStage(token: String, stage: Int) {
        if (VoiceCycle.matches(token)) preferences.edit().putInt("native_greeting_stage", stage).apply()
    }

    internal fun finishNative(token: String, message: String) {
        if (!VoiceCycle.matches(token) || preferences.getString("native_token", null) != token) return
        val returnHome = preferences.getBoolean("native_return_home", false)
        nativeController?.destroy()
        nativeController = null
        preferences.edit().remove("native_token").remove("native_stop").remove("native_return_home").apply()
        VoiceCycle.cancel(this, token, message)
        // Only reached after confirmed Voice exit, or before any Voice command was sent.
        if (returnHome) {
            val accepted = performGlobalAction(GLOBAL_ACTION_HOME)
            DiagnosticLog.record(this, if (accepted) DiagnosticLog.Event.NATIVE_HOME_COMMAND
                else DiagnosticLog.Event.NATIVE_HOME_REJECTED, token)
            if (!accepted) State.setAttempt(this, "Voice terminata; comando Home rifiutato da Android.")
        }
    }
    private val preferences by lazy { getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    private var overlay: HolographicOverlayView? = null
    private var overlayFailed = false
    private var probePending = false
    private var greetingPending = false
    private var nextGreetingAt = 0L
    private var activeShown = false
    private var lastVoiceState: WebVoicePageState? = null
    private var lastGreetingResult: VoiceGreetingResult? = null
    private val poll = object : Runnable {
        override fun run() {
            processAutomation()
            if (currentToken() != null) main.postDelayed(this, 100L)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = WeakReference(this)
        if (currentToken() != null) startPolling() else disarm(this)
    }

    private fun currentToken(): String? = preferences.getString(CYCLE, null)
        ?.takeIf { instance.get() === this && VoiceCycle.matches(it) }

    private fun startPolling() {
        stopPolling()
        overlayFailed = false
        main.post(poll)
    }

    private fun stopPolling() {
        nativeController?.destroy()
        nativeController = null
        main.removeCallbacks(poll)
        probePending = false
        greetingPending = false
        activeShown = false
        nextGreetingAt = 0L
        lastVoiceState = null
        lastGreetingResult = null
        hideOverlay()
    }

    // The single scheduled loop avoids concurrent DOM queries from accessibility events.
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    private fun processAutomation() {
        val token = currentToken() ?: return
        nativeController?.let {
            runCatching { it.poll() }.onFailure {
                VoiceCycle.cancel(this, token, "Interazione nativa non disponibile; arresto da verificare.")
            }
            return
        }
        if (preferences.getString("native_token", null) == token) {
            if (!getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
                val recovered = NativeVoiceController(this, token)
                nativeController = recovered
                runCatching {
                    recovered.launch(openShortcut = false)
                    if (preferences.getBoolean("native_stop", false)) recovered.requestEnd("Ciclo nativo chiuso.")
                }.onFailure {
                    recovered.destroy()
                    nativeController = null
                    State.setAttempt(this, "Cover nativa non disponibile; automazione sospesa.")
                }
            }
            return
        }
        if (VoiceCycle.hiddenForWake) return
        showOverlay(token)
        if (VoiceCycle.phase == VoiceCycleState.Phase.VOICE && !activeShown) {
            activeShown = true
            overlay?.setVoiceActive()
            WebChatActivity.showVoiceActive(token)
            State.set(this, "Modalità Voice attiva: nessun altro tocco Voice verrà eseguito.")
        }
        // Timeout belongs to VoiceCycle and runs even while this condition is false.
        if (!WebChatActivity.isAutomationPageReady() || probePending) return
        probePending = true
        if (VoiceCycle.phase == VoiceCycleState.Phase.WEB) {
            WebChatActivity.tapVoiceControl { result ->
                if (currentToken() != token) return@tapVoiceControl
                probePending = false
                when (result) {
                    WebVoiceTapResult.DISPATCHED, WebVoiceTapResult.BLOCKED, WebVoiceTapResult.ACTIVE -> {
                        VoiceCycle.voiceDispatched(token)
                        preferences.edit().putLong(CLICKED_AT, SystemClock.elapsedRealtime()).apply()
                        if (result == WebVoiceTapResult.ACTIVE) {
                            WaitingAudio.voiceActive(token)
                            preferences.edit().putBoolean(OBSERVED, true).apply()
                        }
                    }
                    WebVoiceTapResult.NOT_FOUND -> {
                        overlay?.setStatus("RICERCA PULSANTE VOICE")
                        WebChatActivity.showStatus(token, "RICERCA PULSANTE VOICE")
                    }
                    WebVoiceTapResult.UNAVAILABLE -> Unit
                }
            }
        } else {
            WebChatActivity.inspectVoiceState { state ->
                if (currentToken() != token) return@inspectVoiceState
                probePending = false
                val now = SystemClock.elapsedRealtime()
                if (state != lastVoiceState) {
                    lastVoiceState = state
                    val event = when (state) {
                        WebVoicePageState.ACTIVE -> DiagnosticLog.Event.VOICE_STATE_ACTIVE
                        WebVoicePageState.START_READY -> DiagnosticLog.Event.VOICE_STATE_START_READY
                        WebVoicePageState.NOT_FOUND -> DiagnosticLog.Event.VOICE_STATE_NOT_FOUND
                        WebVoicePageState.UNAVAILABLE -> DiagnosticLog.Event.VOICE_STATE_UNAVAILABLE
                    }
                    DiagnosticLog.record(this, event, token)
                }
                when (state) {
                    WebVoicePageState.ACTIVE -> {
                        WaitingAudio.voiceActive(token)
                        preferences.edit().putBoolean(OBSERVED, true).remove(INACTIVE_SINCE).apply()
                        tryGreeting(token, now)
                    }
                    WebVoicePageState.START_READY -> {
                        if (!preferences.getBoolean(OBSERVED, false)) return@inspectVoiceState
                        val inactive = preferences.getLong(INACTIVE_SINCE, 0L)
                        if (inactive == 0L) preferences.edit().putLong(INACTIVE_SINCE, now).apply()
                        else if (now - inactive >= 500L && now - preferences.getLong(CLICKED_AT, now) >= 2_000L) {
                            VoiceCycle.cancel(this, token, "Conversazione terminata. Ascolto Hey Jarvis riattivato.")
                        }
                    }
                    WebVoicePageState.NOT_FOUND, WebVoicePageState.UNAVAILABLE ->
                        preferences.edit().remove(INACTIVE_SINCE).apply()
                }
            }
        }
    }

    private fun tryGreeting(token: String, now: Long) {
        if (preferences.getBoolean(GREETING_DONE, false) || greetingPending || now < nextGreetingAt) return
        val started = preferences.getLong(GREETING_STARTED, 0L)
        if (started == 0L) {
            preferences.edit().putLong(GREETING_STARTED, now).apply()
            DiagnosticLog.record(this, DiagnosticLog.Event.GREETING_ATTEMPTED, token)
            State.setAttempt(this, "Voice rilevata; attendo campo testo e pulsante Invia per «ciao jarvis».")
        }
        greetingPending = true
        WebChatActivity.sendGreetingToVoice { result ->
            if (currentToken() != token) return@sendGreetingToVoice
            greetingPending = false
            nextGreetingAt = SystemClock.elapsedRealtime() + 100L
            if (result != lastGreetingResult) {
                lastGreetingResult = result
                val event = when (result) {
                    VoiceGreetingResult.SENT -> DiagnosticLog.Event.GREETING_SENT
                    VoiceGreetingResult.COMPOSER_MISSING -> DiagnosticLog.Event.GREETING_COMPOSER_MISSING
                    VoiceGreetingResult.COMPOSER_WAITING -> DiagnosticLog.Event.GREETING_COMPOSER_WAITING
                    VoiceGreetingResult.COMPOSER_UNAVAILABLE -> DiagnosticLog.Event.GREETING_COMPOSER_UNAVAILABLE
                    VoiceGreetingResult.SEND_NOT_READY -> DiagnosticLog.Event.GREETING_SEND_WAITING
                    VoiceGreetingResult.COMPOSER_OCCUPIED -> DiagnosticLog.Event.GREETING_COMPOSER_OCCUPIED
                    VoiceGreetingResult.UNAVAILABLE -> DiagnosticLog.Event.GREETING_ABORTED
                }
                DiagnosticLog.record(this, event, token)
            }
            when (VoiceGreetingRetryPolicy.action(result)) {
                VoiceGreetingAction.RETRY -> Unit // No click occurred; continue until ready or cycle cancellation.
                VoiceGreetingAction.SENT -> {
                    preferences.edit().putBoolean(GREETING_DONE, true).apply()
                    State.setAttempt(this, "Invio di «ciao jarvis» richiesto una volta; risposta vocale da verificare.")
                }
                VoiceGreetingAction.ABORT -> {
                    preferences.edit().putBoolean(GREETING_DONE, true).apply()
                    State.setAttempt(this, when (result) {
                        VoiceGreetingResult.COMPOSER_OCCUPIED -> "Saluto non inviato: il campo contiene già testo. Voice resta attiva."
                        else -> "Saluto interrotto: esito dell'invio non verificabile; nessun nuovo invio per evitare duplicati."
                    })
                }
            }
        }
    }

    private fun showOverlay(token: String) {
        if (overlay != null) {
            // The Web Activity may have been recreated while this service stayed alive.
            WebChatActivity.setExternalCoverVisible(token, true)
            return
        }
        if (overlayFailed || !Settings.canDrawOverlays(this) ||
            !WebChatActivity.isCycleVisible(token)) return
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard.isDeviceLocked || keyguard.isKeyguardLocked) return
        val manager = getSystemService(WindowManager::class.java)
        val hud = HolographicOverlayView(this).apply { setMicrophoneMuted(VoiceCycle.microphoneMuted) }
        hud.setCoverActions(
            singleTap = { WebChatActivity.toggleMicrophone(token) },
            belowTap = { CoverSettings.setOpaque(this, !CoverSettings.isOpaque(this)) },
            close = { VoiceCycle.cancel(this, token, "Jarvis chiuso. Ascolto Hey Jarvis riattivato.") },
            export = { openDiagnosticsExport() },
            hide = { VoiceCycle.hideForWake(this, token, returnHome = true) },
            revealChat = { VoiceCycle.hideForWake(this, token, returnHome = false) }
        )
        val hudParams = WindowManager.LayoutParams(-1, -1,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        try {
            manager.addView(hud, hudParams)
            overlay = hud
            hud.post { WaitingAudio.coverShown(this, token) }
            if (VoiceCycle.phase == VoiceCycleState.Phase.VOICE) hud.setVoiceActive()
            WebChatActivity.setExternalCoverVisible(token, true)
        } catch (e: RuntimeException) {
            // The Activity retains its local interactive cover if the external overlay fails.
            runCatching { manager.removeView(hud) }
            overlayFailed = true
            State.setAttempt(this, "HUD esterno non disponibile; uso la cover nella schermata ChatGPT.")
        }
    }

    private fun hideCover(returnHome: Boolean) {
        hideOverlay()
        if (returnHome) performGlobalAction(GLOBAL_ACTION_HOME)
    }

    private fun hideOverlay() {
        val manager = getSystemService(WindowManager::class.java)
        overlay?.let { runCatching { manager.removeView(it) } }
        overlay = null
        VoiceCycle.id?.let { WebChatActivity.setExternalCoverVisible(it, VoiceCycle.hiddenForWake) }
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    override fun onInterrupt() = hideOverlay()
    override fun onDestroy() {
        stopPolling()
        if (instance.get() === this) instance.clear()
        super.onDestroy()
    }
}
