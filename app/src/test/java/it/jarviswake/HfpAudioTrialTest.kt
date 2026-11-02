package it.jarviswake

import org.junit.Assert.*
import org.junit.Test

class HfpAudioTrialTest {
    private class Fake:HfpPort<String> {
        var current=HfpState(true,true,false)
        var starts=0;var stops=0;var accept=true;var release=true;var failStart=false;var failState=false;var failStop=false
        override fun state(device:String):HfpState {if(failState) error("unavailable");return current}
        override fun start(device:String):Boolean {starts++;if(failStart) error("uncertain");return accept}
        override fun stop(device:String):Boolean {stops++;if(failStop) error("uncertain");if(release) current=current.copy(audioConnected=false);return release}
    }
    @Test fun noStartWithoutExplicitConditionsAndCurrentSupportedDisconnectedAudio() {
        val p=Fake();val t=HfpAudioTrial(p)
        assertEquals("not_allowed",t.start("watch",0,false));assertEquals(0,p.starts)
        for((s,code) in listOf(HfpState(false,true,false) to "not_connected",HfpState(true,false,false) to "unsupported",
            HfpState(true,null,false) to "support_unavailable",HfpState(true,true,null) to "audio_state_unavailable")) {
            p.current=s;assertEquals(code,t.start("watch",0,true));assertFalse(t.active)
        }
        p.failState=true;assertEquals("state_unavailable",t.start("watch",0,true))
        assertEquals(0,p.starts);assertEquals(0,p.stops)
    }
    @Test fun preexistingAudioIsNeverOwnedOrStopped() {
        val p=Fake();p.current=p.current.copy(audioConnected=true);val t=HfpAudioTrial(p)
        assertEquals("audio_already_connected",t.start("watch",0,true));assertFalse(t.active)
        t.observe(100000,false);t.stop("page_left");assertEquals(0,p.starts);assertEquals(0,p.stops)
    }
    @Test fun acceptedRequestWaitsForObservedAudioAndNeverStartsTwice() {
        val p=Fake();val t=HfpAudioTrial(p)
        assertEquals("request_accepted",t.start("watch",0,true));assertTrue(t.active)
        assertEquals("busy",t.start("other",100,true));assertEquals(1,p.starts)
        assertEquals("request_accepted",t.observe(14999,true))
        p.current=p.current.copy(audioConnected=true)
        assertEquals("audio_connected",t.observe(15000,true));assertEquals(0,p.stops)
        assertEquals("audio_connected",t.observe(89999,true))
        assertEquals("trial_timeout_released",t.observe(90000,true));assertFalse(t.active)
        t.stop();t.observe(91000,true);assertEquals(1,p.starts);assertEquals(1,p.stops)
    }
    @Test fun connectTimeoutCleansUpOnceAndDoesNotRetry() {
        val p=Fake();val t=HfpAudioTrial(p);t.start("watch",100,true)
        assertEquals("request_accepted",t.observe(15099,true))
        assertEquals("connect_timeout_released",t.observe(15100,true))
        t.observe(20000,true);t.stop("page_left");assertEquals(1,p.starts);assertEquals(1,p.stops)
    }
    @Test fun uncertainDispatchStillOwnsOneBoundedCleanup() {
        val p=Fake();p.failStart=true;val t=HfpAudioTrial(p)
        assertEquals("request_uncertain",t.start("watch",0,true));assertTrue(t.active)
        assertEquals("page_left_released",t.stop("page_left"));t.stop();assertEquals(1,p.starts);assertEquals(1,p.stops)
    }
    @Test fun rejectedDispatchDoesNotStopOrRetry() {
        val p=Fake();p.accept=false;val t=HfpAudioTrial(p)
        assertEquals("request_rejected",t.start("watch",0,true));assertFalse(t.active)
        t.observe(20000,true);t.stop();assertEquals(1,p.starts);assertEquals(0,p.stops)
    }
    @Test fun leavingConditionsOrLosingStateStopsOnceAndPreservesUncertainty() {
        val p=Fake();val t=HfpAudioTrial(p);t.start("watch",0,true);p.release=false
        assertEquals("conditions_lost_release_unconfirmed",t.observe(1,false));assertFalse(t.active)
        t.stop();assertEquals(1,p.stops)
        p.release=true;t.start("watch",2,true);p.failState=true
        assertEquals("state_lost_release_unconfirmed",t.observe(3,true));t.stop();assertEquals(2,p.stops)
    }
    @Test fun connectedAudioLossAndThrowingStopNeverCauseRepeatedStop() {
        val p=Fake();val t=HfpAudioTrial(p);t.start("watch",0,true)
        p.current=p.current.copy(audioConnected=true);t.observe(100,true)
        p.current=p.current.copy(audioConnected=false);p.failStop=true
        assertEquals("audio_disconnected_release_unconfirmed",t.observe(200,true))
        t.stop("page_left");t.observe(90000,false);assertEquals(1,p.stops)
    }
}
