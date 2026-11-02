package it.jarviswake

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.app.KeyguardManager
import org.json.JSONArray
import org.json.JSONObject

/** Metadata only: no AudioRecord, no audio bytes, no attribution of another app's recording. */
internal object WatchAudioProbe {
    fun type(n:Int)=when(n) {
        AudioDeviceInfo.TYPE_BUILTIN_MIC->"Microfono integrato"
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER->"Altoparlante integrato"
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE->"Auricolare telefono"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO->"Bluetooth SCO"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP->"Bluetooth A2DP (uscita)"
        AudioDeviceInfo.TYPE_BLE_HEADSET->"Bluetooth LE headset"
        AudioDeviceInfo.TYPE_WIRED_HEADSET->"Cuffia con microfono"
        AudioDeviceInfo.TYPE_USB_HEADSET->"Cuffia USB"
        else->"Tipo $n"
    }
    private fun device(d:AudioDeviceInfo)=JSONObject().put("id",d.id).put("type",d.type).put("name",d.productName.toString().take(64)).put("source",d.isSource).put("sink",d.isSink)
    @Volatile private var last=""
    fun snapshot(c:Context):JSONObject {
        val a=c.getSystemService(AudioManager::class.java)
        val j=JSONObject().put("code","snapshot").put("at",System.currentTimeMillis()).put("sdk",android.os.Build.VERSION.SDK_INT)
            .put("locked",c.getSystemService(KeyguardManager::class.java).isKeyguardLocked)
            .put("voiceCycle",if(VoiceCycle.active) VoiceCycle.phase.name else "IDLE")
        runCatching {
            j.put("devices",JSONArray().apply {(a.getDevices(AudioManager.GET_DEVICES_INPUTS).toList()+a.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()).distinctBy {it.id}.forEach {put(device(it))}})
            j.put("communication",JSONArray().apply {a.availableCommunicationDevices.forEach {put(device(it))}})
            j.put("selected",a.communicationDevice?.let(::device) ?: JSONObject.NULL)
            j.put("recordings",JSONArray().apply {a.activeRecordingConfigurations.forEach {r->put(JSONObject()
                .put("input",r.audioDevice?.let(::device) ?: JSONObject.NULL).put("silenced",r.isClientSilenced))}})
            j.put("metadataAvailable",true)
        }.onFailure {j.put("metadataAvailable",false)}
        // Never claim this is ChatGPT's input: Android may omit/anonymize other apps' metadata.
        j.put("chatgptRouteConfirmed",false)
        val metadata="available=${j.optBoolean("metadataAvailable")} locked=${j.optBoolean("locked")} communication_types="+
            (j.optJSONArray("communication")?.let {a->(0 until a.length()).map {a.getJSONObject(it).optInt("type")}.sorted().joinToString(",")} ?: "unknown")+
            " selected_type=${j.optJSONObject("selected")?.optInt("type") ?: -1} recording_input_types="+
            (j.optJSONArray("recordings")?.let {a->(0 until a.length()).map {a.getJSONObject(it).optJSONObject("input")?.optInt("type") ?: -1}.sorted().joinToString(",")} ?: "unknown")
        if(metadata!=last) {last=metadata;NativeRuntimeSnapshot.save(c,"watch_probe","watch_audio",metadata)}
        return j
    }
    fun describe(j:JSONObject):String=buildString {
        append("Fotografia: "+java.time.Instant.ofEpochMilli(j.getLong("at"))+"\n")
        append("Telefono bloccato: ${j.optBoolean("locked")} · ciclo Jarvis: ${j.optString("voiceCycle")}\n")
        if(!j.optBoolean("metadataAvailable")) {append("Metadati audio non disponibili. Nessuna conclusione sul microfono.\n");return@buildString}
        fun devices(key:String,title:String) {
            append("\n$title\n");val rows=j.optJSONArray(key)
            if(rows==null || rows.length()==0) append("Nessuno esposto dal sistema.\n")
            else for(i in 0 until rows.length()) {val d=rows.getJSONObject(i);append("• ${d.optString("name")} · ${type(d.optInt("type"))} · ingresso=${d.optBoolean("source")} uscita=${d.optBoolean("sink")}\n")}
        }
        devices("devices","Dispositivi audio Android")
        devices("communication","Selezionabili per comunicazione")
        val selected=j.optJSONObject("selected");append("\nDispositivo comunicazione selezionato: "+(selected?.optString("name") ?: "non esposto")+"\n")
        val r=j.optJSONArray("recordings");append("Registrazioni visibili: ${r?.length() ?: 0}\n")
        if(r!=null) for(i in 0 until r.length()) {val x=r.getJSONObject(i);val d=x.optJSONObject("input");append("• Ingresso ${d?.optString("name") ?: "non esposto"} · ${d?.optInt("type")?.let(::type) ?: "sconosciuto"} · silenziato=${x.optBoolean("silenced")}\n")}
        append("\nMetadati globali: non identificano con certezza ChatGPT né provano che ti senta. La nostra app sul telefono non apre il microfono.")
    }
}
