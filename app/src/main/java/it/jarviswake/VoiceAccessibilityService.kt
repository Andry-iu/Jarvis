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

        fun coverHidden(token: String) {
            instance.get()?.takeIf { VoiceCycle.matches(token) }?.hideAndReturnHome()
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
                        if (result == WebVoiceTapResult.ACTIVE) preferences.edit().putBoolean(OBSERVED, true).apply()
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
        val hud = HolographicOverlayView(this)
        hud.setCoverActions(
            singleTap = { CoverSettings.setOpaque(this, !CoverSettings.isOpaque(this)) },
            close = { VoiceCycle.cancel(this, token, "Jarvis chiuso. Ascolto Hey Jarvis riattivato.") },
            export = { openDiagnosticsExport() },
            hide = { VoiceCycle.hideForWake(this, token) }
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
            if (VoiceCycle.phase == VoiceCycleState.Phase.VOICE) hud.setVoiceActive()
            WebChatActivity.setExternalCoverVisible(token, true)
        } catch (e: RuntimeException) {
            // The Activity retains its local interactive cover if the external overlay fails.
            runCatching { manager.removeView(hud) }
            overlayFailed = true
            State.setAttempt(this, "HUD esterno non disponibile; uso la cover nella schermata ChatGPT.")
        }
    }

    private fun hideAndReturnHome() {
        hideOverlay()
        performGlobalAction(GLOBAL_ACTION_HOME)
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
