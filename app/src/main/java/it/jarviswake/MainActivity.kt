package it.jarviswake

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.app.role.RoleManager
import android.provider.Settings
import android.view.View
import android.view.Gravity
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import android.widget.*
import android.webkit.WebView
import android.webkit.WebViewClient

class MainActivity : Activity() {
    companion object {
        @Volatile private var instance = java.lang.ref.WeakReference<MainActivity>(null)

        /** Uses an already-existing activity; never launches UI from the background. */
        fun preloadForScreenOn(): Boolean {
            val activity = instance.get() ?: return false
            if (!WakeScreenPolicy.shouldPreloadForScreenOn(
                    mainActivityAvailable = true, voiceCycleActive = VoiceCycle.active))
                return false
            activity.handler.post {
                if (!activity.isFinishing && !activity.isDestroyed &&
                    WakeScreenPolicy.shouldPreloadForScreenOn(true, VoiceCycle.active) &&
                    activity::contentRoot.isInitialized) {
                    activity.prepareChatGpt()
                }
            }
            return true
        }
    }

    private val assistantPermissionRequest = 2
    private val assistantRoleRequest = 3
    private val diagnosticsExportRequest = 4
    private val waitingAudioRequest = 5
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private lateinit var attemptStatus: TextView
    private lateinit var coverButton: Button
    private lateinit var openingInput: EditText
    private lateinit var greetingInput: EditText
    private lateinit var projectUrlInput: EditText
    private lateinit var waitingAudioName: TextView
    private lateinit var contentRoot: FrameLayout
    private lateinit var homeScreen: View
    private lateinit var menu: SplitMenuScreen
    private lateinit var navigation: MenuNavigation
    private val layout get()=menu.controls
    private var openingDraft:String?=null
    private var greetingDraft:String?=null
    private var projectDraft:String?=null
    private var actionFeedback=false
    private var chatSettingsLayer: FrameLayout? = null
    private var settingsBackCallback: OnBackInvokedCallback? = null
    private val update = object : Runnable {
        override fun run() {
            if (::status.isInitialized) status.text = (if (State.running) "● " else "○ ") + State.get(this@MainActivity)
            if (::attemptStatus.isInitialized) attemptStatus.text = State.getAttempt(this@MainActivity)
            if (::waitingAudioName.isInitialized) updateWaitingAudioName()
            if (::menu.isInitialized && navigation.current==MenuPage.HOME && actionFeedback) refreshActionFeedback()
            handler.postDelayed(this, 1000)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setDecorFitsSystemWindows(false)
        instance = java.lang.ref.WeakReference(this)
        JarvisRecognitionService.rememberSystemRecognizer(this)
        navigation=MenuNavigation(savedInstanceState?.getStringArrayList("menu_path").orEmpty(),savedInstanceState?.getInt("menu_step",-1) ?: -1)
        openingDraft=savedInstanceState?.getString("opening_draft")
        greetingDraft=savedInstanceState?.getString("greeting_draft")
        projectDraft=savedInstanceState?.getString("project_draft")
        contentRoot=FrameLayout(this)
        setContentView(contentRoot)
        renderMenu(false)
        BridgeCarConnection.start(this)
        handler.post { requestStart() }
    }

    private fun text(value:String,size:Float=16f)=menu.text(value,size)
    private fun button(label:String,action:()->Unit)=menu.button(label,action=action)
    private fun openPage(page:MenuPage) { captureDrafts();actionFeedback=false;navigation.open(page);renderMenu(false) }
    private fun captureDrafts() {
        if(navigation.current==MenuPage.GREETING) {
            if(::greetingInput.isInitialized) greetingDraft=greetingInput.text.toString()
            if(::openingInput.isInitialized) openingDraft=openingInput.text.toString()
        }
        if(navigation.current==MenuPage.CHATGPT && ::projectUrlInput.isInitialized && !NativeChatSettings.enabled(this)) projectDraft=projectUrlInput.text.toString()
    }
    private fun title(page:MenuPage)=getString(when(page) {
        MenuPage.HOME->R.string.menu_home;MenuPage.CONFIGURATION->R.string.menu_configuration
        MenuPage.SERVICE->R.string.menu_service;MenuPage.PERMISSIONS->R.string.menu_permissions
        MenuPage.CHATGPT->R.string.menu_chatgpt;MenuPage.GREETING->R.string.menu_greeting
        MenuPage.AUDIO->R.string.menu_audio;MenuPage.COVER->R.string.menu_cover;MenuPage.LINK->R.string.menu_link
        MenuPage.SUMMARY->R.string.menu_summary;MenuPage.WEB_MAINTENANCE->R.string.menu_web_maintenance
        MenuPage.DIAGNOSTICS->R.string.menu_diagnostics
    })
    private fun help(page:MenuPage)=when(page) {
        MenuPage.HOME->"Parla con Jarvis apre il ciclo vocale configurato. Avvia ascolto abilita la parola Hey Jarvis; Ferma ascolto è uno stop volontario. Gli appunti sono accessibili anche senza collegamento. Configurazione e Servizio sono aree separate. Apri la Guida completa per pronuncia, gesti e plugin."
        MenuPage.CONFIGURATION->"Apri direttamente una categoria oppure Configurazione guidata per attraversarle in ordine. Le impostazioni attuali restano salvate: la guida non le ripristina. Nei passaggi puoi tornare indietro; Avanti salva URL e frase compilati e validi, senza avviare prove vocali o azioni del telefono."
        MenuPage.SERVICE->"Qui trovi diagnostica, impostazioni Android e manutenzione. Questi strumenti sono separati dall’uso quotidiano. La cancellazione dei dati WebView richiede conferma e può richiedere un nuovo accesso a ChatGPT. Gli appunti locali e il collegamento hanno archivi distinti."
        MenuPage.PERMISSIONS->"Microfono e notifiche servono all’ascolto. Accessibilità controlla Voice nell’app ChatGPT; HUD consente la cover. Il ruolo Assistente cambia l’assistente digitale predefinito e va scelto nel pannello Android. Permessi e batteria apre le impostazioni di sistema: torna qui dopo la scelta. Nessuna autorizzazione viene concessa automaticamente."
        MenuPage.CHATGPT->"Scegli l’app ChatGPT nativa o il WebView desktop. Nell’app nativa seleziona lo shortcut creato dal progetto e conferma il nome esatto visibile, rispettando gli spazi. Prova collegamento apre solo il progetto, senza Voice. Nel WebView usa l’URL del progetto e accedi con il tuo account. Modello e livello si scelgono dentro ChatGPT. Termina un ciclo attivo prima di cambiare modalità o collegamento."
        MenuPage.GREETING->"Messaggio prima di Voice configura il testo iniziale per app ChatGPT e preparazione in background; Salva o Avanti lo applicano a ciclo chiuso e invalidano la vecchia preparazione se cambia. Il campo Frase del saluto viene richiesto una volta dopo la conferma Voice, nella stessa conversazione. Salva frase applica il testo; Ripristina rimette ciao jarvis. Nella guida Avanti salva una frase valida. Il salvataggio della preferenza non prova che ChatGPT abbia inviato il messaggio o pronunciato il saluto. Le bozze personali non vengono inviate."
        MenuPage.AUDIO->"Attiva l’audio e scegli un file dal telefono. Volume regola solo l’audio di attesa di Jarvis, non quello di ChatGPT o del telefono. Spostando il volume parte un’anteprima anche con audio disattivato: dura il minimo scelto, o 2 secondi se il minimo è zero. Tempo minimo ritarda solo il comando Voice fino a 30 secondi. Lasciando questa sezione l’anteprima si ferma."
        MenuPage.COVER->"Scegli l’aspetto iniziale opaco o semitrasparente. Durante Voice: tocco centrale alterna il microfono; mute confermato significa animazione grigia e ferma. Tocco sotto l’animazione cambia trasparenza. Doppio tocco centrale chiude subito la cover e richiede stop Voice. Swipe sinistro nasconde soltanto la cover mantenendo Voice attiva; swipe in basso termina Voice e torna Home; doppio tocco in alto a destra esporta diagnostica. Leggi la guida per le zone dei gesti."
        MenuPage.LINK->"Collegamento ChatGPT abbina il telefono al plugin JarvisWake Link. Abbinamento, consenso Maps/chiamate/WhatsApp e consenso appunti sono categorie distinte. Le azioni richiedono una richiesta esplicita e i rispettivi permessi. Un aggiornamento firmato conserva l’abbinamento; cancellarlo non elimina gli appunti. Il calendario resta al plugin dedicato."
        MenuPage.SUMMARY->"Questo riepilogo legge le impostazioni attuali, senza eseguire chiamate, messaggi o navigazione. Puoi tornare ai passaggi precedenti. Termina torna all’uso quotidiano. I permessi e l’accesso a ChatGPT restano scelte da effettuare nelle schermate supportate; una configurazione salvata non certifica il collaudo del telefono."
        MenuPage.WEB_MAINTENANCE->"Svuota cache elimina soltanto risorse WebView e ricarica ChatGPT. Cancella dati WebView elimina anche cookie e dati dei siti del WebView, richiede conferma e può disconnettere l’account. Questi comandi non cancellano l’app ChatGPT nativa, gli appunti locali o il codice del plugin. Non eseguirli durante Voice."
        MenuPage.DIAGNOSTICS->"Esporta diagnostica crea un file ZIP nella destinazione che scegli. Puoi allegarlo alla chat per analizzare un problema. Contiene eventi e metadati dei controlli: non audio, valori delle note, password o testo generico delle conversazioni. Anche il doppio tocco in alto a destra sulla cover avvia l’esportazione."
    }
    private fun renderMenu(capture:Boolean=true) {
        if(capture && ::menu.isInitialized) captureDrafts()
        window.hideMenuKeyboard()
        WaitingAudio.stopPreview()
        if(::homeScreen.isInitialized) contentRoot.removeView(homeScreen)
        val page=navigation.current
        val caption=if(navigation.guided && page!=MenuPage.SUMMARY) getString(R.string.menu_step,navigation.step+1,MenuNavigation.STEPS.size-1)+" · "+title(page) else title(page)
        menu=SplitMenuScreen(this,caption,help(page),if(navigation.canBack) ({ menuBack() }) else null)
        homeScreen=menu
        contentRoot.addView(homeScreen,FrameLayout.LayoutParams(-1,-1))
        when(page) {
            MenuPage.HOME -> {
                text("J.A.R.V.I.S. · "+appVersionName(),22f)
                status=text(State.get(this));attemptStatus=text(State.getAttempt(this),14f)
                button("Appunti locali") { startActivity(Intent(this,LocalNotesActivity::class.java)) }
                menu.button("Preferiti e cache contatti", "Due sezioni separate: preferiti dalla rubrica o con nome e numero manuali, cache automatica rimovibile. Non avvia telefonate.") { startActivity(Intent(this,ContactCacheActivity::class.java)) }
                button("Parla con Jarvis") { startVoiceCycle() }
                button("Avvia ascolto Hey Jarvis") {
                    actionFeedback=true
                    DiagnosticLog.record(this,DiagnosticLog.Event.MANUAL_ACTION_REQUESTED,monitorStatus="wake_start")
                    requestStart();refreshActionFeedback()
                }
                button("Ferma ascolto") { actionFeedback=true;startService(Intent(this,WakeService::class.java).setAction("STOP"));refreshActionFeedback() }
                text("Impostazioni e supporto",18f)
                button(getString(R.string.menu_configuration)) { openPage(MenuPage.CONFIGURATION) }
                button(getString(R.string.menu_service)) { openPage(MenuPage.SERVICE) }
                button("Guida completa") { startActivity(Intent(this,GuideActivity::class.java)) }
            }
            MenuPage.CONFIGURATION -> {
                button(getString(R.string.menu_guide)) { captureDrafts();navigation.begin();renderMenu(false) }
                for(category in MenuNavigation.STEPS.dropLast(1)) button(title(category)) { openPage(category) }
            }
            MenuPage.SERVICE -> {
                button("Galaxy Watch · prova audio") { startActivity(Intent(this,WatchProbeActivity::class.java)) }
                for(category in listOf(MenuPage.PERMISSIONS,MenuPage.DIAGNOSTICS,MenuPage.WEB_MAINTENANCE)) button(title(category)) { openPage(category) }
                button("Gestisci abbinamento ChatGPT") { openBridge("pair") }
                button("Guida completa e plugin") { startActivity(Intent(this,GuideActivity::class.java)) }
            }
            MenuPage.PERMISSIONS -> {
                text(permissionSummary())
                button("Autorizza microfono e notifiche") { requestBasicPermissions() }
                button("Imposta Jarvis come assistente") { requestAssistantRole() }
                button("Abilita automazione Voice") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                button("Consenti cover sopra le altre app") { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName"))) }
                button("Permessi Android e batteria") { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:$packageName"))) }
            }
            MenuPage.CHATGPT -> buildChatPage()
            MenuPage.GREETING -> {
                openingInput=menu.field("Messaggio prima di Voice",openingDraft ?: OpeningMessageSettings.get(this),
                    "Testo inviato prima di Voice nell’app ChatGPT e nella preparazione in background. Salva a ciclo chiuso: una frase diversa sostituisce la preparazione precedente. Il saluto dopo Voice ha un campo separato.",multiline=true,minRows=5)
                button("Salva messaggio prima di Voice") {saveOpeningMessage()}
                button("Ripristina messaggio predefinito") {
                    if(OpeningMessageSettings.reset(this)) {
                        openingDraft=OpeningMessageSettings.get(this);openingInput.setText(openingDraft);openingInput.error=null
                        Toast.makeText(this,"Messaggio predefinito ripristinato.",Toast.LENGTH_SHORT).show()
                    } else openingInput.error=if(VoiceCycle.active) "Termina prima il ciclo Voice attivo." else "Impossibile ripristinare il messaggio."
                }
                text("Saluto dopo Voice",18f)
                greetingInput=menu.field("Frase del saluto",greetingDraft ?: VoiceGreetingSettings.get(this),help(page),multiline=true,minRows=5)
                button("Salva frase") {
                    if(VoiceGreetingSettings.save(this,greetingInput.text.toString())) {
                        greetingInput.setText(VoiceGreetingSettings.get(this));greetingDraft=greetingInput.text.toString()
                        Toast.makeText(this,"Frase salvata.",Toast.LENGTH_SHORT).show()
                    } else greetingInput.error="Inserisci una frase prima di salvare."
                }
                button("Ripristina «ciao jarvis»") { VoiceGreetingSettings.reset(this);greetingDraft=VoiceGreetingSettings.get(this);greetingInput.setText(greetingDraft) }
            }
            MenuPage.AUDIO -> buildAudioPage()
            MenuPage.COVER -> {
                coverButton=menu.button("", "Cambia l’opacità iniziale della cover tra opaca e semitrasparente. Non cambia mute, audio o gesti.") { CoverSettings.setOpaque(this,!CoverSettings.isOpaque(this));updateCoverButton() }
                updateCoverButton()
                button("Guida ai gesti") { startActivity(Intent(this,GuideActivity::class.java).putExtra("topic","cover")) }
            }
            MenuPage.LINK -> {
                text(if(BridgeSettings.configured(this)) "Abbinamento presente" else "Collegamento da configurare")
                button("Abbinamento e stato") { openBridge("pair") }
                button("Maps, chiamate e WhatsApp") { openBridge("actions") }
                button("Accesso agli appunti dal plugin") { openBridge("notes") }
            }
            MenuPage.SUMMARY -> text(configurationSummary())
            MenuPage.WEB_MAINTENANCE -> buildWebMaintenance()
            MenuPage.DIAGNOSTICS -> button("Esporta diagnostica") { exportDiagnostics() }
        }
        if(navigation.guided) menu.navigation(
            getString(R.string.menu_back),{menuBack()},
            getString(if(page==MenuPage.SUMMARY) R.string.menu_finish else R.string.menu_next),{advanceGuide()})
        updateMenuBack()
        if(page==MenuPage.HOME && actionFeedback) refreshActionFeedback()
        menu.requestFocus()
    }
    private fun openBridge(section:String) { startActivity(Intent(this,BridgeSetupActivity::class.java).putExtra("section",section)) }
    private fun permissionSummary():String {
        fun granted(p:String)=if(checkSelfPermission(p)==PackageManager.PERMISSION_GRANTED) "autorizzato" else "da autorizzare"
        val roles=getSystemService(RoleManager::class.java)
        return "Microfono: "+granted(Manifest.permission.RECORD_AUDIO)+
            (if(Build.VERSION.SDK_INT>=33) "\nNotifiche: "+granted(Manifest.permission.POST_NOTIFICATIONS) else "")+
            "\nCover sopra le app: "+(if(Settings.canDrawOverlays(this)) "autorizzata" else "da autorizzare")+
            "\nAccessibilità: "+(if(VoiceAccessibilityService.bridgeService()!=null) "collegata" else "non collegata")+
            "\nAssistente: "+(if(roles.isRoleHeld(RoleManager.ROLE_ASSISTANT)) "Jarvis selezionato" else "altro assistente")
    }
    private fun requestBasicPermissions() {
        val missing=mutableListOf<String>()
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) missing+=Manifest.permission.RECORD_AUDIO
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) missing+=Manifest.permission.POST_NOTIFICATIONS
        if(missing.isNotEmpty()) requestPermissions(missing.toTypedArray(),6) else menu.feedback("Permessi", "Microfono e notifiche già autorizzati.")
    }
    private fun buildChatPage() {
        val native=NativeChatSettings.enabled(this)
        val destination=RadioGroup(this).apply { orientation=RadioGroup.VERTICAL }
        fun option(label:String)=RadioButton(this).apply { id=View.generateViewId();text=label;textSize=18f;minHeight=dp(64);setTextColor(Color.WHITE);destination.addView(this) }
        val webOption=option("ChatGPT nel WebView desktop")
        val nativeOption=option("App ChatGPT del telefono")
        destination.check(if(native) nativeOption.id else webOption.id)
        destination.setOnCheckedChangeListener { _,checked ->
            if(VoiceCycle.active) { destination.check(if(NativeChatSettings.enabled(this)) nativeOption.id else webOption.id);Toast.makeText(this,"Termina prima il ciclo attivo.",Toast.LENGTH_SHORT).show() }
            else { captureDrafts();NativeChatSettings.enable(this,checked==nativeOption.id);renderMenu(false) }
        }
        menu.addControl(destination)
        if(native) {
            text("Progetto confermato: "+NativeChatSettings.project(this))
            text(if(NativeChatSettings.id(this)==null) "Shortcut da selezionare" else "Shortcut salvato")
            button("Seleziona collegamento e nome progetto") { selectNativeShortcut() }
            text(PreparedChat.status(this))
            menu.toggle("Prepara la prossima chat in background",PreparedChat.enabled(this),
                "Prima accedi nel WebView con lo stesso account dell’app ChatGPT e verifica l’URL del progetto. La preparazione invia il messaggio prima di Voice configurato in Voce e saluto in una nuova chat, senza Voice o finestre sopra il telefono. Può essere rallentata o fermata da Android.") {
                if(!VoiceCycle.active) PreparedChat.enable(this,it)
                else Toast.makeText(this,"Termina prima il ciclo attivo.",Toast.LENGTH_SHORT).show()
                renderMenu(false)
            }
            button("Accesso WebView per preparazione") {
                if(!VoiceCycle.active) {
                    PreparedChat.resetForLogin(this);ChatGptPreloader.discard()
                    startActivity(Intent(this,WebChatActivity::class.java).putExtra(WebChatActivity.EXTRA_TEXT_ONLY,true))
                }
            }
            projectUrlInput=menu.field("URL progetto per preparazione",projectDraft ?: ChatGptProjectSettings.getUrl(this),
                "Deve essere lo stesso progetto selezionato nell’app nativa. Non inserire il link di una vecchia conversazione.")
            button("Salva progetto per preparazione") {saveChatGptProjectUrl();projectDraft=projectUrlInput.text.toString()}
            button("Aggiorna stato preparazione") {renderMenu(false)}
            button("Riprova preparazione") {if(!VoiceCycle.active) {PreparedChat.retry(this);renderMenu(false)}}
            button("Copia link della chat") {
                PreparedChat.savedLink(this)?.let {url ->
                    getSystemService(android.content.ClipboardManager::class.java)
                        .setPrimaryClip(android.content.ClipData.newPlainText("Chat preparata",url))
                    Toast.makeText(this,"Link privato copiato.",Toast.LENGTH_SHORT).show()
                } ?: Toast.makeText(this,"Nessuna chat pronta.",Toast.LENGTH_SHORT).show()
            }

            button("Prova collegamento senza Voice") {
                if(VoiceCycle.active) Toast.makeText(this,"Termina prima il ciclo attivo.",Toast.LENGTH_SHORT).show()
                else runCatching { NativeChatSettings.launch(this) }.onFailure { Toast.makeText(this,it.message ?: "Collegamento non disponibile.",Toast.LENGTH_LONG).show() }
            }
        } else {
            projectUrlInput=menu.field(getString(R.string.chatgpt_project_url_label),projectDraft ?: ChatGptProjectSettings.getUrl(this),getString(R.string.chatgpt_project_url_description))
            projectUrlInput.inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            button(getString(R.string.chatgpt_project_url_save)) { saveChatGptProjectUrl();projectDraft=projectUrlInput.text.toString() }
        }
        button("Apri accesso a ChatGPT") {
            if(VoiceCycle.active) Toast.makeText(this,"Termina prima il ciclo attivo.",Toast.LENGTH_SHORT).show()
            else if(NativeChatSettings.enabled(this)) runCatching { NativeChatSettings.launch(this) }.onFailure { Toast.makeText(this,it.message ?: "Collegamento non disponibile.",Toast.LENGTH_LONG).show() }
            else { ChatGptPreloader.discard();startActivity(Intent(this,WebChatActivity::class.java)) }
        }
        button("Imposta modello e livello in ChatGPT") { openChatSettings() }
    }
    private fun buildAudioPage() {
        menu.toggle(getString(R.string.waiting_audio_enabled),WaitingAudioSettings.enabled(this),help(MenuPage.AUDIO)) { WaitingAudioSettings.setEnabled(this,it) }
        waitingAudioName=text("");updateWaitingAudioName()
        button(getString(R.string.waiting_audio_choose)) { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE);type="audio/*" },waitingAudioRequest) }
        val volume=text(getString(R.string.waiting_audio_volume,WaitingAudioSettings.volume(this)))
        fun slider(maximum:Int,value:Int,description:String,change:(Int)->Unit) {
            menu.addControl(SeekBar(this).apply {
                max=maximum;progress=value;minimumHeight=dp(56);contentDescription=description
                setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(bar:SeekBar,n:Int,user:Boolean) { if(user) { change(n) } }
                    override fun onStartTrackingTouch(bar:SeekBar)=Unit
                    override fun onStopTrackingTouch(bar:SeekBar)=Unit
                })
            })
        }
        slider(100,WaitingAudioSettings.volume(this),"Volume audio di avvio") { n -> volume.text=getString(R.string.waiting_audio_volume,n);WaitingAudioSettings.setVolume(this,n);WaitingAudio.preview(this) }
        val minimum=text(getString(R.string.waiting_audio_minimum,WaitingAudioSettings.minimumSeconds(this)))
        slider(WaitingAudioMinimum.MAX_SECONDS,WaitingAudioSettings.minimumSeconds(this),"Tempo minimo audio di avvio") { n -> minimum.text=getString(R.string.waiting_audio_minimum,n);WaitingAudioSettings.setMinimumSeconds(this,n) }
    }
    private fun buildWebMaintenance() {
        button(getString(R.string.chatgpt_webview_clear_cache)) {
            Toast.makeText(this,if(ChatGptPreloader.clearResourceCache(this,contentRoot)) R.string.chatgpt_webview_cache_cleared else R.string.chatgpt_webview_cache_clear_failed,Toast.LENGTH_SHORT).show()
        }
        button(getString(R.string.chatgpt_webview_clear_data)) {
            android.app.AlertDialog.Builder(this).setTitle(R.string.chatgpt_webview_clear_data_title).setMessage(R.string.chatgpt_webview_clear_data_confirmation)
                .setNegativeButton(android.R.string.cancel,null).setPositiveButton(R.string.chatgpt_webview_clear_data_confirm) { _,_ ->
                    val accepted=ChatGptPreloader.clearChatGptData(this,contentRoot) { success -> Toast.makeText(this,if(success) R.string.chatgpt_webview_data_cleared else R.string.chatgpt_webview_data_clear_failed,Toast.LENGTH_LONG).show() }
                    if(!accepted) Toast.makeText(this,R.string.chatgpt_webview_data_clear_failed,Toast.LENGTH_LONG).show()
                }.showLarge()
        }
    }
    private fun saveOpeningMessage():Boolean {
        if(openingInput.text.isNullOrBlank()) {openingInput.error="Inserisci un messaggio prima di salvare.";return false}
        if(!OpeningMessageSettings.save(this,openingInput.text.toString())) {
            openingInput.error=if(VoiceCycle.active) "Termina prima il ciclo Voice attivo." else "Impossibile salvare il messaggio."
            return false
        }
        openingDraft=OpeningMessageSettings.get(this);openingInput.setText(openingDraft)
        openingInput.error=null
        Toast.makeText(this,"Messaggio prima di Voice salvato.",Toast.LENGTH_SHORT).show()
        return true
    }
    private fun configurationSummary()=permissionSummary()+
        "\n\nChatGPT: "+(if(NativeChatSettings.enabled(this)) "app nativa · "+NativeChatSettings.project(this) else "WebView desktop")+
        "\nMessaggio prima di Voice: "+OpeningMessageSettings.get(this)+
        "\nSaluto dopo Voice: "+VoiceGreetingSettings.get(this)+
        "\nAudio di avvio: "+(if(WaitingAudioSettings.enabled(this)) "attivo" else "disattivato")+" · "+WaitingAudioSettings.volume(this)+"% · minimo "+WaitingAudioSettings.minimumSeconds(this)+" s"+
        "\nCover: "+(if(CoverSettings.isOpaque(this)) "opaca" else "semitrasparente")+
        "\nCollegamento: "+(if(BridgeSettings.enabled(this)) "attivo" else "disattivato")+
        "\nAzioni telefono: "+(if(BridgeSettings.actionsEnabled(this)) "consentite" else "disattivate")+
        "\nAppunti dal plugin: "+(if(PhoneNotes.enabled(this)) "consentiti" else "disattivati")
    private fun advanceGuide() {
        if(navigation.current==MenuPage.GREETING) {
            if(greetingInput.text.isNullOrBlank()) {greetingInput.error="Inserisci una frase valida o ripristina quella predefinita.";return}
            if(!saveOpeningMessage()) return
            if(!VoiceGreetingSettings.save(this,greetingInput.text.toString())) { greetingInput.error="Inserisci una frase valida o ripristina quella predefinita.";return }
            greetingDraft=VoiceGreetingSettings.get(this)
        }
        if(navigation.current==MenuPage.CHATGPT && !NativeChatSettings.enabled(this)) {
            if(!ChatGptProjectSettings.isValidProjectUrl(projectUrlInput.text.toString().trim())) { projectUrlInput.error=getString(R.string.chatgpt_project_url_invalid);return }
            if(projectUrlInput.text.toString().trim()!=ChatGptProjectSettings.getUrl(this)) saveChatGptProjectUrl()
        }
        captureDrafts();navigation.next();renderMenu(false)
    }
    private fun menuBack():Boolean {
        if(chatSettingsLayer!=null) { closeChatSettings();return true }
        captureDrafts()
        if(!navigation.back()) return false
        renderMenu(false);return true
    }
    private fun updateMenuBack() {
        if(Build.VERSION.SDK_INT<33) return
        val needed=chatSettingsLayer!=null || navigation.canBack
        if(needed && settingsBackCallback==null) {
            val callback=OnBackInvokedCallback { menuBack() };settingsBackCallback=callback
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT,callback)
        } else if(!needed) { settingsBackCallback?.let { onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it) };settingsBackCallback=null }
    }
    override fun onSaveInstanceState(outState:Bundle) {
        captureDrafts();outState.putStringArrayList("menu_path",ArrayList(navigation.savedPath()));outState.putInt("menu_step",navigation.step)
        outState.putString("opening_draft",openingDraft);outState.putString("greeting_draft",greetingDraft);outState.putString("project_draft",projectDraft)
        super.onSaveInstanceState(outState)
    }

    private fun appVersionName(): String = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(
                packageName,
                PackageManager.PackageInfoFlags.of(0)
            ).versionName
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0).versionName
        }
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: ""

    private fun selectNativeShortcut() {
        if (VoiceCycle.active) {
            Toast.makeText(this, "Termina prima il ciclo attivo.", Toast.LENGTH_LONG).show()
            return
        }
        val shortcuts = try { NativeChatSettings.shortcuts(this) } catch (e: RuntimeException) {
            android.app.AlertDialog.Builder(this).setMessage(e.message ?: "Shortcut non accessibili.")
                .setPositiveButton("OK", null).showLarge()
            return
        }
        if (shortcuts.isEmpty()) {
            android.app.AlertDialog.Builder(this)
                .setMessage("ChatGPT non espone collegamenti accessibili a Jarvis. Crea il collegamento al progetto nell'app ChatGPT e verifica che Jarvis sia l'assistente selezionato. Nessun altro collegamento verrà aperto al suo posto.")
                .setPositiveButton("OK", null).showLarge()
            return
        }
        android.app.AlertDialog.Builder(this).setTitle("Collegamento al progetto ChatGPT")
            .setAdapter(largeMenuChoices(this,shortcuts.map { it.shortLabel?.toString().orEmpty() })) { _, index ->
                val selected = shortcuts[index]
                val projectName = EditText(this).apply {
                    setSingleLine(true)
                    setText(NativeChatSettings.project(this@MainActivity))
                    hint = "Nome esatto del progetto nella pagina ChatGPT"
                }
                val dialog = android.app.AlertDialog.Builder(this)
                    .setTitle("Conferma nome progetto")
                    .setMessage("Jarvis avvierà Voice soltanto quando questo nome è visibile nella pagina ChatGPT. Il collegamento selezionato rimane nell'app nativa.")
                    .setView(projectName).setNegativeButton("Annulla", null).setPositiveButton("Salva", null).create()
                dialog.setOnShowListener {
                    dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        if (projectName.text.toString().isBlank()) projectName.error = "Inserisci il nome del progetto"
                        else {
                            NativeChatSettings.select(this, selected, projectName.text.toString())
                            Toast.makeText(this, "Collegamento salvato. Seleziona App ChatGPT per usarlo alla wake.", Toast.LENGTH_LONG).show()
                            dialog.dismiss()
                        }
                    }
                }
                dialog.showLarge()
            }.setNegativeButton("Annulla", null).showLarge()
    }

    private fun requestStart() {
        if (WakeService.bridgeStatus().optBoolean("serviceRunning") && WakeService.resumeExisting()) {
            prepareChatGpt()
            return
        }
        val missing = mutableListOf<String>()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            missing += Manifest.permission.RECORD_AUDIO
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            missing += Manifest.permission.POST_NOTIFICATIONS
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 1)
        else startListening()
    }
    private fun startListening() {
        if (Build.VERSION.SDK_INT >= 33 && !getSystemService(android.app.NotificationManager::class.java).areNotificationsEnabled()) {
            State.set(this, "Abilita le notifiche: Android le richiede per il servizio microfono continuo."); return
        }
        try {
            prepareChatGpt()
            startForegroundService(Intent(this, WakeService::class.java))
        }
        catch (e: RuntimeException) { State.set(this, "Avvio rifiutato: ${e.javaClass.simpleName}") }
    }

    private fun prepareChatGpt() {
        if (NativeChatSettings.enabled(this)) { runCatching {PreparedChat.maintain(this)};return }
        if (::contentRoot.isInitialized) ChatGptPreloader.prepare(this, contentRoot)
    }

    private fun saveChatGptProjectUrl() {
        val url = projectUrlInput.text?.toString().orEmpty().trim()
        if (!ChatGptProjectSettings.isValidProjectUrl(url)) {
            projectUrlInput.error = getString(R.string.chatgpt_project_url_invalid)
            return
        }
        val changed = url != ChatGptProjectSettings.getUrl(this)
        if (!ChatGptProjectSettings.save(this, url)) return
        projectUrlInput.error = null
        if (changed) {
            ChatGptPreloader.discard()
            prepareChatGpt()
        }
        Toast.makeText(
            this,
            if (changed) getString(R.string.chatgpt_project_url_saved_changed)
            else getString(R.string.chatgpt_project_url_saved),
            Toast.LENGTH_LONG
        ).show()
    }

    private fun openChatSettings() {
        WaitingAudio.stopPreview()
        if (NativeChatSettings.enabled(this)) {
            if (!VoiceCycle.active) runCatching { NativeChatSettings.launch(this) }.onFailure {
                Toast.makeText(this, it.message ?: "Collegamento non disponibile.", Toast.LENGTH_LONG).show()
            }
            return
        }
        if (chatSettingsLayer != null) return
        if (VoiceCycle.active) {
            Toast.makeText(this, "Termina prima il ciclo Voice per modificare la chat.", Toast.LENGTH_SHORT).show()
            return
        }
        ChatGptPreloader.prepare(this, contentRoot)
        val layer = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(2, 8, 15))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
        }
        if (!ChatGptPreloader.showForConfiguration(layer)) {
            Toast.makeText(this, "La pagina ChatGPT non è pronta. Riprova tra poco.", Toast.LENGTH_SHORT).show()
            return
        }
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(12), dp(4))
            setBackgroundColor(Color.rgb(13, 24, 36))
            elevation = dp(6).toFloat()
        }
        toolbar.addView(Button(this).apply {
            text = "‹ Jarvis"
            contentDescription = "Torna alla schermata Jarvis mantenendo aperta la sessione ChatGPT"
            setOnClickListener { closeChatSettings() }
        }, LinearLayout.LayoutParams(-2, -2))
        toolbar.addView(TextView(this).apply {
            text = "Modello e livello ChatGPT"
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            contentDescription = "Impostazioni ChatGPT"
        }, LinearLayout.LayoutParams(0, -1, 1f).apply { leftMargin = dp(12) })
        layer.addView(toolbar, FrameLayout.LayoutParams(-1, dp(64), Gravity.TOP))
        homeScreen.visibility = View.GONE
        contentRoot.addView(layer, FrameLayout.LayoutParams(-1, -1))
        chatSettingsLayer = layer
        updateMenuBack()
        State.setAttempt(this, "Impostazioni aperte nel WebView usato dal ciclo wake; torna a Jarvis senza chiudere la pagina.")
    }

    private fun closeChatSettings() {
        val layer = chatSettingsLayer ?: return
        if (!ChatGptPreloader.hideConfiguration(contentRoot)) {
            Toast.makeText(this, "Non riesco a ripristinare la pagina ChatGPT; lascio la schermata aperta.", Toast.LENGTH_SHORT).show()
            return
        }
        contentRoot.removeView(layer)
        chatSettingsLayer = null
        updateMenuBack()
        homeScreen.visibility = View.VISIBLE
        State.setAttempt(this, "Pagina ChatGPT mantenuta nel preloader; il prossimo ciclo wake riutilizza questo WebView.")
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    @SuppressLint("GestureBackNavigation")
    override fun onBackPressed() {
        if (!menuBack()) super.onBackPressed()
    }

    private fun startVoiceCycle() {
        actionFeedback=true
        DiagnosticLog.record(this,DiagnosticLog.Event.MANUAL_ACTION_REQUESTED,monitorStatus="voice")
        val blocked=when {
            VoiceCycle.active -> "voice_active" to "Un ciclo Voice è già attivo o sta terminando. Attendi il rilascio del microfono prima di avviarne un altro."
            !VoiceAccessibilityService.isEnabled(this) -> "access_disabled" to "Automazione Voice non abilitata. Apri Configurazione → Permessi e assistente → Abilita automazione Voice."
            NativeChatSettings.enabled(this) && VoiceAccessibilityService.bridgeService()==null -> "access_pending" to "Il servizio Accessibilità Jarvis non è collegato. Controllalo in Configurazione → Permessi e assistente, poi riprova."
            NativeChatSettings.enabled(this) && NativeChatSettings.id(this).isNullOrBlank() -> "shortcut_missing" to "Seleziona il collegamento al progetto in Configurazione → ChatGPT e progetto."
            else -> null
        }
        if(blocked!=null) { reportManualBlock(blocked.first,blocked.second);return }
        if(NativeChatSettings.enabled(this)) {
            val accessible=runCatching { NativeChatSettings.shortcuts(this).any { it.id==NativeChatSettings.id(this) } }.getOrDefault(false)
            if(!accessible) { reportManualBlock("shortcut_unavailable","Il collegamento al progetto non è accessibile. Verifica assistente, shortcut e nome progetto in Configurazione, senza cancellare i dati.");return }
        }
        VoiceCycle.start(this)
        refreshActionFeedback()
    }

    private fun reportManualBlock(code:String,message:String) {
        State.setAttempt(this,message)
        DiagnosticLog.record(this,DiagnosticLog.Event.MANUAL_ACTION_BLOCKED,monitorStatus=code)
        menu.feedback("Avvio Voice da verificare",message)
        Toast.makeText(this,message,Toast.LENGTH_LONG).show()
    }

    private fun refreshActionFeedback() {
        val snapshot=WakeService.bridgeStatus()
        val capture=when(snapshot.optString("capture")) {
            "active_verified" -> "cattura verificata"
            "unverified" -> "cattura da verificare"
            else -> "non in cattura"
        }
        menu.feedback("Stato Jarvis", "Servizio ascolto: "+(if(snapshot.optBoolean("serviceRunning")) "avviato" else "non avviato")+
            "\nMicrofono wake: "+capture+"\n\n"+State.get(this)+"\n\nUltimo ciclo: "+State.getAttempt(this))
    }

    private fun requestAssistantRole() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
            (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)) {
            val missing = mutableListOf<String>()
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
                missing += Manifest.permission.RECORD_AUDIO
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                missing += Manifest.permission.POST_NOTIFICATIONS
            requestPermissions(missing.toTypedArray(), assistantPermissionRequest)
            return
        }
        val roles = getSystemService(RoleManager::class.java)
        if (!roles.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
            State.set(this, "Questo dispositivo non espone la selezione di un assistente personalizzato.")
            return
        }
        JarvisRecognitionService.rememberSystemRecognizer(this)
        startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT), assistantRoleRequest)
    }
    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, permissions, results)
        if (code == 6) { renderMenu();return }
        if (code == assistantPermissionRequest) {
            val accepted = results.isNotEmpty() && results.all { it == PackageManager.PERMISSION_GRANTED }
            if (accepted) requestAssistantRole()
            else State.set(this, "Microfono e notifiche servono prima di configurare l’assistente Jarvis.")
            return
        }
        if (code == 1 && results.isNotEmpty() && results.all { it == PackageManager.PERMISSION_GRANTED }) startListening()
        else State.set(this, "Microfono e notifiche richiesti per questa prova.")
    }
    @Deprecated("Android RoleManager returns its selection through an activity result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == waitingAudioRequest) {
            if (resultCode == RESULT_OK) data?.data?.let { uri ->
                Toast.makeText(this, R.string.waiting_audio_importing, Toast.LENGTH_SHORT).show()
                WaitingAudioSettings.import(this, uri) { success ->
                    if (!isDestroyed && !isFinishing) {
                        updateWaitingAudioName()
                        Toast.makeText(this, if (success) R.string.waiting_audio_saved
                            else R.string.waiting_audio_failed, Toast.LENGTH_LONG).show()
                    }
                }
            }
            return
        }
        if (requestCode == diagnosticsExportRequest) {
            if (resultCode == RESULT_OK) data?.data?.let { writeDiagnostics(it) }
            return
        }
        if (requestCode != assistantRoleRequest) return
        val roles = getSystemService(RoleManager::class.java)
        when {
            roles.isRoleHeld(RoleManager.ROLE_ASSISTANT) -> {
                State.set(this, "Jarvis è l’assistente predefinito. Avvio l’ascolto wake word.")
                startListening()
            }
            else -> State.set(this, "Android non ha assegnato a Jarvis il ruolo assistente. Riprova dalle impostazioni Assistente digitale.")
        }
    }
    private fun exportDiagnostics() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
            putExtra(Intent.EXTRA_TITLE, "jarvis-diagnostics.zip")
        }
        startActivityForResult(intent, diagnosticsExportRequest)
    }

    private fun writeDiagnostics(uri: Uri) {
        DiagnosticLog.afterPendingWrites {
            val result = runCatching {
                val files = java.io.File(filesDir, "diagnostics").listFiles()
                    ?.filter { it.isFile && (it.name.matches(Regex("events-[0-9]+\\.log")) || it.name == NativeControlSnapshot.FILE_NAME || it.name == NativeRuntimeSnapshot.FILE_NAME || it.name == NativeButtonSnapshot.FILE_NAME) }
                    ?.sortedBy { it.name }.orEmpty()
                contentResolver.openOutputStream(uri, "w")?.use { output ->
                    java.util.zip.ZipOutputStream(output).use { zip ->
                        for (file in files) {
                            zip.putNextEntry(java.util.zip.ZipEntry(file.name))
                            file.inputStream().use { it.copyTo(zip) }
                            zip.closeEntry()
                        }
                    }
                } ?: error("Destinazione non disponibile")
            }
            runOnUiThread {
                State.set(this, if (result.isSuccess) "Diagnostica esportata." else
                    "Esportazione diagnostica non riuscita: ${result.exceptionOrNull()?.javaClass?.simpleName}")
            }
        }
    }

    private fun updateCoverButton() {
        if (!::coverButton.isInitialized) return
        coverButton.text = if (CoverSettings.isOpaque(this)) {
            "Copertura: opaca\nPassa a semitrasparente"
        } else {
            "Copertura: semitrasparente\nPassa a opaca"
        }
    }

    private fun updateWaitingAudioName() {
        waitingAudioName.text = if (WaitingAudioSettings.file(this).isFile)
            getString(R.string.waiting_audio_file, WaitingAudioSettings.name(this)
                ?: getString(R.string.waiting_audio_selected))
        else getString(R.string.waiting_audio_none)
    }

    override fun onResume() {
        super.onResume()
        if (::menu.isInitialized && chatSettingsLayer==null) renderMenu()
        runCatching { BridgeLink.owner(this,"main",true) }
        updateCoverButton()
        if (::contentRoot.isInitialized && !VoiceCycle.active) prepareChatGpt()
        handler.post(update)
        val watchTicket=intent.getStringExtra(it.jarviswake.watch.WatchProtocol.EXTRA)
        intent.removeExtra(it.jarviswake.watch.WatchProtocol.EXTRA)
        if(watchTicket!=null && WatchTickets.transition(this,watchTicket,"ready","consumed")) {
            val previous=VoiceCycle.id
            startVoiceCycle()
            WatchProbeReturn.bindWatch(this,VoiceCycle.id?.takeIf {previous==null})
        }
    }
    override fun onPause() {
        runCatching { BridgeLink.owner(this,"main",false) }
        WaitingAudio.stopPreview()
        handler.removeCallbacks(update)
        if (VoiceCycle.active && chatSettingsLayer != null) closeChatSettings()
        super.onPause()
    }
    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= 33) settingsBackCallback?.let {
            onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it)
        }
        if (!isChangingConfigurations) ChatGptPreloader.discard()
        if (instance.get() === this) instance.clear()
        super.onDestroy()
    }
}
