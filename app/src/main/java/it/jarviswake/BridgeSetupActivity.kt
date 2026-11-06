package it.jarviswake

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.WindowManager
import android.widget.*
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher

class BridgeSetupActivity:Activity() {
    private val handler=Handler(Looper.getMainLooper())
    private lateinit var state:TextView
    private lateinit var enabled:Switch
    private lateinit var menu:SplitMenuScreen
    private var section="overview"
    private var entry="overview"
    private var backCallback:OnBackInvokedCallback?=null
    private val update=object:Runnable { override fun run() { state.text=BridgeLink.status;handler.postDelayed(this,1000) } }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        window.setDecorFitsSystemWindows(false)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        entry=intent.getStringExtra("section")?.takeIf { it in listOf("pair","actions","notes","contacts") } ?: "overview"
        section=savedInstanceState?.getString("section")?.takeIf { it in listOf("overview","pair","actions","notes","contacts") } ?: entry
        render()
        if(Build.VERSION.SDK_INT>=33) {
            val cb=OnBackInvokedCallback { back() };backCallback=cb
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT,cb)
        }
    }
    private fun render() {
        val title=when(section) { "contacts"->"Rubrica verso workspace";"pair"->"Abbinamento e stato";"actions"->"Maps, chiamate e WhatsApp";"notes"->"Appunti e plugin";else->"Collegamento ChatGPT" }
        val help=when(section) {
            "pair"->"Apri la pagina privata di JarvisWake Link, crea un codice e incollalo soltanto qui. Attiva il collegamento: un servizio dedicato con notifica lo mantiene anche quando esci dalla pagina, senza avviare l’ascolto wake. L’abbinamento resta dopo aggiornamenti firmati e normali riavvii. Cancellarlo disconnette il telefono ma non elimina gli appunti locali. Il codice contiene credenziali: non condividerlo in chat."
            "actions"->"Attiva le azioni e autorizza rubrica, chiamate e stato telefonico dal pulsante. In ChatGPT usa JarvisWake Link e chiedi una destinazione, una chiamata o un testo WhatsApp a un contatto preciso. Le ambiguità richiedono una scelta. Il comando inviato non prova una chiamata connessa, un messaggio consegnato o la navigazione su Android Auto. Per parlare con Maps aperto abilita le conversazioni in background in ChatGPT Live."
            "contacts"->"Consenti di trasferire nomi, tutti i numeri e le email accessibili dalla rubrica Android alla copia privata nel workspace tramite lo stesso plugin. Solo telefono → workspace: nessuna scrittura o cancellazione Android. Il comando aggiorna rubrica rinnova la copia completa; una richiesta di importare un numero aggiorna solo quel numero. Il trasferimento passa dal servizio privato e dagli strumenti della conversazione; non è cifratura end-to-end. Disattivare impedisce nuove letture, non ritira copie già ricevute. Nessun invio periodico automatico."
            "notes"->"Il consenso permette al plugin di leggere, creare e aggiornare gli appunti sul telefono sbloccato. Il contenuto richiesto passa nella conversazione. Gli appunti hanno un archivio cifrato separato dall’abbinamento, escluso dalla diagnostica; disinstallazione o cancellazione dati dell’app li elimina. Apri appunti locali funziona anche senza rete o consenso remoto. Google Calendar resta al suo plugin dedicato."
            else->"Scegli una categoria: abbinamento, azioni del telefono oppure appunti. Ogni consenso è distinto. Usa sempre lo stesso JarvisWake Link: non serve un nuovo plugin dopo un aggiornamento. La guida del plugin è consultabile anche senza un telefono online."
        }
        menu=SplitMenuScreen(this,title,help) { back() }
        state=menu.text(BridgeLink.status,14f)
        when(section) {
            "pair" -> {
                menu.text(if(BridgeSettings.configured(this)) "Abbinamento presente" else "Abbinamento da configurare")
                menu.button("Apri pagina privata di collegamento") {
                    runCatching { startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(BridgeProtocol.ORIGIN))) }.onFailure { state.setText(R.string.bridge_browser_unavailable) }
                }
                menu.button("Incolla codice di collegamento") { pasteCode() }
                enabled=menu.toggle(getString(R.string.bridge_enabled),BridgeSettings.enabled(this),help) { value ->
                    if(value && !BridgeSettings.configured(this)) { enabled.isChecked=false;state.setText(R.string.bridge_code_required) }
                    else { BridgeSettings.enable(this,value);BridgeLink.refresh(this);BridgeLinkService.sync(this) }
                }
                menu.button("Cancella collegamento dal telefono") {
                    AlertDialog.Builder(this).setTitle("Cancellare il collegamento?")
                        .setMessage("Il codice di collegamento verrà rimosso. Gli appunti locali restano conservati.")
                        .setNegativeButton("Annulla",null).setPositiveButton("Cancella") { _,_ -> BridgeSettings.clear(this);enabled.isChecked=false;BridgeLink.refresh(this);BridgeLinkService.sync(this);render() }.showLarge()
                }
            }
            "actions" -> {
                menu.toggle(getString(R.string.bridge_actions_enabled),BridgeSettings.actionsEnabled(this),help) { BridgeSettings.enableActions(this,it) }
                menu.button("Autorizza rubrica e chiamate") { requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS,Manifest.permission.CALL_PHONE,Manifest.permission.READ_PHONE_STATE),81) }
                menu.button("Collega WhatsApp con QR", "Apre il collegamento del tuo WhatsApp come dispositivo aggiuntivo di JarvisWake. Non simula tasti o scrittura nell’app WhatsApp.") { startActivity(Intent(this,WhatsAppQrActivity::class.java)) }
                menu.button("Guida alle azioni e ai plugin") { startActivity(Intent(this,GuideActivity::class.java).putExtra("topic","azioni")) }
            }
            "contacts" -> {
                menu.toggle("Consenti copia rubrica nel workspace",PhoneContactExport.enabled(this),help) { PhoneContactExport.enable(this,it) }
                menu.button("Autorizza sola lettura rubrica") { requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS),82) }
                menu.button("Guida alla copia locale") { startActivity(Intent(this,GuideActivity::class.java).putExtra("topic","rubrica_locale")) }
            }
            "notes" -> {
                menu.toggle("Consenti appunti da ChatGPT",PhoneNotes.enabled(this),help) { PhoneNotes.enable(this,it) }
                menu.button("Apri appunti locali") { startActivity(Intent(this,LocalNotesActivity::class.java)) }
                menu.button("Guida alla memoria e alla rilettura") { startActivity(Intent(this,GuideActivity::class.java).putExtra("topic","appunti")) }
            }
            else -> {
                menu.button("Abbinamento e stato") { section="pair";render() }
                menu.button("Maps, chiamate e WhatsApp") { section="actions";render() }
                menu.button("Rubrica verso workspace") { section="contacts";render() }
                menu.button("Appunti e accesso dal plugin") { section="notes";render() }
            }
        }
        setContentView(menu);menu.requestFocus()
    }
    private fun pasteCode() {
        val field=EditText(this).apply { inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint="JW1.…";importantForAutofill=android.view.View.IMPORTANT_FOR_AUTOFILL_NO;isSaveEnabled=false }
        val dialog=AlertDialog.Builder(this).setTitle("Codice dalla pagina JarvisWake Link")
            .setMessage("Incolla il codice privato. Non inviarlo in chat.").setView(field)
            .setNegativeButton("Annulla",null).setPositiveButton("Salva",null).create()
        dialog.setOnShowListener {
            dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                runCatching { BridgeSettings.saveCode(this,field.text.toString()) }.onSuccess {
                    field.text.clear();enabled.isChecked=false;BridgeLink.refresh(this);state.setText(R.string.bridge_code_saved);dialog.dismiss();render()
                }.onFailure { field.error="Codice non valido o impossibile salvarlo. Nessun dettaglio privato registrato." }
            }
        }
        dialog.showLarge()
    }
    private fun back() { if(section==entry || section=="overview") finish() else { section="overview";render() } }
    @Deprecated("Back navigation is also registered for Android 13+")
    @SuppressLint("GestureBackNavigation") // API31 fallback; API33+ uses OnBackInvokedCallback above.
    override fun onBackPressed() { back() }
    override fun onSaveInstanceState(outState:Bundle) { outState.putString("section",section);super.onSaveInstanceState(outState) }
    override fun onResume() { super.onResume();BridgeLink.owner(this,"setup",true);BridgeLinkService.sync(this);handler.post(update) }
    override fun onPause() { handler.removeCallbacks(update);BridgeLink.owner(this,"setup",false);super.onPause() }
    override fun onDestroy() { if(Build.VERSION.SDK_INT>=33) backCallback?.let { onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it) };super.onDestroy() }
}
