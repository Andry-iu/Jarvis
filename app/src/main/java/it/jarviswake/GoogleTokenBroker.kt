package it.jarviswake

import android.os.SystemClock
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** One cycle, one pairing snapshot. Tokens are RAM-only and never handed to the model or diagnostics. */
internal interface GoogleCredentialSession {
    fun resolve(config: GoogleVoiceConfig, minimumMs: Long = 180000): GoogleVoiceConfig
    fun cancel()
}
internal class GoogleTokenBroker(
    private val pairing: BridgeCredentials,
    private val transport: GoogleBrokerTransport = GoogleBrokerHttp(),
    private val elapsed: () -> Long = { SystemClock.elapsedRealtime() }
) : GoogleCredentialSession {
    private var token = ""
    private var project = ""
    private var validUntil = 0L
    @Volatile private var cancelled = false
    @Synchronized override fun resolve(config: GoogleVoiceConfig, minimumMs: Long): GoogleVoiceConfig {
        check(!cancelled)
        require(config.authMode == "backend" && minimumMs in 0..300000)
        if (project != config.quotaProject || elapsed() + minimumMs >= validUntil) {
            val started = elapsed()
            val reply = transport.post(pairing, JSONObject().put("deviceId", pairing.deviceId).put("project", config.quotaProject))
            check(!cancelled)
            require(reply.keys().asSequence().toSet() == setOf("accessToken", "expiresIn", "project"))
            val received = reply.getString("accessToken")
            val seconds = reply.getLong("expiresIn")
            require(received.matches(Regex("[A-Za-z0-9._~+/-]{20,4096}={0,2}")) && seconds in 240..3600 && reply.getString("project") == config.quotaProject)
            val until = started + seconds * 1000 - 30000
            check(until > elapsed() + minimumMs)
            token = received; project = config.quotaProject; validUntil = until
        }
        check(!cancelled)
        return config.copy(sttToken = token, ttsToken = token, expiresAt = System.currentTimeMillis() + validUntil - elapsed())
    }
    override fun cancel() { cancelled = true; transport.cancel() }
    override fun toString() = "GoogleTokenBroker(redacted)"
}
internal interface GoogleBrokerTransport {
    fun post(pairing: BridgeCredentials, body: JSONObject): JSONObject
    fun cancel()
}
internal class GoogleBrokerHttpFailure(val status:Int):Exception()
internal class GoogleBrokerHttp : GoogleBrokerTransport {
    @Volatile private var connection: HttpURLConnection? = null
    @Volatile private var cancelled = false
    override fun cancel() { cancelled = true; connection?.disconnect() }
    override fun post(pairing: BridgeCredentials, body: JSONObject): JSONObject {
        check(!cancelled && BridgeProtocol.validOrigin(pairing.origin))
        val conn = URL(pairing.origin + "/device/google-token").openConnection() as HttpURLConnection
        connection = conn
        try {
            check(!cancelled)
            conn.requestMethod = "POST"; conn.instanceFollowRedirects = false
            conn.connectTimeout = 10000; conn.readTimeout = 16000; conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer ${pairing.deviceToken}")
            conn.setRequestProperty("OAI-Sites-Authorization", "Bearer ${pairing.siteAccess}")
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            if(conn.responseCode!=200) throw GoogleBrokerHttpFailure(conn.responseCode)
            val result = conn.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(2048)
                while (true) {
                    check(!cancelled)
                    val size = input.read(buffer); if (size < 0) break
                    check(out.size() + size <= 16384); out.write(buffer, 0, size)
                }
                JSONObject(out.toString("UTF-8"))
            }
            check(!cancelled); return result
        } finally { if (connection === conn) connection = null; conn.disconnect() }
    }
}
