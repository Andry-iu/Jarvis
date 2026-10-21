package it.jarviswake

import android.content.Context
import android.os.SystemClock
import java.io.File
import java.time.Instant
import java.util.concurrent.Executors

/** Bounded, asynchronous diagnostic events. Never include user text, audio, URLs or credentials. */
internal object DiagnosticLog {
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "JarvisDiagnostic").apply { isDaemon = true }
    }
    private const val MAX_BYTES = 256 * 1024L
    private const val MAX_FILES = 3

    fun record(context: Context, event: Event, cycleId: String? = null) {
        val directory = File(context.applicationContext.filesDir, "diagnostics")
        val timestamp = Instant.now().toString()
        val elapsed = SystemClock.elapsedRealtime()
        val line = "$timestamp\t$elapsed\t${event.name}\t${cycleId ?: "-"}\n"
        executor.execute {
            runCatching {
                if (!directory.exists() && !directory.mkdirs()) return@runCatching
                val current = File(directory, "events-0.log")
                if (current.length() + line.toByteArray(Charsets.UTF_8).size > MAX_BYTES) {
                    File(directory, "events-${MAX_FILES - 1}.log").delete()
                    for (i in MAX_FILES - 2 downTo 0) {
                        val old = File(directory, "events-$i.log")
                        if (old.exists()) old.renameTo(File(directory, "events-${i + 1}.log"))
                    }
                }
                current.appendText(line)
            }
        }
    }

    enum class Event {
        CYCLE_STARTED, WEB_OPENED, VOICE_DISPATCHED, CYCLE_CANCELLED,
        WAKE_DETECTED, MICROPHONE_CAPTURE_STARTED, MICROPHONE_CAPTURE_RELEASED,
        SCREEN_OFF_MIC_PAUSED, SCREEN_ON_MIC_RESUME_REQUESTED,
        SCREEN_OFF_MIC_GRACE_STARTED, SCREEN_OFF_MIC_GRACE_EXPIRED,
        VOSK_MODEL_READY, VOSK_MODEL_RELEASED,
        SCREEN_ON_PRELOAD_REQUESTED, SCREEN_ON_PRELOAD_UNAVAILABLE, MICROPHONE_RELEASE_PENDING,
        WEB_PAGE_READY, VOICE_CONTROL_READY, VOICE_DISPATCHED_FIRST,
        WEB_PRELOAD_STARTED, WEB_PRELOAD_PAGE_READY, WEB_PRELOAD_VOICE_FOUND,
        WEB_PRELOAD_REUSED, WEB_PRELOAD_VOICE_SEEDED, WEB_PRELOAD_FAILED, WEB_PRELOAD_DISCARDED,
        VOICE_INPUT_TRACK_LIVE, VOICE_MEDIA_PLAYING,
        VOICE_CONTROL_SEMANTIC, VOICE_CONTROL_COMPOSER,
        VOICE_STATE_ACTIVE, VOICE_STATE_START_READY, VOICE_STATE_NOT_FOUND, VOICE_STATE_UNAVAILABLE,
        VOICE_STOP_TARGET_FOUND, VOICE_STOP_TARGET_NOT_FOUND, VOICE_STOP_TOUCH_DISPATCHED,
        VOICE_STOP_CONFIRMED, VOICE_STOP_FAILED,
        GREETING_ATTEMPTED, GREETING_COMPOSER_MISSING, GREETING_COMPOSER_WAITING,
        GREETING_COMPOSER_UNAVAILABLE, GREETING_SEND_WAITING, GREETING_SENT,
        GREETING_COMPOSER_OCCUPIED, GREETING_ABORTED,
        HIDE_GESTURE_RECOGNIZED, COVER_HIDDEN_FOR_WAKE, HIDE_FOR_WAKE_FAILED, WAKE_RESUMED_HIDDEN_CYCLE
    }
}
