package it.jarviswake

import android.content.Context
import org.json.JSONObject
import java.util.Locale

/** Small local encrypted index. Never an authorization or substitute for fresh address-book validation. */
internal object BridgeContactCache {
    internal var read:(Context)->JSONObject={BridgeSettings.readPrivate(it,"frequent_contacts")}
    internal var write:(Context,JSONObject)->Unit={c,j->BridgeSettings.writePrivate(c,"frequent_contacts",j)}
    fun normalize(s:String)=s.trim().lowercase(Locale.ROOT).replace(Regex("\\s+")," ")
    @Synchronized fun entries(c:Context):List<JSONObject> = read(c).let {j->j.keys().asSequence().mapNotNull {j.optJSONObject(it)}.toList()}
        .sortedWith(compareByDescending<JSONObject>{it.optBoolean("favorite")}.thenByDescending {it.optLong("count")}.thenByDescending {it.optLong("last")})
    @Synchronized fun remember(c:Context,contact:BridgeContact,favorite:Boolean?=null) {
        if(contact.row<0) return
        val j=read(c);val key=contact.row.toString();val old=j.optJSONObject(key)
        val aliases=old?.optJSONArray("queries") ?: org.json.JSONArray()
        val query=normalize(contact.query)
        if(query.isNotEmpty() && (0 until aliases.length()).none {aliases.optString(it)==query}) {
            if(aliases.length()>=5) aliases.remove(0)
            aliases.put(query)
        }
        j.put(key,JSONObject().put("row",contact.row).put("name",contact.name).put("number",contact.number)
            .put("label",contact.label).put("queries",aliases).put("favorite",favorite ?: old?.optBoolean("favorite",false) ?: false)
            .put("count",(old?.optLong("count",0) ?: 0)+if(favorite==null) 1 else 0)
            .put("last",System.currentTimeMillis()))
        val keep=j.keys().asSequence().toList().sortedWith(compareByDescending<String>{j.getJSONObject(it).optBoolean("favorite")}
            .thenByDescending {j.getJSONObject(it).optLong("count")}.thenByDescending {j.getJSONObject(it).optLong("last")}).take(20).toSet()
        j.keys().asSequence().toList().filter {it !in keep}.forEach(j::remove)
        write(c,j)
    }
    @Synchronized fun favorite(c:Context,row:Long,value:Boolean) { val j=read(c);j.optJSONObject(row.toString())?.put("favorite",value);write(c,j) }
    @Synchronized fun remove(c:Context,row:Long) {val j=read(c);j.remove(row.toString());write(c,j)}
    @Synchronized fun clear(c:Context) {write(c,JSONObject())}
    fun candidate(c:Context,query:String):JSONObject? {
        val q=normalize(query)
        return entries(c).filter {r->normalize(r.optString("name"))==q || r.optJSONArray("queries")?.let {a->(0 until a.length()).any {a.optString(it)==q}}==true}.singleOrNull()
    }
}
