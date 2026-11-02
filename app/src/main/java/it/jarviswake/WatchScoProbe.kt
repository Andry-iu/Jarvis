package it.jarviswake

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager

/** UI-only, bounded legacy public SCO experiment; no own recorder, telephony event, or mode override. */
@Suppress("DEPRECATION")
internal class WatchScoProbe(private val c:Context,private val hfp:WatchHfpProbe,private val changed:()->Unit) {
    private val audio=c.getSystemService(AudioManager::class.java)
    private var registered=false
    private var global:Int?=null
    private var last=""
    private val receiver=object:BroadcastReceiver() {override fun onReceive(context:Context,intent:Intent) {
        if(registered && intent.action==AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED) {read(intent);changed()}
    }}
    private fun read(i:Intent?) {
        val state=i?.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE,-1)
        global=state?.takeIf {it in -1..2}
    }
    private val port=object:ScoPort<BluetoothDevice> {
        override fun state(device:BluetoothDevice):ScoState {
            check(registered && c.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED)
            val rows=hfp.devices();val s=hfp.state(device) ?: error("profile_unavailable")
            return ScoState(s.connected && rows.size==1 && rows.single()==device,
                runCatching {audio.isBluetoothScoAvailableOffCall}.getOrNull(),s.audioConnected,global)
        }
        override fun start() {audio.startBluetoothSco()}
        override fun stop() {audio.stopBluetoothSco()}
    }
    val trial=ScoAudioTrial(port)
    fun open() {
        if(registered) return
        registered=true
        runCatching {
            val filter=IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
            if(android.os.Build.VERSION.SDK_INT>=33) c.registerReceiver(receiver,filter,Context.RECEIVER_NOT_EXPORTED)
            else c.registerReceiver(receiver,filter) // Protected system-only SCO broadcast on API31/32.
        }
            .onSuccess {read(it)}.onFailure {registered=false;global=null}
    }
    fun describe()="SCO Android: ${trial.code}\nDisponibile fuori telefonata: ${runCatching {audio.isBluetoothScoAvailableOffCall}.getOrNull() ?: "non disponibile"}\nStato SCO globale: ${when(global) {0->"disconnesso";1->"connesso";2->"collegamento in corso";-1->"errore";else->"non osservato"}}\n"+
        "Il comando pubblico è deprecato e non sceglie un indirizzo: la prova richiede un solo dispositivo HFP connesso. target_audio_connected osserva l’audio sul dispositivo scelto, non il microfono usato da ChatGPT."
    fun log() {
        val data="trial=${trial.code} receiver=$registered global_state=${global ?: -2}"
        if(last!=data) {last=data;NativeRuntimeSnapshot.save(c,"watch_probe","watch_sco",data)}
    }
    fun close() {
        trial.stop("page_left");log()
        if(registered) runCatching {c.unregisterReceiver(receiver)}
        registered=false;global=null
    }
}
