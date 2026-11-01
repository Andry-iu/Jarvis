package it.jarviswake

import android.content.Context
import android.os.SystemClock
import org.json.JSONObject
import java.io.File

internal interface WhatsAppEngine {
    fun connect(pair:Boolean)
    fun state():String
    fun unlink():String
    fun send(number:String,text:String,id:String,timeout:Long,allowed:()->Boolean):String
}
private class NativeWhatsAppEngine(c:Context):WhatsAppEngine {
    private val native:wabridge.Engine
    init {
        go.Seq.setContext(c.applicationContext)
        native=wabridge.Wabridge.open(File(c.applicationContext.noBackupFilesDir,"whatsapp-linked").absolutePath)
    }
    override fun connect(pair:Boolean)=native.connect(pair)
    override fun state()=native.state()
    override fun unlink()=native.unlink()
    override fun send(number:String,text:String,id:String,timeout:Long,allowed:()->Boolean):String {
        val gateAllowed=allowed
        return native.send(number,text,id,timeout,object:wabridge.Gate {
            override fun allowed()=runCatching { gateAllowed() }.getOrDefault(false)
        })
    }
}

/** Linked-device credentials stay in app-private noBackup storage, never in the relay or diagnostics. */
internal object PhoneWhatsApp {
    @Volatile private var engine:WhatsAppEngine?=null
    internal var factory:(Context)->WhatsAppEngine = { NativeWhatsAppEngine(it) }
    @Volatile private var failed=false
    @Synchronized private fun open(c:Context):WhatsAppEngine {
        engine?.let { return it }
        return factory(c.applicationContext).also { engine=it;failed=false }
    }
    fun connect(c:Context,pair:Boolean) {
        Thread({ runCatching { open(c).connect(pair) }.onFailure { failed=true } },"Jarvis-whatsapp-connect").start()
    }
    /** Only the local QR screen requests private QR material. Other callers receive status alone. */
    fun state(privateQr:Boolean=false):JSONObject = runCatching {
        val state=engine?.state()?.let(::JSONObject) ?: JSONObject().put("state",if(failed) "engine_unavailable" else "not_started")
        if(!privateQr) { state.remove("qr");state.remove("expires") }
        state
    }.getOrElse { JSONObject().put("state","engine_unavailable") }
    fun unlink(c:Context,done:(String)->Unit) {
        Thread({ val result=runCatching { open(c).unlink() }.getOrDefault("logout_unconfirmed");done(result) },"Jarvis-whatsapp-unlink").start()
    }
    fun send(c:Context,number:String,text:String,id:String,deadline:Long,allowed:()->Boolean):JSONObject {
        if(!allowed()) return result("cancelled")
        val remaining=minOf(18_000L,deadline-SystemClock.elapsedRealtime())
        if(remaining<=0) return result("expired_before_dispatch")
        return runCatching {
            val native=open(c)
            result(native.send(number,text,id,remaining,allowed))
        }.getOrElse { result("whatsapp_connection_unavailable") }
    }
}
