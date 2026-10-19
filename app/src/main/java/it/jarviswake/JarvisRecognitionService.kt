package it.jarviswake

import android.content.Intent
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.speech.RecognitionService
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import android.os.Bundle
import java.util.IdentityHashMap

/** Recognition entry required by Android's VoiceInteractionService metadata. */
class JarvisRecognitionService : RecognitionService() {
    private val recognizers = IdentityHashMap<Callback, SpeechRecognizer>()

    override fun onStartListening(intent: Intent?, callback: Callback?) {
        val target = callback ?: return
        val component = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(RECOGNIZER_COMPONENT, null)
            ?.let(ComponentName::unflattenFromString)
            ?.takeUnless { it.packageName == packageName && it.className == javaClass.name }
        if (component == null || !SpeechRecognizer.isRecognitionAvailable(this)) {
            target.error(SpeechRecognizer.ERROR_CLIENT)
            return
        }
        val recognizer = runCatching { SpeechRecognizer.createSpeechRecognizer(this, component) }
            .getOrElse {
                target.error(SpeechRecognizer.ERROR_CLIENT)
                return
            }
        recognizers[target] = recognizer
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = target.readyForSpeech(params)
            override fun onBeginningOfSpeech() = target.beginningOfSpeech()
            override fun onRmsChanged(rmsdB: Float) = target.rmsChanged(rmsdB)
            override fun onBufferReceived(buffer: ByteArray?) = target.bufferReceived(buffer)
            override fun onEndOfSpeech() = target.endOfSpeech()
            override fun onError(error: Int) {
                release(target)
                target.error(error)
            }
            override fun onResults(results: Bundle?) {
                release(target)
                target.results(results)
            }
            override fun onPartialResults(partialResults: Bundle?) = target.partialResults(partialResults)
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        recognizer.startListening(intent ?: Intent())
    }

    override fun onStopListening(callback: Callback?) {
        recognizers[callback]?.stopListening()
    }

    override fun onCancel(callback: Callback?) {
        callback?.let { release(it, cancel = true) }
    }

    override fun onDestroy() {
        recognizers.keys.toList().forEach { release(it, cancel = true) }
        super.onDestroy()
    }

    private fun release(callback: Callback, cancel: Boolean = false) {
        val recognizer = recognizers.remove(callback) ?: return
        if (cancel) runCatching { recognizer.cancel() }
        recognizer.destroy()
    }

    companion object {
        private const val PREFS = "assistant"
        private const val RECOGNIZER_COMPONENT = "system_recognizer"

        fun rememberSystemRecognizer(context: Context) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val existing = prefs.getString(RECOGNIZER_COMPONENT, null)
                ?.let(ComponentName::unflattenFromString)
                ?.takeUnless { it.packageName == context.packageName }
            if (existing != null) return

            val secureSetting = android.provider.Settings.Secure.getString(
                context.contentResolver,
                "voice_recognition_service"
            )?.let(ComponentName::unflattenFromString)
                ?.takeUnless { it.packageName == context.packageName }
            val resolved = secureSetting ?: context.packageManager.resolveService(
                Intent(RecognitionService.SERVICE_INTERFACE),
                PackageManager.MATCH_DEFAULT_ONLY
            )?.serviceInfo?.let { ComponentName(it.packageName, it.name) }
                ?.takeUnless { it.packageName == context.packageName }
            if (resolved != null) prefs.edit()
                .putString(RECOGNIZER_COMPONENT, resolved.flattenToString())
                .apply()
        }
    }
}
