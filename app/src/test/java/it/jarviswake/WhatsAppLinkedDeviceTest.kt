package it.jarviswake

import android.content.Context
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34,36])
class WhatsAppLinkedDeviceTest {
    private val oldFactory=PhoneWhatsApp.factory
    private class Fake:WhatsAppEngine {
        var value="""{"state":"not_linked","qr":"","expires":0}"""
        @Volatile var pairRequests=0
        var sends=0
        override fun connect(pair:Boolean) { if(pair) pairRequests++ }
        override fun state()=value
        override fun unlink()="unlinked"
        override fun send(number:String,text:String,id:String,timeout:Long,allowed:()->Boolean):String {
            if(!allowed()) return "cancelled"
            sends++;return "whatsapp_server_accepted"
        }
    }
    private lateinit var fake:Fake
    @Before fun setup() {
        resetEngine();fake=Fake();PhoneWhatsApp.factory={ fake }
    }
    private fun resetEngine() {
        PhoneWhatsApp::class.java.getDeclaredField("engine").apply { isAccessible=true }.set(PhoneWhatsApp,null)
    }
    @After fun cleanup() { resetEngine();PhoneWhatsApp.factory=oldFactory }
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup) (0 until v.childCount).flatMap {views(v.getChildAt(it))} else emptyList()
    private fun await(check:()->Boolean) {
        val end=System.nanoTime()+2_000_000_000L
        while(System.nanoTime()<end) {shadowOf(Looper.getMainLooper()).idle();if(check()) return;Thread.sleep(10)}
        assertTrue(check())
    }
    @Test fun settingsShowQrOnlyAfterPairingRequestAndHideExpiredCode() {
        val c=Robolectric.buildActivity(WhatsAppQrActivity::class.java).create().start().resume().visible()
        val a=c.get();val root=a.window.decorView
        await {PhoneWhatsApp.state().optString("state")=="not_linked"}
        assertEquals(0,fake.pairRequests)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        views(root).filterIsInstance<Button>().single {it.text.toString()=="Genera QR WhatsApp"}.performClick()
        await {fake.pairRequests==1}
        fake.value=JSONObject().put("state","qr_ready").put("qr","synthetic-test-qr-not-an-account").put("expires",System.currentTimeMillis()+60_000).toString()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        val qr=views(root).filterIsInstance<ImageView>().single()
        assertEquals(View.VISIBLE,qr.visibility);assertNotNull(qr.drawable)
        val status=PhoneWhatsApp.state()
        assertFalse(status.has("qr"));assertFalse(status.has("expires"))
        fake.value=JSONObject().put("state","qr_ready").put("qr","synthetic-test-qr-not-an-account").put("expires",1).toString()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertEquals(View.GONE,qr.visibility)
        assertEquals(0,fake.sends)
        views(root).filterIsInstance<Button>().single {it.text.toString()=="Indietro"}.performClick()
        assertTrue(a.isFinishing);c.pause().stop().destroy()
    }
    @Test fun expiredAndCancelledSendDoNotReachTheEngineOrLaunchAnApp() {
        val app=RuntimeEnvironment.getApplication();shadowOf(app).clearNextStartedActivities()
        val expired=PhoneWhatsApp.send(app,"+39000000000","Explicit test","33c67166-7666-4497-950d-0825781fe769",SystemClock.elapsedRealtime()-1) {true}
        assertEquals("expired_before_dispatch",expired.getString("code"))
        val cancelled=PhoneWhatsApp.send(app,"+39000000000","Explicit test","33c67166-7666-4497-950d-0825781fe769",SystemClock.elapsedRealtime()+1000) {false}
        assertEquals("cancelled",cancelled.getString("code"));assertEquals(0,fake.sends)
        assertNull(shadowOf(app).nextStartedActivity)
    }
    @Test fun successfulDispatchReportsOnlyServerAcknowledgementAndNeverOpensWhatsApp() {
        val app=RuntimeEnvironment.getApplication();shadowOf(app).clearNextStartedActivities()
        val r=PhoneWhatsApp.send(app,"+39000000000","Explicit test","33c67166-7666-4497-950d-0825781fe769",SystemClock.elapsedRealtime()+1000) {true}
        assertEquals("whatsapp_server_accepted",r.getString("code"));assertEquals(1,fake.sends)
        assertFalse(r.has("delivered"));assertNull(shadowOf(app).nextStartedActivity)
    }
}
