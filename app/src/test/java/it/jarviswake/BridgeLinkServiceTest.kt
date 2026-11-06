package it.jarviswake

import android.app.Application
import android.app.Service
import android.content.Intent
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

/** No credentials or live network. Invalid encrypted fixture exercises lifecycle independently of poll. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34,36])
class BridgeLinkServiceTest {
    private val app:Application get()=RuntimeEnvironment.getApplication()
    private val owners get()=BridgeLink::class.java.getDeclaredField("owners").apply {isAccessible=true}.get(null) as Set<*>
    @Before fun setup() {app.getSharedPreferences("bridge_link",0).edit().clear().commit()}
    @After fun cleanup() {BridgeSettings.enable(app,false);BridgeLink.refresh(app);app.getSharedPreferences("bridge_link",0).edit().clear().commit()}

    @Test fun serviceOwnerSurvivesPageDepartureAndIsReleasedAtServiceDestruction() {
        app.getSharedPreferences("bridge_link",0).edit().putBoolean("enabled",true).putString("encrypted","invalid-test-fixture").commit()
        val lifecycle=Robolectric.buildService(BridgeLinkService::class.java).create()
        try {
            assertEquals(Service.START_STICKY,lifecycle.get().onStartCommand(null,0,1))
            BridgeLink.owner(app,"setup",true)
            BridgeLink.owner(app,"setup",false)
            assertTrue("The page is not the persistent owner",owners.contains("link_service"))
            assertFalse(owners.contains("setup"))
        } finally {lifecycle.destroy()}
        assertFalse(owners.contains("link_service"))
    }
    @Test fun disabledBridgeDoesNotStartAndNotificationStopDisablesOnlyTheBridge() {
        BridgeLinkService.sync(app)
        assertNull(shadowOf(app).nextStartedService)
        app.getSharedPreferences("bridge_link",0).edit().putBoolean("enabled",true).putString("encrypted","invalid-test-fixture").commit()
        val lifecycle=Robolectric.buildService(BridgeLinkService::class.java).create()
        try {
            val intent=Intent().setAction("it.jarviswake.STOP_LINK")
            assertEquals(Service.START_NOT_STICKY,lifecycle.get().onStartCommand(intent,0,1))
            assertFalse(BridgeSettings.enabled(app))
            assertTrue(BridgeSettings.configured(app))
        } finally {lifecycle.destroy()}
    }
}
