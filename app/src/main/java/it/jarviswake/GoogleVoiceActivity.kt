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
    private lateinit var transcriptView: CoverTranscriptView
    private lateinit var textToggle: View
    private lateinit var web: WebView
    private lateinit var project: String
    private var conversation = ""
    private var projectOpened = false
    private var preSending = false
    private var preComplete = false
    private var startupComplete = false
    private var preDispatchedAt = -1L
    private lateinit var openingText: String
    private lateinit var greetingText: String
    private val writtenTurns = mutableSetOf<String>()
    private val openingTurn = UUID.randomUUID().toString()
    private lateinit var config: GoogleVoiceConfig
    private var resolvedConfig: GoogleVoiceConfig? = null
    private var credentials: GoogleCredentialSession? = null
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
    private var echoCandidate:String?=null
    private class SpeechAttempt(val text:String) {
        val accepted = true
        @Volatile var synthesized = false
        @Volatile var playbackStarted = false
    }
    private var speech: SpeechAttempt? = null
    private val speechAccepted get() = speech?.accepted == true
    private val synthesized get() = speech?.synthesized == true
    private val playbackStarted get() = speech?.playbackStarted == true
    private var waitingListening=false
    private var receiptPending=false
    private var echoCapture:GoogleCapture?=null
    private var exportBusy = false
    @Volatile private var exportGeneration = 0
    @Volatile private var coverHidden = false
    private var resumed = false
    private var dispatchEpoch = 0
    private var readyDeadline = 0L
    private var readyProbePending = false
    private var readyProbeEpoch = 0
    private val script by lazy { assets.open("google-text.js").bufferedReader().use { it.readText() } }
    private fun alive() = !closed && VoiceCycle.matches(token)
    private fun status(text: String) { if (alive()) {
        hud.setStatus(text); State.setAttempt(this,text)
        GoogleVoiceDiagnostics.record(this,token,"status",if(text.startsWith("BOZZA PRESENTE")) "draft_present" else state.phase)
    } }
    private fun diagnosticSnapshot()=JSONObject().put("project_opened",projectOpened).put("conversation_bound",conversation.isNotEmpty())
        .put("pre_sending",preSending).put("pre_complete",preComplete).put("startup_complete",startupComplete)
        .put("capture_present",capture!=null).put("capture_released",captureOwner?.released!=false)
        .put("playback_present",playback!=null).put("playback_released",playbackOwner?.released!=false)
        .put("muted",muted).put("cover_hidden",coverHidden).put("echo_ready",echoCapture?.echoState=="ready").put("receipt_pending",receiptPending)
        .put("record_route",capture?.routeType ?: 0).put("play_route",playback?.routeType ?: 0)
        .put("car_projection",BridgeCarConnection.keepMapsCover()).put("projection_known",BridgeCarConnection.observed())


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
        GoogleVoiceDiagnostics.record(this,token,"session","entry")
        val selected = runCatching { GoogleVoiceDependencies.configuration(this) }.getOrNull()
        if (selected == null || runCatching { selected.validate() }.isFailure ||
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED || !BridgeSettings.enabled(this)) {
            GoogleVoiceDiagnostics.record(this,token,"configuration",when {
                selected==null -> "config_missing"
                runCatching {selected.validate()}.isFailure -> "config_invalid"
                !BridgeSettings.enabled(this) -> "bridge_disabled"
                else -> "microphone_denied"
            })
            VoiceCycle.cancel(this, token, "Configura credenziali Google valide, microfono e collegamento plugin; nessun consumo avviato."); finish(); return
        }
        config = selected
        project = GoogleVoiceDependencies.project(this)
        if (!ChatGptProjectSettings.isValidProjectUrl(project)) { VoiceCycle.cancel(this, token, "Progetto ChatGPT non valido."); finish(); return }
        if (config.authMode == "backend") {
            credentials = runCatching { GoogleVoiceDependencies.credentials(this) }.getOrNull()
            if (credentials == null) { VoiceCycle.cancel(this, token, "Abbinamento backend non disponibile."); finish(); return }
        }
        openingText = GoogleVoiceDependencies.opening(this)
        greetingText = GoogleVoiceDependencies.greeting(this)
        readyDeadline=SystemClock.elapsedRealtime()+120000
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
        // Keep the actual live page. Handoff never navigates away from a user draft.
        web = retainedWeb?.also { retainedWeb=null } ?: ChatGptPreloader.handoff(token)?.webView ?: GoogleVoiceDependencies.web(this)
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
                val target=GoogleSessionRoute.clean(request.url.toString())
                if (conversation.isNotEmpty() && target==conversation) return false
                if (conversation.isEmpty() && (GoogleSessionRoute.projectPage(request.url.toString(),project) ||
                    GoogleSessionRoute.conversation(request.url.toString(),project,(preSending || state.phase in setOf("submitting","send_dispatched")))!=null)) return false
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
                close = { closeLocal(false) }, export = { exportDiagnostics() },
                hide = { closeLocal(true) }, revealChat = { revealChat() })
        }
        cover.addView(hud, FrameLayout.LayoutParams(-1, -1))
        transcriptView=CoverTranscriptView(this)
        cover.addView(transcriptView,FrameLayout.LayoutParams(-1,-1))
        textToggle=View(this).apply {
            isClickable=true;isFocusable=true
            contentDescription="Mostra o nascondi chat sulla cover; due dita scorrono la cronologia"
            setOnClickListener {transcriptView.toggle()}
        }
        cover.addView(textToggle,FrameLayout.LayoutParams((96*resources.displayMetrics.density).toInt(),(56*resources.displayMetrics.density).toInt(),Gravity.BOTTOM or Gravity.END).apply {
            rightMargin=(12*resources.displayMetrics.density).toInt();bottomMargin=(8*resources.displayMetrics.density).toInt()
        })
        hud.setOpacityBottomAnchor(textToggle)
        root.addView(cover,FrameLayout.LayoutParams(-1,-1))
        setContentView(root)
        if (android.os.Build.VERSION.SDK_INT >= 33) onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT) { closeLocal(false) }
        BridgeLink.owner(this, "google_voice", true)
        if (web.url.isNullOrBlank() || web.url == "about:blank") { projectOpened=true; web.loadUrl(project) }
        else { projectOpened=GoogleSessionRoute.projectPage(web.url.orEmpty(),project); verifyReady() }
        main.postDelayed({ if (alive() && !startupComplete) error("AVVIO CHAT NON COMPLETATO — NESSUN REINVIO") }, 120000)
    }
    private fun verifyReady() {
        if (!alive() || state.phase != "ready" || capture != null || readyProbePending) return
        if(projectOpened && !preComplete && openingText.isNotBlank()) { sendOpening(); return }
        if(projectOpened) preComplete=true
        val epoch=captureEpoch;readyProbeEpoch=epoch;readyProbePending=true
        web.evaluateJavascript(scriptFor(if(projectOpened) "probe" else "restart", "", "")) { raw ->
            if(readyProbeEpoch==epoch) readyProbePending=false
            if (!alive() || epoch!=captureEpoch || state.phase != "ready" || capture != null) return@evaluateJavascript
            GoogleVoiceDiagnostics.dom(this,token,"page",raw)
            if(projectOpened) observeConversation(raw)
            when(parse(raw)) {
                "restart_ready" -> {
                    // A new wake explicitly requests a new project chat. Never navigate away from a draft.
                    projectOpened=true;conversation=""
                    web.evaluateJavascript("window.__jarvisGoogleText?.disposeDiagnostics?.(); delete window.__jarvisGoogleText; delete window.__jarvisGoogleBinding;", null)
                    web.loadUrl(project)
                }
                "ready" -> {
                    if(!projectOpened) {error("NUOVA CHAT NON CONFERMATA — NESSUN INVIO");return@evaluateJavascript}
                    if(preDispatchedAt>=0 && SystemClock.elapsedRealtime()-preDispatchedAt<300) {
                        main.postDelayed({verifyReady()},300-(SystemClock.elapsedRealtime()-preDispatchedAt));return@evaluateJavascript
                    }
                    if(!startupComplete) {
                        startupComplete=true;VoiceCycle.voiceDispatched(token);WaitingAudio.stop()
                        if(greetingText.isNotBlank()) {
                            val turn=UUID.randomUUID().toString();check(state.begin(turn));check(state.submitting())
                            submit(turn,greetingText,true);return@evaluateJavascript
                        }
                    }
                    startListening()
                }
                "draft" -> error("BOZZA PRESENTE — NESSUNA SCRITTURA O INVIO")
                "wrong_conversation", "old_session", "project_has_messages" -> error("PAGINA FUORI DAL PROGETTO DI AVVIO — NESSUN INVIO")
                else -> {
                    status("ATTESA COMPOSITORE JARVISRUNTIME…")
                    if(SystemClock.elapsedRealtime()<readyDeadline) main.postDelayed({verifyReady()},250)
                    else error("COMPOSITORE NON PRONTO — NESSUN INVIO")
                }
            }
        }
    }
    private fun sendOpening() {
        if(preSending || !alive()) return
        preSending=true
        val epoch=dispatchEpoch
        fun step() {
            if(!alive() || epoch!=dispatchEpoch || preComplete) return
            web.evaluateJavascript(scriptFor("send",openingTurn,openingText)) { raw ->
                if(!alive() || epoch!=dispatchEpoch || preComplete) return@evaluateJavascript
                GoogleVoiceDiagnostics.dom(this,token,"pre",raw)
                when(parse(raw)) {
                    "ambiguous_editor", "unverified_conversation", "busy" -> {
                        // These phases guarantee no write/click. Never retry after any mutation.
                        if(!writtenTurns.contains(openingTurn) && SystemClock.elapsedRealtime()<readyDeadline) main.postDelayed({step()},250)
                        else error("COMPOSITORE DI AVVIO NON PRONTO — NESSUN REINVIO")
                    }
                    "written", "waiting_send" -> {
                        writtenTurns.add(openingTurn)
                        if(SystemClock.elapsedRealtime()<readyDeadline) main.postDelayed({step()},150)
                        else error("MESSAGGIO PRIMA NON INVIATO — NESSUN REINVIO")
                    }
                    "dispatched", "waiting_observation" -> {
                        writtenTurns.add(openingTurn)
                        if(preDispatchedAt<0) preDispatchedAt=SystemClock.elapsedRealtime()
                        status("MESSAGGIO PRIMA INVIATO — ATTESA NUOVA CHAT…")
                        if(SystemClock.elapsedRealtime()<readyDeadline) main.postDelayed({step()},200)
                        else error("NUOVA CHAT NON OSSERVATA — NESSUN REINVIO")
                    }
                    "observed" -> {
                        observeConversation(raw)
                        if(conversation.isEmpty()) {error("DESTINAZIONE NON CONFERMATA — NESSUN REINVIO");return@evaluateJavascript}
                        preComplete=true;preSending=false;verifyReady()
                    }
                    else -> error("MESSAGGIO PRIMA BLOCCATO O INCERTO — NESSUN REINVIO")
                }
            }
        }
        step()
    }
    private fun observeConversation(raw: String) {
        val reply=runCatching {JSONObject(JSONTokener(raw).nextValue() as String)}.getOrNull() ?: return
        if(reply.optString("phase") !in setOf("ready","observed")) return
        val candidate=reply.optString("conversation")
        if(candidate.isEmpty()) return
        val valid=GoogleSessionRoute.conversation(candidate,project,reply.optBoolean("directVerified")) ?: return
        if(conversation.isEmpty()) conversation=valid
    }
    private fun parse(raw: String): String = runCatching {
        JSONObject(JSONTokener(raw).nextValue() as String).getString("phase")
    }.getOrDefault("uncertain")
    private fun scriptFor(action: String, turn: String, text: String) = script
        .replace("__JW_CONFIG__", JSONObject().put("project", project).put("session", token)
            .put("turn", turn).put("action", action).put("text", text).put("resume",writtenTurns.contains(turn)).put("fresh",conversation.isEmpty()).toString())
    private fun ensureAudioFocus():Boolean {
        if(focus!=null) return true
        val request=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setOnAudioFocusChangeListener({ change -> if(change<0 && alive()) error("FOCUS AUDIO PERSO — MICROFONO E RIPRODUZIONE FERMATI") },main).build()
        if(getSystemService(AudioManager::class.java).requestAudioFocus(request)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return false
        focus=request;return true
    }
    private fun canListen() = (state.phase=="ready" && !(receiptPending && conversation.isEmpty())) || (waitingListening && state.phase=="waiting_reply")
    private fun startListening() {
        if (!alive() || muted || !canListen() || capture?.released==false) return
        if(!ensureAudioFocus()) {error("FOCUS AUDIO NON DISPONIBILE — NESSUN ASCOLTO");return}
        val broker = credentials
        if (broker == null) { resolvedConfig = config; startCapture(); return }
        val epoch = ++captureEpoch
        Thread({
            GoogleVoiceDiagnostics.record(this,token,"auth","started")
            val result = runCatching { broker.resolve(config) }
            GoogleVoiceDiagnostics.record(this,token,"auth",if(result.isSuccess) "completed" else "failed",JSONObject().put("http",(result.exceptionOrNull() as? GoogleBrokerHttpFailure)?.status ?: 0))
            main.post {
                if (!alive() || epoch != captureEpoch || muted || !canListen()) return@post
                result.onSuccess { resolvedConfig = it; startCapture() }
                    .onFailure { error("AUTENTICAZIONE GOOGLE NON DISPONIBILE — VERIFICA CHIAVE, PROGETTO E RETE") }
            }
        }, "Jarvis-google-auth").apply { isDaemon = true; start() }
    }
    private fun startCapture() {
        if (!alive() || muted || !canListen() || capture?.released==false) return
        val epoch=++captureEpoch;val gate=NativeAudioReleaseGate();val until=SystemClock.elapsedRealtime()+8000
        fun waitForAudio() {
            if(!alive() || epoch!=captureEpoch || muted || !canListen()) return
            if(audioBusy() || !WakeService.isMicrophoneReleased() || !gate.observe(token,SystemClock.elapsedRealtime(),NativeAudioState.read(this).free)) {
                if(SystemClock.elapsedRealtime()>=until) error("MICROFONO OCCUPATO — RIPROVA ESPLICITAMENTE") else main.postDelayed({waitForAudio()},100)
                return
            }
            val recorder=GoogleVoiceDependencies.capture(config);capture=recorder;captureOwner=recorder;echoCapture=null
            status(if(waitingListening) "ATTENDO RISPOSTA — PUOI PARLARE DI NUOVO" else "ASCOLTO GOOGLE — TOCCO CENTRALE PER MUTE")
            launchCapture(recorder,epoch,false)
        }
        waitForAudio()
    }
    private fun launchCapture(recorder:GoogleCapture,epoch:Int,echo:Boolean) {
        GoogleVoiceDiagnostics.record(this,token,"capture","started")
        recorder.start(onSpeech={initial -> main.post {
            if(alive() && epoch==captureEpoch && !muted) beginRecognition(initial) else initial.fill(0)
        }},onChunk={frame,size ->
            val copy=frame.copyOf(size)
            main.post {try {if(alive() && epoch==captureEpoch && state.phase=="transcribing") recognition?.audio(copy)}
                catch(_:Exception) {if(alive() && epoch==captureEpoch) error("ERRORE STREAM GOOGLE — NESSUN REINVIO")}
                finally {copy.fill(0)}}
        },onEnd={audio,failure -> main.post {
            if(capture===recorder) capture=null
            GoogleVoiceDiagnostics.record(this,token,"capture","capture_released",JSONObject().put("capture_released",recorder.released))
            if(!alive() || epoch!=captureEpoch) {audio?.fill(0);return@post}
            if(audio==null) {
                if(failure!=null)GoogleVoiceDiagnostics.record(this,token,"capture",when(failure){"nessun_parlato"->"no_speech";"echo_non_disponibile"->"echo_unavailable";"microfono_silenziato"->"microphone_silenced";else->"microphone_unavailable"})
                if(echo && state.phase=="synthesizing") {
                    echoCapture=null;GoogleVoiceDiagnostics.record(this,token,"barge","echo_unavailable")
                    status("RISPOSTA VOCALE — ASCOLTO AUTOMATICO AL TERMINE")
                } else if(failure=="nessun_parlato" && canListen()) {main.postDelayed({startListening()},150)}
                else if(failure!=null) error("MICROFONO NON DISPONIBILE — VERIFICA AUDIO E PERMESSI")
                return@post
            }
            audio.fill(0);status("TRASCRIZIONE GOOGLE — ATTESA DEFINITIVO…")
            try {recognition?.finish() ?: error("STREAM NON DISPONIBILE — NESSUN INVIO")}
            catch(_:Exception) {error("ERRORE CHIUSURA STREAM GOOGLE — NESSUN REINVIO")}
        }})
    }
    /** Runs on the TTS worker: no main-thread waits, and no capture competing with an old recorder. */
    private fun prepareEcho(turn:String?):GoogleCapture? {
        val allowed={alive() && state.turn==turn && state.phase=="synthesizing" && !muted}
        val old=BridgeActions.onMain({alive() && state.turn==turn}) {captureEpoch++;val previous=capture;previous?.stop();capture=null;echoCapture=null;previous}
        val until=SystemClock.elapsedRealtime()+2000
        while(old?.released==false && allowed() && SystemClock.elapsedRealtime()<until) Thread.sleep(20)
        check(old?.released!=false && alive() && state.turn==turn)
        if(!config.bargeIn || !allowed() || conversation.isEmpty()) return null
        val fresh=credentials?.resolve(config,240000) ?: config
        if(fresh.expiresAt-System.currentTimeMillis()<240000) return null
        val recorder=BridgeActions.onMain(allowed) {
            resolvedConfig=fresh
            GoogleVoiceDependencies.echoCapture(config).also {capture=it;captureOwner=it;echoCapture=it;launchCapture(it,++captureEpoch,true)}
        } ?: return null
        val deadline=SystemClock.elapsedRealtime()+1600
        while(recorder.echoState=="pending" && allowed() && SystemClock.elapsedRealtime()<deadline) Thread.sleep(20)
        if(!allowed() || recorder.echoState!="ready") {
            BridgeActions.onMain({alive() && state.turn==turn}) {if(capture===recorder){captureEpoch++;capture=null;echoCapture=null};recorder.stop()}
            GoogleVoiceDiagnostics.record(this,token,"barge","echo_unavailable");return null
        }
        return recorder
    }
    private fun monitorEcho(recorder:GoogleCapture,player:GooglePlayback,turn:String?) {
        val until=SystemClock.elapsedRealtime()+700
        var original:Pair<Int?,Int?>?=null
        fun inspect() {
            if(!alive() || state.turn!=turn || echoCapture!==recorder || recorder.released || state.phase!="synthesizing") return
            val pair=recorder.routeType to player.routeType
            if(pair.first==null || pair.second==null) {
                if(SystemClock.elapsedRealtime()<until) {main.postDelayed({inspect()},50);return}
            }
            val valid=recorder.echoState=="ready" && GoogleEchoRoutePolicy.compatible(pair.first,pair.second) && (original==null || original==pair)
            GoogleVoiceDiagnostics.record(this,token,"barge",if(valid) "echo_ready" else "route_changed",JSONObject().put("record_route",pair.first ?: 0).put("play_route",pair.second ?: 0))
            if(!valid) {
                recorder.allowEchoSpeech(false);captureEpoch++;recorder.stop();if(capture===recorder)capture=null;echoCapture=null
                status("USCITA AUDIO CAMBIATA O NON VERIFICATA — ASCOLTO AL TERMINE");return
            }
            if(original==null)status("RISPOSTA GOOGLE — PUOI INTERROMPERE PARLANDO")
            original=pair;recorder.allowEchoSpeech(true)
            main.postDelayed({inspect()},400)
        }
        inspect()
    }
    private fun interruptReply() {
        waitingListening=false;receiptPending=false;dispatchEpoch++
        api?.cancel();playback?.stop()
        echoCandidate=if(playbackStarted) speech?.text else null
        val done=completion;completion=null
        done?.invoke(JSONObject().put("code","interrupted").put("accepted",speechAccepted).put("synthesized",synthesized)
            .put("playback_started",playbackStarted).put("completed",false))
        state.close();state=GoogleTurnState(token);speech=null
        GoogleVoiceDiagnostics.record(this,token,"barge","interrupted")
    }
    private fun beginRecognition(initial: ShortArray) {
        if(alive() && state.phase in setOf("waiting_reply","synthesizing")) interruptReply()
        waitingListening=false
        val turn = UUID.randomUUID().toString()
        if (!alive() || !state.begin(turn)) { initial.fill(0); return }
        GoogleVoiceDiagnostics.record(this,token,"stt","started")
        status("DETTATURA GOOGLE — PAUSA PER INVIARE")
        val client = GoogleVoiceDependencies.api(resolvedConfig ?: config); api = client
        client.reportTo {kind,code -> GoogleVoiceDiagnostics.record(this,token,"stt","provider_status",JSONObject().put(kind,code))}
        try {
            recognition=client.openRecognition { text, samples ->
                GoogleVoiceUsage.add(applicationContext,"stt_ms",samples/16L,!text.isNullOrBlank())
                GoogleVoiceDiagnostics.record(this,token,"stt",if(text.isNullOrBlank()) "failed" else "completed",JSONObject().put("samples",samples))
                main.post {
                    if (!alive() || state.turn != turn || state.phase != "transcribing") return@post
                    if(capture!=null) {error("STREAM GOOGLE TERMINATO PRIMA DELLA DETTATURA — NESSUN INVIO");return@post}
                    if (text==null) {error("ERRORE GOOGLE STT — CREDENZIALI, QUOTA O RETE; NESSUN REINVIO");return@post}
                    if (text.isBlank()) {error("NESSUNA TRASCRIZIONE — NUOVA DETTATURA");return@post}
                    val spoken=echoCandidate;echoCandidate=null
                    if(spoken!=null && GoogleEchoTextGuard.matches(text,spoken)) {
                        GoogleVoiceDiagnostics.record(this,token,"barge","echo_rejected")
                        state.close();state=GoogleTurnState(token);waitingListening=false
                        status("POSSIBILE ECO SCARTATA — RIPRENDO ASCOLTO, PARLA DI NUOVO")
                        main.postDelayed({if(alive())startListening()},700);return@post
                    }
                    transcriptView.append("Dettato",text)
                    if (state.submitting()) submit(turn,text)
                }
            }
            recognition?.audio(initial)
        } catch(_:Exception) {error("ERRORE AVVIO GOOGLE STT — NESSUN REINVIO")}
        finally {initial.fill(0)}
    }
    private fun submit(turn: String, transcript: String, startup: Boolean = false) {
        val epoch = ++dispatchEpoch
        val text = transcript + "\n\n[JarvisWake: session_id=$token; turn_id=$turn. Rispondi con jarviswake_speak per la voce, senza ChatGPT Voice.]"
        val until = SystemClock.elapsedRealtime() + 120000
        var sendConsumed=false
        fun current()=alive() && state.turn==turn && epoch==dispatchEpoch &&
            (state.phase in setOf("submitting","send_dispatched") || (sendConsumed && state.phase in setOf("synthesizing","ready")))
        fun step() {
            if (!current()) return
            web.evaluateJavascript(scriptFor("send", turn, text)) { raw ->
                if (!current()) return@evaluateJavascript
                GoogleVoiceDiagnostics.dom(this,token,if(startup) "post" else "send",raw)
                observeConversation(raw)
                when (parse(raw)) {
                    "busy", "ambiguous_editor" -> {
                        if(!writtenTurns.contains(turn) && SystemClock.elapsedRealtime()<until) {
                            status("CHAT OCCUPATA — DETTATO PRONTO, ATTENDO INVIO")
                            main.postDelayed({step()},300)
                        } else error("CHAT NON DISPONIBILE — NESSUN REINVIO")
                    }
                    "written", "waiting_send" -> { writtenTurns.add(turn); if (SystemClock.elapsedRealtime() < until) main.postDelayed({ step() }, 150) else error("INVIO NON DISPONIBILE — NESSUN REINVIO") }
                    "dispatched", "waiting_observation" -> {
                        sendConsumed=true;receiptPending=true;writtenTurns.add(turn);state.dispatched()
                        if(state.phase=="send_dispatched")status("INVIO ESEGUITO — ATTESA RISCONTRO CHAT")
                        if (SystemClock.elapsedRealtime() < until) main.postDelayed({ step() }, 200)
                        else if(state.phase=="send_dispatched") waitReply(turn,false)
                    }
                    "observed" -> {
                        if(conversation.isEmpty()) {error("DESTINAZIONE NON CONFERMATA — NESSUN REINVIO");return@evaluateJavascript}
                        receiptPending=false;state.dispatched()
                        if(state.phase=="send_dispatched") {state.observed();waitReply(turn,true)}
                        else if(state.phase=="ready")startListening()
                        // A receipt may bind the chat during synthesis/playback, without changing that phase.
                    }
                    "draft_changed" -> error("TESTO O EDITOR CAMBIATO — INVIO BLOCCATO; ESPORTA DIAGNOSTICA")
                    else -> error("INVIO NON CONFERMATO O BOZZA PRESENTE — NESSUN REINVIO")
                }
            }
        }
        step()
    }
    private fun waitReply(turn: String, observed: Boolean) {
        waitingListening=observed && conversation.isNotEmpty()
        if(waitingListening) main.postDelayed({if(alive() && state.turn==turn)startListening()},150)
        status(if (observed) "MESSAGGIO OSSERVATO — ATTESA VOCE DAL PLUGIN" else "INVIO DISPATCHED, NON OSSERVATO — ATTESA PLUGIN")
        main.postDelayed({
            if (alive() && state.turn == turn && state.phase in setOf("send_dispatched", "waiting_reply"))
                status("NESSUNA VOCE DAL PLUGIN — ATTENDO ANCORA; PARLA PER UN NUOVO TURNO; I LAVORI PROSEGUONO")
        }, 90000)
        if (config.authMode == "manual") main.postDelayed({
            if(alive() && state.turn==turn && state.phase in setOf("send_dispatched","waiting_reply"))
                error("CREDENZIALI GOOGLE SCADUTE — CHIUDI LA SESSIONE E RINNOVA SUL TELEFONO; LAVORI CONSERVATI")
        },(config.expiresAt-System.currentTimeMillis()).coerceAtLeast(0))
    }
    private fun acceptSpeech(p: JSONObject, permitted: () -> Boolean, progress: (String) -> Unit, done: (JSONObject) -> Unit): Boolean {
        val text = p.optString("text")
        if(alive() && config.authMode == "manual" && System.currentTimeMillis()>=config.expiresAt) {
            error("CREDENZIALI GOOGLE SCADUTE — NESSUNA SINTESI AVVIATA");return false
        }
        if (!alive() || !permitted() || text.isBlank() || text.toByteArray(Charsets.UTF_8).size > 4500 ||
            !state.acceptReply(p.optString("session_id"), p.optString("turn_id"))) return false
        val attempt=SpeechAttempt(text); speech=attempt; completion = done
        GoogleVoiceDiagnostics.record(this,token,"reply","accepted")
        progress("accepted")
        transcriptView.append("Jarvis",text)
        // Keep the already-consumed send's read-only receipt alive; it cannot overwrite this reply phase.
        status("RISPOSTA ACCETTATA — SINTESI GOOGLE…")
        val turn = state.turn
        val player = GoogleVoiceDependencies.playback(); playback = player; playbackOwner = player
        if(!ensureAudioFocus()) {finishSpeech("audio_focus_denied",false);return true}
        Thread({
            var ok = false
            var synthesisRequested = false
            var authenticated = false
            var retainedPcm:ByteArray?=null
            try {
                val fresh = credentials?.resolve(config, 90000) ?: config
                authenticated = true
                check(alive() && state.turn == turn && permitted())
                val client = GoogleVoiceDependencies.api(fresh); api = client
                client.reportTo {kind,code -> GoogleVoiceDiagnostics.record(this,token,"tts","provider_status",JSONObject().put(kind,code))}
                if (!alive() || state.turn != turn || !permitted()) { client.cancel(); throw InterruptedException() }
                synthesisRequested = true
                GoogleVoiceDiagnostics.record(this,token,"tts","started",JSONObject().put("characters",text.codePointCount(0,text.length)))
                val pcm = client.synthesize(text);retainedPcm=pcm; attempt.synthesized = true; progress("synthesized");GoogleVoiceDiagnostics.record(this,token,"tts","synthesized")
                GoogleVoiceUsage.add(applicationContext, "tts_characters", text.codePointCount(0, text.length).toLong(), true)
                val echo=try {prepareEcho(turn)} catch(_:Exception) {null}
                if(!alive() || state.turn!=turn || !permitted()) {pcm.fill(0);player.stop();return@Thread}
                check(echo!=null || captureOwner?.released!=false) {"audio_release_unconfirmed"}
                ok = player.play(pcm, { alive() && state.turn == turn && permitted() }) {
                    attempt.playbackStarted = true
                    progress("playback_started");GoogleVoiceDiagnostics.record(this,token,"playback","playback_started")
                    main.post { if (alive() && state.turn == turn) {
                        if(echo!=null) monitorEcho(echo,player,turn) else status("RISPOSTA GOOGLE — ASCOLTO AUTOMATICO AL TERMINE")
                    } }
                }
                main.post { if (alive() && state.turn == turn) finishSpeech(if (ok) "speech_completed" else "playback_failed", ok) }
            } catch (_: Exception) {
                if (synthesisRequested && !attempt.synthesized) GoogleVoiceUsage.add(applicationContext, "tts_characters", text.codePointCount(0, text.length).toLong(), false)
                main.post { if (alive() && state.turn == turn) finishSpeech(if (!authenticated) "authentication_failed" else if (attempt.synthesized) "playback_failed" else "synthesis_failed", false) }
            } finally {retainedPcm?.fill(0)}
        }, "Jarvis-google-tts").apply { isDaemon = true; start() }
        return true
    }
    private fun finishSpeech(code: String, ok: Boolean) {
        GoogleVoiceDiagnostics.record(this,token,"playback",code)
        val done = completion; completion = null
        done?.invoke(JSONObject().put("code", code).put("accepted", speechAccepted).put("synthesized", synthesized)
            .put("playback_started", playbackStarted).put("completed", ok))
        if (ok) {
            waitingListening=false;state.ready()
            if(receiptPending && conversation.isEmpty())status("RISPOSTA COMPLETATA — ATTESA RISCONTRO CHAT")
            else if(capture?.released==false && echoCapture===capture && capture?.echoState=="ready") {
                capture?.allowEchoSpeech(true);status("ASCOLTO GOOGLE — PARLA QUANDO VUOI")
            } else {status("RISPOSTA COMPLETATA — RIPRESA ASCOLTO…");main.postDelayed({if(alive())startListening()},700)}
        }
        else error(if (code == "authentication_failed") "RINNOVO GOOGLE FALLITO — NESSUNA SINTESI AVVIATA; VERIFICA BACKEND" else "RIPRODUZIONE FALLITA — NUOVA DETTATURA O CHIUDI")
    }
    private fun error(message: String) {
        if (!alive()) return
        WaitingAudio.stop()
        focus?.let {getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it)};focus=null
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
        else { status("MICROFONO RIATTIVATO"); if (canListen()) startListening() }
        hud.setMicrophoneMuted(muted); VoiceCycle.confirmMicrophoneMuted(token, muted)
    }
    private fun keepCoverOverMaps() {
        if(coverHidden || overlayManager!=null) return
        val service=VoiceAccessibilityService.bridgeService() ?: throw BridgeFailure("unavailable")
        val manager=service.getSystemService(android.view.WindowManager::class.java)
        (cover.parent as? android.view.ViewGroup)?.removeView(cover)
        val params=android.view.WindowManager.LayoutParams(-1,-1,android.view.WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,android.graphics.PixelFormat.TRANSLUCENT)
            .apply {gravity=Gravity.TOP or Gravity.START}
        overlayManager=manager
        try {manager.addView(cover,params)} catch(_:Exception) {
            runCatching {manager.removeViewImmediate(cover)};overlayManager=null
            if(alive() && !coverHidden && cover.parent==null) root.addView(cover,FrameLayout.LayoutParams(-1,-1))
            throw BridgeFailure("unavailable")
        }
    }
    private fun exportDiagnostics() {
        if(exportBusy || !alive() || coverHidden) return
        exportBusy=true
        val generation=++exportGeneration
        GoogleVoiceDiagnostics.snapshot(this,token,diagnosticSnapshot())
        val allowed={alive() && !coverHidden && generation==exportGeneration}
        val done: (String)->Unit = { code ->
            if(allowed()) {
                exportBusy=false
                GoogleVoiceDiagnostics.record(this,token,"screenshot",code)
                DiagnosticScreenshots.notify(this,code)
                hideCoverForApp()
                DiagnosticExportActivity.open(this)
            }
        }
        if(overlayManager==null) DiagnosticScreenshots.window(this,allowed,done)
        else VoiceAccessibilityService.bridgeService()?.let {DiagnosticScreenshots.display(it,allowed,done)} ?: done("accessibility_missing")
    }
    private fun removeCover() {
        // Remove interception before token guards/audio cleanup, including an Android Auto overlay.
        exportGeneration++;exportBusy=false
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
        closed = true;echoCandidate=null;receiptPending=false
        credentials?.cancel(); credentials = null; resolvedConfig = null
        if (::transcriptView.isInitialized) transcriptView.clearText()
        if (::web.isInitialized) runCatching { web.evaluateJavascript("window.__jarvisGoogleText?.disposeDiagnostics?.(); delete window.__jarvisGoogleText;", null) }
        if (::state.isInitialized) state.close()
        GoogleVoiceDiagnostics.record(this,token,"session","closed")
        GoogleVoiceDiagnostics.snapshot(this,token,diagnosticSnapshot())
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
    var credentials: (android.content.Context) -> GoogleCredentialSession = { GoogleTokenBroker(requireNotNull(BridgeSettings.read(it))) }
    var opening: (android.content.Context) -> String = { OpeningMessageSettings.get(it) }
    var greeting: (android.content.Context) -> String = { VoiceGreetingSettings.get(it) }
    var project: (android.content.Context) -> String = { ChatGptProjectSettings.getUrl(it) }
    var configuration: (android.content.Context) -> GoogleVoiceConfig? = { GoogleVoiceSettings.read(it) }
    var api: (GoogleVoiceConfig) -> GoogleSpeechApi = { GoogleSpeechClient(it) }
    var echoCapture: (GoogleVoiceConfig) -> GoogleCapture = { GoogleAudioCapture(it,true) }
    var capture: (GoogleVoiceConfig) -> GoogleCapture = { GoogleAudioCapture(it) }
    var playback: () -> GooglePlayback = { GoogleAudioPlayback() }
    var web: (android.content.Context) -> WebView = { WebView(android.content.MutableContextWrapper(it.applicationContext)) }
    fun reset() {
        credentials = { GoogleTokenBroker(requireNotNull(BridgeSettings.read(it))) }
        opening = { OpeningMessageSettings.get(it) }; greeting = { VoiceGreetingSettings.get(it) }
        project = { ChatGptProjectSettings.getUrl(it) }
        configuration = { GoogleVoiceSettings.read(it) }; api = { GoogleSpeechClient(it) }
        echoCapture = { GoogleAudioCapture(it,true) }
        capture = { GoogleAudioCapture(it) }; playback = { GoogleAudioPlayback() }; web = { WebView(android.content.MutableContextWrapper(it.applicationContext)) }
    }
}
