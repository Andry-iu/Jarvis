package it.jarviswake

import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** One bounded authenticated HTTP stream, reused for successive commands during a Voice session.
 * Transport renewal does not renew a command, claim, journal, or operation permission. */
internal class BridgeLiveChannel(
    private val credentials:BridgeCredentials,
    private val versionCode:Long,
    private val connect:(URL)->HttpURLConnection={it.openConnection() as HttpURLConnection},
    private val clock:()->Long=SystemClock::elapsedRealtime,
    private val onReady:()->Unit={}
) {
    data class Delivery(val request:JSONObject,val receivedAt:Long,val transitBudget:Long)
    @Volatile private var closed=false
    @Volatile private var connection:HttpURLConnection?=null
    private var input:BufferedInputStream?=null
    private var readyServer=0L
    private var readyLocal=0L
    private var handshake=0L
    val isClosed get()=closed

    fun close() {closed=true;connection?.disconnect()}

    private fun open() {
        check(BridgeProtocol.validOrigin(credentials.origin))
        if(closed) throw IOException("live_closed")
        val started=clock()
        val conn=connect(URL(credentials.origin+"/device/live"));connection=conn
        if(closed){conn.disconnect();throw IOException("live_closed")}
        conn.requestMethod="POST";conn.instanceFollowRedirects=false
        conn.connectTimeout=4000;conn.readTimeout=15000;conn.doOutput=true
        conn.setRequestProperty("Content-Type","application/json")
        conn.setRequestProperty("Accept","application/x-ndjson")
        conn.setRequestProperty("Authorization","Bearer ${credentials.deviceToken}")
        conn.setRequestProperty("OAI-Sites-Authorization","Bearer ${credentials.siteAccess}")
        val body=JSONObject().put("deviceId",credentials.deviceId).put("protocol",1).put("versionCode",versionCode)
            .put("operations",JSONArray(BridgeProtocol.OPERATIONS.toList())).toString().toByteArray(Charsets.UTF_8)
        conn.setFixedLengthStreamingMode(body.size)
        conn.outputStream.use {it.write(body)}
        if(conn.responseCode!=200 || conn.contentType?.substringBefore(';')!="application/x-ndjson") {
            close();throw IOException("live_unavailable")
        }
        if(closed) throw IOException("live_closed")
        input=BufferedInputStream(conn.inputStream)
        handshake=(clock()-started).coerceAtLeast(0)
    }

    fun next():Delivery? {
        if(input==null) open()
        while(!closed) {
            val bytes=ByteArrayOutputStream()
            while(true) {
                val ch=input!!.read()
                if(closed) throw IOException("live_closed")
                if(ch<0) {close();if(bytes.size()!=0)throw IOException("live_truncated");return null}
                if(ch==10) break
                if(bytes.size()>=24*1024+256) {close();throw IOException("live_frame_too_large")}
                bytes.write(ch)
            }
            val frame=JSONObject(bytes.toString("UTF-8"))
            when(frame.getString("type")) {
                "ready" -> {
                    check(readyServer==0L && frame.getInt("protocol")==1 && frame.getLong("serverNow")>0)
                    readyServer=frame.getLong("serverNow");readyLocal=clock();onReady()
                }
                "heartbeat" -> check(readyServer>0)
                "request" -> {
                    check(readyServer>0)
                    val received=clock();val sent=frame.getLong("sentAt")
                    check(sent>=readyServer)
                    val command=frame.getJSONObject("request")
                    check(BridgeProtocol.validRequest(command.optString("requestId"),command.optString("operation"),
                        command.optLong("createdAt"),command.optLong("expiresAt")))
                    // Relative server time + monotonic local clock; no dependency on phone wall time.
                    val transit=(received-readyLocal+handshake-(sent-readyServer)).coerceAtLeast(0)
                    return Delivery(command,received,transit)
                }
                else -> {close();throw IOException("live_invalid_frame")}
            }
        }
        throw IOException("live_closed")
    }
}
