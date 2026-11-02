package it.jarviswake

import android.Manifest
import android.media.AudioManager
import android.provider.Settings
import android.view.View
import android.widget.CheckBox
import java.lang.ref.WeakReference
import java.time.Duration
import java.util.UUID
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings
import org.robolectric.shadows.ShadowSystemClock

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34,36])
class CoverOnlyRevealTest {
    private val token=UUID.randomUUID().toString()
    private val cycleField=VoiceCycle::class.java.getDeclaredField("state").apply {isAccessible=true}
    private val serviceField=VoiceAccessibilityService::class.java.getDeclaredField("instance").apply {isAccessible=true}
    private val previousCycle=cycleField.get(null)
    private val previousService=serviceField.get(null)
    private lateinit var lifecycle:org.robolectric.android.controller.ServiceController<VoiceAccessibilityService>
    private lateinit var service:VoiceAccessibilityService
    private lateinit var controller:NativeVoiceController
    private val hudField=NativeVoiceController::class.java.getDeclaredField("hud").apply {isAccessible=true}
    @Before fun setup() {
        lifecycle=Robolectric.buildService(VoiceAccessibilityService::class.java).create();service=lifecycle.get()
        serviceField.set(null,WeakReference(service));setCycle()
        NativeChatSettings.enable(service,true)
        service.getSharedPreferences("voice_accessibility",0).edit().putString("native_token",token)
            .putBoolean("native_start_sent",true).putString("native_voice_observed",token).apply()
        controller=NativeVoiceController(service,token)
        VoiceAccessibilityService::class.java.getDeclaredField("nativeController").apply {isAccessible=true}.set(service,controller)
        controller.launch(openShortcut=false,playWaitingAudio=false)
        service.getSystemService(AudioManager::class.java).mode=AudioManager.MODE_IN_COMMUNICATION
        shadowOf(service.application).clearNextStartedActivities()
    }
    private fun setCycle() {cycleField.set(null,VoiceCycleState().apply {restoreNativeExit(token)})}
    @After fun clean() {
        WatchProbeReturn.cancel(service);controller.destroy();lifecycle.destroy()
        cycleField.set(null,previousCycle);serviceField.set(null,previousService)
    }
    private fun action(view:HolographicOverlayView,name:String) {
        @Suppress("UNCHECKED_CAST")
        val callback=HolographicOverlayView::class.java.getDeclaredField(name).apply {isAccessible=true}.get(view) as ()->Unit
        callback()
    }
    @Test fun leftGestureDetachesRealHudWithoutStopCycleReplacementOrWakeResumeAndRecoveryCannotReopen() {
        val view=hudField.get(controller) as HolographicOverlayView
        action(view,"onRevealChat")
        assertNull(hudField.get(controller));assertFalse(view.isAttachedToWindow);assertEquals(View.GONE,view.visibility)
        assertTrue(VoiceCycle.matches(token));assertEquals(VoiceCycleState.Phase.VOICE,VoiceCycle.phase)
        assertFalse(VoiceCycle.hiddenForWake);assertFalse(VoiceCycle.touchGate.canDispatch)
        assertNull(VoiceAccessibilityService.pendingNativeExit(service));assertFalse(service.nativeStopSent(token))
        assertNull(shadowOf(service.application).nextStartedActivity)
        val recovered=NativeVoiceController(service,token);recovered.launch(openShortcut=false,playWaitingAudio=false)
        assertNull(hudField.get(recovered));recovered.destroy()
        assertFalse(VoiceAccessibilityService.finishReleasedHiddenNative(service))
    }
    @Test fun downStillRequestsOneStopAndHomeAndLeftCannotUndoAnAlreadyPendingExit() {
        val view=hudField.get(controller) as HolographicOverlayView
        action(view,"onHide");assertNull(hudField.get(controller));assertFalse(view.isAttachedToWindow)
        val p=service.getSharedPreferences("voice_accessibility",0)
        assertTrue(p.getBoolean("native_stop",false));assertTrue(p.getBoolean("native_return_home",false))
        action(view,"onRevealChat");assertEquals(token,VoiceAccessibilityService.pendingNativeExit(service))
        assertTrue(p.getBoolean("native_return_home",false));assertTrue(VoiceCycle.active)
    }
    @Test fun centralDoubleTapStillRequestsStopWhileDetachedHudStaysGone() {
        val view=hudField.get(controller) as HolographicOverlayView;action(view,"onClose")
        assertNull(hudField.get(controller));assertEquals(token,VoiceAccessibilityService.pendingNativeExit(service))
        action(view,"onRevealChat");assertEquals(token,VoiceAccessibilityService.pendingNativeExit(service))
    }
    @Test fun hiddenVoiceKeepsWakeSuspendedUntilAudioIsStablyFreeWithoutAnyStopClick() {
        action(hudField.get(controller) as HolographicOverlayView,"onRevealChat")
        assertFalse(VoiceAccessibilityService.finishReleasedHiddenNative(service));assertTrue(VoiceCycle.active)
        service.getSystemService(AudioManager::class.java).mode=AudioManager.MODE_NORMAL
        assertFalse(VoiceAccessibilityService.finishReleasedHiddenNative(service))
        ShadowSystemClock.advanceBy(Duration.ofMillis(999));assertFalse(VoiceAccessibilityService.finishReleasedHiddenNative(service))
        ShadowSystemClock.advanceBy(Duration.ofMillis(1));assertTrue(VoiceAccessibilityService.finishReleasedHiddenNative(service))
        assertFalse(VoiceCycle.active);assertNull(VoiceAccessibilityService.pendingNativeExit(service))
    }
    @Test fun realProbeGuardRejectsPendingStopAndMissingVoiceObservation() {
        shadowOf(service.application).grantPermissions(Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.READ_PHONE_STATE)
        val a=Robolectric.buildActivity(WatchProbeActivity::class.java).setup();val probe=a.get()
        (WatchProbeActivity::class.java.getDeclaredField("consent").apply {isAccessible=true}.get(probe) as CheckBox).isChecked=true
        val allowed=WatchProbeActivity::class.java.getDeclaredMethod("allowed").apply {isAccessible=true}
        assertEquals(true,allowed.invoke(probe))
        val p=service.getSharedPreferences("voice_accessibility",0);p.edit().putBoolean("native_stop",true).apply()
        assertEquals(false,allowed.invoke(probe))
        p.edit().remove("native_stop").remove("native_voice_observed").apply();assertEquals(false,allowed.invoke(probe))
        a.pause().stop().destroy()
    }
    @Test fun preparedReturnStartsOnlyOwnProbeAfterConfirmedVoiceAndRealOnResumeAcknowledgesOnce() {
        cycleField.set(null,VoiceCycleState());WatchTickets.enable(service,true);ShadowSettings.setCanDrawOverlays(true)
        assertTrue(WatchProbeReturn.prepare(service));setCycle();WatchProbeReturn.bindWatch(service,token)
        WatchProbeReturn.nativeVoiceObserved(service,token);WatchProbeReturn.nativeVoiceObserved(service,token)
        shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(2500))
        val intent=shadowOf(service.application).nextStartedActivity
        assertNotNull(intent);assertEquals(WatchProbeActivity::class.java.name,intent.component?.className)
        assertEquals(token,intent.getStringExtra(WatchProbeReturn.EXTRA));assertNull(shadowOf(service.application).nextStartedActivity)
        val page=Robolectric.buildActivity(WatchProbeActivity::class.java,intent).setup()
        assertTrue(WatchProbeReturn.describe().contains("page_opened"));assertNull(hudField.get(controller))
        assertNull(VoiceAccessibilityService.pendingNativeExit(service));assertTrue(VoiceCycle.matches(token))
        shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(5000))
        assertNull(shadowOf(service.application).nextStartedActivity);assertTrue(VoiceCycle.matches(token))
        page.pause().stop().destroy()
    }
    @Test fun stopRequestedDuringPreparedDelayPreventsAnyAutomaticPageLaunch() {
        cycleField.set(null,VoiceCycleState());WatchTickets.enable(service,true);ShadowSettings.setCanDrawOverlays(true)
        assertTrue(WatchProbeReturn.prepare(service));setCycle();WatchProbeReturn.bindWatch(service,token)
        WatchProbeReturn.nativeVoiceObserved(service,token)
        service.requestNativeExit(token,returnHome=true)
        shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(2500))
        assertNull(shadowOf(service.application).nextStartedActivity)
        assertTrue(WatchProbeReturn.describe().contains("stop_pending"))
        assertEquals(token,VoiceAccessibilityService.pendingNativeExit(service));assertTrue(VoiceCycle.active)
    }

    @Test fun preparedReturnDoesNotLaunchAfterAccessibilityServiceDisconnects() {
        cycleField.set(null,VoiceCycleState());WatchTickets.enable(service,true);ShadowSettings.setCanDrawOverlays(true)
        assertTrue(WatchProbeReturn.prepare(service));setCycle();WatchProbeReturn.bindWatch(service,token)
        WatchProbeReturn.nativeVoiceObserved(service,token)
        serviceField.set(null,WeakReference<VoiceAccessibilityService>(null))
        shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(2500))
        assertNull(shadowOf(service.application).nextStartedActivity)
        assertTrue(WatchProbeReturn.describe().contains("voice_not_ready"));assertTrue(VoiceCycle.active)
    }

}
