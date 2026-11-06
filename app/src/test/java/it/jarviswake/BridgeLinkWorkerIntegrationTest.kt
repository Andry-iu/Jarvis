package it.jarviswake

import android.app.Application
import android.os.Looper
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Real poll loop and foreground lifecycle, with only credentials/HTTP replaced by synthetic data. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34,36])
class BridgeLinkWorkerIntegrationTest {
    private val app:Application get()=RuntimeEnvironment.getApplication()
    private val oldReader=BridgeLink.credentialsReader;private val oldTransport=BridgeLink.transport
    private val workerField=BridgeLink::class.java.getDeclaredField("worker").apply {isAccessible=true}
    private val worker get()=workerField.get(null) as? Thread
    private val entered=CountDownLatch(1);private val release=CountDownLatch(1);private val responded=CountDownLatch(1)
    private val polls=AtomicInteger();private var interrupted=false
    private var response:JSONObject?=null
    private val id="4ac0e8f0-19b7-413c-aed8-5e49089e5f18"
    private var service:org.robolectric.android.controller.ServiceController<BridgeLinkService>?=null
    @Before fun setup() {
        app.getSharedPreferences("bridge_link",0).edit().clear().putBoolean("enabled",true).putString("encrypted","test-fixture").commit()
        BridgeLink.credentialsReader={BridgeCredentials(BridgeProtocol.ORIGIN,id,"simulated-device-token","simulated-site-token")}
        BridgeLink.transport={_,path,data,_->
            when(path) {
                "/device/poll" -> {
                    val count=polls.incrementAndGet();entered.countDown()
                    try {check(release.await(3,TimeUnit.SECONDS))} catch(e:InterruptedException){interrupted=true;throw e}
                    if(count==1) {val now=System.currentTimeMillis();JSONObject().put("request",JSONObject().put("requestId",id)
                        .put("operation","status").put("createdAt",now).put("expiresAt",now+25000))} else JSONObject()
                }
                "/device/respond" -> {response=JSONObject(data.toString());responded.countDown();JSONObject()}
                else -> error("unexpected simulated transport operation")
            }
        }
    }
    @After fun cleanup() {
        val thread=worker
        BridgeSettings.enable(app,false);BridgeLink.refresh(app);release.countDown()
        service?.destroy();service=null
        BridgeLink.owner(app,"setup",false)
        thread?.join(1000)
        BridgeLink.credentialsReader=oldReader;BridgeLink.transport=oldTransport
        app.getSharedPreferences("bridge_link",0).edit().clear().commit()
    }
    @Test fun sameWorkerDeliversStatusAfterPageLeavesWithWakeOffAndServiceHeartbeatDoesNotReplaceIt() {
        BridgeLink.owner(app,"setup",true)
        assertTrue(entered.await(2,TimeUnit.SECONDS));val original=worker;assertNotNull(original)
        service=Robolectric.buildService(BridgeLinkService::class.java).create()
        service!!.get().onStartCommand(null,0,1)
        BridgeLink.owner(app,"setup",false)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))
        assertSame(original,worker);assertEquals(1,polls.get());assertFalse(interrupted)
        release.countDown()
        val until=System.nanoTime()+2_000_000_000L
        while(responded.count>0 && System.nanoTime()<until) {shadowOf(Looper.getMainLooper()).idle();Thread.sleep(5)}
        assertEquals(0L,responded.count);assertEquals(id,response!!.getString("requestId"))
        assertFalse(response!!.getJSONObject("snapshot").getJSONObject("wake").getBoolean("serviceRunning"))
        assertSame(original,worker)
        BridgeSettings.enable(app,false);BridgeLink.refresh(app);original!!.join(1000)
        assertFalse(original.isAlive)
    }
}
