package it.jarviswake

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.graphics.PixelFormat
import android.os.SystemClock
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo

/** Native session never uses WebView scripts or coordinates. Owned by the accessibility service. */
internal class NativeVoiceController(private val service: VoiceAccessibilityService, val token: String) {
    private var hud: HolographicOverlayView? = null
    private var startSent = !VoiceCycle.touchGate.canDispatch
    private var stopSent = false
    private var ending: String? = null
    private var observed = false
    private var stableCandidate: Pair<Int, android.graphics.Rect>? = null
    private var stableSince = 0L
    private var lastStatus: String? = null
    private var lastProbe: Pair<Int, Int>? = null

    fun launch(openShortcut: Boolean = true) {
        check(!service.getSystemService(KeyguardManager::class.java).isKeyguardLocked) { "Sblocca il telefono per aprire l'app ChatGPT." }
        val view = HolographicOverlayView(service)
        view.setCoverActions(
            singleTap = { toggleMute() },
            belowTap = { CoverSettings.setOpaque(service, !CoverSettings.isOpaque(service)) },
            close = { VoiceCycle.cancel(service, token, "Ciclo nativo chiuso.") },
            export = { status("TERMINA IL CICLO PRIMA DI ESPORTARE") },
            hide = { VoiceCycle.cancel(service, token, "Voice nativa terminata.") },
            revealChat = { status("MODALITÀ APP: COVER MANTENUTA") }
        )
        val params = WindowManager.LayoutParams(-1, -1, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START }
        service.getSystemService(WindowManager::class.java).addView(view, params)
        hud = view
        status("APERTURA PROGETTO CHATGPT")
        WaitingAudio.coverShown(service, token)
        if (openShortcut) {
            NativeChatSettings.launch(service)
            DiagnosticLog.record(service, DiagnosticLog.Event.NATIVE_SHORTCUT_OPENED, token)
        }
    }

    private fun nodes(): List<AccessibilityNodeInfo> {
        val roots = service.windows.filter { it.isActive || it.isFocused }.mapNotNull { it.root }
            .filter { it.packageName?.toString() == NativeChatSettings.PACKAGE }
        if (roots.size != 1) return emptyList()
        val result = ArrayList<AccessibilityNodeInfo>()
        fun visit(node: AccessibilityNodeInfo, depth: Int) {
            if (depth > 40 || result.size >= 1500) return
            if (node.isVisibleToUser) result.add(node)
            for (i in 0 until node.childCount) node.getChild(i)?.let { visit(it, depth + 1) }
        }
        visit(roots.single(), 0)
        return result
    }
    private fun labels(node: AccessibilityNodeInfo) = listOfNotNull(node.contentDescription?.toString(), node.text?.toString())
        .map(NativeVoiceLabels::normalize)
    private fun targets(nodes: List<AccessibilityNodeInfo>, vocabulary: Set<String>) =
        NativeControlResolver.targets(nodes, vocabulary, ::labels,
            parent = { node -> node.parent?.takeIf { it.packageName?.toString() == NativeChatSettings.PACKAGE } },
            visible = { it.isVisibleToUser }, enabled = { it.isEnabled }, clickable = { it.isClickable })
    private fun target(nodes: List<AccessibilityNodeInfo>, vocabulary: Set<String>): AccessibilityNodeInfo? =
        targets(nodes, vocabulary).singleOrNull()
    private fun projectVisible(nodes: List<AccessibilityNodeInfo>): Boolean {
        val expected = NativeVoiceLabels.normalize(NativeChatSettings.project(service))
        return expected.isNotEmpty() && nodes.any { !it.isEditable && labels(it).contains(expected) }
    }
    private fun status(message: String) {
        hud?.setStatus(message)
        if (message != lastStatus) {
            lastStatus = message
            State.setAttempt(service, message)
        }
    }
    fun poll() {
        if (!VoiceCycle.matches(token)) return
        val nodes = nodes()
        if (nodes.isEmpty()) { status("ATTENDO LA FINESTRA CHATGPT"); return }
        val stop = target(nodes, NativeVoiceLabels.stop)
        val mute = target(nodes, NativeVoiceLabels.mute)
        val unmute = target(nodes, NativeVoiceLabels.unmute)
        val active = stop != null && (mute != null || unmute != null)
        val starts = targets(nodes, NativeVoiceLabels.start)
        val start = starts.singleOrNull()
        if (ending != null) {
            if (stop != null && !stopSent) {
                stopSent = true
                DiagnosticLog.record(service, DiagnosticLog.Event.NATIVE_STOP_COMMAND, token)
                stop.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                status("ATTENDO CONFERMA ARRESTO VOICE")
            } else if (!active && start != null && projectVisible(nodes)) {
                val message = ending!!
                startSent = false
                service.finishNative(token, message)
            } else status("ARRESTO DA VERIFICARE — MICROFONO WAKE SOSPESO")
            return
        }
        if (!startSent) {
            if (active) { VoiceCycle.cancel(service, token, "Voice era già attiva: nessun nuovo comando inviato."); return }
            if (!projectVisible(nodes)) { status("ATTENDO CONFERMA DEL PROGETTO"); return }
            if (start == null) {
                val labelled = nodes.count { labels(it).any(NativeVoiceLabels.start::contains) }
                val probe = labelled to starts.size
                if (lastProbe != probe) {
                    lastProbe = probe
                    DiagnosticLog.record(service, DiagnosticLog.Event.NATIVE_CONTROL_PROBE, token,
                        visibleNodes = labelled, eligibleNodes = starts.size)
                }
                status(if (starts.isEmpty()) "PULSANTE VOICE NON RICONOSCIUTO" else "PIÙ PULSANTI VOICE: ${starts.size}")
                return
            }
            val bounds = android.graphics.Rect().also { start.getBoundsInScreen(it) }
            val candidate = start.windowId to bounds
            if (candidate != stableCandidate) {
                stableCandidate = candidate; stableSince = SystemClock.elapsedRealtime(); return
            }
            if (SystemClock.elapsedRealtime() - stableSince < 250L || !WakeService.isMicrophoneReleased() ||
                !WaitingAudio.canActivateVoice(token) || !VoiceCycle.touchGate.canDispatch) return
            // Consume before dispatch, including rejected or uncertain actions. Never auto-retry.
            startSent = true
            VoiceCycle.touchGate.block()
            DiagnosticLog.record(service, DiagnosticLog.Event.NATIVE_VOICE_COMMAND, token)
            start.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            status("COMANDO VOICE INVIATO — ATTENDO CONFERMA")
        } else if (active) {
            if (!observed) DiagnosticLog.record(service, DiagnosticLog.Event.NATIVE_VOICE_CONFIRMED, token)
            observed = true
            WaitingAudio.voiceActive(token)
            VoiceCycle.voiceDispatched(token)
            hud?.setVoiceActive()
            hud?.setMicrophoneMuted(unmute != null)
        } else if (observed && start != null && projectVisible(nodes)) {
            startSent = false
            service.finishNative(token, "Voice nativa terminata; ascolto riattivato.")
        }
    }
    fun requestEnd(message: String): Boolean {
        if (!startSent) return false
        ending = message
        WaitingAudio.stop()
        status("ARRESTO VOICE NATIVA")
        return true
    }
    private fun toggleMute() {
        if (!observed || ending != null) return
        val nodes = nodes()
        val mute = target(nodes, NativeVoiceLabels.mute)
        val unmute = target(nodes, NativeVoiceLabels.unmute)
        val button = if ((mute == null) != (unmute == null)) mute ?: unmute else null
        button?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }
    fun destroy() {
        hud?.let { runCatching { service.getSystemService(WindowManager::class.java).removeView(it) } }
        hud = null
    }
}
