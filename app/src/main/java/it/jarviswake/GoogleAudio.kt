package it.jarviswake

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder

/** Single owner. stop() invalidates first; release is acknowledged only by the worker's finally. */
internal interface GoogleCapture {
    val released: Boolean
    fun stop()
    fun start(onSpeech: (ShortArray) -> Unit, onEnd: (ShortArray?, String?) -> Unit, onChunk: (ShortArray, Int) -> Unit = {_,_->})
}
internal interface GooglePlayback {
    val released: Boolean
    fun stop()
    fun play(pcm: ByteArray, allowed: () -> Boolean, started: () -> Unit): Boolean
}
internal class GoogleAudioCapture(private val config: GoogleVoiceConfig) : GoogleCapture {
    @Volatile private var stopped = false
    @Volatile private var recorder: AudioRecord? = null
    @Volatile override var released = true
        private set
    override fun stop() { stopped = true; runCatching { recorder?.stop() } }
    @SuppressLint("MissingPermission") // Activity checks RECORD_AUDIO before constructing the capture.
    override fun start(onSpeech: (ShortArray) -> Unit, onEnd: (ShortArray?, String?) -> Unit, onChunk: (ShortArray, Int) -> Unit) {
        check(released); released = false
        Thread({
            val vad = LocalSpeechBuffer(config.silenceMs, config.maximumMs, config.threshold)
            val frame = ShortArray(320)
            var output: ShortArray? = null; var failure: String? = null
            try {
                val size = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                check(size > 0)
                val audio = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 16000, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, size.coerceAtLeast(6400))
                recorder = audio
                check(audio.state == AudioRecord.STATE_INITIALIZED && !stopped)
                audio.startRecording(); check(audio.recordingState == AudioRecord.RECORDSTATE_RECORDING)
                var total = 0
                var streamed = 0
                while (!stopped && total < 16000 * 110) { // at most 60 s waiting for onset + 50 s utterance
                    val n = audio.read(frame, 0, frame.size)
                    check(n > 0 && audio.activeRecordingConfiguration?.isClientSilenced == false) // Android silencing is an error, never successful capture.
                    total += n
                    val wasSpeaking = vad.speaking
                    output = vad.accept(frame, n)
                    if (!wasSpeaking && vad.speaking) {
                        val initial=vad.preRoll();streamed=initial.size;onSpeech(initial)
                    } else if (wasSpeaking) {
                        val take=n.coerceAtMost(config.maximumMs*16-streamed)
                        if(take>0) {onChunk(frame,take);streamed+=take}
                    }
                    if (output != null) break
                    if (!vad.speaking && total >= 16000 * 60) break
                }
                if (!stopped && output == null) failure = "nessun_parlato"
            } catch (_: Exception) { if (!stopped) failure = "microfono_non_disponibile" }
            finally {
                vad.clear(); frame.fill(0)
                runCatching { recorder?.stop() }; runCatching { recorder?.release() }; recorder = null; released = true
                if (stopped) { output?.fill(0); onEnd(null, null) } else onEnd(output, failure)
            }
        }, "Jarvis-google-microphone").apply { isDaemon = true; start() }
    }
}

internal class GoogleAudioPlayback : GooglePlayback {
    @Volatile private var stopped = false
    @Volatile private var track: AudioTrack? = null
    @Volatile override var released = true
        private set
    override fun stop() { stopped = true; runCatching { track?.pause() }; runCatching { track?.flush() } }
    override fun play(pcm: ByteArray, allowed: () -> Boolean, started: () -> Unit): Boolean {
        released = false
        try {
            if (stopped || !allowed()) return false
            val audio = AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(24000)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(pcm.size).build()
            track = audio
            check(audio.write(pcm, 0, pcm.size) == pcm.size)
            if (stopped || !allowed()) return false
            audio.play(); started()
            val end = android.os.SystemClock.elapsedRealtime() + pcm.size / 48 + 5000
            while (!stopped && allowed() && audio.playbackHeadPosition < pcm.size / 2) {
                if (android.os.SystemClock.elapsedRealtime() >= end) return false
                Thread.sleep(20)
            }
            return !stopped && allowed() && audio.playbackHeadPosition >= pcm.size / 2
        } finally {
            runCatching { track?.stop() }; runCatching { track?.release() }; track = null; released = true; pcm.fill(0)
        }
    }
}
