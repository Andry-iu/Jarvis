package it.jarviswake

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle

/** Cache contents stay local; no immediate call button and no export of numbers. */
class ContactCacheActivity:Activity() {
    override fun onCreate(savedInstanceState:Bundle?) {super.onCreate(savedInstanceState);window.setDecorFitsSystemWindows(false);render()}
    private fun render() {
        val menu=SplitMenuScreen(this,"Contatti frequenti e preferiti","Cache cifrata solo sul telefono. Prima di chiamare la rubrica viene ricontrollata.") {finish()}
        menu.button("Aggiorna elenco","Rilegge la cache locale, senza effettuare telefonate."){render()}
        menu.button("Importa preferiti rubrica","Importa fino a20 numeri contrassegnati come preferiti nella rubrica Android; richiede il permesso Contatti. Non risolve automaticamente nomi ambigui.") {
            runCatching {BridgeContacts.importFavorites(this)}.onSuccess {render()}.onFailure {menu.feedback("Importazione","Autorizza la rubrica oppure riprova.")}
        }
        val loaded=runCatching {BridgeContactCache.entries(this)}
        loaded.onFailure {menu.feedback("Cache non leggibile","Impossibile leggere i contatti salvati. Nessun dato è stato cancellato.")}
        val rows=loaded.getOrDefault(emptyList())
        if(loaded.isSuccess && rows.isEmpty()) menu.text("Nessun contatto memorizzato. La cache si aggiorna dopo l’avvio di una telefonata richiesta oppure importando i preferiti.")
        for(row in rows) menu.button((if(row.optBoolean("favorite")) "★ " else "")+row.optString("name")+"\n"+row.optString("label")+" · …"+row.optString("number").takeLast(4),"Contatto conservato solo sul telefono. Gli utilizzi contano comandi telefonici avviati, non conversazioni completate.") {
            AlertDialog.Builder(this).setTitle(row.optString("name"))
                .setItems(arrayOf(if(row.optBoolean("favorite")) "Togli dai preferiti" else "Segna preferito","Rimuovi dalla cache")) {_,index ->
                    runCatching {if(index==0) BridgeContactCache.favorite(this,row.optLong("row"),!row.optBoolean("favorite")) else BridgeContactCache.remove(this,row.optLong("row"))};render()
                }.setNegativeButton("Annulla",null).showLarge()
        }
        menu.button("Svuota cache","Elimina soltanto frequenti/preferiti di JarvisWake, non la rubrica o gli appunti.") {
            AlertDialog.Builder(this).setTitle("Svuotare la cache contatti?").setNegativeButton("Annulla",null)
                .setPositiveButton("Svuota") {_,_->runCatching {BridgeContactCache.clear(this)};render()}.showLarge()
        }
        setContentView(menu)
    }
}
