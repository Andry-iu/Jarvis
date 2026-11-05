package it.jarviswake

import android.content.Context
import org.json.JSONObject

internal data class GoogleVoiceConfig(
    val sttToken: String, val ttsToken: String, val expiresAt: Long, val quotaProject: String,
    val conversation: String, val language: String = "it-IT", val voice: String = "it-IT-Chirp3-HD-Enceladus",
    val silenceMs: Int = 1200, val maximumMs: Int = 50000, val threshold: Int = 600, val authMode: String = "manual"
) {
    fun validate() {
        require(authMode in setOf("manual", "backend"))
        require(listOf(sttToken, ttsToken).all { (authMode == "backend" && it.isEmpty()) || it.matches(Regex("[A-Za-z0-9._~+/-]{20,4096}={0,2}")) })
        require(authMode == "backend" || expiresAt > System.currentTimeMillis() && expiresAt <= System.currentTimeMillis() + 3600000L)
        require(quotaProject.matches(Regex("[a-z][a-z0-9-]{4,61}[a-z0-9]")))
        require(GoogleConversation.valid(conversation) && language.matches(Regex("[a-z]{2}-[A-Z]{2}")))
        require(voice.startsWith("$language-") && voice.matches(Regex("[A-Za-z0-9-]{5,100}")))
        require(silenceMs in 400..3000 && maximumMs in 5000..50000 && threshold in 100..10000)
    }
    fun json() = JSONObject().put("stt", sttToken).put("tts", ttsToken).put("expiry", expiresAt)
        .put("project", quotaProject).put("conversation", conversation).put("language", language).put("voice", voice)
        .put("authMode", authMode).put("silence", silenceMs).put("maximum", maximumMs).put("threshold", threshold)
    override fun toString() = "GoogleVoiceConfig(redacted)"
}

internal object GoogleVoiceSettings {
    private fun prefs(c: Context) = c.getSharedPreferences("google_voice_mode", Context.MODE_PRIVATE)
    fun enabled(c: Context) = prefs(c).getBoolean("enabled", false)
    /** The preference is for the next cycle. An active cycle keeps its immutable mode/config. */
    fun enable(c: Context, value: Boolean) { check(prefs(c).edit().putBoolean("enabled", value).commit()) }
    fun read(c: Context): GoogleVoiceConfig? {
        val j = BridgeSettings.readPrivate(c, "configuration", "google_voice_private")
        if (!j.has("stt")) return null
        return GoogleVoiceConfig(j.getString("stt"), j.getString("tts"), j.getLong("expiry"), j.getString("project"),
            j.getString("conversation"), j.getString("language"), j.getString("voice"), j.getInt("silence"), j.getInt("maximum"), j.getInt("threshold"), j.optString("authMode", "manual"))
    }
    fun save(c: Context, value: GoogleVoiceConfig) {
        check(!VoiceCycle.active); value.validate()
        BridgeSettings.writePrivate(c, "configuration", value.json(), "google_voice_private")
    }
    fun clear(c: Context) { check(!VoiceCycle.active); c.getSharedPreferences("google_voice_private", Context.MODE_PRIVATE).edit().clear().commit() }
}

/** Counts only exposed quantities; these are attempts, not provider invoices or model-token estimates. */
internal object GoogleVoiceUsage {
    @Synchronized fun add(c: Context, kind: String, quantity: Long, success: Boolean) {
        require(kind in setOf("stt_ms", "tts_characters"))
        val p = c.getSharedPreferences("google_voice_usage", Context.MODE_PRIVATE)
        p.edit().putLong(kind, p.getLong(kind, 0) + quantity)
            .putLong(kind + if (success) "_ok" else "_failed", p.getLong(kind + if (success) "_ok" else "_failed", 0) + 1).commit()
    }
    fun summary(c: Context): String {
        val p = c.getSharedPreferences("google_voice_usage", Context.MODE_PRIVATE)
        return "Google STT: ${p.getLong("stt_ms", 0)} ms di audio tentati; ${p.getLong("stt_ms_ok", 0)} esiti riusciti, ${p.getLong("stt_ms_failed", 0)} falliti.\n" +
            "Google TTS: ${p.getLong("tts_characters", 0)} caratteri tentati; ${p.getLong("tts_characters_ok", 0)} riusciti, ${p.getLong("tts_characters_failed", 0)} falliti.\n" +
            "Modello testuale: consumo/token non esposti dal bridge. Dialogo e sviluppo non classificati automaticamente. Quantità tecniche, non costi fatturati."
    }
}
