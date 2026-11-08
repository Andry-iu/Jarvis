package it.jarviswake

import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34, 36])
class CarCubeProbeActivityTest {
    @After fun cleanup() { CarCubeProbe.disarm() }

    @Test fun directLaunchWithNoValidTargetFinishesBeforeCreatingBrowser() {
        val controller = Robolectric.buildActivity(CarCubeProbeActivity::class.java).create()
        assertTrue(controller.get().isFinishing)
        controller.destroy()
    }

    @Test fun noDisplayOrProjectionDoesNotArmOrLaunchAnything() {
        val application = RuntimeEnvironment.getApplication()
        assertTrue(CarCubeProbe.targets(application).isEmpty())
        assertFalse(CarCubeProbe.arm(application, 42))
        assertFalse(CarCubeProbe.state.armed(android.os.SystemClock.elapsedRealtime()))
    }

    @Test fun probeSettingsDoNotEnableAutomaticProjectionOnOpen() {
        val controller = Robolectric.buildActivity(CarCubeProbeSettingsActivity::class.java).setup()
        assertFalse(CarCubeProbe.state.armed(android.os.SystemClock.elapsedRealtime()))
        controller.pause().stop().destroy()
    }

    @Test fun ordinaryPhoneCoverCanDetachImmediatelyWithProbeDisabled() {
        val controller = Robolectric.buildActivity(android.app.Activity::class.java).setup()
        val activity = controller.get()
        val cover = HolographicOverlayView(activity)
        activity.setContentView(cover)
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        activity.setContentView(android.widget.FrameLayout(activity))
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertFalse(cover.isAttachedToWindow)
        assertFalse(CarCubeProbe.state.armed(android.os.SystemClock.elapsedRealtime()))
        controller.pause().stop().destroy()
    }
}
