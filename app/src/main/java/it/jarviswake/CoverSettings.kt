package it.jarviswake

import android.content.Context
import android.content.SharedPreferences

/** One persistent appearance setting shared by every Jarvis cover. */
object CoverSettings {
    const val KEY_OPAQUE = "opaque"

    fun preferences(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences("cover_appearance", Context.MODE_PRIVATE)

    fun isOpaque(context: Context): Boolean = preferences(context).getBoolean(KEY_OPAQUE, true)

    fun setOpaque(context: Context, opaque: Boolean) {
        preferences(context).edit().putBoolean(KEY_OPAQUE, opaque).apply()
    }
}
