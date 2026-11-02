package it.jarviswake

/** Public SCO request is global: only attempt when the chosen HFP device is the sole connected one. */
internal data class ScoState(val soleTarget:Boolean,val offCallAvailable:Boolean?,val targetAudio:Boolean?,val globalAudio:Int?)
internal interface ScoPort<D> {fun state(device:D):ScoState;fun start();fun stop()}
internal class ScoAudioTrial<D>(private val port:ScoPort<D>) {
    private var owned:D?=null
    private var startedAt=0L
    private var connectedOnce=false
    private var connectingOnce=false
    var code="idle";private set
    val active get()=owned!=null
    fun start(device:D,now:Long,allowed:Boolean):String {
        if(active) return "busy"
        if(!allowed) {code="not_allowed";return code}
        val s=runCatching {port.state(device)}.getOrNull()
        code=when {
            s==null->"state_unavailable"
            !s.soleTarget->"target_not_unique"
            s.offCallAvailable!=true->if(s.offCallAvailable==false) "off_call_unavailable" else "off_call_unknown"
            s.targetAudio==null->"target_audio_unknown"
            s.targetAudio==true || s.globalAudio==1 || s.globalAudio==2->"audio_already_active"
            s.globalAudio==-1->"global_audio_error"
            else->"ready"
        }
        if(code!="ready") return code
        owned=device;startedAt=now;connectedOnce=false;connectingOnce=false
        // startBluetoothSco returns void. A completed call is dispatch, never acceptance or routing proof.
        code=if(runCatching {port.start()}.isSuccess) "request_dispatched" else "request_uncertain"
        return code
    }
    fun observe(now:Long,allowed:Boolean):String {
        val d=owned ?: return code
        if(!allowed) return stop("conditions_lost")
        val s=runCatching {port.state(d)}.getOrNull()
        if(s==null || !s.soleTarget || s.offCallAvailable!=true || s.targetAudio==null) return stop("state_lost")
        if(s.globalAudio==2) connectingOnce=true
        if(s.targetAudio==true) {connectedOnce=true;code="target_audio_connected"}
        else if(connectedOnce) return stop("target_audio_disconnected")
        else if(s.globalAudio==1) code="global_connected_target_unconfirmed"
        else if(s.globalAudio==-1 || (connectingOnce && s.globalAudio==0)) return stop("connection_failed")
        if(now-startedAt>=90000) return stop("trial_timeout")
        if(!connectedOnce && now-startedAt>=15000) return stop("connect_timeout")
        return code
    }
    fun stop(reason:String="manual"):String {
        val d=owned ?: return code
        owned=null // Consume before the public stop; never retry an uncertain cleanup.
        val dispatched=runCatching {port.stop()}.isSuccess
        val s=runCatching {port.state(d)}.getOrNull()
        val observed=dispatched && s?.targetAudio==false && s.globalAudio==0
        code=reason+if(observed) "_release_observed" else "_release_unconfirmed"
        return code
    }
}
