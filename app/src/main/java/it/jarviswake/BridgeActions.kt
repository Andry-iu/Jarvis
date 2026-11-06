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
    fun execute(c:Context,deviceId:String,command:JSONObject,deadline:Long,progress:(String)->Unit = {},permitted:()->Boolean):JSONObject {
        val operation=command.getString("operation")
        if (operation == "speak") {
            if (!BridgeSettings.enabled(c) || !permitted()) return result("cancelled")
            return GoogleVoiceActivity.speak(command, deadline, permitted, progress)
        }
        if(operation in BridgeProtocol.FAVORITE_OPERATIONS) {
            fun favoritesAllowed()=permitted() && BridgeSettings.enabled(c) && BridgeContactFavorites.remoteEnabled(c) &&
                SystemClock.elapsedRealtime()<deadline && !c.getSystemService(KeyguardManager::class.java).isKeyguardLocked
            if(!BridgeContactFavorites.remoteEnabled(c)) return result("favorites_disabled")
            if(c.getSystemService(KeyguardManager::class.java).isKeyguardLocked) return result("locked")
            if(!favoritesAllowed()) return result("expired_before_dispatch")
            return FavoriteOperations.execute(c,deviceId,operation,command.getJSONObject("params"),::favoritesAllowed)
        }
        if(operation in BridgeProtocol.CONTACT_EXPORT_OPERATIONS) {
            val id=command.optString("requestId")
            val rejection=java.util.concurrent.atomic.AtomicBoolean(false)
            fun exportAllowed():Boolean {
                val reason=when {
                    !permitted() -> ContactExportDiagnostics.Reason.GENERATION_CHANGED
                    !BridgeSettings.enabled(c) -> ContactExportDiagnostics.Reason.LINK_DISABLED
                    !PhoneContactExport.enabled(c) -> ContactExportDiagnostics.Reason.COPY_DISABLED
                    c.checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED -> ContactExportDiagnostics.Reason.CONTACT_PERMISSION
                    SystemClock.elapsedRealtime()>=deadline -> ContactExportDiagnostics.Reason.DEADLINE
                    c.getSystemService(KeyguardManager::class.java).isKeyguardLocked -> ContactExportDiagnostics.Reason.SCREEN_LOCKED
                    else -> null
                }
                if(reason!=null && rejection.compareAndSet(false,true)) ContactExportDiagnostics.record(c,id,ContactExportDiagnostics.Stage.GUARD_REJECTED,reason=reason)
                return reason==null
            }
            if(c.getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
                ContactExportDiagnostics.record(c,id,ContactExportDiagnostics.Stage.GUARD_REJECTED,reason=ContactExportDiagnostics.Reason.SCREEN_LOCKED)
                return result("locked")
            }
            return PhoneContactExport.execute(c,deviceId,operation,command.getJSONObject("params"),requestId=id,progress=progress,allowed=::exportAllowed)
        }
        if(operation in BridgeProtocol.NOTE_OPERATIONS) {
            fun notesAllowed()=permitted() && BridgeSettings.enabled(c) && PhoneNotes.enabled(c) &&
                SystemClock.elapsedRealtime()<deadline && !c.getSystemService(KeyguardManager::class.java).isKeyguardLocked
            if(!PhoneNotes.enabled(c)) return result("notes_disabled")
            if(c.getSystemService(KeyguardManager::class.java).isKeyguardLocked) return result("locked")
            if(!notesAllowed()) return result("expired_before_dispatch")
            return PhoneNotes.execute(c,operation,command.getJSONObject("params"),::notesAllowed)
        }
        // The dedicated read-only consent must suffice to select a number for export;
        // it grants no navigate/call/WhatsApp permission and leaves the old actions path intact.
        if(operation=="find_contacts" && !BridgeSettings.actionsEnabled(c) && PhoneContactExport.enabled(c)) {
            fun searchAllowed()=permitted() && BridgeSettings.enabled(c) && PhoneContactExport.enabled(c) &&
                SystemClock.elapsedRealtime()<deadline && !c.getSystemService(KeyguardManager::class.java).isKeyguardLocked
            if(c.getSystemService(KeyguardManager::class.java).isKeyguardLocked) return result("locked")
            if(!searchAllowed()) return result("cancelled")
            return try {
                val found=BridgeContacts.search(c,deviceId,command.getJSONObject("params").getString("name"),command.getJSONObject("params").optInt("offset",0))
                if(searchAllowed()) found else result("cancelled")
            } catch(e:BridgeFailure) { result(e.code) }
            catch(_:SecurityException) { result("permission_required") }
            catch(_:Exception) { result("unavailable") }
        }
        fun allowed()=permitted() && BridgeSettings.enabled(c) && BridgeSettings.actionsEnabled(c) && SystemClock.elapsedRealtime()<deadline
        if(!BridgeSettings.actionsEnabled(c)) return result("actions_disabled")
        if(!allowed()) return result("expired_before_dispatch")
        val p=command.optJSONObject("params") ?: return result("unavailable")
        return try {
            when(command.getString("operation")) {
                "find_contacts" -> BridgeContacts.search(c,deviceId,p.getString("name"),p.optInt("offset",0))
                "navigate" -> {
                    val destination=p.getString("destination")
                    if(!BridgeProtocol.validText(destination,300)) throw BridgeFailure("unavailable")
                    onMain(::allowed) {
                        unlocked(c)
                        if(VoiceCycle.active && (VoiceCycle.phase!=VoiceCycleState.Phase.VOICE || (!VoiceCycle.googleMode && !NativeChatSettings.enabled(c)))) throw BridgeFailure("unsupported")
                        launch(c,Intent(Intent.ACTION_VIEW,Uri.parse("google.navigation:q="+Uri.encode(destination)+"&mode=d"))
                            .setPackage("com.google.android.apps.maps"),keepCover=BridgeCarConnection.keepMapsCover())
                    }
                    result("intent_dispatched")
                }
                "call_contact" -> call(c,deviceId,command.getString("requestId"),p.getString("contact_ref"),deadline,::allowed)
                "call_by_name" -> {
                    unlocked(c)
                    val timing=BridgeCallTiming(c,command.getString("requestId"))
                    timing.stage("received")
                    val contacts=BridgeContacts.directRef(c,deviceId,p.getString("name"),timing)
                    val rows=contacts.getJSONArray("contacts")
                    if(rows.length()!=1 || contacts.optBoolean("truncated")) contacts
                    else call(c,deviceId,command.getString("requestId"),rows.getJSONObject(0).getString("contact_ref"),deadline,::allowed,timing)
                }
                "whatsapp" -> {
                    val contact=BridgeContacts.resolve(c,deviceId,p.getString("contact_ref"))
                    val phone=contact.international ?: throw BridgeFailure("international_number_required")
                    val text=p.getString("text")
                    if(!BridgeProtocol.validText(text,2000)) throw BridgeFailure("unavailable")
                    val original=onMain(::allowed) {
                        unlocked(c)
                        if(VoiceCycle.active && !VoiceCycle.googleMode && NativeChatSettings.enabled(c) && !VoiceAccessibilityService.showBridgeVoiceCover()) throw BridgeFailure("cancelled")
                        VoiceCycle.id
                    }
                    PhoneWhatsApp.send(c,phone,text,command.getString("requestId"),deadline) {
                        onMain(::allowed) { unlocked(c);allowed() && (original==null || (VoiceCycle.matches(original) && VoiceAccessibilityService.pendingNativeExit(c)!=original)) }
                    }
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
    internal fun launch(c:Context,intent:Intent,keepCover:Boolean=false) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if(intent.resolveActivity(c.packageManager)==null) throw BridgeFailure("unavailable")
        // The already-bound accessibility service has Android's supported background-launch role.
        val service=VoiceAccessibilityService.bridgeService()
        if(VoiceCycle.active && VoiceCycle.googleMode) GoogleVoiceActivity.prepareAppLaunch(keepCover)
        else if(!keepCover) VoiceAccessibilityService.releaseBridgeCover()
        (service ?: c).startActivity(intent)
    }
    internal fun chatGptResumeIntent(c:Context):Intent {
        val intent=c.packageManager.getLaunchIntentForPackage(NativeChatSettings.PACKAGE) ?: throw BridgeFailure("unavailable")
        val destructive=Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
        intent.flags=(intent.flags or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT) and destructive.inv()
        return intent
    }
    private fun call(c:Context,deviceId:String,id:String,ref:String,deadline:Long,allowed:()->Boolean,timing:BridgeCallTiming=BridgeCallTiming(c,id)):JSONObject {
        timing.stage("received")
        if(c.checkSelfPermission(Manifest.permission.CALL_PHONE)!=PackageManager.PERMISSION_GRANTED ||
            c.checkSelfPermission(Manifest.permission.READ_PHONE_STATE)!=PackageManager.PERMISSION_GRANTED) throw BridgeFailure("permission_required")
        val contact=BridgeContacts.resolve(c,deviceId,ref)
        timing.stage("contact_validated")
        var original:String?=null
        var localGoogle=false
        var dispatched=false
        try {
            onMain(allowed) {
                unlocked(c)
                localGoogle=VoiceCycle.active && VoiceCycle.googleMode
                if(VoiceCycle.active && !localGoogle && !NativeChatSettings.enabled(c)) throw BridgeFailure("unsupported")
                original=VoiceCycle.id
                BridgeCallHold.acquire(c,id)
                if(localGoogle) GoogleVoiceActivity.stopForTelephone(c)
                BridgeCallPreparation.begin(c,id)
                if(VoiceCycle.active && !localGoogle) {
                    val service=VoiceAccessibilityService.bridgeService() ?: throw BridgeFailure("voice_stop_unconfirmed")
                    service.startActivity(chatGptResumeIntent(c))
                    timing.stage("foreground_requested")
                }
                if(!localGoogle && !VoiceAccessibilityService.stopBridgeVoice(c)) throw BridgeFailure("voice_stop_unconfirmed")
                timing.stage("stop_requested")
            }
            val release=NativeAudioReleaseGate()
            val stopDeadline=minOf(deadline,SystemClock.elapsedRealtime()+12000)
            var ready=false
            while(allowed() && BridgeCallPreparation.allowed(id) && SystemClock.elapsedRealtime()<stopDeadline) {
                ready=onMain(allowed) {
                    if(VoiceCycle.active || GoogleVoiceActivity.audioBusy()) false else {
                        timing.stage("cycle_closed")
                        val reused=!localGoogle && original?.let {NativeAudioRelease.freshProof(c,it)}==true
                        if(reused) timing.stage("audio_proof_reused")
                        reused || release.observe(id,SystemClock.elapsedRealtime(),NativeAudioState.read(c).free)
                    }
                }
                if(ready) break
                Thread.sleep(100)
            }
            if(!ready) { timing.stage("stop_failed");return result(if(!BridgeCallPreparation.allowed(id)) "cancelled" else if(!allowed()) "expired_before_dispatch" else "voice_stop_unconfirmed") }
            timing.stage("audio_ready")
            onMain({allowed() && BridgeCallPreparation.allowed(id)}) {
                unlocked(c)
                val current=BridgeContacts.resolve(c,deviceId,ref)
                if(current.number!=contact.number) throw BridgeFailure("contact_changed")
                if(GoogleVoiceActivity.audioBusy() || !NativeAudioState.read(c).free) throw BridgeFailure("audio_busy")
                BridgeCallPreparation.finish(id)
                launch(c,Intent(Intent.ACTION_CALL,Uri.fromParts("tel",current.number,null)))
                dispatched=true
                timing.stage("call_intent_dispatched")
                runCatching {BridgeContactCache.remember(c,current)}.onFailure {timing.stage("cache_write_failed")}
            }
            return result("call_intent_dispatched")
        } finally {
            main.post { BridgeCallPreparation.finish(id);if(!dispatched) BridgeCallHold.abort(c,id) }
        }
    }
}

