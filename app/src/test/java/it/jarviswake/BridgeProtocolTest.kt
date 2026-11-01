package it.jarviswake

import org.junit.Assert.*
import org.junit.Test

class BridgeProtocolTest {
    private val id="4ac0e8f0-19b7-413c-aed8-5e49089e5f18"
    @Test fun acceptsOnlyOwnedHttpsOrigin() {
        assertTrue(BridgeProtocol.validOrigin(BridgeProtocol.ORIGIN))
        for(s in listOf("http://example.com",BridgeProtocol.ORIGIN+"/",BridgeProtocol.ORIGIN+".attacker.invalid",BridgeProtocol.ORIGIN+"?x=1",BridgeProtocol.ORIGIN+"#secret","https://user@"+BridgeProtocol.ORIGIN.removePrefix("https://"))) assertFalse(s,BridgeProtocol.validOrigin(s))
    }
    @Test fun acceptsOnlyCanonicalRandomIds() {
        assertTrue(BridgeProtocol.validId(id))
        for(s in listOf("",id.uppercase(),"../../secret","00000000-0000-0000-0000-000000000000")) assertFalse(BridgeProtocol.validId(s))
    }
    @Test fun onlyDeclaredOperationsAndBoundedLifetimeAreAccepted() {
        assertTrue(BridgeProtocol.validRequest(id,"status",1000,26000))
        assertFalse(BridgeProtocol.validRequest(id,"arbitrary_intent",1000,26000))
        assertFalse(BridgeProtocol.validRequest(id,"status",1000,1000))
        assertFalse(BridgeProtocol.validRequest(id,"status",1000,32000))
        assertFalse(BridgeProtocol.validRequest("bad","status",1000,26000))
    }
}
