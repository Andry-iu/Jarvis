package it.jarviswake

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.Process

internal object NativeChatSettings {
    const val PACKAGE = "com.openai.chatgpt"
    private fun prefs(context: Context) = context.getSharedPreferences("native_chat", Context.MODE_PRIVATE)
    fun enabled(context: Context) = prefs(context).getBoolean("enabled", false)
    fun enable(context: Context, value: Boolean) { prefs(context).edit().putBoolean("enabled", value).apply() }
    fun id(context: Context) = prefs(context).getString("shortcut", null)
    fun project(context: Context) = prefs(context).getString("project", "Jarvis Runtime").orEmpty()
    fun select(context: Context, shortcut: ShortcutInfo, project: String) {
        prefs(context).edit().putString("shortcut", shortcut.id).putString("project", project.trim()).apply()
    }
    fun shortcuts(context: Context): List<ShortcutInfo> {
        val launcher = context.getSystemService(LauncherApps::class.java)
        check(launcher.hasShortcutHostPermission()) { "Imposta Jarvis come assistente del telefono, poi riprova." }
        val query = LauncherApps.ShortcutQuery().setPackage(PACKAGE).setQueryFlags(
            LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED_BY_ANY_LAUNCHER)
        return launcher.getShortcuts(query, Process.myUserHandle()).orEmpty().filter { it.isEnabled }
    }
    fun launch(context: Context) {
        val selected = shortcuts(context).singleOrNull { it.id == id(context) }
            ?: error("Collegamento ChatGPT non accessibile. Selezionalo nelle impostazioni Jarvis.")
        context.getSystemService(LauncherApps::class.java).startShortcut(selected, null, null)
    }
}
