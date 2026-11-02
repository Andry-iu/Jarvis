package it.jarviswake

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/** UI-scoped profile proxy. Does not bond/connect devices, capture audio, or change AudioManager.mode. */
internal class WatchHfpProbe(private val c:Context,private val changed:()->Unit) {
    private val main=Handler(Looper.getMainLooper())
    private var profile:BluetoothHeadset?=null
    private var epoch=0
    private var resumed=false
    private var loading=false
    var serviceCode="idle";private set
    private var last=""
    private val port=object:HfpPort<BluetoothDevice> {
        override fun state(device:BluetoothDevice):HfpState {
            if(c.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED) throw SecurityException()
            val p=profile ?: error("profile_unavailable")
            return HfpState(p.getConnectionState(device)==BluetoothProfile.STATE_CONNECTED,
                runCatching {p.isVoiceRecognitionSupported(device)}.getOrNull(),runCatching {p.isAudioConnected(device)}.getOrNull())
        }
        override fun start(device:BluetoothDevice):Boolean {
            if(c.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED) throw SecurityException()
            return (profile ?: error("profile_unavailable")).startVoiceRecognition(device)
        }
        override fun stop(device:BluetoothDevice):Boolean {
            if(c.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED) throw SecurityException()
            return (profile ?: error("profile_unavailable")).stopVoiceRecognition(device)
        }
    }
    val trial=HfpAudioTrial(port)
    fun open() {
        resumed=true
        if(c.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED) {serviceCode="permission_required";changed();return}
        if(profile!=null || loading) return
        val adapter=runCatching {c.getSystemService(BluetoothManager::class.java)?.adapter?.takeIf {it.isEnabled}}.getOrNull()
        if(adapter==null) {serviceCode="bluetooth_unavailable";changed();return}
        loading=true;val current=++epoch;serviceCode="connecting"
        val listener=object:BluetoothProfile.ServiceListener {
            override fun onServiceConnected(type:Int,p:BluetoothProfile) {main.post {
                if(!resumed || current!=epoch || type!=BluetoothProfile.HEADSET || p !is BluetoothHeadset) {runCatching {adapter.closeProfileProxy(type,p)};return@post}
                profile=p;loading=false;serviceCode="profile_connected";changed()
            }}
            override fun onServiceDisconnected(type:Int) {main.post {
                if(current!=epoch) return@post
                trial.stop("profile_lost");profile=null;loading=false;serviceCode="profile_disconnected";changed()
            }}
        }
        val accepted=runCatching {adapter.getProfileProxy(c,listener,BluetoothProfile.HEADSET)}.getOrDefault(false)
        if(!accepted) {loading=false;serviceCode="profile_unavailable"}
        main.postDelayed({if(resumed && current==epoch && loading) {epoch++;loading=false;serviceCode="profile_timeout";changed()}},5000)
        changed()
    }
    fun devices():List<BluetoothDevice> {
        if(c.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED) return emptyList()
        return runCatching {profile?.connectedDevices.orEmpty()}.getOrDefault(emptyList())
    }
    fun name(d:BluetoothDevice):String {
        if(c.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED) return "Dispositivo HFP"
        return runCatching {d.name?.take(60) ?: "Dispositivo HFP"}.getOrDefault("Dispositivo HFP")
    }
    fun state(d:BluetoothDevice)=runCatching {port.state(d)}.getOrNull()
    fun describe():String=buildString {
        append("HFP: $serviceCode\nProva: ${trial.code}\n")
        val rows=devices()
        if(rows.isEmpty()) append("Nessun dispositivo HFP connesso esposto. Questo non significa che il Watch non sia collegato tramite Data Layer.\n")
        for(d in rows) {val s=state(d);append("${name(d)}\nSupporto riconoscimento vocale HFP: ${s?.supported ?: "non disponibile"}\nAudio SCO connesso: ${s?.audioConnected ?: "non disponibile"}\n")}
        append("\nSolo audio_connected conferma il canale Bluetooth; non prova che ChatGPT lo usi. La prova richiede scelta esplicita del Watch, nessuna telefonata e assenza della cattura wake.")
    }
    fun log() {
        val states=devices().map {state(it)}
        val data="service=$serviceCode trial=${trial.code} connected_count=${states.size} supported_count=${states.count {it?.supported==true}} audio_count=${states.count {it?.audioConnected==true}}"
        if(last!=data) {last=data;NativeRuntimeSnapshot.save(c,"watch_probe","watch_hfp",data)}
    }
    fun close() {
        resumed=false;epoch++;loading=false;trial.stop("page_left");log()
        val old=profile;profile=null
        if(old!=null) runCatching {c.getSystemService(BluetoothManager::class.java)?.adapter?.closeProfileProxy(BluetoothProfile.HEADSET,old)}
    }
}
