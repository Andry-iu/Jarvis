package it.jarviswake

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process

/** Fixed, read-only, same-certificate protocol for the separate Jarvis Cube game. */
class CarCubeSyncProvider : ContentProvider() {
    companion object {
        const val AUTHORITY = "it.jarviswake.cube.status"
        internal fun trusted(context: Context, uid: Int): Boolean = uid == Process.myUid() ||
            (context.packageManager.getPackagesForUid(uid)?.contains("it.jarviswake.cube") == true &&
                context.packageManager.checkSignatures(Process.myUid(), uid) == PackageManager.SIGNATURE_MATCH)
    }
    override fun onCreate() = true
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
                       selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val c = requireNotNull(context)
        if (!trusted(c, Binder.getCallingUid())) throw SecurityException("Caller not allowed")
        require(uri.scheme == "content" && uri.authority == AUTHORITY && uri.path == "/state" &&
            uri.query == null && uri.fragment == null && projection == null && selection == null &&
            selectionArgs == null && sortOrder == null)
        val state = CarCubeSync.current()
        return MatrixCursor(arrayOf("protocol", "cover_visible", "muted", "sampled_elapsed_ms")).apply {
            addRow(arrayOf(1, if (state.visible) 1 else 0, if (state.muted) 1 else 0, state.at))
        }
    }
    override fun getType(uri: Uri) = "vnd.android.cursor.item/vnd.it.jarviswake.cube-state"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException("Read only")
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException("Read only")
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException("Read only")
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? = throw UnsupportedOperationException("Read only")
}
