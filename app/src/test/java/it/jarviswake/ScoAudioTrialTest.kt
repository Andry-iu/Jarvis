package it.jarviswake

import org.junit.Assert.*
import org.junit.Test

class ScoAudioTrialTest {
    private class Fake:ScoPort<String> {
        var current=ScoState(true,true,false,null)
        var starts=0;var stops=0;var failStart=false;var failState=false;var failStop=false;var release=true
        override fun state(device:String):ScoState {if(failState) error("unavailable");return current}
        override fun start() {starts++;if(failStart) error("uncertain")}
        override fun stop() {stops++;if(failStop) error("uncertain");if(release) current=current.copy(targetAudio=false,globalAudio=0)}
    }
    @Test fun dispatchIsNotAcceptanceAndNoVoiceRecognitionFeatureIsRequired() {
        val p=Fake();val t=ScoAudioTrial(p)
        assertEquals("request_dispatched",t.start("watch",0,true));assertTrue(t.active)
        assertEquals("busy",t.start("other",10,true));assertEquals(1,p.starts)
        assertEquals("request_dispatched",t.observe(100,true))
        p.current=p.current.copy(globalAudio=1)
        assertEquals("global_connected_target_unconfirmed",t.observe(200,true))
        p.current=p.current.copy(targetAudio=true)
        assertEquals("target_audio_connected",t.observe(300,true))
    }
    @Test fun onlyChosenSoleConnectedHfpMayDispatchAndUnknownOrBusyAudioIsRejected() {
        val p=Fake();val t=ScoAudioTrial(p)
        assertEquals("not_allowed",t.start("watch",0,false))
        for((s,code) in listOf(ScoState(false,true,false,0) to "target_not_unique",ScoState(true,false,false,0) to "off_call_unavailable",
            ScoState(true,null,false,0) to "off_call_unknown",ScoState(true,true,null,0) to "target_audio_unknown",
            ScoState(true,true,true,0) to "audio_already_active",ScoState(true,true,false,1) to "audio_already_active",
            ScoState(true,true,false,2) to "audio_already_active",ScoState(true,true,false,-1) to "global_audio_error")) {
            p.current=s;assertEquals(code,t.start("watch",0,true));assertFalse(t.active)
        }
        p.failState=true;assertEquals("state_unavailable",t.start("watch",0,true))
        t.stop();assertEquals(0,p.starts);assertEquals(0,p.stops)
    }
    @Test fun connectionTimeoutStopsOnceAndDoesNotRepeatDispatch() {
        val p=Fake();val t=ScoAudioTrial(p);t.start("watch",50,true)
        assertEquals("request_dispatched",t.observe(15049,true))
        assertEquals("connect_timeout_release_observed",t.observe(15050,true));assertFalse(t.active)
        t.observe(100000,true);t.stop("page_left");assertEquals(1,p.starts);assertEquals(1,p.stops)
    }
    @Test fun connectedTrialStillEndsAtTotalDeadlineAndReleasesOnlyOnce() {
        val p=Fake();val t=ScoAudioTrial(p);t.start("watch",0,true);p.current=p.current.copy(targetAudio=true,globalAudio=1)
        assertEquals("target_audio_connected",t.observe(89999,true))
        assertEquals("trial_timeout_release_observed",t.observe(90000,true))
        t.stop();assertEquals(1,p.stops)
    }
    @Test fun dispatchExceptionKeepsResponsibilityForBoundedCleanup() {
        val p=Fake();p.failStart=true;val t=ScoAudioTrial(p)
        assertEquals("request_uncertain",t.start("watch",0,true));assertTrue(t.active)
        assertEquals("page_left_release_observed",t.stop("page_left"));t.stop();assertEquals(1,p.starts);assertEquals(1,p.stops)
    }
    @Test fun permissionCallLockWakeOrLeavingVoiceConditionsStopWithoutRetry() {
        val p=Fake();p.release=false;val t=ScoAudioTrial(p);t.start("watch",0,true)
        assertEquals("conditions_lost_release_unconfirmed",t.observe(100,false));assertFalse(t.active)
        t.stop();t.observe(90000,true);assertEquals(1,p.stops)
    }
    @Test fun newDeviceOrUnknownProfileStopsRequestRatherThanSelectingAnotherHeadset() {
        val p=Fake();val t=ScoAudioTrial(p);t.start("watch",0,true);p.current=p.current.copy(soleTarget=false)
        assertEquals("state_lost_release_observed",t.observe(100,true))
        p.current=p.current.copy(soleTarget=true);t.start("watch",200,true);p.failState=true
        assertEquals("state_lost_release_unconfirmed",t.observe(300,true));t.stop();assertEquals(2,p.stops)
    }
    @Test fun failedConnectionAndAudioLostAfterConnectionAreSeparateOutcomes() {
        val p=Fake();val t=ScoAudioTrial(p);t.start("watch",0,true);p.current=p.current.copy(globalAudio=2);t.observe(100,true)
        p.current=p.current.copy(globalAudio=0);assertEquals("connection_failed_release_observed",t.observe(200,true))
        t.start("watch",300,true);p.current=p.current.copy(targetAudio=true,globalAudio=1);t.observe(400,true)
        p.current=p.current.copy(targetAudio=false,globalAudio=0);p.failStop=true
        assertEquals("target_audio_disconnected_release_unconfirmed",t.observe(500,true));t.stop();assertEquals(2,p.stops)
    }
    @Test fun voidStopWithoutObservedDisconnectedGlobalStateDoesNotProveRelease() {
        val p=Fake();val t=ScoAudioTrial(p);t.start("watch",0,true);p.release=false
        assertEquals("manual_release_unconfirmed",t.stop());assertEquals(1,p.stops)
    }
}
