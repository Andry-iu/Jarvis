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
    private var stopSent = service.nativeStopSent(token)
    private var ending: String? = null
    private var observed = false
    private val exitConfirmation = NativeExitConfirmation()
    private var stableCandidate: Pair<Int, android.graphics.Rect>? = null
    private var stableSince = 0L
    private var lastStatus: String? = null
    private var lastProbe: Pair<Int, Int>? = null
    private var lastWindowMetadata: String? = null
    private var lastGreetingMetadata: String? = null
    private var lastComposerMetadata: String? = null
    private var composerObservation = NativeNodeObservation<AccessibilityNodeInfo>()
    private var composerObservationComplete = false
    private data class ComposerCandidate(val node: AccessibilityNodeInfo, val field: AccessibilityNodeInfo,
        val buttonBounds: android.graphics.Rect, val fieldBounds: android.graphics.Rect)
    private val composerStability = NativeSendStability<ComposerCandidate>()
    private val buttonPhases = HashSet<String>()
    private var pendingButtonPhase: String? = null
    private var exporting = false
    private var snapshotPhase = ""
    private var snapshotCount = 0
    private var snapshotAt = 0L
    private var snapshotFingerprint: Int? = null

    fun launch(openShortcut: Boolean = true) {
        check(!service.getSystemService(KeyguardManager::class.java).isKeyguardLocked) { "Sblocca il telefono per aprire l'app ChatGPT." }
        if (!service.nativeCoverDismissed(token)) {
            val view = HolographicOverlayView(service)
            view.setCoverActions(
                singleTap = { toggleMute() },
                belowTap = { CoverSettings.setOpaque(service, !CoverSettings.isOpaque(service)) },
                close = { dismissForExit(); service.requestNativeExit(token, returnHome = false, emergency = true) },
                export = { exportControls() },
                hide = { dismissForExit(); service.requestNativeExit(token, returnHome = true) },
                revealChat = { dismissForExit(); service.requestNativeExit(token, returnHome = false) }
            )
            val params = WindowManager.LayoutParams(-1, -1, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START }
            service.getSystemService(WindowManager::class.java).addView(view, params)
            hud = view
            status("APERTURA PROGETTO CHATGPT")
            WaitingAudio.coverShown(service, token)
        }
        if (openShortcut) {
            NativeChatSettings.launch(service)
            DiagnosticLog.record(service, DiagnosticLog.Event.NATIVE_SHORTCUT_OPENED, token)
        }
    }

    private fun exportControls() {
        if (exporting) return
        val current = nodes()
        if (current.isEmpty()) { status("CHATGPT NON VISIBILE: RIPROVA L'ESPORTAZIONE"); return }
        NativeControlSnapshot.save(service, token, 1,
            "phase=manual_export startSent=$startSent stopping=${ending != null}\n" + NativeControlSnapshot.describe(current))
        captureButtons(current, "manual_export", force = true)
        exporting = true
        hud?.visibility = android.view.View.GONE
        runCatching { DiagnosticExportActivity.open(service, token) }.onFailure { restoreAfterExport() }
    }
    fun restoreAfterExport() {
        if (!VoiceCycle.matches(token)) return
        exporting = false
        hud?.visibility = if (service.nativeCoverDismissed(token)) android.view.View.GONE else android.view.View.VISIBLE
    }
    fun dismissForExit() {
        val hadHud = hud != null
        detachHud()
        if (hadHud) DiagnosticLog.record(service, DiagnosticLog.Event.NATIVE_COVER_DISMISSED, token)
    }
    private fun captureButtons(nodes: List<AccessibilityNodeInfo>, phase: String, force: Boolean = false) {
        if (!force && !buttonPhases.add(phase)) return
        runCatching { NativeButtonSnapshot.capture(service, token, phase, nodes) }
    }

    private fun captureControls(nodes: List<AccessibilityNodeInfo>, phase: String) {
        if (phase != snapshotPhase) {
            snapshotPhase = phase
            snapshotCount = 0
            snapshotAt = 0L
            snapshotFingerprint = null
        }
        val now = SystemClock.elapsedRealtime()
        if (snapshotCount >= 3 || (snapshotCount != 0 && now - snapshotAt < 1500L)) return
        snapshotAt = now
        val snapshot = NativeControlSnapshot.describe(nodes)
        val fingerprint = snapshot.hashCode()
        if (fingerprint == snapshotFingerprint) return
        snapshotFingerprint = fingerprint
        snapshotCount++
        NativeControlSnapshot.save(service, token, snapshotCount, "phase=$phase\n" + snapshot)
    }

    private fun nodes(): List<AccessibilityNodeInfo> {
        composerObservationComplete = false
        val windows = service.windows
        val roots = windows.associate { window -> window.id to runCatching { window.root }.getOrNull() }
        val descriptors = windows.map { window ->
            val root = roots[window.id]
            val kind = when (window.type) {
                android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION -> NativeWindowSelector.Kind.APP
                android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD -> NativeWindowSelector.Kind.IME
                android.view.accessibility.AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY -> NativeWindowSelector.Kind.OVERLAY
                android.view.accessibility.AccessibilityWindowInfo.TYPE_SYSTEM -> NativeWindowSelector.Kind.SYSTEM
                else -> NativeWindowSelector.Kind.OTHER
            }
            NativeWindowSelector.Window(window.id, kind, window.layer, window.isActive, window.isFocused,
                root?.packageName?.toString(), root?.isVisibleToUser ?: true)
        }
        val selected = NativeWindowSelector.select(descriptors, NativeChatSettings.PACKAGE)
        val metadata = "selected=${selected ?: -1}; " + descriptors.joinToString("; ") {
            val owner = when (it.packageName) {
                NativeChatSettings.PACKAGE -> "chatgpt"
                service.packageName -> "jarvis"
                null -> "unknown"
                else -> "other"
            }
            "id=${it.id},type=${it.kind},layer=${it.layer},active=${it.active},focused=${it.focused},owner=$owner,visible=${it.visible},root=${roots[it.id] != null}"
        }
        if (metadata != lastWindowMetadata) {
            lastWindowMetadata = metadata
            NativeRuntimeSnapshot.save(service, token, "windows", metadata)
        }
        val root = selected?.let { roots[it] } ?: return emptyList()
        val result = ArrayList<AccessibilityNodeInfo>()
        val observation = NativeNodeObservation<AccessibilityNodeInfo>()
        composerObservation = observation
        composerObservationComplete = true
        fun visit(node: AccessibilityNodeInfo, depth: Int, parent: Int) {
            if (depth > 40 || result.size >= 1500 || observation.entries.size >= 2500) {
                composerObservationComplete = false
                return
            }
            // Preserve the edge from getChild, never reconstruct it using source-ID equality.
            val index = observation.add(node, parent)
            if (node.isVisibleToUser) result.add(node)
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child == null) composerObservationComplete = false else visit(child, depth + 1, index)
            }
        }
        visit(root, 0, -1)
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
    private fun composerSend(nodes: List<AccessibilityNodeInfo>, field: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (!composerObservationComplete) {
            NativeRuntimeSnapshot.save(service, token, "composer", "reason=observation_incomplete")
            return null
        }
        val observation = composerObservation
        val fieldIndex = observation.indexOfIdentity(field)
        val descriptors = observation.entries.map { entry ->
            val node = entry.value
            val bounds = android.graphics.Rect().also(node::getBoundsInScreen)
            NativeComposerSendSelector.Node(
                parent = entry.parent,
                box = NativeComposerSendSelector.Box(bounds.left, bounds.top, bounds.right, bounds.bottom),
                sameAppWindow = node.isVisibleToUser && node.packageName?.toString() == NativeChatSettings.PACKAGE && node.windowId == field.windowId,
                enabled = node.isEnabled, editable = node.isEditable, focused = node.isFocused,
                clickable = node.isClickable, clickAction = node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK },
                button = node.className?.toString() == "android.widget.Button",
                labelled = !node.contentDescription.isNullOrBlank() || (!node.isEditable && !node.text.isNullOrBlank()),
                password = node.isPassword)
        }
        val inspection = NativeComposerSendSelector.inspect(descriptors, fieldIndex)
        val rejected = observation.entries.getOrNull(inspection.rejected)?.value
        val details = listOf(inspection.field, inspection.composer, inspection.rejected).distinct().mapNotNull { index ->
            descriptors.getOrNull(index)?.let { n ->
                "node=$index,parent=${n.parent},box=${n.box.left},${n.box.top},${n.box.right},${n.box.bottom},context=${n.sameAppWindow},enabled=${n.enabled},editable=${n.editable},focused=${n.focused},clickable=${n.clickable},clickAction=${n.clickAction},button=${n.button},labelled=${n.labelled},password=${n.password}"
            }
        }.joinToString("; ")
        val sourceAliases = observation.entries.size - observation.entries.map { it.value }.distinct().size
        val metadata = "reason=${inspection.reason} field=${inspection.field} composer=${inspection.composer} rejected=${inspection.rejected} controls=${inspection.controls} editors=${inspection.editors} invalidPaths=${inspection.invalidPaths} foreignNodes=${inspection.foreignNodes} visibleNodes=${nodes.size} observedNodes=${observation.entries.size} graph=child_edges sourceAliases=$sourceAliases rejectedHasDescription=${!rejected?.contentDescription.isNullOrBlank()} rejectedHasText=${rejected?.let { !it.isEditable && !it.text.isNullOrBlank() } == true}; $details"
        if (metadata != lastComposerMetadata) {
            lastComposerMetadata = metadata
            NativeRuntimeSnapshot.save(service, token, "composer", metadata)
        }
        return inspection.candidate?.let { observation.entries[it].value }
    }
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
        if (!VoiceCycle.matches(token) || exporting) { exitConfirmation.reset(); return }
        if (ending != null) service.reportNativeExitTimeout(token)
        checkGreetingTimeout()
        val nodes = nodes()
        if (nodes.isEmpty()) { exitConfirmation.reset(); status("ATTENDO LA FINESTRA CHATGPT"); return }
        pendingButtonPhase?.let { captureButtons(nodes, it); pendingButtonPhase = null }
        val stops = targets(nodes, NativeVoiceLabels.stop)
        val mutes = targets(nodes, NativeVoiceLabels.mute)
        val unmutes = targets(nodes, NativeVoiceLabels.unmute)
        val stop = stops.singleOrNull()
        val mute = mutes.singleOrNull()
        val unmute = unmutes.singleOrNull()
        val active = stop != null && ((mute != null) != (unmute != null))
        val starts = targets(nodes, NativeVoiceLabels.start)
        val start = starts.singleOrNull()
        val startKey = start?.let { node ->
            val bounds = android.graphics.Rect().also { node.getBoundsInScreen(it) }
            "${node.windowId}:$bounds"
        }
        val closed = if (startSent) exitConfirmation.observe(SystemClock.elapsedRealtime(), startKey,
            stops.isNotEmpty() || mutes.isNotEmpty() || unmutes.isNotEmpty()) else false
        if (ending != null) {
            if (stop != null && active && !stopSent && service.consumeNativeStop(token)) {
                stopSent = true
                DiagnosticLog.record(service, DiagnosticLog.Event.NATIVE_STOP_COMMAND, token)
                stop.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                status("ATTENDO CONFERMA ARRESTO VOICE")
            } else if (closed) {
                val message = ending!!
                startSent = false
                DiagnosticLog.record(service, DiagnosticLog.Event.NATIVE_STOP_CONFIRMED, token)
                service.finishNative(token, message)
            } else status(if (stop == null && !stopSent)
                "CHIUSURA NON RICONOSCIUTA — DOPPIO TOCCO IN ALTO A DESTRA: ESPORTA"
                else "ARRESTO DA VERIFICARE — MICROFONO WAKE SOSPESO")
            if (VoiceCycle.matches(token)) {
                captureControls(nodes, "stopping")
                captureButtons(nodes, "stopping")
            }
            return
        }
        if (startSent) captureControls(nodes, "after_voice_command")
        if (!startSent) {
            if (active) { VoiceCycle.cancel(service, token, "Voice era già attiva: nessun nuovo comando inviato."); return }
            if (!projectVisible(nodes)) { status("ATTENDO CONFERMA DEL PROGETTO"); return }
            if (start == null) {
                captureControls(nodes, "before_voice_command")
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
                stableCandidate = candidate
                stableSince = SystemClock.elapsedRealtime()
                status("PREPARO CHATGPT VOICE")
                return
            }
            if (SystemClock.elapsedRealtime() - stableSince < 250L) return
            if (!WakeService.isMicrophoneReleased()) { status("PREPARO IL MICROFONO VOICE"); return }
            if (!WaitingAudio.canActivateVoice(token)) { status("AUDIO DI AVVIO IN CORSO"); return }
            if (!VoiceCycle.touchGate.canDispatch) return
            // Consume before dispatch, including rejected or uncertain actions. Never auto-retry.
            startSent = true
            VoiceCycle.touchGate.block()
            captureButtons(nodes, "before_voice")
            DiagnosticLog.record(service, DiagnosticLog.Event.NATIVE_VOICE_COMMAND, token)
            start.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            status("COMANDO VOICE INVIATO — ATTENDO CONFERMA")
        } else if (active) {
            if (!observed) DiagnosticLog.record(service, DiagnosticLog.Event.NATIVE_VOICE_CONFIRMED, token)
            observed = true
            captureButtons(nodes, "voice_active")
            WaitingAudio.voiceActive(token)
            VoiceCycle.voiceDispatched(token)
            hud?.setVoiceActive()
            hud?.setMicrophoneMuted(unmute != null)

        } else if (observed && closed) {
            DiagnosticLog.record(service, DiagnosticLog.Event.NATIVE_STOP_CONFIRMED, token)
            startSent = false
            service.finishNative(token, "Voice nativa terminata; ascolto riattivato.")
        }
        if (startSent && ending == null && !closed && (observed || service.nativeGreetingStarted(token))) {
            runCatching { tryGreeting(nodes, allowWrite = active) }
                .onFailure { abortGreeting("probe_error") }
        }
    }
    fun requestEnd(message: String): Boolean {
        if (!startSent) return false
        ending = message
        WaitingAudio.stop()
        status("ARRESTO VOICE NATIVA")
        return true
    }
    private fun abortGreeting(reason: String) {
        service.setNativeGreetingStage(token, NativeGreetingPolicy.ABORTED)
        pendingButtonPhase = "greeting_$reason"
        DiagnosticLog.record(service, DiagnosticLog.Event.NATIVE_GREETING_UNAVAILABLE, token, monitorStatus = reason)
    }
    private fun checkGreetingTimeout() {
        if (ending != null || !service.nativeGreetingStarted(token)) return
        val stage = service.nativeGreetingStage(token)
        if (stage != NativeGreetingPolicy.WAITING && stage != NativeGreetingPolicy.WRITING) return
        if (service.nativeGreetingElapsed(token) >= 10000L) abortGreeting("timeout")
    }
    private fun tryGreeting(nodes: List<AccessibilityNodeInfo>, allowWrite: Boolean) {
        if (ending != null || !VoiceCycle.matches(token)) return
        val stage = service.nativeGreetingStage(token)
        if (stage == NativeGreetingPolicy.DISPATCHED || stage == NativeGreetingPolicy.ABORTED) return
        val phrase = service.nativeGreetingText(token)
        val fields = nodes.filter { it.packageName?.toString() == NativeChatSettings.PACKAGE &&
            it.isEditable && !it.isPassword && it.isEnabled && it.isVisibleToUser &&
                (stage != NativeGreetingPolicy.WRITING || it.windowId == service.nativeGreetingWindow(token)) }.distinct()
        val field = fields.singleOrNull()
        val text = field?.takeUnless { it.isShowingHintText }?.text?.toString().orEmpty()
        // These are conservative semantic candidates, not claimed Galaxy resource IDs.
        val sendLabels = setOf("invia", "invia messaggio", "send", "send message")
        val buttons = targets(nodes, sendLabels)
        val send = buttons.singleOrNull()
        val imeSend = field?.actionList?.singleOrNull {
            field.isFocused && it.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id &&
                NativeVoiceLabels.normalize(it.label?.toString().orEmpty()) in sendLabels
        }
        if (stage == NativeGreetingPolicy.WRITING && text == phrase) {
            captureButtons(nodes, "after_greeting_write")
            if (field?.isFocused == true) captureButtons(nodes, "after_greeting_focus")
        }
        val canWrite = allowWrite && field?.actionList?.any { it.id == AccessibilityNodeInfo.ACTION_SET_TEXT } == true
        if (stage == NativeGreetingPolicy.WRITING && field != null && text == phrase && buttons.isEmpty() && imeSend == null) {
            service.prepareNativeEditor(token)
            if (!field.isFocused && field.actionList.any { it.id == AccessibilityNodeInfo.ACTION_FOCUS } &&
                service.consumeNativeGreetingFocus(token)) {
                val focused = runCatching { field.performAction(AccessibilityNodeInfo.ACTION_FOCUS) }.getOrNull()
                DiagnosticLog.record(service, DiagnosticLog.Event.NATIVE_GREETING_RESULT, token,
                    monitorStatus = if (focused == true) "focus_accepted" else "focus_unavailable")
                return // Re-observe focus and EditorInfo, do not send using the previous snapshot.
            }
        }
        val editor = service.nativeEditorState(token)
        val editorSend = stage == NativeGreetingPolicy.WRITING && buttons.isEmpty() && imeSend == null &&
            editor.allowsSend(field?.isFocused == true, text == phrase)
        // Fallback explicitly authorized after the .73 Galaxy dump: local structure, not semantic identity.
        val structural = if (stage == NativeGreetingPolicy.WRITING && fields.size == 1 && text == phrase &&
            field != null && field.isFocused && buttons.isEmpty() && imeSend == null && !editorSend)
            composerSend(nodes, field) else null
        val candidate = structural?.let { button ->
            ComposerCandidate(button, field!!, android.graphics.Rect().also(button::getBoundsInScreen),
                android.graphics.Rect().also(field::getBoundsInScreen))
        }
        val structuralReady = composerStability.observe(candidate, SystemClock.elapsedRealtime())
        val editorOwner = when (editor.owner) { NativeChatSettings.PACKAGE -> "chatgpt"; null -> "none"; else -> "other" }
        val metadata = "stage=$stage fields=${fields.size} empty=${text.isBlank()} matches=${text == phrase} focused=${field?.isFocused == true} write=$canWrite sendButtons=${buttons.size} imeSend=${imeSend != null} editorReady=${editor.ready} editorOwner=$editorOwner editorStarted=${editor.started} editorConnection=${editor.connection} editorAction=${editor.options?.and(android.view.inputmethod.EditorInfo.IME_MASK_ACTION) ?: -1} editorSend=$editorSend composerCandidate=${structural != null} composerStable=$structuralReady voiceControls=$allowWrite window=${field?.windowId ?: -1}"
        if (metadata != lastGreetingMetadata) {
            lastGreetingMetadata = metadata
            NativeRuntimeSnapshot.save(service, token, "greeting", metadata)
        }
        val decision = NativeGreetingPolicy.decide(stage, service.nativeGreetingElapsed(token), fields.size,
            text.isBlank(), text == phrase, canWrite, send != null || (buttons.isEmpty() && imeSend != null) || editorSend || structuralReady)
        when (decision) {
            NativeGreetingPolicy.Action.WRITE -> {
                captureButtons(nodes, "before_greeting_write")
                service.setNativeGreetingStage(token, NativeGreetingPolicy.WRITING)
                service.setNativeGreetingWindow(token, field!!.windowId)
                DiagnosticLog.record(service, DiagnosticLog.Event.NATIVE_GREETING_WRITE, token)
                val args = android.os.Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, phrase)
                }
                val accepted = runCatching { field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args) }.getOrNull()
                DiagnosticLog.record(service, DiagnosticLog.Event.NATIVE_GREETING_RESULT, token,
                    monitorStatus = if (accepted == true) "write_accepted" else if (accepted == false) "write_rejected" else "write_error")
                if (accepted != true) abortGreeting("write_failed")
            }
            NativeGreetingPolicy.Action.SEND -> {
                captureButtons(nodes, if (structuralReady) "before_composer_send" else "before_greeting_send")
                // Consume before dispatch, even if Android rejects it or the outcome is unknown.
                service.setNativeGreetingStage(token, NativeGreetingPolicy.DISPATCHED)
                DiagnosticLog.record(service, DiagnosticLog.Event.NATIVE_GREETING_COMMAND, token)
                val accepted = runCatching {
                    when {
                        send != null -> send.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        imeSend != null -> field?.performAction(imeSend.id) == true
                        editorSend -> service.sendNativeGreetingThroughEditor(token, field?.isFocused == true, text == phrase)
                        structuralReady -> structural?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
                        else -> false
                    }
                }.getOrNull()
                DiagnosticLog.record(service, DiagnosticLog.Event.NATIVE_GREETING_RESULT, token,
                    monitorStatus = if (accepted == true && editorSend) "editor_dispatched"
                        else if (accepted == true && structuralReady) "composer_click_accepted"
                        else if (accepted == true) "send_accepted" else if (accepted == false) "send_rejected" else "send_error")
                if (accepted != true) abortGreeting("send_failed")
            }
            NativeGreetingPolicy.Action.ABORT -> {
                abortGreeting(if (text.isNotBlank() && text != phrase) "draft_protected" else "controls_unavailable")
            }
            else -> Unit
        }
    }

    private fun toggleMute() {
        if (!observed || ending != null) return
        val nodes = nodes()
        val mute = target(nodes, NativeVoiceLabels.mute)
        val unmute = target(nodes, NativeVoiceLabels.unmute)
        val button = if ((mute == null) != (unmute == null)) mute ?: unmute else null
        button?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }
    private fun detachHud() {
        val view = hud ?: return
        hud = null
        view.visibility = android.view.View.GONE
        view.isClickable = false
        val manager = service.getSystemService(WindowManager::class.java)
        val params = view.layoutParams as? WindowManager.LayoutParams
        if (params != null) {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            runCatching { manager.updateViewLayout(view, params) }
        }
        runCatching { manager.removeViewImmediate(view) }
    }
    fun destroy() = detachHud()
}
