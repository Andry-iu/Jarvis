package it.jarviswake

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

/** Local viewing/editing is independent of pairing, networking and microphone/cover state. */
class LocalNotesActivity:Activity() {
    private val main=Handler(Looper.getMainLooper())
    private lateinit var rows:LinearLayout
    private lateinit var state:TextView
    private var loading=false
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val layout=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL;setPadding(28,48,28,24);setBackgroundColor(Color.rgb(13,24,36))
            importantForAutofill=View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        }
        state=TextView(this).apply { setTextColor(Color.WHITE);textSize=17f;layout.addView(this) }
        layout.addView(Button(this).apply { text="Nuovo appunto";setOnClickListener { editor(null) } })
        rows=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;layout.addView(this) }
        layout.addView(Button(this).apply { text="Torna";setOnClickListener { finish() } })
        setContentView(ScrollView(this).apply { addView(layout) })
        reload()
    }
    private fun reload() {
        if(loading) return
        loading=true;state.text="Caricamento appunti locali…"
        Thread({
            val result=runCatching {
                val repository=PhoneNotes.book(this)
                val all=ArrayList<LocalNote>();var offset=0
                do { val (page,next)=repository.list(offset);all.addAll(page);offset=next ?: -1 } while(offset>=0)
                all
            }
            main.post {
                loading=false
                if(isFinishing || isDestroyed) return@post
                rows.removeAllViews()
                result.onSuccess { notes ->
                    state.text=if(notes.isEmpty()) "Nessun appunto. I dati sono conservati soltanto nell’archivio locale cifrato." else "Appunti locali: ${notes.size}"
                    for(n in notes) rows.addView(Button(this).apply { text=n.title;setOnClickListener { read(n.id) } })
                }.onFailure { state.text="Archivio non disponibile. Nessun appunto è stato cancellato o ricreato." }
            }
        },"Jarvis-notes-list").start()
    }
    private fun read(id:String) {
        Thread({
            val value=runCatching { PhoneNotes.book(this).read(id) }
            main.post { if(!isFinishing && !isDestroyed) value.onSuccess { editor(it) }
                .onFailure { state.text="Appunto non disponibile. Riapri l’elenco." } }
        },"Jarvis-notes-read").start()
    }
    private fun editor(note:LocalNote?) {
        val fields=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(24,8,24,0)
            importantForAutofill=View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS }
        val title=EditText(this).apply { hint="Titolo (massimo 80 caratteri)";isSingleLine=true;setText(note?.title ?: "");fields.addView(this) }
        val body=EditText(this).apply { hint="Appunto (massimo 4000 caratteri)";minLines=5
            inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setText(note?.text ?: "");fields.addView(this) }
        val message=TextView(this).apply { fields.addView(this) }
        val dialog=AlertDialog.Builder(this).setTitle(if(note==null) "Nuovo appunto locale" else "Appunto locale · revisione ${note.revision}")
            .setView(ScrollView(this).apply { addView(fields) }).setNegativeButton("Chiudi",null).setPositiveButton("Salva",null).create()
        dialog.setOnShowListener {
            dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val t=title.text.toString().trim();val b=body.text.toString()
                if(!LocalNotesRepository.validTitle(t) || !LocalNotesRepository.validBody(b)) {
                    message.text="Inserisci titolo e testo entro i limiti indicati.";return@setOnClickListener
                }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled=false
                Thread({
                    val saved=runCatching { PhoneNotes.book(this).save(note?.id ?: "",t,b,note?.revision ?: 0) }
                    main.post {
                        if(isFinishing || isDestroyed) return@post
                        saved.onSuccess { dialog.dismiss();reload() }.onFailure { e ->
                            // Never blindly repeat a save with an uncertain disk outcome.
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled=false
                            message.text=if(e is NotesFailure && e.code=="note_conflict") "L’appunto è cambiato. Chiudi e rileggilo prima di aggiornarlo."
                                else "Salvataggio non confermato. Chiudi e verifica l’elenco prima di riprovare."
                        }
                    }
                },"Jarvis-notes-save").start()
            }
        }
        dialog.show()
    }
}
