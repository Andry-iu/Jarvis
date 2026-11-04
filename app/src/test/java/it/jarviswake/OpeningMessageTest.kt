package it.jarviswake

import android.graphics.Rect
import android.os.Looper
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import java.time.Duration
import java.util.UUID
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock

/** Real controller/actions and delayed gesture callbacks; no device/network acceptance implied. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34,36])
class OpeningMessageTest {
    private val token = UUID.randomUUID().toString()
    private val cycleField = VoiceCycle::class.java.getDeclaredField("state").apply { isAccessible = true }
    private val previousCycle = cycleField.get(null)
    private lateinit var lifecycle: org.robolectric.android.controller.ServiceController<VoiceAccessibilityService>
    private lateinit var service: VoiceAccessibilityService
    private lateinit var controller: NativeVoiceController
    private lateinit var window: AccessibilityWindowInfo
    private lateinit var root: AccessibilityNodeInfo
    private lateinit var editor: AccessibilityNodeInfo
    private lateinit var send: AccessibilityNodeInfo
    private lateinit var voice: AccessibilityNodeInfo
    private var writes = 0
    private var starts = 0
    private fun node(text: String = "", bounds: Rect = Rect(0,0,500,50)) = AccessibilityNodeInfo.obtain().apply {
        packageName = NativeChatSettings.PACKAGE
        this.text = text
        isEnabled = true; isVisibleToUser = true
        setBoundsInScreen(bounds)
    }
    @Before fun setup() {
        lifecycle = Robolectric.buildService(VoiceAccessibilityService::class.java).create()
        service = lifecycle.get()
        cycleField.set(null, VoiceCycleState().apply { begin(token, 0L); openWeb(token,0L,true,true,false,false) })
        service.getSharedPreferences("voice_accessibility",0).edit().clear().putString("native_token",token).apply()
        window = AccessibilityWindowInfo.obtain()
        shadowOf(window).apply {
            setId(7); setType(AccessibilityWindowInfo.TYPE_APPLICATION); setLayer(1)
            setActive(true); setFocused(true); setBoundsInScreen(Rect(0,0,1000,2000))
        }
        root = node()
        editor = node(bounds=Rect(50,1400,950,1500)).apply {
            isEditable = true; isFocused = true
            addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_TEXT)
        }
        voice = node("Voice",Rect(880,1700,940,1760)).apply { isClickable = true }
        val edge = (48*service.resources.displayMetrics.density).toInt()
        send = node("Invia",Rect(950-edge,1500,950,1500+edge)).apply { isClickable = true }
        listOf(root,editor,voice,send).forEach { shadowOf(it).setAccessibilityWindowInfo(window) }
        shadowOf(root).apply {
            addChild(node(NativeChatSettings.project(service))); addChild(editor); addChild(voice); addChild(send)
        }
        shadowOf(window).setRoot(root); shadowOf(service).setWindows(listOf(window))
        shadowOf(editor).setOnPerformActionListener { action, args ->
            assertEquals(AccessibilityNodeInfo.ACTION_SET_TEXT,action)
            writes++; editor.text=args.getCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE);true
        }
        shadowOf(voice).setOnPerformActionListener { action, _ ->
            assertEquals(AccessibilityNodeInfo.ACTION_CLICK,action);starts++;true
        }
        shadowOf(service).setCanDispatchGestures(true)
        controller = NativeVoiceController(service,token)
        controller.launch(openShortcut=false,playWaitingAudio=false)
    }
    @After fun cleanup() { controller.destroy(); lifecycle.destroy(); cycleField.set(null,previousCycle) }
    private fun showActiveVoiceControls() {
        for(label in listOf("Termina","Disattiva microfono")) {
            val control=node(label).apply {isClickable=true}
            shadowOf(control).setAccessibilityWindowInfo(window);shadowOf(root).addChild(control)
        }
    }
    @Test fun bothEmptyMessagesStartVoiceWithoutTextOrSendDelayAndNeverAttemptAGreeting() {
        service.getSharedPreferences("opening_message",0).edit().putString("text","").commit()
        assertTrue(VoiceGreetingSettings.save(service,""))
        controller.poll();assertEquals(1,starts);assertEquals(0,writes)
        assertEquals(NativeGreetingPolicy.SKIPPED,service.nativeGreetingStage(token,true))
        showActiveVoiceControls();controller.poll()
        assertEquals(NativeGreetingPolicy.SKIPPED,service.nativeGreetingStage(token))
        advance(11000);controller.poll();controller.poll()
        assertEquals(1,starts);assertEquals(0,writes);assertTrue(shadowOf(service).gesturesDispatched.isEmpty())
        assertEquals(NativeGreetingPolicy.SKIPPED,service.nativeGreetingStage(token))
    }
    @Test fun emptyOpeningStillProtectsAnExistingDraft() {
        service.getSharedPreferences("opening_message",0).edit().putString("text","").commit()
        editor.text="Bozza personale";controller.poll()
        assertEquals("Bozza personale",editor.text.toString());assertEquals(0,writes);assertEquals(0,starts)
        editor.text="";controller.poll();assertEquals(1,starts);assertEquals(0,writes)
    }
    @Test fun anEmptyGreetingDoesNotSkipTheConfiguredOpeningMessageOrItsDelay() {
        assertTrue(VoiceGreetingSettings.save(service,""))
        queueSend();completeSend();controller.poll();advance(299);controller.poll();assertEquals(0,starts)
        advance(1);controller.poll();assertEquals(1,starts)
        showActiveVoiceControls();controller.poll();advance(11000);controller.poll()
        assertEquals(1,writes);assertEquals(1,shadowOf(service).gesturesDispatched.size)
        assertEquals(NativeGreetingPolicy.SKIPPED,service.nativeGreetingStage(token))
    }
    @Test fun anEmptyOpeningDoesNotDisableANonemptyGreetingAfterVoice() {
        service.getSharedPreferences("opening_message",0).edit().putString("text","").commit()
        assertTrue(VoiceGreetingSettings.save(service,"Saluto configurato"))
        controller.poll();assertEquals(1,starts);assertEquals(0,writes)
        showActiveVoiceControls();controller.poll()
        assertEquals("Saluto configurato",editor.text.toString());assertEquals(1,writes)
        assertEquals(NativeGreetingPolicy.WRITING,service.nativeGreetingStage(token))
    }
    private fun advance(ms: Long) { ShadowSystemClock.advanceBy(Duration.ofMillis(ms)) }
    private fun queueSend() {
        controller.poll()
        assertEquals(OpeningMessagePolicy.TEXT,editor.text.toString()); assertEquals(1,writes);assertEquals(0,starts)
        controller.poll();advance(250);controller.poll()
        assertEquals(NativeGreetingPolicy.DISPATCHED, service.nativeGreetingStage(token,true))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(80))
        assertEquals(1,shadowOf(service).gesturesDispatched.size)
    }
    private fun completeSend(clear: Boolean = true) {
        val gesture=shadowOf(service).gesturesDispatched.single()
        gesture.callback().onCompleted(gesture.description())
        if (clear) editor.text=""
    }
    private fun withPreparedClaim(action:()->Unit) {
        val ledgerField=PreparedChat::class.java.getDeclaredField("ledger").apply {isAccessible=true}
        val previous=ledgerField.get(null)
        val ledger=PreparedChatLedger(null) {}
        val project=ChatGptProjectSettings.getUrl(service)
        val attempt=ledger.begin(project,NativeChatSettings.project(service),System.currentTimeMillis())!!
        assertTrue(ledger.complete(attempt.attempt,"https://chatgpt.com/c/12345678-1234-4234-8234-123456789abc",
            "Ho letto le istruzioni del progetto e sono pronto a continuare questa conversazione.",System.currentTimeMillis()))
        assertNotNull(ledger.claim(token,project,NativeChatSettings.project(service),System.currentTimeMillis()))
        ledgerField.set(null,ledger)
        service.getSharedPreferences("prepared_chat_settings",0).edit().putBoolean("enabled",true).apply()
        try {action()} finally {
            ledgerField.set(null,previous)
            service.getSharedPreferences("prepared_chat_settings",0).edit().putBoolean("enabled",false).apply()
        }
    }
    @Test fun configuredOpeningTextIsWrittenAndSentOnceBeforeTheOriginalVoiceDelay() {
        val custom="Leggi le regole.\nRispondi senza annunci preliminari."
        service.getSharedPreferences("opening_message",0).edit().putString("text",custom).commit()
        controller.poll();assertEquals(custom,editor.text.toString());assertEquals(1,writes)
        controller.poll();advance(250);controller.poll()
        assertEquals(NativeGreetingPolicy.DISPATCHED,service.nativeGreetingStage(token,true))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(80));completeSend()
        controller.poll();advance(299);controller.poll();assertEquals(0,starts)
        advance(1);controller.poll();assertEquals(1,starts);controller.poll();assertEquals(1,starts)
        assertEquals(1,writes);assertEquals(1,shadowOf(service).gesturesDispatched.size)
        assertEquals(VoiceGreetingSettings.DEFAULT_PHRASE,service.nativeGreetingText(token,false))
    }
    @Test fun linkOnlyPreparationStartsVoiceWithoutRequiringOrReinsertingAResponse() {
        val f=PreparedChat::class.java.getDeclaredField("ledger").apply {isAccessible=true};val previous=f.get(null)
        val l=PreparedChatLedger(null) {};val project=ChatGptProjectSettings.getUrl(service)
        val now=System.currentTimeMillis();val a=l.begin(project,NativeChatSettings.project(service),now-1000)!!
        assertTrue(l.markSubmitted(a.attempt,now-500))
        assertTrue(l.captureLink(a.attempt,"https://chatgpt.com/c/12345678-1234-4234-8234-123456789abc",now))
        assertNotNull(l.claim(token,project,NativeChatSettings.project(service),now));f.set(null,l)
        service.getSharedPreferences("prepared_chat_settings",0).edit().putBoolean("enabled",true).apply()
        try {
            controller.poll();assertEquals(0,writes);assertEquals(1,starts)
        } finally {
            f.set(null,previous);service.getSharedPreferences("prepared_chat_settings",0).edit().putBoolean("enabled",false).apply()
        }
    }
    @Test fun preparedChatWithDeepWrappersUsesTheExistingVoiceClickOnceAndExportsItsLabel() {
        var outer=root
        repeat(40) {
            val wrapper=node();shadowOf(wrapper).setAccessibilityWindowInfo(window)
            shadowOf(wrapper).addChild(outer);outer=wrapper
        }
        shadowOf(window).setRoot(outer)
        voice.contentDescription="Avvia una conversazione vocale"
        linkOnlyPreparationStartsVoiceWithoutRequiringOrReinsertingAResponse()
        controller.poll();assertEquals(1,starts);assertEquals(0,writes)
        assertTrue(shadowOf(service).gesturesDispatched.isEmpty())
        val capture=NativeObservationWalker.capture(outer,{it.isVisibleToUser},{it.childCount},
            {n,i->n.getChild(i)},{it.isEditable})
        val dump=NativeButtonSnapshot.describe(capture.visible)
        assertTrue(dump.contains("description=[Avvia una conversazione vocale]"))
    }
    @Test fun preparedNativeContentStartsVoiceWithoutReinsertingTheOpeningMessage() = withPreparedClaim {
        shadowOf(root).addChild(node("Ho letto le istruzioni del progetto e sono pronto a continuare questa conversazione."))
        controller.poll();assertEquals(0,writes);assertEquals(1,starts)
        assertTrue(service.nativeOpeningConfirmed(token));controller.poll();assertEquals(1,starts)
    }
    @Test fun projectListingPreviewCannotBeMistakenForThePreparedConversation() = withPreparedClaim {
        shadowOf(root).addChild(node("Chat"));shadowOf(root).addChild(node("Fonti"))
        shadowOf(root).addChild(node("Ho letto le istruzioni del progetto e sono pronto a continuare questa conversazione."))
        controller.poll();assertEquals(0,writes);assertEquals(0,starts)
    }
    @Test fun missingPreparedContentOrUserDraftNeverTriggersVoiceOrDuplicateInstructions() = withPreparedClaim {
        controller.poll();assertEquals(0,writes);assertEquals(0,starts)
        shadowOf(root).addChild(node("Ho letto le istruzioni del progetto e sono pronto a continuare questa conversazione."))
        editor.text="una bozza dell’utente";controller.poll();assertEquals(0,writes);assertEquals(0,starts)
    }
    @Test fun observed097OverlappingComposerSendsOnceAndStillWaits300msBeforeVoice() {
        // Sanitized ZIP _10_ geometry, scaled to this test display's density.
        val scale=service.resources.displayMetrics.density/3.5f
        fun box(l:Int,t:Int,r:Int,b:Int)=Rect((l*scale).toInt(),(t*scale).toInt(),(r*scale).toInt(),(b*scale).toInt())
        shadowOf(window).setBoundsInScreen(box(0,0,1440,3120))
        editor.setBoundsInScreen(box(112,1352,1328,1544))
        send.text="" // The real actionable wrapper has no accessible label.
        send.setBoundsInScreen(box(1192,1512,1384,1704))
        val microphone=node(bounds=box(1000,1512,1192,1704)).apply {isClickable=true}
        shadowOf(microphone).setAccessibilityWindowInfo(window);shadowOf(root).addChild(microphone)
        val keyboard=AccessibilityWindowInfo.obtain()
        shadowOf(keyboard).apply {setId(9);setType(AccessibilityWindowInfo.TYPE_INPUT_METHOD);setBoundsInScreen(box(0,1752,1440,3120))}
        shadowOf(service).setWindows(listOf(window,keyboard))
        voice.isEnabled=false
        queueSend();completeSend();controller.poll();assertEquals(0,starts)
        advance(299);voice.isEnabled=true;controller.poll();assertEquals(0,starts)
        advance(1);controller.poll();assertEquals(1,starts)
        controller.poll();assertEquals(1,starts);assertEquals(1,writes)
        assertEquals(1,shadowOf(service).gesturesDispatched.size)
    }
    @Test fun exactTextIsSentBeforeVoiceAndTimerStartsAtSendCompletion() {
        queueSend();advance(300);controller.poll();assertEquals(0,starts)
        completeSend(clear=false);advance(300);controller.poll();assertEquals(0,starts)
        editor.text="";controller.poll();assertEquals(1,starts)
        controller.poll();assertEquals(1,starts);assertEquals(1,writes)
    }
    @Test fun earlyReadyVoiceWaits299ThenStartsAt300WithoutAnotherDelay() {
        queueSend();completeSend();controller.poll();assertEquals(0,starts)
        advance(299);controller.poll();assertEquals(0,starts)
        advance(1);controller.poll();assertEquals(1,starts)
    }
    @Test fun lateReadyVoiceStartsOnFirstEligiblePollWithoutRestartingTimer() {
        voice.isEnabled=false;queueSend();completeSend();advance(1000);controller.poll();assertEquals(0,starts)
        voice.isEnabled=true;controller.poll();assertEquals(1,starts)
    }
    @Test fun draftIsNeverOverwrittenAndTextDoesNotWaitForVoiceReadiness() {
        voice.isEnabled=false;editor.text="bozza personale";controller.poll()
        assertEquals(0,writes);assertEquals("bozza personale",editor.text.toString());assertEquals(0,starts)
        editor.text="";advance(500);controller.poll();assertEquals(0,writes);assertEquals(0,starts)
    }
    @Test fun rejectedGestureNeverStartsVoiceOrRetriesTheMessage() {
        shadowOf(service).setCanDispatchGestures(false)
        controller.poll();controller.poll();advance(250);controller.poll()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(80))
        editor.text="";advance(1000);controller.poll();assertEquals(0,starts);assertEquals(1,writes)
        assertEquals(NativeGreetingPolicy.ABORTED,service.nativeGreetingStage(token,true))
    }
    @Test fun reconnectKeepsTextAndTimerConsumedAndLiveGreetingIndependent() {
        queueSend();completeSend();controller.poll();advance(300)
        controller.destroy();controller=NativeVoiceController(service,token)
        controller.launch(openShortcut=false,playWaitingAudio=false);controller.poll()
        assertEquals(1,starts);assertEquals(1,writes)
        assertEquals(NativeGreetingPolicy.WAITING,service.nativeGreetingStage(token))
        assertFalse(service.nativeGreetingStarted(token))
    }
    @Test fun exitWhileTapIsQueuedRemovesWindowAndLateCallbackCannotStartOrRestore() {
        controller.poll();controller.poll();advance(250);controller.poll()
        val hudField=NativeVoiceController::class.java.getDeclaredField("hud").apply { isAccessible=true }
        val hud=hudField.get(controller) as HolographicOverlayView
        controller.dismissForExit();VoiceCycle.cancel(service,token,"annullato")
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1000))
        assertNull(hudField.get(controller));assertEquals(View.GONE,hud.visibility);assertFalse(hud.isAttachedToWindow)
        assertTrue(shadowOf(service).gesturesDispatched.isEmpty());assertEquals(0,starts)
    }
    @Test fun sentConversationMayReplaceProjectHeaderWithoutBlockingVoice() {
        queueSend();completeSend()
        root.getChild(0).text="titolo conversazione"
        advance(300);controller.poll();assertEquals(1,starts)
    }
    @Test fun projectSwitchBeforeDelayedSendPreventsGestureAndVoice() {
        controller.poll();controller.poll();advance(250);controller.poll()
        service.getSharedPreferences("native_chat",0).edit().putString("project","altro progetto").apply()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(80))
        assertTrue(shadowOf(service).gesturesDispatched.isEmpty());assertEquals(0,starts)
        assertEquals(NativeGreetingPolicy.ABORTED,service.nativeGreetingStage(token,true))
    }
    @Test fun cancelledGestureNeverStartsVoiceAndLateCompletionCannotReverseCancellation() {
        queueSend()
        val gesture=shadowOf(service).gesturesDispatched.single()
        gesture.callback().onCancelled(gesture.description())
        gesture.callback().onCompleted(gesture.description());editor.text="";advance(300);controller.poll()
        assertEquals(0,starts);assertEquals(-1L,service.nativeOpeningCompletedAt(token))
    }
    @Test fun completedCallbackDoesNotCertifySendAndUncertainSendTimesOutWithoutVoice() {
        queueSend();completeSend(clear=false);advance(10000);controller.poll()
        assertEquals(0,starts);assertEquals(1,writes)
        editor.text="";controller.poll();assertEquals(0,starts)
        assertEquals(NativeGreetingPolicy.ABORTED,service.nativeGreetingStage(token,true))
    }
}
