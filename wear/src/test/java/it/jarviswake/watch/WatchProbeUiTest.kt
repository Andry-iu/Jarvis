package it.jarviswake.watch

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34,36])
class WatchProbeUiTest {
    private fun all(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap {all(v.getChildAt(it))}else emptyList()
    @Test fun watchOpensWithExplicitTestsAndNoAutomaticMicrophoneOrRemoteStart() {
        val a=Robolectric.buildActivity(WatchProbeActivity::class.java).create().get()
        val buttons=all(a.window.decorView).filterIsInstance<Button>().map {it.text.toString()}
        assertTrue(buttons.contains("1 · Registra 5 secondi"));assertTrue(buttons.contains("2 · Riascolta sul Watch"))
        assertTrue(buttons.contains("3 · Fotografia audio telefono"));assertTrue(buttons.contains("4 · Avvia Jarvis sul telefono"))
        for(name in listOf("recorder","player","request")) assertNull(WatchProbeActivity::class.java.getDeclaredField(name).apply {isAccessible=true}.get(a))
        a.finish()
    }
    @Test fun leavingTheWatchScreenErasesTheSampleAndCancelsPendingWork() {
        val c=Robolectric.buildActivity(WatchProbeActivity::class.java).setup().visible();val a=c.get()
        val bytes=shortArrayOf(1,1200,-300)
        WatchProbeActivity::class.java.getDeclaredField("sample").apply {isAccessible=true}.set(a,bytes)
        c.pause().stop().destroy()
        assertTrue(bytes.all {it==0.toShort()})
        assertEquals(0,(WatchProbeActivity::class.java.getDeclaredField("sample").apply {isAccessible=true}.get(a) as ShortArray).size)
        assertNull(WatchProbeActivity::class.java.getDeclaredField("request").apply {isAccessible=true}.get(a))
    }

}
