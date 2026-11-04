package it.jarviswake

import org.junit.Assert.*
import org.junit.Test

class PreparedChatPolicyTest {
    private val project="https://chatgpt.com/g/g-p-abc123/project"
    private val url="https://chatgpt.com/c/12345678-1234-4234-8234-123456789abc"
    private val reply="Ho letto le istruzioni del progetto e sono pronto per proseguire la conversazione."
    @Test fun onlyCanonicalPrivateConversationLinksFromTheExpectedProjectAreAccepted() {
        assertEquals(url,PreparedChatPolicy.conversationUrl(url,project))
        assertEquals(url,PreparedChatPolicy.conversationUrl(url+"?model=example-model",project))
        assertNull(PreparedChatPolicy.conversationUrl(url,"https://other.example/project"))
        assertNotNull(PreparedChatPolicy.conversationUrl(url.replace("/c/","/g/g-p-abc123/c/"),project))
        for(bad in listOf(url.replace("https:","http:"),url.replace("chatgpt.com","other.example"),
            url.replace("/c/","/share/"),"$url?token=private","$url#fragment",url.replace("chatgpt.com","user@chatgpt.com"),
            url.replace("/c/","/g/g-p-other/c/"),url.replace("123456789abc","bad"),project)) {
            assertNull(bad,PreparedChatPolicy.conversationUrl(bad,project))
        }
    }
    @Test fun sameProjectIdAcceptsADecorativeSlugButNeverAnotherProjectOrPrefix() {
        val id="0123456789abcdef0123456789abcdef"
        val project="https://chatgpt.com/g/g-p-$id/project"
        val decorated=url.replace("/c/","/g/g-p-$id-project-name/c/")
        assertEquals(decorated,PreparedChatPolicy.conversationUrl(decorated,project))
        assertEquals(decorated,PreparedChatPolicy.conversationUrl(decorated,project.replace("/project","-other-title/project")))
        assertNotNull(PreparedChatPolicy.conversationUrl(url.replace("/c/","/g/g-p-$id/c/"),project.replace("/project","-title/project")))
        for(bad in listOf(decorated.replace(id,id.dropLast(1)+"0"),decorated.replace(id,id+"0"),
            decorated.replace("project-name",""),decorated.replace("project-name","title%2Fother")))
            assertNull(PreparedChatPolicy.conversationUrl(bad,project))
    }
    @Test fun linkFailureCodesSeparateUrlSubmissionAndPersistenceWithoutReturningPrivateData() {
        assertEquals("link_origin",PreparedChatPolicy.checkConversationUrl(url.replace("chatgpt.com","other.example"),project).error)
        assertEquals("link_parameters",PreparedChatPolicy.checkConversationUrl("$url?private=secret",project).error)
        assertEquals("link_path",PreparedChatPolicy.checkConversationUrl(url.replace("/c/","/share/"),project).error)
        assertEquals("link_project",PreparedChatPolicy.checkConversationUrl(url.replace("/c/","/g/g-p-other/c/"),project).error)
        val l=PreparedChatLedger(null) {};val a=l.begin(project,"Jarvis",100)!!
        assertEquals("submission_missing",l.captureLinkResult(a.attempt,url,110))
        assertTrue(l.markSubmitted(a.attempt,120))
        assertEquals("submission_time",l.captureLinkResult(a.attempt,url,119))
        assertEquals("attempt_mismatch",l.captureLinkResult("other",url,130))
        val storage=PreparedChatLedger(l.record) {error("private-storage-error")}
        assertThrows(IllegalStateException::class.java) {storage.captureLinkResult(a.attempt,url,130)}
        assertEquals(PreparationPhase.STARTED,storage.record!!.phase)
        assertNull(l.captureLinkResult(a.attempt,url,130))
    }
    @Test fun nativeRouteUsesOnlyTheConversationIdAfterFullCapturedLinkValidation() {
        val nested=url.replace("/c/","/g/g-p-abc123/c/")
        assertEquals(url,PreparedChatPolicy.nativeConversationUrl(nested,project))
        assertEquals(url,PreparedChatPolicy.nativeConversationUrl("$nested?model=example-model",project))
        assertEquals(url,PreparedChatPolicy.nativeConversationUrl(url,project))
        for(bad in listOf(nested.replace("abc123","other"),url.replace("/c/","/share/"),
            "$url?token=private",url.replace("chatgpt.com","other.example"),project))
            assertNull(PreparedChatPolicy.nativeConversationUrl(bad,project))
    }
    @Test fun aPreparedConversationIsBoundToItsInitialTextAndCannotBeClaimedWithADifferentMessage() {
        val custom="Prima riga\nSeconda riga"
        val l=PreparedChatLedger(null) {};val a=l.begin(project,"Jarvis",100,custom)!!
        assertEquals(custom,a.openingText);assertTrue(l.markSubmitted(a.attempt,110))
        assertTrue(l.captureLink(a.attempt,url,120))
        assertNull(l.claim("old",project,"Jarvis",130))
        assertNull(l.claim("different",project,"Jarvis",130,"Testo diverso"))
        assertNotNull(l.claim("same",project,"Jarvis",130,custom))
    }
    @Test fun claimIsPersistedBeforeReturningAndCannotBeReusedByAnotherCycle() {
        var persisted:PreparedConversation?=null
        val ledger=PreparedChatLedger(null) {persisted=it}
        val attempt=ledger.begin(project,"Jarvis",100)!!
        assertTrue(ledger.complete(attempt.attempt,url,reply,200))
        val claimed=ledger.claim("cycle-one",project,"Jarvis",300)!!
        assertEquals(PreparationPhase.CLAIMED,persisted!!.phase);assertEquals("cycle-one",persisted!!.cycle)
        assertEquals(claimed,ledger.forCycle("cycle-one"));assertNull(ledger.forCycle("cycle-two"))
        assertNull(ledger.claim("cycle-two",project,"Jarvis",400))
        assertNull(PreparedChatLedger(persisted) {}.claim("cycle-two",project,"Jarvis",400))
    }
    @Test fun uncertainInterruptedOrRejectedPreparationCannotAutomaticallySendAgain() {
        var saved:PreparedConversation?=null
        val a=PreparedChatLedger(null) {saved=it};a.begin(project,"Jarvis",0)
        val recovered=PreparedChatLedger(saved) {saved=it};recovered.recover()
        assertEquals(PreparationPhase.FAILED,recovered.record!!.phase)
        assertNull(recovered.begin(project,"Jarvis",20));assertNull(recovered.claim("c",project,"Jarvis",30))
        recovered.reset();assertNotNull(recovered.begin(project,"Jarvis",40))
    }
    @Test fun completionRequiresOwnedAttemptValidUrlResponseAndTimeAndClaimRequiresSameProject() {
        val l=PreparedChatLedger(null) {};val a=l.begin(project,"Jarvis",100)!!
        assertFalse(l.complete("other",url,reply,200));assertFalse(l.complete(a.attempt,project,reply,200))
        assertFalse(l.complete(a.attempt,url,"OK",200));assertFalse(l.complete(a.attempt,url,reply,99))
        assertFalse(l.complete(a.attempt,url,reply,100+PreparedChatPolicy.PREPARE_TIMEOUT_MS+1))
        assertTrue(l.complete(a.attempt,url,reply,200))
        assertNull(l.claim("c",project,"Different project",300));assertNull(l.claim("c",project+"/","Jarvis",300))
        assertNull(l.claim("c",project,"Jarvis",199))
        assertNull(l.claim("c",project,"Jarvis",201+PreparedChatPolicy.READY_TTL_MS))
        assertNotNull(l.claim("c",project,"Jarvis",300))
    }
    @Test fun linkCanBeCapturedWithoutReplyButOnlyAfterObservedSubmissionAndOnlyOnce() {
        var saved:PreparedConversation?=null
        val l=PreparedChatLedger(null) {saved=it};val a=l.begin(project,"Jarvis",100)!!
        assertFalse(l.captureLink(a.attempt,url,110));assertNull(l.claim("c",project,"Jarvis",110))
        assertTrue(l.markSubmitted(a.attempt,120));assertTrue(l.captureLink(a.attempt,url,130))
        assertTrue(saved!!.linkOnly);assertEquals("",saved!!.hash);assertEquals(0,saved!!.proofLength)
        assertFalse(l.captureLink(a.attempt,url,140))
        assertNotNull(l.claim("c",project,"Jarvis",150));assertNull(l.claim("other",project,"Jarvis",160))
    }
    @Test fun invalidOrUnownedLinksNeverBecomeReadyEvenWithoutResponseWaiting() {
        val l=PreparedChatLedger(null) {};val a=l.begin(project,"Jarvis",100)!!
        assertTrue(l.markSubmitted(a.attempt,110))
        assertFalse(l.captureLink("other",url,120));assertFalse(l.captureLink(a.attempt,project,120))
        assertFalse(l.captureLink(a.attempt,url.replace("/c/","/share/"),120))
        assertFalse(l.captureLink(a.attempt,url,109));assertEquals(PreparationPhase.STARTED,l.record!!.phase)
    }
    @Test fun failedNativeHandoffCannotCreateNewPreparationsUntilAnExplicitRetry() {
        val l=PreparedChatLedger(null) {};val a=l.begin(project,"Jarvis",0)!!
        assertTrue(l.complete(a.attempt,url,reply,100));assertNotNull(l.claim("c",project,"Jarvis",200))
        l.rejectClaim("other");assertEquals(PreparationPhase.CLAIMED,l.record!!.phase)
        l.rejectClaim("c");assertEquals(PreparationPhase.FAILED,l.record!!.phase)
        assertNull(l.begin(project,"Jarvis",300));l.reset();assertNotNull(l.begin(project,"Jarvis",400))
    }
    @Test fun storageFailureDoesNotReturnAClaimOrAdvanceTheMemoryState() {
        val initial=PreparedConversation("attempt",project,"Jarvis",0,PreparationPhase.READY,url,
            PreparedChatPolicy.proof(reply)!!.first,64,100)
        val l=PreparedChatLedger(initial) {error("commit rejected")}
        assertThrows(IllegalStateException::class.java) {l.claim("c",project,"Jarvis",200)}
        assertEquals(PreparationPhase.READY,l.record!!.phase)
    }
    @Test fun nativeContentProofUsesOnlyADigestAndRejectsMissingOrDifferentContent() {
        val proof=PreparedChatPolicy.proof(reply)!!
        assertEquals(64,proof.first.length);assertFalse(proof.first.contains("istruzioni"))
        assertTrue(PreparedChatPolicy.matches(proof.first,proof.second,listOf("Header\n$reply")))
        assertFalse(PreparedChatPolicy.matches(proof.first,proof.second,listOf("Una conversazione diversa")))
        assertFalse(PreparedChatPolicy.matches(proof.first,2,listOf(reply)))
    }
}
