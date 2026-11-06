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
    private var oldMicrophoneMuted=false
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
        var autoSpeech=true
        var onset:((ShortArray)->Unit)?=null
        var end:((ShortArray?,String?)->Unit)?=null
        var chunk:((ShortArray,Int)->Unit)?=null
        override fun start(onSpeech:(ShortArray)->Unit,onEnd:(ShortArray?,String?)->Unit,onChunk:(ShortArray,Int)->Unit) {
            starts++;released=false;end=onEnd;chunk=onChunk;onset=onSpeech;if(starts==1 && autoSpeech)onSpeech(ShortArray(320){1000})
        }
        override fun stop() {stops++;if(!deferRelease)released=true;end?.invoke(null,null);end=null}
        fun speak() {onset?.invoke(ShortArray(320){2000})}
        fun phrase() {repeat(49){chunk?.invoke(ShortArray(320){1000},320)};released=true;val done=end;end=null;done?.invoke(ShortArray(16000){1000},null)}
    }
    private class FakeApi:GoogleSpeechApi {
        var transcriptions=0;var syntheses=0;var failStt=false;var failTts=false;var cancelled=false
        var streams=0;var audioQueued=0;var transcript="TESTO_PRIVATO_DETTATO"
        var synthGate:java.util.concurrent.CountDownLatch?=null
        private var sttEnd:(()->Unit)?=null
        override fun openRecognition(onResult:(String?,Int)->Unit):GoogleRecognitionStream {
            streams++
            var queued=0;var finished=false
            fun complete(text:String?) {if(!finished){finished=true;onResult(text,queued)}}
            sttEnd={complete(null)}
            return object:GoogleRecognitionStream {
                override fun audio(samples:ShortArray,size:Int){if(!finished){queued+=size;audioQueued=queued}}
                override fun finish(){transcriptions++;complete(if(failStt)null else transcript)}
                override fun cancel(){complete(null)}
            }
        }
        override fun synthesize(text:String):ByteArray {syntheses++;synthGate?.await(2,java.util.concurrent.TimeUnit.SECONDS);if(failTts)error("fake");return ByteArray(480)}
        override fun cancel(){cancelled=true;synthGate?.countDown();sttEnd?.invoke()}
    }
    private class FakeWeb(c:Context,val target:String):WebView(c) {
        var currentUrl=target.substringBefore("/c/")+"/project"
        var loads=0
        val submittedScripts=mutableListOf<String>()
        var observedNewUrl=target
        override fun loadUrl(url:String) {loads++;currentUrl=url;post {webViewClient.onPageFinished(this,url)}}
        var sends=0;var writes=0;var probes=0;var draft=false
        var sendChecks=0
        var writeReadyAfterCalls=1
        var holdWrite=false;var changeAfterWrite=false
        var waitsPerSend=0;var observationWaits=0;var receiptProbes=0;var holdObservation=false
        var heldObservation:ValueCallback<String>?=null
        var held:ValueCallback<String>?=null
        var readyAfterProbes=1
        private var stage=0
        override fun getUrl():String=currentUrl
        override fun evaluateJavascript(script:String,callback:ValueCallback<String>?) {
            if(script.contains("delete window.")) {callback?.onReceiveValue("null");return}
            if(script.contains("\"action\":\"restart\"")) {
                callback?.onReceiveValue(JSONObject.quote(JSONObject().put("phase",if(draft) "draft" else "restart_ready").toString()));return
            }
            if(!script.contains("\"action\":\"probe\"") && ++sendChecks<writeReadyAfterCalls) {
                callback?.onReceiveValue(JSONObject.quote(JSONObject().put("phase","ambiguous_editor").toString()));return
            }
            if(changeAfterWrite && writes>0 && !script.contains("\"action\":\"probe\"")) {
                callback?.onReceiveValue(JSONObject.quote(JSONObject().put("phase","draft_changed").put("diagnostics",JSONObject()
                    .put("sameEditor",false).put("sameForm",true).put("textMatches",true).put("writeVerified",true)).toString()));return
            }
            if(!script.contains("\"action\":\"probe\"") && stage%3==2) {
                receiptProbes++
                if(holdObservation) {heldObservation=callback;return}
                if(observationWaits>0) {observationWaits--;callback?.onReceiveValue(JSONObject.quote(JSONObject().put("phase","waiting_observation").toString()));return}
            }
            val phase=if(script.contains("\"action\":\"probe\"")){probes++;if(draft) "draft" else if(probes<readyAfterProbes) "ambiguous_editor" else "ready"}
            else when(stage++ % 3){0->{writes++;submittedScripts.add(script);"written"};1->{sends++;observationWaits=waitsPerSend;currentUrl=observedNewUrl;"dispatched"};else->"observed"}
            if(holdWrite && phase=="written") {held=callback;return}
            callback?.onReceiveValue(JSONObject.quote(JSONObject().put("phase",phase).apply {if(phase=="observed") put("conversation",currentUrl)}.toString()))
        }
    }
    @Before fun setup() {
        oldCycle=cycleField.get(null) as VoiceCycleState
        cycleField.set(null,VoiceCycleState().apply {begin(token,SystemClock.elapsedRealtime());openWeb(token,SystemClock.elapsedRealtime(),true,true,false,false)})
        oldMicrophoneMuted=VoiceCycle.microphoneMuted
        VoiceCycle.confirmMicrophoneMuted(token,false)
        googleField.setBoolean(null,true)
        app.getSharedPreferences("bridge_link",0).edit().putBoolean("enabled",true).commit()
        GoogleVoiceSettings.enable(app,true)
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        capture=FakeCapture();api=FakeApi()
        GoogleVoiceDependencies.project={"https://chatgpt.com/g/g-p-test/project"}
        GoogleVoiceDependencies.opening={""};GoogleVoiceDependencies.greeting={""}
        GoogleVoiceDependencies.configuration={config};GoogleVoiceDependencies.capture={capture.apply {autoSpeech=web.submittedScripts.none {it.contains("Rispondi con jarviswake_speak")}}};GoogleVoiceDependencies.api={api}
        GoogleVoiceDependencies.echoCapture={object:GoogleCapture {
            override val released=true
            override fun stop(){}
            override fun start(onSpeech:(ShortArray)->Unit,onEnd:(ShortArray?,String?)->Unit,onChunk:(ShortArray,Int)->Unit) {onEnd(null,"echo_non_disponibile")}
        }}
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
        controller?.pause()?.stop()?.destroy();GoogleVoiceDependencies.reset();DiagnosticScreenshots.reset()
        VoiceCycle.confirmMicrophoneMuted(token,oldMicrophoneMuted)
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
    @Test fun heldMicrophoneReleaseFinalizesStreamOnceAndKeepsListeningMuted() {
        launch()
        val finish=GoogleVoiceActivity::class.java.getDeclaredMethod("finishMicrophoneHold").apply {isAccessible=true}
        finish.invoke(activity)
        pump{web.sends==1}
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        assertTrue(VoiceCycle.microphoneMuted);assertTrue(capture.released)
        assertEquals(1,api.transcriptions);assertEquals(1,web.sends);assertEquals(1,capture.starts)
    }
    @Test fun heldMicrophoneDoesNotClaimMuteOrFinishStreamBeforeRecorderRelease() {
        launch();capture.deferRelease=true
        VoiceCycle.confirmMicrophoneMuted(token,false)
        val finish=GoogleVoiceActivity::class.java.getDeclaredMethod("finishMicrophoneHold").apply {isAccessible=true}
        finish.invoke(activity);shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300))
        assertFalse(VoiceCycle.microphoneMuted);assertEquals(0,api.transcriptions);assertEquals(0,web.sends)
        capture.released=true
        pump{web.sends==1}
        assertTrue(VoiceCycle.microphoneMuted);assertEquals(1,api.transcriptions)
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
    @Test fun coverTextsStartHiddenForEveryCycleEvenWithOldPreferenceAndTogglesKeepSessionHistory() {
        app.getSharedPreferences("google_voice_mode",0).edit().putBoolean("show_text",true).commit()
        val panel=CoverTranscriptView(app);assertFalse(panel.shown)
        panel.append("Dettato","TESTO_PRIVATO_DETTATO");assertFalse(panel.shown);assertEquals(1,panel.textCount)
        assertTrue(panel.toggle());panel.append("Jarvis","TESTO_PRIVATO_RISPOSTA")
        assertFalse(panel.toggle());assertEquals(2,panel.textCount);assertTrue(panel.toggle())
        val another=CoverTranscriptView(app);assertFalse(another.shown);assertEquals(0,another.textCount)
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
    private fun backendSession(gate: java.util.concurrent.CountDownLatch? = null, fail: Boolean = false): java.util.concurrent.atomic.AtomicInteger {
        val calls = java.util.concurrent.atomic.AtomicInteger()
        GoogleVoiceDependencies.configuration = { config.copy(sttToken="",ttsToken="",expiresAt=0,authMode="backend") }
        GoogleVoiceDependencies.credentials = { object : GoogleCredentialSession {
            override fun resolve(config:GoogleVoiceConfig,minimumMs:Long):GoogleVoiceConfig {
                calls.incrementAndGet(); gate?.await(3,java.util.concurrent.TimeUnit.SECONDS)
                if(fail) error("simulated broker unavailable")
                return config.copy(sttToken="renewed-fake-stt-access-token",ttsToken="renewed-fake-tts-access-token",expiresAt=System.currentTimeMillis()+3600000)
            }
            override fun cancel() {gate?.countDown()}
        } }
        return calls
    }
    @Test fun backendRenewalCompletesBeforeCaptureAndLongWorkCanReceiveSpeechAfterOriginalExpiry() {
        val calls=backendSession();launch();pump{capture.starts==1}
        assertEquals(1,calls.get());capture.phrase();pump{web.sends==1}
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3700))
        val state=GoogleVoiceActivity::class.java.getDeclaredField("state").apply{isAccessible=true}.get(activity) as GoogleTurnState
        assertEquals("waiting_reply",state.phase);assertEquals(1,web.sends)
        val command=JSONObject().put("params",JSONObject().put("session_id",token).put("turn_id",state.turn).put("text","Risposta dopo lavoro lungo"))
        val outcome=java.util.concurrent.atomic.AtomicReference<JSONObject>()
        Thread({outcome.set(GoogleVoiceActivity.speak(command,SystemClock.elapsedRealtime()+25000,{true}){})}).apply{isDaemon=true;start()}
        pump{outcome.get()!=null};assertTrue(outcome.get().getBoolean("completed"));assertTrue(calls.get()>=2);assertEquals(1,api.syntheses)
    }
    @Test fun failedBackendRenewalNeverStartsCaptureOrSendsChatMessages() {
        val calls=backendSession(fail=true);launch();pump{calls.get()==1}
        pump{State.getAttempt(app).contains("AUTENTICAZIONE GOOGLE NON DISPONIBILE")}
        assertEquals(0,capture.starts);assertEquals(0,api.streams);assertEquals(0,web.sends)
    }
    @Test fun closeDuringBackendRenewalRemovesCoverAndLateGrantCannotOpenAudioOrRestoreIt() {
        val gate=java.util.concurrent.CountDownLatch(1);val calls=backendSession(gate)
        launch();pump{calls.get()==1};assertEquals(0,capture.starts)
        val hud=view<HolographicOverlayView>("hud");GoogleVoiceActivity.closeForCycle(token)
        assertNull(hud.parent);gate.countDown();Thread.sleep(30)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(4))
        assertEquals(0,capture.starts);assertEquals(0,api.streams);assertEquals(0,web.sends);assertNull(hud.parent);assertFalse(VoiceCycle.active)
    }

    @Test fun backendAuthFailureBeforeReplyDoesNotCountAnyTtsCharactersOrAttemptSynthesis() {
        val calls=backendSession()
        val original=GoogleVoiceDependencies.credentials
        GoogleVoiceDependencies.credentials={ context ->
            val delegate=original(context)
            object:GoogleCredentialSession {
                override fun resolve(config:GoogleVoiceConfig,minimumMs:Long):GoogleVoiceConfig {
                    if(minimumMs==90000L) error("simulated revoked credential")
                    return delegate.resolve(config,minimumMs)
                }
                override fun cancel()=delegate.cancel()
            }
        }
        launch();pump{capture.starts==1};assertEquals(1,calls.get())
        capture.phrase();pump{web.sends==1};shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        val before=app.getSharedPreferences("google_voice_usage",0).getLong("tts_characters",0)
        val state=GoogleVoiceActivity::class.java.getDeclaredField("state").apply{isAccessible=true}.get(activity) as GoogleTurnState
        val command=JSONObject().put("params",JSONObject().put("session_id",token).put("turn_id",state.turn).put("text","Risposta con credenziale revocata"))
        val outcome=java.util.concurrent.atomic.AtomicReference<JSONObject>()
        Thread({outcome.set(GoogleVoiceActivity.speak(command,SystemClock.elapsedRealtime()+25000,{true}){})}).apply{isDaemon=true;start()}
        pump{outcome.get()!=null};assertEquals("authentication_failed",outcome.get().getString("code"));assertEquals(0,api.syntheses)
        assertEquals(before,app.getSharedPreferences("google_voice_usage",0).getLong("tts_characters",0));assertFalse(outcome.get().getBoolean("synthesized"))
    }

    @Test fun eachWakeUsesConfiguredProjectAndBothSavedStartupMessagesExactlyOnce() {
        GoogleVoiceDependencies.opening={"PRE_CONFIGURATO"};GoogleVoiceDependencies.greeting={"POST_CONFIGURATO"}
        // Start from the preceding chat as on a real subsequent wake: inspect before navigating.
        GoogleVoiceDependencies.web={FakeWeb(it,url).also{w->w.currentUrl=url;web=w}}
        launch();pump{web.sends==2}
        assertEquals(1,web.loads)
        assertTrue(web.submittedScripts[0].contains("PRE_CONFIGURATO"));assertFalse(web.submittedScripts[0].contains("Rispondi con jarviswake_speak"))
        assertTrue(web.submittedScripts[1].contains("POST_CONFIGURATO"));assertTrue(web.submittedScripts[1].contains("Rispondi con jarviswake_speak"))
        assertEquals(url,web.currentUrl);assertEquals(0,api.streams)
        assertEquals(0,view<CoverTranscriptView>("transcriptView").textCount);assertFalse(view<CoverTranscriptView>("transcriptView").shown)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3));assertEquals(2,web.sends)
    }
    @Test fun startupComposerChangeLogsPostStageAndStopsBeforeSendWithoutAddingCoverText() {
        GoogleVoiceDependencies.greeting={"POST_PRIVATO_DIAGNOSTICO"}
        GoogleVoiceDependencies.web={FakeWeb(it,url).also{w->w.changeAfterWrite=true;web=w}}
        launch();pump{State.getAttempt(app).contains("EDITOR CAMBIATO")}
        assertEquals(1,web.writes);assertEquals(0,web.sends);assertEquals(0,api.streams)
        assertEquals(0,view<CoverTranscriptView>("transcriptView").textCount)
        val done=java.util.concurrent.CountDownLatch(1);DiagnosticLog.afterPendingWrites{done.countDown()};assertTrue(done.await(3,java.util.concurrent.TimeUnit.SECONDS))
        val log=java.io.File(app.filesDir,"diagnostics/google-voice-0.log").readText()
        assertTrue(log.contains("stage=post code=draft_changed"));assertTrue(log.contains("sameEditor"));assertFalse(log.contains("POST_PRIVATO_DIAGNOSTICO"))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5));assertEquals(1,web.writes);assertEquals(0,web.sends)
        GoogleVoiceActivity.closeForCycle(token);assertNull(view<HolographicOverlayView>("hud").parent)
    }
    @Test fun emptyBeforeStillSendsAfterInNewChatAndEmptyAfterStartsCaptureAfterBefore() {
        GoogleVoiceDependencies.opening={"PRE_SOLO"};launch();pump{capture.starts==1}
        assertEquals(1,web.sends);assertEquals(1,web.writes);assertTrue(web.submittedScripts.single().contains("PRE_SOLO"))
    }
    @Test fun emptyBeforeAndConfiguredAfterCreatesOneStartupTurnWithoutOpeningMessage() {
        GoogleVoiceDependencies.greeting={"POST_SOLO"};launch();pump{web.sends==1}
        assertEquals(1,web.writes);assertTrue(web.submittedScripts.single().contains("POST_SOLO"))
    }
    @Test fun preAndPostAreFrozenWhenCycleStartsAndNoFuturePreparationIsRequired() {
        var pre="PRE_FISSATO";var post="POST_FISSATO"
        GoogleVoiceDependencies.opening={pre};GoogleVoiceDependencies.greeting={post}
        GoogleVoiceDependencies.web={FakeWeb(it,url).also{w->w.readyAfterProbes=3;web=w}}
        launch();pre="PRE_CAMBIATO";post="POST_CAMBIATO";pump{web.sends==2}
        assertTrue(web.submittedScripts[0].contains("PRE_FISSATO"));assertTrue(web.submittedScripts[1].contains("POST_FISSATO"))
        assertFalse(web.submittedScripts.any{it.contains("CAMBIATO")})
    }
    @Test fun draftOnPreviousChatPreventsNavigationAndAllStartupMessages() {
        GoogleVoiceDependencies.opening={"PRE"};GoogleVoiceDependencies.greeting={"POST"}
        GoogleVoiceDependencies.web={FakeWeb(it,url).also{w->w.currentUrl=url;w.draft=true;web=w}}
        launch();assertEquals(0,web.loads);assertEquals(url,web.currentUrl);assertEquals(0,web.writes);assertEquals(0,web.sends);assertEquals(0,capture.starts)
    }
    @Test fun handoffUsesExistingAuthenticatedWebViewAndStartsNewChatWithoutReplacingItsDraft() {
        val page=FakeWeb(app,url).also{it.currentUrl=url}
        val parent=android.widget.FrameLayout(app).apply{addView(page)}
        val field=ChatGptPreloader::class.java.getDeclaredField("web").apply{isAccessible=true}
        val previous=field.get(null);field.set(null,java.lang.ref.WeakReference(page))
        GoogleVoiceDependencies.web={error("Must reuse the live WebView")}
        try {web=page;launch();pump{capture.starts==1};assertEquals(1,page.loads);assertEquals(0,parent.childCount);assertTrue(page.parent!=null)}
        finally {field.set(null,previous)}
    }

    @Test fun closingDuringOpeningInvalidatesLateWriteCallbackWithoutSendOrCoverReturn() {
        GoogleVoiceDependencies.opening={"PRE_DA_ANNULLARE"}
        GoogleVoiceDependencies.web={FakeWeb(it,url).also{w->w.holdWrite=true;web=w}}
        launch();assertEquals(1,web.writes);assertEquals(0,web.sends)
        val late=web.held!!;val hud=view<HolographicOverlayView>("hud")
        GoogleVoiceActivity.closeForCycle(token);assertNull(hud.parent)
        late.onReceiveValue(JSONObject.quote(JSONObject().put("phase","written").toString()))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
        assertEquals(0,web.sends);assertEquals(0,capture.starts);assertNull(hud.parent)
    }
    @Test fun nextWakeReopensProjectAsNewChatAndDoesNotReusePreviousConversation() {
        launch();capture.phrase();pump{web.sends==1};shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        val firstPage=web;assertEquals(url,web.currentUrl)
        GoogleVoiceActivity.closeForCycle(token);controller!!.pause().stop().destroy()
        val nextToken=UUID.randomUUID().toString()
        cycleField.set(null,VoiceCycleState().apply{begin(nextToken,SystemClock.elapsedRealtime());openWeb(nextToken,SystemClock.elapsedRealtime(),true,true,false,false)})
        googleField.setBoolean(null,true);GoogleVoiceDependencies.web={error("Must reuse authenticated profile page")}
        controller=Robolectric.buildActivity(GoogleVoiceActivity::class.java,Intent(app,GoogleVoiceActivity::class.java).putExtra(VoiceCycle.EXTRA_ID,nextToken)).setup()
        activity=controller!!.get();pump{capture.starts==2}
        assertSame(firstPage,web);assertEquals("https://chatgpt.com/g/g-p-test/project",web.currentUrl);assertEquals(1,web.loads);assertEquals(1,web.sends)
        GoogleVoiceActivity.closeForCycle(nextToken)
    }

    @Test fun delayedReceiptAfterPreAndPostDoesNotRetryAndBindsChatBeforeListening() {
        GoogleVoiceDependencies.opening={"PRE_UNICO"};GoogleVoiceDependencies.greeting={"POST_UNICO"}
        GoogleVoiceDependencies.web={FakeWeb(it,url).also{w->w.waitsPerSend=4;web=w}}
        launch();pump{capture.starts>=1}
        assertEquals(2,web.writes);assertEquals(2,web.sends);assertTrue(web.receiptProbes>=10)
        assertEquals(0,api.streams);assertFalse(State.getAttempt(app).contains("NON CONFERMATO"))
        val field=GoogleVoiceActivity::class.java.getDeclaredField("conversation").apply{isAccessible=true}
        assertEquals(url,field.get(activity))
    }
    @Test fun observedReplyWithInvalidDestinationNeverOpensTheFirstMicrophone() {
        GoogleVoiceDependencies.greeting={"POST_UNICO"}
        GoogleVoiceDependencies.web={FakeWeb(it,url).also{w->w.observedNewUrl="https://example.invalid/c/12345678-1234-4234-8234-123456789abc";web=w}}
        launch();pump{State.getAttempt(app).contains("DESTINAZIONE NON CONFERMATA")}
        assertEquals(1,web.writes);assertEquals(1,web.sends);assertEquals(0,capture.starts);assertEquals(0,api.streams)
    }
    @Test fun closingDuringReceiptIgnoresLateObservationAndNeverRestoresCoverOrAudio() {
        GoogleVoiceDependencies.greeting={"POST_UNICO"}
        GoogleVoiceDependencies.web={FakeWeb(it,url).also{w->w.holdObservation=true;web=w}}
        launch();pump{web.heldObservation!=null};val late=web.heldObservation!!
        assertEquals(1,web.writes);assertEquals(1,web.sends);GoogleVoiceActivity.closeForCycle(token)
        late.onReceiveValue(JSONObject.quote(JSONObject().put("phase","observed").put("conversation",url).toString()))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
        assertEquals(0,capture.starts);assertEquals(1,web.sends);assertNull(view<HolographicOverlayView>("hud").parent)
    }
    @Test fun receiptDuringEarlyPluginSynthesisBindsChatWithoutOverwritingReplyPhase() {
        val gate=java.util.concurrent.CountDownLatch(1);api.synthGate=gate
        GoogleVoiceDependencies.greeting={"POST_UNICO"}
        GoogleVoiceDependencies.web={FakeWeb(it,url).also{w->w.holdObservation=true;web=w}}
        launch();pump{web.heldObservation!=null};val late=web.heldObservation!!
        val field=GoogleVoiceActivity::class.java.getDeclaredField("state").apply{isAccessible=true}
        val turn=(field.get(activity) as GoogleTurnState).turn
        val command=JSONObject().put("params",JSONObject().put("session_id",token).put("turn_id",turn).put("text","Risposta anticipata"))
        val outcome=java.util.concurrent.atomic.AtomicReference<JSONObject>()
        Thread({outcome.set(GoogleVoiceActivity.speak(command,SystemClock.elapsedRealtime()+25000,{true}){})}).apply{isDaemon=true;start()}
        pump{api.syntheses==1};late.onReceiveValue(JSONObject.quote(JSONObject().put("phase","observed").put("conversation",url).toString()))
        shadowOf(Looper.getMainLooper()).idle();assertEquals("synthesizing",(field.get(activity) as GoogleTurnState).phase)
        assertEquals(0,capture.starts);gate.countDown();pump{outcome.get()!=null};pump{capture.starts==1}
        assertTrue(outcome.get().getBoolean("completed"));assertEquals(1,web.sends);assertEquals(1,web.writes)
    }
    @Test fun earlySpeechCompletionWaitsForReceiptBeforeFirstMicrophoneCapture() {
        GoogleVoiceDependencies.greeting={"POST_UNICO"}
        GoogleVoiceDependencies.web={FakeWeb(it,url).also{w->w.holdObservation=true;web=w}}
        launch();pump{web.heldObservation!=null};val late=web.heldObservation!!
        val state=GoogleVoiceActivity::class.java.getDeclaredField("state").apply{isAccessible=true}.get(activity) as GoogleTurnState
        val command=JSONObject().put("params",JSONObject().put("session_id",token).put("turn_id",state.turn).put("text","Risposta prima del riscontro"))
        val outcome=java.util.concurrent.atomic.AtomicReference<JSONObject>()
        Thread({outcome.set(GoogleVoiceActivity.speak(command,SystemClock.elapsedRealtime()+25000,{true}){})}).apply{isDaemon=true;start()}
        pump{outcome.get()!=null};shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        assertEquals(0,capture.starts);assertTrue(State.getAttempt(app).contains("ATTESA RISCONTRO CHAT"))
        late.onReceiveValue(JSONObject.quote(JSONObject().put("phase","observed").put("conversation",url).toString()))
        pump{capture.starts==1};assertEquals(1,web.writes);assertEquals(1,web.sends)
    }
    @Test fun nextWakeAfterUnobservedSendReturnsToProjectWithoutReplayingOldText() {
        val direct="https://chatgpt.com/c/12345678-1234-4234-8234-123456789abc"
        GoogleVoiceDependencies.greeting={"POST_VECCHIO"}
        GoogleVoiceDependencies.web={FakeWeb(it,url).also{w->w.holdObservation=true;w.observedNewUrl=direct;web=w}}
        launch();pump{web.heldObservation!=null};val page=web;val late=web.heldObservation!!
        assertEquals(direct,web.currentUrl);assertEquals(1,web.sends)
        GoogleVoiceActivity.closeForCycle(token);controller!!.pause().stop().destroy()
        GoogleVoiceDependencies.greeting={""};GoogleVoiceDependencies.web={error("Must retain authenticated page")}
        val nextToken=UUID.randomUUID().toString()
        cycleField.set(null,VoiceCycleState().apply{begin(nextToken,SystemClock.elapsedRealtime());openWeb(nextToken,SystemClock.elapsedRealtime(),true,true,false,false)})
        googleField.setBoolean(null,true)
        controller=Robolectric.buildActivity(GoogleVoiceActivity::class.java,Intent(app,GoogleVoiceActivity::class.java).putExtra(VoiceCycle.EXTRA_ID,nextToken)).setup()
        activity=controller!!.get();pump{capture.starts==1}
        assertSame(page,web);assertEquals("https://chatgpt.com/g/g-p-test/project",web.currentUrl);assertEquals(1,web.loads)
        assertEquals(1,web.writes);assertEquals(1,web.sends)
        late.onReceiveValue(JSONObject.quote(JSONObject().put("phase","observed").put("conversation",direct).toString()))
        shadowOf(Looper.getMainLooper()).idle();assertEquals(1,web.sends)
        GoogleVoiceActivity.closeForCycle(nextToken)
    }

    @Test fun startupWaitsForComposerBeforeFirstWriteAndNeverRetriesAMutatedMessage() {
        GoogleVoiceDependencies.opening={"PRE_DOPO_REACT"}
        GoogleVoiceDependencies.web={FakeWeb(it,url).also{w->w.writeReadyAfterCalls=3;web=w}}
        launch();pump{capture.starts==1};assertEquals(1,web.writes);assertEquals(1,web.sends)
        assertTrue(web.sendChecks>=5)
    }

    @Test fun newSpeechWhileWaitingCreatesNewTurnInSameChatAndRejectsOldReplyWithoutAButton() {
        launch();capture.phrase();pump{web.sends==1}
        val stateField=GoogleVoiceActivity::class.java.getDeclaredField("state").apply{isAccessible=true}
        val oldTurn=(stateField.get(activity) as GoogleTurnState).turn
        pump{capture.starts>=2};capture.speak();shadowOf(Looper.getMainLooper()).idle();capture.phrase();pump{web.sends==2}
        val newState=stateField.get(activity) as GoogleTurnState
        assertNotEquals(oldTurn,newState.turn);assertEquals(url,web.currentUrl);assertEquals(0,web.loads)
        assertFalse(newState.acceptReply(token,oldTurn!!));assertEquals(2,web.writes)
    }
    @Test fun screenshotCapturesBeforeCoverRemovalAndDoesNotOpenExportAfterLateClosure() {
        launch();val cover=view<android.widget.FrameLayout>("cover")
        assertEquals(0,activity.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE)
        var callback:((android.graphics.Bitmap?,String)->Unit)?=null
        DiagnosticScreenshots.windowCapture={_,done -> assertEquals(View.VISIBLE,cover.visibility);callback=done}
        GoogleVoiceActivity::class.java.getDeclaredMethod("exportDiagnostics").apply{isAccessible=true}.invoke(activity)
        assertNotNull(callback);GoogleVoiceActivity.closeForCycle(token)
        callback!!(android.graphics.Bitmap.createBitmap(4,4,android.graphics.Bitmap.Config.ARGB_8888),"captured")
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
        assertNull(view<HolographicOverlayView>("hud").parent)
        val started=shadowOf(app).nextStartedActivity
        assertTrue(started==null || started.component?.className!=DiagnosticExportActivity::class.java.name)
    }
    @Test fun chatToggleIsAnInvisibleCoverTargetAndNoDictationButtonIsRendered() {
        launch();val target=view<View>("textToggle");assertFalse(target is android.widget.Button)
        assertNull(target.background);val before=view<CoverTranscriptView>("transcriptView").shown
        target.performClick();assertEquals(!before,view<CoverTranscriptView>("transcriptView").shown)
        fun buttons(v:View):List<android.widget.Button> = (if(v is android.widget.Button)listOf(v) else emptyList())+
            (if(v is android.view.ViewGroup)(0 until v.childCount).flatMap{buttons(v.getChildAt(it))} else emptyList())
        assertTrue(buttons(view("cover")).isEmpty())
    }

    private class EchoRecorder:GoogleCapture {
        override var released=true
        override val echoState get()=if(released) "unavailable" else "ready"
        override val routeType=android.media.AudioDeviceInfo.TYPE_BUILTIN_MIC
        var enabled=false;var onset:((ShortArray)->Unit)?=null;var end:((ShortArray?,String?)->Unit)?=null
        override fun allowEchoSpeech(allowed:Boolean){enabled=allowed}
        override fun stop(){released=true;end?.invoke(null,null);end=null}
        override fun start(onSpeech:(ShortArray)->Unit,onEnd:(ShortArray?,String?)->Unit,onChunk:(ShortArray,Int)->Unit){released=false;onset=onSpeech;end=onEnd}
        fun speak(){assertTrue(enabled);onset!!(ShortArray(320){2000})}
        fun phrase(){released=true;val done=end;end=null;done!!(ShortArray(320){2000},null)}
    }
    private class BlockingPlayback:GooglePlayback {
        @Volatile override var released=true
        @Volatile var stopped=false
        override var routeType:Int?=android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        val complete=java.util.concurrent.CountDownLatch(1)
        override fun stop(){stopped=true;complete.countDown()}
        override fun play(pcm:ByteArray,allowed:()->Boolean,started:()->Unit):Boolean {
            released=false
            return try {started();val end=System.nanoTime()+4_000_000_000;while(!stopped && allowed() && System.nanoTime()<end && !complete.await(20,java.util.concurrent.TimeUnit.MILLISECONDS)){};!stopped && allowed()}
            finally {released=true;pcm.fill(0)}
        }
    }
    @Test fun speakingInterruptsCompatiblePlaybackAndStartsExactlyOneNewTurn() {
        val echo=EchoRecorder();val player=BlockingPlayback();GoogleVoiceDependencies.echoCapture={echo};GoogleVoiceDependencies.playback={player}
        launch();capture.phrase();pump{web.sends==1};pump{capture.starts>=2}
        val stateField=GoogleVoiceActivity::class.java.getDeclaredField("state").apply{isAccessible=true}
        val oldTurn=(stateField.get(activity) as GoogleTurnState).turn
        val command=JSONObject().put("params",JSONObject().put("session_id",token).put("turn_id",oldTurn).put("text","Risposta da interrompere"))
        val outcome=java.util.concurrent.atomic.AtomicReference<JSONObject>()
        Thread({outcome.set(GoogleVoiceActivity.speak(command,SystemClock.elapsedRealtime()+25000,{true}){})}).apply{isDaemon=true;start()}
        pump{echo.enabled};assertFalse(player.stopped);echo.speak();shadowOf(Looper.getMainLooper()).idle();echo.phrase()
        pump{outcome.get()!=null};assertEquals("interrupted",outcome.get().getString("code"));assertTrue(outcome.get().getBoolean("playback_started"));assertFalse(outcome.get().getBoolean("completed"))
        assertTrue(player.stopped);pump{web.sends==2};assertNotEquals(oldTurn,(stateField.get(activity) as GoogleTurnState).turn)
        assertEquals(2,api.transcriptions);assertEquals(url,web.currentUrl)
    }
    @Test fun suspectedPlaybackEchoIsDiscardedAndNextRealSpeechCanSendWithoutATap() {
        val echo=EchoRecorder();val player=BlockingPlayback();GoogleVoiceDependencies.echoCapture={echo};GoogleVoiceDependencies.playback={player}
        launch();capture.phrase();pump{web.sends==1};pump{capture.starts>=2}
        val state=GoogleVoiceActivity::class.java.getDeclaredField("state").apply{isAccessible=true}.get(activity) as GoogleTurnState
        val spoken="La risposta pronunciata dal telefono"
        val command=JSONObject().put("params",JSONObject().put("session_id",token).put("turn_id",state.turn).put("text",spoken))
        val outcome=java.util.concurrent.atomic.AtomicReference<JSONObject>()
        Thread({outcome.set(GoogleVoiceActivity.speak(command,SystemClock.elapsedRealtime()+25000,{true}){})}).apply{isDaemon=true;start()}
        pump{echo.enabled};api.transcript=spoken;echo.speak();shadowOf(Looper.getMainLooper()).idle();echo.phrase()
        pump{outcome.get()!=null};pump{capture.starts>=3};assertEquals(1,web.sends);assertTrue(player.stopped)
        api.transcript="Questa è una nuova richiesta diversa";capture.speak();shadowOf(Looper.getMainLooper()).idle();capture.phrase()
        pump{web.sends==2};assertEquals(3,api.transcriptions);assertEquals(url,web.currentUrl)
    }
    @Test fun changingOutputToUnverifiedCarBusStopsBargeCaptureAndResumesAfterPlayback() {
        val echo=EchoRecorder();val player=BlockingPlayback();GoogleVoiceDependencies.echoCapture={echo};GoogleVoiceDependencies.playback={player}
        launch();capture.phrase();pump{web.sends==1};pump{capture.starts>=2}
        val state=GoogleVoiceActivity::class.java.getDeclaredField("state").apply{isAccessible=true}.get(activity) as GoogleTurnState
        val command=JSONObject().put("params",JSONObject().put("session_id",token).put("turn_id",state.turn).put("text","Risposta con cambio uscita auto"))
        val outcome=java.util.concurrent.atomic.AtomicReference<JSONObject>()
        Thread({outcome.set(GoogleVoiceActivity.speak(command,SystemClock.elapsedRealtime()+25000,{true}){})}).apply{isDaemon=true;start()}
        pump{echo.enabled};player.routeType=android.media.AudioDeviceInfo.TYPE_BUS
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500));assertFalse(echo.enabled);assertTrue(echo.released);assertFalse(player.stopped)
        player.complete.countDown();pump{outcome.get()!=null};assertTrue(outcome.get().getBoolean("completed"));pump{capture.starts>=3};assertEquals(1,web.sends)
    }

    @Test fun gallerySaveHappensWithVisibleCoverAndUnchangedTransparencyBeforeZipPicker() {
        launch();val cover=view<android.widget.FrameLayout>("cover")
        val opacity=CoverSettings.isOpaque(app);var photographed=false;var saved=false
        DiagnosticScreenshots.windowCapture={_,done ->
            photographed=true;assertEquals(View.VISIBLE,cover.visibility);assertNotNull(cover.parent);assertEquals(opacity,CoverSettings.isOpaque(app))
            done(android.graphics.Bitmap.createBitmap(4,4,android.graphics.Bitmap.Config.ARGB_8888),"captured")
        }
        DiagnosticScreenshots.galleryWrite={_,_,allowed -> assertTrue(allowed());assertEquals(View.VISIBLE,cover.visibility);saved=true;"saved"}
        GoogleVoiceActivity::class.java.getDeclaredMethod("exportDiagnostics").apply{isAccessible=true}.invoke(activity)
        pump{saved};pump{cover.parent==null}
        assertTrue(photographed);assertEquals(opacity,CoverSettings.isOpaque(app))
        assertEquals(DiagnosticExportActivity::class.java.name,shadowOf(app).nextStartedActivity.component!!.className)
    }

}
