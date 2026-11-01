package it.jarviswake

import android.app.Activity
import android.app.AlertDialog
import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.WindowManager
import android.widget.*

class BridgeSetupActivity:Activity() {
    private val handler=Handler(Looper.getMainLooper())
    private lateinit var state:TextView
    private lateinit var enabled:Switch
    private val update=object:Runnable { override fun run() { state.text=BridgeLink.status;handler.postDelayed(this,1000) } }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val layout=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(32,64,32,32);setBackgroundColor(Color.rgb(13,24,36)) }
        fun text(value:String)=TextView(this).apply { text=value;setTextColor(Color.WHITE);textSize=17f;setPadding(0,16,0,16);layout.addView(this) }
        fun button(label:String,action:()->Unit) { layout.addView(Button(this).apply { text=label;setOnClickListener { action() } }) }
        text("Collegamento ChatGPT")
        text("Legge lo stato di Jarvis e, se abiliti le azioni, apre Maps, cerca i contatti richiesti, avvia chiamate e invia il testo WhatsApp dettato. Funziona mentre il servizio Jarvis è avviato o questa schermata è aperta. L’abbinamento resta salvato dopo aggiornamenti e riavvii.")
        state=text("")
        button("1. Apri pagina privata di collegamento") {
            runCatching { startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(BridgeProtocol.ORIGIN))) }
                .onFailure { state.setText(R.string.bridge_browser_unavailable) }
        }
        button("2. Incolla codice di collegamento") {
            val field=EditText(this).apply {
                inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                hint="JW1.…";importantForAutofill=android.view.View.IMPORTANT_FOR_AUTOFILL_NO
            }
            val dialog=AlertDialog.Builder(this).setTitle("Codice privato dalla pagina JarvisWake Link")
                .setMessage("Incolla il codice creato nella pagina privata. Non inviarlo in chat.")
                .setView(field).setNegativeButton("Annulla",null).setPositiveButton("Salva",null).create()
            dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            dialog.setOnShowListener {
                dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    runCatching { BridgeSettings.saveCode(this,field.text.toString()) }.onSuccess {
                        field.text.clear();enabled.isChecked=false;BridgeLink.refresh(this);state.setText(R.string.bridge_code_saved);dialog.dismiss()
                    }.onFailure { field.error="Codice non valido o impossibile salvarlo. Nessun dettaglio privato registrato." }
                }
            }
            dialog.show()
        }
        enabled=Switch(this).apply {
            setText(R.string.bridge_enabled);setTextColor(Color.WHITE);isChecked=BridgeSettings.enabled(this@BridgeSetupActivity)
            setOnCheckedChangeListener { _,value ->
                if(value && !BridgeSettings.configured(this@BridgeSetupActivity)) { isChecked=false;state.setText(R.string.bridge_code_required) }
                else { BridgeSettings.enable(this@BridgeSetupActivity,value);BridgeLink.refresh(this@BridgeSetupActivity) }
            }
        }
        layout.addView(enabled)
        layout.addView(Switch(this).apply {
            setText(R.string.bridge_actions_enabled);setTextColor(Color.WHITE)
            isChecked=BridgeSettings.actionsEnabled(this@BridgeSetupActivity)
            setOnCheckedChangeListener { _,value -> BridgeSettings.enableActions(this@BridgeSetupActivity,value) }
        })
        text("La ricerca restituisce solo i contatti corrispondenti al nome richiesto. Per telefonare, Jarvis chiude prima Voice e verifica il rilascio del microfono. Lo stato telefonico serve a sospendere la wake durante la chiamata. Gli invii WhatsApp richiedono Accessibilità e chat/campo riconosciuti; una bozza personale non viene sovrascritta.")
        button("Autorizza rubrica e chiamate") {
            requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS,Manifest.permission.CALL_PHONE,Manifest.permission.READ_PHONE_STATE),81)
        }
        button("Cancella collegamento dal telefono") {
            BridgeSettings.clear(this);enabled.isChecked=false;BridgeLink.refresh(this)
        }
        text("Dopo l’abbinamento avvia l’ascolto dalla Home Jarvis. In ChatGPT Live puoi richiedere lo stato, una destinazione Maps, una telefonata o un messaggio WhatsApp. Se ci sono più contatti o numeri, indica quello corretto. Per continuare a parlare con Maps aperto, abilita le conversazioni in background di ChatGPT Live. Il comando inviato non certifica che il destinatario abbia risposto o letto il messaggio.")
        button("Torna a JarvisWake") { finish() }
        setContentView(ScrollView(this).apply { addView(layout) })
    }
    override fun onResume() { super.onResume();BridgeLink.owner(this,"setup",true);handler.post(update) }
    override fun onPause() { handler.removeCallbacks(update);BridgeLink.owner(this,"setup",false);super.onPause() }
}
