package it.jarviswake

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import it.jarviswake.watch.WatchProtocol
import java.time.Duration
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34,36])
class WatchProbeTest {
    private val c get()=RuntimeEnvironment.getApplication()
    private val id="12345678-1234-4234-8234-123456789012"
    @Test fun publicLinkWithoutTicketAndOpaqueLinksNeverStartVoice() {
        for(link in listOf("jarviswake://watch/start?id=$id","jarviswake:opaque","jarviswake://watch/start?id=$id&other=1")) {
            val a=Robolectric.buildActivity(WatchEntryActivity::class.java,Intent(Intent.ACTION_VIEW,Uri.parse(link))).setup().get()
            assertTrue(a.isFinishing);assertNull(shadowOf(a).nextStartedActivity)
        }
        assertFalse(VoiceCycle.active)
    }
    @Test fun ticketRequiresConsentSequenceAndIsConsumedOnlyOnce() {
        assertEquals("disabled",WatchTickets.prepare(c,id))
        WatchTickets.enable(c,true)
        assertEquals("prepared",WatchTickets.prepare(c,id))
        assertFalse(WatchTickets.transition(c,id,"ready","consumed"))
        assertTrue(WatchTickets.transition(c,id,"prepared","accepted"))
        assertFalse(WatchTickets.transition(c,id,"prepared","accepted"))
        assertTrue(WatchTickets.transition(c,id,"accepted","ready"))
        assertTrue(WatchTickets.transition(c,id,"ready","consumed"))
        assertFalse(WatchTickets.transition(c,id,"ready","consumed"))
        assertEquals("replayed",WatchTickets.prepare(c,id))
    }
    @Test fun expiredDisabledAndRebootedTicketsCannotLaunch() {
        WatchTickets.enable(c,true);assertEquals("prepared",WatchTickets.prepare(c,id))
        ShadowSystemClock.advanceBy(Duration.ofSeconds(61))
        assertFalse(WatchTickets.transition(c,id,"prepared","accepted"))
        val fresh="22345678-1234-4234-8234-123456789012"
        assertEquals("prepared",WatchTickets.prepare(c,fresh))
        Settings.Global.putInt(c.contentResolver,Settings.Global.BOOT_COUNT,100)
        assertFalse(WatchTickets.transition(c,fresh,"prepared","accepted"))
        val third="32345678-1234-4234-8234-123456789012"
        assertEquals("prepared",WatchTickets.prepare(c,third));WatchTickets.enable(c,false)
        assertFalse(WatchTickets.transition(c,third,"prepared","accepted"))
    }
    @Test fun protocolRejectsOversizedUnknownFieldsAndUncorrelatedIds() {
        assertNotNull(WatchProtocol.request("{\"id\":\"$id\"}".toByteArray()))
        assertNull(WatchProtocol.request("{\"id\":\"$id\",\"audio\":\"data\"}".toByteArray()))
        assertNull(WatchProtocol.request(ByteArray(257)))
        assertNull(WatchProtocol.request("{\"id\":\"other\"}".toByteArray()))
    }
    @Test fun phoneProbeStartsWithoutMicrophoneCaptureOrVoiceAndNeverClaimsChatgptRoute() {
        val a=Robolectric.buildActivity(WatchProbeActivity::class.java).setup().visible()
        val snapshot=WatchAudioProbe.snapshot(a.get())
        assertFalse(snapshot.getBoolean("chatgptRouteConfirmed"))
        assertFalse(VoiceCycle.active)
        assertFalse(WatchAudioProbe.describe(snapshot).contains("instradamento verificato"))
        assertNull(shadowOf(a.get()).nextStartedActivity)
        a.pause().stop().destroy()
    }
}
