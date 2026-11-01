package it.jarviswake

import java.net.URI
import java.util.UUID

/** This first bridge accepts only a fresh status request, never an arbitrary command or URL. */
internal object BridgeProtocol {
    const val ORIGIN = "https://jarviswake-link-andrea.autech-sm267-2695.chatgpt.site"
    fun validOrigin(value: String): Boolean = runCatching {
        val u=URI(value)
        value==ORIGIN && u.scheme=="https" && u.rawUserInfo==null && u.rawQuery==null && u.rawFragment==null
    }.getOrDefault(false)
    fun validId(value: String): Boolean = runCatching {
        UUID.fromString(value).toString()==value && UUID.fromString(value).version()==4
    }.getOrDefault(false)
    fun validRequest(id: String, operation: String, created: Long, expires: Long): Boolean =
        validId(id) && operation=="status" && created>0 && expires>created && expires-created<=30_000
}
