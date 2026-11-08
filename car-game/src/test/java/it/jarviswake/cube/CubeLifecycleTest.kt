package it.jarviswake.cube

import android.graphics.Bitmap
import android.graphics.Canvas
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34, 36])
class CubeLifecycleTest {
    @Test fun gameOpensWithoutJarvisAndStopsWhenPaused() {
        val controller = Robolectric.buildActivity(CubeGameActivity::class.java).setup()
        assertFalse(controller.get().isFinishing)
        controller.pause().stop().destroy()
    }
    @Test fun muteAndLostConnectionFreezeAnimationAndIdleResetsIt() {
        val view = CubeView(RuntimeEnvironment.getApplication(), CubeGame())
        view.updateLink(CubeLink("linked", true, true)); assertTrue(view.paused)
        view.updateLink(CubeLink("linked", true, false)); assertFalse(view.paused)
        view.updateLink(CubeLink("stale")); assertTrue(view.paused)
        view.updateLink(CubeLink("linked", false, true)); assertFalse(view.paused)
    }
    @Test fun renderingHandlesCanonicalAndTinyAspectRatios() {
        val view = CubeView(RuntimeEnvironment.getApplication(), CubeGame())
        for ((w,h) in listOf(800 to 480, 1920 to 484, 480 to 800, 1 to 1)) {
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            view.layout(0,0,w,h); view.draw(Canvas(bitmap)); bitmap.recycle()
        }
    }
}
