package it.jarviswake

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Small, user-triggered picker flow for exporting only the bounded synthetic event logs. */
class DiagnosticExportActivity : Activity() {
    companion object {
        private const val REQUEST_EXPORT = 901
        private const val NATIVE_TOKEN = "native_export_token"
        fun open(context: Context, nativeToken: String? = null) {
            context.startActivity(Intent(context, DiagnosticExportActivity::class.java)
                .putExtra(NATIVE_TOKEN, nativeToken)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION))
        }
    }

    override fun onDestroy() {
        if (isFinishing) VoiceAccessibilityService.restoreNativeAfterExport(intent.getStringExtra(NATIVE_TOKEN))
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val picker = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
            putExtra(Intent.EXTRA_TITLE, "jarvis-diagnostics.zip")
        }
        runCatching { startActivityForResult(picker, REQUEST_EXPORT) }
            .onFailure { Toast.makeText(this, "Esportazione diagnostica non disponibile.", Toast.LENGTH_LONG).show(); finish() }
    }

    @Deprecated("Activity result callback retained for the minimum supported API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_EXPORT) { finish(); return }
        val uri = data?.data
        if (resultCode == RESULT_OK && uri != null) export(uri)
        else finish()
    }

    private fun export(uri: Uri) {
        DiagnosticLog.afterPendingWrites {
            val success = runCatching {
                val logs = java.io.File(filesDir, "diagnostics").listFiles()
                    ?.filter { it.isFile && (it.name.matches(Regex("events-[0-9]+\\.log")) || it.name == NativeControlSnapshot.FILE_NAME) }
                    ?.sortedBy { it.name }.orEmpty()
                contentResolver.openOutputStream(uri, "w")?.use { output ->
                    ZipOutputStream(output).use { zip ->
                        logs.forEach { file ->
                            zip.putNextEntry(ZipEntry(file.name))
                            file.inputStream().use { it.copyTo(zip) }
                            zip.closeEntry()
                        }
                    }
                } ?: error("Destinazione non disponibile")
            }.isSuccess
            runOnUiThread {
                Toast.makeText(this, if (success) "Diagnostica esportata." else
                    "Esportazione diagnostica non riuscita.", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }
}
