package it.jarviswake

import io.grpc.CallOptions
import io.grpc.ClientCall
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.Status
import io.grpc.okhttp.OkHttpChannelBuilder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal interface GoogleRecognitionStream {
    fun audio(samples: ShortArray, size: Int = samples.size)
    fun finish()
    fun cancel()
}
internal interface GoogleStreamTransport {
    fun newCall(method:MethodDescriptor<ByteArray,ByteArray>,options:CallOptions):ClientCall<ByteArray,ByteArray>
    fun close()
}
private class GoogleGrpcTransport:GoogleStreamTransport {
    private val channel=OkHttpChannelBuilder.forAddress("speech.googleapis.com",443).useTransportSecurity()
        .maxInboundMessageSize(262144).build()
    override fun newCall(method:MethodDescriptor<ByteArray,ByteArray>,options:CallOptions)=channel.newCall(method,options)
    override fun close() {channel.shutdownNow()}
}

/** Public v1 StreamingRecognize, TLS + user OAuth. Only final results are delivered once, on close. */
internal class GoogleStreamingRecognizer(config: GoogleVoiceConfig, private val result: (String?, Int) -> Unit,
    private val transport:GoogleStreamTransport=GoogleGrpcTransport(),private val reportStatus:(Int)->Unit={}) : GoogleRecognitionStream {
    private val done = AtomicBoolean(false)
    @Volatile private var halfClosed = false
    private var samples = 0
    private val transcript = StringBuilder()
    private val call: ClientCall<ByteArray,ByteArray>
    init {
        val marshaller = object : MethodDescriptor.Marshaller<ByteArray> {
            override fun stream(value: ByteArray): InputStream = ByteArrayInputStream(value)
            override fun parse(stream: InputStream): ByteArray {
                val out = ByteArrayOutputStream();val block=ByteArray(4096)
                while(true) {val n=stream.read(block);if(n<0) break;require(out.size()+n<=262144);out.write(block,0,n)}
                return out.toByteArray()
            }
        }
        val method=MethodDescriptor.newBuilder<ByteArray,ByteArray>().setType(MethodDescriptor.MethodType.BIDI_STREAMING)
            .setFullMethodName("google.cloud.speech.v1.Speech/StreamingRecognize")
            .setRequestMarshaller(marshaller).setResponseMarshaller(marshaller).build()
        call=transport.newCall(method,CallOptions.DEFAULT.withDeadlineAfter(70,TimeUnit.SECONDS))
        val headers=Metadata().apply {
            put(Metadata.Key.of("authorization",Metadata.ASCII_STRING_MARSHALLER),"Bearer ${config.sttToken}")
            put(Metadata.Key.of("x-goog-user-project",Metadata.ASCII_STRING_MARSHALLER),config.quotaProject)
        }
        try {
            call.start(object:ClientCall.Listener<ByteArray>() {
                override fun onMessage(message:ByteArray) {
                    try {
                        val parts=GoogleStreamingWire.finals(message)
                        synchronized(this@GoogleStreamingRecognizer) {
                            if(!done.get()) for(part in parts) {
                                require(transcript.length+part.length+1<=8000)
                                if(transcript.isNotEmpty()) transcript.append(' ')
                                transcript.append(part)
                            }
                        }
                        call.request(1)
                    } catch(_:Exception) { cancel() }
                    finally {message.fill(0)}
                }
                override fun onClose(status:Status,trailers:Metadata) {
                    if(!done.get())reportStatus(status.code.value())
                    complete(if(status.isOk && halfClosed) synchronized(this@GoogleStreamingRecognizer) {transcript.toString().trim()} else null)
                }
            },headers)
            call.request(1)
            call.sendMessage(GoogleStreamingWire.configuration(config.language))
        } catch(_:Exception) { call.cancel("start_failed",null);complete(null) }
    }
    @Synchronized override fun audio(samples:ShortArray,size:Int) {
        require(size in 1..samples.size && size<=8000) // <=16 kB, below the documented streaming message limit
        if(done.get() || halfClosed) return
        check(this.samples+size<=800000)
        val request=GoogleStreamingWire.audio(samples,size)
        // grpc owns the immutable request until it is serialized; do not mutate it after sendMessage.
        call.sendMessage(request);this.samples+=size
    }
    @Synchronized override fun finish() {
        if(done.get() || halfClosed) return
        halfClosed=true
        try {call.halfClose()} catch(_:Exception) {cancel()}
    }
    override fun cancel() {try {call.cancel("local_cancelled",null)} finally {complete(null)}}
    private fun complete(text:String?) {
        if(!done.compareAndSet(false,true)) return
        val count=synchronized(this) {val n=samples;transcript.setLength(0);n}
        transport.close()
        result(text,count)
    }
}

/** Minimal bounded protobuf codec for the fields in the official v1 proto; skips unknown fields. */
internal object GoogleStreamingWire {
    private fun varint(value:Long):ByteArray {
        var n=value;val out=ByteArrayOutputStream()
        do {var byte=(n and 127).toInt();n=n ushr 7;if(n!=0L)byte=byte or 128;out.write(byte)} while(n!=0L)
        return out.toByteArray()
    }
    private fun number(field:Int,value:Long)=varint((field*8).toLong())+varint(value)
    internal fun bytes(field:Int,value:ByteArray)=varint((field*8+2).toLong())+varint(value.size.toLong())+value
    fun configuration(language:String):ByteArray {
        require(language.matches(Regex("[a-z]{2}-[A-Z]{2}")))
        val config=number(1,1)+number(2,16000)+bytes(3,language.toByteArray(Charsets.UTF_8))+number(11,1)
        // StreamingRecognizeRequest.streaming_config(1).config(1); interim_results false by default.
        return bytes(1,bytes(1,config))
    }
    fun audio(samples:ShortArray,size:Int):ByteArray {
        require(size in 1..samples.size && size<=8000)
        val pcm=ByteArray(size*2)
        for(i in 0 until size) {pcm[i*2]=(samples[i].toInt() and 255).toByte();pcm[i*2+1]=(samples[i].toInt() ushr 8).toByte()}
        return try {bytes(2,pcm)} finally {pcm.fill(0)}
    }
    private class Reader(private val data:ByteArray) {
        var at=0
        fun more()=at<data.size
        fun integer():Long {
            var n=0L
            for(i in 0..9) {require(at<data.size);val b=data[at++].toInt() and 255;require(i<9 || b<=1);n=n or ((b and 127).toLong() shl (i*7));if(b<128)return n}
            error("invalid_protobuf")
        }
        fun blob():ByteArray {val n=integer();require(n in 0..(data.size-at).toLong());return data.copyOfRange(at,at+n.toInt()).also{at+=n.toInt()}}
        fun skip(type:Int) {when(type) {0->integer();1->{require(at+8<=data.size);at+=8};2->blob().fill(0);5->{require(at+4<=data.size);at+=4};else->error("invalid_protobuf")}}
    }
    fun finals(response:ByteArray):List<String> {
        require(response.size<=262144)
        val parts=mutableListOf<String>();val top=Reader(response)
        while(top.more()) {
            val tag=top.integer().toInt();require(tag>0)
            if(tag==10) { // google.rpc.Status error is never relayed verbatim.
                val status=top.blob();val r=Reader(status);var code=0L
                while(r.more()){val t=r.integer().toInt();if(t==8)code=r.integer() else r.skip(t and 7)}
                status.fill(0);require(code==0L)
            } else if(tag==18) {
                val raw=top.blob();val r=Reader(raw);var final=false;var alternative:ByteArray?=null
                try {
                    while(r.more()) {val t=r.integer().toInt();when(t) {10->{val a=r.blob();if(alternative==null)alternative=a else a.fill(0)};16->final=r.integer()==1L;else->r.skip(t and 7)}}
                    if(final && alternative!=null) {
                        val a=Reader(alternative);var text:String?=null
                        while(a.more()) {val t=a.integer().toInt();if(t==10){val b=a.blob();require(b.size<=32000);text=String(b,Charsets.UTF_8);b.fill(0)} else a.skip(t and 7)}
                        text?.takeIf{it.isNotBlank()}?.let {parts.add(it)}
                    }
                } finally {raw.fill(0);alternative?.fill(0)}
            } else top.skip(tag and 7)
        }
        return parts
    }
}
