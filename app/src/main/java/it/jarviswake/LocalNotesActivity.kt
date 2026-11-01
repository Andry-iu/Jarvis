package it.jarviswake

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.widget.*
import android.os.Build
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher

/** Local viewing/editing is independent of pairing, networking and microphone/cover state. */
class LocalNotesActivity:Activity() {
    private val main=Handler(Looper.getMainLooper())
    private lateinit var state:TextView
    private lateinit var menu:SplitMenuScreen
    private var generation=0
    private var editing=false
    private var listRequest=0
    private var observed:LocalNotesRepository?=null
    private var observing=false
    private lateinit var notesArea:LinearLayout
    private lateinit var refresh:Button
    private var displayed:List<LocalNote>?=null
    private val changed:()->Unit={ main.post { if(observing && !editing) refreshList() };Unit }
    private var backCallback:OnBackInvokedCallback?=null
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        window.setDecorFitsSystemWindows(false)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        reload()
        if(Build.VERSION.SDK_INT>=33) {
            val cb=OnBackInvokedCallback { if(editing) reload() else finish() };backCallback=cb
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT,cb)
        }
    }
    private fun reload() {
        window.hideMenuKeyboard()
        generation++;editing=false;displayed=null
        menu=SplitMenuScreen(this,"Appunti locali","Apri un titolo per leggere o aggiornare l’appunto, oppure Nuovo appunto. I contenuti hanno un archivio cifrato sul telefono. Puoi usarli qui anche senza rete o plugin. Le letture richieste a ChatGPT passano nella conversazione. Modifiche concorrenti richiedono rilettura; non ripetere alla cieca un salvataggio non confermato.") { finish() }
        menu.importantForAutofill=View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        state=menu.text("Caricamento appunti locali…")
        refresh=menu.button("Aggiorna appunti") { refreshList() }
        menu.button("Nuovo appunto") { editor(null) }
        notesArea=menu.buttonsArea()
        setContentView(menu)
        refreshList()
    }
    private fun refreshList() {
        if(editing) return
        val page=generation;val request=++listRequest;refresh.isEnabled=false
        Thread({
            val result=runCatching { PhoneNotes.book(this).snapshot() }
            main.post {
                if(isFinishing || isDestroyed || editing || page!=generation || request!=listRequest) return@post
                refresh.isEnabled=true
                result.onSuccess { notes ->
                    state.text=if(notes.isEmpty()) "Nessun appunto locale." else "Appunti locali: ${notes.size}"
                    if(notes!=displayed) {
                        notesArea.removeAllViews()
                        for(n in notes) menu.button(n.title,"Apre questo appunto salvato per leggerlo, aggiornarlo o eliminarlo con conferma.",area=notesArea) { read(n.id) }
                        displayed=notes
                    }
                }.onFailure { state.text="Archivio non disponibile. Nessun appunto è stato cancellato o ricreato." }
            }
        },"Jarvis-notes-list").start()
    }
    override fun onResume() {
        super.onResume();observing=true
        observed=runCatching { PhoneNotes.book(this) }.getOrNull()
        observed?.observe(changed)
        if(!editing) refreshList()
    }
    override fun onPause() {
        observing=false;observed?.removeObserver(changed);observed=null;listRequest++
        super.onPause()
    }
    private fun read(id:String) {
        val request=generation
        Thread({
            val value=runCatching { PhoneNotes.book(this).read(id) }
            main.post { if(!isFinishing && !isDestroyed && request==generation) value.onSuccess { editor(it) }
                .onFailure { state.text="Appunto non disponibile. Riapri l’elenco." } }
        },"Jarvis-notes-read").start()
    }
    private fun editor(note:LocalNote?) {
        generation++;val request=generation;editing=true
        menu=SplitMenuScreen(this,if(note==null) "Nuovo appunto" else "Modifica appunto · r${note.revision}",
            "Scrivi titolo (massimo 80 caratteri) e testo (massimo 4000), poi Salva. Per rimuovere un appunto già salvato usa Elimina appunto e conferma: non è annullabile. Indietro torna all’elenco senza salvare. In caso di conflitto o esito incerto, rileggi l’appunto prima di provare ancora.") { reload() }
        menu.importantForAutofill=View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        val title=menu.field("Titolo",note?.title ?: "","Il titolo identifica l’appunto. Un altro appunto con lo stesso titolo non viene sovrascritto implicitamente.")
        val body=menu.field("Testo dell’appunto",note?.text ?: "","Salva conserva esattamente questo testo nell’archivio locale. Verifica il contenuto prima di confermare.",true)
        val message=menu.text("")
        var save:Button?=null
        var delete:Button?=null
        var committing=false
        var confirmation:AlertDialog?=null
        fun beginCommit() {
            committing=true;save?.isEnabled=false;delete?.isEnabled=false
            title.isEnabled=false;body.isEnabled=false
        }
        save=menu.button("Salva appunto") {
            if(committing) return@button
            val t=title.text.toString().trim();val b=body.text.toString()
            if(!LocalNotesRepository.validTitle(t) || !LocalNotesRepository.validBody(b)) {
                message.text="Inserisci titolo e testo entro i limiti indicati."
            } else {
                beginCommit()
                Thread({
                    val saved=runCatching { PhoneNotes.book(this).save(note?.id ?: "",t,b,note?.revision ?: 0) }
                    main.post {
                        if(isFinishing || isDestroyed || request!=generation) return@post
                        saved.onSuccess { reload() }.onFailure { e ->
                            message.text=if(e is NotesFailure && e.code=="note_conflict") "L’appunto è cambiato. Torna all’elenco e rileggilo prima di aggiornarlo."
                                else "Salvataggio non confermato. Torna all’elenco e verifica prima di riprovare."
                        }
                    }
                },"Jarvis-notes-save").start()
            }
        }
        if(note!=null) delete=menu.button("Elimina appunto") {
            if(committing || confirmation?.isShowing==true) return@button
            confirmation=AlertDialog.Builder(this).setTitle("Eliminare questo appunto?")
                .setMessage("L’appunto aperto verrà eliminato dal telefono. Non puoi annullare questa operazione. Le modifiche non salvate andranno perse.")
                .setNegativeButton("Annulla",null).setPositiveButton("Elimina") { _,_ ->
                    if(committing || request!=generation || isFinishing || isDestroyed) return@setPositiveButton
                    beginCommit()
                    Thread({
                        val deleted=runCatching { PhoneNotes.book(this).delete(note.id,note.revision) }
                        main.post {
                            if(isFinishing || isDestroyed || request!=generation) return@post
                            deleted.onSuccess { reload() }.onFailure { e ->
                                message.text=if(e is NotesFailure && e.code=="note_conflict")
                                    "L’appunto è cambiato. Torna all’elenco e rileggilo prima di eliminarlo."
                                else "Eliminazione non confermata. Torna all’elenco e verifica prima di riprovare."
                            }
                        }
                    },"Jarvis-notes-delete").start()
                }.showLarge()
        }
        setContentView(menu);menu.requestFocus()
    }
    @Deprecated("Local note editor returns to its list before leaving the activity")
    @SuppressLint("GestureBackNavigation") // API31 fallback; API33+ uses OnBackInvokedCallback above.
    override fun onBackPressed() { if(editing) reload() else super.onBackPressed() }
    override fun onDestroy() {
        observing=false;observed?.removeObserver(changed)
        if(Build.VERSION.SDK_INT>=33) backCallback?.let { onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it) }
        super.onDestroy()
    }
}
