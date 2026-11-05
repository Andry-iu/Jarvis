package it.jarviswake

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34,36])
class GoogleTokenBrokerTest {
    private val pairing=BridgeCredentials(BridgeProtocol.ORIGIN,"12345678-1234-4234-8234-123456789abc","d".repeat(43),"platform-private-access")
    private val config=GoogleVoiceConfig("","",0,"test-project","https://chatgpt.com/g/g-p-test/c/12345678-1234-4234-8234-123456789abc",authMode="backend")
    private class Transport:GoogleBrokerTransport {
        var calls=0;var stopped=false;var fail=false
        var reply=JSONObject().put("accessToken","unit-test-private-access-token").put("expiresIn",3600).put("project","test-project")
        override fun post(pairing:BridgeCredentials,body:JSONObject):JSONObject {
            calls++;assertEquals(setOf("deviceId","project"),body.keys().asSequence().toSet())
            assertEquals(pairing.deviceId,body.getString("deviceId"));if(fail)error("fake auth error");return reply
        }
        override fun cancel(){stopped=true}
    }
    @Test fun backendConfigHasNoManualTokenExpiryAndLegacyJsonDefaultsToManual() {
        config.validate();assertEquals("backend",config.json().getString("authMode"))
        assertThrows(IllegalArgumentException::class.java){config.copy(authMode="manual").validate()}
        assertThrows(IllegalArgumentException::class.java){config.copy(authMode="invalid").validate()}
        assertThrows(IllegalArgumentException::class.java){config.copy(sttToken="{\"private_key\":\"secret\"}").validate()}
        assertFalse(config.toString().contains(config.conversation))
    }
    @Test fun tokenIsCachedAndRefreshesBeforeNextOperationWithoutChangingFrozenConfig() {
        var now=1000L;val t=Transport();val broker=GoogleTokenBroker(pairing,t){now}
        val first=broker.resolve(config);assertEquals(1,t.calls);assertEquals(first.sttToken,first.ttsToken);assertTrue(first.expiresAt>System.currentTimeMillis())
        broker.resolve(config);assertEquals(1,t.calls);now+=3500000;broker.resolve(config);assertEquals(2,t.calls)
        assertEquals("",config.sttToken);assertEquals(0,config.expiresAt);assertFalse(broker.toString().contains(first.sttToken))
    }
    @Test fun projectMalformedShortOrUnexpectedGrantsFailWithoutRetry() {
        for(reply in listOf(
            JSONObject().put("accessToken","valid-private-token-for-test").put("expiresIn",3600).put("project","other-project"),
            JSONObject().put("accessToken","short").put("expiresIn",3600).put("project","test-project"),
            JSONObject().put("accessToken","valid-private-token-for-test").put("expiresIn",100).put("project","test-project"),
            JSONObject().put("accessToken","valid-private-token-for-test").put("expiresIn",3600).put("project","test-project").put("private_key","unexpected")
        )) {
            val t=Transport().apply{this.reply=reply};val broker=GoogleTokenBroker(pairing,t){1000}
            assertThrows(IllegalArgumentException::class.java){broker.resolve(config)};assertEquals(1,t.calls)
        }
        val t=Transport().apply{fail=true};assertThrows(IllegalStateException::class.java){GoogleTokenBroker(pairing,t).resolve(config)};assertEquals(1,t.calls)
    }
    @Test fun cancellationRejectsEvenCachedTokenAndLateTransportCompletion() {
        val t=Transport();val broker=GoogleTokenBroker(pairing,t){1000};broker.resolve(config);broker.cancel()
        assertTrue(t.stopped);assertThrows(IllegalStateException::class.java){broker.resolve(config)};assertEquals(1,t.calls)
        lateinit var pending:GoogleTokenBroker
        val transport=object:GoogleBrokerTransport {
            override fun post(pairing:BridgeCredentials,body:JSONObject):JSONObject {pending.cancel();return t.reply}
            override fun cancel(){}
        }
        pending=GoogleTokenBroker(pairing,transport){1000};assertThrows(IllegalStateException::class.java){pending.resolve(config)}
    }
    @Test fun aSlowExchangeCannotExtendTokenLifeAndConcurrentCallersShareOneGrant() {
        var now=0L
        val slow=object:GoogleBrokerTransport {
            override fun post(pairing:BridgeCredentials,body:JSONObject):JSONObject {now=3600000;return Transport().reply}
            override fun cancel(){}
        }
        assertThrows(IllegalStateException::class.java){GoogleTokenBroker(pairing,slow){now}.resolve(config)}
        val t=Transport();val broker=GoogleTokenBroker(pairing,t){1000}
        val threads=List(4){Thread{broker.resolve(config)}};threads.forEach{it.start()};threads.forEach{it.join()};assertEquals(1,t.calls)
    }
}
