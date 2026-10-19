package it.jarviswake

import android.content.Context

object State {
    @Volatile var running = false
    fun set(context: Context, message: String) {
        context.getSharedPreferences("status", 0).edit().putString("message", message).apply()
    }
    fun setAttempt(context: Context, message: String) {
        context.getSharedPreferences("status", 0).edit().putString("attempt", message).apply()
    }
    fun get(context: Context) = context.getSharedPreferences("status", 0)
        .getString("message", "Pronto. Avvia l’ascolto per iniziare.")!!
    fun getAttempt(context: Context) = context.getSharedPreferences("status", 0)
        .getString("attempt", "Nessun ciclo manuale o wake rilevato.")!!
}
