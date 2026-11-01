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
    private lateinit var rows:LinearLayout
    private lateinit var state:TextView
    private lateinit var menu:SplitMenuScreen
    private var generation=0
    private var editing=false
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
        window.insetsController?.hide(android.view.WindowInsets.Type.ime())
        generation++;val request=generation;editing=false
        menu=SplitMenuScreen(this,"Appunti locali","Apri un titolo per leggere o aggiornare l’appunto, oppure Nuovo appunto. I contenuti hanno un archivio cifrato sul telefono. Puoi usarli qui anche senza rete o plugin. Le letture richieste a ChatGPT passano nella conversazione. Modifiche concorrenti richiedono rilettura; non ripetere alla cieca un salvataggio non confermato.") { finish() }
        menu.importantForAutofill=View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        state=menu.text("Caricamento appunti locali…")
        menu.button("Nuovo appunto") { editor(null) }
        rows=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;menu.addControl(this) }
        setContentView(menu)
        Thread({
            val result=runCatching {
                val repository=PhoneNotes.book(this)
                val all=ArrayList<LocalNote>();var offset=0
                do { val (page,next)=repository.list(offset);all.addAll(page);offset=next ?: -1 } while(offset>=0)
                all
            }
            main.post {
                if(isFinishing || isDestroyed || request!=generation) return@post
                result.onSuccess { notes ->
                    state.text=if(notes.isEmpty()) "Nessun appunto locale." else "Appunti locali: ${notes.size}"
                    for(n in notes) rows.addView(menu.makeButton(n.title) { read(n.id) },LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=(8*resources.displayMetrics.density).toInt() })
                }.onFailure { state.text="Archivio non disponibile. Nessun appunto è stato cancellato o ricreato." }
            }
        },"Jarvis-notes-list").start()
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
            "Scrivi titolo (massimo 80 caratteri) e testo (massimo 4000), poi Salva. Il contenuto viene sostituito con quello scritto qui. Torna all’elenco senza salvare per abbandonare le modifiche. In caso di conflitto o esito incerto, rileggi l’appunto prima di provare ancora.") { reload() }
        menu.importantForAutofill=View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        val title=menu.field("Titolo",note?.title ?: "","Il titolo identifica l’appunto. Un altro appunto con lo stesso titolo non viene sovrascritto implicitamente.")
        val body=menu.field("Testo dell’appunto",note?.text ?: "","Salva conserva esattamente questo testo nell’archivio locale. Verifica il contenuto prima di confermare.",true)
        val message=menu.text("")
        var save:Button?=null
        save=menu.button("Salva appunto") {
            val t=title.text.toString().trim();val b=body.text.toString()
            if(!LocalNotesRepository.validTitle(t) || !LocalNotesRepository.validBody(b)) {
                message.text="Inserisci titolo e testo entro i limiti indicati."
            } else {
                save?.isEnabled=false
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
        setContentView(menu);menu.requestFocus()
    }
    @Deprecated("Local note editor returns to its list before leaving the activity")
    @SuppressLint("GestureBackNavigation") // API31 fallback; API33+ uses OnBackInvokedCallback above.
    override fun onBackPressed() { if(editing) reload() else super.onBackPressed() }
    override fun onDestroy() { if(Build.VERSION.SDK_INT>=33) backCallback?.let { onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it) };super.onDestroy() }
}
