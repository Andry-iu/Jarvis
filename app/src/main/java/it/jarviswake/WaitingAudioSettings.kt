package it.jarviswake

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.util.concurrent.Executors

internal object WaitingAudioSettings {
    private val imports = Executors.newSingleThreadExecutor()
    private fun prefs(context: Context) = context.getSharedPreferences("waiting_audio", Context.MODE_PRIVATE)
    fun file(context: Context) = File(context.noBackupFilesDir, "waiting-audio")
    fun enabled(context: Context) = prefs(context).getBoolean("enabled", false)
    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("enabled", enabled).apply()
        if (!enabled) WaitingAudio.stop()
    }
    fun name(context: Context): String? = prefs(context).getString("name", null)
    fun volume(context: Context) = prefs(context).getInt("volume_percent", 100).coerceIn(0, 100)
    fun setVolume(context: Context, percent: Int) {
        val value = percent.coerceIn(0, 100)
        prefs(context).edit().putInt("volume_percent", value).apply()
        WaitingAudio.updateVolume(value)
    }

    /** Copy while the picker grant is valid; preserve the old file on any failure. */
    fun import(context: Context, uri: Uri, done: (Boolean) -> Unit) {
        val app = context.applicationContext
        imports.execute {
            val temporary = File(app.noBackupFilesDir, "waiting-audio-import")
            val success = runCatching {
                app.contentResolver.openInputStream(uri)?.use { input ->
                    temporary.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var total = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            require(total <= 64L * 1024 * 1024)
                            output.write(buffer, 0, count)
                        }
                        require(total > 0)
                        output.fd.sync()
                    }
                } ?: error("No input")
                val probe = MediaPlayer()
                try {
                    probe.setDataSource(temporary.path)
                    probe.prepare()
                    require(probe.duration > 0 && probe.videoWidth == 0 && probe.videoHeight == 0)
                } finally { probe.release() }
                val displayName = runCatching {
                    app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                        if (it.moveToFirst()) it.getString(0)?.take(160) else null
                    }
                }.getOrNull() ?: app.getString(R.string.waiting_audio_selected)
                check(temporary.renameTo(file(app)))
                prefs(app).edit().putString("name", displayName).apply()
                true
            }.getOrDefault(false)
            temporary.delete()
            android.os.Handler(android.os.Looper.getMainLooper()).post { done(success) }
        }
    }
}
