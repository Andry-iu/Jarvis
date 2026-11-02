package it.jarviswake.watch

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.*
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.*
import com.google.android.gms.wearable.*
import androidx.wear.remote.interactions.RemoteActivityHelper
import java.util.UUID
import org.json.JSONObject

/** Foreground-only microphone test. Samples stay in RAM on the watch; never sent to phone or MCP. */
class WatchProbeActivity:Activity(),MessageClient.OnMessageReceivedListener {
    private lateinit var status:TextView
    private val main=Handler(Looper.getMainLooper())
    @Volatile private var generation=0
    @Volatile private var recorder:AudioRecord?=null
    @Volatile private var player:AudioTrack?=null
    private var sample=ShortArray(0)
    private var busy=false
    private var active=false
    private var node:String?=null
    private var request:String?=null
    private var remotePending=false
    private val timeout=Runnable {if(remotePending) {remotePending=false;request=null;status.text="Nessuna risposta correlata dal telefono. Nessun avvio confermato; ricontrolla connessione e consenso."}}
    override fun onCreate(b:Bundle?) {
        super.onCreate(b)
        val list=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_HORIZONTAL;setPadding(30,36,30,48);setBackgroundColor(0xff08121c.toInt())}
        fun text(s:String,size:Float=14f)=TextView(this).apply {text=s;textSize=size;setTextColor(0xffc6dfe5.toInt());gravity=Gravity.CENTER;setPadding(4,10,4,10);list.addView(this)}
        text("JARVIS · WATCH\nPROVA 0.5.93",18f)
        text("Prima verifica il Watch da solo. Durante ChatGPT usa solo Fotografia telefono: non avviare un’altra registrazione.")
        fun button(s:String,action:()->Unit) {list.addView(Button(this).apply {text=s;isAllCaps=false;isSingleLine=false;ellipsize=null;minHeight=(56*resources.displayMetrics.density).toInt();setOnClickListener {action()}},LinearLayout.LayoutParams(-1,-2))}
        button("1 · Registra 5 secondi") {
            if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) {requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),1);return@button}
            record()
        }
        button("2 · Riascolta sul Watch") {play()}
        button("3 · Fotografia audio telefono") {remote(false)}
        button("4 · Avvia Jarvis sul telefono") {remote(true)}
        button("Ferma prova locale") {stopLocal();status.text="Prova locale terminata. Voice sul telefono non è stata toccata."}
        status=text("Telefono: attiva Jarvis → Servizio → Galaxy Watch · prova audio → Consenti prova dal Watch.")
        text("Il richiamo non rende il Watch un microfono di ChatGPT. Il doppio clic Home può aprire questa app dopo l’assegnazione nelle impostazioni Samsung.")
        setContentView(ScrollView(this).apply {addView(list)})
    }
    private fun stopLocal() {
        generation++;busy=false
        runCatching {recorder?.stop()};runCatching {player?.stop()}
        sample.fill(0);sample=ShortArray(0)
    }
    private fun record() {
        if(busy || remotePending) {status.text="Termina la prova corrente prima di registrare.";return}
        val a=getSystemService(AudioManager::class.java)
        if(runCatching {a.activeRecordingConfigurations.isNotEmpty()}.getOrDefault(true)) {status.text="Un’altra cattura è attiva sul Watch o lo stato non è disponibile. Nessuna registrazione avviata.";return}
        val mic=a.getDevices(AudioManager.GET_DEVICES_INPUTS).singleOrNull {it.type==AudioDeviceInfo.TYPE_BUILTIN_MIC}
        if(mic==null) {status.text="Microfono integrato non identificato in modo univoco.";return}
        sample.fill(0);sample=ShortArray(0);busy=true;val epoch=++generation;status.text="Registra dal microfono del WATCH · 5 secondi…"
        Thread({
            var rec:AudioRecord?=null
            val bytes=ShortArray(80000)
            val answer=runCatching {
                val min=AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);check(min>0)
                if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) throw SecurityException("microphone_permission")
                rec=AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.MIC).setAudioFormat(AudioFormat.Builder().setSampleRate(16000).setChannelMask(AudioFormat.CHANNEL_IN_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build()).setBufferSizeInBytes(maxOf(min*2,4096)).build()
                recorder=rec;check(rec!!.setPreferredDevice(mic));check(epoch==generation)
                rec!!.startRecording();val end=android.os.SystemClock.elapsedRealtime()+5500;var count=0;var peak=0
                while(count<bytes.size && epoch==generation && android.os.SystemClock.elapsedRealtime()<end) {
                    val n=rec!!.read(bytes,count,minOf(320,bytes.size-count),AudioRecord.READ_NON_BLOCKING)
                    check(n>=0)
                    if(n>0) {for(i in count until count+n) peak=maxOf(peak,kotlin.math.abs(bytes[i].toInt()));count+=n} else Thread.sleep(10)
                }
                check(epoch==generation && count>0)
                val routed=rec!!.routedDevice;check(routed?.type==AudioDeviceInfo.TYPE_BUILTIN_MIC)
                check(rec!!.activeRecordingConfiguration?.isClientSilenced==false)
                Triple(bytes.copyOf(count),peak,"Ingresso verificato: microfono integrato del Watch. Campione ${(count/16000f)}s, picco=$peak. Riascoltalo; questo non è ancora audio ChatGPT.")
            }
            runCatching {rec?.stop()};runCatching {rec?.release()};if(recorder===rec) recorder=null;bytes.fill(0)
            main.post {if(active && epoch==generation) {busy=false;answer.onSuccess {sample=it.first;status.text=it.third}.onFailure {status.text="Registrazione Watch non confermata: ${it.javaClass.simpleName}."}} else answer.getOrNull()?.first?.fill(0)}
        },"Jarvis-watch-mic-test").start()
    }
    private fun play() {
        if(busy || remotePending || sample.isEmpty()) {status.text="Registra prima un campione e attendi la fine.";return}
        val a=getSystemService(AudioManager::class.java)
        if(runCatching {a.activeRecordingConfigurations.isNotEmpty()}.getOrDefault(true)) {status.text="Cattura attiva sul Watch: riascolto sospeso.";return}
        val speaker=a.getDevices(AudioManager.GET_DEVICES_OUTPUTS).singleOrNull {it.type==AudioDeviceInfo.TYPE_BUILTIN_SPEAKER}
        if(speaker==null) {status.text="Altoparlante Watch non identificato.";return}
        busy=true;val epoch=++generation;val copy=sample.copyOf();status.text="Riascolto sul Watch…"
        Thread({
            var track:AudioTrack?=null
            val result=runCatching {
                track=AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    .setAudioFormat(AudioFormat.Builder().setSampleRate(16000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build()).setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(copy.size*2).build()
                player=track;check(track!!.setPreferredDevice(speaker));check(epoch==generation)
                check(track!!.write(copy,0,copy.size)==copy.size);track!!.play()
                val end=android.os.SystemClock.elapsedRealtime()+copy.size*1000L/16000+100
                var routed=false
                while(epoch==generation && android.os.SystemClock.elapsedRealtime()<end) {if(track!!.routedDevice?.type==AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) routed=true;Thread.sleep(25)}
                check(epoch==generation && routed)
            }
            runCatching {track?.stop()};runCatching {track?.release()};if(player===track) player=null;copy.fill(0)
            main.post {if(active && epoch==generation) {busy=false;status.text=if(result.isSuccess) "Riproduzione instradata all’altoparlante Watch. Se l’hai sentita, microfono e uscita locali sono verificati; ChatGPT non è ancora collegata a questo campione." else "Uscita Watch non confermata."}}
        },"Jarvis-watch-speaker-test").start()
    }
    private fun remote(start:Boolean) {
        if(busy || remotePending) {status.text="Termina prima la prova locale o attendi la risposta.";return}
        val id=UUID.randomUUID().toString();request=id;remotePending=true;status.text="Cerco il telefono vicino…";main.postDelayed(timeout,15000)
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener {nodes->
            if(!active || request!=id) return@addOnSuccessListener
            val phones=nodes.filter {it.isNearby}
            if(phones.size!=1) {remotePending=false;request=null;main.removeCallbacks(timeout);status.text="Serve un solo telefono vicino e connesso. Trovati: ${phones.size}.";return@addOnSuccessListener}
            node=phones.single().id
            Wearable.getMessageClient(this).sendMessage(node!!,if(start) WatchProtocol.PREPARE else WatchProtocol.SNAPSHOT,JSONObject().put("id",id).toString().toByteArray())
                .addOnFailureListener {if(request==id) {remotePending=false;request=null;main.removeCallbacks(timeout);status.text="Messaggio non consegnato. Nessun avvio confermato."}}
        }.addOnFailureListener {if(request==id) {remotePending=false;request=null;main.removeCallbacks(timeout);status.text="Collegamento Wear OS non disponibile."}}
    }
    override fun onMessageReceived(e:MessageEvent) {
        if(e.path!=WatchProtocol.REPLY || e.sourceNodeId!=node || e.data.size>16000) return
        val j=runCatching {JSONObject(e.data.toString(Charsets.UTF_8))}.getOrNull() ?: return
        runOnUiThread {
            if(!active || !remotePending || j.optString("id")!=request) return@runOnUiThread
            main.removeCallbacks(timeout);remotePending=false;val id=request;request=null
            when(j.optString("code")) {
                "prepared" -> {
                    status.text="Richiamo telefono… Lo sblocco può richiedere PIN o biometria sul telefono."
                    val intent=Intent(Intent.ACTION_VIEW,Uri.parse("jarviswake://watch/start?id=$id")).addCategory(Intent.CATEGORY_BROWSABLE)
                    val future=RemoteActivityHelper(this,mainExecutor).startRemoteActivity(intent,node)
                    future.addListener({if(active) {status.text=runCatching {future.get();"Richiesta di apertura consegnata, non conferma di sblocco o Voice. Usa Fotografia telefono per verificarne la fase."}.getOrDefault("Apertura non confermata; la richiesta scade. Nessun retry automatico.")}},mainExecutor)
                }
                "snapshot" -> status.text=describe(j)
                "disabled" -> status.text="Sul telefono attiva Consenti prova dal Watch nella sezione Servizio → Galaxy Watch · prova audio."
                "nearby_required" -> status.text="Il telefono non conferma il Watch vicino. Verifica Bluetooth."
                else->status.text="Risposta telefono: ${j.optString("code")}. Nessun avvio confermato."
            }
        }
    }
    private fun describe(j:JSONObject)=buildString {
        append("Fotografia telefono: ${java.time.Instant.ofEpochMilli(j.optLong("at"))}\nBloccato=${j.optBoolean("locked")} · ciclo=${j.optString("voiceCycle")}\n")
        if(!j.optBoolean("metadataAvailable")) {append("Metadati audio non disponibili.");return@buildString}
        val ds=j.optJSONArray("communication");append("\nDispositivi comunicazione:\n")
        if(ds==null || ds.length()==0) append("Nessuno esposto.\n")
        else for(i in 0 until ds.length()) {val d=ds.getJSONObject(i);append("${d.optString("name")} · tipo ${d.optInt("type")}\n")}
        append("Selezionato: ${j.optJSONObject("selected")?.optString("name") ?: "non esposto"}\n")
        val rs=j.optJSONArray("recordings");append("Registrazioni visibili: ${rs?.length() ?: 0}\n")
        if(rs!=null) for(i in 0 until rs.length()) {val d=rs.getJSONObject(i).optJSONObject("input");append("Ingresso: ${d?.optString("name") ?: "non esposto"} · tipo ${d?.optInt("type") ?: -1}\n")}
        append("\nNon attribuibile con certezza a ChatGPT. Dettagli e tentativo di selezione nella pagina di prova sul telefono. Nessun audio del telefono è stato registrato da Jarvis.")
    }
    override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults)
        if(requestCode==1) status.text=if(grantResults.firstOrNull()==PackageManager.PERMISSION_GRANTED) "Microfono autorizzato. Premi Registra 5 secondi per iniziare." else "Microfono non autorizzato. Nessuna registrazione avviata."
    }
    override fun onResume() {super.onResume();active=true;Wearable.getMessageClient(this).addListener(this)}
    override fun onPause() {active=false;Wearable.getMessageClient(this).removeListener(this);main.removeCallbacks(timeout);request=null;remotePending=false;stopLocal();super.onPause()}
}
