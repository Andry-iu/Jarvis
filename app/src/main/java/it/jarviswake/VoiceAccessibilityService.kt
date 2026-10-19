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
    }

    private val main = Handler(Looper.getMainLooper())
    private val preferences by lazy { getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    private var overlay: HolographicOverlayView? = null
    private var closeButton: View? = null
    private var overlayFailed = false
    private var probePending = false
    private var greetingPending = false
    private var nextGreetingAt = 0L
    private var activeShown = false
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
        hideOverlay()
    }

    // The single scheduled loop avoids concurrent DOM queries from accessibility events.
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    private fun processAutomation() {
        val token = currentToken() ?: return
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
            State.setAttempt(this, "Voice rilevata; attendo campo testo e pulsante Invia per «ciao jarvis».")
        } else if (now - started >= 10_000L) {
            preferences.edit().putBoolean(GREETING_DONE, true).apply()
            State.setAttempt(this, "Voice attiva; campo o Invia non pronti entro 10 secondi. Saluto non inviato.")
            return
        }
        greetingPending = true
        WebChatActivity.sendGreetingToVoice { result ->
            if (currentToken() != token) return@sendGreetingToVoice
            greetingPending = false
            nextGreetingAt = SystemClock.elapsedRealtime() + 100L
            when (result) {
                VoiceGreetingResult.NOT_READY -> Unit // Only observe/fill; no send has happened.
                VoiceGreetingResult.SENT -> {
                    preferences.edit().putBoolean(GREETING_DONE, true).apply()
                    State.setAttempt(this, "Invio di «ciao jarvis» richiesto una volta; risposta vocale da verificare.")
                }
                VoiceGreetingResult.COMPOSER_UNAVAILABLE, VoiceGreetingResult.UNAVAILABLE -> {
                    preferences.edit().putBoolean(GREETING_DONE, true).apply()
                    State.setAttempt(this, "Saluto interrotto: campo occupato o pagina cambiata. Voice resta attiva.")
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
        val button = CycleUi.closeButton(this) {
            VoiceCycle.cancel(this, token, "ChatGPT chiuso. Ascolto Hey Jarvis riattivato.")
        }
        val hudParams = WindowManager.LayoutParams(-1, -1,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        val closeParams = WindowManager.LayoutParams(dp(56), dp(56),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT).apply { gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; y = dp(36) }
        try {
            manager.addView(hud, hudParams)
            manager.addView(button, closeParams)
            overlay = hud
            closeButton = button
            if (VoiceCycle.phase == VoiceCycleState.Phase.VOICE) hud.setVoiceActive()
            WebChatActivity.setExternalCoverVisible(token, true)
        } catch (e: RuntimeException) {
            // Do not leave an overlay without its X. The Activity retains its own cover/X.
            runCatching { manager.removeView(button) }
            runCatching { manager.removeView(hud) }
            overlayFailed = true
            State.setAttempt(this, "HUD esterno non disponibile; uso cover e X nella schermata ChatGPT.")
        }
    }

    private fun hideOverlay() {
        val manager = getSystemService(WindowManager::class.java)
        closeButton?.let { runCatching { manager.removeView(it) } }
        overlay?.let { runCatching { manager.removeView(it) } }
        closeButton = null
        overlay = null
        VoiceCycle.id?.let { WebChatActivity.setExternalCoverVisible(it, false) }
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    override fun onInterrupt() = hideOverlay()
    override fun onDestroy() {
        stopPolling()
        if (instance.get() === this) instance.clear()
        super.onDestroy()
    }
}
