package it.jarviswake

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock

/** Only counts/mode are read; never package names, recording contents or other apps' identities. */
internal data class NativeAudioState(val recordings: Int?,val normalMode: Boolean?,val microphoneMuted: Boolean? = false) {
    val free get() = recordings==0 && normalMode==true
    fun canKeepOwn(ownActive: Boolean) = normalMode==true && microphoneMuted==false && recordings!=null && recordings>=0 && recordings<=(if (ownActive) 1 else 0)
    companion object {
        fun read(context: Context): NativeAudioState = runCatching {
            val manager=context.getSystemService(AudioManager::class.java)
            NativeAudioState(manager.activeRecordingConfigurations.size,manager.mode==AudioManager.MODE_NORMAL,manager.isMicrophoneMute)
        }.getOrDefault(NativeAudioState(null,null,null))
    }
}

/** A UI timeout is not proof. Require continuously free system capture for this same cycle. */
internal class NativeAudioReleaseGate {
    private var token: String?=null
    private var since: Long?=null
    fun observe(cycle: String?,now: Long,free: Boolean): Boolean {
        if (cycle!=token) { token=cycle;since=null }
        if (cycle==null || !free) { since=null;return false }
        val start=since ?: now.also { since=it }
        return now-start>=1000L
    }
}

internal object NativeAudioRelease {
    private val gate=NativeAudioReleaseGate()
    private var last: String?=null
    private val proof=NativeAudioProof()
    fun freshProof(context:Context,token:String):Boolean = proof.valid(token,SystemClock.elapsedRealtime(),NativeAudioState.read(context).free)
    fun confirmed(context: Context,token: String): Boolean {
        val state=NativeAudioState.read(context)
        val now=SystemClock.elapsedRealtime()
        val ready=gate.observe(token,now,state.free)
        proof.record(token,now,ready)
        val metadata="recordings=${state.recordings ?: -1} normal=${state.normalMode} confirmed=$ready"
        if ("$token:$metadata"!=last) {
            last="$token:$metadata"
            NativeRuntimeSnapshot.save(context,token,"exit_audio",metadata)
        }
        return ready
    }
}

/** Recover resource failures without fighting Voice or undoing an explicit user stop. */
internal object WakeRecoveryPolicy {
    fun allowServiceStart(userStopped: Boolean,manualStart: Boolean) = !userStopped || manualStart
    fun canResume(screenAllowed: Boolean,cycleActive: Boolean,stopping: Boolean,wakePending: Boolean,
        now: Long,retryAt: Long,audioAvailable: Boolean) = screenAllowed && !cycleActive && !stopping &&
        !wakePending && now>=retryAt && audioAvailable
    fun retryDelay(failures: Int): Long = when (failures) { 0,1 -> 1000L;2 -> 2000L;else -> 5000L }
}

/** Reuse only a recent full one-second confirmation and a fresh free-resource observation. */
internal class NativeAudioProof {
    private var token:String?=null
    private var confirmedAt:Long?=null
    fun record(cycle:String,now:Long,confirmed:Boolean) { token=cycle;confirmedAt=if(confirmed) now else null }
    fun valid(cycle:String,now:Long,free:Boolean):Boolean {
        if(!free) {confirmedAt=null;return false}
        return token==cycle && confirmedAt?.let {now-it in 0..250L}==true
    }
}
internal object BridgePollPolicy {
    fun delay(voiceActive:Boolean,recentCommand:Boolean)=if(voiceActive || recentCommand) 200L else 2000L
}
