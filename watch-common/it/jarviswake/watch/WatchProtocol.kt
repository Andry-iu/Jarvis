package it.jarviswake.watch

import org.json.JSONObject

object WatchProtocol {
    const val PREPARE="/jarviswake/probe/v1/prepare"
    const val SNAPSHOT="/jarviswake/probe/v1/snapshot"
    const val REPLY="/jarviswake/probe/v1/reply"
    const val EXTRA="watch_start_ticket"
    fun id(s:String)=s.matches(Regex("[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}"))
    fun request(bytes:ByteArray):JSONObject?=runCatching {
        require(bytes.size in 1..256)
        JSONObject(bytes.toString(Charsets.UTF_8)).also {require(it.keys().asSequence().toSet()==setOf("id") && id(it.getString("id")))}
    }.getOrNull()
}
