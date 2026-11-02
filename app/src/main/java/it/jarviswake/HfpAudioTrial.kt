package it.jarviswake

internal data class HfpState(val connected:Boolean,val supported:Boolean?,val audioConnected:Boolean?)
internal interface HfpPort<D> {
    fun state(device:D):HfpState
    fun start(device:D):Boolean
    fun stop(device:D):Boolean
}

/** One explicit, bounded request. Never stop an audio connection that existed before our trial. */
internal class HfpAudioTrial<D>(private val port:HfpPort<D>) {
    private var owned:D?=null
    private var startedAt=0L
    private var connectedOnce=false
    var code="idle";private set
    val active get()=owned!=null
    fun start(device:D,now:Long,allowed:Boolean):String {
        if(active) return "busy"
        if(!allowed) {code="not_allowed";return code}
        val state=runCatching {port.state(device)}.getOrNull()
        code=when {
            state==null->"state_unavailable"
            !state.connected->"not_connected"
            state.supported!=true->if(state.supported==false) "unsupported" else "support_unavailable"
            state.audioConnected!=false->if(state.audioConnected==true) "audio_already_connected" else "audio_state_unavailable"
            else->"ready"
        }
        if(code!="ready") return code
        owned=device;startedAt=now;connectedOnce=false
        val accepted=runCatching {port.start(device)}.getOrNull()
        if(accepted==false) {owned=null;code="request_rejected"}
        else code=if(accepted==true) "request_accepted" else "request_uncertain"
        return code
    }
    fun observe(now:Long,allowed:Boolean):String {
        val d=owned ?: return code
        if(!allowed) return stop("conditions_lost")
        val state=runCatching {port.state(d)}.getOrNull()
        if(state==null || !state.connected || state.audioConnected==null) return stop("state_lost")
        if(state.audioConnected==true) {connectedOnce=true;code="audio_connected"}
        else if(connectedOnce) return stop("audio_disconnected")
        if(now-startedAt>=90000) return stop("trial_timeout")
        if(!connectedOnce && now-startedAt>=15000) return stop("connect_timeout")
        return code
    }
    fun stop(reason:String="manual"):String {
        val d=owned ?: return code
        owned=null // Consume before calling Android. No repeated stop if callback/poll/close arrives again.
        val stopped=runCatching {port.stop(d)}.getOrDefault(false)
        val released=stopped && runCatching {port.state(d).audioConnected==false}.getOrDefault(false)
        code=reason+if(released) "_released" else "_release_unconfirmed"
        return code
    }
}
