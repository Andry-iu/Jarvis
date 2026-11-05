package it.jarviswake

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.os.SystemClock
import android.view.View
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
import java.time.Duration
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34,36])
class GoogleVoiceRuntimeTest {
    private val app:android.app.Application get()=RuntimeEnvironment.getApplication()
    private val token=UUID.randomUUID().toString()
    private val cycleField=VoiceCycle::class.java.getDeclaredField("state").apply {isAccessible=true}
    private val googleField=VoiceCycle::class.java.getDeclaredField("googleMode").apply {isAccessible=true}
    private lateinit var oldCycle:VoiceCycleState
    private lateinit var capture:FakeCapture
    private lateinit var web:FakeWeb
    private lateinit var api:FakeApi
    private lateinit var activity:GoogleVoiceActivity
    private var controller:org.robolectric.android.controller.ActivityController<GoogleVoiceActivity>?=null
    private val url="https://chatgpt.com/g/g-p-test/c/12345678-1234-4234-8234-123456789abc"
    private val config get()=GoogleVoiceConfig("fake-access-token-unit-test-stt","fake-access-token-unit-test-tts",System.currentTimeMillis()+1800000,"test-project",url)
    private class FakeCapture:GoogleCapture {
        override var released=true
        var deferRelease=false
        var starts=0;var stops=0
        var end:((ShortArray?,String?)->Unit)?=null
        var chunk:((ShortArray,Int)->Unit)?=null
        override fun start(onSpeech:(ShortArray)->Unit,onEnd:(ShortArray?,String?)->Unit,onChunk:(ShortArray,Int)->Unit) {
            starts++;released=false;end=onEnd;chunk=onChunk;onSpeech(ShortArray(320){1000})
        }
        override fun stop() {stops++;if(!deferRelease)released=true;end?.invoke(null,null);end=null}
        fun phrase() {repeat(49){chunk?.invoke(ShortArray(320){1000},320)};released=true;val done=end;end=null;done?.invoke(ShortArray(16000){1000},null)}
    }
    private class FakeApi:GoogleSpeechApi {
        var transcriptions=0;var syntheses=0;var failStt=false;var failTts=false;var cancelled=false
        var streams=0;var audioQueued=0
        var synthGate:java.util.concurrent.CountDownLatch?=null
        private var sttEnd:(()->Unit)?=null
        override fun openRecognition(onResult:(String?,Int)->Unit):GoogleRecognitionStream {
            streams++
            var queued=0;var finished=false
            fun complete(text:String?) {if(!finished){finished=true;onResult(text,queued)}}
            sttEnd={complete(null)}
            return object:GoogleRecognitionStream {
                override fun audio(samples:ShortArray,size:Int){if(!finished){queued+=size;audioQueued=queued}}
                override fun finish(){transcriptions++;complete(if(failStt)null else "TESTO_PRIVATO_DETTATO")}
                override fun cancel(){complete(null)}
            }
        }
        override fun synthesize(text:String):ByteArray {syntheses++;synthGate?.await(2,java.util.concurrent.TimeUnit.SECONDS);if(failTts)error("fake");return ByteArray(480)}
        override fun cancel(){cancelled=true;synthGate?.countDown();sttEnd?.invoke()}
    }
    private class FakeWeb(c:Context,val target:String):WebView(c) {
        var sends=0;var writes=0;var probes=0;var draft=false
        var readyAfterProbes=1
        private var stage=0
        override fun getUrl():String=target
        override fun evaluateJavascript(script:String,callback:ValueCallback<String>?) {
            if(script.startsWith("delete window.")) {callback?.onReceiveValue("null");return}
            val phase=if(script.contains("\"action\":\"probe\"")){probes++;if(draft) "draft" else if(probes<readyAfterProbes) "ambiguous_editor" else "ready"}
            else when(stage++){0->{writes++;"written"};1->{sends++;"dispatched"};else->"observed"}
            callback?.onReceiveValue(JSONObject.quote(JSONObject().put("phase",phase).toString()))
        }
    }
    @Before fun setup() {
        oldCycle=cycleField.get(null) as VoiceCycleState
        cycleField.set(null,VoiceCycleState().apply {begin(token,SystemClock.elapsedRealtime());openWeb(token,SystemClock.elapsedRealtime(),true,true,false,false)})
        googleField.setBoolean(null,true)
        app.getSharedPreferences("bridge_link",0).edit().putBoolean("enabled",true).commit()
        GoogleVoiceSettings.enable(app,true)
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        capture=FakeCapture();api=FakeApi()
        GoogleVoiceDependencies.configuration={config};GoogleVoiceDependencies.capture={capture};GoogleVoiceDependencies.api={api}
        GoogleVoiceDependencies.playback={object:GooglePlayback {
            override var released=true
            override fun stop(){released=true}
            override fun play(pcm:ByteArray,allowed:()->Boolean,started:()->Unit):Boolean {if(!allowed())return false;started();pcm.fill(0);return true}
        }}
        GoogleVoiceDependencies.web={FakeWeb(it,url).also {w->web=w}}
        // Remove the RAM-only retained page from the previous test; no production data exist in tests.
        GoogleVoiceActivity::class.java.getDeclaredField("retainedWeb").apply {isAccessible=true;get(null)?.let {(it as WebView).destroy()};set(null,null)}
    }
    private fun launch() {
        controller=Robolectric.buildActivity(GoogleVoiceActivity::class.java,Intent(app,GoogleVoiceActivity::class.java).putExtra(VoiceCycle.EXTRA_ID,token)).setup()
        activity=controller!!.get()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1300))
    }
    private fun pump(check:()->Boolean) {
        val until=System.nanoTime()+3_000_000_000L
        while(!check() && System.nanoTime()<until) {shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100));Thread.sleep(5)}
        assertTrue("Simulated callback did not complete",check())
    }
    private fun <T:View> view(name:String):T {
        @Suppress("UNCHECKED_CAST") return GoogleVoiceActivity::class.java.getDeclaredField(name).apply {isAccessible=true}.get(activity) as T
    }
    @After fun cleanup() {
        if(::capture.isInitialized) {capture.deferRelease=false;capture.released=true}
        if(::activity.isInitialized) GoogleVoiceActivity.closeForCycle(token)
        controller?.pause()?.stop()?.destroy();GoogleVoiceDependencies.reset()
        cycleField.set(null,oldCycle);googleField.setBoolean(null,false);GoogleVoiceSettings.enable(app,false)
        app.getSharedPreferences("bridge_link",0).edit().clear().commit()
        shadowOf(Looper.getMainLooper()).idle()
    }
    @Test fun googleUsesOneFinalTextSubmissionAndKeepsCurrentModeWhileNextChoiceChanges() {
        launch();assertEquals(1,capture.starts);assertEquals(1,api.streams);assertEquals(320,api.audioQueued)
        assertEquals(0,api.transcriptions);assertEquals(0,web.writes) // stream starts at onset, before utterance end
        GoogleVoiceSettings.enable(app,false);assertTrue(VoiceCycle.googleMode)
        capture.phrase();pump{web.sends==1};shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        assertEquals(1,api.transcriptions);assertEquals(1,web.writes);assertEquals(1,web.sends)
        assertEquals(16000,api.audioQueued)
        assertEquals(1,view<CoverTranscriptView>("transcriptView").textCount)
        val state=GoogleVoiceActivity::class.java.getDeclaredField("state").apply{isAccessible=true}.get(activity) as GoogleTurnState
        assertEquals("waiting_reply",state.phase)
        assertFalse(state.acceptReply("old-session",state.turn!!))
        assertTrue(state.acceptReply(token,state.turn!!));assertFalse(state.acceptReply(token,state.turn!!))
    }
    @Test fun googleSttErrorDoesNotWriteSendOrPersistPrivateContents() {
        api.failStt=true;launch();capture.phrase();pump{api.transcriptions==1}
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));assertEquals(0,web.writes);assertEquals(0,web.sends)
        assertEquals(0,view<CoverTranscriptView>("transcriptView").textCount)
        assertFalse(State.getAttempt(app).contains("TESTO_PRIVATO"))
    }
    @Test fun slowWorkWarningKeepsWaitingWithoutPollingOrRepeatingTheInput() {
        launch();capture.phrase();pump{web.sends==1};shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(92))
        val state=GoogleVoiceActivity::class.java.getDeclaredField("state").apply{isAccessible=true}.get(activity) as GoogleTurnState
        assertEquals("waiting_reply",state.phase);assertEquals(1,web.sends);assertEquals(0,api.syntheses)
        assertTrue(State.getAttempt(app).contains("ATTENDO ANCORA"))
    }
    @Test fun pluginReplySynthesizesAndPlaysOnceWithCorrelatedProgress() {
        launch();capture.phrase();pump{web.sends==1};shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        val state=GoogleVoiceActivity::class.java.getDeclaredField("state").apply{isAccessible=true}.get(activity) as GoogleTurnState
        val command=JSONObject().put("params",JSONObject().put("session_id",token).put("turn_id",state.turn).put("text","TESTO_PRIVATO_RISPOSTA"))
        val outcome=java.util.concurrent.atomic.AtomicReference<JSONObject>()
        val progress=java.util.Collections.synchronizedList(mutableListOf<String>())
        Thread({outcome.set(GoogleVoiceActivity.speak(command,SystemClock.elapsedRealtime()+25000,{true}){progress.add(it)})}).apply{isDaemon=true;start()}
        pump{outcome.get()!=null}
        assertEquals("speech_completed",outcome.get().getString("code"));assertTrue(outcome.get().getBoolean("completed"))
        assertEquals(listOf("accepted","synthesized","playback_started"),progress.toList());assertEquals(1,api.syntheses)
        assertEquals(2,view<CoverTranscriptView>("transcriptView").textCount)
        val duplicate=java.util.concurrent.atomic.AtomicReference<JSONObject>()
        Thread({duplicate.set(GoogleVoiceActivity.speak(command,SystemClock.elapsedRealtime()+25000,{true}){})}).apply{isDaemon=true;start()}
        pump{duplicate.get()!=null};assertEquals("stale_voice_turn",duplicate.get().getString("code"));assertEquals(1,api.syntheses)
    }
    @Test fun googleTtsFailureReportsAcceptedButNeverClaimsPlayback() {
        api.failTts=true;launch();capture.phrase();pump{web.sends==1};shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        val state=GoogleVoiceActivity::class.java.getDeclaredField("state").apply{isAccessible=true}.get(activity) as GoogleTurnState
        val command=JSONObject().put("params",JSONObject().put("session_id",token).put("turn_id",state.turn).put("text","Risposta simulata"))
        val outcome=java.util.concurrent.atomic.AtomicReference<JSONObject>()
        Thread({outcome.set(GoogleVoiceActivity.speak(command,SystemClock.elapsedRealtime()+25000,{true}){})}).apply{isDaemon=true;start()}
        pump{outcome.get()!=null};assertEquals("synthesis_failed",outcome.get().getString("code"))
        assertTrue(outcome.get().getBoolean("accepted"));assertFalse(outcome.get().getBoolean("synthesized"));assertFalse(outcome.get().getBoolean("playback_started"))
    }
    @Test fun existingDraftPreventsCaptureAndEveryTextMutation() {
        GoogleVoiceDependencies.web={FakeWeb(it,url).also{w->w.draft=true;web=w}}
        launch();assertEquals(0,capture.starts);assertEquals(0,web.writes);assertEquals(0,web.sends)
    }
    @Test fun delayedReactComposerIsProbedLocallyBeforeOpeningTheMicrophone() {
        GoogleVoiceDependencies.web={FakeWeb(it,url).also{w->w.readyAfterProbes=3;web=w}}
        launch();shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(700))
        assertEquals(3,web.probes);assertEquals(1,capture.starts);assertEquals(1,api.streams);assertEquals(0,web.sends)
    }
    @Test fun muteControlsOwnMicrophoneAndUnmuteStartsOnlyAfterItsRelease() {
        launch()
        val toggle=GoogleVoiceActivity::class.java.getDeclaredMethod("toggleMute").apply{isAccessible=true}
        toggle.invoke(activity);assertTrue(VoiceCycle.microphoneMuted);assertTrue(capture.released)
        shadowOf(Looper.getMainLooper()).idle();toggle.invoke(activity)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1200));assertFalse(VoiceCycle.microphoneMuted);assertEquals(2,capture.starts)
        assertEquals(0,web.sends)
    }
    @Test fun modeCannotRestartEitherAudioSystemUntilClosedCaptureAcknowledgesRelease() {
        launch();capture.deferRelease=true;GoogleVoiceActivity.closeForCycle(token)
        assertFalse(VoiceCycle.active);assertTrue(GoogleVoiceActivity.audioBusy())
        GoogleVoiceSettings.enable(app,false);VoiceCycle.start(app);assertFalse(VoiceCycle.active)
        capture.released=true;VoiceCycle.start(app);assertTrue(VoiceCycle.active);assertFalse(VoiceCycle.googleMode)
        VoiceCycle.cancel(app,VoiceCycle.id!!,"test cleanup")
    }
    @Test fun closingDuringPendingSynthesisReturnsCancelledAndLateCallbacksCannotRestoreCover() {
        api.synthGate=java.util.concurrent.CountDownLatch(1)
        launch();capture.phrase();pump{web.sends==1};shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        val state=GoogleVoiceActivity::class.java.getDeclaredField("state").apply{isAccessible=true}.get(activity) as GoogleTurnState
        val command=JSONObject().put("params",JSONObject().put("session_id",token).put("turn_id",state.turn).put("text","Risposta simulata"))
        val outcome=java.util.concurrent.atomic.AtomicReference<JSONObject>()
        Thread({outcome.set(GoogleVoiceActivity.speak(command,SystemClock.elapsedRealtime()+25000,{true}){})}).apply{isDaemon=true;start()}
        pump{api.syntheses==1};val hud=view<HolographicOverlayView>("hud");GoogleVoiceActivity.closeForCycle(token)
        pump{outcome.get()!=null};assertEquals("cancelled",outcome.get().getString("code"));assertNull(hud.parent)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3));assertNull(hud.parent);assertFalse(VoiceCycle.active)
    }
    @Test fun closureDetachesCoverBeforeLateMicrophoneCallbackAndClearsText() {
        launch();val oldEnd=capture.end!!;val hud=view<HolographicOverlayView>("hud");val texts=view<CoverTranscriptView>("transcriptView")
        texts.append("Dettato","TESTO_PRIVATO");assertEquals(1,texts.textCount)
        GoogleVoiceActivity.closeForCycle(token)
        assertNull(hud.parent);assertEquals(0,texts.textCount);assertTrue(capture.released);assertFalse(VoiceCycle.active)
        oldEnd(ShortArray(16000){1000},null);shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(4))
        assertEquals(0,api.transcriptions);assertNull(hud.parent);assertEquals(0,web.sends)
    }
    @Test fun leftHidesOnlyTheCoverAndNeverStopsOrRecreatesTheSession() {
        launch();val hud=view<HolographicOverlayView>("hud")
        GoogleVoiceActivity::class.java.getDeclaredMethod("revealChat").apply{isAccessible=true}.invoke(activity)
        assertNull(hud.parent);assertEquals(0,capture.stops);assertTrue(VoiceCycle.active)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));assertNull(hud.parent)
    }
    @Test fun mapsCoverTransfersOnlyOnExplicitLaunchAndExitRemovesTheExternalWindowWithoutVoiceActions() {
        launch()
        val serviceController=Robolectric.buildService(VoiceAccessibilityService::class.java).create()
        val service=serviceController.get()
        val field=VoiceAccessibilityService::class.java.getDeclaredField("instance").apply{isAccessible=true}
        val previous=field.get(null);field.set(null,java.lang.ref.WeakReference(service))
        try {
            val cover=view<android.widget.FrameLayout>("cover");val hud=view<HolographicOverlayView>("hud")
            val oldParent=cover.parent
            val maps=Intent(Intent.ACTION_VIEW,android.net.Uri.parse("google.navigation:q=Simulated")).setPackage("com.google.android.apps.maps")
            shadowOf(app.packageManager).addResolveInfoForIntent(maps,android.content.pm.ResolveInfo().apply {
                activityInfo=android.content.pm.ActivityInfo().apply {packageName="com.google.android.apps.maps";name="com.google.android.apps.maps.TestLauncher";exported=true}
            })
            BridgeActions.launch(app,maps,keepCover=true)
            assertNotSame(oldParent,cover.parent);assertTrue(VoiceCycle.active);assertEquals(0,capture.stops)
            assertNotNull(GoogleVoiceActivity::class.java.getDeclaredField("overlayManager").apply{isAccessible=true}.get(activity))
            BridgeActions.launch(app,maps,keepCover=false)
            assertNull(cover.parent);assertNull(hud.parent);assertTrue(VoiceCycle.active);assertEquals(0,capture.stops)
            GoogleVoiceActivity.prepareAppLaunch(true);assertNull(cover.parent) // hidden means no automatic resurrection
            GoogleVoiceActivity.stopForTelephone(app)
            assertFalse(VoiceCycle.active);assertTrue(capture.released);assertNull(hud.parent)
        } finally {field.set(null,previous);serviceController.destroy()}
    }
    @Test fun coverTextToggleIsPersistentButMessagesAreSessionOnlyAndSingleTouchesStayPassive() {
        val panel=CoverTranscriptView(app);val before=panel.shown
        assertEquals(!before,panel.toggle());panel.append("Jarvis","TESTO_PRIVATO_RISPOSTA")
        val another=CoverTranscriptView(app);assertEquals(panel.shown,another.shown);assertEquals(0,another.textCount)
        val event=android.view.MotionEvent.obtain(0,1,android.view.MotionEvent.ACTION_DOWN,20f,20f,0)
        assertFalse(panel.scrollText(event));event.recycle();panel.clearText();assertEquals(0,panel.textCount)
    }
    @Test fun disabledGoogleKeepsLegacyLaunchAndDoesNotConstructGoogleProviders() {
        cycleField.set(null,VoiceCycleState());googleField.setBoolean(null,false);GoogleVoiceSettings.enable(app,false)
        var calls=0;GoogleVoiceDependencies.api={calls++;api}
        VoiceCycle.start(app);assertFalse(VoiceCycle.googleMode);assertEquals(0,calls)
        assertEquals(LaunchActivity::class.java.name,shadowOf(app).nextStartedActivity.component!!.className)
        VoiceCycle.cancel(app,VoiceCycle.id!!,"test cleanup")
    }
}
