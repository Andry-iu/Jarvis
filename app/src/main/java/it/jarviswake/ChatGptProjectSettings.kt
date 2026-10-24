package it.jarviswake

import android.content.Context
import java.net.URI
import java.util.Locale

/** Persistent ChatGPT project destination shared by the preloader and Voice activity. */
internal object ChatGptProjectSettings {
    const val DEFAULT_URL =
        "https://chatgpt.com/g/g-p-6adc87c2850c8191b52e9df5d94bfba6/project"

    private const val PREFS = "chatgpt_project"
    private const val KEY_URL = "project_url"
    private val projectPath = Regex("^/g/g-p-[a-z0-9]+(?:-[a-z0-9]+)*/project/?$", RegexOption.IGNORE_CASE)

    fun getUrl(context: Context): String {
        val saved = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_URL, null)
            ?.trim()
        return saved?.takeIf(::isValidProjectUrl) ?: DEFAULT_URL
    }

    fun save(context: Context, value: String): Boolean {
        val url = value.trim()
        if (!isValidProjectUrl(url)) return false
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_URL, url)
            .apply()
        return true
    }

    fun isValidProjectUrl(value: String): Boolean = runCatching {
        val uri = URI(value.trim())
        uri.scheme.equals("https", ignoreCase = true) &&
            uri.host?.lowercase(Locale.ROOT) == "chatgpt.com" &&
            uri.port == -1 &&
            uri.rawUserInfo == null &&
            uri.rawQuery == null &&
            uri.rawFragment == null &&
            projectPath.matches(uri.path.orEmpty())
    }.getOrDefault(false)
}
