package it.jarviswake

import android.annotation.SuppressLint
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.*
import android.provider.Settings
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.util.UUID
import kotlin.math.sqrt

class WakeService : Service() {
    companion object {
        @Volatile private var instance: WakeService? = null

        /** Resumes only the foreground service that the user already started. */
        fun resumeExisting(): Boolean {
            val service = instance ?: return false
            service.main.post { service.resumeListening() }
            return true
        }

        fun isMicrophoneReleased(): Boolean = instance?.workerRunning != true

        fun pauseForVoiceCycle() {
            val service = instance ?: return
            service.main.post {
                service.resumeRequested = false
                service.listening = false
                runCatching { service.recorder?.stop() }
                service.pauseForVoice()
            }
        }
    }

    @Volatile private var workerRunning = false
    private var resumeRequested = false
    @Volatile private var listening = false
    @Volatile private var stopping = false
    @Volatile private var recorder: AudioRecord? = null
    private var screenInteractive = true
    private var screenReceiverRegistered = false
    private var foregroundStarted = false
    internal val main = Handler(Looper.getMainLooper())
    private lateinit var notifications: NotificationManager

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> setScreenInteractive(false)
                Intent.ACTION_SCREEN_ON -> setScreenInteractive(true)
            }
        }
    }

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        notifications = getSystemService(NotificationManager::class.java)
        screenInteractive = getSystemService(PowerManager::class.java).isInteractive
        val screenFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenStateReceiver, screenFilter, RECEIVER_NOT_EXPORTED)
        else {
            @Suppress("DEPRECATION")
            registerReceiver(screenStateReceiver, screenFilter)
        }
        screenReceiverRegistered = true
        notifications.createNotificationChannel(NotificationChannel("listen", "Ascolto offline", NotificationManager.IMPORTANCE_LOW))
        notifications.cancel(2)
        notifications.deleteNotificationChannel("wake")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopCompletely("Ascolto fermato.")
            return START_NOT_STICKY
        }
        stopping = false
        try {
            ensureForeground("Preparazione ascolto offline…")
            resumeListening()
        } catch (e: RuntimeException) {
            State.set(this, "Servizio rifiutato: ${e.javaClass.simpleName}")
            State.running = false
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun ensureForeground(message: String) {
        val notification = serviceNotification(message)
        if (!foregroundStarted) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            foregroundStarted = true
        } else notifications.notify(1, notification)
        State.running = true
    }

    private fun serviceNotification(message: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, WakeService::class.java).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, "listen")
            .setSmallIcon(R.drawable.ic_jarvis)
            .setContentTitle("JarvisWake • servizio continuo")
            .setContentText(message)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Ferma", stop).build())
            .build()
    }

    private fun resumeListening() {
        if (!WakeScreenPolicy.shouldListen(screenInteractive, VoiceCycle.active, stopping) || listening) return
        if (workerRunning) { resumeRequested = true; return }
        resumeRequested = false
        workerRunning = true
        listening = true
        ensureForeground("Microfono attivo. Pronuncia Hey Jarvis.")
        State.set(this, "Preparazione modello offline…")
        Thread({ listen() }, "JarvisWakeAudio").start()
    }

    private fun setScreenInteractive(interactive: Boolean) {
        if (screenInteractive == interactive) return
        screenInteractive = interactive
        if (!interactive) {
            resumeRequested = false
            listening = false
            runCatching { recorder?.stop() }
            notifications.notify(1, serviceNotification("Microfono sospeso; si riattiva quando lo schermo si accende."))
            if (!VoiceCycle.active) State.set(this, "Schermo spento: microfono sospeso; Jarvis si riattiva quando lo schermo si accende.")
            DiagnosticLog.record(this, DiagnosticLog.Event.SCREEN_OFF_MIC_PAUSED)
        } else {
            DiagnosticLog.record(this, DiagnosticLog.Event.SCREEN_ON_MIC_RESUME_REQUESTED)
            if (!stopping && !VoiceCycle.active) {
                DiagnosticLog.record(this, DiagnosticLog.Event.SCREEN_ON_PRELOAD_REQUESTED)
                if (!MainActivity.preloadForScreenOn())
                    DiagnosticLog.record(this, DiagnosticLog.Event.SCREEN_ON_PRELOAD_UNAVAILABLE)
                resumeListening()
            }
        }
    }

    private fun pauseForVoice() {
        notifications.notify(1, serviceNotification("In pausa mentre ChatGPT usa il microfono."))
        State.running = true
    }

    private fun stopCompletely(message: String) {
        stopping = true
        listening = false
        runCatching { recorder?.stop() }
        State.running = false
        State.set(this, message)
        if (foregroundStarted) stopForeground(STOP_FOREGROUND_REMOVE)
        foregroundStarted = false
        stopSelf()
    }

    private fun unpack(): File {
        val target = File(filesDir, "model-0.15")
        if (File(target, ".ready").exists()) return target
        val temp = File(filesDir, "model-install")
        temp.deleteRecursively(); temp.mkdirs()
        fun copy(source: String, dest: File) {
            if (!listening) throw InterruptedException()
            val children = assets.list(source) ?: emptyArray()
            if (children.isEmpty()) {
                assets.open(source).use { input -> dest.outputStream().use { input.copyTo(it) } }
            } else {
                dest.mkdirs()
                children.forEach { copy("$source/$it", File(dest, it)) }
            }
        }
        copy("model", temp)
        File(temp, ".ready").writeText("0.15")
        target.deleteRecursively()
        check(temp.renameTo(target)) { "Impossibile installare modello" }
        return target
    }

    @SuppressLint("MissingPermission", "WakelockTimeout")
    private fun listen() {
        var model: Model? = null
        var recognizer: Recognizer? = null
        var audio: AudioRecord? = null
        var lock: PowerManager.WakeLock? = null
        var detected = false
        var wakeCycleId: String? = null
        var captureSessionId: String? = null
        var failure: String? = null
        try {
            lock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "JarvisWake:audio")
            lock.acquire()
            val path = unpack()
            if (!listening) return
            model = Model(path.absolutePath)
            recognizer = Recognizer(model, 16000f, "[\"hey jarvis\", \"[unk]\"]")
            recognizer.setWords(true)
            val min = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            check(min > 0) { "Formato audio non disponibile" }
            audio = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 16000, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, maxOf(min * 2, 6400))
            recorder = audio
            check(audio.state == AudioRecord.STATE_INITIALIZED) { "Microfono non inizializzato" }
            if (!listening) return
            captureSessionId = UUID.randomUUID().toString()
            audio.startRecording()
            check(audio.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "Microfono non avviato" }
            DiagnosticLog.record(this, DiagnosticLog.Event.MICROPHONE_CAPTURE_STARTED, captureSessionId)
            State.set(this, "In ascolto continuo: pronuncia Hey Jarvis. Consumo da misurare.")
            val frame = ShortArray(320)
            val preRoll = java.util.ArrayDeque<ShortArray>()
            var tail = 0
            while (listening) {
                val n = audio.read(frame, 0, frame.size, AudioRecord.READ_BLOCKING)
                if (!listening) break
                check(n > 0) { "Lettura microfono fallita: $n" }
                val rms = sqrt((0 until n).sumOf { frame[it].toDouble() * frame[it] } / n)
                if (rms > 140) {
                    if (tail == 0) {
                        for (previous in preRoll) recognizer.acceptWaveForm(previous, previous.size)
                        preRoll.clear()
                    }
                    tail = 100
                }
                if (tail > 0) {
                    tail--
                    if (recognizer.acceptWaveForm(frame, n)) {
                        val result = JSONObject(recognizer.result)
                        val words = result.optJSONArray("result")
                        val confidence = words != null && words.length() == 2 && (0 until words.length()).all {
                            words.getJSONObject(it).optDouble("conf", 0.0) >= 0.75
                        }
                        if (confidence && WakePhrase.matches(result.optString("text"))) {
                            detected = true
                            wakeCycleId = captureSessionId
                            DiagnosticLog.record(this, DiagnosticLog.Event.WAKE_DETECTED, wakeCycleId)
                            break
                        }
                    }
                } else {
                    if (preRoll.size >= 25) preRoll.removeFirst()
                    preRoll.addLast(frame.copyOf(n))
                }
            }
        } catch (e: Exception) {
            if (listening) failure = "Ascolto interrotto: ${e.javaClass.simpleName}: ${e.message}"
        } finally {
            listening = false
            recorder = null
            runCatching { audio?.stop() }; runCatching { audio?.release() }
            runCatching { recognizer?.close() }; runCatching { model?.close() }
            if (lock?.isHeld == true) lock.release()
            captureSessionId?.let {
                DiagnosticLog.record(this, DiagnosticLog.Event.MICROPHONE_CAPTURE_RELEASED, it)
            }
            main.post {
                workerRunning = false
                if (instance !== this) return@post
                when {
                    stopping -> Unit
                    VoiceCycle.active -> Unit
                    !screenInteractive -> Unit
                    resumeRequested -> resumeListening()
                    detected -> onWake(wakeCycleId)
                    failure != null -> stopCompletely(failure!!)
                }
            }
        }
    }

    private fun onWake(wakeCycleId: String?) {
        vibrateWakeConfirmation()
        pauseForVoice()
        State.setAttempt(this, "Wake word Hey Jarvis rilevata; avvio schermata di sblocco.")
        State.set(this, "Hey Jarvis rilevato. Microfono in pausa per ChatGPT.")
        if (Settings.canDrawOverlays(this)) {
            VoiceCycle.start(this, wakeCycleId)
        } else {
            State.set(this, "Consenti la visualizzazione sopra altre app per aprire ChatGPT. Ascolto riattivato.")
            resumeListening()
        }
    }

    private fun vibrateWakeConfirmation() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
        vibrator.vibrate(VibrationEffect.createOneShot(120L, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        if (screenReceiverRegistered) {
            runCatching { unregisterReceiver(screenStateReceiver) }
            screenReceiverRegistered = false
        }
        listening = false
        runCatching { recorder?.stop() }
        State.running = false
        if (!stopping) State.set(this, "Servizio arrestato da Android. Riapri JarvisWake.")
        super.onDestroy()
    }
}
