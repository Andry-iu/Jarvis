package it.jarviswake

import android.content.Context

internal object VoiceGreetingSettings {
    const val DEFAULT_PHRASE = "ciao jarvis"
    private const val PREFERENCES = "voice_greeting"
    private const val PHRASE = "phrase"

    fun get(context: Context): String = context.applicationContext
        .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        .getString(PHRASE, DEFAULT_PHRASE)
        ?.takeIf { it.isNotBlank() }
        ?: DEFAULT_PHRASE

    fun save(context: Context, rawPhrase: String): Boolean {
        val phrase = VoiceGreetingPhrase.normalize(rawPhrase) ?: return false
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit().putString(PHRASE, phrase).apply()
        return true
    }

    fun reset(context: Context) {
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit().remove(PHRASE).apply()
    }
}

internal object VoiceGreetingPhrase {
    fun normalize(rawPhrase: String): String? = rawPhrase.trim().takeIf { it.isNotEmpty() }
}
