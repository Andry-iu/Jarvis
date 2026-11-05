package it.jarviswake

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.Toast

internal class GoogleVoiceSettingsActivity : Activity() {
    private lateinit var menu: SplitMenuScreen
    private lateinit var stt: EditText
    private lateinit var tts: EditText
    private lateinit var minutes: EditText
    private lateinit var project: EditText
    private lateinit var conversation: EditText
    private lateinit var language: EditText
    private lateinit var voice: EditText
    private lateinit var silence: EditText
    private lateinit var maximum: EditText
    private lateinit var threshold: EditText
    private var automatic = false
    private var saved: GoogleVoiceConfig? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setDecorFitsSystemWindows(false)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        saved = runCatching { GoogleVoiceSettings.read(this) }.getOrNull()
        automatic = saved?.authMode == "backend"
        menu = SplitMenuScreen(this, "Voce Google · ChatGPT testuale", "Google ha costi propri. Nessuna prova di sintesi o trascrizione automatica. Conserva l’account ChatGPT nella WebView; non apre Voice.") { finish() }
        menu.text("Modalità predefinita: ChatGPT Voice. L’interruttore si applica solo alla prossima sessione, dopo il rilascio audio. Le configurazioni restano separate.")
        menu.toggle("Google STT/TTS e ChatGPT testuale", GoogleVoiceSettings.enabled(this), "Cambio reversibile; non interrompe la sessione in corso.") { enabled ->
            GoogleVoiceSettings.enable(this, enabled)
            PreparedChat.stop(this, "voice_mode_changed")
            menu.feedback("Modalità salvata", if (VoiceCycle.active || GoogleVoiceActivity.audioBusy()) "Si applicherà dopo chiusura e rilascio audio." else "Disponibile al prossimo avvio.")
        }
        menu.toggle("Rinnovo automatico dal backend", automatic, "Salva a sessione chiusa. La chiave JSON resta sul backend; i token manuali vengono conservati.") { automatic = it }
        menu.button("Configura chiave Google nel backend privato") {
            val pairing = runCatching { BridgeSettings.read(this) }.getOrNull()
            if (pairing == null || !BridgeProtocol.validOrigin(pairing.origin)) feedback("Configura prima il collegamento JarvisWake Link esistente.")
            else runCatching { startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(pairing.origin + "/google"))) }
                .onFailure { feedback("Browser non disponibile. Apri la pagina /google del collegamento privato già abbinato.") }
        }
        runCatching { BridgeSettings.read(this) }.getOrNull()?.let { menu.text("ID del telefono abbinato: ${it.deviceId}") }
        menu.text("Rinnovo automatico: nella pagina privata carica una volta la chiave JSON per il telefono già abbinato. Il backend la cifra e restituisce soltanto token brevi al telefono. Usa qui lo stesso ID progetto del JSON. Nessuna chiave privata nell’APK o nella conversazione. Il file originale resta dove lo hai scaricato: custodiscilo privatamente. Con l’opzione disattivata rimane l’importazione manuale.")
        menu.text("Autenticazione manuale: OAuth 2.0 utente, scope cloud-platform. Importa token a breve durata ottenuti nel tuo ambiente Google fidato; nessun JSON di service account, refresh token o API key. Nessun segreto va inviato in chat. Nella sola modalità manuale i token non si rinnovano automaticamente. Progetto/API e relativi permessi devono essere già disponibili; l’app non attiva servizi o fatturazione.")
        stt = secret("Token manuale accesso Speech-to-Text")
        tts = secret("Token manuale accesso Text-to-Speech")
        minutes = field("Validità residua dei nuovi token in minuti (1–60)", "30")
        menu.text(if (saved == null) "Nessuna credenziale salvata." else "Credenziali protette presenti. Lascia entrambi i token vuoti per conservarli con la scadenza originale. Per rinnovarli sostituiscili entrambi. Modalità salvata: ${if(saved!!.authMode == "backend") "rinnovo backend" else "token manuali, scadenza ${java.util.Date(saved!!.expiresAt)}"}. La scadenza effettiva può essere precedente.")
        project = field("Progetto Google per quota", saved?.quotaProject ?: "")
        conversation = field("Link della conversazione esistente nel progetto ChatGPT", saved?.conversation ?: "")
        menu.button("Usa conversazione WebView o link già preparato") {
            val observed=ChatGptPreloader.currentUrl()?.takeIf {GoogleConversation.valid(it)}
                ?: PreparedChat.savedLink(this)?.takeIf {GoogleConversation.valid(it)}
            if(observed==null) feedback("Nessun link privato di conversazione nel progetto osservato. Apri e verifica la chat nell’accesso WebView esistente, oppure incolla qui il suo link privato.")
            else { conversation.setText(observed);feedback("Link osservato inserito nella configurazione; verifica account, spazio e progetto, poi salva.") }
        }
        menu.text("Usa il link privato /g/g-p-…/c/UUID della conversazione corrente, nello stesso progetto. Verifica nella WebView account e spazio già configurati. Nessuna nuova chat o selezione dell’ultima conversazione automatica; una bozza presente blocca l’invio.")
        language = field("Lingua", saved?.language ?: "it-IT")
        voice = field("Voce", saved?.voice ?: "it-IT-Chirp3-HD-Enceladus")
        silence = field("Pausa di fine frase, ms (400–3000)", (saved?.silenceMs ?: 1200).toString())
        maximum = field("Durata massima frase, ms (5000–50000)", (saved?.maximumMs ?: 50000).toString())
        threshold = field("Soglia locale RMS PCM (100–10000)", (saved?.threshold ?: 600).toString())
        menu.button("Salva configurazione Google") {
            if (VoiceCycle.active || GoogleVoiceActivity.audioBusy()) { feedback("Termina prima la sessione e attendi il rilascio audio."); return@button }
            runCatching {
                val a=stt.text.toString().trim(); val b=tts.text.toString().trim()
                require(a.isEmpty()==b.isEmpty())
                if(automatic) require(BridgeSettings.enabled(this) && BridgeSettings.configured(this))
                val old=saved
                val expiry=if (a.isEmpty()) old?.expiresAt ?: 0 else System.currentTimeMillis()+minutes.text.toString().toLong().also { require(it in 1..60) }*60000
                val c=GoogleVoiceConfig(a.ifEmpty { old?.sttToken ?: "" }, b.ifEmpty { old?.ttsToken ?: "" }, expiry,
                    project.text.toString().trim(), conversation.text.toString().trim(), language.text.toString().trim(), voice.text.toString().trim(),
                    silence.text.toString().toInt(), maximum.text.toString().toInt(), threshold.text.toString().toInt(), if(automatic) "backend" else "manual")
                GoogleVoiceSettings.save(this,c); saved=c; stt.text.clear(); tts.text.clear()
            }.onSuccess { feedback("Configurazione protetta salvata. Nessuna chiamata Google eseguita.") }
                .onFailure { feedback("Dati non validi o archivio non disponibile. Verifica token, scadenza, progetto, link e intervalli; nulla viene inviato.") }
        }
        menu.button("Verifica voce salvata · solo catalogo Google") {
            val c=saved
            if(c==null || VoiceCycle.active) { feedback("Salva prima la configurazione a ciclo chiuso."); return@button }
            feedback("Consultazione esplicita catalogo; nessuna sintesi o trascrizione.")
            Thread({
                val broker = if(c.authMode == "backend") runCatching { GoogleTokenBroker(requireNotNull(BridgeSettings.read(this))) }.getOrNull() else null
                val present=runCatching { GoogleSpeechClient(if(c.authMode == "backend") requireNotNull(broker).resolve(c) else c).voiceAvailable() }
                broker?.cancel()
                runOnUiThread { if(!isFinishing && !isDestroyed) feedback(when {
                    present.isFailure -> "Catalogo non disponibile: verifica credenziali, permessi, quota e rete. Disponibilità voce non confermata."
                    present.getOrDefault(false) -> "Voce presente nel catalogo Google. Sintesi/riproduzione ancora da collaudare."
                    else -> "Voce non presente nel catalogo restituito. Scegli un nome compatibile."
                }) }
            },"Jarvis-google-voice-catalogue").start()
        }
        menu.button("Copia istruzioni iniziali per ChatGPT") {
            getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Istruzioni voce JarvisWake", INSTRUCTIONS))
            Toast.makeText(this,"Istruzioni copiate; incollale nella conversazione/progetto associato.",Toast.LENGTH_LONG).show()
        }
        menu.button("Elimina credenziali Google") {
            if(VoiceCycle.active || GoogleVoiceActivity.audioBusy()) feedback("Termina prima la sessione.")
            else { GoogleVoiceSettings.clear(this); saved=null; stt.text.clear(); tts.text.clear(); feedback("Credenziali locali Google eliminate. La chiave sul backend va eliminata dalla pagina privata; modalità e altre configurazioni conservate.") }
        }
        menu.text("Durante la sintesi microfono e wake sono sospesi: interruzione parlando non attiva. Doppio tocco/basso/Indietro chiudono e fermano l’audio locale; sinistro nasconde soltanto la cover, mantenendo stessa chat e sessione/audio. I lavori desktop già avviati proseguono. Testi in basso a destra mostra dettato definitivo e risposta plugin su tutta la cover, piccoli e semitrasparenti; scorrimento automatico, due dita per la cronologia. Solo RAM della sessione, cancellati alla chiusura. Se il modello omette il comando, attesa visibile e avviso dopo90s; puoi ancora attendere lavori lunghi in modalità manuale fino alla scadenza delle credenziali o annullare l’attesa. In modalità backend il token viene rinnovato prima della risposta. Nessuna lettura indiscriminata della chat.")
        menu.text(GoogleVoiceUsage.summary(this))
        setContentView(menu)
    }
    private fun feedback(text:String) = menu.feedback("Voce Google",text)
    private fun field(label:String,value:String) = menu.field(label,value,"Configurazione locale protetta; salva a sessione chiusa.").apply { isSaveEnabled=false; importantForAutofill=View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS }
    private fun secret(label:String) = field(label,"").apply {
        inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        setPrivateImeOptions("nm"); isSaveEnabled=false
    }
    override fun onPause() { if(::stt.isInitialized) { stt.text.clear(); tts.text.clear() }; super.onPause() }
    companion object {
        const val INSTRUCTIONS = "Quando un messaggio contiene metadati JarvisWake session_id e turn_id, lavora esclusivamente nella conversazione testuale corrente. Non avviare ChatGPT Voice né usare API OpenAI sostitutive. Esegui i lavori richiesti con i plugin esistenti. Per la risposta destinata alla voce chiama una sola volta jarviswake_speak sul telefono abbinato con session_id e turn_id del messaggio e un request_id UUIDv4 nuovo, conservato per eventuali verifiche dello stesso comando. Testo semplice, massimo4500 byte UTF-8, nessun segreto. Accettato/in coda non significa riprodotto: leggi solo l’esito correlato con jarviswake_status_result se necessario, senza polling continuo degli input o richieste in loop. Non ripetere una risposta incerta né inviarla a sessioni nuove. Se il comando manca o fallisce, spiega per iscritto l’esito senza inventare parlato. Dialogo testuale e lavori di sviluppo sono categorie diverse; token del modello non esposti dal bridge. Queste istruzioni orientano il modello e non garantiscono una chiamata software."
    }
}
