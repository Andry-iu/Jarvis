package it.jarviswake

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import org.json.JSONObject
import org.json.JSONTokener
import java.util.UUID

/** Text-only account WebView. No Voice probe, microphone permission or private ChatGPT endpoint. */
internal class GoogleVoiceActivity : Activity() {
    companion object {
        private var current = java.lang.ref.WeakReference<GoogleVoiceActivity>(null)
        @Volatile private var captureOwner: GoogleCapture? = null
        @Volatile private var playbackOwner: GooglePlayback? = null
        // One bounded live page. Its MutableContextWrapper is reset to Application on detach.
        @SuppressLint("StaticFieldLeak")
        private var retainedWeb: WebView? = null
        fun audioBusy() = captureOwner?.released == false || playbackOwner?.released == false
        fun closeForCycle(token: String) { current.get()?.takeIf { it.token == token }?.closeLocal(false) }
        fun prepareAppLaunch(keepCover:Boolean) {
            val a=current.get()?.takeIf {it.alive()} ?: throw BridgeFailure("unsupported")
            if(keepCover) a.keepCoverOverMaps() else a.hideCoverForApp()
        }
        fun stopForTelephone(c:android.content.Context) {
            val token=VoiceCycle.id ?: return
            current.get()?.hideCoverForApp()
            VoiceCycle.cancel(c,token,"Voce Google fermata per la telefonata; lavori desktop conservati.")
        }
        fun speak(command: JSONObject, deadline: Long, permitted: () -> Boolean, progress: (String) -> Unit): JSONObject {
            val latch = java.util.concurrent.CountDownLatch(1)
            var outcome = JSONObject().put("code", "outcome_uncertain")
            val p = command.getJSONObject("params")
            val stages = java.util.concurrent.ConcurrentLinkedQueue<String>()
            val accepted = BridgeActions.onMain(permitted) {
                val a = current.get()
                if (SystemClock.elapsedRealtime() >= deadline) return@onMain false
                a?.acceptSpeech(p, permitted, { stages.add(it) }) { result -> outcome = result; latch.countDown() } == true
            }
            if (!accepted) return JSONObject().put("code", "stale_voice_turn")
            // Existing bridge worker only; gestures never wait. The command lease gates acceptance;
            // the already accepted playback has its own bounded lifetime and cancellation.
            val until = SystemClock.elapsedRealtime()+105000
            while (SystemClock.elapsedRealtime()<until) {
                val finished = latch.await(250, java.util.concurrent.TimeUnit.MILLISECONDS)
                while (true) { val stage=stages.poll() ?: break; runCatching { progress(stage) } }
                if (finished) return outcome
            }
            mainCancelSpeech(p.optString("session_id"),p.optString("turn_id"))
            return JSONObject().put("code", "outcome_uncertain")
        }
        private fun mainCancelSpeech(session:String,turn:String) {
            Handler(Looper.getMainLooper()).post { current.get()?.takeIf {it.token==session && it.state.turn==turn}?.error("RISPOSTA SCADUTA — RIPRODUZIONE FERMATA") }
        }
    }
    private val main = Handler(Looper.getMainLooper())
    private lateinit var root: FrameLayout
    private lateinit var cover: FrameLayout
    private var overlayManager: android.view.WindowManager? = null
    private lateinit var hud: HolographicOverlayView
    private lateinit var next: Button
    private lateinit var transcriptView: CoverTranscriptView
    private lateinit var textToggle: Button
    private lateinit var web: WebView
    private lateinit var config: GoogleVoiceConfig
    @Volatile private lateinit var state: GoogleTurnState
    private var token = ""
    @Volatile private var closed = false
    private var capture: GoogleCapture? = null
    private var api: GoogleSpeechApi? = null
    private var recognition: GoogleRecognitionStream? = null
    private var playback: GooglePlayback? = null
    private var captureEpoch = 0
    private var muted = false
    private var focus: AudioFocusRequest? = null
    private var completion: ((JSONObject) -> Unit)? = null
    private class SpeechAttempt {
        val accepted = true
        @Volatile var synthesized = false
        @Volatile var playbackStarted = false
    }
    private var speech: SpeechAttempt? = null
    private val speechAccepted get() = speech?.accepted == true
    private val synthesized get() = speech?.synthesized == true
    private val playbackStarted get() = speech?.playbackStarted == true
    private var coverHidden = false
    private var resumed = false
    private var dispatchEpoch = 0
    private var readyDeadline = 0L
    private var readyProbePending = false
    private var readyProbeEpoch = 0
    private val script by lazy { assets.open("google-text.js").bufferedReader().use { it.readText() } }
    private fun alive() = !closed && VoiceCycle.matches(token)
    private fun status(text: String) { if (alive()) { hud.setStatus(text); State.setAttempt(this, text) } }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        token = intent.getStringExtra(VoiceCycle.EXTRA_ID).orEmpty()
        if (!VoiceCycle.matches(token) || !VoiceCycle.googleMode || savedInstanceState != null || current.get() != null) {
            if (VoiceCycle.matches(token)) VoiceCycle.cancel(this, token, "Sessione Google interrotta; nessun reinvio.")
            finish(); return
        }
        current = java.lang.ref.WeakReference(this)
        state = GoogleTurnState(token)
        val selected = runCatching { GoogleVoiceDependencies.configuration(this) }.getOrNull()
        if (selected == null || runCatching { selected.validate() }.isFailure ||
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED || !BridgeSettings.enabled(this)) {
            VoiceCycle.cancel(this, token, "Configura credenziali Google valide, microfono e collegamento plugin; nessun consumo avviato."); finish(); return
        }
        config = selected
        readyDeadline=SystemClock.elapsedRealtime()+35000
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setShowWhenLocked(true); setTurnScreenOn(true)
        root = FrameLayout(this)
        cover = object : FrameLayout(this) {
            private var scrollingText = false
            override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean {
                if (!coverHidden && ::transcriptView.isInitialized && transcriptView.scrollText(event)) {
                    if (!scrollingText) {
                        val cancel=android.view.MotionEvent.obtain(event);cancel.action=android.view.MotionEvent.ACTION_CANCEL
                        super.dispatchTouchEvent(cancel);cancel.recycle();scrollingText=true
                    }
                    if(event.actionMasked in setOf(android.view.MotionEvent.ACTION_UP,android.view.MotionEvent.ACTION_CANCEL)) scrollingText=false
                    return true
                }
                return super.dispatchTouchEvent(event)
            }
        }
        cover.setOnApplyWindowInsetsListener { v, insets ->
            val bars=insets.getInsets(android.view.WindowInsets.Type.systemBars())
            v.setPadding(bars.left,bars.top,bars.right,bars.bottom);insets
        }
        // Use the same authenticated WebView profile without moving/reloading a legacy draft.
        web = retainedWeb?.also { retainedWeb=null } ?: GoogleVoiceDependencies.web(this)
        (web.context as? android.content.MutableContextWrapper)?.baseContext=this
        web.alpha = 1f; web.isClickable = true; web.isFocusable = true
        web.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        web.settings.apply {
            DesktopWeb.configure(this); javaScriptEnabled = true; domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = true; allowFileAccess = false; allowContentAccess = false
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportMultipleWindows(false)
        }
        web.webChromeClient = object : WebChromeClient() { override fun onPermissionRequest(request: PermissionRequest) { request.deny() } }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (!request.isForMainFrame) return request.url.scheme !in setOf("https", "about")
                if (request.url.toString().trimEnd('/') == config.conversation.trimEnd('/')) return false
                error("DESTINAZIONE CAMBIATA — CHIUDI O RICONFIGURA"); return true
            }
            override fun onPageFinished(view: WebView, url: String) { if (alive()) verifyReady() }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, e: android.webkit.WebResourceError) {
                if (request.isForMainFrame) error("CHATGPT NON RAGGIUNGIBILE")
            }
        }
        root.addView(web, FrameLayout.LayoutParams(-1, -1))
        hud = HolographicOverlayView(this).apply {
            setCoverActions(singleTap = { toggleMute() }, belowTap = { CoverSettings.setOpaque(this@GoogleVoiceActivity, !CoverSettings.isOpaque(this@GoogleVoiceActivity)) },
                close = { closeLocal(false) }, export = { hideCoverForApp(); DiagnosticExportActivity.open(this@GoogleVoiceActivity) },
                hide = { closeLocal(true) }, revealChat = { revealChat() })
        }
        cover.addView(hud, FrameLayout.LayoutParams(-1, -1))
        transcriptView=CoverTranscriptView(this)
        cover.addView(transcriptView,FrameLayout.LayoutParams(-1,-1))
        textToggle=Button(this).apply {
            text=if(transcriptView.shown) "Testi ✓" else "Testi"
            textSize=11f;contentDescription="Mostra o nascondi testi sulla cover; due dita scorrono la cronologia"
            setOnClickListener { text=if(transcriptView.toggle()) "Testi ✓" else "Testi" }
        }
        cover.addView(textToggle,FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM or Gravity.END).apply {
            rightMargin=(12*resources.displayMetrics.density).toInt();bottomMargin=(8*resources.displayMetrics.density).toInt()
        })
        next = Button(this).apply { text = "Nuova dettatura / annulla attesa"; setOnClickListener { newDictation() } }
        cover.addView(next, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = (64*resources.displayMetrics.density).toInt() })
        hud.setOpacityBottomAnchor(next)
        root.addView(cover,FrameLayout.LayoutParams(-1,-1))
        setContentView(root)
        if (android.os.Build.VERSION.SDK_INT >= 33) onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT) { closeLocal(false) }
        BridgeLink.owner(this, "google_voice", true)
        WaitingAudio.stop()
        if (web.url?.trimEnd('/') == config.conversation.trimEnd('/')) verifyReady() else web.loadUrl(config.conversation)
        main.postDelayed({ if (alive() && VoiceCycle.phase != VoiceCycleState.Phase.VOICE) error("CHAT NON PRONTA — NESSUN INVIO") }, 35000)
    }
    private fun verifyReady() {
        if (!alive() || state.phase != "ready" || capture != null || readyProbePending) return
        val epoch=captureEpoch;readyProbeEpoch=epoch;readyProbePending=true
        web.evaluateJavascript(scriptFor("probe", "", "")) { raw ->
            if(readyProbeEpoch==epoch) readyProbePending=false
            if (!alive() || epoch!=captureEpoch || state.phase != "ready" || capture != null) return@evaluateJavascript
            when(parse(raw)) {
                "ready" -> {VoiceCycle.voiceDispatched(token);startListening()}
                "draft" -> error("BOZZA PRESENTE — NESSUNA SCRITTURA O INVIO")
                "wrong_conversation" -> error("CONVERSAZIONE DIVERSA — NESSUN INVIO")
                else -> {
                    status("ATTESA COMPOSITORE DELLA CHAT ASSOCIATA…")
                    if(SystemClock.elapsedRealtime()<readyDeadline) main.postDelayed({verifyReady()},250)
                    else error("COMPOSITORE NON PRONTO — NESSUN INVIO")
                }
            }
        }
    }
    private fun parse(raw: String): String = runCatching {
        JSONObject(JSONTokener(raw).nextValue() as String).getString("phase")
    }.getOrDefault("uncertain")
    private fun scriptFor(action: String, turn: String, text: String) = script
        .replace("__JW_CONFIG__", JSONObject().put("url", config.conversation).put("session", token)
            .put("turn", turn).put("action", action).put("text", text).toString())
    private fun startListening() {
        if (!alive() || muted || state.phase != "ready") return
        val epoch = ++captureEpoch
        val gate = NativeAudioReleaseGate()
        val until = SystemClock.elapsedRealtime() + 8000
        fun waitForAudio() {
            if (!alive() || epoch != captureEpoch || muted || state.phase != "ready") return
            if (audioBusy() || !WakeService.isMicrophoneReleased() ||
                !gate.observe(token, SystemClock.elapsedRealtime(), NativeAudioState.read(this).free)) {
                if (SystemClock.elapsedRealtime() >= until) error("MICROFONO OCCUPATO — RIPROVA ESPLICITAMENTE")
                else main.postDelayed({ waitForAudio() }, 100)
                return
            }
            val recorder = GoogleVoiceDependencies.capture(config); capture = recorder; captureOwner = recorder
            status("ASCOLTO GOOGLE — TOCCA PER MUTE")
            recorder.start(onSpeech = { initial ->
                main.post {
                    if (alive() && epoch == captureEpoch) beginRecognition(initial) else initial.fill(0)
                }
            }, onChunk = { frame, size ->
                val copy=frame.copyOf(size)
                main.post {
                    try {if(alive() && epoch==captureEpoch && state.phase=="transcribing") recognition?.audio(copy)}
                    catch(_:Exception) {if(alive() && epoch==captureEpoch) error("ERRORE STREAM GOOGLE — NESSUN REINVIO")}
                    finally {copy.fill(0)}
                }
            }, onEnd = { audio, failure ->
                main.post {
                    if (capture === recorder) capture = null
                    if (!alive() || epoch != captureEpoch) { audio?.fill(0); return@post }
                    if (audio == null) { if (failure != null) error("MICROFONO: $failure"); return@post }
                    audio.fill(0)
                    status("TRASCRIZIONE GOOGLE — ATTESA DEFINITIVO…")
                    try {recognition?.finish() ?: error("STREAM NON DISPONIBILE — NESSUN INVIO")}
                    catch(_:Exception) {error("ERRORE CHIUSURA STREAM GOOGLE — NESSUN REINVIO")}
                }
            })
        }
        waitForAudio()
    }
    private fun beginRecognition(initial: ShortArray) {
        val turn = UUID.randomUUID().toString()
        if (!alive() || !state.begin(turn)) { initial.fill(0); return }
        status("DETTATURA GOOGLE — PAUSA PER INVIARE")
        val client = GoogleVoiceDependencies.api(config); api = client
        try {
            recognition=client.openRecognition { text, samples ->
                GoogleVoiceUsage.add(applicationContext,"stt_ms",samples/16L,!text.isNullOrBlank())
                main.post {
                    if (!alive() || state.turn != turn || state.phase != "transcribing") return@post
                    if(capture!=null) {error("STREAM GOOGLE TERMINATO PRIMA DELLA DETTATURA — NESSUN INVIO");return@post}
                    if (text==null) {error("ERRORE GOOGLE STT — CREDENZIALI, QUOTA O RETE; NESSUN REINVIO");return@post}
                    if (text.isBlank()) {error("NESSUNA TRASCRIZIONE — NUOVA DETTATURA");return@post}
                    transcriptView.append("Dettato",text)
                    if (state.submitting()) submit(turn,text)
                }
            }
            recognition?.audio(initial)
        } catch(_:Exception) {error("ERRORE AVVIO GOOGLE STT — NESSUN REINVIO")}
        finally {initial.fill(0)}
    }
    private fun submit(turn: String, transcript: String) {
        val epoch = ++dispatchEpoch
        val text = transcript + "\n\n[JarvisWake: session_id=$token; turn_id=$turn. Rispondi con jarviswake_speak per la voce, senza ChatGPT Voice.]"
        val until = SystemClock.elapsedRealtime() + 12000
        fun step() {
            if (!alive() || state.turn != turn || epoch != dispatchEpoch || state.phase !in setOf("submitting", "send_dispatched")) return
            web.evaluateJavascript(scriptFor("send", turn, text)) { raw ->
                if (!alive() || state.turn != turn || epoch != dispatchEpoch) return@evaluateJavascript
                when (parse(raw)) {
                    "written", "waiting_send" -> if (SystemClock.elapsedRealtime() < until) main.postDelayed({ step() }, 150) else error("INVIO NON DISPONIBILE — NESSUN REINVIO")
                    "dispatched", "waiting_observation" -> {
                        state.dispatched(); status("INVIO ESEGUITO — ATTESA RISCONTRO CHAT")
                        if (SystemClock.elapsedRealtime() < until) main.postDelayed({ step() }, 200) else waitReply(turn, false)
                    }
                    "observed" -> { state.dispatched(); state.observed(); waitReply(turn, true) }
                    else -> error("INVIO NON CONFERMATO O BOZZA PRESENTE — NESSUN REINVIO")
                }
            }
        }
        step()
    }
    private fun waitReply(turn: String, observed: Boolean) {
        status(if (observed) "MESSAGGIO OSSERVATO — ATTESA VOCE DAL PLUGIN" else "INVIO DISPATCHED, NON OSSERVATO — ATTESA PLUGIN")
        main.postDelayed({
            if (alive() && state.turn == turn && state.phase in setOf("send_dispatched", "waiting_reply"))
                status("NESSUNA VOCE DAL PLUGIN — ATTENDO ANCORA; NUOVA DETTATURA ANNULLA L’ATTESA, NON I LAVORI")
        }, 90000)
        main.postDelayed({
            if(alive() && state.turn==turn && state.phase in setOf("send_dispatched","waiting_reply"))
                error("CREDENZIALI GOOGLE SCADUTE — CHIUDI LA SESSIONE E RINNOVA SUL TELEFONO; LAVORI CONSERVATI")
        },(config.expiresAt-System.currentTimeMillis()).coerceAtLeast(0))
    }
    private fun acceptSpeech(p: JSONObject, permitted: () -> Boolean, progress: (String) -> Unit, done: (JSONObject) -> Unit): Boolean {
        val text = p.optString("text")
        if(alive() && System.currentTimeMillis()>=config.expiresAt) {
            error("CREDENZIALI GOOGLE SCADUTE — NESSUNA SINTESI AVVIATA");return false
        }
        if (!alive() || !permitted() || text.isBlank() || text.toByteArray(Charsets.UTF_8).size > 4500 ||
            !state.acceptReply(p.optString("session_id"), p.optString("turn_id"))) return false
        val attempt=SpeechAttempt(); speech=attempt; completion = done
        progress("accepted")
        transcriptView.append("Jarvis",text)
        dispatchEpoch++ // A late DOM callback must not change the accepted reply phase.
        status("RISPOSTA ACCETTATA — SINTESI GOOGLE…")
        val turn = state.turn
        val client = GoogleVoiceDependencies.api(config); api = client
        val player = GoogleVoiceDependencies.playback(); playback = player; playbackOwner = player
        val manager = getSystemService(AudioManager::class.java)
        val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setOnAudioFocusChangeListener({ change -> if (change < 0 && alive()) error("FOCUS AUDIO PERSO — RIPRODUZIONE FERMATA") }, main).build()
        focus = focusRequest
        if (manager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { finishSpeech("audio_focus_denied", false); return true }
        Thread({
            var ok = false
            try {
                val pcm = client.synthesize(text); attempt.synthesized = true; progress("synthesized")
                GoogleVoiceUsage.add(applicationContext, "tts_characters", text.codePointCount(0, text.length).toLong(), true)
                ok = player.play(pcm, { alive() && state.turn == turn && permitted() }) {
                    attempt.playbackStarted = true
                    progress("playback_started")
                    main.post { if (alive() && state.turn == turn) status("RIPRODUZIONE GOOGLE — MICROFONO SOSPESO") }
                }
                main.post { if (alive() && state.turn == turn) finishSpeech(if (ok) "speech_completed" else "playback_failed", ok) }
            } catch (_: Exception) {
                if (!attempt.synthesized) GoogleVoiceUsage.add(applicationContext, "tts_characters", text.codePointCount(0, text.length).toLong(), false)
                main.post { if (alive() && state.turn == turn) finishSpeech(if (attempt.synthesized) "playback_failed" else "synthesis_failed", false) }
            }
        }, "Jarvis-google-tts").apply { isDaemon = true; start() }
        return true
    }
    private fun finishSpeech(code: String, ok: Boolean) {
        val done = completion; completion = null
        done?.invoke(JSONObject().put("code", code).put("accepted", speechAccepted).put("synthesized", synthesized)
            .put("playback_started", playbackStarted).put("completed", ok))
        focus?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }; focus = null
        if (ok) { state.ready(); status("RISPOSTA COMPLETATA — RIPRESA ASCOLTO…"); main.postDelayed({ if (alive()) startListening() }, 700) }
        else error("RIPRODUZIONE FALLITA — NUOVA DETTATURA O CHIUDI")
    }
    private fun error(message: String) {
        if (!alive()) return
        captureEpoch++; dispatchEpoch++; capture?.stop(); api?.cancel(); playback?.stop()
        state.fail(); status(message)
        if (completion != null) finishSpeech("cancelled", false)
    }
    private fun toggleMute() {
        if (!alive()) return
        muted = !muted
        if (muted) {
            captureEpoch++; capture?.stop()
            if(state.phase=="transcribing") {state.close();api?.cancel();recognition=null;state=GoogleTurnState(token)}
            status("MICROFONO MUTO")
        }
        else { status("MICROFONO RIATTIVATO"); if (state.phase == "ready") startListening() }
        hud.setMicrophoneMuted(muted); VoiceCycle.confirmMicrophoneMuted(token, muted)
    }
    private fun newDictation() {
        if (!alive()) return
        captureEpoch++; dispatchEpoch++; capture?.stop(); api?.cancel(); playback?.stop()
        if (completion != null) finishSpeech("cancelled", false)
        state.close(); state = GoogleTurnState(token)
        readyProbePending=false;readyDeadline=SystemClock.elapsedRealtime()+35000
        muted = false; hud.setMicrophoneMuted(false)
        fun afterRelease() {
            if (!alive()) return
            if (capture != null || audioBusy()) main.postDelayed({ afterRelease() }, 100)
            else verifyReady()
        }
        afterRelease()
    }
    private fun keepCoverOverMaps() {
        if(coverHidden || overlayManager!=null) return
        val service=VoiceAccessibilityService.bridgeService() ?: throw BridgeFailure("unavailable")
        val manager=service.getSystemService(android.view.WindowManager::class.java)
        (cover.parent as? android.view.ViewGroup)?.removeView(cover)
        val params=android.view.WindowManager.LayoutParams(-1,-1,android.view.WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                android.view.WindowManager.LayoutParams.FLAG_SECURE,android.graphics.PixelFormat.TRANSLUCENT)
            .apply {gravity=Gravity.TOP or Gravity.START}
        overlayManager=manager
        try {manager.addView(cover,params)} catch(_:Exception) {
            runCatching {manager.removeViewImmediate(cover)};overlayManager=null
            if(alive() && !coverHidden && cover.parent==null) root.addView(cover,FrameLayout.LayoutParams(-1,-1))
            throw BridgeFailure("unavailable")
        }
    }
    private fun removeCover() {
        // Remove interception before token guards/audio cleanup, including an Android Auto overlay.
        coverHidden=true
        if(::cover.isInitialized) {
            cover.visibility=View.GONE
            overlayManager?.let { manager ->
                (cover.layoutParams as? android.view.WindowManager.LayoutParams)?.let { params ->
                    params.flags=params.flags or android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    runCatching {manager.updateViewLayout(cover,params)}
                }
                runCatching {manager.removeViewImmediate(cover)}
            };overlayManager=null
            (cover.parent as? android.view.ViewGroup)?.removeView(cover)
            if(::hud.isInitialized) cover.removeView(hud)
            if(::next.isInitialized) cover.removeView(next)
            if(::transcriptView.isInitialized) cover.removeView(transcriptView)
            if(::textToggle.isInitialized) cover.removeView(textToggle)
        }
    }
    private fun hideCoverForApp() {removeCover()}
    private fun revealChat() {
        // Only this explicit user gesture may bring the existing text conversation to the front.
        removeCover()
        if(alive() && !resumed) runCatching {
            (VoiceAccessibilityService.bridgeService() ?: this).startActivity(Intent(this,GoogleVoiceActivity::class.java)
                .putExtra(VoiceCycle.EXTRA_ID,token).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        }
    }
    override fun onResume() {super.onResume();resumed=true}
    override fun onPause() {resumed=false;super.onPause()}
    private fun closeLocal(home: Boolean) {
        removeCover()
        terminate()
        if (home) startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }
    private fun terminate() {
        removeCover()
        if (closed) return
        closed = true
        if (::transcriptView.isInitialized) transcriptView.clearText()
        if (::web.isInitialized) runCatching { web.evaluateJavascript("delete window.__jarvisGoogleText;", null) }
        if (::state.isInitialized) state.close()
        main.removeCallbacksAndMessages(null)
        captureEpoch++; dispatchEpoch++; capture?.stop(); api?.cancel(); playback?.stop()
        completion?.invoke(JSONObject().put("code", "cancelled").put("accepted", speechAccepted).put("synthesized", synthesized)
            .put("playback_started", playbackStarted).put("completed", false)); completion = null
        focus?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }; focus = null
        if (current.get() === this) current.clear()
        BridgeLink.owner(this, "google_voice", false)
        if (VoiceCycle.matches(token)) VoiceCycle.cancel(this, token, "Sessione Google chiusa. Lavori desktop già avviati conservati.")
    }
    @Suppress("DEPRECATION")
    @SuppressLint("GestureBackNavigation")
    override fun onBackPressed() { closeLocal(false) }
    override fun onDestroy() {
        terminate()
        if (::web.isInitialized) {
            (web.parent as? android.view.ViewGroup)?.removeView(web)
            web.webViewClient=WebViewClient()
            web.webChromeClient=object:WebChromeClient() {override fun onPermissionRequest(request:PermissionRequest) {request.deny()} }
            // Keep this exact DOM, including a protected user draft, for the next explicit cycle.
            (web.context as? android.content.MutableContextWrapper)?.baseContext=applicationContext
            retainedWeb?.takeIf {it!==web}?.destroy();retainedWeb=web
        }
        super.onDestroy()
    }
}

/** Test seams use simulated providers only; production defaults never run without an explicit cycle. */
internal object GoogleVoiceDependencies {
    var configuration: (android.content.Context) -> GoogleVoiceConfig? = { GoogleVoiceSettings.read(it) }
    var api: (GoogleVoiceConfig) -> GoogleSpeechApi = { GoogleSpeechClient(it) }
    var capture: (GoogleVoiceConfig) -> GoogleCapture = { GoogleAudioCapture(it) }
    var playback: () -> GooglePlayback = { GoogleAudioPlayback() }
    var web: (android.content.Context) -> WebView = { WebView(android.content.MutableContextWrapper(it.applicationContext)) }
    fun reset() {
        configuration = { GoogleVoiceSettings.read(it) }; api = { GoogleSpeechClient(it) }
        capture = { GoogleAudioCapture(it) }; playback = { GoogleAudioPlayback() }; web = { WebView(android.content.MutableContextWrapper(it.applicationContext)) }
    }
}
