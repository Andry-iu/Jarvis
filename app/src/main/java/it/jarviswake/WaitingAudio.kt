package it.jarviswake

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/** Main-thread owner: keep playing through the LaunchActivity/WebView handoff. */
internal object WaitingAudio {
    private val state = WaitingAudioState()
    private val minimum = WaitingAudioMinimum()
    private val main = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var playerPrepared = false
    private var previewing = false
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null
    private val timeout = Runnable { stop() }

    fun begin(context: Context, token: String) {
        stop()
        state.begin(token)
        if (WaitingAudioSettings.enabled(context) && WaitingAudioSettings.file(context).isFile)
            minimum.begin(token, WaitingAudioSettings.minimumSeconds(context))
    }

    fun coverShown(context: Context, token: String) {
        if (!VoiceCycle.matches(token) || VoiceCycle.hiddenForWake) return
        if (!WaitingAudioSettings.enabled(context) || !WaitingAudioSettings.file(context).isFile) {
            stop()
            return
        }
        if (!state.request(token, enabled = true, fileAvailable = true)) return
        startPlayer(context, token)
    }

    /** A user-requested preview never creates a Voice cycle or arms its minimum gate. */
    fun preview(context: Context) {
        if (VoiceCycle.active && !VoiceCycle.hiddenForWake) return
        stop()
        if (!WaitingAudioSettings.file(context).isFile) return
        previewing = true
        val seconds = WaitingAudioSettings.minimumSeconds(context).takeIf { it > 0 } ?: 2
        startPlayer(context, token = null, previewDurationMs = seconds * 1_000L)
    }

    fun stopPreview() {
        if (previewing) stop()
    }

    private fun startPlayer(context: Context, token: String?, previewDurationMs: Long = 0L) {
        val candidate = try { MediaPlayer() } catch (_: RuntimeException) { stop(); return }
        player = candidate
        main.postDelayed(timeout, 40_000L)
        try {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
            val manager = context.getSystemService(AudioManager::class.java)
            val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attributes)
                .setWillPauseWhenDucked(true)
                .setOnAudioFocusChangeListener({ change ->
                    if (change < 0 && player === candidate) stop()
                }, main).build()
            audioManager = manager
            focusRequest = focus
            if (manager.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                stop()
                return
            }
            candidate.setOnPreparedListener {
                if (player === it && (if (token == null) previewing && (!VoiceCycle.active || VoiceCycle.hiddenForWake)
                    else state.mayPlay(token) && VoiceCycle.matches(token))) {
                    try {
                        playerPrepared = true
                        updateVolume(WaitingAudioSettings.volume(context))
                        it.isLooping = true
                        it.start()
                        if (token != null) minimum.playbackStarted(token, SystemClock.elapsedRealtime())
                        else {
                            main.removeCallbacks(timeout)
                            main.postDelayed(timeout, previewDurationMs)
                        }
                    } catch (_: RuntimeException) { stop() }
                }
            }
            candidate.setOnErrorListener { failed, _, _ ->
                if (player === failed) stop()
                true
            }
            candidate.setDataSource(WaitingAudioSettings.file(context).path)
            candidate.setAudioAttributes(attributes)
            candidate.prepareAsync()
        } catch (_: Exception) { stop() }
    }

    fun voiceActive(token: String) {
        if (previewing) return // A late callback from the hidden cycle must not stop a preview.
        if (state.finish(token)) stop()
    }

    fun canActivateVoice(token: String) = !minimum.shouldWait(token, SystemClock.elapsedRealtime())

    fun updateVolume(percent: Int) {
        if (!playerPrepared) return // Preparation will read the latest saved setting.
        val gain = percent.coerceIn(0, 100) / 100f
        try { player?.setVolume(gain, gain) }
        catch (_: IllegalStateException) { stop() }
    }

    fun stop() {
        minimum.stop()
        main.removeCallbacks(timeout)
        val previous = player
        previewing = false
        playerPrepared = false
        player = null // Invalidate queued prepared/error callbacks before releasing.
        previous?.release()
        focusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        focusRequest = null
        audioManager = null
    }
}
