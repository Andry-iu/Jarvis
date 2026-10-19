package it.jarviswake

import android.content.Context
import android.content.Intent

object ChatGpt {
    fun attempt(context: Context, token: String) {
        if (!VoiceCycle.matches(token)) return
        val armed = VoiceAccessibilityService.armWeb(context, token)
        if (!armed) {
            VoiceCycle.cancel(context, token, "Abilita JarvisWake in Accessibilità per avviare il ciclo Voice.")
            return
        }
        try {
            context.startActivity(Intent(context, WebChatActivity::class.java)
                .putExtra(WebChatActivity.EXTRA_AUTOMATION, true)
                .putExtra(VoiceCycle.EXTRA_ID, token)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        } catch (e: RuntimeException) {
            VoiceCycle.cancel(context, token, "Apertura ChatGPT rifiutata: ${e.javaClass.simpleName}.")
        }
    }
}
