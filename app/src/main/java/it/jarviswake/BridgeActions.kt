package it.jarviswake

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal object BridgeActions {
    private val main=Handler(Looper.getMainLooper())
    fun <T> onMain(allowed:()->Boolean,action:()->T):T {
        val latch=CountDownLatch(1);val abandoned=AtomicBoolean(false)
        var value:T?=null;var failure:Throwable?=null
        main.post {
            try { if(abandoned.get() || !allowed()) throw BridgeFailure("cancelled");value=action() }
            catch(e:Throwable) { failure=e } finally { latch.countDown() }
        }
        if(!latch.await(2000,TimeUnit.MILLISECONDS)) { abandoned.set(true);throw BridgeFailure("outcome_uncertain") }
        failure?.let { throw it }
        @Suppress("UNCHECKED_CAST") return value as T
    }
    fun execute(c:Context,deviceId:String,command:JSONObject,deadline:Long,permitted:()->Boolean):JSONObject {
        val operation=command.getString("operation")
        if(operation in BridgeProtocol.NOTE_OPERATIONS) {
            fun notesAllowed()=permitted() && BridgeSettings.enabled(c) && PhoneNotes.enabled(c) &&
                SystemClock.elapsedRealtime()<deadline && !c.getSystemService(KeyguardManager::class.java).isKeyguardLocked
            if(!PhoneNotes.enabled(c)) return result("notes_disabled")
            if(c.getSystemService(KeyguardManager::class.java).isKeyguardLocked) return result("locked")
            if(!notesAllowed()) return result("expired_before_dispatch")
            return PhoneNotes.execute(c,operation,command.getJSONObject("params"),::notesAllowed)
        }
        fun allowed()=permitted() && BridgeSettings.enabled(c) && BridgeSettings.actionsEnabled(c) && SystemClock.elapsedRealtime()<deadline
        if(!BridgeSettings.actionsEnabled(c)) return result("actions_disabled")
        if(!allowed()) return result("expired_before_dispatch")
        val p=command.optJSONObject("params") ?: return result("unavailable")
        return try {
            when(command.getString("operation")) {
                "find_contacts" -> BridgeContacts.search(c,deviceId,p.getString("name"))
                "navigate" -> {
                    val destination=p.getString("destination")
                    if(!BridgeProtocol.validText(destination,300)) throw BridgeFailure("unavailable")
                    onMain(::allowed) {
                        unlocked(c)
                        if(VoiceCycle.active && (!NativeChatSettings.enabled(c) || VoiceCycle.phase!=VoiceCycleState.Phase.VOICE)) throw BridgeFailure("unsupported")
                        launch(c,Intent(Intent.ACTION_VIEW,Uri.parse("google.navigation:q="+Uri.encode(destination)+"&mode=d"))
                            .setPackage("com.google.android.apps.maps"))
                    }
                    result("intent_dispatched")
                }
                "call_contact" -> call(c,deviceId,command.getString("requestId"),p.getString("contact_ref"),deadline,::allowed)
                "whatsapp" -> {
                    val contact=BridgeContacts.resolve(c,deviceId,p.getString("contact_ref"))
                    val phone=contact.international ?: throw BridgeFailure("international_number_required")
                    val text=p.getString("text")
                    if(!BridgeProtocol.validText(text,2000)) throw BridgeFailure("unavailable")
                    val done=CountDownLatch(1);var outcome=result("outcome_uncertain")
                    onMain(::allowed) {
                        unlocked(c)
                        if(VoiceCycle.active && (!NativeChatSettings.enabled(c) || VoiceCycle.phase!=VoiceCycleState.Phase.VOICE)) throw BridgeFailure("unsupported")
                        val service=VoiceAccessibilityService.bridgeService() ?: throw BridgeFailure("unavailable")
                        WhatsAppFlow.start(service,contact,text,deadline,::allowed) { value -> outcome=value;done.countDown() }
                        try {
                            launch(c,Intent(Intent.ACTION_VIEW,Uri.parse("https://wa.me/"+phone.removePrefix("+"))).setPackage("com.whatsapp"))
                        } catch(e:Exception) { WhatsAppFlow.cancel();throw e }
                    }
                    if(done.await(minOf(12000L,maxOf(1,deadline-SystemClock.elapsedRealtime())),TimeUnit.MILLISECONDS)) outcome
                    else { main.post { WhatsAppFlow.cancel() };result("outcome_uncertain") }
                }
                else -> result("unsupported")
            }
        } catch(e:BridgeFailure) { result(e.code) }
        catch(_:SecurityException) { result("permission_required") }
        catch(_:Exception) { result("outcome_uncertain") }
    }
    private fun unlocked(c:Context) {
        if(c.getSystemService(KeyguardManager::class.java).isKeyguardLocked) throw BridgeFailure("locked")
    }
    internal fun launch(c:Context,intent:Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if(intent.resolveActivity(c.packageManager)==null) throw BridgeFailure("unavailable")
        // The already-bound accessibility service has Android's supported background-launch role.
        val service=VoiceAccessibilityService.bridgeService()
        VoiceAccessibilityService.releaseBridgeCover()
        (service ?: c).startActivity(intent)
    }
    private fun call(c:Context,deviceId:String,id:String,ref:String,deadline:Long,allowed:()->Boolean):JSONObject {
        if(c.checkSelfPermission(Manifest.permission.CALL_PHONE)!=PackageManager.PERMISSION_GRANTED ||
            c.checkSelfPermission(Manifest.permission.READ_PHONE_STATE)!=PackageManager.PERMISSION_GRANTED) throw BridgeFailure("permission_required")
        val contact=BridgeContacts.resolve(c,deviceId,ref)
        var dispatched=false
        try {
            onMain(allowed) {
                unlocked(c)
                if(VoiceCycle.active && !NativeChatSettings.enabled(c)) throw BridgeFailure("unsupported")
                BridgeCallHold.acquire(c,id)
                if(!VoiceAccessibilityService.stopBridgeVoice(c)) throw BridgeFailure("voice_stop_unconfirmed")
            }
            val release=NativeAudioReleaseGate()
            val stopDeadline=minOf(deadline,SystemClock.elapsedRealtime()+9000)
            var ready=false
            while(allowed() && SystemClock.elapsedRealtime()<stopDeadline) {
                ready=onMain(allowed) { !VoiceCycle.active && release.observe(id,SystemClock.elapsedRealtime(),NativeAudioState.read(c).free) }
                if(ready) break
                Thread.sleep(100)
            }
            if(!ready) return result(if(!allowed()) "expired_before_dispatch" else "voice_stop_unconfirmed")
            onMain(allowed) {
                unlocked(c)
                if(!NativeAudioState.read(c).free) throw BridgeFailure("audio_busy")
                launch(c,Intent(Intent.ACTION_CALL,Uri.fromParts("tel",contact.number,null)))
                dispatched=true
            }
            return result("call_intent_dispatched")
        } finally {
            if(!dispatched) main.post { BridgeCallHold.abort(c,id) }
        }
    }
}
