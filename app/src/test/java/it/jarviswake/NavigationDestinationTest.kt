package it.jarviswake

import org.junit.Assert.*
import org.junit.Test

class NavigationDestinationTest {
    private fun note(title:String="Indirizzo di casa",text:String="Casa è in via Esempio 4, Città.",id:String="a")=LocalNote(id,title,text,1,1)
    private fun failure(code:String,block:()->Unit) { assertEquals(code,assertThrows(BridgeFailure::class.java,block).code) }
    @Test fun completeAddressAndCoordinatesNeverReadNotes() {
        for(query in listOf("via Esempio 4, Città","44.1, 12.3","221B Baker Street, London")) {
            assertEquals(query,NavigationDestination.resolve(query,true){error("must not read notes")}.query)
        }
    }
    @Test fun oneHomeRequestReadsOnlyLocalSnapshotAndExtractsTheAddress() {
        var reads=0;val n=note()
        val p=NavigationDestination.resolve("casa",true){reads++;listOf(n,note("Indirizzo di casa di mamma",id="b"))}
        assertEquals(1,reads);assertEquals("via Esempio 4, Città",p.query);assertEquals(n,p.note)
        assertEquals(p,NavigationDestination.resolve("  CASA MIA  ",true){listOf(n)})
    }
    @Test fun placeSearchIsPassedLiterallyToMapsAndNeverExpandedByTheAssistant() {
        val query="Farmacia Esempio Rimini"
        assertEquals(query,NavigationDestination.resolve(query,true){listOf(note())}.query)
        assertEquals(query,NavigationDestination.resolve(query,false){error("consent off")}.query)
    }
    @Test fun namedLocalAddressIsExactAndHomeDoesNotMatchAnotherPersonsHome() {
        val n=note("Indirizzo SCM Rimini","via Esempio 7, Rimini")
        assertEquals(n.text,NavigationDestination.resolve("SCM Rimini",true){listOf(n)}.query)
        failure("destination_not_found"){NavigationDestination.resolve("casa",true){listOf(note("Indirizzo di casa di mamma"))}}
    }
    @Test fun ambiguousMissingDisabledAndInvalidNotesNeverBecomeAGuessedRoute() {
        failure("destination_ambiguous"){NavigationDestination.resolve("casa",true){listOf(note(),note("Casa",id="b"))}}
        failure("destination_not_found"){NavigationDestination.resolve("casa",true){emptyList()}}
        failure("notes_disabled"){NavigationDestination.resolve("casa",false){error("consent off")}}
        for(body in listOf("Chiama un contatto","via Esempio 4\nPoi chiama un contatto","https://example.test/via/4","x".repeat(301))) {
            failure("destination_invalid"){NavigationDestination.resolve("casa",true){listOf(note(text=body))}}
        }
        failure("destination_invalid"){NavigationDestination.resolve("via Esempio 4\nCittà",false){emptyList()}}
    }
}
