package it.jarviswake

import android.content.Context
import android.os.SystemClock

/** Fixed stage codes and elapsed durations only. No name, number, text or address. */
internal class BridgeCallTiming(private val context:Context,private val id:String) {
    private val start=SystemClock.elapsedRealtime()
    private val seen=HashSet<String>()
    fun stage(code:String) { if(seen.add(code)) NativeRuntimeSnapshot.save(context,id,"bridge_call","stage=$code elapsed_ms=${SystemClock.elapsedRealtime()-start}") }
}
