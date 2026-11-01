package it.jarviswake

import android.app.Activity
import android.app.AlertDialog
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
        text("Collegamento ChatGPT — prova di stato")
        text("Condivide soltanto versione, stato wake/ciclo e visibilità delle cover. Nessun audio, testo chat o comando per altre app. Funziona mentre il servizio Jarvis è avviato o questa schermata è aperta; Android può limitare la rete a schermo spento.")
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
        button("Cancella collegamento dal telefono") {
            BridgeSettings.clear(this);enabled.isChecked=false;BridgeLink.refresh(this)
        }
        text("Dopo l’abbinamento, avvia l’ascolto dalla Home Jarvis. In ChatGPT Live chiedi: «Usa JarvisWake Link e leggi lo stato del mio telefono». Una richiesta scaduta o in attesa non certifica una risposta del Galaxy.")
        button("Torna a JarvisWake") { finish() }
        setContentView(ScrollView(this).apply { addView(layout) })
    }
    override fun onResume() { super.onResume();BridgeLink.owner(this,"setup",true);handler.post(update) }
    override fun onPause() { handler.removeCallbacks(update);BridgeLink.owner(this,"setup",false);super.onPause() }
}
