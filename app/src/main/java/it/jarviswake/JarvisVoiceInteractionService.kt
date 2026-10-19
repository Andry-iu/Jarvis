package it.jarviswake

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.service.voice.VoiceInteractionService

/** System-bound assistant service. The OS keeps the selected instance available for hotword use. */
class JarvisVoiceInteractionService : VoiceInteractionService() {
    override fun onReady() {
        super.onReady()
        if (!isActiveService(this, ComponentName(this, JarvisVoiceInteractionService::class.java))) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            State.set(this, "Assistente attivo; concedi il microfono e riapri JarvisWake per ascoltare.")
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            State.set(this, "Assistente attivo; concedi le notifiche per rendere visibile l’ascolto continuo.")
            return
        }
        if (!getSystemService(NotificationManager::class.java).areNotificationsEnabled()) {
            State.set(this, "Assistente attivo; abilita le notifiche di JarvisWake per avviare l’ascolto.")
            return
        }
        try {
            startForegroundService(Intent(this, WakeService::class.java).setAction(ACTION_ASSISTANT_START))
        } catch (e: RuntimeException) {
            State.set(this, "Assistente selezionato, avvio ascolto rifiutato: ${e.javaClass.simpleName}.")
        }
    }

    override fun onShutdown() {
        stopService(Intent(this, WakeService::class.java).setAction(ACTION_ASSISTANT_STOP))
        super.onShutdown()
    }

    companion object {
        const val ACTION_ASSISTANT_START = "it.jarviswake.action.ASSISTANT_START"
        const val ACTION_ASSISTANT_STOP = "it.jarviswake.action.ASSISTANT_STOP"
    }
}
