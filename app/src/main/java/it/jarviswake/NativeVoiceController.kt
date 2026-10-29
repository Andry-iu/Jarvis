package it.jarviswake

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.graphics.PixelFormat
import android.os.SystemClock
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo

/** Native session owned by accessibility; greeting uses the user-authorized coordinate tap. */
internal class NativeVoiceController(private val service: VoiceAccessibilityService, val token: String) {
    private var hud: HolographicOverlayView? = null
    private var startSent = service.nativeStartSent(token) || !VoiceCycle.touchGate.canDispatch
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
    private val tapStability = NativeSendStability<NativeCoordinateSend.Plan>()
    private val tapHandler = android.os.Handler(android.os.Looper.getMainLooper())
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
        val capture = NativeObservationWalker.capture(root,
            visible = { it.isVisibleToUser }, childCount = { it.childCount }, child = { node, index -> node.getChild(index) },
            composerLeaf = { it.isEditable || it.isPassword || it.className?.toString() == "android.widget.Button" })
        return capture.visible
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
            if (stop != null && !stopSent && service.consumeNativeStop(token)) {
                stopSent = true
                DiagnosticLog.record(service, DiagnosticLog.Event.NATIVE_STOP_COMMAND, token)
                val accepted=runCatching { stop.performAction(AccessibilityNodeInfo.ACTION_CLICK) }.getOrDefault(false)
                DiagnosticLog.record(service,DiagnosticLog.Event.NATIVE_STOP_RESULT,token,
                    monitorStatus=if (accepted) "accepted" else "rejected_or_error")
                status("ATTENDO CONFERMA ARRESTO VOICE")
            } else if (closed) {
                val message = ending!!
                if (service.finishNative(token,message)) DiagnosticLog.record(service,DiagnosticLog.Event.NATIVE_STOP_CONFIRMED,token)
            } else if (stop==null && service.windows.any { it.type==android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD } &&
                service.consumeNativeExitKeyboard(token)) {
                val accepted=service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                DiagnosticLog.record(service,DiagnosticLog.Event.NATIVE_EXIT_KEYBOARD,token,
                    monitorStatus=if (accepted) "accepted" else "rejected")
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
            service.markNativeStart(token)
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
            if (service.finishNative(token,"Voice nativa terminata; ripresa wake richiesta."))
                DiagnosticLog.record(service,DiagnosticLog.Event.NATIVE_STOP_CONFIRMED,token)
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
        if (stage == NativeGreetingPolicy.WRITING && text == phrase) {
            captureButtons(nodes, "after_greeting_write")
            if (field?.isFocused == true) captureButtons(nodes, "after_greeting_focus")
        }
        val canWrite = allowWrite && field?.actionList?.any { it.id == AccessibilityNodeInfo.ACTION_SET_TEXT } == true
        if (stage == NativeGreetingPolicy.WRITING && field != null && text == phrase) {
            if (!field.isFocused && field.actionList.any { it.id == AccessibilityNodeInfo.ACTION_FOCUS } &&
                service.consumeNativeGreetingFocus(token)) {
                val focused = runCatching { field.performAction(AccessibilityNodeInfo.ACTION_FOCUS) }.getOrNull()
                DiagnosticLog.record(service, DiagnosticLog.Event.NATIVE_GREETING_RESULT, token,
                    monitorStatus = if (focused == true) "focus_accepted" else "focus_unavailable")
                return // Re-observe focus and geometry before scheduling the tap.
            }
        }
        // Explicit user request: send this greeting with a real coordinate tap, not ACTION_CLICK.
        val tap = if (stage == NativeGreetingPolicy.WRITING && fields.size == 1 && field != null &&
            field.isFocused && text == phrase) coordinatePlan(nodes, field) else null
        val tapReady = tapStability.observe(tap, SystemClock.elapsedRealtime())
        val metadata = "stage=$stage fields=${fields.size} empty=${text.isBlank()} matches=${text == phrase} focused=${field?.isFocused == true} write=$canWrite coordinateCandidate=${tap != null} coordinateStable=$tapReady coordinateSource=${tap?.source ?: "none"} voiceControls=$allowWrite window=${field?.windowId ?: -1}"
        if (metadata != lastGreetingMetadata) {
            lastGreetingMetadata = metadata
            NativeRuntimeSnapshot.save(service, token, "greeting", metadata)
        }
        val decision = NativeGreetingPolicy.decide(stage, service.nativeGreetingElapsed(token), fields.size,
            text.isBlank(), text == phrase, canWrite, tapReady)
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
                captureButtons(nodes, "before_coordinate_send")
                // Consume before dispatch, even if Android rejects it or the outcome is unknown.
                service.setNativeGreetingStage(token, NativeGreetingPolicy.DISPATCHED)
                DiagnosticLog.record(service, DiagnosticLog.Event.NATIVE_GREETING_COMMAND, token)
                val queued = runCatching { dispatchCoordinateGreeting(tap!!) }.getOrDefault(false)
                if (!queued) abortGreeting("coordinate_prepare_failed")
            }
            NativeGreetingPolicy.Action.ABORT -> {
                abortGreeting(if (text.isNotBlank() && text != phrase) "draft_protected" else "controls_unavailable")
            }
            else -> Unit
        }
    }

    private fun coordinatePlan(nodes: List<AccessibilityNodeInfo>, field: AccessibilityNodeInfo): NativeCoordinateSend.Plan? {
        fun box(r: android.graphics.Rect) = NativeCoordinateSend.Box(r.left,r.top,r.right,r.bottom)
        fun bounds(node: AccessibilityNodeInfo) = android.graphics.Rect().also(node::getBoundsInScreen)
        val window = service.windows.singleOrNull { it.id == field.windowId } ?: return null
        val frame=android.graphics.Rect().also(window::getBoundsInScreen)
        val blocked=service.windows.filter { it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD }
            .map { box(android.graphics.Rect().also(it::getBoundsInScreen)) }
        val sendLabels=setOf("invia","invia messaggio","send","send message")
        val controls=nodes.filter { it.packageName?.toString()==NativeChatSettings.PACKAGE && it.windowId==field.windowId &&
            it.isVisibleToUser && !it.isEditable && !it.isPassword &&
            (it.isClickable || it.actionList.any { a -> a.id==AccessibilityNodeInfo.ACTION_CLICK }) }.map {
            val label=labels(it).filter(String::isNotBlank)
            NativeCoordinateSend.Control(box(bounds(it)),it.isEnabled,label.isEmpty() || label.any(sendLabels::contains))
        }
        return NativeCoordinateSend.plan(box(bounds(field)),box(frame),field.windowId,
            service.resources.displayMetrics.density,controls,blocked)
    }

    private fun dispatchCoordinateGreeting(plan: NativeCoordinateSend.Plan): Boolean {
        val view=hud ?: return false
        if (ending!=null || exporting || !VoiceCycle.matches(token) || service.nativeCoverDismissed(token)) return false
        val manager=service.getSystemService(WindowManager::class.java)
        val params=view.layoutParams as? WindowManager.LayoutParams ?: return false
        val originalFlags=params.flags
        val gate=NativeTapGate()
        var restoreDeadline: Runnable?=null
        fun result(code: String) = DiagnosticLog.record(service,DiagnosticLog.Event.NATIVE_GREETING_RESULT,token,monitorStatus=code)
        fun finish(code: String) {
            if (!gate.finish()) return
            restoreDeadline?.let(tapHandler::removeCallbacks)
            if (gate.mayRestore(hud===view,view.isAttachedToWindow,service.nativeCoverDismissed(token))) {
                params.flags=originalFlags
                runCatching { manager.updateViewLayout(view,params) }.onFailure {
                    // Failed restoration must release the phone, never leave a blocking overlay.
                    dismissForExit()
                    runCatching { service.requestNativeExit(token,returnHome=false,emergency=true) }
                }
            }
            result(code)
        }
        params.flags=originalFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        if (runCatching { manager.updateViewLayout(view,params) }.isFailure) {
            finish("coordinate_cover_prepare_error")
            return false
        }
        result("coordinate_queued")
        NativeRuntimeSnapshot.save(service,token,"coordinate","source=${plan.source} x=${plan.x} y=${plan.y} window=${plan.windowId}")
        restoreDeadline=Runnable { finish("coordinate_timeout_uncertain") }
        tapHandler.postDelayed(restoreDeadline!!,750L)
        // Give WindowManager time to apply touch-through, then recheck the live target before injection.
        tapHandler.postDelayed({
            if (hud!==view || !view.isAttachedToWindow || ending!=null || exporting ||
                !VoiceCycle.matches(token) || service.nativeCoverDismissed(token)) {
                finish("coordinate_cancelled_before_dispatch")
                return@postDelayed
            }
            val fresh=runCatching {
                val current=nodes()
                val fields=current.filter { it.isEditable && !it.isPassword && it.isEnabled &&
                    it.packageName?.toString()==NativeChatSettings.PACKAGE && it.windowId==plan.windowId }
                val field=fields.singleOrNull()
                if (field==null || !field.isFocused || field.isShowingHintText || field.text?.toString()!=service.nativeGreetingText(token)) null
                else coordinatePlan(current,field)
            }.getOrNull()
            if (!gate.dispatch(fresh==plan)) {
                finish("coordinate_target_changed")
                return@postDelayed
            }
            val accepted=runCatching {
                val path=android.graphics.Path().apply { moveTo(plan.x,plan.y) }
                val gesture=android.accessibilityservice.GestureDescription.Builder()
                    .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path,0L,60L)).build()
                service.dispatchGesture(gesture,object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: android.accessibilityservice.GestureDescription?) { finish("coordinate_completed") }
                    override fun onCancelled(gestureDescription: android.accessibilityservice.GestureDescription?) { finish("coordinate_cancelled") }
                },tapHandler)
            }.getOrDefault(false)
            if (accepted) result("coordinate_dispatched") else finish("coordinate_rejected")
        },80L)
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
