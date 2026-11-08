package it.jarviswake.cube

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock

internal data class CubeLink(val code: String, val visible: Boolean = false, val muted: Boolean = true)

/** A fixed signature-protected read. No app launch, audio, display control, or remote data. */
internal object CubeSyncClient {
    private val uri = Uri.parse("content://it.jarviswake.cube.status/state")
    fun read(c: Context): CubeLink = try {
        val provider = c.packageManager.resolveContentProvider("it.jarviswake.cube.status", 0)
        if (provider == null) CubeLink("missing")
        else if (provider.packageName != "it.jarviswake" ||
            c.packageManager.checkSignatures(c.packageName, "it.jarviswake") != PackageManager.SIGNATURE_MATCH) CubeLink("untrusted")
        else c.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.count != 1 || cursor.columnNames.toList() != listOf("protocol", "cover_visible", "muted", "sampled_elapsed_ms") || !cursor.moveToFirst()) CubeLink("protocol")
            else {
                val protocol = cursor.getInt(0); val visible = cursor.getInt(1); val muted = cursor.getInt(2)
                val at = cursor.getLong(3); val now = SystemClock.elapsedRealtime()
                when {
                    protocol != 1 || visible !in 0..1 || muted !in 0..1 -> CubeLink("protocol")
                    at < 0 || now < at || now - at > 1500L -> CubeLink("stale")
                    else -> CubeLink("linked", visible == 1, muted == 1)
                }
            }
        } ?: CubeLink("unavailable")
    } catch (_: RuntimeException) { CubeLink("unavailable") }
}
