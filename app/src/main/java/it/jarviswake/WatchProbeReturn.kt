package it.jarviswake

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings

/** No Voice/audio action here. Only the user-prepared next Watch cycle may open our own probe page. */
internal object WatchProbeReturn {
    const val EXTRA="it.jarviswake.WATCH_PROBE_RETURN"
    private val main=Handler(Looper.getMainLooper())
    private val plan=WatchProbePlan()
    private var scheduled:String?=null
    private fun log(c:Context) = NativeRuntimeSnapshot.save(c,"watch_probe","watch_return","state=${plan.code}")
    fun prepare(c:Context):Boolean {
        if(VoiceCycle.active || !NativeChatSettings.enabled(c) || !WatchTickets.enabled(c) || !Settings.canDrawOverlays(c)) return false
        main.removeCallbacksAndMessages(null);scheduled=null;plan.prepare(SystemClock.elapsedRealtime());log(c);return true
    }
    fun cancel(c:Context) {main.removeCallbacksAndMessages(null);scheduled=null;plan.cancel();log(c)}
    fun describe():String {plan.expire(SystemClock.elapsedRealtime());return "Ritorno automatico prova: ${plan.code}"}
    fun bindWatch(c:Context,token:String?) {plan.bind(token,SystemClock.elapsedRealtime());log(c)}
    fun nativeVoiceObserved(service:VoiceAccessibilityService,token:String) {
        if(scheduled==token || !plan.waiting(token,SystemClock.elapsedRealtime())) return
        scheduled=token
        // Leave the existing greeting dispatch a brief foreground window. Revalidate everything afterwards.
        main.postDelayed({
            if(scheduled!=token) return@postDelayed
            scheduled=null
            val keyguard=service.getSystemService(KeyguardManager::class.java)
            val ready=VoiceAccessibilityService.bridgeService()===service && VoiceCycle.matches(token) && VoiceCycle.phase==VoiceCycleState.Phase.VOICE && NativeChatSettings.enabled(service) &&
                VoiceAccessibilityService.nativeVoiceObserved(service,token) && Settings.canDrawOverlays(service)
            if(!plan.consume(token,SystemClock.elapsedRealtime(),ready,VoiceAccessibilityService.pendingNativeExit(service)!=null,
                    !keyguard.isKeyguardLocked && !keyguard.isDeviceLocked)) {log(service);return@postDelayed}
            log(service)
            runCatching {service.startActivity(Intent(service,WatchProbeActivity::class.java).putExtra(EXTRA,token)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))}
            // startActivity has no acceptance result. The Activity must acknowledge its real onResume.
            main.postDelayed({if(plan.unconfirmed(token)) {
                log(service);State.setAttempt(service,"Ritorno alla prova non confermato. Swipe sinistro mostra ChatGPT mantenendo Voice; apri poi la notifica Prova audio Watch.")
                VoiceAccessibilityService.showCoverStatus(token,"PROVA NON APERTA — SWIPE SINISTRO PER LIBERARE LA COVER")
            }},5000)
        },2500)
    }
    fun acknowledge(c:Context,token:String?) {
        if(token!=null && VoiceCycle.matches(token) && VoiceAccessibilityService.pendingNativeExit(c)==null && plan.acknowledge(token)) log(c)
    }
    fun cycleEnded(c:Context,token:String) {plan.cycleEnded(token);log(c)}
}
