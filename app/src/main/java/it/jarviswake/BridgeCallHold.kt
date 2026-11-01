package it.jarviswake

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager

/** Observes call state only, never phone numbers; no new microphone or audio routing. */
internal object BridgeCallHold {
    private var manager:TelephonyManager?=null
    private var callback:TelephonyCallback?=null
    private var state:Int?=null
    private var token:String?=null
    private var startedAt=0L
    private var sawCall=false
    val holding:Boolean get()=token!=null || state==TelephonyManager.CALL_STATE_OFFHOOK || state==TelephonyManager.CALL_STATE_RINGING
    @SuppressLint("MissingPermission")
    fun connect(c:Context):Boolean {
        if(c.checkSelfPermission(Manifest.permission.READ_PHONE_STATE)!=PackageManager.PERMISSION_GRANTED) return false
        if(callback!=null) return state!=null
        return runCatching {
            val tm=c.getSystemService(TelephonyManager::class.java)
            @Suppress("DEPRECATION") val current=tm.callState
            state=current
            val observer=object:TelephonyCallback(),TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(value:Int) {
                    state=value
                    if(value!=TelephonyManager.CALL_STATE_IDLE) sawCall=true
                    tick(c.applicationContext)
                }
            }
            tm.registerTelephonyCallback(c.mainExecutor,observer)
            manager=tm;callback=observer
            val saved=c.getSharedPreferences("bridge_call_hold",0)
            token=saved.getString("token",null)
            startedAt=saved.getLong("elapsed",0)
            sawCall=state!=TelephonyManager.CALL_STATE_IDLE
            true
        }.getOrDefault(false)
    }
    fun acquire(c:Context,id:String) {
        if(!connect(c)) throw BridgeFailure("permission_required")
        tick(c)
        if(holding) throw BridgeFailure("audio_busy")
        token=id;startedAt=SystemClock.elapsedRealtime();sawCall=false
        check(c.getSharedPreferences("bridge_call_hold",0).edit().putString("token",id).putLong("elapsed",startedAt).commit())
        WakeService.pauseForTelephone()
    }
    fun abort(c:Context,id:String) {
        if(token!=id) return
        token=null
        c.getSharedPreferences("bridge_call_hold",0).edit().clear().apply()
    }
    fun tick(c:Context) {
        if(c.checkSelfPermission(Manifest.permission.READ_PHONE_STATE)!=PackageManager.PERMISSION_GRANTED) {
            disconnect()
            if(token!=null && NativeAudioState.read(c).free) abort(c,token!!)
            return
        }
        if(token==null) return
        val now=SystemClock.elapsedRealtime()
        val age=if(now<startedAt) 30_000 else now-startedAt
        if(BridgeActionPolicy.mayResumeAfterCall(state==TelephonyManager.CALL_STATE_IDLE,sawCall,age,
                NativeAudioState.read(c).free)) abort(c,token!!)
    }
    fun disconnect() {
        callback?.let { observer -> runCatching { manager?.unregisterTelephonyCallback(observer) } }
        manager=null;callback=null;state=null
    }
}
