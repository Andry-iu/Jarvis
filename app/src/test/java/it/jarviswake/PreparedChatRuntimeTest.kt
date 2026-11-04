package it.jarviswake

import android.app.Application
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Looper
import android.webkit.PermissionRequest
import android.webkit.WebView
import java.time.Duration
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34,36])
class PreparedChatRuntimeTest {
    private val project="https://chatgpt.com/g/g-p-abc123/project"
    private val url="https://chatgpt.com/c/12345678-1234-4234-8234-123456789abc"
    private val reply="Ho letto le istruzioni del progetto e sono pronto a proseguire con la conversazione."
    private val ledgerField=PreparedChat::class.java.getDeclaredField("ledger").apply {isAccessible=true}
    private val viewField=PreparedChat::class.java.getDeclaredField("view").apply {isAccessible=true}
    private val cycleField=VoiceCycle::class.java.getDeclaredField("state").apply {isAccessible=true}
    private val previousLedger=ledgerField.get(null)
    private val previousCycle=cycleField.get(null)
    private lateinit var app:Application
    private lateinit var ledger:PreparedChatLedger
    @Before fun setup() {
        app=RuntimeEnvironment.getApplication()
        cycleField.set(null,VoiceCycleState())
        ledger=PreparedChatLedger(null) {};ledgerField.set(null,ledger)
        ChatGptProjectSettings.save(app,project);NativeChatSettings.enable(app,true)
        app.getSharedPreferences("prepared_chat_settings",0).edit().putBoolean("enabled",true).apply()
        shadowOf(app).clearNextStartedActivities()
    }
    @After fun clean() {
        PreparedChat.stop(app);ledgerField.set(null,previousLedger);cycleField.set(null,previousCycle)
    }
    private fun start():WebView {
        PreparedChat.maintain(app)
        return viewField.get(null) as WebView
    }
    private fun probe(web:WebView) {
        shadowOf(web).webViewClient.onPageFinished(web,project)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
    }
    private fun ready()=JSONObject.quote(JSONObject().put("phase","ready").put("url",url).put("reply",reply).toString())
    @Test fun rendererIsDetachedWithConfiguredViewportNeverOpensActivityAndDeniesBothMediaResources() {
        val web=start()
        assertNull(web.parent);assertFalse(web.isAttachedToWindow);// Robolectric's WebView provider has no setFrame delegate; real renderer sizing needs a device.
        assertTrue(web.layoutParams.width>0);assertTrue(web.layoutParams.height>0)
        assertEquals(project,shadowOf(web).lastLoadedUrl);assertNull(shadowOf(app).nextStartedActivity)
        var denied=false;var granted=false
        shadowOf(web).webChromeClient.onPermissionRequest(object:PermissionRequest() {
            override fun getOrigin()=Uri.parse("https://chatgpt.com")
            override fun getResources()=arrayOf(RESOURCE_AUDIO_CAPTURE,RESOURCE_VIDEO_CAPTURE)
            override fun grant(resources:Array<out String>) {granted=true}
            override fun deny() {denied=true}
        })
        assertTrue(denied);assertFalse(granted)
        assertTrue(web.settings.mediaPlaybackRequiresUserGesture)
    }
    @Test fun actualWebViewCallbackCapturesLinkAndDestroysRendererWithoutLaunchingNativeApp() {
        val web=start();probe(web)
        assertTrue("script="+shadowOf(web).lastEvaluatedJavascript+" record="+ledger.record?.phase,shadowOf(web).lastEvaluatedJavascript.contains("__jarvisPreparation"))
        shadowOf(web).lastEvaluatedJavascriptCallback.onReceiveValue(ready())
        assertEquals(PreparationPhase.READY,ledger.record!!.phase);assertEquals(url,PreparedChat.savedLink(app))
        assertNull(viewField.get(null));assertNull(shadowOf(app).nextStartedActivity)
        PreparedChat.maintain(app);assertNull(viewField.get(null))
    }
    @Test fun navigationAfterProbeCancelsPreparationAndLateCallbackCannotRestoreItOrSendAgain() {
        val web=start();probe(web);val callback=shadowOf(web).lastEvaluatedJavascriptCallback
        val attempt=ledger.record!!.attempt
        shadowOf(web).webViewClient.onPageStarted(web,project,null)
        assertNull(viewField.get(null));assertEquals(PreparationPhase.FAILED,ledger.record!!.phase)
        callback.onReceiveValue(ready());PreparedChat.maintain(app)
        assertEquals(attempt,ledger.record!!.attempt);assertNull(PreparedChat.savedLink(app));assertNull(viewField.get(null))
        assertNull(shadowOf(app).nextStartedActivity)
    }
    @Test fun inactiveSettingDoesNotCreateWebViewOrSendMessages() {
        app.getSharedPreferences("prepared_chat_settings",0).edit().putBoolean("enabled",false).apply()
        PreparedChat.maintain(app);assertNull(viewField.get(null));assertNull(ledger.record)
    }
    @Test fun activeVoiceDoesNotStartNewPreparationButAnExistingTextPreparationMayFinish() {
        cycleField.set(null,VoiceCycleState().apply {begin("active",0)})
        PreparedChat.maintain(app);assertNull(viewField.get(null));assertNull(ledger.record)
        cycleField.set(null,VoiceCycleState());val web=start();probe(web)
        cycleField.set(null,VoiceCycleState().apply {begin("active",0)})
        shadowOf(web).lastEvaluatedJavascriptCallback.onReceiveValue(ready())
        assertEquals(PreparationPhase.READY,ledger.record!!.phase);assertNull(shadowOf(app).nextStartedActivity)
    }
    @Test fun loginResetDisablesAutomaticPreparationAndInvalidatesAnOldReadyLink() {
        val web=start();probe(web);shadowOf(web).lastEvaluatedJavascriptCallback.onReceiveValue(ready())
        PreparedChat.resetForLogin(app);assertFalse(PreparedChat.enabled(app));assertNull(PreparedChat.savedLink(app))
        PreparedChat.maintain(app);assertNull(viewField.get(null));assertNull(shadowOf(app).nextStartedActivity)
    }
    @Test fun failedHandoffIsMarkedAfterExitAndCannotStartAnotherHiddenChat() {
        val a=ledger.begin(project,NativeChatSettings.project(app),System.currentTimeMillis())!!
        assertTrue(ledger.complete(a.attempt,url,reply,System.currentTimeMillis()))
        assertNotNull(ledger.claim("c",project,NativeChatSettings.project(app),System.currentTimeMillis()))
        PreparedChat.cycleEnded(app,"c",false)
        assertEquals(PreparationPhase.CLAIMED,ledger.record!!.phase)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(PreparationPhase.FAILED,ledger.record!!.phase)
        PreparedChat.maintain(app);assertNull(viewField.get(null));assertNull(shadowOf(app).nextStartedActivity)
    }
    @Test fun missingRendererCallbackStillTimesOutAndCannotBeReplayedByLateReady() {
        val web=start();probe(web);val callback=shadowOf(web).lastEvaluatedJavascriptCallback
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(PreparedChatPolicy.PREPARE_TIMEOUT_MS))
        assertNull(viewField.get(null));assertEquals(PreparationPhase.FAILED,ledger.record!!.phase)
        callback.onReceiveValue(ready());PreparedChat.maintain(app)
        assertNull(viewField.get(null));assertNull(PreparedChat.savedLink(app));assertNull(shadowOf(app).nextStartedActivity)
    }
    @Test fun changingToWebModeStopsAnInFlightPreparationAndDiscardsLateCallbacks() {
        val web=start();probe(web);val callback=shadowOf(web).lastEvaluatedJavascriptCallback
        NativeChatSettings.enable(app,false)
        assertNull(viewField.get(null));assertEquals(PreparationPhase.FAILED,ledger.record!!.phase)
        callback.onReceiveValue(ready());PreparedChat.maintain(app)
        assertNull(PreparedChat.savedLink(app));assertNull(viewField.get(null));assertNull(shadowOf(app).nextStartedActivity)
    }
    @Test fun nativeLinkIntentTargetsOnlyChatGptAndKeepsTheCapturedConversationUri() {
        val expected=Intent(Intent.ACTION_VIEW,Uri.parse(url)).setPackage(NativeChatSettings.PACKAGE)
        shadowOf(app.packageManager).addResolveInfoForIntent(expected,ResolveInfo().apply {activityInfo=ActivityInfo().apply {
            packageName=NativeChatSettings.PACKAGE;name="ChatActivity"
        }})
        NativeChatSettings.openConversation(app,url)
        val actual=shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW,actual.action);assertEquals(url,actual.data.toString())
        assertEquals(NativeChatSettings.PACKAGE,actual.`package`)
        assertNotEquals(0,actual.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    @Test fun unsupportedOrInvalidNativeLinksNeverFallBackToBrowserOrProjectShortcut() {
        assertThrows(IllegalStateException::class.java) {NativeChatSettings.openConversation(app,url)}
        assertThrows(IllegalStateException::class.java) {NativeChatSettings.openConversation(app,"https://example.com/c/other")}
        assertNull(shadowOf(app).nextStartedActivity)
    }
}
