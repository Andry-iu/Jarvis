package it.jarviswake

import org.junit.Assert.*
import org.junit.Test

class BridgeFastCallTest {
    @Test fun audioProofMustBeRecentForSameCycleAndCurrentResourcesMustStillBeFree() {
        val p=NativeAudioProof()
        assertFalse(p.valid("a",0,true))
        p.record("a",1000,true)
        assertTrue(p.valid("a",1100,true));assertFalse(p.valid("b",1100,true))
        assertFalse(p.valid("a",1251,true));assertFalse(p.valid("a",999,true))
        p.record("a",2000,true);assertFalse(p.valid("a",2001,false));assertFalse(p.valid("a",2002,true))
        p.record("a",3000,false);assertFalse(p.valid("a",3001,true))
    }
    @Test fun pollingIsFastOnlyDuringVoiceOrShortCommandBurst() {
        assertEquals(2000L,BridgePollPolicy.delay(false,false))
        assertEquals(200L,BridgePollPolicy.delay(true,false))
        assertEquals(200L,BridgePollPolicy.delay(false,true))
    }
    @Test fun onlyExplicitCallMayIgnoreConfirmedMapsPictureInPicture() {
        val target="com.openai.chatgpt"
        fun window(id:Int,owner:String,layer:Int,pip:Boolean=false,focus:Boolean=false)=NativeWindowSelector.Window(id,NativeWindowSelector.Kind.APP,layer,focus,focus,owner,true,pip)
        val chat=window(1,target,1,focus=true);val maps=window(2,"com.google.android.apps.maps",5,pip=true)
        assertNull(NativeWindowSelector.select(listOf(chat,maps),target))
        assertEquals(1,NativeWindowSelector.select(listOf(chat,maps),target,true))
        assertNull(NativeWindowSelector.select(listOf(chat,maps.copy(pip=false)),target,true))
        assertNull(NativeWindowSelector.select(listOf(chat,maps.copy(packageName="other")),target,true))
        assertNull(NativeWindowSelector.select(listOf(chat,maps,window(3,"other",4)),target,true))
        assertTrue(BridgeProtocol.validRequest("4ac0e8f0-19b7-413c-aed8-5e49089e5f18","call_by_name",1000,26000))
    }
}
