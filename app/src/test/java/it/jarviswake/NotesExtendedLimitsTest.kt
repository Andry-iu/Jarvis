package it.jarviswake

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class NotesExtendedLimitsTest {
    @Test fun longUnicodeNotesSurviveSaveReadAndReopenWhileOversizeSavePreservesExistingData() {
        var stored=emptyList<LocalNote>()
        val storage=object:NotesStorage {
            override fun load()=stored.toList()
            override fun store(notes:List<LocalNote>) {stored=notes.toList()}
        }
        fun book()=LocalNotesRepository(storage,{1000L},{UUID.randomUUID().toString()})
        val repo=book();val original=repo.save("","Long note","界".repeat(8000),0)
        assertEquals(original,book().read(original.id))
        assertEquals(20,LocalNotesRepository.PAGE_SIZE)
        assertThrows(NotesFailure::class.java) {repo.save(original.id,original.title,"x".repeat(8001),original.revision)}
        assertEquals(original,book().read(original.id))
        val changed=book().save(original.id,original.title,"Updated",original.revision)
        assertEquals(2,changed.revision);assertEquals("Updated",book().read(original.id).text)
    }
}
