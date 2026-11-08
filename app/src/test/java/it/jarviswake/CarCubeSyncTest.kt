package it.jarviswake

import android.content.ContentResolver
import android.net.Uri
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
class CarCubeSyncTest {
    @Test fun statusProtocolContainsOnlyFixedNumericFlagsAndNoCommands() {
        val provider = Robolectric.buildContentProvider(CarCubeSyncProvider::class.java).create().get()
        val uri = Uri.parse("content://it.jarviswake.cube.status/state")
        provider.query(uri,null,null,null,null).use { c ->
            assertEquals(listOf("protocol","cover_visible","muted","sampled_elapsed_ms"), c.columnNames.toList())
            assertTrue(c.moveToFirst()); assertEquals(1,c.getInt(0))
        }
        assertFalse(CarCubeSyncProvider.trusted(RuntimeEnvironment.getApplication(), 999999))
    }
    @Test fun coverDetachesWithoutWaitingForTheGame() {
        val controller = Robolectric.buildActivity(android.app.Activity::class.java).setup()
        val cover = HolographicOverlayView(controller.get())
        controller.get().setContentView(cover)
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        controller.get().setContentView(android.widget.FrameLayout(controller.get()))
        assertFalse(cover.isAttachedToWindow)
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertFalse(CarCubeSync.current().visible)
        controller.pause().stop().destroy()
    }
    @Test(expected = UnsupportedOperationException::class)
    fun providerNeverAcceptsCommandsOrWrites() {
        Robolectric.buildContentProvider(CarCubeSyncProvider::class.java).create().get().call("start",null,null)
    }
    @Test(expected = IllegalArgumentException::class)
    fun providerRejectsQueriesWithFreeParameters() {
        Robolectric.buildContentProvider(CarCubeSyncProvider::class.java).create().get()
            .query(Uri.parse("content://it.jarviswake.cube.status/state"),null,"anything",null,null)
    }
}
