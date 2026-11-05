package it.jarviswake

import io.grpc.CallOptions
import io.grpc.ClientCall
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.Status
import org.junit.Assert.*
import org.junit.Test

class GoogleStreamingRecognizerTest {
    private class FakeTransport:GoogleStreamTransport {
        var closed=0;var method="";var halfClosed=false;var requested=0;var cancelled=0
        lateinit var headers:Metadata
        lateinit var listener:ClientCall.Listener<ByteArray>
        val messages=mutableListOf<ByteArray>()
        override fun close(){closed++}
        override fun newCall(method:MethodDescriptor<ByteArray,ByteArray>,options:CallOptions):ClientCall<ByteArray,ByteArray> {
            this.method=method.fullMethodName;assertNotNull(options.deadline)
            return object:ClientCall<ByteArray,ByteArray>() {
                override fun start(listener:Listener<ByteArray>,headers:Metadata){this@FakeTransport.listener=listener;this@FakeTransport.headers=headers}
                override fun request(n:Int){requested+=n}
                override fun cancel(message:String?,cause:Throwable?){cancelled++;listener.onClose(Status.CANCELLED,Metadata())}
                override fun halfClose(){halfClosed=true}
                override fun sendMessage(message:ByteArray){messages.add(message.clone())}
            }
        }
        fun partial(text:String,final:Boolean) {listener.onMessage(GoogleStreamingWire.bytes(2,
            GoogleStreamingWire.bytes(1,GoogleStreamingWire.bytes(1,text.toByteArray()))+byteArrayOf(16,if(final)1 else 0)))}
        fun complete(status:Status=Status.OK){listener.onClose(status,Metadata())}
    }
    private val config get()=GoogleVoiceConfig("fake-user-access-token-stt","fake-user-access-token-tts",System.currentTimeMillis()+60000,"test-project",
        "https://chatgpt.com/g/g-p-test/c/12345678-1234-4234-8234-123456789abc")
    @Test fun publicStreamSendsConfigFirstThenPreRollAndFramesAndOnlyOneCompleteFinalText() {
        val fake=FakeTransport();val results=mutableListOf<Pair<String?,Int>>()
        val stream=GoogleStreamingRecognizer(config,{text,samples->results.add(text to samples)},fake)
        assertEquals("google.cloud.speech.v1.Speech/StreamingRecognize",fake.method)
        assertEquals("Bearer fake-user-access-token-stt",fake.headers.get(Metadata.Key.of("authorization",Metadata.ASCII_STRING_MARSHALLER)))
        assertEquals("test-project",fake.headers.get(Metadata.Key.of("x-goog-user-project",Metadata.ASCII_STRING_MARSHALLER)))
        assertArrayEquals(GoogleStreamingWire.configuration("it-IT"),fake.messages.single())
        stream.audio(ShortArray(6400){1000});stream.audio(ShortArray(320){2000})
        assertEquals(3,fake.messages.size)
        fake.partial("provvisorio",false);fake.partial("frase uno",true);assertTrue(results.isEmpty())
        fake.partial("frase due",true);stream.finish();assertTrue(fake.halfClosed);assertTrue(results.isEmpty())
        fake.complete();assertEquals(listOf("frase uno frase due" to 6720),results);assertEquals(1,fake.closed)
        fake.complete();stream.finish();stream.audio(ShortArray(320));assertEquals(1,results.size);assertEquals(3,fake.messages.size)
    }
    @Test fun cancellationAndProviderErrorsNeverDeliverPartialOrDuplicateResults() {
        for(status in listOf(Status.CANCELLED,Status.UNAUTHENTICATED,Status.PERMISSION_DENIED,Status.RESOURCE_EXHAUSTED,Status.UNAVAILABLE,Status.DEADLINE_EXCEEDED)) {
            val fake=FakeTransport();val results=mutableListOf<Pair<String?,Int>>()
            val stream=GoogleStreamingRecognizer(config,{text,samples->results.add(text to samples)},fake)
            stream.audio(ShortArray(320));fake.partial("testo incompleto",true);fake.complete(status);stream.cancel()
            assertEquals(listOf(null to 320),results);assertEquals(1,fake.closed)
        }
    }
    @Test fun prematureServerSuccessAndMalformedResponsesFailWithoutSendingAnIncompleteUtterance() {
        val fake=FakeTransport();val results=mutableListOf<String?>()
        val stream=GoogleStreamingRecognizer(config,{text,_->results.add(text)},fake)
        stream.audio(ShortArray(320));fake.partial("prematuro",true);fake.complete()
        assertEquals(listOf(null),results)
        val bad=FakeTransport();val errors=mutableListOf<String?>()
        GoogleStreamingRecognizer(config,{text,_->errors.add(text)},bad)
        bad.listener.onMessage(byteArrayOf(18,127,1));assertEquals(listOf(null),errors);assertEquals(1,bad.cancelled)
    }
}
