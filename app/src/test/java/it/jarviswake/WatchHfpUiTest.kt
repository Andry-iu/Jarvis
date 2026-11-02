package it.jarviswake

import android.Manifest
import android.app.NotificationManager
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34,36])
class WatchHfpUiTest {
    private fun buttons(v:View):List<Button> = (if(v is Button) listOf(v) else emptyList())+
        (if(v is ViewGroup) (0 until v.childCount).flatMap {buttons(v.getChildAt(it))} else emptyList())
    @Test fun permissionIsRequestedOnlyByExplicitButtonAndNoImplicitAudioTrial() {
        val c=RuntimeEnvironment.getApplication()
        shadowOf(c).denyPermissions(Manifest.permission.BLUETOOTH_CONNECT)
        val lifecycle=Robolectric.buildActivity(WatchProbeActivity::class.java).setup().visible();val a=lifecycle.get()
        assertNull(shadowOf(a).lastRequestedPermission)
        val field=WatchProbeActivity::class.java.getDeclaredField("hfp").apply {isAccessible=true}
        val hfp=field.get(a) as WatchHfpProbe
        assertEquals("permission_required",hfp.serviceCode);assertFalse(hfp.trial.active)
        assertEquals("idle",hfp.trial.code);assertTrue(hfp.devices().isEmpty())
        buttons(a.window.decorView).single {it.text=="Autorizza dispositivi vicini"}.performClick()
        assertArrayEquals(arrayOf(Manifest.permission.BLUETOOTH_CONNECT),shadowOf(a).lastRequestedPermission.requestedPermissions)
        buttons(a.window.decorView).single {it.text=="Leggi profili Bluetooth"}.performClick()
        assertFalse(hfp.trial.active);assertFalse(VoiceCycle.active)
        lifecycle.pause().stop().destroy();assertFalse(hfp.trial.active)
    }
    @Test fun returnNotificationIsExplicitAndTargetsOnlyProbePage() {
        val c=RuntimeEnvironment.getApplication()
        shadowOf(c).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val lifecycle=Robolectric.buildActivity(WatchProbeActivity::class.java).setup();val a=lifecycle.get()
        val n=shadowOf(c.getSystemService(NotificationManager::class.java)).getNotification(94)
        assertNotNull(n)
        val intent=shadowOf(n.contentIntent).savedIntent
        assertEquals(WatchProbeActivity::class.java.name,intent.component?.className)
        assertNull(intent.data);assertNull(shadowOf(a).nextStartedActivity)
        assertFalse(VoiceCycle.active)
        a.finish();lifecycle.pause().stop().destroy()
        assertNull(shadowOf(c.getSystemService(NotificationManager::class.java)).getNotification(94))
    }
}
