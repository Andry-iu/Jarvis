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
    // Test transport replaces only the authenticated I/O boundary, never the worker/owner logic.
    internal var credentialsReader:(Context)->BridgeCredentials?=BridgeSettings::read
    internal var liveChannelFactory:((BridgeCredentials,Long)->BridgeLiveChannel)?=null
    internal var transport:((BridgeCredentials,String,JSONObject,Int)->JSONObject)?=null
    internal var commandReader:(Context)->JSONObject={BridgeSettings.readPrivate(it,"commands")}
    internal var commandWriter:(Context,JSONObject)->Unit={c,value->BridgeSettings.writePrivate(c,"commands",value)}
    private val main=Handler(Looper.getMainLooper())
    private val owners=HashSet<String>()
    private val actionLock=Object()
    @Volatile private var generation=0
    @Volatile private var worker:Thread?=null
    private val connections=java.util.concurrent.ConcurrentHashMap.newKeySet<HttpURLConnection>()
    @Volatile private var exportJob:Thread?=null
    @Volatile private var exportRequest:String?=null
    private val pollWake=Object()
    @Volatile private var liveWanted=false
    @Volatile private var liveRetryAt=0L
    @Volatile private var liveChannel:BridgeLiveChannel?=null
    @Volatile private var idlePoll:HttpURLConnection?=null
    private class LiveModeChanged:Exception()
    // Session-only channel. Closing it must never interrupt an in-flight action/outbox response.
    fun voiceSession(context:Context,active:Boolean) {
        check(Looper.myLooper()==Looper.getMainLooper())
        liveWanted=active
        if(active) {liveRetryAt=0;idlePoll?.disconnect()} else {liveChannel?.close();liveChannel=null}
        synchronized(pollWake){pollWake.notifyAll()}
        owner(context,"voice_live",active)
    }
    private fun useLive()=liveWanted && (transport==null || liveChannelFactory!=null) && SystemClock.elapsedRealtime()>=liveRetryAt
    private var fastUntil=0L
    @Volatile var status="Collegamento disattivato."
        private set
    fun owner(context:Context,id:String,active:Boolean) {
        check(Looper.myLooper()==Looper.getMainLooper())
        val changed=if(active) owners.add(id) else owners.remove(id)
        if(changed) NativeRuntimeSnapshot.save(context,"-","bridge_lifecycle",
            "event=owner_changed owners=${owners.size} dedicated=${"link_service" in owners} active=$active")
        // App switches during a command must not abort its response or repeat its action.
        if(owners.isNotEmpty() && BridgeSettings.enabled(context) && worker?.isAlive==true) return
        refresh(context)
    }
    fun serviceUnavailable(context:Context) {
        if(BridgeSettings.enabled(context)) status="Servizio collegamento non avviato da Android; riapri Abbinamento e stato."
        NativeRuntimeSnapshot.save(context,"-","bridge_lifecycle","event=service_start_rejected")
    }
    fun refresh(context:Context) {
        check(Looper.myLooper()==Looper.getMainLooper())
        ContactExportDiagnostics.record(context,exportRequest?.substringAfter(':'),ContactExportDiagnostics.Stage.WORKER_REFRESH,
            reason=ContactExportDiagnostics.Reason.GENERATION_CHANGED)
        generation++;liveChannel?.close();liveChannel=null;connections.forEach {it.disconnect()};worker?.interrupt()
        val epoch=generation
        if(owners.isEmpty() || !BridgeSettings.enabled(context)) {
            status=if(BridgeSettings.enabled(context)) "Collegamento abilitato; servizio collegamento non operativo." else "Collegamento disattivato."
            return
        }
        val app=context.applicationContext
        val credentials=runCatching { credentialsReader(app) }.getOrNull()
        if(credentials==null) { status="Collegamento da configurare.";return }
        status="Connessione al servizio…"
        NativeRuntimeSnapshot.save(app,"-","bridge_lifecycle","event=worker_started owners=${owners.size} dedicated=${"link_service" in owners}")
        worker=Thread({
            var failures=0
            var sessionChannel:BridgeLiveChannel?=null
            fun closeSessionChannel() {
                val old=sessionChannel;old?.close()
                if(liveChannel===old)liveChannel=null
                sessionChannel=null
            }
            while(epoch==generation && !Thread.currentThread().isInterrupted) {
                try {
                    val outboxReady=flushOutbox(app,credentials,epoch)
                    val info=app.packageManager.getPackageInfo(app.packageName,0)
                    val polledAt=SystemClock.elapsedRealtime()
                    var transitBudget=0L
                    val command=if(useLive()) {
                        try {
                            val channel=sessionChannel?.takeUnless {it.isClosed} ?: (liveChannelFactory?.invoke(credentials,info.longVersionCode) ?: BridgeLiveChannel(credentials,info.longVersionCode,onReady={
                                if(epoch==generation) {
                                    status="Canale Voice disponibile; attendo una richiesta."
                                    NativeRuntimeSnapshot.save(app,"-","bridge_transport","stage=live_ready")
                                }
                            })).also {sessionChannel=it;liveChannel=it}
                            val delivery=channel.next()
                            if(delivery==null) {closeSessionChannel();continue}
                            transitBudget=delivery.transitBudget+(SystemClock.elapsedRealtime()-delivery.receivedAt).coerceAtLeast(0)
                            delivery.request
                        } catch(e:Exception) {
                            closeSessionChannel()
                            if(epoch!=generation) break
                            if(!liveWanted) continue
                            liveRetryAt=SystemClock.elapsedRealtime()+60000L
                            NativeRuntimeSnapshot.save(app,"-","bridge_transport","stage=live_fallback code=unavailable")
                            continue
                        }
                    } else {
                        closeSessionChannel()
                        val reply=post(credentials,"/device/poll",JSONObject().put("deviceId",credentials.deviceId)
                            .put("versionCode",info.longVersionCode).put("operations",JSONArray(BridgeProtocol.OPERATIONS.toList())),epoch)
                        transitBudget=SystemClock.elapsedRealtime()-polledAt
                        reply.optJSONObject("request")
                    }
                    if(epoch!=generation) break
                    if(command!=null) {
                        var delivered=true
                        fastUntil=SystemClock.elapsedRealtime()+5000L
                        val id=command.optString("requestId");val op=command.optString("operation")
                        check(BridgeProtocol.validRequest(id,op,command.optLong("createdAt"),command.optLong("expiresAt")))
                        if(op in setOf("call_contact","call_by_name")) NativeRuntimeSnapshot.save(app,id,"bridge_transport",
                            "stage=delivered channel=${if(sessionChannel!=null) "live" else "poll"} transit_budget_ms=$transitBudget server_queue_ms=${(25000-command.optLong("remainingMs",25000)).coerceAtLeast(0)}")
                        if(op in BridgeProtocol.CONTACT_EXPORT_OPERATIONS) ContactExportDiagnostics.record(app,id,ContactExportDiagnostics.Stage.POLLED,
                            offset=command.optJSONObject("params")?.optInt("offset",-1),elapsedMs=transitBudget,
                            remainingMs=command.optLong("remainingMs",0))
                        if(op=="status") {
                            val ready=CountDownLatch(1);var snapshot:JSONObject?=null
                            main.post { try { if(epoch==generation) snapshot=capture(app) } finally { ready.countDown() } }
                            check(ready.await(1000,TimeUnit.MILLISECONDS))
                            val value=snapshot ?: error("state_unavailable")
                            post(credentials,"/device/respond",JSONObject().put("deviceId",credentials.deviceId)
                                .put("requestId",id).put("snapshot",value),epoch)
                        } else {
                            val remaining=BridgeProtocol.remainingLease(command.optLong("remainingMs",0),transitBudget)
                            val deadline=SystemClock.elapsedRealtime()+remaining
                            BridgeActions.onMain({epoch==generation}) { owner(app,"command",true) }
                            try {
                                synchronized(actionLock) { executeOnce(app,credentials,command,deadline,epoch) }
                                delivered=flushOutbox(app,credentials,epoch)
                            } finally { main.post { owner(app,"command",false) } }
                        }
                        if(epoch==generation) status=if(exportJob?.isAlive==true) "Importazione rubrica in corso."
                            else if(delivered) "Risposta consegnata al servizio; verifica l’esito in ChatGPT."
                            else "Risultato in attesa di consegna; collegamento ancora attivo."
                    } else if(epoch==generation) {
                        if(exportJob?.isAlive==true) status="Importazione rubrica in corso."
                        else if(!outboxReady) status="Risultato in attesa di consegna; collegamento ancora attivo."
                        else if(failures>0 || status=="Connessione al servizio…" || status=="Importazione rubrica in corso." || status=="Risultato in attesa di consegna; collegamento ancora attivo.") status="Collegamento disponibile; attendo una richiesta."
                    }
                    failures=0
                    if(!useLive()) synchronized(pollWake) {
                        if(!useLive()) pollWake.wait(BridgePollPolicy.delay(VoiceCycle.active,SystemClock.elapsedRealtime()<fastUntil))
                    }
                } catch(_:LiveModeChanged) { continue }
                catch(_:InterruptedException) { break }
                catch(error:Exception) {
                    if(epoch!=generation) break
                    if(useLive() && sessionChannel==null) continue
                    val code=when(error) {
                        is BridgeHttpFailure -> "http_${error.statusCode}"
                        is java.net.SocketTimeoutException -> "timeout"
                        is java.net.UnknownHostException -> "dns"
                        is javax.net.ssl.SSLException -> "tls"
                        is java.io.IOException -> "network"
                        else -> "protocol_or_storage"
                    }
                    failures++
                    status=when(code) {
                        "http_401","http_403" -> "Collegamento non autorizzato dal servizio; verifica l’abbinamento."
                        "protocol_or_storage" -> "Errore nel protocollo o archivio del collegamento; nuovo tentativo."
                        else -> "Rete o servizio remoto non disponibili; nuovo tentativo."
                    }
                    NativeRuntimeSnapshot.save(app,"-","bridge_transport","stage=retry code=$code failures=$failures")
                    try { Thread.sleep(if(failures<3) 3000 else 10000) } catch(_:InterruptedException) { break }
                }
            }
            closeSessionChannel()
            ContactExportDiagnostics.record(app,exportRequest?.substringAfter(':'),ContactExportDiagnostics.Stage.WORKER_STOPPED,
                reason=if(epoch!=generation) ContactExportDiagnostics.Reason.GENERATION_CHANGED else ContactExportDiagnostics.Reason.INTERRUPTED)
            NativeRuntimeSnapshot.save(app,"-","bridge_lifecycle","event=worker_stopped superseded=${epoch!=generation}")
        },"Jarvis-device-link").apply { isDaemon=true;start() }
    }
    private fun executeOnce(c:Context,auth:BridgeCredentials,command:JSONObject,deadline:Long,epoch:Int) {
        val id=command.getString("requestId");val key=auth.deviceId+":"+id
        val export=command.optString("operation") in BridgeProtocol.CONTACT_EXPORT_OPERATIONS
        val ledger=commandReader(c)
        val previous=ledger.optJSONObject(key)
        if(export && previous!=null) ContactExportDiagnostics.record(c,id,ContactExportDiagnostics.Stage.REPLAY,
            code=if(previous.has("result")) "saved" else "uncertain")
        if(BridgeReplayPolicy.decide(previous!=null,previous?.has("result")==true)==BridgeReplayPolicy.Decision.SAVED) return
        if(BridgeReplayPolicy.decide(previous!=null,false)==BridgeReplayPolicy.Decision.UNCERTAIN) {
            if(exportRequest==key && exportJob?.isAlive==true) return
            previous!!.put("result",result("outcome_uncertain"));commandWriter(c,ledger);return
        }
        while(ledger.length()>=64) ledger.remove(ledger.keys().asSequence().filter {it!=exportRequest}.minByOrNull { ledger.getJSONObject(it).optLong("at") }!!)
        val record=JSONObject().put("at",System.currentTimeMillis()).put("requestId",id).put("deviceId",auth.deviceId).put("acked",false).put("operation",command.getString("operation"))
        ledger.put(key,record)
        // Persist consumption before a network claim or any phone action. Crashes never re-dispatch.
        commandWriter(c,ledger)
        val outcome=try {
            val claimAt=SystemClock.elapsedRealtime()
            if(export) ContactExportDiagnostics.record(c,id,ContactExportDiagnostics.Stage.CLAIM_BEGIN,
                remainingMs=(deadline-claimAt).coerceIn(0L,25000L))
            val claim=post(auth,"/device/claim",JSONObject().put("deviceId",auth.deviceId).put("requestId",id),epoch)
            if(command.optString("operation") in setOf("call_contact","call_by_name")) NativeRuntimeSnapshot.save(c,id,"bridge_transport","stage=claim roundtrip_ms=${SystemClock.elapsedRealtime()-claimAt}")
            if(export) ContactExportDiagnostics.record(c,id,ContactExportDiagnostics.Stage.CLAIMED,code=claim.optString("state"),
                elapsedMs=SystemClock.elapsedRealtime()-claimAt)
            if(claim.optString("state")!="claimed") result("outcome_uncertain")
            else if(command.getString("operation")=="contacts_export_start") {
                if(exportJob?.isAlive==true) result("contacts_export_busy") else {
                    startExport(c,auth,command,epoch,key)
                    return
                }
            } else BridgeActions.execute(c,auth.deviceId,command,deadline,progress={ stage ->
                post(auth,"/device/progress",JSONObject().put("deviceId",auth.deviceId).put("requestId",id).put("stage",stage),epoch)
            }) { epoch==generation }
        } catch(e:Exception) {
            if(export) ContactExportDiagnostics.record(c,id,ContactExportDiagnostics.Stage.RESULT_READY,code="outcome_uncertain",
                reason=if(e is BridgeHttpFailure) ContactExportDiagnostics.Reason.HTTP else ContactExportDiagnostics.reason(e),
                httpStatus=(e as? BridgeHttpFailure)?.statusCode)
            result("outcome_uncertain")
        }
        record.put("result",outcome);ledger.put(key,record);commandWriter(c,ledger)
        if(export) ContactExportDiagnostics.record(c,id,ContactExportDiagnostics.Stage.RESULT_QUEUED,code=outcome.optString("code"))
    }
    /** Claimed export runs independently: no total-duration timeout, with live heartbeats. */
    private fun startExport(c:Context,auth:BridgeCredentials,command:JSONObject,epoch:Int,key:String) {
        val id=command.getString("requestId");val ownerId="export:$id"
        BridgeActions.onMain({epoch==generation}) {owner(c,ownerId,true)}
        exportRequest=key
        exportJob=Thread({
            val phase=java.util.concurrent.atomic.AtomicReference("starting")
            val active=java.util.concurrent.atomic.AtomicBoolean(true)
            val heartbeat=java.util.Timer("Jarvis-export-heartbeat",true)
            heartbeat.schedule(object:java.util.TimerTask(){override fun run(){
                if(!active.get() || epoch!=generation) return
                try {
                    post(auth,"/device/progress",JSONObject().put("deviceId",auth.deviceId).put("requestId",id).put("stage",phase.get()),epoch)
                    ContactExportDiagnostics.record(c,id,ContactExportDiagnostics.Stage.HEARTBEAT,phase=phase.get())
                } catch(e:Exception) {
                    ContactExportDiagnostics.record(c,id,ContactExportDiagnostics.Stage.HEARTBEAT,phase=phase.get(),
                        reason=if(e is BridgeHttpFailure) ContactExportDiagnostics.Reason.HTTP else ContactExportDiagnostics.reason(e),
                        httpStatus=(e as? BridgeHttpFailure)?.statusCode)
                    NativeRuntimeSnapshot.save(c,id,"bridge_transport","stage=export_heartbeat_deferred")
                }
            }},0,5000)
            val outcome=try {
                BridgeActions.execute(c,auth.deviceId,command,Long.MAX_VALUE,progress={phase.set(it)}) {epoch==generation}
            } catch(e:BridgeFailure) {result(e.code)} catch(_:Exception) {result("contacts_export_unavailable")}
            finally {active.set(false);heartbeat.cancel()}
            synchronized(actionLock) {
                val ledger=commandReader(c);val record=ledger.optJSONObject(key)
                if(record!=null && !record.has("result")) {record.put("result",outcome).put("completedAt",System.currentTimeMillis());commandWriter(c,ledger)}
                exportRequest=null
            }
            ContactExportDiagnostics.record(c,id,ContactExportDiagnostics.Stage.RESULT_QUEUED,code=outcome.optString("code"))
            main.post {owner(c,ownerId,false)}
        },"Jarvis-contact-export").apply {isDaemon=true;start()}
    }
    private fun flushOutbox(c:Context,auth:BridgeCredentials,epoch:Int):Boolean = synchronized(actionLock) {
        val ledger=commandReader(c)
        for(key in ledger.keys().asSequence().toList()) {
            val item=ledger.getJSONObject(key)
            if(item.optString("deviceId")!=auth.deviceId || item.optBoolean("acked") || !item.has("result")) continue
            try {
                val export=item.optString("operation") in BridgeProtocol.CONTACT_EXPORT_OPERATIONS
                val rejection=if(!export) null else when {
                    !PhoneContactExport.enabled(c) -> ContactExportDiagnostics.Reason.COPY_DISABLED
                    System.currentTimeMillis()-item.optLong("completedAt",item.optLong("at"))>120_000 -> ContactExportDiagnostics.Reason.RESULT_AGE
                    c.checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED -> ContactExportDiagnostics.Reason.CONTACT_PERMISSION
                    c.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked -> ContactExportDiagnostics.Reason.SCREEN_LOCKED
                    else -> null
                }
                if(rejection!=null) {
                    ContactExportDiagnostics.record(c,item.getString("requestId"),ContactExportDiagnostics.Stage.RESULT_REVOKED,
                        code=item.getJSONObject("result").optString("code"),reason=rejection)
                    item.put("result",result("contacts_export_expired"))
                }
                if(item.optString("operation") in BridgeProtocol.NOTE_OPERATIONS) {
                    val envelope=JSONObject().put("deviceId",auth.deviceId).put("requestId",item.getString("requestId")).put("result",item.getJSONObject("result"))
                    if(envelope.toString().toByteArray(Charsets.UTF_8).size>24*1024) item.put("result",result("note_payload_too_large"))
                }
                val sentAt=SystemClock.elapsedRealtime()
                if(export) ContactExportDiagnostics.record(c,item.getString("requestId"),ContactExportDiagnostics.Stage.SEND_BEGIN,
                    code=item.getJSONObject("result").optString("code"),offset=item.getJSONObject("result").optInt("offset",-1))
                post(auth,"/device/respond",JSONObject().put("deviceId",auth.deviceId).put("requestId",item.getString("requestId"))
                    .put("result",item.getJSONObject("result")),epoch)
                if(export) ContactExportDiagnostics.record(c,item.getString("requestId"),ContactExportDiagnostics.Stage.SEND_ACK,
                    code=item.getJSONObject("result").optString("code"),elapsedMs=SystemClock.elapsedRealtime()-sentAt)
                item.put("acked",true)
                if(item.optString("operation") in BridgeProtocol.CONTACT_EXPORT_OPERATIONS) item.put("result",result("contacts_export_expired"))
                commandWriter(c,ledger)
            } catch(e:BridgeHttpFailure) {
                if(item.optString("operation") in BridgeProtocol.CONTACT_EXPORT_OPERATIONS) ContactExportDiagnostics.record(c,item.getString("requestId"),
                    ContactExportDiagnostics.Stage.SEND_DEFERRED,reason=ContactExportDiagnostics.Reason.HTTP,httpStatus=e.statusCode)
                if(e.statusCode!=409) {
                    if(e.statusCode in setOf(401,403)) throw e
                    NativeRuntimeSnapshot.save(c,item.getString("requestId"),"bridge_transport","stage=outbox_deferred code=http_${e.statusCode}")
                    return@synchronized false
                }
                // Expired/never claimed: retain the consumed local record, never repeat the phone action.
                if(item.optString("operation") in BridgeProtocol.CONTACT_EXPORT_OPERATIONS) ContactExportDiagnostics.record(c,item.getString("requestId"),
                    ContactExportDiagnostics.Stage.RESULT_REVOKED,reason=ContactExportDiagnostics.Reason.HTTP,httpStatus=409)
                item.put("acked",true)
                if(item.optString("operation") in BridgeProtocol.CONTACT_EXPORT_OPERATIONS) item.put("result",result("contacts_export_expired"))
                commandWriter(c,ledger)
            } catch(e:java.io.IOException) {
                if(item.optString("operation") in BridgeProtocol.CONTACT_EXPORT_OPERATIONS) ContactExportDiagnostics.record(c,item.getString("requestId"),
                    ContactExportDiagnostics.Stage.SEND_DEFERRED,reason=ContactExportDiagnostics.reason(e))
                NativeRuntimeSnapshot.save(c,item.getString("requestId"),"bridge_transport","stage=outbox_deferred code=network")
                return@synchronized false
            }
        }
        true
    }
    private class BridgeHttpFailure(val statusCode:Int):Exception()
    private fun post(c:BridgeCredentials,path:String,data:JSONObject,epoch:Int):JSONObject {
        check(BridgeProtocol.validOrigin(c.origin))
        if(epoch!=generation) throw InterruptedException()
        transport?.let { return it(c,path,data,epoch) }
        if(path=="/device/poll" && useLive()) throw LiveModeChanged()
        val conn=URL(c.origin+path).openConnection() as HttpURLConnection;connections.add(conn)
        if(path=="/device/poll") idlePoll=conn
        try {
            if(epoch!=generation) throw InterruptedException()
            conn.requestMethod="POST";conn.instanceFollowRedirects=false
            conn.connectTimeout=4000;conn.readTimeout=15000;conn.doOutput=true
            conn.setRequestProperty("Content-Type","application/json")
            conn.setRequestProperty("Authorization","Bearer ${c.deviceToken}")
            conn.setRequestProperty("OAI-Sites-Authorization","Bearer ${c.siteAccess}")
            conn.outputStream.use { it.write(data.toString().toByteArray(Charsets.UTF_8)) }
            val status=conn.responseCode;if(status!=200) throw BridgeHttpFailure(status)
            val raw=conn.inputStream.use { input ->
                val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(2048)
                val maximum=if(path=="/device/poll") 24*1024 else 16*1024
                while(true) { val count=input.read(buffer);if(count<0) break;check(output.size()+count<=maximum);output.write(buffer,0,count) }
                output.toByteArray()
            }
            val reply=JSONObject(String(raw,Charsets.UTF_8))
            check(raw.size<=16*1024 || (path=="/device/poll" && reply.optJSONObject("request")?.optString("operation").orEmpty() in BridgeProtocol.NOTE_OPERATIONS))
            return reply
        } finally {if(idlePoll===conn)idlePoll=null;connections.remove(conn);conn.disconnect()}
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

