package it.jarviswake

import org.junit.Assert.*
import org.junit.Test

class GoogleVoicePolicyTest {
    @Test fun vadPreservesPreRollAndEndsOnlyAfterConfiguredPause() {
        val vad=LocalSpeechBuffer(silenceMs=400,threshold=600)
        repeat(17) { assertNull(vad.accept(ShortArray(320){100},320)) }
        repeat(3) { assertNull(vad.accept(ShortArray(320){2000},320)) }
        assertTrue(vad.speaking)
        repeat(19) { assertNull(vad.accept(ShortArray(320),320)) }
        val phrase=vad.accept(ShortArray(320),320)!!
        assertEquals(12800,phrase.size) // 400 ms ring including onset + 400 ms ending pause
        assertEquals(100.toShort(),phrase.first());assertEquals(2000.toShort(),phrase[6399])
        assertNull(vad.accept(ShortArray(320){3000},320))
    }
    @Test fun transientDoesNotTriggerAndMaximumBoundsContinuousSpeech() {
        val vad=LocalSpeechBuffer(maximumMs=5000)
        assertNull(vad.accept(ShortArray(320){4000},320));assertFalse(vad.speaking)
        assertNull(vad.accept(ShortArray(320),320));assertFalse(vad.speaking)
        var result:ShortArray?=null
        repeat(260) { val value=vad.accept(ShortArray(320){2000},320);if(value!=null)result=value }
        assertEquals(80000,result!!.size)
    }
    @Test fun localSilenceNeverProducesACloudRequestAndClearErasesPreRoll() {
        val vad=LocalSpeechBuffer();repeat(500) { assertNull(vad.accept(ShortArray(320),320)) };assertFalse(vad.speaking)
        vad.accept(ShortArray(320){111},320);vad.clear()
        repeat(3) { vad.accept(ShortArray(320){2222},320) }
        assertTrue(vad.speaking)
    }
    @Test fun turnAcceptsOnlyOneReplyForCurrentSessionAndNeverAfterCloseOrError() {
        val s=GoogleTurnState("session")
        assertFalse(s.acceptReply("session","t"));assertTrue(s.begin("t"));assertFalse(s.begin("duplicate"))
        assertTrue(s.submitting());assertFalse(s.submitting());s.dispatched()
        assertFalse(s.acceptReply("old-session","t"));assertFalse(s.acceptReply("session","old-turn"))
        s.observed();assertTrue(s.acceptReply("session","t"));assertFalse(s.acceptReply("session","t"))
        s.ready();assertTrue(s.begin("next"));s.submitting();s.dispatched();s.fail();assertFalse(s.acceptReply("session","next"))
        s.close();assertFalse(s.begin("late"));s.ready();assertEquals("closed",s.phase)
    }
    @Test fun bindsExistingProjectConversationAndRejectsGenericNewChatAndLookalikeOrigins() {
        val path="/g/g-p-abc123/c/12345678-1234-4234-8234-123456789abc"
        assertTrue(GoogleConversation.valid("https://chatgpt.com$path"))
        for(u in listOf("https://chatgpt.com/g/g-p-abc123/project","https://chatgpt.com/c/12345678-1234-4234-8234-123456789abc",
            "https://chatgpt.com.evil$path","https://chatgpt.com$path?share=true","https://user@chatgpt.com$path","http://chatgpt.com$path")) assertFalse(u,GoogleConversation.valid(u))
    }
    @Test fun wavParserRejectsCompressedAndTruncatedDataAndStripsHeader() {
        val b=java.nio.ByteBuffer.allocate(48).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(40).put("WAVEfmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(24000)
            .putInt(48000).putShort(2).putShort(16).put("data".toByteArray()).putInt(4).putInt(123)
        assertArrayEquals(byteArrayOf(123,0,0,0),GoogleWave.pcm(b.array()))
        b.putShort(20,3)
        assertThrows(IllegalArgumentException::class.java){GoogleWave.pcm(b.array())}
        assertThrows(IllegalArgumentException::class.java){GoogleWave.pcm(ByteArray(20))}
    }
    @Test fun streamingWireMatchesOfficialConfigAndPcmFieldsWithoutSendingInterimText() {
        assertArrayEquals(byteArrayOf(10,16,10,14,8,1,16,0x80.toByte(),0x7d,26,5,105,116,45,73,84,88,1),GoogleStreamingWire.configuration("it-IT"))
        assertArrayEquals(byteArrayOf(18,4,0,0x80.toByte(),0xff.toByte(),0x7f),GoogleStreamingWire.audio(shortArrayOf(-32768,32767),2))
        fun response(text:String,final:Boolean):ByteArray = GoogleStreamingWire.bytes(2,
            GoogleStreamingWire.bytes(1,GoogleStreamingWire.bytes(1,text.toByteArray()))+byteArrayOf(16,if(final)1 else 0))
        assertTrue(GoogleStreamingWire.finals(response("parziale",false)).isEmpty())
        assertEquals(listOf("frase uno","frase due"),GoogleStreamingWire.finals(response("frase uno",true)+response("frase due",true)))
        assertThrows(IllegalArgumentException::class.java){GoogleStreamingWire.finals(byteArrayOf(18,16,1))}
        assertThrows(IllegalArgumentException::class.java){GoogleStreamingWire.finals(GoogleStreamingWire.bytes(1,byteArrayOf(8,7)))}
    }
    @Test fun currentCycleRouteBindsOnlyConfiguredProjectAndValidatesDirectChatProofSeparately() {
        val project="https://chatgpt.com/g/g-p-1234567890abcdef1234567890abcdef-jarvisruntime/project"
        val chat="https://chatgpt.com/g/g-p-1234567890abcdef1234567890abcdef/c/12345678-1234-4234-8234-123456789abc"
        assertTrue(GoogleSessionRoute.projectPage(project,project));assertEquals(chat,GoogleSessionRoute.conversation(chat,project))
        assertNull(GoogleSessionRoute.conversation(chat.replace("90abcdef/c","90abcdee/c"),project))
        val direct="https://chatgpt.com/c/12345678-1234-4234-8234-123456789abc"
        assertNull(GoogleSessionRoute.conversation(direct,project));assertEquals(direct,GoogleSessionRoute.conversation(direct,project,true))
        for(raw in listOf(chat+"#wrong",chat+"?share=true",chat.replace("chatgpt.com","chatgpt.com.evil"),chat.replace("/c/","/%63/")))assertNull(GoogleSessionRoute.conversation(raw,project,true))
    }

}
