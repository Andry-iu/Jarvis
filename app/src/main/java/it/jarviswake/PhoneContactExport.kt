package it.jarviswake

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.Email
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

/** Explicit one-way export. Frozen bytes live only in RAM, never in audio/UI/diagnostic storage. */
internal object PhoneContactExport {
    const val CHUNK=6144
    const val MAX_BYTES=8*1024*1024
    const val TTL=30*60*1000L
    private data class Snapshot(val device:String,val id:String,val bytes:ByteArray,val until:Long,val expires:Long)
    private var snapshot:Snapshot?=null
    internal var now:()->Long=SystemClock::elapsedRealtime
    fun enabled(c:Context)=c.getSharedPreferences("contact_export",Context.MODE_PRIVATE).getBoolean("enabled",false)
    @Synchronized fun enable(c:Context,value:Boolean) {
        c.getSharedPreferences("contact_export",Context.MODE_PRIVATE).edit().putBoolean("enabled",value).commit()
        if(!value) clear()
    }
    @Synchronized fun clear() { snapshot?.bytes?.fill(0);snapshot=null }
    private fun checkAllowed(allowed:()->Boolean) { if(!allowed()) throw BridgeFailure("cancelled") }
    private fun text(v:String?):String { val s=v.orEmpty();if(s.length>8192) throw BridgeFailure("contacts_export_too_large");return s }
    /** No filtering/deduplication by number or name: Android aggregate IDs and data-row IDs are retained. */
    internal fun collect(c:Context,selected:BridgeContact?,allowed:()->Boolean):JSONObject {
        val rows=linkedMapOf<String,JSONObject>()
        fun query(uri:Uri,projection:Array<String>,selection:String?=null,args:Array<String>?=null,visit:(android.database.Cursor)->Unit) {
            checkAllowed(allowed)
            val signal=android.os.CancellationSignal()
            // Provider execution is bounded even if it blocks before returning its cursor.
            val watchdog=java.util.Timer(true)
            val queryUntil=now()+20_000
            watchdog.schedule(object:java.util.TimerTask(){ override fun run(){ if(now()>=queryUntil || !allowed()) signal.cancel() } },100,100)
            try { (c.contentResolver.query(uri,projection,selection,args,null,signal) ?: throw BridgeFailure("contacts_export_unavailable")).use { cursor ->
                while(cursor.moveToNext()) { checkAllowed(allowed);visit(cursor) }
            } } finally { watchdog.cancel() }
        }
        var selectedId:String?=null
        if(selected!=null && selected.row>=0) {
            query(Phone.CONTENT_URI,arrayOf(Phone.CONTACT_ID),Phone._ID+"=?",arrayOf(selected.row.toString())) { selectedId=it.getString(0) }
            if(selectedId==null) throw BridgeFailure("contact_changed")
        }
        if(selected!=null && selected.row<0) {
            val id="favorite:"+(selected.favoriteId ?: throw BridgeFailure("contact_changed"))
            rows[id]=JSONObject().put("id",id).put("lookup_key","").put("name",selected.name)
                .put("phones",JSONArray().put(JSONObject().put("id",id).put("number",selected.number).put("label",selected.label))).put("emails",JSONArray())
        } else {
            val projection=arrayOf(Contacts._ID,Contacts.LOOKUP_KEY,Contacts.DISPLAY_NAME_PRIMARY,Contacts.CONTACT_LAST_UPDATED_TIMESTAMP)
            fun headers():Map<String,String> {
                val stamps=linkedMapOf<String,String>();var headerSize=0L
                query(Contacts.CONTENT_URI,projection,if(selectedId==null) null else Contacts._ID+"=?",selectedId?.let {arrayOf(it)}) { cur ->
                    val id=cur.getString(0);stamps[id]=cur.getString(3)+":"+text(cur.getString(1))+":"+text(cur.getString(2))
                    headerSize+=stamps.getValue(id).length+100
                    if(headerSize>MAX_BYTES) throw BridgeFailure("contacts_export_too_large")
                    if(stamps.size>100_000) throw BridgeFailure("contacts_export_too_large")
                    if(rows.isEmpty() || !rows.containsKey(id)) rows[id]=JSONObject().put("id",id).put("lookup_key",text(cur.getString(1)))
                        .put("name",text(cur.getString(2))).put("phones",JSONArray()).put("emails",JSONArray())
                };return stamps
            }
            val before=headers();var size=0L
            query(Phone.CONTENT_URI,arrayOf(Phone.CONTACT_ID,Phone._ID,Phone.NUMBER,Phone.TYPE,Phone.LABEL),
                if(selected==null) null else Phone._ID+"=?",selected?.let {arrayOf(it.row.toString())}) { cur ->
                val row=rows[cur.getString(0)] ?: throw BridgeFailure("contacts_export_changed")
                val n=text(cur.getString(2));val label=text(Phone.getTypeLabel(c.resources,cur.getInt(3),cur.getString(4)).toString())
                size+=n.length+label.length+100;if(size>MAX_BYTES) throw BridgeFailure("contacts_export_too_large")
                row.getJSONArray("phones").put(JSONObject().put("id",cur.getString(1)).put("number",n).put("label",label))
            }
            if(selected==null) query(Email.CONTENT_URI,arrayOf(Email.CONTACT_ID,Email._ID,Email.ADDRESS,Email.TYPE,Email.LABEL)) { cur ->
                val row=rows[cur.getString(0)] ?: throw BridgeFailure("contacts_export_changed")
                val a=text(cur.getString(2));val label=text(Email.getTypeLabel(c.resources,cur.getInt(3),cur.getString(4)).toString())
                size+=a.length+label.length+100;if(size>MAX_BYTES) throw BridgeFailure("contacts_export_too_large")
                row.getJSONArray("emails").put(JSONObject().put("id",cur.getString(1)).put("address",a).put("label",label))
            }
            if(before!=headers()) throw BridgeFailure("contacts_export_changed")
            if(selected!=null && (rows.size!=1 || rows.values.single().getJSONArray("phones").length()!=1)) throw BridgeFailure("contact_changed")
        }
        checkAllowed(allowed)
        return JSONObject().put("schema",1).put("mode",if(selected==null) "full" else "number").put("contacts",JSONArray(rows.values.toList()))
    }
    @Synchronized internal fun freeze(device:String,body:JSONObject,allowed:()->Boolean):JSONObject {
        val bytes=body.toString().toByteArray(Charsets.UTF_8)
        if(bytes.size>MAX_BYTES) throw BridgeFailure("contacts_export_too_large")
        checkAllowed(allowed);clear()
        val s=Snapshot(device,UUID.randomUUID().toString(),bytes,now()+TTL,System.currentTimeMillis()+TTL);snapshot=s
        val contacts=body.getJSONArray("contacts");var phones=0;var emails=0
        for(i in 0 until contacts.length()) { phones+=contacts.getJSONObject(i).getJSONArray("phones").length();emails+=contacts.getJSONObject(i).getJSONArray("emails").length() }
        return result("contacts_export_ready").put("snapshot_id",s.id).put("bytes",bytes.size).put("chunk_bytes",CHUNK)
            .put("sha256",MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)})
            .put("contacts",contacts.length()).put("phones",phones).put("emails",emails).put("mode",body.getString("mode")).put("expires_at",s.expires)
    }
    @Synchronized internal fun page(device:String,id:String,offset:Int,allowed:()->Boolean):JSONObject {
        val s=snapshot ?: throw BridgeFailure("contacts_export_expired")
        if(now()>=s.until) { clear();throw BridgeFailure("contacts_export_expired") }
        if(device!=s.device || id!=s.id) throw BridgeFailure("contacts_export_expired")
        if(offset<0 || offset>=s.bytes.size || offset%CHUNK!=0) throw BridgeFailure("invalid_export_offset")
        checkAllowed(allowed);val end=minOf(offset+CHUNK,s.bytes.size)
        return result("contacts_export_page").put("snapshot_id",s.id).put("offset",offset).put("expires_at",s.expires)
            .put("next_offset",if(end==s.bytes.size) JSONObject.NULL else end)
            .put("data_base64",Base64.encodeToString(s.bytes.copyOfRange(offset,end),Base64.NO_WRAP))
    }
    fun execute(c:Context,device:String,op:String,p:JSONObject,allowed:()->Boolean):JSONObject = try {
        if(!enabled(c)) throw BridgeFailure("contacts_export_disabled")
        if(c.checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED) throw BridgeFailure("permission_required")
        checkAllowed(allowed)
        when(op) {
            "contacts_export_start" -> {
                val ref=p.getString("contact_ref")
                if(ref.isNotEmpty() && !BridgeProtocol.validId(ref)) throw BridgeFailure("contact_changed")
                val selected=if(ref.isEmpty()) null else BridgeContacts.resolve(c,device,ref)
                val body=collect(c,selected,allowed)
                if(selected!=null) BridgeContacts.resolve(c,device,ref) // Revalidate selection before releasing any complete number.
                freeze(device,body,allowed)
            }
            "contacts_export_page" -> page(device,p.getString("snapshot_id"),p.getInt("offset"),allowed)
            else -> result("unsupported")
        }
    } catch(e:BridgeFailure) { result(e.code) }
    catch(_:SecurityException) { result("permission_required") }
    catch(_:Exception) { result("contacts_export_unavailable") }
}
