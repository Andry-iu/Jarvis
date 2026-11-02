package it.jarviswake

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract.CommonDataKinds.Phone
import org.json.JSONArray
import org.json.JSONObject

internal class FavoriteFailure(val code:String):Exception()

/** No Android contact writes. Only the app-private favorite archive is mutable. */
internal object FavoriteOperations {
    fun bookConflict(c:Context,f:JSONObject):String {
        if(c.checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED) return "unavailable"
        return runCatching {
            val row=f.optLong("origin_row",f.optLong("row",-1))
            val uri=if(row>=0) Phone.CONTENT_URI else android.net.Uri.withAppendedPath(Phone.CONTENT_FILTER_URI,android.net.Uri.encode(f.getString("name")))
            c.contentResolver.query(uri,arrayOf(Phone.NUMBER,Phone.DISPLAY_NAME),if(row>=0) Phone._ID+"=?" else null,if(row>=0) arrayOf(row.toString()) else null,null)?.use {cursor->
                var found=false;var differs=false
                while(cursor.moveToNext()) {
                    if(row<0 && BridgeContactCache.normalize(cursor.getString(1).orEmpty())!=BridgeContactCache.normalize(f.getString("name"))) continue
                    found=true
                    if(runCatching {BridgeContactFavorites.phone(cursor.getString(0).orEmpty())}.getOrNull()!=f.getString("number")) differs=true
                }
                if(differs) "different" else if(row>=0 && !found) "unavailable" else "none"
            } ?: "unavailable"
        }.getOrDefault("unavailable")
    }
    fun metadata(c:Context,f:JSONObject)=JSONObject().put("favorite_id",f.getString("id")).put("name",f.getString("name"))
        .put("suffix",f.getString("number").takeLast(4)).put("source",f.getString("kind"))
        .put("revision",f.getInt("revision")).put("book_conflict",bookConflict(c,f))
    fun cleanCache(c:Context,before:JSONObject):String=runCatching {BridgeContactCache.invalidateFavorite(c,before);"cleared"}.getOrDefault("unconfirmed")
    fun execute(c:Context,device:String,op:String,p:JSONObject,allowed:()->Boolean):JSONObject = try {
        if(!allowed()) throw FavoriteFailure("expired_before_dispatch")
        when(op) {
            "list_favorites" -> {
                val name=p.getString("name");require(name.isEmpty() || BridgeProtocol.validText(name,80))
                val offset=p.getInt("offset");require(offset in 0..100)
                val all=BridgeContactFavorites.entries(c).filter {name.isEmpty() || BridgeContactCache.normalize(it.getString("name")).contains(BridgeContactCache.normalize(name))}
                val page=all.drop(offset).take(20)
                val response=result("favorites_listed").put("favorites",JSONArray().apply {page.forEach {put(metadata(c,it))}})
                    .put("next_offset",if(offset+page.size<all.size) offset+page.size else JSONObject.NULL)
                if(!allowed()) throw FavoriteFailure("expired_before_dispatch");response
            }
            "add_favorite" -> {
                val contact=BridgeContacts.resolve(c,device,p.getString("contact_ref"))
                val existing=contact.favoriteId?.let {BridgeContactFavorites.find(c,it)} ?:
                    BridgeContactFavorites.entries(c).firstOrNull {contact.row>=0 && it.optString("kind")=="book" && it.optLong("row")==contact.row && it.getString("number")==contact.number}
                val f=existing ?: BridgeContactFavorites.save(c,contact.name,contact.number,contact.row,contact.label,allowed)
                if(existing!=null && !allowed()) throw FavoriteFailure("expired_before_dispatch")
                result("favorite_saved").put("favorite",metadata(c,f))
            }
            "create_favorite" -> {
                val f=BridgeContactFavorites.save(c,p.getString("name"),p.getString("number"),allowed=allowed)
                result("favorite_saved").put("favorite",metadata(c,f))
            }
            "rename_favorite","change_favorite_number" -> {
                val (old,f)=BridgeContactFavorites.change(c,p.getString("favorite_id"),p.getInt("expected_revision"),
                    name=if(op=="rename_favorite") p.getString("name") else null,
                    number=if(op=="change_favorite_number") p.getString("number") else null,allowed=allowed)
                val cache=cleanCache(c,old)
                result("favorite_updated").put("favorite",metadata(c,f)).put("cache_status",cache)
            }
            "delete_favorite" -> {
                val old=BridgeContactFavorites.deleteRemote(c,p.getString("favorite_id"),p.getInt("expected_revision"),allowed)
                result("favorite_deleted").put("favorite_id",old.getString("id")).put("cache_status",cleanCache(c,old))
            }
            else -> result("unsupported")
        }
    } catch(e:FavoriteFailure) {result(e.code)}
    catch(e:BridgeFailure) {result(e.code)}
    catch(_:IllegalArgumentException) {result("invalid_favorite")}
    catch(_:SecurityException) {result("permission_required")}
    catch(_:Exception) {result("outcome_uncertain")}
}
