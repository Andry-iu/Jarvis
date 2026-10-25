package it.jarviswake

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper

/** Main-thread owner: keep playing through the LaunchActivity/WebView handoff. */
internal object WaitingAudio {
    private val state = WaitingAudioState()
    private val main = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null
    private val timeout = Runnable { stop() }

    fun begin(token: String) {
        stop()
        state.begin(token)
    }

    fun coverShown(context: Context, token: String) {
        if (!VoiceCycle.matches(token) || VoiceCycle.hiddenForWake ||
            !state.request(token, WaitingAudioSettings.enabled(context), WaitingAudioSettings.file(context).isFile)) return
        val candidate = MediaPlayer()
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
                if (player === it && state.mayPlay(token) && VoiceCycle.matches(token)) {
                    try {
                        it.isLooping = true
                        it.start()
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
        if (state.finish(token)) stop()
    }

    fun stop() {
        main.removeCallbacks(timeout)
        val previous = player
        player = null // Invalidate queued prepared/error callbacks before releasing.
        previous?.release()
        focusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        focusRequest = null
        audioManager = null
    }
}
