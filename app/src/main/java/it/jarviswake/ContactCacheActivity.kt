package it.jarviswake

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.text.InputType
import android.view.View
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher

/** Favorites are user data; the separate automatic cache may be removed without losing them. */
class ContactCacheActivity:Activity() {
    private var page="home"
    private var active:SplitMenuScreen?=null
    private var backCallback:OnBackInvokedCallback?=null
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState);window.setDecorFitsSystemWindows(false)
        home()
        if(Build.VERSION.SDK_INT>=33) {
            val callback=OnBackInvokedCallback {back()};backCallback=callback
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT,callback)
        }
    }
    private fun back() {when(page) {"home"->finish();"editor","detail"->favorites();else->home()}}
    @Deprecated("Android legacy back")
    @SuppressLint("GestureBackNavigation") // Only API31/32 fallback; API33+ registers OnBackInvokedCallback.
    override fun onBackPressed() {back()}
    private fun menu(title:String,description:String):SplitMenuScreen {
        window.hideMenuKeyboard()
        return SplitMenuScreen(this,title,description) {back()}.also {active=it;it.importantForAutofill=View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS}
    }
    private fun failure(menu:SplitMenuScreen,error:Throwable) {
        menu.feedback("Operazione non confermata",if(error is IllegalArgumentException) error.message.orEmpty() else "Rileggi l’elenco prima di riprovare. Nessun archivio è stato ricreato.")
    }
    private fun home() {
        page="home"
        val m=menu("Contatti JarvisWake","Preferiti scelti da te e cache automatica sono separati. I dati rimangono cifrati sul telefono.")
        m.button("Preferiti","Aggiungi un contatto dalla rubrica o scrivi nome e numero. La cache non elimina questi preferiti."){favorites()}
        m.button("Cache frequenti","Elenco automatico dei numeri usati, fino a20 voci. Puoi rimuovere una voce o svuotarlo senza cancellare i preferiti."){cache()}
        setContentView(m)
    }
    private fun favorites() {
        page="favorites"
        val m=menu("Preferiti","I numeri scelti da te non scadono con la cache. Sono disponibili nella risoluzione dei contatti del plugin, con scelta in caso di ambiguità.")
        m.button("Aggiungi dalla rubrica","Scegli qualsiasi numero della rubrica, anche se non è un preferito Android. Non modifica la rubrica del telefono."){pick()}
        m.button("Nome e numero manuali","Salva un nome e un numero direttamente in JarvisWake, senza creare un contatto nella rubrica Android."){editor()}
        m.button("Importa preferiti rubrica","Importazione facoltativa fino a20 numeri già preferiti Android. Non sostituisce i tuoi preferiti manuali.") {
            runCatching {BridgeContacts.importFavorites(this)}.onSuccess {favorites()}.onFailure {failure(m,it)}
        }
        m.button("Aggiorna preferiti","Rilegge l’archivio locale, senza telefonare o cancellare dati."){favorites()}
        runCatching {BridgeContactFavorites.entries(this)}.onSuccess {rows->
            if(rows.isEmpty()) m.text("Nessun preferito. Aggiungilo dalla rubrica oppure inserisci nome e numero.")
            for(r in rows) m.button(r.getString("name")+"\n"+if(r.getString("kind")=="manual") "Manuale · …"+r.getString("number").takeLast(4) else "Rubrica · …"+r.getString("number").takeLast(4),"Mostra questo preferito salvato e permette di rimuoverlo, senza modificare rubrica o cache.") {detail(r)}
        }.onFailure {failure(m,it)}
        setContentView(m)
    }
    private fun editor() {
        page="editor"
        val m=menu("Nuovo preferito manuale","Nome e numero sono salvati soltanto in JarvisWake. Usa un nome riconoscibile; con nomi uguali ti verrà chiesto quale numero scegliere.")
        val name=m.field("Nome del preferito","","Nome da1 a80 caratteri da pronunciare per richiamare il numero.")
        val number=m.field("Numero di telefono","","Numero con eventuale prefisso internazionale +. Niente codici speciali o interni.").apply {inputType=InputType.TYPE_CLASS_PHONE}
        m.button("Salva preferito","Salva e rilegge il numero nell’archivio cifrato. Non effettua alcuna telefonata.") {
            runCatching {BridgeContactFavorites.save(this,name.text.toString(),number.text.toString())}.onSuccess {favorites()}.onFailure {failure(m,it)}
        }
        setContentView(m)
    }
    private fun detail(row:org.json.JSONObject) {
        page="detail"
        val m=menu(row.getString("name"),"Questo preferito è indipendente dalla cache. La rimozione non tocca la rubrica Android.")
        m.text(row.getString("number"),24f)
        m.text(if(row.getString("kind")=="manual") "Numero manuale salvato in JarvisWake" else "Contatto collegato alla rubrica: i dati vengono ricontrollati prima dell’uso.")
        m.button("Rimuovi preferito","Rimuove solo questa voce dai preferiti di JarvisWake, dopo conferma.") {
            AlertDialog.Builder(this).setTitle("Rimuovere questo preferito?").setMessage(row.getString("name")+"\n"+row.getString("number"))
                .setNegativeButton("Annulla",null).setPositiveButton("Rimuovi") {_,_->
                    runCatching {BridgeContactFavorites.remove(this,row.getString("id"),row.getInt("revision"))}.onSuccess {favorites()}.onFailure {failure(m,it)}
                }.showLarge()
        }
        setContentView(m)
    }
    private fun cache() {
        page="cache"
        val m=menu("Cache frequenti","Elenco automatico fino a20 numeri. La rimozione non cancella preferiti o rubrica; una nuova telefonata può aggiungere di nuovo la voce.")
        m.button("Aggiorna cache","Rilegge la cache locale."){cache()}
        m.button("Svuota cache","Elimina soltanto la cache dei frequenti. I preferiti restano conservati.") {
            AlertDialog.Builder(this).setTitle("Svuotare solo la cache?").setMessage("I preferiti e la rubrica rimangono conservati.")
                .setNegativeButton("Annulla",null).setPositiveButton("Svuota") {_,_->
                    runCatching {BridgeContactCache.clear(this)}.onSuccess {cache()}.onFailure {failure(m,it)}
                }.showLarge()
        }
        runCatching {BridgeContactCache.entries(this)}.onSuccess {rows->
            if(rows.isEmpty()) m.text("Cache vuota. Si aggiorna dopo l’avvio di una telefonata richiesta.")
            for(r in rows) m.button(r.getString("name")+"\n…"+r.getString("number").takeLast(4),"Rimuove questa voce soltanto dalla cache automatica, senza toccare i preferiti.") {
                AlertDialog.Builder(this).setTitle("Rimuovere dalla cache?").setMessage(r.getString("name"))
                    .setNegativeButton("Annulla",null).setPositiveButton("Rimuovi") {_,_->
                        runCatching {BridgeContactCache.remove(this,r.optString("key",r.optLong("row").toString()))}.onSuccess {cache()}.onFailure {failure(m,it)}
                    }.showLarge()
            }
        }.onFailure {failure(m,it)}
        setContentView(m)
    }
    private fun pick() {
        if(checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED) {requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS),91);return}
        runCatching {startActivityForResult(Intent(Intent.ACTION_PICK,Phone.CONTENT_URI),92)}.onFailure {active?.let {m->failure(m,it)}}
    }
    override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults)
        if(requestCode==91) {if(grantResults.firstOrNull()==PackageManager.PERMISSION_GRANTED) pick() else active?.feedback("Rubrica non autorizzata","Puoi inserire un preferito manualmente oppure concedere il permesso Contatti.")}
    }
    @Deprecated("Android picker result") override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==92 && resultCode==RESULT_OK) {
            val uri=data?.data ?: return
            runCatching {BridgeContacts.addFavoriteFromUri(this,uri)}.onSuccess {favorites()}.onFailure {active?.let {m->failure(m,it)}}
        }
    }
    override fun onDestroy() {
        if(Build.VERSION.SDK_INT>=33) backCallback?.let {onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it)}
        super.onDestroy()
    }
}
