package it.jarviswake

import android.content.Context
import java.io.File
import java.time.Instant

/** Caller supplies only window categories and control counts/booleans, never UI text. */
internal object NativeRuntimeSnapshot {
    const val FILE_NAME = "native-runtime.log"
    fun save(context: Context, token: String, category: String, metadata: String) {
        val app = context.applicationContext
        val line = "${Instant.now()} cycle=$token category=$category $metadata\n"
        DiagnosticLog.afterPendingWrites {
            runCatching {
                val file = File(app.filesDir, "diagnostics/$FILE_NAME")
                file.parentFile?.mkdirs()
                if (file.length() > 128 * 1024) file.writeText("")
                file.appendText(line.take(8192))
            }
        }
    }
}
