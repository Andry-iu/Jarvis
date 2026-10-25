package it.jarviswake

import android.app.Application
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.UUID

/** Owns cancellation and deadlines, including before accessibility or WebView is ready. */
internal object VoiceCycle {
    const val EXTRA_ID = "it.jarviswake.CYCLE_ID"
    private var state = VoiceCycleState()
    private var hideStopInFlightToken: String? = null
    var microphoneMuted = false
        private set
    fun confirmMicrophoneMuted(token: String, muted: Boolean) {
        if (matches(token)) microphoneMuted = muted
    }
    private val main = Handler(Looper.getMainLooper())
    private var app: Application? = null
    val active: Boolean get() = state.id != null
    val hiddenForWake: Boolean get() = state.hiddenForWake
    val id: String? get() = state.id
    val phase: VoiceCycleState.Phase get() = state.phase
    val touchGate: WebVoiceTouchGate get() = state.touchGate
    fun matches(token: String?) = state.matches(token)

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) {
                id?.let { cancel(context, it, "Telefono bloccato. Ciclo chiuso; ascolto Hey Jarvis riattivato.") }
            }
        }
    }
    private val watchdog = object : Runnable {
        override fun run() {
            val context = app ?: return
            val token = id ?: return
            if (state.expired(SystemClock.elapsedRealtime())) {
                val reason = if (phase == VoiceCycleState.Phase.UNLOCK) "Schermata Jarvis non pronta entro 45 secondi"
                    else "ChatGPT / Voice non disponibili entro 40 secondi"
                cancel(context, token, "$reason. Ciclo chiuso; ascolto Hey Jarvis riattivato.")
            } else if (phase != VoiceCycleState.Phase.VOICE) {
                main.postDelayed(this, 250L)
            }
        }
    }

    fun start(context: Context, existingCycleId: String? = null) {
        if (active) return
        state = VoiceCycleState()
        microphoneMuted = false
        hideStopInFlightToken = null
        val token = existingCycleId ?: UUID.randomUUID().toString()
        state.begin(token, SystemClock.elapsedRealtime())
        WaitingAudio.begin(token)
        DiagnosticLog.record(context, DiagnosticLog.Event.CYCLE_STARTED, token)
        app = context.applicationContext as Application
        try {
            val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
            if (Build.VERSION.SDK_INT >= 33) app!!.registerReceiver(screenOff, filter, Context.RECEIVER_NOT_EXPORTED)
            else app!!.registerReceiver(screenOff, filter)
            WakeService.pauseForVoiceCycle()
            main.post(watchdog)
            State.setAttempt(context, "Ciclo richiesto; preparo Jarvis anche a telefono bloccato.")
            context.startActivity(Intent(context, LaunchActivity::class.java)
                .putExtra(EXTRA_ID, token)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        } catch (e: RuntimeException) {
            cancel(context, token, "Avvio ciclo rifiutato: ${e.javaClass.simpleName}. Ascolto riattivato.")
        }
    }

    fun openWeb(context: Context, token: String, resumed: Boolean, focused: Boolean): Boolean {
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        val opened = state.openWeb(token, SystemClock.elapsedRealtime(), resumed, focused,
            keyguard.isDeviceLocked, keyguard.isKeyguardLocked, canShowWhileLocked = true)
        if (opened) DiagnosticLog.record(context, DiagnosticLog.Event.WEB_OPENED, token)
        return opened
    }

    fun voiceDispatched(token: String) {
        if (!matches(token) || phase != VoiceCycleState.Phase.WEB) return
        state.voiceDispatched(token)
        if (matches(token) && phase == VoiceCycleState.Phase.VOICE) {
            app?.let { DiagnosticLog.record(it, DiagnosticLog.Event.VOICE_DISPATCHED_FIRST, token) }
            main.removeCallbacks(watchdog)
        }
    }

    fun hideForWake(context: Context, token: String, returnHome: Boolean = true) {
        if (!matches(token) || phase != VoiceCycleState.Phase.VOICE || hiddenForWake ||
            hideStopInFlightToken == token) return
        hideStopInFlightToken = token
        DiagnosticLog.record(context, if (returnHome) DiagnosticLog.Event.HIDE_GESTURE_RECOGNIZED
            else DiagnosticLog.Event.CHAT_REVEAL_GESTURE_RECOGNIZED, token)
        VoiceAccessibilityService.showCoverStatus(token, if (returnHome) "ARRESTO VOICE IN CORSO…"
            else "ARRESTO VOICE — APRO LA CHAT…")
        WebChatActivity.endVoiceForWake(token) { released ->
            if (hideStopInFlightToken == token) hideStopInFlightToken = null
            if (!matches(token)) return@endVoiceForWake
            if (!released || !matches(token) || !state.hideForWake(token)) {
                DiagnosticLog.record(context, if (returnHome) DiagnosticLog.Event.HIDE_FOR_WAKE_FAILED
                    else DiagnosticLog.Event.CHAT_REVEAL_FAILED, token)
                VoiceAccessibilityService.showCoverStatus(token, "ERRORE — VOICE NON SI È FERMATA")
                State.setAttempt(context, "Non ho potuto fermare Voice in sicurezza; la cover resta visibile.")
                return@endVoiceForWake
            }
            WaitingAudio.stop()
            DiagnosticLog.record(context, if (returnHome) DiagnosticLog.Event.COVER_HIDDEN_FOR_WAKE
                else DiagnosticLog.Event.CHAT_REVEALED_FOR_INTERACTION, token)
            VoiceAccessibilityService.coverHidden(token, returnHome)
            WakeService.listenWhileHidden()
        }
    }

    fun resumeHidden(context: Context, token: String?): Boolean {
        if (!hiddenForWake || token == null || !state.resumeFromWake(token, SystemClock.elapsedRealtime())) return false
        WaitingAudio.begin(token)
        DiagnosticLog.record(context, DiagnosticLog.Event.WAKE_RESUMED_HIDDEN_CYCLE, token)
        WakeService.pauseForVoiceCycle()
        VoiceAccessibilityService.resumeWeb(token)
        WebChatActivity.bringExistingToFront(context, token)
        State.setAttempt(context, "Wake rilevata; riapro Voice nella conversazione esistente.")
        return true
    }

    fun cancel(context: Context, token: String, message: String) {
        if (!state.cancel(token)) return
        WaitingAudio.stop()
        if (hideStopInFlightToken == token) hideStopInFlightToken = null
        DiagnosticLog.record(context, DiagnosticLog.Event.CYCLE_CANCELLED, token)
        // Invalidate first. Every pending launch/DOM callback now belongs to a dead cycle.
        main.removeCallbacks(watchdog)
        app?.let { runCatching { it.unregisterReceiver(screenOff) } }
        app = null
        VoiceAccessibilityService.disarm(context)
        LaunchActivity.closeForCycle(token)
        WebChatActivity.closeForCycle(token)
        State.setAttempt(context, message)
        State.set(context, message)
        WakeService.resumeExisting()
    }
}
