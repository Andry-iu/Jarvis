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
internal data class BridgeContact(val name:String,val number:String,val international:String?,val uniqueName:Boolean,val row:Long=-1,val query:String="",val label:String="",val favoriteId:String?=null,val favoriteRevision:Int=0)

internal object BridgeContacts {
    internal var readStore:(Context)->JSONObject={BridgeSettings.readPrivate(it,"contacts")}
    internal var writeStore:(Context,JSONObject)->Unit={c,j->BridgeSettings.writePrivate(c,"contacts",j)}
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
    private data class SearchSnapshot(val device:String,val query:String,val rows:List<JSONObject>,val created:Long,val wall:Long,
        val store:JSONObject=JSONObject(),val references:MutableMap<Int,String> = mutableMapOf())
    private var searchSnapshot:SearchSnapshot?=null
    @Synchronized fun search(c:Context,deviceId:String,name:String,offset:Int=0):JSONObject {
        if(!BridgeProtocol.validText(name,80)) throw BridgeFailure("unavailable")
        if(offset !in 0..10000 || offset%20!=0) throw BridgeFailure("unavailable")
        if(offset>0) {
            val old=searchSnapshot ?: throw BridgeFailure("contact_expired")
            if(old.device!=deviceId || old.query!=name || SystemClock.elapsedRealtime()-old.created !in 0..600_000 ||
                System.currentTimeMillis()-old.wall !in 0..600_000) throw BridgeFailure("contact_expired")
            // Permission/revocation and the live row are rechecked again by resolve before any use.
            if(c.checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED &&
                old.rows.any {it.optLong("row",-1)>=0}) throw BridgeFailure("permission_required")
            return searchPage(c,old,offset)
        }
        val rows=ArrayList<JSONObject>();val seen=HashSet<Pair<String,String>>()
        val favorites=BridgeContactFavorites.matches(c,name)
        val canRead=c.checkSelfPermission(Manifest.permission.READ_CONTACTS)==PackageManager.PERMISSION_GRANTED
        if(!canRead && favorites.none {it.getString("kind")=="manual"}) throw BridgeFailure("permission_required")
        val uri=Uri.withAppendedPath(Phone.CONTENT_FILTER_URI,Uri.encode(name))
        if(canRead) c.contentResolver.query(uri,projection,null,null,Phone.DISPLAY_NAME+" ASC")?.use { cursor ->
            while(cursor.moveToNext()) {
                if(rows.size>=10000) throw BridgeFailure("contacts_search_too_large")
                val normalized=runCatching { number(cursor.getString(2).orEmpty()) }.getOrNull() ?: continue
                val display=cursor.getString(1).orEmpty().take(100)
                if(!seen.add(normalized to display)) continue
                val custom=cursor.getString(4)?.take(40)
                val label=("Rubrica · "+Phone.getTypeLabel(c.resources,cursor.getInt(3),custom)).take(40)
                rows.add(JSONObject().put("row",cursor.getLong(0)).put("name",display).put("number",normalized).put("label",label))
            }
        }
        for(f in favorites) {
            if(f.getString("kind")=="book" && !bookMatches(c,f)) continue
            val item=JSONObject().put("row",f.getLong("row")).put("name",f.getString("name")).put("number",f.getString("number"))
                .put("label",(if(f.getString("kind")=="manual") "Preferito JarvisWake" else "Preferito · "+f.optString("label")).take(40))
                .put("favorite_id",f.getString("id")).put("favorite_revision",f.getInt("revision"))
                .put("book_name",f.optString("book_name",f.getString("name")))
            val duplicate=rows.indexOfFirst {BridgeContactCache.normalize(it.getString("name"))==BridgeContactCache.normalize(f.getString("name")) && it.getString("number")==f.getString("number")}
            if(duplicate>=0) rows[duplicate]=item else rows.add(item)
        }
        val ranks=runCatching {BridgeContactCache.entries(c).filter {it.optLong("row",-1)>=0}.mapIndexed {i,r->r.optLong("row") to (20-i)}.toMap()}.getOrDefault(emptyMap())
        rows.sortByDescending {ranks[it.optLong("row")] ?: 0}
        if(rows.size>10000 || rows.sumOf {it.toString().toByteArray(Charsets.UTF_8).size.toLong()}>8*1024*1024) throw BridgeFailure("contacts_search_too_large")
        val current=SearchSnapshot(deviceId,name,rows,SystemClock.elapsedRealtime(),System.currentTimeMillis())
        searchSnapshot=current
        return searchPage(c,current,0)
    }
    private fun searchPage(c:Context,current:SearchSnapshot,offset:Int):JSONObject {
        if(offset>current.rows.size) throw BridgeFailure("contact_expired")
        val store=current.store;val output=JSONArray()
        val end=minOf(offset+20,current.rows.size)
        for(index in offset until end) {
            val row=current.rows[index]
            val ref=current.references.getOrPut(index) {UUID.randomUUID().toString()}
            row.put("query",current.query).put("device",current.device).put("created",current.created).put("wall",current.wall)
                .put("unique",current.rows.count {it.getString("name")==row.getString("name")}==1)
            store.put(ref,row)
            output.put(JSONObject().put("contact_ref",ref).put("name",row.getString("name"))
                .put("label",row.getString("label")).put("suffix",row.getString("number").takeLast(4)))
        }
        writeStore(c,store)
        return result("contacts_found").put("contacts",output).put("truncated",end<current.rows.size)
            .put("next_offset",if(end<current.rows.size) end else JSONObject.NULL)
    }
    fun resolve(c:Context,deviceId:String,ref:String):BridgeContact {
        val saved=readStore(c).optJSONObject(ref) ?: throw BridgeFailure("contact_expired")
        val age=SystemClock.elapsedRealtime()-saved.optLong("created")
        if(saved.optString("device")!=deviceId || age !in 0..600_000 || System.currentTimeMillis()-saved.optLong("wall") !in 0..600_000) throw BridgeFailure("contact_expired")
        val favoriteId=saved.optString("favorite_id").takeIf {it.isNotEmpty()}
        if(favoriteId!=null) {
            val f=BridgeContactFavorites.find(c,favoriteId) ?: throw BridgeFailure("contact_changed")
            if(f.getInt("revision")!=saved.optInt("favorite_revision") || f.getString("name")!=saved.getString("name") || f.getString("number")!=saved.getString("number")) throw BridgeFailure("contact_changed")
            if(f.getString("kind")=="manual") {
                val n=BridgeContactFavorites.phone(f.getString("number"))
                val international=PhoneNumberUtils.formatNumberToE164(n,region(c))?.takeIf {it.matches(Regex("\\+[0-9]{5,15}"))}
                return BridgeContact(f.getString("name"),n,international,saved.optBoolean("unique"),-1,saved.optString("query"),saved.optString("label"),favoriteId,f.getInt("revision"))
            }
        }
        requirePermission(c)
        c.contentResolver.query(Phone.CONTENT_URI,projection,Phone._ID+"=?",arrayOf(saved.getLong("row").toString()),null)?.use { cursor ->
            if(cursor.moveToFirst()) {
                val current=number(cursor.getString(2).orEmpty())
                val name=cursor.getString(1).orEmpty().take(100)
                if(name!=saved.optString("book_name",saved.getString("name")) || current!=saved.getString("number")) throw BridgeFailure("contact_changed")
                val international=PhoneNumberUtils.formatNumberToE164(current,region(c))
                    ?.takeIf { it.matches(Regex("\\+[0-9]{5,15}")) }
                return BridgeContact(saved.getString("name"),current,international,saved.optBoolean("unique"),saved.getLong("row"),saved.optString("query"),saved.optString("label"),favoriteId,saved.optInt("favorite_revision"))
            }
        }
        throw BridgeFailure("contact_changed")
    }
    fun directRef(c:Context,deviceId:String,name:String,timing:BridgeCallTiming):JSONObject {
        val cached=runCatching {BridgeContactCache.candidate(c,name)}.getOrNull()
        timing.stage(if(cached==null) "cache_miss" else "cache_candidate")
        val current=search(c,deviceId,name).also {it.remove("next_offset")}
        val contacts=current.getJSONArray("contacts")
        if(cached!=null) {
            val j=readStore(c)
            val matching=(0 until contacts.length()).map {j.getJSONObject(contacts.getJSONObject(it).getString("contact_ref"))}
                .any {it.optLong("row")==cached.optLong("row") && it.optString("number")==cached.optString("number") && it.optString("name")==cached.optString("name")}
            if(!matching) BridgeContactCache.remove(c,cached.optString("key",cached.optLong("row").toString()))
            timing.stage(if(matching) "cache_validated" else "cache_invalidated")
        }
        timing.stage("contacts_resolved")
        return current
    }
    fun importFavorites(c:Context) {
        requirePermission(c)
        c.contentResolver.query(Phone.CONTENT_URI,projection,Phone.STARRED+"=1",null,Phone.DISPLAY_NAME+" ASC")?.use { cursor ->
            var count=0
            while(count<20 && cursor.moveToNext()) {
                val n=runCatching {number(cursor.getString(2).orEmpty())}.getOrNull() ?: continue
                val name=cursor.getString(1).orEmpty().take(100)
                val label=Phone.getTypeLabel(c.resources,cursor.getInt(3),cursor.getString(4)).toString().take(40)
                BridgeContactFavorites.save(c,name,n,cursor.getLong(0),label);count++
            }
        }
    }

    private fun bookMatches(c:Context,f:JSONObject):Boolean {
        requirePermission(c)
        return c.contentResolver.query(Phone.CONTENT_URI,projection,Phone._ID+"=?",arrayOf(f.getLong("row").toString()),null)?.use {cur->
            cur.moveToFirst() && cur.getString(1)==f.optString("book_name",f.getString("name")) && runCatching {number(cur.getString(2).orEmpty())}.getOrNull()==f.getString("number")
        }==true
    }
    fun addFavoriteFromUri(c:Context,uri:Uri):JSONObject {
        require(uri.scheme=="content" && uri.authority==android.provider.ContactsContract.AUTHORITY) {"Seleziona un numero dalla rubrica del telefono."}
        requirePermission(c)
        return c.contentResolver.query(uri,projection,null,null,null)?.use {cur->
            require(cur.moveToFirst()) {"Numero non disponibile."}
            val row=cur.getLong(0);val name=cur.getString(1).orEmpty();val n=number(cur.getString(2).orEmpty())
            val probe=JSONObject().put("row",row).put("name",name).put("number",n)
            require(bookMatches(c,probe)) {"Contatto cambiato. Selezionalo di nuovo."}
            BridgeContactFavorites.save(c,name,n,row,Phone.getTypeLabel(c.resources,cur.getInt(3),cur.getString(4)).toString())
        } ?: throw IllegalStateException("Numero non disponibile.")
    }

}
