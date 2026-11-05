package it.jarviswake

import android.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal interface GoogleSpeechApi {
    fun openRecognition(onResult: (String?, Int) -> Unit): GoogleRecognitionStream
    fun synthesize(text: String): ByteArray
    fun cancel()
}

/** Public Google v1 APIs: streaming STT and REST TTS. Never redirects, logs or returns raw provider errors. */
internal class GoogleSpeechClient(private val config: GoogleVoiceConfig) : GoogleSpeechApi {
    @Volatile private var connection: HttpURLConnection? = null
    @Volatile private var cancelled = false
    @Volatile private var recognition: GoogleRecognitionStream? = null
    override fun cancel() { cancelled = true; connection?.disconnect(); recognition?.cancel() }
    private fun request(host: String, path: String, token: String, payload: JSONObject?, limit: Int): JSONObject {
        check(!cancelled) { "cancelled" }
        check(System.currentTimeMillis() < config.expiresAt) { "credential_expired" }
        val conn = URL("https://$host/v1/$path").openConnection() as HttpURLConnection
        connection = conn
        try {
            check(!cancelled) { "cancelled" }
            conn.instanceFollowRedirects = false; conn.connectTimeout = 10000; conn.readTimeout = 45000
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("x-goog-user-project", config.quotaProject)
            if (payload != null) {
                conn.requestMethod = "POST"; conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            }
            check(conn.responseCode == 200) { "google_http_${conn.responseCode}" }
            val bytes = conn.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(4096)
                while (true) {
                    check(!cancelled) { "cancelled" }
                    val n = input.read(buffer); if (n < 0) break
                    check(out.size() + n <= limit) { "response_too_large" }; out.write(buffer, 0, n)
                }
                out.toByteArray()
            }
            return JSONObject(String(bytes, Charsets.UTF_8))
        } finally { if (connection === conn) connection = null; conn.disconnect() }
    }
    override fun openRecognition(onResult: (String?, Int) -> Unit): GoogleRecognitionStream {
        check(!cancelled && System.currentTimeMillis()<config.expiresAt)
        return GoogleStreamingRecognizer(config,onResult).also { recognition=it; if(cancelled) it.cancel() }
    }
    override fun synthesize(text: String): ByteArray {
        require(text.toByteArray(Charsets.UTF_8).size in 1..4500)
        val reply = request("texttospeech.googleapis.com", "text:synthesize", config.ttsToken,
            JSONObject().put("input", JSONObject().put("text", text))
                .put("voice", JSONObject().put("languageCode", config.language).put("name", config.voice))
                .put("audioConfig", JSONObject().put("audioEncoding", "LINEAR16").put("sampleRateHertz", 24000)), 6000000)
        val wav = Base64.decode(reply.getString("audioContent"), Base64.DEFAULT)
        return try { GoogleWave.pcm(wav) } finally { wav.fill(0) }
    }
    /** Explicit settings action: catalogue lookup only; never a billable synthesis probe. */
    fun voiceAvailable(): Boolean {
        val voices = request("texttospeech.googleapis.com", "voices?languageCode=${config.language}", config.ttsToken, null, 1500000).getJSONArray("voices")
        return (0 until voices.length()).any { voices.getJSONObject(it).optString("name") == config.voice }
    }
}

internal object GoogleWave {
    fun pcm(wav: ByteArray): ByteArray {
        require(wav.size in 44..4500000)
        val b = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        fun ascii(at: Int, size: Int) = String(wav, at, size, Charsets.US_ASCII)
        require(ascii(0, 4) == "RIFF" && ascii(8, 4) == "WAVE")
        var offset = 12; var format = false
        while (offset + 8 <= wav.size) {
            val n = b.getInt(offset + 4); require(n >= 0 && n <= wav.size - offset - 8)
            if (ascii(offset, 4) == "fmt ") {
                require(n >= 16 && b.getShort(offset + 8).toInt() == 1 && b.getShort(offset + 10).toInt() == 1 &&
                    b.getInt(offset + 12) == 24000 && b.getShort(offset + 22).toInt() == 16)
                format = true
            }
            if (ascii(offset, 4) == "data") { require(format && n > 0 && n % 2 == 0); return wav.copyOfRange(offset + 8, offset + 8 + n) }
            offset += 8 + n + n % 2
        }
        error("invalid_wave")
    }
}
