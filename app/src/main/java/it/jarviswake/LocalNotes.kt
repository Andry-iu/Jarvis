package it.jarviswake

internal data class LocalNote(val id:String,val title:String,val text:String,val revision:Int,val updatedAt:Long)
internal class NotesFailure(val code:String):Exception()
internal interface NotesStorage {
    fun load():List<LocalNote>
    /** Must replace atomically or throw, never clear the previous archive on error. */
    fun store(notes:List<LocalNote>)
}

/** One repository owns the lock for local UI and remote commands. No network or Android UI. */
internal class LocalNotesRepository(private val storage:NotesStorage,private val now:()->Long,
    private val newId:()->String) {
    private val listeners=java.util.concurrent.CopyOnWriteArraySet<()->Unit>()
    fun observe(listener:()->Unit) { listeners.add(listener) }
    fun removeObserver(listener:()->Unit) { listeners.remove(listener) }
    private fun changed() { listeners.forEach { runCatching { it() } } }
    companion object {
        const val MAX_NOTES=100
        const val PAGE_SIZE=20
        const val MAX_TOTAL=200000
        fun validTitle(s:String)=BridgeProtocol.validText(s,80) && s==s.trim() && '\n' !in s && '\r' !in s && '\t' !in s
        fun validBody(s:String)=BridgeProtocol.validText(s,8000)
    }
    private fun load():List<LocalNote> {
        val notes=storage.load()
        if(notes.size>MAX_NOTES || notes.map { it.id }.toSet().size!=notes.size ||
            notes.any { !BridgeProtocol.validId(it.id) || !validTitle(it.title) || !validBody(it.text) || it.revision<1 || it.updatedAt<1 } ||
            notes.sumOf { it.title.length+it.text.length }>MAX_TOTAL) throw NotesFailure("notes_unavailable")
        return notes
    }
    @Synchronized fun list(offset:Int):Pair<List<LocalNote>,Int?> {
        if(offset !in 0..MAX_NOTES) throw NotesFailure("invalid_note")
        val notes=load().sortedBy { it.id }
        val end=minOf(offset+PAGE_SIZE,notes.size)
        return notes.drop(offset).take(PAGE_SIZE) to if(end<notes.size) end else null
    }
    /** UI reads all records under the same lock, without paging across concurrent writes. */
    @Synchronized fun snapshot():List<LocalNote> = load().sortedBy { it.id }
    @Synchronized fun read(id:String):LocalNote {
        if(!BridgeProtocol.validId(id)) throw NotesFailure("invalid_note")
        return load().find { it.id==id } ?: throw NotesFailure("note_not_found")
    }
    /** Local deletion uses the same lock/revision as saves, including remote updates. */
    @Synchronized fun delete(id:String,expectedRevision:Int) {
        if(!BridgeProtocol.validId(id) || expectedRevision<1) throw NotesFailure("invalid_note")
        val notes=load()
        val previous=notes.find { it.id==id } ?: throw NotesFailure("note_not_found")
        if(previous.revision!=expectedRevision) throw NotesFailure("note_conflict")
        val updated=notes.filter { it.id!=id }
        try {
            storage.store(updated)
            // Absence alone is insufficient: unrelated records must also survive readback.
            if(load().sortedBy { it.id }!=updated.sortedBy { it.id }) throw NotesFailure("outcome_uncertain")
        } finally { changed() }
    }
    @Synchronized fun save(id:String,title:String,text:String,expectedRevision:Int,allowed:()->Boolean={true}):LocalNote {
        if(!validTitle(title) || !validBody(text) || expectedRevision<0 || (id.isEmpty() && expectedRevision!=0) ||
            (id.isNotEmpty() && (!BridgeProtocol.validId(id) || expectedRevision==0))) throw NotesFailure("invalid_note")
        val notes=load()
        val previous=if(id.isEmpty()) null else notes.find { it.id==id } ?: throw NotesFailure("note_not_found")
        if(previous!=null && previous.revision!=expectedRevision) throw NotesFailure("note_conflict")
        if(previous?.revision==Int.MAX_VALUE) throw NotesFailure("note_conflict")
        if(notes.any { it.id!=id && it.title.equals(title,ignoreCase=true) }) throw NotesFailure("note_title_exists")
        if(previous==null && notes.size>=MAX_NOTES) throw NotesFailure("notes_full")
        val record=LocalNote(previous?.id ?: newId(),title,text,(previous?.revision ?: 0)+1,now())
        if(!BridgeProtocol.validId(record.id) || record.updatedAt<1 || (previous==null && notes.any { it.id==record.id })) throw NotesFailure("notes_unavailable")
        val updated=notes.filter { it.id!=id }+record
        if(updated.sumOf { it.title.length+it.text.length }>MAX_TOTAL) throw NotesFailure("notes_full")
        if(!allowed()) throw NotesFailure("expired_before_dispatch")
        try {
            storage.store(updated)
            // A save is acknowledged only after readback of the committed record.
            if(load().find { it.id==record.id }!=record) throw NotesFailure("outcome_uncertain")
        } finally { changed() }
        return record
    }
}
