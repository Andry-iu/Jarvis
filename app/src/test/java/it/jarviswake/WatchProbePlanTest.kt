package it.jarviswake

import org.junit.Assert.*
import org.junit.Test

class WatchProbePlanTest {
    @Test fun onlyPreparedWatchCycleCanOpenOnceAndActivityMustAcknowledge() {
        val p=WatchProbePlan()
        assertFalse(p.bind("old",0));p.prepare(0)
        assertTrue(p.bind("watch",100));assertFalse(p.waiting("manual",100))
        assertFalse(p.consume("stale",101,true,false,true))
        assertTrue(p.consume("watch",102,true,false,true));assertEquals("open_dispatched",p.code)
        assertFalse(p.consume("watch",103,true,false,true));assertFalse(p.acknowledge("wrong"))
        assertTrue(p.acknowledge("watch"));assertEquals("page_opened",p.code)
        assertFalse(p.unconfirmed("watch"));assertFalse(p.acknowledge("watch"))
    }
    @Test fun expiryAppliesBeforeBindAndBeforeConfirmationWithoutASecondWindow() {
        val p=WatchProbePlan();p.prepare(100)
        assertFalse(p.bind("late",120100));assertEquals("expired",p.code)
        p.prepare(200);assertTrue(p.bind("watch",300))
        assertFalse(p.consume("watch",120200,true,false,true));assertEquals("expired",p.code)
    }
    @Test fun pendingStopUnconfirmedVoiceAndLockConsumeWithoutOpening() {
        for((ready,stop,unlocked) in listOf(Triple(true,true,true),Triple(false,false,true),Triple(true,false,false))) {
            val p=WatchProbePlan();p.prepare(0);p.bind("watch",1)
            assertFalse(p.consume("watch",2,ready,stop,unlocked))
            assertFalse(p.consume("watch",3,true,false,true))
        }
    }
    @Test fun failureToStartVoiceCancelAndCycleEndDoNotLeakIntoNextCycle() {
        val p=WatchProbePlan();p.prepare(0);assertFalse(p.bind(null,1));assertEquals("launch_not_started",p.code)
        p.prepare(2);p.bind("a",3);p.cycleEnded("a")
        assertFalse(p.consume("b",4,true,false,true));p.prepare(5);p.bind("b",6);p.cancel()
        assertFalse(p.consume("b",7,true,false,true))
    }
    @Test fun blockedAndroidLaunchIsUnconfirmedOnceAndCannotRetryAutomatically() {
        val p=WatchProbePlan();p.prepare(0);p.bind("watch",1)
        assertTrue(p.consume("watch",2,true,false,true));assertTrue(p.unconfirmed("watch"))
        assertEquals("open_unconfirmed",p.code);assertFalse(p.unconfirmed("watch"))
        assertFalse(p.consume("watch",3,true,false,true));assertFalse(p.acknowledge("watch"))
    }
}
