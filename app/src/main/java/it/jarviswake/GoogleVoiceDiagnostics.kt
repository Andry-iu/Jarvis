package it.jarviswake

import android.content.Context
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.time.Instant

/** Whitelisted technical metadata only; never raw DOM, UI status text, provider errors, URLs or secrets. */
internal object GoogleVoiceDiagnostics {
    const val SNAPSHOT="google-state.json"
    private val codes=setOf("entry","ready","closed","draft","busy","restart_ready","wrong_conversation","old_session","project_has_messages","ambiguous_editor","approval_or_dialog","unverified_conversation","written","waiting_send","dispatched","waiting_observation","observed","invalid","ledger_lost","write_failed","write_uncertain","draft_changed","uncertain","send_uncertain","session_limit","started","completed","failed","cancelled","saved","capture_timeout","capture_unavailable","secure_window","accessibility_missing","target_unverified","accepted","synthesized","playback_started","speech_completed","synthesis_failed","authentication_failed","playback_failed","audio_focus_denied","interrupted","no_speech","config_missing","config_invalid","microphone_denied","bridge_disabled","status_changed","draft_present","listening","waiting_reply","transcribing","submitting","synthesizing","error","echo_ready","echo_unavailable","route_changed","capture_released","provider_status","echo_rejected","microphone_unavailable","microphone_silenced","gallery_failed","gallery_unavailable","image_too_large","invalid_size","window_not_allowed","pixel_unavailable","memory_unavailable","display_unavailable")
    private val stages=setOf("session","configuration","page","pre","post","send","auth","capture","stt","reply","tts","playback","error","screenshot","barge","snapshot","status")
    private val boolKeys=setOf("project_opened","conversation_bound","pre_sending","pre_complete","startup_complete","capture_present","capture_released","playback_present","playback_released","muted","cover_hidden","bridge_enabled","bridge_configured","google_enabled","cycle_active","google_cycle","config_present","manual_auth","backend_auth","pre_enabled","post_enabled","hasDraft","draftOwned","ledger","sendDispatched","editorEnabled","echo_ready","car_projection","projection_known","sameEditor","sameForm","originalEditorConnected","currentEditorConnected","textMatches","lineEndingsMatch","normalizedTextMatches","writeVerified","trustedInputSeen","inputObservationAvailable","contentEditable")
    private val numberKeys=setOf("editors","draftChars","buttons","samples","characters","http","record_route","play_route","grpc","expectedChars","draftLineBreaks","expectedLineBreaks")
    private var state=JSONObject()
    private var last=""
    private fun clean(raw:JSONObject):JSONObject=JSONObject().apply {
        for(k in boolKeys) if(raw.opt(k) is Boolean)put(k,raw.getBoolean(k))
        for(k in numberKeys) if(raw.opt(k) is Number)put(k,raw.getLong(k).coerceIn(0,10_000_000))
    }
    @Synchronized fun record(c:Context,token:String,stage:String,code:String,metadata:JSONObject=JSONObject()) {
        val s=stage.takeIf {it in stages} ?: "error"
        val v=code.takeIf {it in codes || it.matches(Regex("(?:pixel|display)_error_[0-9]{1,2}"))} ?: "failed"
        val m=clean(metadata);val key="$token/$s/$v/$m";if(last==key)return;last=key
        val time=Instant.now().toString();state.put("time",time).put("stage",s).put("code",v).put("cycle",token)
        val line="$time cycle=$token stage=$s code=$v metadata=$m\n"
        val app=c.applicationContext
        DiagnosticLog.afterPendingWrites {runCatching {
            val dir=File(app.filesDir,"diagnostics").apply{mkdirs()};val file=File(dir,"google-voice-0.log")
            if(file.length()+line.length>128*1024) {File(dir,"google-voice-1.log").delete();file.renameTo(File(dir,"google-voice-1.log"))}
            file.appendText(line)
        }}
    }
    fun dom(c:Context,token:String,stage:String,raw:String) {
        val j=runCatching{JSONObject(JSONTokener(raw).nextValue() as String)}.getOrNull() ?: JSONObject()
        record(c,token,stage,j.optString("phase"),j.optJSONObject("diagnostics") ?: JSONObject())
    }
    @Synchronized fun snapshot(c:Context,token:String,metadata:JSONObject) {
        state.put("cycle",token).put("runtime",clean(metadata));exportSnapshot(c)
    }
    @Synchronized fun exportSnapshot(c:Context) {
        val config=runCatching {GoogleVoiceSettings.read(c)}.getOrNull()
        val info=runCatching {c.packageManager.getPackageInfo(c.packageName,0)}.getOrNull()
        val copy=JSONObject(state.toString()).put("exportedAt",Instant.now().toString()).put("appVersion",info?.versionName ?: "unknown")
            .put("configuration",JSONObject().put("google_enabled",GoogleVoiceSettings.enabled(c)).put("cycle_active",VoiceCycle.active)
                .put("google_cycle",VoiceCycle.googleMode).put("bridge_enabled",BridgeSettings.enabled(c)).put("bridge_configured",BridgeSettings.configured(c))
                .put("config_present",config!=null).put("backend_auth",config?.authMode=="backend").put("manual_auth",config?.authMode=="manual")
                .put("pre_enabled",OpeningMessageSettings.get(c).isNotBlank()).put("post_enabled",VoiceGreetingSettings.get(c).isNotBlank()))
        val app=c.applicationContext
        DiagnosticLog.afterPendingWrites {runCatching {File(app.filesDir,"diagnostics/$SNAPSHOT").apply{parentFile?.mkdirs()}.writeText(copy.toString(2))}}
    }
}
