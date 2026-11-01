package it.jarviswake

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

internal class BridgeFailure(val code:String):Exception()
internal fun result(code:String)=JSONObject().put("code",code)
internal data class BridgeContact(val name:String,val number:String,val international:String?,val uniqueName:Boolean)

internal object BridgeContacts {
    private val projection=arrayOf(Phone._ID,Phone.DISPLAY_NAME,Phone.NUMBER,Phone.TYPE,Phone.LABEL)
    private fun requirePermission(c:Context) {
        if(c.checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED) throw BridgeFailure("permission_required")
    }
    private fun number(value:String):String {
        if(value.any { it in "*#;,Nn" }) throw BridgeFailure("invalid_number")
        return PhoneNumberUtils.normalizeNumber(value).takeIf(BridgeProtocol::safePhone) ?: throw BridgeFailure("invalid_number")
    }
    private fun region(c:Context)=runCatching {
        c.getSystemService(TelephonyManager::class.java).networkCountryIso.uppercase(Locale.ROOT)
    }.getOrNull()?.takeIf { it.length==2 } ?: Locale.getDefault().country
    fun search(c:Context,deviceId:String,name:String):JSONObject {
        requirePermission(c)
        if(!BridgeProtocol.validText(name,80)) throw BridgeFailure("unavailable")
        val rows=ArrayList<JSONObject>()
        val uri=Uri.withAppendedPath(Phone.CONTENT_FILTER_URI,Uri.encode(name))
        c.contentResolver.query(uri,projection,null,null,Phone.DISPLAY_NAME+" ASC")?.use { cursor ->
            while(cursor.moveToNext() && rows.size<40) {
                val normalized=runCatching { number(cursor.getString(2).orEmpty()) }.getOrNull() ?: continue
                val display=cursor.getString(1).orEmpty().take(100)
                if(rows.any { it.getString("number")==normalized && it.getString("name")==display }) continue
                val custom=cursor.getString(4)?.take(40)
                val label=Phone.getTypeLabel(c.resources,cursor.getInt(3),custom).toString().take(40)
                rows.add(JSONObject().put("row",cursor.getLong(0)).put("name",display).put("number",normalized).put("label",label))
            }
        }
        val store=JSONObject();val output=JSONArray();val now=SystemClock.elapsedRealtime()
        // Replace the old search, so another contact query cannot silently select an earlier result.
        for(row in rows.take(8)) {
            val ref=UUID.randomUUID().toString()
            row.put("device",deviceId).put("created",now).put("wall",System.currentTimeMillis())
                .put("unique",rows.size<40 && rows.count { it.getString("name")==row.getString("name") }==1)
            store.put(ref,row)
            output.put(JSONObject().put("contact_ref",ref).put("name",row.getString("name"))
                .put("label",row.getString("label")).put("suffix",row.getString("number").takeLast(4)))
        }
        BridgeSettings.writePrivate(c,"contacts",store)
        return result("contacts_found").put("contacts",output).put("truncated",rows.size>8)
    }
    fun resolve(c:Context,deviceId:String,ref:String):BridgeContact {
        requirePermission(c)
        val saved=BridgeSettings.readPrivate(c,"contacts").optJSONObject(ref) ?: throw BridgeFailure("contact_expired")
        val age=SystemClock.elapsedRealtime()-saved.optLong("created")
        if(saved.optString("device")!=deviceId || age !in 0..600_000 || System.currentTimeMillis()-saved.optLong("wall") !in 0..600_000) throw BridgeFailure("contact_expired")
        c.contentResolver.query(Phone.CONTENT_URI,projection,Phone._ID+"=?",arrayOf(saved.getLong("row").toString()),null)?.use { cursor ->
            if(cursor.moveToFirst()) {
                val current=number(cursor.getString(2).orEmpty())
                val name=cursor.getString(1).orEmpty().take(100)
                if(name!=saved.getString("name") || current!=saved.getString("number")) throw BridgeFailure("contact_changed")
                val international=PhoneNumberUtils.formatNumberToE164(current,region(c))
                    ?.takeIf { it.matches(Regex("\\+[0-9]{5,15}")) }
                return BridgeContact(name,current,international,saved.optBoolean("unique"))
            }
        }
        throw BridgeFailure("contact_changed")
    }
}
