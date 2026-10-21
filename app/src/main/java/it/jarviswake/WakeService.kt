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

        fun isMicrophoneReleased(): Boolean = instance?.recorder?.let {
            runCatching { it.recordingState != AudioRecord.RECORDSTATE_RECORDING }.getOrDefault(true)
        } ?: true

        fun pauseForVoiceCycle() {
            val service = instance ?: return
            service.main.post {
                service.setCaptureRequested(false)
                service.pauseForVoice()
            }
        }

        fun listenWhileHidden() {
            val service = instance ?: return
            service.main.post {
                if (!VoiceCycle.hiddenForWake) return@post
                service.updateCaptureRequest()
            }
        }
    }

    @Volatile private var workerRunning = false
    @Volatile private var captureRequested = false
    @Volatile private var captureEpoch = 0L
    @Volatile private var stopping = false
    @Volatile private var recorder: AudioRecord? = null
    @Volatile private var screenInteractive = true
    @Volatile private var screenOffDeadlineElapsedMs = 0L
    private var screenReceiverRegistered = false
    private var foregroundStarted = false
    private val workerMonitor = Object()
    internal val main = Handler(Looper.getMainLooper())
    private lateinit var notifications: NotificationManager

    private val screenOffGraceExpired = Runnable {
        if (!screenInteractive && !stopping && screenOffDeadlineElapsedMs > 0L &&
            SystemClock.elapsedRealtime() >= screenOffDeadlineElapsedMs) {
            screenOffDeadlineElapsedMs = 0L
            updateCaptureRequest()
            DiagnosticLog.record(this, DiagnosticLog.Event.SCREEN_OFF_MIC_GRACE_EXPIRED)
            if (!VoiceCycle.active) {
                notifications.notify(1, serviceNotification("Schermo spento da 30 secondi; microfono sospeso."))
                State.set(this, "Schermo spento da 30 secondi: microfono sospeso. Si riattiva appena lo schermo si accende.")
            }
        }
    }

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
        if (!WakeScreenPolicy.shouldListen(
                screenInteractive, VoiceCycle.active, stopping,
                SystemClock.elapsedRealtime(), screenOffDeadlineElapsedMs
            )) return
        ensureForeground("Microfono attivo. Pronuncia Hey Jarvis.")
        setCaptureRequested(true)
        if (!workerRunning) {
            workerRunning = true
            State.set(this, "Preparazione modello offline…")
            Thread({ listen() }, "JarvisWakeAudio").start()
        }
    }

    private fun setScreenInteractive(interactive: Boolean) {
        if (screenInteractive == interactive) return
        screenInteractive = interactive
        if (!interactive) {
            screenOffDeadlineElapsedMs = SystemClock.elapsedRealtime() + WakeScreenPolicy.SCREEN_OFF_GRACE_MS
            main.removeCallbacks(screenOffGraceExpired)
            main.postDelayed(screenOffGraceExpired, WakeScreenPolicy.SCREEN_OFF_GRACE_MS)
            updateCaptureRequest()
            notifications.notify(1, serviceNotification("Microfono attivo per 30 secondi dopo lo spegnimento."))
            if (!VoiceCycle.active) State.set(this, "Schermo spento: ascolto Hey Jarvis mantenuto per 30 secondi.")
            DiagnosticLog.record(this, DiagnosticLog.Event.SCREEN_OFF_MIC_GRACE_STARTED)
        } else {
            screenOffDeadlineElapsedMs = 0L
            main.removeCallbacks(screenOffGraceExpired)
            DiagnosticLog.record(this, DiagnosticLog.Event.SCREEN_ON_MIC_RESUME_REQUESTED)
            if (!stopping && !VoiceCycle.active) {
                DiagnosticLog.record(this, DiagnosticLog.Event.SCREEN_ON_PRELOAD_REQUESTED)
                if (!MainActivity.preloadForScreenOn())
                    DiagnosticLog.record(this, DiagnosticLog.Event.SCREEN_ON_PRELOAD_UNAVAILABLE)
                resumeListening()
            }
        }
    }

    private fun updateCaptureRequest() {
        val shouldCapture = if (VoiceCycle.hiddenForWake) {
            WakeScreenPolicy.shouldListen(screenInteractive, false, stopping,
                SystemClock.elapsedRealtime(), screenOffDeadlineElapsedMs)
        } else WakeScreenPolicy.shouldListen(
            screenInteractive, VoiceCycle.active, stopping,
            SystemClock.elapsedRealtime(), screenOffDeadlineElapsedMs
        )
        setCaptureRequested(shouldCapture)
    }

    private fun setCaptureRequested(requested: Boolean) {
        if (captureRequested != requested) {
            captureRequested = requested
            captureEpoch++
            if (!requested) runCatching { recorder?.stop() }
        }
        synchronized(workerMonitor) { workerMonitor.notifyAll() }
    }

    private fun pauseForVoice() {
        notifications.notify(1, serviceNotification("In pausa mentre ChatGPT usa il microfono."))
        State.running = true
    }

    private fun stopCompletely(message: String) {
        stopping = true
        main.removeCallbacks(screenOffGraceExpired)
        setCaptureRequested(false)
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
            if (stopping) throw InterruptedException()
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
        var modelLoadLock: PowerManager.WakeLock? = null
        var failure: String? = null
        try {
            modelLoadLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "JarvisWake:model-load")
            if (captureRequested) modelLoadLock?.acquire()
            val path = unpack()
            val loadedModel = Model(path.absolutePath)
            model = loadedModel
            val activeRecognizer = Recognizer(loadedModel, 16000f, "[\"hey jarvis\", \"[unk]\"]")
            recognizer = activeRecognizer
            activeRecognizer.setWords(true)
            modelLoadLock?.let { if (it.isHeld) it.release() }
            DiagnosticLog.record(this, DiagnosticLog.Event.VOSK_MODEL_READY)

            while (!stopping) {
                val shouldStart = synchronized(workerMonitor) {
                    while (!captureRequested && !stopping) workerMonitor.wait()
                    !stopping
                }
                if (!shouldStart) break

                val sessionEpoch = captureEpoch
                val sessionId = UUID.randomUUID().toString()
                var audio: AudioRecord? = null
                var lock: PowerManager.WakeLock? = null
                var captureStarted = false
                var detected = false
                try {
                    if (!captureRequested || captureEpoch != sessionEpoch) continue
                    lock = getSystemService(PowerManager::class.java)
                        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "JarvisWake:audio")
                    lock.acquire()
                    val min = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                    check(min > 0) { "Formato audio non disponibile" }
                    val activeAudio = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 16000, AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT, maxOf(min * 2, 6400))
                    audio = activeAudio
                    recorder = activeAudio
                    check(activeAudio.state == AudioRecord.STATE_INITIALIZED) { "Microfono non inizializzato" }
                    if (!captureRequested || captureEpoch != sessionEpoch) continue
                    activeAudio.startRecording()
                    check(activeAudio.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "Microfono non avviato" }
                    captureStarted = true
                    DiagnosticLog.record(this, DiagnosticLog.Event.MICROPHONE_CAPTURE_STARTED, sessionId)
                    State.set(this, if (screenInteractive) "In ascolto continuo: pronuncia Hey Jarvis. Consumo da misurare."
                        else "Schermo spento: ascolto Hey Jarvis attivo per la finestra di 30 secondi.")
                    val frame = ShortArray(320)
                    val preRoll = java.util.ArrayDeque<ShortArray>()
                    var tail = 0
                    while (captureRequested && !stopping && captureEpoch == sessionEpoch) {
                        val n = activeAudio.read(frame, 0, frame.size, AudioRecord.READ_BLOCKING)
                        if (!captureRequested || stopping || captureEpoch != sessionEpoch) break
                        check(n > 0) { "Lettura microfono fallita: $n" }
                        val rms = sqrt((0 until n).sumOf { frame[it].toDouble() * frame[it] } / n)
                        if (rms > 140) {
                            if (tail == 0) {
                                for (previous in preRoll) activeRecognizer.acceptWaveForm(previous, previous.size)
                                preRoll.clear()
                            }
                            tail = 100
                        }
                        if (tail > 0) {
                            tail--
                            if (activeRecognizer.acceptWaveForm(frame, n)) {
                                val result = JSONObject(activeRecognizer.result)
                                val words = result.optJSONArray("result")
                                val confidence = words != null && words.length() == 2 && (0 until words.length()).all {
                                    words.getJSONObject(it).optDouble("conf", 0.0) >= 0.75
                                }
                                if (confidence && WakePhrase.matches(result.optString("text"))) {
                                    detected = true
                                    DiagnosticLog.record(this, DiagnosticLog.Event.WAKE_DETECTED, sessionId)
                                    break
                                }
                            }
                        } else {
                            if (preRoll.size >= 25) preRoll.removeFirst()
                            preRoll.addLast(frame.copyOf(n))
                        }
                    }
                } finally {
                    if (recorder === audio) recorder = null
                    runCatching { audio?.stop() }
                    runCatching { audio?.release() }
                    if (lock?.isHeld == true) lock.release()
                    if (captureStarted) {
                        DiagnosticLog.record(this, DiagnosticLog.Event.MICROPHONE_CAPTURE_RELEASED, sessionId)
                    }
                }

                activeRecognizer.reset()
                if (detected) {
                    setCaptureRequested(false)
                    main.post { if (!stopping) onWake(sessionId) }
                }
            }
        } catch (e: Exception) {
            if (!stopping) failure = "Ascolto interrotto: ${e.javaClass.simpleName}: ${e.message}"
        } finally {
            if (modelLoadLock?.isHeld == true) modelLoadLock.release()
            runCatching { recognizer?.close() }
            runCatching { model?.close() }
            if (model != null) DiagnosticLog.record(this, DiagnosticLog.Event.VOSK_MODEL_RELEASED)
            main.post {
                workerRunning = false
                if (instance !== this) return@post
                if (failure != null && !stopping) stopCompletely(failure!!)
                else if (!stopping && captureRequested) resumeListening()
            }
        }
    }

    private fun onWake(wakeCycleId: String?) {
        vibrateWakeConfirmation()
        if (VoiceCycle.resumeHidden(this, VoiceCycle.id)) return
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
        val stoppedByUserOrWorker = stopping
        stopping = true
        main.removeCallbacks(screenOffGraceExpired)
        setCaptureRequested(false)
        if (screenReceiverRegistered) {
            runCatching { unregisterReceiver(screenStateReceiver) }
            screenReceiverRegistered = false
        }
        State.running = false
        if (!stoppedByUserOrWorker) State.set(this, "Servizio arrestato da Android. Riapri JarvisWake.")
        super.onDestroy()
    }
}
