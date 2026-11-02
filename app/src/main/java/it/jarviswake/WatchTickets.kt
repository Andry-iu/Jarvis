package it.jarviswake

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import org.json.JSONObject
import it.jarviswake.watch.WatchProtocol

/** Data Layer creates a short-lived ticket before a browsable launch. Never accept arbitrary launch extras. */
internal object WatchTickets {
    private val process=java.util.UUID.randomUUID().toString()
    private fun prefs(c:Context)=c.getSharedPreferences("watch_probe",0)
    fun enabled(c:Context)=prefs(c).getBoolean("enabled",false)
    @Synchronized fun enable(c:Context,value:Boolean) {prefs(c).edit().putBoolean("enabled",value).remove("ticket").apply()}
    private fun boot(c:Context)=Settings.Global.getInt(c.contentResolver,Settings.Global.BOOT_COUNT,-1)
    @Synchronized fun prepare(c:Context,id:String):String {
        if(!enabled(c)) return "disabled"
        if(!WatchProtocol.id(id)) return "invalid_request"
        val p=prefs(c)
        if(p.getString("seen","").orEmpty().split(',').contains(id)) return "replayed"
        val seen=(p.getString("seen","").orEmpty().split(',').filter(String::isNotEmpty)+id).takeLast(32)
        val ticket=JSONObject().put("id",id).put("at",SystemClock.elapsedRealtime()).put("boot",boot(c)).put("stage","prepared").put("process",process)
        check(p.edit().putString("ticket",ticket.toString()).putString("seen",seen.joinToString(",")).commit())
        return "prepared"
    }
    @Synchronized fun transition(c:Context,id:String,from:String,to:String):Boolean {
        if(!enabled(c) || !WatchProtocol.id(id)) return false
        val p=prefs(c);val j=runCatching {JSONObject(p.getString("ticket",null) ?: return false)}.getOrNull() ?: return false
        val elapsed=SystemClock.elapsedRealtime()-j.optLong("at",Long.MIN_VALUE)
        if(j.optString("process")!=process || j.optString("id")!=id || j.optString("stage")!=from || j.optInt("boot",-2)!=boot(c) || elapsed !in 0..60000) return false
        return p.edit().putString("ticket",j.put("stage",to).toString()).commit()
    }
}
