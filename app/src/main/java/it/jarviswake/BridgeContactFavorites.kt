package it.jarviswake

import android.content.Context
import android.telephony.PhoneNumberUtils
import org.json.JSONObject
import java.util.UUID

/** User-managed contacts. Separate encrypted preference archive, retained when pairing/cache is cleared. */
internal object BridgeContactFavorites {
    internal var read:(Context)->JSONObject={BridgeSettings.readPrivate(it,"items","contact_favorites")}
    internal var write:(Context,JSONObject)->Unit={c,j->BridgeSettings.writePrivate(c,"items",j,"contact_favorites")}
    fun phone(raw:String):String {
        val input=raw.trim()
        require(input.length in 5..64 && input.matches(Regex("\\+?[0-9\\s().-]+"))) {"Inserisci un numero telefonico valido, senza codici o interni."}
        val n=PhoneNumberUtils.normalizeNumber(input)
        require(BridgeProtocol.safePhone(n)) {"Inserisci da5 a15 cifre, con eventuale + iniziale."}
        return n
    }
    private fun same(a:JSONObject,b:JSONObject):Boolean {
        val keys=a.keys().asSequence().toSet()
        return keys==b.keys().asSequence().toSet() && keys.all {key->
            val x=a.get(key);val y=b.get(key)
            if(x is JSONObject && y is JSONObject) same(x,y) else x.toString()==y.toString()
        }
    }
    private fun persist(c:Context,book:JSONObject) {
        write(c,book)
        check(same(book,read(c))) {"Salvataggio preferiti non confermato: rileggi prima di riprovare."}
    }
    /** Copy legacy .88 favorites first and confirm persistence before touching their old records. */
    @Synchronized private fun book(c:Context):JSONObject {
        val stored=read(c)
        val items=stored.optJSONObject("items") ?: JSONObject().also {stored.put("items",it)}
        if(stored.optBoolean("migrated")) return stored
        val old=BridgeContactCache.read(c)
        val legacy=old.keys().asSequence().toList().mapNotNull {old.optJSONObject(it)}.filter {it.optBoolean("favorite")}
        if(legacy.isNotEmpty()) {
            for(r in legacy) {
                val row=r.optLong("row",-1);if(row<0) continue
                val id="book:$row"
                if(!items.has(id)) items.put(id,JSONObject().put("id",id).put("kind","book").put("row",row)
                    .put("name",r.getString("name")).put("number",phone(r.getString("number"))).put("label",r.optString("label"))
                    .put("revision",1))
            }
            stored.put("migrated",true);persist(c,stored)
            for(r in legacy) {
                val key=r.optLong("row").toString()
                if(r.optLong("count")==0L) old.remove(key) else old.optJSONObject(key)?.remove("favorite")
            }
            BridgeContactCache.write(c,old)
        }
        return stored
    }
    @Synchronized fun migrate(c:Context) {book(c)}
    @Synchronized fun entries(c:Context):List<JSONObject> = book(c).getJSONObject("items").let {items->
        items.keys().asSequence().map {JSONObject(items.getJSONObject(it).toString())}.toList()
            .sortedBy {BridgeContactCache.normalize(it.getString("name"))}
    }
    @Synchronized fun find(c:Context,id:String):JSONObject?=book(c).getJSONObject("items").optJSONObject(id)?.let {JSONObject(it.toString())}
    @Synchronized fun save(c:Context,name:String,raw:String,row:Long=-1,label:String="Numero manuale"):JSONObject {
        val display=if(row>=0) name else name.trim();require(BridgeProtocol.validText(display,if(row>=0) 100 else 80)) {"Inserisci un nome da1 a80 caratteri."}
        val number=phone(raw);val book=book(c);val items=book.getJSONObject("items")
        val existing=items.keys().asSequence().map {items.getJSONObject(it)}.firstOrNull {
            (row>=0 && it.optString("kind")=="book" && it.optLong("row")==row) ||
                (row<0 && it.optString("kind")=="manual" && BridgeContactCache.normalize(it.getString("name"))==BridgeContactCache.normalize(display) && it.getString("number")==number)
        }
        val id=existing?.getString("id") ?: if(row>=0) "book:$row" else UUID.randomUUID().toString()
        require(existing!=null || items.length()<100) {"Hai raggiunto100 preferiti. Rimuovi una voce prima di aggiungerne un’altra."}
        val value=JSONObject().put("id",id).put("kind",if(row>=0) "book" else "manual").put("row",row)
            .put("name",display).put("number",number).put("label",label.take(40)).put("revision",existing?.optInt("revision",0)?.plus(1) ?: 1)
        items.put(id,value);book.put("migrated",true);persist(c,book)
        return JSONObject(value.toString())
    }
    @Synchronized fun remove(c:Context,id:String,revision:Int) {
        val book=book(c);val items=book.getJSONObject("items")
        val value=items.optJSONObject(id) ?: throw IllegalStateException("Preferito non più presente. Rileggi l’elenco.")
        check(value.getInt("revision")==revision) {"Il preferito è cambiato. Rileggi prima di rimuoverlo."}
        items.remove(id);book.put("migrated",true);persist(c,book)
    }
    fun matches(c:Context,name:String)=entries(c).filter {BridgeContactCache.normalize(it.getString("name")).contains(BridgeContactCache.normalize(name))}
}
