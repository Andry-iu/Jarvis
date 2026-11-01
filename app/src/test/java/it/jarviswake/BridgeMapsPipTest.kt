package it.jarviswake

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34,36])
class BridgeMapsPipTest {
    private fun window(id:Int,owner:String,layer:Int,pip:Boolean,bounds:Rect):Pair<AccessibilityWindowInfo,AccessibilityNodeInfo> {
        val node=AccessibilityNodeInfo.obtain().apply {packageName=owner;isVisibleToUser=true}
        val w=AccessibilityWindowInfo.obtain()
        shadowOf(w).apply {setId(id);setRoot(node);setType(AccessibilityWindowInfo.TYPE_APPLICATION);setLayer(layer);setPictureInPicture(pip);setBoundsInScreen(bounds)}
        return w to node
    }
    @Test fun aSingleExposedMoveMustBeFollowedByChangedGeometryAndAnUncoveredStop() {
        val controller=Robolectric.buildService(VoiceAccessibilityService::class.java).create();val service=controller.get()
        val (chat,_)=window(1,NativeChatSettings.PACKAGE,1,false,Rect(0,0,1200,2400))
        val (maps,node)=window(2,"com.google.android.apps.maps",5,true,Rect(900,2000,1200,2400))
        node.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_MOVE_WINDOW)
        var moves=0
        shadowOf(node).setOnPerformActionListener {action,args->
            assertEquals(AccessibilityNodeInfo.AccessibilityAction.ACTION_MOVE_WINDOW.id,action)
            assertTrue(args.containsKey(AccessibilityNodeInfo.ACTION_ARGUMENT_MOVE_WINDOW_X));moves++;true
        }
        val p=BridgeMapsPip();val windows=listOf(chat,maps)
        assertFalse(p.prepare(service,"test",windows));assertEquals(1,moves)
        assertFalse(p.prepare(service,"test",windows));assertEquals(1,moves)
        shadowOf(maps).setBoundsInScreen(Rect(20,100,320,500))
        assertTrue(p.prepare(service,"test",windows));assertEquals(1,moves)
        val stop=AccessibilityNodeInfo.obtain().apply {setBoundsInScreen(Rect(950,2100,1100,2300))}
        assertTrue(p.stopUncovered(windows,stop))
        stop.setBoundsInScreen(Rect(50,150,100,200));assertFalse(p.stopUncovered(windows,stop))
        controller.destroy()
    }
    @Test fun unsupportedOrForeignPipNeverReceivesActions() {
        val c=Robolectric.buildService(VoiceAccessibilityService::class.java).create();val s=c.get()
        val (chat,_)=window(1,NativeChatSettings.PACKAGE,1,false,Rect(0,0,1200,2400))
        val (pip,node)=window(2,"other.app",5,true,Rect(800,1600,1200,2100))
        node.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_MOVE_WINDOW)
        assertFalse(BridgeMapsPip().prepare(s,"test",listOf(chat,pip)))
        assertTrue(shadowOf(node).performedActions.isEmpty())
        node.packageName="com.google.android.apps.maps";node.removeAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_MOVE_WINDOW)
        assertFalse(BridgeMapsPip().prepare(s,"test",listOf(chat,pip)))
        assertTrue(shadowOf(node).performedActions.isEmpty());c.destroy()
    }
}
