package it.jarviswake

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.os.Bundle
import android.media.AudioManager
import android.media.AudioDeviceInfo
import android.widget.CheckBox
import android.widget.TextView
import android.widget.LinearLayout
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.content.Intent
import android.content.pm.PackageManager
import android.app.KeyguardManager
import android.telephony.TelephonyManager
import android.view.WindowManager

class WatchProbeActivity:Activity() {
    private val handler=Handler(Looper.getMainLooper())
    private lateinit var output:TextView
    private lateinit var hfpStatus:TextView
    private lateinit var consent:CheckBox
    private lateinit var menu:SplitMenuScreen
    private lateinit var hfpArea:LinearLayout
    private lateinit var routeArea:LinearLayout
    private lateinit var hfp:WatchHfpProbe
    private var selected=false
    private var resumed=false
    private var hfpKey=""
    private var routeKey=""
    private val poll=object:Runnable {override fun run() {
        if(!resumed) return
        hfp.trial.observe(SystemClock.elapsedRealtime(),allowed())
        hfpStatus.text=hfp.describe();hfp.log();renderChoices()
        output.text=WatchAudioProbe.describe(WatchAudioProbe.snapshot(this@WatchProbeActivity))
        if(hfp.trial.active) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        handler.postDelayed(this,1000)
    }}
    override fun onCreate(b:Bundle?) {
        super.onCreate(b)
        menu=SplitMenuScreen(this,"Galaxy Watch · prova Bluetooth","Prova .94 sul telefono; app Watch .93 invariata. Nessuna cattura audio sul telefono e nessuna chiamata simulata."){finish()}
        val m=menu
        m.toggle("Consenti prova dal Watch",WatchTickets.enabled(this),"Consente richieste Data Layer da app Watch con la stessa firma. Non abilita automaticamente HFP o il microfono ChatGPT.") {WatchTickets.enable(this,it)}
        m.button("Autorizza dispositivi vicini","Richiede BLUETOOTH_CONNECT per leggere il profilo HFP dei dispositivi già collegati. Nessuna scansione, associazione o connessione automatica.") {
            if(checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT),94) else hfp.open()
        }
        m.button("Leggi profili Bluetooth","Legge i dispositivi HFP già connessi; non avvia alcun canale audio."){hfp.open();renderChoices()}
        consent=CheckBox(this).apply {text="Scelgo il mio Watch per la prova. Nessuna telefonata; ChatGPT Voice già attiva e registrazione di prova sul Watch ferma.";setTextColor(android.graphics.Color.WHITE)}
        m.addControl(consent)
        hfpStatus=m.text("Profilo HFP non ancora letto.",15f)
        hfpArea=m.buttonsArea()
        m.button("Ferma prova Bluetooth","Richiede lo stop una sola volta del canale aperto da questa prova. Un canale già attivo prima non viene chiuso.") {hfp.trial.stop();hfpStatus.text=hfp.describe();hfp.log()}
        m.text("Avvia Voice dal Watch come prima. Per tornare qui usa la notifica Prova audio Watch sul telefono: apre questa pagina e nasconde la cover, mantenendo il ciclo Voice. Se non ci sono dispositivi HFP, invia lo stato senza ripetere avvii. Se compare il tuo Watch, prova il suo canale. Il tentativo attende al massimo15s la connessione e termina entro90s o uscendo dalla pagina.")
        routeArea=m.buttonsArea()
        m.button("Rilascia selezione audio","Rimuove soltanto la richiesta di dispositivo fatta da Jarvis."){releaseRoute()}
        m.text("Se dopo l’apertura HFP compare un dispositivo Bluetooth SCO, puoi sceglierlo qui. Non basta request_accepted: confronta audio_connected e l’ingresso riportato sotto. Voce riconosciuta e risposta dal Watch vanno verificate con il telefono distante. La selezione di Jarvis può essere ignorata o sostituita da ChatGPT; nessun microfono virtuale viene creato.")
        output=m.text("Leggo i metadati…",14f)
        hfp=WatchHfpProbe(this) {if(::hfp.isInitialized && resumed) {hfpStatus.text=hfp.describe();renderChoices()}}
        setContentView(m);showReturnNotification()
    }
    private fun allowed():Boolean {
        val k=getSystemService(KeyguardManager::class.java)
        val wake=WakeService.bridgeStatus().optString("capture")
        return resumed && consent.isChecked && VoiceCycle.active && VoiceCycle.phase==VoiceCycleState.Phase.VOICE && NativeChatSettings.enabled(this) && !k.isKeyguardLocked && !k.isDeviceLocked && wake=="not_recording" &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.READ_PHONE_STATE)==PackageManager.PERMISSION_GRANTED &&
            runCatching {getSystemService(TelephonyManager::class.java).callState==TelephonyManager.CALL_STATE_IDLE}.getOrDefault(false)
    }
    private fun renderChoices() {
        val rows=hfp.devices();val key=rows.joinToString("|") {it.hashCode().toString()+":"+hfp.name(it)}
        if(key!=hfpKey) {hfpKey=key;hfpArea.removeAllViews()
            for(d in rows) menu.button("Prova canale: ${hfp.name(d)}","Un solo startVoiceRecognition sul dispositivo scelto, soltanto se supportato e audio non già connesso. Usa la richiesta pubblica HFP di riconoscimento vocale; la risposta del firmware Watch resta da verificare. Non avvia una telefonata.",area=hfpArea) {
                val code=hfp.trial.start(d,SystemClock.elapsedRealtime(),allowed())
                menu.feedback("Prova HFP",explain(code));hfpStatus.text=hfp.describe();hfp.log()
            }
        }
        val devices=runCatching {getSystemService(AudioManager::class.java).availableCommunicationDevices}.getOrDefault(emptyList())
        val rkey=devices.joinToString("|") {"${it.id}:${it.type}:${it.productName}"}
        if(rkey!=routeKey) {routeKey=rkey;routeArea.removeAllViews()
            for(d in devices) menu.button("Prova dispositivo: ${d.productName}","Richiede il dispositivo attuale per la comunicazione di Jarvis, senza modificare AudioManager.mode. Nessuna attribuzione automatica a ChatGPT.",area=routeArea) {select(d)}
        }
    }
    private fun select(d:AudioDeviceInfo) {
        if(!allowed()) {menu.feedback("Prova sospesa","Conferma la casella, autorizza Bluetooth e Stato telefono, termina le telefonate e assicurati che la wake sia sospesa durante Voice.");return}
        val a=getSystemService(AudioManager::class.java)
        val current=a.availableCommunicationDevices.singleOrNull {it.id==d.id && it.type==d.type && it.productName.toString()==d.productName.toString()}
        val accepted=current!=null && runCatching {a.setCommunicationDevice(current)}.getOrDefault(false)
        selected=selected || accepted
        menu.feedback("Richiesta audio",if(accepted) "Richiesta accettata per Jarvis; controlla l’ingresso effettivo. Non conferma ChatGPT sul Watch." else "Dispositivo cambiato o richiesta rifiutata. Nessun instradamento confermato.")
    }
    private fun releaseRoute() {if(selected) runCatching {getSystemService(AudioManager::class.java).clearCommunicationDevice()};selected=false}
    private fun explain(code:String)=when(code) {
        "request_accepted"->"Richiesta accettata. Attendo audio_connected; non è ancora prova di audio ChatGPT."
        "unsupported"->"Il dispositivo non dichiara supporto al canale vocale HFP richiesto. Nessun tentativo eseguito."
        "audio_already_connected"->"Un canale audio era già connesso. La prova non lo modifica e non lo chiuderà."
        "not_allowed"->"Conferma la casella e i permessi, nessuna telefonata, telefono sbloccato e wake sospesa. Nessun tentativo eseguito."
        else->"Stato: $code. Nessuna ripetizione automatica."
    }
    private fun showReturnNotification() {
        if(android.os.Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) return
        val n=getSystemService(NotificationManager::class.java)
        n.createNotificationChannel(NotificationChannel("watch_probe","Prova audio Watch",NotificationManager.IMPORTANCE_LOW))
        val open=PendingIntent.getActivity(this,94,Intent(this,WatchProbeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        n.notify(94,Notification.Builder(this,"watch_probe").setSmallIcon(R.drawable.ic_jarvis).setContentTitle("Prova audio Watch").setContentText("Tocca per aprire la prova durante ChatGPT Voice.").setContentIntent(open).setOnlyAlertOnce(true).build())
    }
    override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults)
        if(requestCode==94 && resumed) hfp.open()
    }
    override fun onResume() {
        super.onResume();resumed=true
        if(VoiceCycle.active && NativeChatSettings.enabled(this)) VoiceAccessibilityService.releaseBridgeCover()
        hfp.open();handler.post(poll)
    }
    override fun onPause() {resumed=false;handler.removeCallbacks(poll);hfp.close();releaseRoute();window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);super.onPause()}
    override fun onDestroy() {if(isFinishing) getSystemService(NotificationManager::class.java).cancel(94);super.onDestroy()}
}
