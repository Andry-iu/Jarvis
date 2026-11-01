package it.jarviswake

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList

class LocalNotesRepositoryTest {
    private class Memory(var notes:List<LocalNote> = emptyList()):NotesStorage {
        var fail=false;var drop=false;var writes=0
        override fun load()=notes.toList()
        override fun store(notes:List<LocalNote>) { writes++;if(fail) throw IllegalStateException();if(!drop) this.notes=notes.toList() }
    }
    private fun repository(s:Memory)=LocalNotesRepository(s,{123456L}) { UUID.randomUUID().toString() }
    private fun code(expected:String,action:()->Unit) {
        try { action();fail("expected rejection") } catch(e:NotesFailure) { assertEquals(expected,e.code) }
    }
    @Test fun createAndReadBackSurviveAnotherRepositoryUsingSameStorage() {
        val s=Memory();val r=repository(s)
        val n=r.save("","Preferenze","Testo esplicitamente richiesto",0)
        assertEquals(1,n.revision);assertEquals(123456L,n.updatedAt)
        assertEquals(n,repository(s).read(n.id));assertEquals(1,s.writes)
    }
    @Test fun updateRequiresReadRevisionAndRetainsIdentity() {
        val r=repository(Memory());val n=r.save("","Titolo","Prima versione",0)
        val updated=r.save(n.id,"Titolo aggiornato","Seconda versione",n.revision)
        assertEquals(n.id,updated.id);assertEquals(2,updated.revision)
        code("note_conflict") { r.save(n.id,"Titolo","Sovrascrittura obsoleta",1) }
        assertEquals(updated,r.read(n.id))
    }
    @Test fun creationCannotSilentlyDuplicateATitleOrOverwriteAnotherNote() {
        val r=repository(Memory());val n=r.save("","Memoria","Contenuto",0)
        code("note_title_exists") { r.save("","MEMORIA","Duplicato",0) }
        code("invalid_note") { r.save(n.id,"Memoria","Nuovo",0) }
        code("invalid_note") { r.save("","Altro","Nuovo",1) }
        code("note_not_found") { r.save(UUID.randomUUID().toString(),"Altro","Nuovo",1) }
        assertEquals(n,r.read(n.id))
    }
    @Test fun pagingIsBoundedAndDoesNotExposeMoreThanRequestedPage() {
        val r=repository(Memory())
        repeat(45) { r.save("","Nota $it","Testo",0) }
        val (first,next)=r.list(0);val (second,last)=r.list(next!!);val (third,end)=r.list(last!!)
        assertEquals(20,first.size);assertEquals(20,second.size);assertEquals(5,third.size)
        assertNull(end);assertEquals(45,(first+second+third).map { it.id }.toSet().size)
        code("invalid_note") { r.list(-1) };code("invalid_note") { r.list(101) }
    }
    @Test fun cancellationBeforeCommitDoesNotWrite() {
        val s=Memory();val r=repository(s)
        code("expired_before_dispatch") { r.save("","Titolo","Contenuto",0) { false } }
        assertEquals(0,s.writes);assertTrue(r.list(0).first.isEmpty())
    }
    @Test fun failedAtomicReplacementPreservesPreviousRecord() {
        val s=Memory();val r=repository(s);val n=r.save("","Titolo","Originale",0);s.fail=true
        try { r.save(n.id,"Titolo","Modifica",1);fail() } catch(_:IllegalStateException) {}
        assertEquals(n,r.read(n.id))
    }
    @Test fun successIsNotReportedWhenCommittedRecordCannotBeReadBack() {
        val s=Memory();val r=repository(s);s.drop=true
        code("outcome_uncertain") { r.save("","Titolo","Testo",0) }
    }
    @Test fun invalidOrCorruptArchiveIsNeverTreatedAsEmpty() {
        val s=Memory(listOf(LocalNote("invalid","Titolo","Testo",1,1)))
        code("notes_unavailable") { repository(s).save("","Nuovo","Testo",0) }
        assertEquals(0,s.writes)
        s.notes=listOf(LocalNote(UUID.randomUUID().toString(),"Titolo","Testo",0,1))
        code("notes_unavailable") { repository(s).list(0) }
    }
    @Test fun blankOversizedControlAndMultilineTitlesAreRejected() {
        val r=repository(Memory())
        for(title in listOf(" ","x".repeat(81),"Titolo\naltro"," Titolo","Titolo\taltro"))
            code("invalid_note") { r.save("",title,"Testo",0) }
        for(text in listOf(" ","x".repeat(4001),"Testo\u0000")) code("invalid_note") { r.save("","Titolo",text,0) }
        assertEquals("Riga 1\nRiga 2",r.save("","Valido","Riga 1\nRiga 2",0).text)
    }
    @Test fun capacityLimitsDoNotDiscardOldNotes() {
        val r=repository(Memory());repeat(100) { r.save("","Nota $it","Testo",0) }
        code("notes_full") { r.save("","Una di troppo","Testo",0) }
        val s=Memory();val big=repository(s);repeat(49) { big.save("","Grande $it","x".repeat(4000),0) }
        code("notes_full") { big.save("","Troppo grande","x".repeat(4000),0) }
        assertEquals(49,s.notes.size)
    }
    @Test fun concurrentLocalAndRemoteEditsHaveExactlyOneWinner() {
        val r=repository(Memory());val n=r.save("","Titolo","Originale",0)
        val start=CountDownLatch(1);val outcomes=CopyOnWriteArrayList<String>()
        val threads=(1..2).map { i -> Thread {
            start.await()
            try { r.save(n.id,"Titolo","Modifica $i",1);outcomes.add("saved") } catch(e:NotesFailure) { outcomes.add(e.code) }
        }.apply { start() } }
        start.countDown();threads.forEach { it.join(3000);assertFalse(it.isAlive) }
        assertEquals(listOf("note_conflict","saved"),outcomes.sorted());assertEquals(2,r.read(n.id).revision)
    }
    @Test fun deleteReadbackRetainsOtherNotesAndSurvivesRepositoryReopen() {
        val s=Memory();val r=repository(s)
        val a=r.save("","Da eliminare","Testo",0);val b=r.save("","Da conservare","Altro testo",0)
        r.delete(a.id,a.revision)
        code("note_not_found") { repository(s).read(a.id) }
        assertEquals(listOf(b),repository(s).list(0).first)
    }
    @Test fun staleDeleteAndInvalidIdentityNeverWrite() {
        val s=Memory();val r=repository(s);val n=r.save("","Titolo","Prima",0)
        val updated=r.save(n.id,"Titolo","Dopo",1);val writes=s.writes
        code("note_conflict") { r.delete(n.id,1) }
        code("invalid_note") { r.delete(n.id,0) }
        code("invalid_note") { r.delete("invalid",1) }
        code("note_not_found") { r.delete(UUID.randomUUID().toString(),1) }
        assertEquals(writes,s.writes);assertEquals(updated,r.read(n.id))
    }
    @Test fun failedDeletePreservesArchiveAndUncertainDeleteIsNotAcknowledged() {
        val s=Memory();val r=repository(s);val n=r.save("","Titolo","Originale",0)
        s.fail=true
        assertThrows(IllegalStateException::class.java) { r.delete(n.id,1) }
        assertEquals(n,r.read(n.id));s.fail=false;s.drop=true
        code("outcome_uncertain") { r.delete(n.id,1) };assertEquals(n,r.read(n.id))
    }
    @Test fun deleteReadbackMustAlsoPreserveUnrelatedRecords() {
        val s=Memory();val r=repository(s);val a=r.save("","Uno","Primo",0);r.save("","Due","Secondo",0)
        val broken=object:NotesStorage {
            override fun load()=s.load()
            override fun store(notes:List<LocalNote>) { s.notes=emptyList() }
        }
        code("outcome_uncertain") { LocalNotesRepository(broken,{1L}) { UUID.randomUUID().toString() }.delete(a.id,1) }
    }
    @Test fun deletionAndConcurrentUpdateCannotBothCommit() {
        val s=Memory();val r=repository(s);val n=r.save("","Titolo","Originale",0)
        val start=CountDownLatch(1);val outcomes=CopyOnWriteArrayList<String>()
        val actions=listOf<()->Unit>({r.delete(n.id,1);outcomes.add("deleted")},
            {r.save(n.id,"Titolo","Aggiornato",1);outcomes.add("saved")})
        val threads=actions.map { action -> Thread {
            start.await();try { action() } catch(e:NotesFailure) { outcomes.add(e.code) }
        }.apply { start() } }
        start.countDown();threads.forEach { it.join(3000);assertFalse(it.isAlive) }
        assertTrue(outcomes.sorted()==listOf("deleted","note_not_found") || outcomes.sorted()==listOf("note_conflict","saved"))
        assertEquals(2,s.writes)
    }
    @Test fun observersReadCommittedStateAndCannotTurnASaveIntoFailure() {
        val r=repository(Memory());var events=0;var seen=listOf<LocalNote>()
        val observer:()->Unit={ events++;seen=r.snapshot() }
        r.observe { throw IllegalStateException("Observer failure") };r.observe(observer)
        val n=r.save("","Titolo","Prima",0)
        assertEquals(listOf(n),seen);assertEquals(1,events)
        r.delete(n.id,n.revision);assertTrue(seen.isEmpty());assertEquals(2,events)
        r.removeObserver(observer);r.save("","Altro","Testo",0);assertEquals(2,events)
    }
    @Test fun uncertainCommitRequestsReadbackWithoutClaimingSuccess() {
        val s=Memory();val r=repository(s);var notifications=0
        r.observe { notifications++ };s.drop=true
        code("outcome_uncertain") { r.save("","Titolo","Testo",0) }
        assertEquals(1,notifications);assertTrue(r.snapshot().isEmpty())
    }
}
