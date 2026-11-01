package it.jarviswake

import android.content.Context
import org.json.JSONObject
import java.util.Locale

/** Automatic bounded cache, independent of persistent user favorites. */
internal object BridgeContactCache {
    internal var read:(Context)->JSONObject={BridgeSettings.readPrivate(it,"frequent_contacts")}
    internal var write:(Context,JSONObject)->Unit={c,j->BridgeSettings.writePrivate(c,"frequent_contacts",j)}
    fun normalize(s:String)=s.trim().lowercase(Locale.ROOT).replace(Regex("\\s+")," ")
    @Synchronized fun entries(c:Context):List<JSONObject> {
        BridgeContactFavorites.migrate(c)
        return read(c).let {j->j.keys().asSequence().mapNotNull {j.optJSONObject(it)}.toList()}
            .sortedWith(compareByDescending<JSONObject>{it.optLong("count")}.thenByDescending {it.optLong("last")})
    }
    fun key(contact:BridgeContact)=if(contact.row>=0) contact.row.toString() else "favorite:${contact.favoriteId}"
    @Synchronized fun remember(c:Context,contact:BridgeContact) {
        if(contact.row<0 && contact.favoriteId==null) return
        BridgeContactFavorites.migrate(c)
        val j=read(c);val key=key(contact)
        val duplicates=j.keys().asSequence().toList().filter {it!=key && j.optJSONObject(it)?.let {r->normalize(r.optString("name"))==normalize(contact.name) && r.optString("number")==contact.number}==true}
        val old=j.optJSONObject(key) ?: duplicates.firstOrNull()?.let {j.optJSONObject(it)}
        duplicates.forEach(j::remove)
        val aliases=old?.optJSONArray("queries") ?: org.json.JSONArray()
        val query=normalize(contact.query)
        if(query.isNotEmpty() && (0 until aliases.length()).none {aliases.optString(it)==query}) {
            if(aliases.length()>=5) aliases.remove(0)
            aliases.put(query)
        }
        j.put(key,JSONObject().put("key",key).put("row",contact.row).put("name",contact.name).put("number",contact.number)
            .put("label",contact.label).put("queries",aliases).put("count",(old?.optLong("count",0) ?: 0)+1)
            .put("last",System.currentTimeMillis()).apply {contact.favoriteId?.let {put("favorite_id",it)}})
        val keep=j.keys().asSequence().toList().sortedWith(compareByDescending<String>{j.getJSONObject(it).optLong("count")}
            .thenByDescending {j.getJSONObject(it).optLong("last")}).take(20).toSet()
        j.keys().asSequence().toList().filter {it !in keep}.forEach(j::remove)
        write(c,j)
    }
    @Synchronized fun remove(c:Context,key:String) {BridgeContactFavorites.migrate(c);val j=read(c);j.remove(key);write(c,j)}
    @Synchronized fun clear(c:Context) {BridgeContactFavorites.migrate(c);write(c,JSONObject())}
    fun candidate(c:Context,query:String):JSONObject? {
        val q=normalize(query)
        return entries(c).filter {r->normalize(r.optString("name"))==q || r.optJSONArray("queries")?.let {a->(0 until a.length()).any {a.optString(it)==q}}==true}.singleOrNull()
    }
}
