package it.jarviswake

import org.junit.Assert.*
import org.junit.Test

class NativeCoordinateSendTest {
    private fun b(l:Int,t:Int,r:Int,bt:Int)=NativeCoordinateSend.Box(l,t,r,bt)
    private val field=b(112,1111,1328,1496)
    private val window=b(0,0,1440,3120)
    private val arrow=b(1192,1512,1384,1704)
    private val ime=b(0,1752,1440,3120)
    private fun c(box: NativeCoordinateSend.Box, enabled:Boolean=true,label:Boolean=true)=NativeCoordinateSend.Control(box,enabled,label)
    private fun plan(controls:List<NativeCoordinateSend.Control>,blocked:List<NativeCoordinateSend.Box> = listOf(ime))=
        NativeCoordinateSend.plan(field,window,610,3.5f,controls,blocked)
    @Test fun observedGalaxyGeometryTargetsArrowInsteadOfPlusOrMicrophone() {
        val p=plan(listOf(c(b(48,1512,240,1704)),c(b(816,1512,1000,1704)),c(arrow)))!!
        assertEquals(1288f,p.x);assertEquals(1608f,p.y)
        assertEquals("control_bounds",p.source)
    }
    @Test fun explicitFallbackUsesLiveFieldAnchorWhenNoAccessibleArrowExists() {
        val p=plan(emptyList())!!
        assertEquals(1286f,p.x);assertEquals(1608f,p.y)
        assertEquals("field_anchor",p.source)
    }
    @Test fun liveDensityAndKeyboardMovementScaleAndTranslateThePoint() {
        val p=NativeCoordinateSend.plan(b(56,800,664,1000),b(0,0,720,1560),4,1.75f,
            emptyList(),listOf(b(0,1200,720,1560)))!!
        assertEquals(643f,p.x);assertEquals(1056f,p.y)
    }
    @Test fun disabledOrConflictingLabelNeverFallsBackToBlindAnchor() {
        assertNull(plan(listOf(c(arrow,false))))
        assertNull(plan(listOf(c(arrow,label=false))))
        assertNull(plan(listOf(c(arrow),c(b(1160,1512,1352,1704)))))
    }
    @Test fun tapCannotEnterKeyboardOrLeaveTheApp() {
        assertNull(plan(emptyList(),listOf(b(0,1500,1440,3120))))
        assertNull(NativeCoordinateSend.plan(field,b(0,0,1440,1550),1,3.5f,emptyList(),emptyList()))
    }
    @Test fun unknownLayoutAndInvalidGeometryCannotInventATap() {
        assertNull(NativeCoordinateSend.plan(b(100,100,200,200),window,1,3.5f,emptyList(),emptyList()))
        assertNull(NativeCoordinateSend.plan(field,window,1,Float.NaN,emptyList(),emptyList()))
        assertNull(NativeCoordinateSend.plan(field,window,1,0f,emptyList(),emptyList()))
        assertNull(NativeCoordinateSend.plan(b(100,100,2700,300),b(0,0,3120,1440),1,3.5f,emptyList(),emptyList()))
    }
    @Test fun stabilityRequiresTheExactPointAndWindowToRemainCurrent() {
        val stable=NativeSendStability<NativeCoordinateSend.Plan>()
        val p=plan(emptyList())!!
        assertFalse(stable.observe(p,0));assertTrue(stable.observe(p,250))
        assertFalse(stable.observe(p.copy(windowId=900),300))
        assertFalse(stable.observe(p.copy(x=p.x+10),600))
        assertFalse(stable.observe(null,900))
    }
    @Test fun rejectedCancelledTimedOutAndLateCallbackCannotRepeatDispatch() {
        val gate=NativeTapGate()
        assertTrue(gate.dispatch(true));assertFalse(gate.dispatch(true))
        assertTrue(gate.finish());assertFalse(gate.finish());assertFalse(gate.dispatch(true))
        val changed=NativeTapGate();assertFalse(changed.dispatch(false));assertTrue(changed.finish())
        assertFalse(changed.dispatch(true))
        val timeout=NativeTapGate();assertTrue(timeout.finish());assertFalse(timeout.dispatch(true))
    }
    @Test fun lateCallbackNeverRestoresADismissedDetachedOrReplacedCover() {
        val gate=NativeTapGate()
        assertFalse(gate.mayRestore(false,true,false))
        assertFalse(gate.mayRestore(true,false,false))
        assertFalse(gate.mayRestore(true,true,true))
        assertTrue(gate.mayRestore(true,true,false))
    }
}
