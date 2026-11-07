package it.jarviswake

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34,36])
class BridgeLiveChannelTest {
    private val auth=BridgeCredentials(BridgeProtocol.ORIGIN,"4ac0e8f0-19b7-413c-aed8-5e49089e5f18","synthetic-device","synthetic-site")
    private class Connection(url:URL,private val frames:String,private val code:Int=200):HttpURLConnection(url) {
        val sent=ByteArrayOutputStream();var disconnected=false
        override fun connect() {}
        override fun usingProxy()=false
        override fun disconnect() {disconnected=true}
        override fun getOutputStream()=sent
        override fun getInputStream()=ByteArrayInputStream(frames.toByteArray())
        override fun getResponseCode()=code
        override fun getContentType()="application/x-ndjson"
    }
    private fun ready()="""{"type":"ready","protocol":1,"serverNow":10000}
"""
    private fun request(id:String,op:String="navigate",sent:Long=11000)=JSONObject().put("type","request").put("sentAt",sent)
        .put("request",JSONObject().put("requestId",id).put("operation",op).put("createdAt",10000).put("expiresAt",35000)
            .put("remainingMs",24000).put("params",JSONObject())).toString()+"\n"
    @Test fun successiveCommandsReuseOneAuthenticatedStreamAndKeepRequestIds() {
        val a="d73b7e3a-1045-459b-9a22-811a2c77f070";val b="6e080285-3c20-434f-a16e-0c7e8394df6f"
        var opened=0;lateinit var connection:Connection
        val channel=BridgeLiveChannel(auth,142,{url->opened++;Connection(url,ready()+request(a)+"{\"type\":\"heartbeat\"}\n"+request(b,"get_contact")).also {connection=it}},{5000L})
        assertEquals(a,channel.next()!!.request.getString("requestId"))
        assertEquals(b,channel.next()!!.request.getString("requestId"))
        assertEquals(1,opened)
        assertEquals(BridgeProtocol.ORIGIN+"/device/live",connection.url.toString())
        assertEquals("Bearer synthetic-device",connection.getRequestProperty("Authorization"))
        assertEquals("Bearer synthetic-site",connection.getRequestProperty("OAI-Sites-Authorization"))
        assertFalse(connection.instanceFollowRedirects)
        val body=JSONObject(connection.sent.toString("UTF-8"));assertEquals(1,body.getInt("protocol"))
        assertEquals(auth.deviceId,body.getString("deviceId"));assertTrue(body.getJSONArray("operations").length()>10)
        assertNull(channel.next());assertTrue(connection.disconnected)
    }
    @Test fun closeDoesNotOpenAnotherConnectionOrReadAnotherCommand() {
        var opens=0
        val channel=BridgeLiveChannel(auth,142,{url->opens++;Connection(url,ready())})
        channel.close()
        assertThrows(Exception::class.java){channel.next()}
        assertEquals(0,opens)
    }
    @Test fun rejectsUnreadyUnknownOrOversizedFramesAndTruncatedInput() {
        val id="d73b7e3a-1045-459b-9a22-811a2c77f070"
        for(frames in listOf(request(id),ready()+request(id,"shell"),ready()+"x".repeat(24*1024+1)+"\n",ready()+"{\"type\":\"request\"}",ready()+"{\"type\":\"unknown\"}\n")) {
            val channel=BridgeLiveChannel(auth,142,{url->Connection(url,frames)})
            try {assertThrows(Exception::class.java){channel.next()}} finally {channel.close()}
        }
    }
    @Test fun unavailableEndpointNeverRedirectsAndCanBeClosedForLegacyFallback() {
        lateinit var conn:Connection
        val channel=BridgeLiveChannel(auth,142,{url->Connection(url,"",404).also {conn=it}})
        assertThrows(Exception::class.java){channel.next()}
        assertTrue(conn.disconnected);assertFalse(conn.instanceFollowRedirects)
    }
    @Test fun leaseBudgetUsesRelativeServerAndMonotonicTimesInsteadOfPhoneWallClock() {
        val id="d73b7e3a-1045-459b-9a22-811a2c77f070"
        // Clock: open start=1000, handshake end=1200, ready received=1250, request received=2600.
        val ticks=java.util.ArrayDeque(listOf(1000L,1200L,1250L,2600L))
        val channel=BridgeLiveChannel(auth,142,{url->Connection(url,ready()+request(id,sent=11000))},{ticks.removeFirst()})
        try {
            val result=channel.next()!!
            assertEquals(550L,result.transitBudget)
            assertEquals(23450L,BridgeProtocol.remainingLease(result.request.getLong("remainingMs"),result.transitBudget))
        } finally {channel.close()}
    }
}
