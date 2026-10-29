package it.jarviswake

import org.junit.Assert.*
import org.junit.Test

class NativeAudioRecoveryTest {
    @Test fun unknownOrBusyAudioNeverRearmsWakeEvenAfterUiTimeout() {
        val gate=NativeAudioReleaseGate()
        assertFalse(gate.observe("a",0,false))
        assertFalse(gate.observe("a",5000,false))
        assertFalse(gate.observe("a",60000,false))
        assertFalse(NativeAudioState(null,true).free)
        assertFalse(NativeAudioState(0,null).free)
        assertFalse(NativeAudioState(1,true).free)
        assertFalse(NativeAudioState(0,false).free)
    }
    @Test fun continuousReleaseIsRequiredAndInterruptionOrNewCycleResetsIt() {
        val gate=NativeAudioReleaseGate()
        assertFalse(gate.observe("a",0,true))
        assertFalse(gate.observe("a",999,true))
        assertTrue(gate.observe("a",1000,true))
        assertFalse(gate.observe("a",1200,false))
        assertFalse(gate.observe("a",1500,true))
        assertTrue(gate.observe("a",2500,true))
        assertFalse(gate.observe("b",3000,true))
        assertFalse(gate.observe(null,4000,true))
    }
    @Test fun draftOrMissingAccessibilityCannotPermanentlyHoldReleasedPendingExit() {
        for (home in listOf(false,true)) {
            val exit=NativeExitState()
            val cycle=VoiceCycleState();assertTrue(cycle.restoreNativeExit("cycle"))
            assertTrue(exit.dismiss(home,0))
            assertTrue(exit.consumeStop());assertFalse(exit.consumeStop())
            val released=NativeAudioReleaseGate()
            assertFalse(released.observe(cycle.id,5000,false))
            assertFalse(released.observe(cycle.id,6000,true))
            assertTrue(released.observe(cycle.id,7000,true))
            assertTrue(exit.coverDismissed);assertEquals(home,exit.returnHome)
            // No start-button label is required for the hardware-release recovery path.
            assertTrue(cycle.cancel("cycle"))
            assertTrue(WakeRecoveryPolicy.canResume(true,cycle.id!=null,false,false,7000,0,true))
        }
    }
    @Test fun recoveredProcessCannotSendAnotherVoiceStartOrReplaceALiveCycle() {
        val state=VoiceCycleState()
        assertTrue(state.restoreNativeExit("old"))
        assertEquals(VoiceCycleState.Phase.VOICE,state.phase)
        assertFalse(state.touchGate.canDispatch)
        assertFalse(state.restoreNativeExit("new"))
        assertEquals("old",state.id)
        assertFalse(state.expired(Long.MAX_VALUE))
    }
    @Test fun microphoneAcquisitionRequiresFreeKnownAudioWithoutPrivacyMute() {
        assertTrue(NativeAudioState(0,true).canKeepOwn(false))
        assertFalse(NativeAudioState(1,true).canKeepOwn(false))
        assertTrue(NativeAudioState(1,true).canKeepOwn(true))
        assertFalse(NativeAudioState(2,true).canKeepOwn(true))
        assertFalse(NativeAudioState(null,true).canKeepOwn(false))
        assertFalse(NativeAudioState(0,true,true).canKeepOwn(false))
        assertFalse(NativeAudioState(-1,true).canKeepOwn(false))
    }
    @Test fun retriesWaitForResourcesAndNeverOverrideVoiceManualStopOrWakeHandoff() {
        fun can(cycle:Boolean=false,stop:Boolean=false,pending:Boolean=false,audio:Boolean=true,screen:Boolean=true,now:Long=1000)=
            WakeRecoveryPolicy.canResume(screen,cycle,stop,pending,now,1000,audio)
        assertTrue(can())
        assertFalse(can(cycle=true));assertFalse(can(stop=true));assertFalse(can(pending=true))
        assertFalse(can(audio=false));assertFalse(can(screen=false));assertFalse(can(now=999))
        assertEquals(1000L,WakeRecoveryPolicy.retryDelay(1))
        assertEquals(2000L,WakeRecoveryPolicy.retryDelay(2))
        assertEquals(5000L,WakeRecoveryPolicy.retryDelay(100))
    }
    @Test fun repeatedExitKeepsFirstDestinationAndStopConsumedWhileWakeWaits() {
        val state=NativeExitState()
        assertTrue(state.dismiss(true,0));assertTrue(state.consumeStop())
        assertFalse(state.dismiss(false,1000))
        assertTrue(state.reportTimeout(5000));assertTrue(state.coverDismissed)
        assertTrue(state.returnHome);assertFalse(state.consumeStop())
        assertFalse(WakeRecoveryPolicy.canResume(true,true,false,false,6000,0,true))
    }
    @Test fun screenOffGraceRemainsExactlyThirtySeconds() {
        assertTrue(WakeScreenPolicy.shouldListen(false,false,false,29999,30000))
        assertFalse(WakeScreenPolicy.shouldListen(false,false,false,30000,30000))
        assertFalse(WakeScreenPolicy.shouldListen(false,true,false,1000,30000))
    }    @Test fun screenOnCanResumeImmediatelyAfterResettingRetryButNotAgainstVoice() {
        assertTrue(WakeRecoveryPolicy.canResume(true,false,false,false,100,0,true))
        assertFalse(WakeRecoveryPolicy.canResume(true,true,false,false,100,0,true))
        assertFalse(WakeRecoveryPolicy.canResume(true,false,false,false,100,0,false))
    }
    @Test fun automaticServiceRestartCannotUndoManualStopEvenWhenARecoveryIntentWasQueued() {
        assertFalse(WakeRecoveryPolicy.allowServiceStart(true,false))
        assertTrue(WakeRecoveryPolicy.allowServiceStart(true,true))
        assertTrue(WakeRecoveryPolicy.allowServiceStart(false,false))
    }

}
