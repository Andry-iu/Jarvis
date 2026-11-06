package it.jarviswake

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.ValueCallback
import android.webkit.WebView
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
import java.lang.ref.WeakReference
import java.time.Duration

/** Executes the Activity's real SET/timeout callbacks; no ChatGPT page, audio or network. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34,36])
class WebMicrophoneCallbackTest {
    private val cycle=VoiceCycle::class.java.getDeclaredField("state").apply {isAccessible=true}
    private val muted=VoiceCycle::class.java.getDeclaredField("microphoneMuted").apply {isAccessible=true}
    private val active=WebChatActivity::class.java.getDeclaredField("active").apply {isAccessible=true}
    private lateinit var oldCycle:Any;private var oldMuted=false;private var oldActive:Any?=null
    private lateinit var a:WebChatActivity;private lateinit var web:DelayedWeb
    private val token="4ac0e8f0-19b7-413c-aed8-5e49089e5f18"
    private class DelayedWeb(c:Context):WebView(c) {
        val targets=mutableListOf<Boolean>();val callbacks=mutableListOf<ValueCallback<String>>()
        override fun evaluateJavascript(script:String,callback:ValueCallback<String>?) {
            targets.add(script.contains("media.setMuted(true)"));callbacks.add(callback!!)
        }
        override fun postDelayed(action:Runnable,delayMillis:Long)=Handler(Looper.getMainLooper()).postDelayed(action,delayMillis)
        fun confirm(index:Int) {callbacks[index].onReceiveValue(JSONObject.quote(JSONObject().put("available",true).put("muted",targets[index]).toString()))}
    }
    @Before fun setup() {
        oldCycle=checkNotNull(cycle.get(null));oldMuted=VoiceCycle.microphoneMuted;oldActive=active.get(null)
        cycle.set(null,VoiceCycleState().apply {restoreNativeExit(token)});muted.setBoolean(null,true)
        a=Robolectric.buildActivity(WebChatActivity::class.java).get()
        web=DelayedWeb(RuntimeEnvironment.getApplication())
        fun field(name:String,value:Any) {WebChatActivity::class.java.getDeclaredField(name).apply {isAccessible=true}.set(a,value)}
        field("cycleToken",token);field("webView",web);active.set(null,WeakReference(a))
    }
    @After fun reset() {active.set(null,oldActive);cycle.set(null,oldCycle);muted.setBoolean(null,oldMuted);web.destroy()}
    @Test fun releaseBeforeUnmuteConfirmationQueuesOneMuteAndLateUnmuteCannotOverwriteIt() {
        WebChatActivity.setMicrophoneMuted(token,false);WebChatActivity.setMicrophoneMuted(token,true)
        assertEquals(listOf(false),web.targets);assertTrue(VoiceCycle.microphoneMuted)
        web.confirm(0);assertEquals(listOf(false,true),web.targets);assertFalse(VoiceCycle.microphoneMuted)
        web.confirm(1);assertTrue(VoiceCycle.microphoneMuted)
        web.confirm(0);assertTrue(VoiceCycle.microphoneMuted);assertEquals(2,web.targets.size)
    }
    @Test fun missingUnmuteCallbackRestoresViaMuteSetAfterTimeoutAndIgnoresTheLateCallback() {
        WebChatActivity.setMicrophoneMuted(token,false);WebChatActivity.setMicrophoneMuted(token,true)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(2001))
        assertEquals(listOf(false,true),web.targets)
        web.confirm(0);assertTrue(VoiceCycle.microphoneMuted)
        web.confirm(1);assertTrue(VoiceCycle.microphoneMuted);assertEquals(2,web.targets.size)
    }
    @Test fun documentChangeAndClosedCycleDiscardOldCallbacksWithoutNewActionsOrStateConfirmation() {
        WebChatActivity.setMicrophoneMuted(token,false);WebChatActivity.setMicrophoneMuted(token,true)
        WebChatActivity::class.java.getDeclaredField("documentGeneration").apply {isAccessible=true;setInt(a,1)}
        web.confirm(0);assertEquals(listOf(false),web.targets);assertTrue(VoiceCycle.microphoneMuted)
        WebChatActivity.setMicrophoneMuted(token,false)
        cycle.set(null,VoiceCycleState())
        web.confirm(1);shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
        assertEquals(listOf(false,false),web.targets);assertFalse(VoiceCycle.active);assertTrue(VoiceCycle.microphoneMuted)
    }
}
