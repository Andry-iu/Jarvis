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
        SCREEN_ON_PRELOAD_REQUESTED, SCREEN_ON_PRELOAD_UNAVAILABLE, MICROPHONE_RELEASE_PENDING,
        WEB_PAGE_READY, VOICE_CONTROL_READY, VOICE_DISPATCHED_FIRST,
        WEB_PRELOAD_STARTED, WEB_PRELOAD_PAGE_READY, WEB_PRELOAD_VOICE_FOUND,
        WEB_PRELOAD_REUSED, WEB_PRELOAD_VOICE_SEEDED, WEB_PRELOAD_FAILED, WEB_PRELOAD_DISCARDED,
        VOICE_INPUT_TRACK_LIVE, VOICE_MEDIA_PLAYING,
        VOICE_CONTROL_SEMANTIC, VOICE_CONTROL_COMPOSER
    }
}
