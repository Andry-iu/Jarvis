package it.jarviswake

import android.content.Context
import android.os.SystemClock
import java.io.File
import java.time.Instant
import java.util.concurrent.Executors

/** Bounded, asynchronous diagnostic events. Never include user text, audio, full URLs or credentials. */
internal object DiagnosticLog {
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "JarvisDiagnostic").apply { isDaemon = true }
    }
    private const val MAX_BYTES = 256 * 1024L
    private const val MAX_FILES = 3

    fun record(
        context: Context,
        event: Event,
        cycleId: String? = null,
        responseId: Int? = null,
        revision: Int? = null,
        chars: Int? = null,
        duplicateOf: Int? = null,
        repeatCount: Int? = null,
        sequence: Int? = null,
        monitorStatus: String? = null,
        roleNodes: Int? = null,
        assistantNodes: Int? = null,
        visibleNodes: Int? = null,
        eligibleNodes: Int? = null,
        stableNodes: Int? = null,
        documentGeneration: Int? = null,
        webResourceHost: String? = null,
        webErrorCode: Int? = null,
        webHttpStatus: Int? = null,
        webErrorCategory: String? = null,
        webRuntimeVersion: String? = null
    ) {
        val directory = File(context.applicationContext.filesDir, "diagnostics")
        val timestamp = Instant.now().toString()
        val elapsed = SystemClock.elapsedRealtime()
        val details = listOf(
            "response=${responseId?.takeIf { it > 0 } ?: "-"}",
            "revision=${revision?.takeIf { it > 0 } ?: "-"}",
            "chars=${chars?.takeIf { it >= 0 } ?: "-"}",
            "duplicateOf=${duplicateOf?.takeIf { it > 0 } ?: "-"}",
            "repeats=${repeatCount?.takeIf { it > 0 } ?: "-"}",
            "sequence=${sequence?.takeIf { it > 0 } ?: "-"}",
            "monitor=${monitorStatus?.takeIf { it.matches(Regex("[a-z_]{1,24}")) } ?: "-"}",
            "roleNodes=${roleNodes?.takeIf { it >= 0 } ?: "-"}",
            "assistantNodes=${assistantNodes?.takeIf { it >= 0 } ?: "-"}",
            "visibleNodes=${visibleNodes?.takeIf { it >= 0 } ?: "-"}",
            "eligibleNodes=${eligibleNodes?.takeIf { it >= 0 } ?: "-"}",
            "stableNodes=${stableNodes?.takeIf { it >= 0 } ?: "-"}",
            "documentGeneration=${documentGeneration?.takeIf { it >= 0 } ?: "-"}",
            "webHost=${webResourceHost?.lowercase()?.takeIf { it.matches(Regex("[a-z0-9.-]{1,253}")) } ?: "-"}",
            "webError=${webErrorCode?.takeIf { it in -1000..10000 } ?: "-"}",
            "webHttp=${webHttpStatus?.takeIf { it in 100..599 } ?: "-"}",
            "webCategory=${webErrorCategory?.takeIf { it.matches(Regex("[a-z0-9_]{1,32}")) } ?: "-"}",
            "webView=${webRuntimeVersion?.takeIf { it.matches(Regex("[0-9A-Za-z.+_-]{1,40}")) } ?: "-"}"
        ).joinToString("\t")
        val line = "$timestamp\t$elapsed\t${event.name}\t${cycleId ?: "-"}\t$details\n"
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

    /** Run a snapshot/export after all earlier diagnostic writes have finished. */
    fun afterPendingWrites(action: () -> Unit) {
        executor.execute(action)
    }

    enum class Event {
        NATIVE_CONTROL_PROBE, NATIVE_STOP_CONFIRMED, NATIVE_HOME_COMMAND, NATIVE_HOME_REJECTED, NATIVE_COVER_DISMISSED, NATIVE_STOP_WAIT_TIMEOUT,
        NATIVE_SHORTCUT_OPENED, NATIVE_VOICE_COMMAND, NATIVE_VOICE_CONFIRMED, NATIVE_STOP_COMMAND,
        WEB_MAIN_HTTP_ERROR, WEB_NAVIGATION_BLOCKED,
        CYCLE_STARTED, WEB_OPENED, VOICE_DISPATCHED, CYCLE_CANCELLED,
        WAKE_DETECTED, MICROPHONE_CAPTURE_STARTED, MICROPHONE_CAPTURE_RELEASED,
        SCREEN_OFF_MIC_PAUSED, SCREEN_ON_MIC_RESUME_REQUESTED,
        SCREEN_OFF_MIC_GRACE_STARTED, SCREEN_OFF_MIC_GRACE_EXPIRED,
        VOSK_MODEL_READY, VOSK_MODEL_RELEASED,
        SCREEN_ON_PRELOAD_REQUESTED, SCREEN_ON_PRELOAD_UNAVAILABLE, MICROPHONE_RELEASE_PENDING,
        WEB_PAGE_READY, VOICE_CONTROL_READY, VOICE_DISPATCHED_FIRST,
        WEB_PRELOAD_STARTED, WEB_PRELOAD_PAGE_READY, WEB_PRELOAD_VOICE_FOUND,
        WEB_PRELOAD_REUSED, WEB_PRELOAD_VOICE_SEEDED, WEB_PRELOAD_FAILED, WEB_PRELOAD_DISCARDED,
        WEBVIEW_RUNTIME, WEB_SUBRESOURCE_LOAD_FAILED, WEB_SUBRESOURCE_HTTP_ERROR, WEB_MODULE_CONSOLE_ERROR,
        MICROPHONE_MUTED, MICROPHONE_UNMUTED,
        VOICE_INPUT_TRACK_LIVE, VOICE_MEDIA_PLAYING, VOICE_MEDIA_PLAYBACK_STARTED,
        VOICE_MEDIA_PLAYBACK_STOPPED,
        ASSISTANT_RESPONSE_SEEN, ASSISTANT_RESPONSE_UPDATED, ASSISTANT_RESPONSE_DUPLICATE,
        ASSISTANT_RESPONSE_INTERNAL_REPEAT,
        ASSISTANT_MONITOR_QUERY, ASSISTANT_MONITOR_RESULT, ASSISTANT_MONITOR_INVALID_RESULT,
        VOICE_CONTROL_SEMANTIC, VOICE_CONTROL_COMPOSER,
        VOICE_STATE_ACTIVE, VOICE_STATE_START_READY, VOICE_STATE_NOT_FOUND, VOICE_STATE_UNAVAILABLE,
        VOICE_STOP_TARGET_FOUND, VOICE_STOP_TARGET_NOT_FOUND, VOICE_STOP_COORDINATES_REPLAYED,
        VOICE_STOP_BOTTOM_CONTROL_FOUND,
        VOICE_STOP_TOUCH_DISPATCHED,
        VOICE_STOP_CONFIRMED, VOICE_STOP_FAILED,
        GREETING_ATTEMPTED, GREETING_COMPOSER_MISSING, GREETING_COMPOSER_WAITING,
        GREETING_COMPOSER_UNAVAILABLE, GREETING_SEND_WAITING, GREETING_SENT,
        GREETING_COMPOSER_OCCUPIED, GREETING_ABORTED,
        HIDE_GESTURE_RECOGNIZED, COVER_HIDDEN_FOR_WAKE, HIDE_FOR_WAKE_FAILED, WAKE_RESUMED_HIDDEN_CYCLE,
        CHAT_REVEAL_GESTURE_RECOGNIZED, CHAT_REVEALED_FOR_INTERACTION, CHAT_REVEAL_FAILED
    }
}
