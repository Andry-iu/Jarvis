package it.jarviswake

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject
import org.json.JSONTokener

/** A second, detached text-only renderer. Never adds a window, opens UI, or requests media. */
internal object PreparedChat {
    private val main=Handler(Looper.getMainLooper())
    private var app: Context?=null
    private var ledger: PreparedChatLedger?=null
    private var view: WebView?=null
    private var deadline:Runnable?=null
    private var pending=false
    private var evaluated=false
    private var pageReady=false
    private var script: String?=null
    private var lastPhase=""
    private var lastProgress=""
    private const val NAMESPACE="prepared_chat_private"
    private fun prefs(c:Context)=c.getSharedPreferences("prepared_chat_settings",Context.MODE_PRIVATE)
    // Enabling is also the user's confirmation that the WebView has the same account/project.
    fun enabled(c:Context)=prefs(c).getBoolean("enabled",false)
    fun enable(c:Context,value:Boolean) {
        prefs(c).edit().putBoolean("enabled",value).apply()
        if (!value) stop(c,"disabled") else maintain(c)
    }
    private fun encode(r:PreparedConversation?)=if(r==null) JSONObject() else JSONObject()
        .put("attempt",r.attempt).put("project",r.project).put("name",r.projectName).put("created",r.createdAt)
        .put("phase",r.phase.name).put("url",r.url).put("hash",r.hash).put("length",r.proofLength)
        .put("ready",r.readyAt).put("cycle",r.cycle).put("error",r.error)
    private fun decode(j:JSONObject):PreparedConversation? {
        if (!j.has("attempt")) return null
        return PreparedConversation(j.getString("attempt"),j.getString("project"),j.getString("name"),j.getLong("created"),
            PreparationPhase.valueOf(j.getString("phase")),j.optString("url"),j.optString("hash"),j.optInt("length"),
            j.optLong("ready"),j.optString("cycle"),j.optString("error"))
    }
    private fun repository(c:Context):PreparedChatLedger {
        ledger?.let {return it}
        app=c.applicationContext
        val context=app!!
        val created=PreparedChatLedger(decode(BridgeSettings.readPrivate(context,"slot",NAMESPACE))) {
            BridgeSettings.writePrivate(context,"slot",encode(it),NAMESPACE)
        }
        created.recover();ledger=created;return created
    }
    private fun project(c:Context)=ChatGptProjectSettings.getUrl(c)
    fun status(c:Context):String {
        if (!enabled(c)) return "Preparazione disattivata. Accedi nel WebView con lo stesso account ChatGPT e verifica il progetto prima di abilitarla."
        return runCatching {
            when(repository(c).record?.phase) {
                PreparationPhase.STARTED -> when(lastProgress) {
                    "waiting_url" -> "Messaggio inviato; attendo l’indirizzo della nuova chat."
                    "waiting_user", "waiting_acceptance" -> "Messaggio inviato; attendo conferma nella pagina della chat."
                    "waiting_reply" -> "Messaggio verificato; attendo la risposta di ChatGPT."
                    else -> "Preparazione testuale in corso; puoi usare il telefono."
                }
                PreparationPhase.READY -> if(System.currentTimeMillis()-repository(c).record!!.readyAt in 0..PreparedChatPolicy.READY_TTL_MS)
                    "Chat preparata e link salvato. Verrà usata una sola volta al prossimo avvio Voice."
                    else "Preparazione scaduta; usa Riprova preparazione."
                PreparationPhase.CLAIMED -> "Chat preparata già assegnata a un ciclo; nessuna seconda apertura automatica."
                PreparationPhase.FAILED -> if(repository(c).record?.error=="handoff_unconfirmed")
                    "Riapertura nativa non confermata. Controlla il link salvato e l’account; nessuna nuova preparazione automatica."
                    else if(repository(c).record?.error=="wrong_conversation")
                        "Messaggio inviato ma non verificato nella nuova chat. Esporta la diagnostica prima di riprovare."
                    else "Preparazione non confermata. Verifica l’accesso nel WebView e usa Riprova preparazione."
                null -> "In attesa di preparare una chat. Avvia l’ascolto per mantenerla in background."
            }
        }.getOrDefault("Archivio preparazione non disponibile; percorso Voice normale conservato.")
    }
    fun resetForLogin(c:Context) {
        enable(c,false)
        runCatching {repository(c).reset()}
    }
    fun retry(c:Context) {
        if (VoiceCycle.active) return
        destroyView();repository(c).reset();maintain(c)
    }
    fun savedLink(c:Context):String?=runCatching {
        repository(c).record?.takeIf {it.project==project(c) && it.projectName==NativeChatSettings.project(c) &&
            PreparedChatPolicy.conversationUrl(it.url,it.project)!=null}?.url
    }.getOrNull()
    fun claim(c:Context,cycle:String):PreparedConversation? {
        if (!enabled(c)) return null
        return runCatching {
            repository(c).claim(cycle,project(c),NativeChatSettings.project(c),System.currentTimeMillis())?.also {
                destroyView();record(c,it.attempt,"claimed")
            }
        }.getOrNull()
    }
    fun forCycle(c:Context,cycle:String):PreparedConversation? = if (!enabled(c)) null else runCatching {
        repository(c).forCycle(cycle)
    }.getOrNull()
    fun cycleEnded(c:Context,cycle:String,voiceObserved:Boolean) {
        if(!enabled(c) || voiceObserved) return
        // Never perform storage work in the immediate HUD removal/exit callback.
        main.post {runCatching {repository(c).rejectClaim(cycle)}}
    }
    private fun progressDetails(outcome:JSONObject?):String {
        if(outcome==null || !outcome.has("users")) return ""
        val source=outcome.optString("source").takeIf {it in setOf("none","body","root","clean_root")} ?: "none"
        val reason=outcome.optString("reason").takeIf {it in setOf("message_absent","message_count","body_pending",
            "message_mismatch","pending_acceptance","verified","url_changed")} ?: "unknown"
        return " users=${outcome.optInt("users",-1).coerceIn(-1,1000)} rawUsers=${outcome.optInt("rawUsers",-1).coerceIn(-1,1000)}"+
            " source=$source matches=${outcome.optBoolean("userMatches")} echo=${outcome.optBoolean("echoObserved")}"+
            " urlObserved=${outcome.optBoolean("urlObserved")} reason=$reason"
    }
    private fun record(c:Context,attempt:String,phase:String,details:String="") {
        val key="$attempt|$phase|$details"
        if (key==lastPhase) return
        lastPhase=key;lastProgress=phase
        // Only bounded counts, booleans and fixed codes; never URL, ID, title or response.
        NativeRuntimeSnapshot.save(c,attempt,"preparation","phase=$phase$details")
    }
    fun maintain(c:Context) {
        if (!enabled(c) || !NativeChatSettings.enabled(c)) {if(view!=null) stop(c,"disabled");return}
        runCatching {
            val repo=repository(c)
            val current=repo.record
            if (current!=null && (current.project!=project(c) || current.projectName!=NativeChatSettings.project(c))) {
                destroyView();repo.reset()
            }
            if (view!=null) return
            if (VoiceCycle.active) return
            if (repo.record?.phase==PreparationPhase.STARTED) return // Never reconstruct/replay an uncertain document.
            if (repo.record?.phase==PreparationPhase.READY || repo.record?.phase==PreparationPhase.FAILED) return
            val attempt=repo.begin(project(c),NativeChatSettings.project(c),System.currentTimeMillis()) ?: return
            start(c.applicationContext,attempt)
        }.onFailure {
            ledger?.record?.takeIf {it.phase==PreparationPhase.STARTED}?.let {fail(it,"setup_error")}
            destroyView()
        }
    }
    private val poll=object:Runnable {
        override fun run() {
            val c=app ?: return
            if(!enabled(c) || !NativeChatSettings.enabled(c)) {stop(c,"disabled");return}
            val attempt=ledger?.record ?: return
            val current=view ?: return
            if (attempt.phase!=PreparationPhase.STARTED) return
            if (System.currentTimeMillis()-attempt.createdAt !in 0..PreparedChatPolicy.PREPARE_TIMEOUT_MS) {
                fail(attempt,"timeout");return
            }
            if (!pageReady || pending) {main.postDelayed(this,500L);return}
            pending=true; evaluated=true
            val code=(script ?: c.assets.open("prepare-chat.js").bufferedReader().use {it.readText()}.also {script=it})
                .replace("__JARVIS_PROJECT_JSON__",JSONObject.quote(attempt.project))
                .replace("__JARVIS_ATTEMPT_JSON__",JSONObject.quote(attempt.attempt))
            runCatching {
                current.evaluateJavascript(code) { raw ->
                    if (view!==current || ledger?.record?.attempt!=attempt.attempt) return@evaluateJavascript
                    pending=false
                    val outcome=runCatching {JSONObject(JSONTokener(raw).nextValue() as String)}.getOrNull()
                    val phase=outcome?.optString("phase").orEmpty()
                    record(c,attempt.attempt,phase.takeIf {it in phases} ?: "invalid_result",progressDetails(outcome))
                    when {
                        phase=="ready" -> {
                            if (outcome?.optBoolean("echoObserved")==true && outcome.optBoolean("urlObserved") &&
                                ledger?.complete(attempt.attempt,outcome.optString("url"),outcome.optString("reply"),System.currentTimeMillis())==true) {
                                destroyView();record(c,attempt.attempt,"ready")
                            } else fail(attempt,"invalid_result")
                        }
                        phase !in waiting -> fail(attempt,phase.takeIf {it in phases} ?: "invalid_result")
                        else -> main.postDelayed(this,500L)
                    }
                }
            }.onFailure {pending=false;fail(attempt,"script_error")}
        }
    }
    private val waiting=setOf("waiting_editor","written","waiting_send","submitted","waiting_url","waiting_user","waiting_acceptance","waiting_reply")
    private val phases=waiting+setOf("ready","login","foreign_attempt","wrong_project","existing_conversation","draft","write_failed","send_uncertain","wrong_conversation")
    private fun fail(attempt:PreparedConversation,code:String) {
        runCatching {ledger?.fail(attempt.attempt,code)}
        app?.let {record(it,attempt.attempt,code)}
        destroyView()
    }
    @SuppressLint("SetJavaScriptEnabled")
    private fun start(c:Context,attempt:PreparedConversation) {
        app=c;pageReady=false;pending=false;evaluated=false
        val web=WebView(c);view=web
        web.settings.apply {
            DesktopWeb.configure(this);javaScriptEnabled=true;domStorageEnabled=true
            mediaPlaybackRequiresUserGesture=true;allowFileAccess=false;allowContentAccess=false
            mixedContentMode=android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportMultipleWindows(false)
        }
        web.webChromeClient=object:WebChromeClient() {
            override fun onPermissionRequest(request:PermissionRequest) {request.deny()}
        }
        web.webViewClient=object:WebViewClient() {
            override fun shouldOverrideUrlLoading(v:WebView,request:WebResourceRequest):Boolean =
                !runCatching {android.net.Uri.parse(request.url.toString()).let {
                    it.scheme=="https" && it.host in setOf("chatgpt.com","auth.openai.com","auth0.openai.com")
                }}.getOrDefault(false)
            override fun onPageStarted(v:WebView,url:String,icon:android.graphics.Bitmap?) {
                if(view===v) {
                    if(evaluated) {fail(attempt,"document_changed");return}
                    pageReady=false;pending=false
                }
            }
            override fun onPageFinished(v:WebView,url:String) {
                if(view!==v) return
                v.evaluateJavascript(DesktopWeb.viewportScript,null);pageReady=true
            }
            override fun onReceivedError(v:WebView,request:WebResourceRequest,error:WebResourceError) {
                if(view===v && request.isForMainFrame) fail(attempt,"page_error")
            }
            override fun onReceivedHttpError(v:WebView,request:WebResourceRequest,response:WebResourceResponse) {
                if(view===v && request.isForMainFrame) fail(attempt,"http_error")
            }
            override fun onRenderProcessGone(v:WebView,detail:RenderProcessGoneDetail):Boolean {
                if(view===v) fail(attempt,"renderer_gone")
                return true
            }
        }
        // A detached view has a layout viewport but owns no WindowManager surface or touch area.
        val width=c.resources.displayMetrics.widthPixels.coerceAtLeast(720)
        val height=c.resources.displayMetrics.heightPixels.coerceAtLeast(1280)
        web.layoutParams=android.view.ViewGroup.LayoutParams(width,height)
        web.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY))
        web.layout(0,0,width,height);web.onResume()
        record(c,attempt.attempt,"started")
        deadline=Runnable {
            if(view===web && ledger?.record?.attempt==attempt.attempt) fail(attempt,"timeout")
        }.also {main.postDelayed(it,PreparedChatPolicy.PREPARE_TIMEOUT_MS)}
        main.removeCallbacks(poll);main.postDelayed(poll,500L)
        web.loadUrl(attempt.project)
    }
    fun stop(c:Context,reason:String="service_stopped") {
        ledger?.record?.takeIf {it.phase==PreparationPhase.STARTED}?.let {fail(it,reason)}
        destroyView()
    }
    private fun destroyView() {
        deadline?.let(main::removeCallbacks);deadline=null
        main.removeCallbacks(poll);pending=false;pageReady=false
        val previous=view;view=null
        previous?.let {runCatching {it.stopLoading();it.onPause();it.destroy()}}
    }
}
