package it.jarviswake

import org.junit.Assert.*
import org.junit.Test

class BridgeActionPolicyTest {
    @Test fun roundTripCannotExtendCommandLifetimeOrDependOnPhoneWallClock() {
        assertEquals(23000L,BridgeProtocol.remainingLease(25000,2000))
        assertEquals(0L,BridgeProtocol.remainingLease(2000,3000))
        assertEquals(25000L,BridgeProtocol.remainingLease(90000,0))
        assertEquals(0L,BridgeProtocol.remainingLease(-1,1000))
        assertEquals(25000L,BridgeProtocol.remainingLease(25000,-1000))
    }
    @Test fun sendRequiresKnownRecipientUniqueEditorExactTextUniqueStableSend() {
        assertTrue(BridgeActionPolicy.maySend(true,1,true,1,true,false))
        assertFalse(BridgeActionPolicy.maySend(false,1,true,1,true,false))
        assertFalse(BridgeActionPolicy.maySend(true,2,true,1,true,false))
        assertFalse(BridgeActionPolicy.maySend(true,1,false,1,true,false))
        assertFalse(BridgeActionPolicy.maySend(true,1,true,2,true,false))
        assertFalse(BridgeActionPolicy.maySend(true,1,true,1,false,false))
        assertFalse(BridgeActionPolicy.maySend(true,1,true,1,true,true))
    }
    @Test fun idleCallAndAudioReleaseAreBothRequiredBeforeWakeResume() {
        assertFalse(BridgeActionPolicy.mayResumeAfterCall(false,true,100000,true))
        assertFalse(BridgeActionPolicy.mayResumeAfterCall(true,true,100000,false))
        assertFalse(BridgeActionPolicy.mayResumeAfterCall(true,false,29999,true))
        assertTrue(BridgeActionPolicy.mayResumeAfterCall(true,false,30000,true))
        assertTrue(BridgeActionPolicy.mayResumeAfterCall(true,true,1000,true))
    }
    @Test fun processRecoveryNeverRepeatsConsumedActions() {
        assertEquals(BridgeReplayPolicy.Decision.RUN,BridgeReplayPolicy.decide(false,false))
        assertEquals(BridgeReplayPolicy.Decision.UNCERTAIN,BridgeReplayPolicy.decide(true,false))
        assertEquals(BridgeReplayPolicy.Decision.SAVED,BridgeReplayPolicy.decide(true,true))
    }
    @Test fun contactNumbersRejectServiceCodesExtensionsAndUriInjection() {
        assertTrue(BridgeProtocol.safePhone("+391234567890"))
        assertTrue(BridgeProtocol.safePhone("0123456789"))
        for(number in listOf("*123#","+391234,999","tel:+391234","+39123;999","123","http://evil","1234567890123456")) assertFalse(number,BridgeProtocol.safePhone(number))
    }
    @Test fun onlyBoundedLiteralParametersAndDeclaredOperationsAreAccepted() {
        assertTrue(BridgeProtocol.validText("Un messaggio\ncon due righe",2000))
        assertFalse(BridgeProtocol.validText("\u0000command",2000))
        assertFalse(BridgeProtocol.validText("x".repeat(2001),2000))
        val id="4ac0e8f0-19b7-413c-aed8-5e49089e5f18"
        for(op in BridgeProtocol.OPERATIONS) assertTrue(BridgeProtocol.validRequest(id,op,1,25001))
        assertFalse(BridgeProtocol.validRequest(id,"shell",1,25001))
    }
}
