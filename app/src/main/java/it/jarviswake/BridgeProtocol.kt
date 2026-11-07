package it.jarviswake

import java.net.URI
import java.util.UUID

/** Fixed origin and bounded operations: no generic URL, intent, shell or UI command. */
internal object BridgeProtocol {
    const val ORIGIN = "https://jarviswake-link-andrea.autech-sm267-2695.chatgpt.site"
    fun validOrigin(value: String): Boolean = runCatching {
        val u=URI(value)
        value==ORIGIN && u.scheme=="https" && u.rawUserInfo==null && u.rawQuery==null && u.rawFragment==null
    }.getOrDefault(false)
    fun validId(value: String): Boolean = runCatching {
        UUID.fromString(value).toString()==value && UUID.fromString(value).version()==4 && UUID.fromString(value).variant()==2
    }.getOrDefault(false)
    fun validRequest(id: String, operation: String, created: Long, expires: Long): Boolean =
        validId(id) && operation in OPERATIONS && created>0 && expires>created && expires-created<=30_000
    val FAVORITE_OPERATIONS=setOf("list_favorites","add_favorite","create_favorite","rename_favorite","change_favorite_number","delete_favorite")
    val CONTACT_EXPORT_OPERATIONS=setOf("contacts_export_start","contacts_export_page","get_contact")
    val NOTE_OPERATIONS=setOf("list_notes","read_note","save_note")
    val OPERATIONS=setOf("status","speak","find_contacts","navigate","call_contact","call_by_name","whatsapp")+NOTE_OPERATIONS+FAVORITE_OPERATIONS+CONTACT_EXPORT_OPERATIONS
    fun validText(value:String,max:Int)=value.isNotBlank() && value.length<=max &&
        value.none { it.code in 0..8 || it.code in 11..12 || it.code in 14..31 }
    fun safePhone(value:String)=value.matches(Regex("\\+?[0-9]{5,15}"))
    // Subtract the whole round trip conservatively; a wrong phone wall clock cannot extend a lease.
    fun remainingLease(advertised:Long,roundTrip:Long)=(advertised.coerceAtMost(25_000)-roundTrip.coerceAtLeast(0)).coerceAtLeast(0)
}
