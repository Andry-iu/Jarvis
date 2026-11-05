package it.jarviswake

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Optional fixed-operation connection; waits never run on gesture/audio threads. */
internal object BridgeLink {
    private val main=Handler(Looper.getMainLooper())
    private val owners=HashSet<String>()
    private val actionLock=Object()
    @Volatile private var generation=0
    @Volatile private var worker:Thread?=null
    @Volatile private var connection:HttpURLConnection?=null
    private var fastUntil=0L
    @Volatile var status="Collegamento disattivato."
        private set
    fun owner(context:Context,id:String,active:Boolean) {
        check(Looper.myLooper()==Looper.getMainLooper())
        if(active) owners.add(id) else owners.remove(id)
        // App switches during a command must not abort its response or repeat its action.
        if(owners.isNotEmpty() && BridgeSettings.enabled(context) && worker?.isAlive==true) return
        refresh(context)
    }
    fun refresh(context:Context) {
        check(Looper.myLooper()==Looper.getMainLooper())
        generation++;connection?.disconnect();worker?.interrupt()
        val epoch=generation
        if(owners.isEmpty() || !BridgeSettings.enabled(context)) {
            status=if(BridgeSettings.enabled(context)) "Collegamento in attesa del servizio Jarvis." else "Collegamento disattivato."
            return
        }
        val app=context.applicationContext
        val credentials=runCatching { BridgeSettings.read(app) }.getOrNull()
        if(credentials==null) { status="Collegamento da configurare.";return }
        status="Connessione al servizio…"
        worker=Thread({
            var failures=0
            while(epoch==generation && !Thread.currentThread().isInterrupted) {
                try {
                    flushOutbox(app,credentials,epoch)
                    val info=app.packageManager.getPackageInfo(app.packageName,0)
                    val polledAt=SystemClock.elapsedRealtime()
                    val reply=post(credentials,"/device/poll",JSONObject().put("deviceId",credentials.deviceId)
                        .put("versionCode",info.longVersionCode).put("operations",JSONArray(BridgeProtocol.OPERATIONS.toList())),epoch)
                    if(epoch!=generation) break
                    val command=reply.optJSONObject("request")
                    if(command!=null) {
                        fastUntil=SystemClock.elapsedRealtime()+5000L
                        val id=command.optString("requestId");val op=command.optString("operation")
                        check(BridgeProtocol.validRequest(id,op,command.optLong("createdAt"),command.optLong("expiresAt")))
                        if(op in setOf("call_contact","call_by_name")) NativeRuntimeSnapshot.save(app,id,"bridge_transport",
                            "stage=polled roundtrip_ms=${SystemClock.elapsedRealtime()-polledAt} server_queue_ms=${(25000-command.optLong("remainingMs",25000)).coerceAtLeast(0)}")
                        if(op=="status") {
                            val ready=CountDownLatch(1);var snapshot:JSONObject?=null
                            main.post { try { if(epoch==generation) snapshot=capture(app) } finally { ready.countDown() } }
                            check(ready.await(1000,TimeUnit.MILLISECONDS))
                            val value=snapshot ?: error("state_unavailable")
                            post(credentials,"/device/respond",JSONObject().put("deviceId",credentials.deviceId)
                                .put("requestId",id).put("snapshot",value),epoch)
                        } else {
                            val remaining=BridgeProtocol.remainingLease(command.optLong("remainingMs",0),SystemClock.elapsedRealtime()-polledAt)
                            val deadline=SystemClock.elapsedRealtime()+remaining
                            BridgeActions.onMain({epoch==generation}) { owner(app,"command",true) }
                            try {
                                synchronized(actionLock) { executeOnce(app,credentials,command,deadline,epoch) }
                                flushOutbox(app,credentials,epoch)
                            } finally { main.post { owner(app,"command",false) } }
                        }
                        if(epoch==generation) status="Risposta consegnata al servizio; verifica l’esito in ChatGPT."
                    } else if(epoch==generation && (failures>0 || status=="Connessione al servizio…")) status="Collegamento disponibile; attendo una richiesta."
                    failures=0;Thread.sleep(BridgePollPolicy.delay(VoiceCycle.active,SystemClock.elapsedRealtime()<fastUntil))
                } catch(_:InterruptedException) { break }
                catch(_:Exception) {
                    if(epoch!=generation) break
                    failures++;status="Servizio non raggiungibile o collegamento da verificare; nuovo tentativo."
                    try { Thread.sleep(if(failures<3) 3000 else 10000) } catch(_:InterruptedException) { break }
                }
            }
        },"Jarvis-device-link").apply { isDaemon=true;start() }
    }
    private fun executeOnce(c:Context,auth:BridgeCredentials,command:JSONObject,deadline:Long,epoch:Int) {
        val id=command.getString("requestId");val key=auth.deviceId+":"+id
        val ledger=BridgeSettings.readPrivate(c,"commands")
        val previous=ledger.optJSONObject(key)
        if(BridgeReplayPolicy.decide(previous!=null,previous?.has("result")==true)==BridgeReplayPolicy.Decision.SAVED) return
        if(BridgeReplayPolicy.decide(previous!=null,false)==BridgeReplayPolicy.Decision.UNCERTAIN) {
            previous!!.put("result",result("outcome_uncertain"));BridgeSettings.writePrivate(c,"commands",ledger);return
        }
        while(ledger.length()>=64) ledger.remove(ledger.keys().asSequence().minByOrNull { ledger.getJSONObject(it).optLong("at") }!!)
        val record=JSONObject().put("at",System.currentTimeMillis()).put("requestId",id).put("deviceId",auth.deviceId).put("acked",false)
        ledger.put(key,record)
        // Persist consumption before a network claim or any phone action. Crashes never re-dispatch.
        BridgeSettings.writePrivate(c,"commands",ledger)
        val outcome=try {
            val claimAt=SystemClock.elapsedRealtime()
            val claim=post(auth,"/device/claim",JSONObject().put("deviceId",auth.deviceId).put("requestId",id),epoch)
            if(command.optString("operation") in setOf("call_contact","call_by_name")) NativeRuntimeSnapshot.save(c,id,"bridge_transport","stage=claim roundtrip_ms=${SystemClock.elapsedRealtime()-claimAt}")
            if(claim.optString("state")!="claimed") result("outcome_uncertain")
            else BridgeActions.execute(c,auth.deviceId,command,deadline,progress={ stage ->
                post(auth,"/device/progress",JSONObject().put("deviceId",auth.deviceId).put("requestId",id).put("stage",stage),epoch)
            }) { epoch==generation }
        } catch(_:Exception) { result("outcome_uncertain") }
        record.put("result",outcome);ledger.put(key,record);BridgeSettings.writePrivate(c,"commands",ledger)
    }
    private fun flushOutbox(c:Context,auth:BridgeCredentials,epoch:Int) = synchronized(actionLock) {
        val ledger=BridgeSettings.readPrivate(c,"commands")
        for(key in ledger.keys().asSequence().toList()) {
            val item=ledger.getJSONObject(key)
            if(item.optString("deviceId")!=auth.deviceId || item.optBoolean("acked") || !item.has("result")) continue
            try {
                post(auth,"/device/respond",JSONObject().put("deviceId",auth.deviceId).put("requestId",item.getString("requestId"))
                    .put("result",item.getJSONObject("result")),epoch)
                item.put("acked",true);BridgeSettings.writePrivate(c,"commands",ledger)
            } catch(e:BridgeHttpFailure) {
                if(e.statusCode!=409) throw e
                // Expired/never claimed: retain the consumed local record, never repeat the phone action.
                item.put("acked",true);BridgeSettings.writePrivate(c,"commands",ledger)
            }
        }
    }
    private class BridgeHttpFailure(val statusCode:Int):Exception()
    private fun post(c:BridgeCredentials,path:String,data:JSONObject,epoch:Int):JSONObject {
        check(BridgeProtocol.validOrigin(c.origin))
        val conn=URL(c.origin+path).openConnection() as HttpURLConnection;connection=conn
        try {
            if(epoch!=generation) throw InterruptedException()
            conn.requestMethod="POST";conn.instanceFollowRedirects=false
            conn.connectTimeout=4000;conn.readTimeout=4000;conn.doOutput=true
            conn.setRequestProperty("Content-Type","application/json")
            conn.setRequestProperty("Authorization","Bearer ${c.deviceToken}")
            conn.setRequestProperty("OAI-Sites-Authorization","Bearer ${c.siteAccess}")
            conn.outputStream.use { it.write(data.toString().toByteArray(Charsets.UTF_8)) }
            val status=conn.responseCode;if(status!=200) throw BridgeHttpFailure(status)
            return JSONObject(conn.inputStream.use { input ->
                val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(2048)
                while(true) { val count=input.read(buffer);if(count<0) break;check(output.size()+count<=16384);output.write(buffer,0,count) }
                output.toString("UTF-8")
            })
        } finally { if(connection===conn) connection=null;conn.disconnect() }
    }
    private fun capture(c:Context):JSONObject {
        val info=c.packageManager.getPackageInfo(c.packageName,0)
        fun permission(name:String)=c.checkSelfPermission(name)==PackageManager.PERMISSION_GRANTED
        return JSONObject().put("app",JSONObject().put("version",info.versionName).put("versionCode",info.longVersionCode))
            .put("wake",WakeService.bridgeStatus())
            .put("voiceCycle",JSONObject().put("active",VoiceCycle.active).put("phase",if(VoiceCycle.active) VoiceCycle.phase.name else "IDLE")
                .put("hiddenForWake",VoiceCycle.hiddenForWake))
            .put("cover",JSONObject().put("accessibility",VoiceAccessibilityService.bridgeCoverVisible())
                .put("web",WebChatActivity.bridgeCoverVisible()).put("launch",LaunchActivity.bridgeCoverVisible()))
            .put("permissions",JSONObject().put("actionsEnabled",BridgeSettings.actionsEnabled(c)).put("contacts",permission(Manifest.permission.READ_CONTACTS))
                .put("callPhone",permission(Manifest.permission.CALL_PHONE)).put("phoneState",permission(Manifest.permission.READ_PHONE_STATE))
                .put("notesEnabled",PhoneNotes.enabled(c)).put("favoritesEnabled",BridgeContactFavorites.remoteEnabled(c)))
            .put("screenInteractive",c.getSystemService(PowerManager::class.java).isInteractive)
    }
}
