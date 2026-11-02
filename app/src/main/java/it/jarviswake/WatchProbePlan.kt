package it.jarviswake

/** Explicit, short-lived preparation. A Watch launch binds it to one cycle; a UI launch is consumed first. */
internal class WatchProbePlan {
    private var until:Long?=null
    private var bound:String?=null
    private var sent:String?=null
    var code="idle";private set
    fun prepare(now:Long) {until=now+120000;bound=null;sent=null;code="prepared"}
    fun expire(now:Long) {if(until?.let {now>=it}==true) cancel("expired")}
    fun bind(token:String?,now:Long):Boolean {
        expire(now)
        if(code!="prepared") return false
        if(token==null) {cancel("launch_not_started");return false}
        bound=token;code="waiting_voice";return true
    }
    fun waiting(token:String,now:Long):Boolean {expire(now);return code=="waiting_voice" && bound==token}
    fun consume(token:String,now:Long,ready:Boolean,stopping:Boolean,unlocked:Boolean):Boolean {
        if(!waiting(token,now)) return false
        if(stopping) {cancel("stop_pending");return false}
        if(!ready || !unlocked) {cancel("voice_not_ready");return false}
        bound=null;sent=token;code="open_dispatched";return true
    }
    fun acknowledge(token:String):Boolean {
        if(sent!=token || code!="open_dispatched") return false
        code="page_opened";sent=null;until=null;return true
    }
    fun unconfirmed(token:String):Boolean {
        if(sent!=token || code!="open_dispatched") return false
        cancel("open_unconfirmed");return true
    }
    fun cycleEnded(token:String) {if(bound==token || sent==token) cancel("cycle_ended")}
    fun cancel(reason:String="cancelled") {until=null;bound=null;sent=null;code=reason}
}
