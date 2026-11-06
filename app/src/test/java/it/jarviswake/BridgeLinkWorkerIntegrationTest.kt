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
    private val oldCommandReader=BridgeLink.commandReader;private val oldCommandWriter=BridgeLink.commandWriter
    @Volatile private var ledger="{}"
    private val workerField=BridgeLink::class.java.getDeclaredField("worker").apply {isAccessible=true}
    private val worker get()=workerField.get(null) as? Thread
    private val entered=CountDownLatch(1);private val release=CountDownLatch(1);private val responded=CountDownLatch(1)
    private val polls=AtomicInteger();private var interrupted=false
    private var response:JSONObject?=null
    private val id="4ac0e8f0-19b7-413c-aed8-5e49089e5f18"
    private var service:org.robolectric.android.controller.ServiceController<BridgeLinkService>?=null
    @Before fun setup() {
        BridgeLink.commandReader={JSONObject(ledger)};BridgeLink.commandWriter={_,value->ledger=value.toString()}
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
        (BridgeLink::class.java.getDeclaredField("exportJob").apply {isAccessible=true}.get(null) as? Thread)?.join(1000)
        BridgeLink.credentialsReader=oldReader;BridgeLink.transport=oldTransport
        BridgeLink.commandReader=oldCommandReader;BridgeLink.commandWriter=oldCommandWriter
        PhoneContactExport.enable(app,false)
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

    private fun awaitMain(latch:CountDownLatch) {
        val until=System.nanoTime()+5_000_000_000L
        while(latch.count>0 && System.nanoTime()<until) {shadowOf(Looper.getMainLooper()).idle();Thread.sleep(5)}
        assertEquals(0L,latch.count)
    }
    @Test fun timedOutOutboxDoesNotStarveStatusOrReplayTheConsumedAction() {
        val delivered=CountDownLatch(1);val attempts=AtomicInteger();val claims=AtomicInteger()
        ledger=JSONObject().put("old",JSONObject().put("at",System.currentTimeMillis()).put("deviceId",id)
            .put("requestId",id).put("operation","navigate").put("acked",false).put("result",JSONObject().put("code","intent_dispatched"))).toString()
        BridgeLink.transport={_,path,data,_->when(path) {
            "/device/poll" -> if(polls.incrementAndGet()==1) {val now=System.currentTimeMillis();JSONObject().put("request",JSONObject()
                .put("requestId",id).put("operation","status").put("createdAt",now).put("expiresAt",now+25000))} else JSONObject()
            "/device/respond" -> if(data.has("result")) {attempts.incrementAndGet();throw java.net.SocketTimeoutException("simulated delayed acknowledgement")}
                else {delivered.countDown();JSONObject()}
            "/device/claim" -> {claims.incrementAndGet();error("consumed action must not be claimed again")}
            else -> error("unexpected fixture route")
        }}
        BridgeLink.owner(app,"setup",true);awaitMain(delivered)
        assertTrue(attempts.get()>0);assertEquals(0,claims.get())
        assertFalse(JSONObject(ledger).getJSONObject("old").getBoolean("acked"))
    }
    @Test fun longExportRunsIndependentlyWhileStatusIsAnsweredAndCompletesExactlyOnce() {
        val collecting=CountDownLatch(1);val providerRelease=CountDownLatch(1)
        val statusDelivered=CountDownLatch(1);val exportDelivered=CountDownLatch(1);val heartbeat=CountDownLatch(1)
        val claims=AtomicInteger();val exports=AtomicInteger();val first=java.util.concurrent.atomic.AtomicBoolean(true)
        val exportId="dc698e98-3665-492b-a78e-6191f9234f9c"
        val fake=PhoneContactExportTest.FakeContacts()
        org.robolectric.shadows.ShadowContentResolver.registerProviderInternal(android.provider.ContactsContract.AUTHORITY,
            object:android.content.ContentProvider() {
                override fun onCreate()=true
                override fun getType(uri:android.net.Uri)="test/contacts"
                override fun query(uri:android.net.Uri,projection:Array<out String>?,selection:String?,args:Array<out String>?,sort:String?):android.database.Cursor? {
                    if(first.getAndSet(false)) {collecting.countDown();check(providerRelease.await(5,TimeUnit.SECONDS))}
                    return fake.query(uri,projection,selection,args,sort)
                }
                override fun insert(uri:android.net.Uri,values:android.content.ContentValues?):android.net.Uri?=error("read only")
                override fun update(uri:android.net.Uri,values:android.content.ContentValues?,selection:String?,args:Array<out String>?)=error("read only")
                override fun delete(uri:android.net.Uri,selection:String?,args:Array<out String>?)=error("read only")
            })
        shadowOf(app).grantPermissions(android.Manifest.permission.READ_CONTACTS)
        PhoneContactExport.enable(app,true)
        BridgeLink.transport={_,path,data,_->when(path) {
            "/device/poll" -> {val n=polls.incrementAndGet();if(n<=2) {val now=System.currentTimeMillis();JSONObject().put("request",JSONObject()
                .put("requestId",if(n==1) exportId else id).put("operation",if(n==1) "contacts_export_start" else "status")
                .put("createdAt",now).put("expiresAt",now+25000).put("remainingMs",25000).put("params",JSONObject().put("contact_ref","")))} else JSONObject()}
            "/device/claim" -> {claims.incrementAndGet();JSONObject().put("state","claimed")}
            "/device/progress" -> {heartbeat.countDown();JSONObject().put("state","accepted")}
            "/device/respond" -> {if(data.has("snapshot")) statusDelivered.countDown() else {
                assertEquals("contacts_export_ready",data.getJSONObject("result").getString("code"));exports.incrementAndGet();exportDelivered.countDown()
            };JSONObject()}
            else -> error("unexpected fixture route")
        }}
        try {
            BridgeLink.owner(app,"setup",true);awaitMain(collecting);awaitMain(heartbeat)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(3))
            awaitMain(statusDelivered);assertEquals(0,exports.get())
            providerRelease.countDown();awaitMain(exportDelivered)
            assertEquals(1,claims.get());assertEquals(1,exports.get());assertEquals(0,fake.writes)
        } finally {providerRelease.countDown()}
    }
}

