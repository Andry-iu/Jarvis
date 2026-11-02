package it.jarviswake

import android.app.Activity
import android.os.Bundle
import android.media.AudioManager
import android.widget.CheckBox
import android.widget.TextView
import android.os.Handler
import android.os.Looper
import android.content.Intent

class WatchProbeActivity:Activity() {
    private val handler=Handler(Looper.getMainLooper())
    private lateinit var output:TextView
    private var selected=false
    private val poll=object:Runnable {override fun run() {if(::output.isInitialized) output.text=WatchAudioProbe.describe(WatchAudioProbe.snapshot(this@WatchProbeActivity));handler.postDelayed(this,1000)}}
    override fun onCreate(b:Bundle?) {
        super.onCreate(b)
        val m=SplitMenuScreen(this,"Galaxy Watch · prova audio","App sperimentale: nessun audio viene inviato a ChatGPT e non viene letto l’audio delle altre app."){finish()}
        m.toggle("Consenti prova dal Watch",WatchTickets.enabled(this),"Autorizza solo il Watch vicino, con app firmata come Jarvis, a richiedere fotografie audio e apertura del ciclo. Il collegamento MCP e la rubrica restano separati.") {WatchTickets.enable(this,it)}
        m.button("Apri Jarvis","Torna alla Home; Parla con Jarvis usa il ciclo esistente."){startActivity(Intent(this,MainActivity::class.java))}
        val consent=CheckBox(this).apply {text="Per la prova è attiva solo ChatGPT Voice, nessuna telefonata. Consento un tentativo di selezione audio di Jarvis.";setTextColor(android.graphics.Color.WHITE)}
        m.addControl(consent)
        val a=getSystemService(AudioManager::class.java)
        a.availableCommunicationDevices.forEach {d->m.button("Prova: ${d.productName}","Richiede questo dispositivo per la comunicazione di Jarvis. Non cambia il microfono privato di ChatGPT e non avvia registrazioni. Nessun cambio del modo audio; rilascia la richiesta uscendo dalla pagina.") {
            if(!consent.isChecked) {m.feedback("Conferma la prova","Seleziona prima la casella e termina eventuali telefonate.");return@button}
            val idle=checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE)==android.content.pm.PackageManager.PERMISSION_GRANTED &&
                runCatching {getSystemService(android.telephony.TelephonyManager::class.java).callState==android.telephony.TelephonyManager.CALL_STATE_IDLE}.getOrDefault(false)
            if(!idle) {m.feedback("Prova sospesa","Stato telefonata non libero o non verificabile. Nessuna selezione audio richiesta.");return@button}
            val accepted=runCatching {a.setCommunicationDevice(d)}.getOrDefault(false);selected=selected || accepted
            m.feedback("Richiesta audio",if(accepted) "Richiesta accettata da Android per Jarvis. Non prova che ChatGPT usi questo ingresso; controlla la fotografia e verifica con telefono distante dal Watch." else "Richiesta rifiutata: nessun instradamento confermato.")
        }}
        m.button("Rilascia selezione audio","Rimuove soltanto la richiesta di dispositivo fatta da questa prova."){if(selected) a.clearCommunicationDevice();selected=false}
        m.text("Per verificare ChatGPT: avvia Voice, torna qui lasciandola in background e confronta i metadati con la Voice ferma. Non registrare dal Watch contemporaneamente. Il telefono deve essere abbastanza distante da non sentire la frase pronunciata al Watch. Se il Watch non è un ingresso compatibile, questa app non crea un microfono virtuale.")
        output=m.text("Leggo i metadati…",14f)
        setContentView(m)
    }
    override fun onResume() {super.onResume();handler.post(poll)}
    override fun onPause() {handler.removeCallbacks(poll);if(selected) getSystemService(AudioManager::class.java).clearCommunicationDevice();selected=false;super.onPause()}
}
