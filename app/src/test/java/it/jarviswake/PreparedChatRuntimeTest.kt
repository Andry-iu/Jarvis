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
    private fun ready():String {
        ledger.record?.let {ledger.markSubmitted(it.attempt,System.currentTimeMillis())}
        return JSONObject.quote(JSONObject().put("phase","ready").put("url",url).put("submitted",true).toString())
    }

    @Test fun emptyOpeningTextInvalidatesTheOldSlotAndNeverCreatesAHiddenRendererUntilReenabled() {
        val web=start();probe(web);shadowOf(web).lastEvaluatedJavascriptCallback.onReceiveValue(ready())
        assertNotNull(PreparedChat.savedLink(app))
        assertTrue(OpeningMessageSettings.save(app," \n "))
        shadowOf(Looper.getMainLooper()).idle();PreparedChat.maintain(app)
        assertNull(viewField.get(null));assertNull(ledger.record);assertNull(PreparedChat.savedLink(app))
        assertNull(PreparedChat.claim(app,"disabled"));assertTrue(PreparedChat.status(app).contains("sospesa"))
        PreparedChat.coverClosed(app,"closed-disabled");shadowOf(Looper.getMainLooper()).idle()
        assertNull(viewField.get(null));assertNull(shadowOf(app).nextStartedActivity)
        assertTrue(OpeningMessageSettings.save(app,"Nuovo messaggio"));shadowOf(Looper.getMainLooper()).idle()
        assertNotNull(viewField.get(null));assertEquals("Nuovo messaggio",ledger.record!!.openingText)
    }
    @Test fun customOpeningTextIsSnapshottedIntoThePreparationAndQuotedSafelyInJavascript() {
        val custom="Prima riga \"Jarvis\"\nSeconda riga `test`"
        assertTrue(OpeningMessageSettings.save(app,custom))
        val web=start();probe(web)
        assertEquals(custom,ledger.record!!.openingText)
        assertTrue(shadowOf(web).lastEvaluatedJavascript.contains("const message = "+JSONObject.quote(custom)))
        assertFalse(shadowOf(web).lastEvaluatedJavascript.contains("__JARVIS_MESSAGE_JSON__"))
        assertNull(shadowOf(app).nextStartedActivity)
    }
    @Test fun changingTheMessageDestroysAnInflightViewAndItsLateResultCannotReplaceTheNewAttempt() {
        val web=start();probe(web);val callback=shadowOf(web).lastEvaluatedJavascriptCallback
        val old=ledger.record!!.attempt
        assertTrue(OpeningMessageSettings.save(app,"Nuova preparazione"))
        assertNull(viewField.get(null));assertNull(PreparedChat.savedLink(app));assertNull(PreparedChat.claim(app,"old"))
        callback.onReceiveValue(JSONObject.quote(JSONObject().put("phase","ready").put("url",url).put("submitted",true).toString()))
        shadowOf(Looper.getMainLooper()).idle()
        assertNotEquals(old,ledger.record!!.attempt);assertEquals("Nuova preparazione",ledger.record!!.openingText)
        assertEquals(PreparationPhase.STARTED,ledger.record!!.phase);assertNotNull(viewField.get(null))
    }
    @Test fun readyLinkSurvivesAnUnchangedSaveButIsInvalidatedByANewText() {
        val web=start();probe(web);shadowOf(web).lastEvaluatedJavascriptCallback.onReceiveValue(ready())
        val old=ledger.record!!.attempt
        assertTrue(OpeningMessageSettings.save(app,OpeningMessagePolicy.TEXT))
        assertEquals(old,ledger.record!!.attempt);assertEquals(url,PreparedChat.savedLink(app))
        assertTrue(OpeningMessageSettings.save(app,"Nuove istruzioni"))
        assertNull(PreparedChat.savedLink(app));assertNull(PreparedChat.claim(app,"old"))
        shadowOf(Looper.getMainLooper()).idle();assertNotEquals(old,ledger.record!!.attempt)
        assertEquals("Nuove istruzioni",ledger.record!!.openingText)
    }
    @Test fun encryptedSlotSerializationPreservesCustomTextAndMigratesOldSlotsWithTheDefault() {
        val custom=PreparedConversation("test",project,NativeChatSettings.project(app),100,openingText="Riga1\nRiga2")
        val encode=PreparedChat::class.java.getDeclaredMethod("encode",PreparedConversation::class.java).apply {isAccessible=true}
        val decode=PreparedChat::class.java.getDeclaredMethod("decode",JSONObject::class.java).apply {isAccessible=true}
        val json=encode.invoke(PreparedChat,custom) as JSONObject
        assertEquals(custom,decode.invoke(PreparedChat,json))
        json.remove("openingText")
        assertEquals(OpeningMessagePolicy.TEXT,(decode.invoke(PreparedChat,json) as PreparedConversation).openingText)
    }
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
    @Test fun readyWithoutObservedSubmissionIsRejectedDespiteAValidUrl() {
        val web=start();probe(web)
        val outcome=JSONObject().put("phase","ready").put("url",url).put("reply",reply)
            .put("echoObserved",false).put("urlObserved",true)
        shadowOf(web).lastEvaluatedJavascriptCallback.onReceiveValue(JSONObject.quote(outcome.toString()))
        assertEquals(PreparationPhase.FAILED,ledger.record!!.phase);assertNull(PreparedChat.savedLink(app))
        assertNull(shadowOf(app).nextStartedActivity)
    }
    @Test fun verificationDiagnosticsContainOnlyBoundedCountsBooleansAndKnownCodes() {
        val web=start();probe(web)
        val outcome=JSONObject().put("phase","waiting_user").put("users",9999999).put("rawUsers",-200)
            .put("source",url).put("reason","PRIVATE_RESPONSE").put("reply","PRIVATE_RESPONSE").put("url",url)
        shadowOf(web).lastEvaluatedJavascriptCallback.onReceiveValue(JSONObject.quote(outcome.toString()))
        val done=java.util.concurrent.CountDownLatch(1)
        DiagnosticLog.afterPendingWrites {done.countDown()}
        assertTrue(done.await(5,java.util.concurrent.TimeUnit.SECONDS))
        val log=java.io.File(app.filesDir,"diagnostics/"+NativeRuntimeSnapshot.FILE_NAME).readText()
        assertTrue(log.contains("phase=waiting_user users=1000 rawUsers=-1 source=none"))
        assertFalse(log.contains(url));assertFalse(log.contains("PRIVATE_RESPONSE"))
        assertEquals(PreparationPhase.STARTED,ledger.record!!.phase)
    }
    @Test fun submittedCallbackThenUrlCaptureClosesTheRendererWithoutAnyResponseEvidence() {
        val web=start();probe(web)
        shadowOf(web).lastEvaluatedJavascriptCallback.onReceiveValue(JSONObject.quote(JSONObject().put("phase","submitted").toString()))
        assertTrue(ledger.record!!.submittedAt>=ledger.record!!.createdAt)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
        val outcome=JSONObject().put("phase","ready").put("url",url).put("submitted",true)
        shadowOf(web).lastEvaluatedJavascriptCallback.onReceiveValue(JSONObject.quote(outcome.toString()))
        assertEquals(PreparationPhase.READY,ledger.record!!.phase);assertTrue(ledger.record!!.linkOnly)
        assertEquals(url,PreparedChat.savedLink(app));assertNull(viewField.get(null));assertNull(shadowOf(app).nextStartedActivity)
    }
    @Test fun decoratedProjectLinkIsSavedAfterRealSubmittedCallbackAndCanBeClaimedOnce() {
        val id="0123456789abcdef0123456789abcdef"
        ChatGptProjectSettings.save(app,"https://chatgpt.com/g/g-p-$id/project")
        val web=start();probe(web)
        shadowOf(web).lastEvaluatedJavascriptCallback.onReceiveValue(JSONObject.quote(JSONObject().put("phase","submitted").toString()))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
        val decorated=url.replace("/c/","/g/g-p-$id-project-name/c/")
        shadowOf(web).lastEvaluatedJavascriptCallback.onReceiveValue(JSONObject.quote(JSONObject()
            .put("phase","ready").put("url",decorated).put("submitted",true).toString()))
        assertEquals(decorated,PreparedChat.savedLink(app));assertNull(viewField.get(null))
        assertEquals(decorated,PreparedChat.claim(app,"one")!!.url);assertNull(PreparedChat.claim(app,"two"))
        assertNull(shadowOf(app).nextStartedActivity)
    }
    @Test fun rejectedLinkReportsFixedReasonWithoutLeakingTheUrlAndStorageFailureIsSeparate() {
        val web=start();probe(web)
        ledger.markSubmitted(ledger.record!!.attempt,System.currentTimeMillis())
        val privateUrl="$url?private=PRIVATE_VALUE"
        shadowOf(web).lastEvaluatedJavascriptCallback.onReceiveValue(JSONObject.quote(JSONObject()
            .put("phase","ready").put("url",privateUrl).put("submitted",true).toString()))
        assertEquals("link_parameters",ledger.record!!.error);assertNull(PreparedChat.savedLink(app));assertNull(viewField.get(null))
        val done=java.util.concurrent.CountDownLatch(1);DiagnosticLog.afterPendingWrites {done.countDown()}
        assertTrue(done.await(5,java.util.concurrent.TimeUnit.SECONDS))
        val log=java.io.File(app.filesDir,"diagnostics/"+NativeRuntimeSnapshot.FILE_NAME).readText()
        assertTrue(log.contains("phase=link_received"));assertTrue(log.contains("phase=link_parameters"))
        assertFalse(log.contains("PRIVATE_VALUE"));assertFalse(log.contains(url))
        ledger=PreparedChatLedger(null) {};ledgerField.set(null,ledger)
        val next=start();probe(next);ledger.markSubmitted(ledger.record!!.attempt,System.currentTimeMillis())
        ledger=PreparedChatLedger(ledger.record) {error("PRIVATE_STORAGE_VALUE")};ledgerField.set(null,ledger)
        shadowOf(next).lastEvaluatedJavascriptCallback.onReceiveValue(JSONObject.quote(JSONObject()
            .put("phase","ready").put("url",url).put("submitted",true).toString()))
        assertNull(viewField.get(null));assertNull(PreparedChat.savedLink(app))
        val saved=java.util.concurrent.CountDownLatch(1);DiagnosticLog.afterPendingWrites {saved.countDown()}
        assertTrue(saved.await(5,java.util.concurrent.TimeUnit.SECONDS))
        val storedLog=java.io.File(app.filesDir,"diagnostics/"+NativeRuntimeSnapshot.FILE_NAME).readText()
        assertTrue(storedLog.contains("phase=storage_error"));assertFalse(storedLog.contains("PRIVATE_STORAGE_VALUE"))
    }
    @Test fun closingCoverQueuesOnePreparationDuringTheSameVoiceCycleWithoutOpeningActivity() {
        val a=ledger.begin(project,NativeChatSettings.project(app),System.currentTimeMillis())!!
        assertTrue(ledger.complete(a.attempt,url,reply,System.currentTimeMillis()))
        assertNotNull(ledger.claim("active",project,NativeChatSettings.project(app),System.currentTimeMillis()))
        cycleField.set(null,VoiceCycleState().apply {restoreNativeExit("active")})
        PreparedChat.coverClosed(app,"active");assertNull(viewField.get(null))
        shadowOf(Looper.getMainLooper()).idle()
        val attempt=ledger.record!!.attempt
        assertNotEquals(a.attempt,attempt);assertNotNull(viewField.get(null))
        assertTrue(VoiceCycle.active);assertFalse(VoiceCycle.touchGate.canDispatch);assertNull(shadowOf(app).nextStartedActivity)
        PreparedChat.coverClosed(app,"active");shadowOf(Looper.getMainLooper()).idle()
        assertEquals(attempt,ledger.record!!.attempt)
        PreparedChat.stop(app);PreparedChat.maintain(app)
        assertNull(viewField.get(null));assertEquals(PreparationPhase.FAILED,ledger.record!!.phase)
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
    @Test fun projectConversationIntentUsesTheDirectConversationRouteAndPreservesTheStoredLink() {
        val id="0123456789abcdef0123456789abcdef"
        val configured="https://chatgpt.com/g/g-p-$id/project"
        assertTrue(ChatGptProjectSettings.save(app,configured))
        val captured=url.replace("/c/","/g/g-p-$id-project-name/c/")
        val now=System.currentTimeMillis();val a=ledger.begin(configured,NativeChatSettings.project(app),now)!!
        assertTrue(ledger.markSubmitted(a.attempt,now));assertTrue(ledger.captureLink(a.attempt,captured,now))
        val expected=Intent(Intent.ACTION_VIEW,Uri.parse(url)).setPackage(NativeChatSettings.PACKAGE)
        shadowOf(app.packageManager).addResolveInfoForIntent(expected,ResolveInfo().apply {activityInfo=ActivityInfo().apply {
            packageName=NativeChatSettings.PACKAGE;name="ChatActivity"
        }})
        NativeChatSettings.openConversation(app,captured+"?model=example-model")
        val actual=shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW,actual.action);assertEquals(url,actual.data.toString())
        assertEquals(NativeChatSettings.PACKAGE,actual.`package`)
        assertNotEquals(0,actual.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
        assertEquals(captured,PreparedChat.savedLink(app));assertEquals(PreparationPhase.READY,ledger.record!!.phase)
        assertThrows(IllegalStateException::class.java) {
            NativeChatSettings.openConversation(app,captured.replace(id,"1123456789abcdef0123456789abcdef"))
        }
        assertNull(shadowOf(app).nextStartedActivity)
    }
    @Test fun unsupportedOrInvalidNativeLinksNeverFallBackToBrowserOrProjectShortcut() {
        assertThrows(IllegalStateException::class.java) {NativeChatSettings.openConversation(app,url)}
        assertThrows(IllegalStateException::class.java) {NativeChatSettings.openConversation(app,"https://example.com/c/other")}
        assertNull(shadowOf(app).nextStartedActivity)
    }
}
