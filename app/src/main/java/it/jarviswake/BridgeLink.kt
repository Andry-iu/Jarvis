package it.jarviswake

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Optional status-only connection. Network waits never run on the gesture/audio threads. */
internal object BridgeLink {
    private val main=Handler(Looper.getMainLooper())
    private val owners=HashSet<String>() // Main looper only: existing service or visible configuration.
    @Volatile private var generation=0
    @Volatile private var worker:Thread?=null
    @Volatile private var connection:HttpURLConnection?=null
    @Volatile var status="Collegamento disattivato."
        private set
    fun owner(context:Context,id:String,active:Boolean) {
        check(Looper.myLooper()==Looper.getMainLooper())
        if(active) owners.add(id) else owners.remove(id)
        refresh(context)
    }
    fun refresh(context:Context) {
        check(Looper.myLooper()==Looper.getMainLooper())
        generation++
        connection?.disconnect()
        worker?.interrupt()
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
                    val reply=post(credentials,"/device/poll",JSONObject().put("deviceId",credentials.deviceId),epoch)
                    if(epoch!=generation) break
                    val command=reply.optJSONObject("request")
                    if(command!=null) {
                        val id=command.optString("requestId")
                        check(BridgeProtocol.validRequest(id,command.optString("operation"),command.optLong("createdAt"),command.optLong("expiresAt")))
                        val ready=CountDownLatch(1)
                        var snapshot:JSONObject?=null
                        main.post {
                            try { if(epoch==generation) snapshot=capture(app) } finally { ready.countDown() }
                        }
                        check(ready.await(1000,TimeUnit.MILLISECONDS))
                        val value=snapshot ?: error("state_unavailable")
                        if(epoch!=generation) break
                        post(credentials,"/device/respond",JSONObject().put("deviceId",credentials.deviceId)
                            .put("requestId",id).put("snapshot",value),epoch)
                        if(epoch==generation) status="Risposta di stato consegnata al servizio."
                    } else if(epoch==generation && failures>0) status="Collegamento disponibile; attendo una richiesta."
                    else if(epoch==generation && status=="Connessione al servizio…") status="Collegamento disponibile; attendo una richiesta."
                    failures=0
                    Thread.sleep(2000)
                } catch(_:InterruptedException) { break }
                catch(_:Exception) {
                    if(epoch!=generation) break
                    failures++
                    status="Servizio non raggiungibile o collegamento da verificare; nuovo tentativo."
                    try { Thread.sleep(if(failures<3) 3000 else 10000) } catch(_:InterruptedException) { break }
                }
            }
        },"Jarvis-status-link").apply { isDaemon=true;start() }
    }
    private fun post(c:BridgeCredentials,path:String,data:JSONObject,epoch:Int):JSONObject {
        check(BridgeProtocol.validOrigin(c.origin))
        val conn=URL(c.origin+path).openConnection() as HttpURLConnection
        connection=conn
        try {
            if(epoch!=generation) throw InterruptedException()
            conn.requestMethod="POST";conn.instanceFollowRedirects=false
            conn.connectTimeout=4000;conn.readTimeout=4000;conn.doOutput=true
            conn.setRequestProperty("Content-Type","application/json")
            conn.setRequestProperty("Authorization","Bearer ${c.deviceToken}")
            conn.setRequestProperty("OAI-Sites-Authorization","Bearer ${c.siteAccess}")
            conn.outputStream.use { it.write(data.toString().toByteArray(Charsets.UTF_8)) }
            check(conn.responseCode==200)
            val result=conn.inputStream.use { input ->
                val output=java.io.ByteArrayOutputStream()
                val buffer=ByteArray(2048)
                while(true) {
                    val count=input.read(buffer)
                    if(count<0) break
                    check(output.size()+count<=16384)
                    output.write(buffer,0,count)
                }
                output.toString("UTF-8")
            }
            return JSONObject(result)
        } finally { if(connection===conn) connection=null;conn.disconnect() }
    }
    private fun capture(c:Context):JSONObject {
        val info=c.packageManager.getPackageInfo(c.packageName,0)
        return JSONObject().put("app",JSONObject().put("version",info.versionName).put("versionCode",info.longVersionCode))
            .put("wake",WakeService.bridgeStatus())
            .put("voiceCycle",JSONObject().put("active",VoiceCycle.active).put("phase",if(VoiceCycle.active) VoiceCycle.phase.name else "IDLE")
                .put("hiddenForWake",VoiceCycle.hiddenForWake))
            .put("cover",JSONObject().put("accessibility",VoiceAccessibilityService.bridgeCoverVisible())
                .put("web",WebChatActivity.bridgeCoverVisible()).put("launch",LaunchActivity.bridgeCoverVisible()))
            .put("screenInteractive",c.getSystemService(PowerManager::class.java).isInteractive)
    }
}
