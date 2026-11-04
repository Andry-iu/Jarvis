package it.jarviswake

import android.content.Context
import android.annotation.SuppressLint

/** Text sent before native Voice, also used by the hidden next-chat preparation. */
internal object OpeningMessageSettings {
    private fun prefs(c:Context)=c.applicationContext.getSharedPreferences("opening_message",Context.MODE_PRIVATE)
    fun get(c:Context):String=prefs(c).getString("text",OpeningMessagePolicy.TEXT)
        ?: OpeningMessagePolicy.TEXT
    @SuppressLint("ApplySharedPref") // Persist the choice before invalidating its prepared conversation.
    fun save(c:Context,raw:String):Boolean {
        val text=raw.replace("\r\n","\n").replace('\r','\n').trim()
        if(VoiceCycle.active) return false
        if(text==get(c)) return true
        if(!prefs(c).edit().putString("text",text).commit()) return false
        PreparedChat.openingMessageChanged(c)
        return true
    }
    fun reset(c:Context):Boolean=save(c,OpeningMessagePolicy.TEXT)
}
