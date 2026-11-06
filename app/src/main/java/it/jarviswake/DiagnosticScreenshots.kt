package it.jarviswake

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Display
import android.view.PixelCopy
import android.view.WindowManager
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Explicit cover gesture only. Images go to Gallery; the diagnostic ZIP never reads media. */
internal object DiagnosticScreenshots {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { Thread(it,"Jarvis-gallery").apply {isDaemon=true} }
    private const val MAX_PIXELS=6_000_000L
    var windowCapture: (Activity,(Bitmap?,String)->Unit)->Unit = ::pixelCopy
    var displayCapture: (AccessibilityService,(Bitmap?,String)->Unit)->Unit = ::displayCopy
    var galleryWrite:(Context,Bitmap,()->Boolean)->String={c,b,a->save(c.contentResolver,b,a)}
    fun reset() {windowCapture=::pixelCopy;displayCapture=::displayCopy;galleryWrite={c,b,a->save(c.contentResolver,b,a)}}
    fun window(a:Activity,allowed:()->Boolean,done:(String)->Unit) = capture(a,allowed,done) { callback -> windowCapture(a,callback) }
    fun display(s:AccessibilityService,allowed:()->Boolean,done:(String)->Unit) = capture(s,allowed,done) { callback -> displayCapture(s,callback) }
    private fun capture(c:Context,allowed:()->Boolean,done:(String)->Unit,start:((Bitmap?,String)->Unit)->Unit) {
        if(!allowed()) return
        val received=AtomicBoolean(false)
        val timeout=Runnable {if(received.compareAndSet(false,true) && allowed()) done("capture_timeout")}
        main.postDelayed(timeout,2000)
        val callback: (Bitmap?,String)->Unit = { bitmap,code ->
            main.post {
                if(!received.compareAndSet(false,true)) bitmap?.recycle()
                else {
                    main.removeCallbacks(timeout)
                    if(!allowed()) bitmap?.recycle()
                    else if(bitmap==null) done(code)
                    else worker.execute {
                        val outcome=try { galleryWrite(c,bitmap,allowed) } finally {bitmap.recycle()}
                        main.post {if(allowed()) done(outcome)}
                    }
                }
            }
        }
        try {start(callback)} catch(_:Exception) {callback(null,"capture_unavailable")}
    }
    fun notify(c:Context,code:String) {
        Toast.makeText(c,if(code=="saved") "Screenshot salvato nella Galleria · Pictures/JarvisWake" else
            "Screenshot non salvato ($code). La diagnostica testuale resta disponibile.",Toast.LENGTH_LONG).show()
    }
    internal fun save(resolver:ContentResolver,bitmap:Bitmap,allowed:()->Boolean):String {
        if(!allowed()) return "cancelled"
        if(bitmap.width.toLong()*bitmap.height>MAX_PIXELS) return "image_too_large"
        var uri:android.net.Uri?=null
        return try {
            val name="JarvisWake-"+Instant.now().toString().replace(':','-')+".png"
            uri=resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME,name);put(MediaStore.Images.Media.MIME_TYPE,"image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/JarvisWake");put(MediaStore.Images.Media.IS_PENDING,1)
            }) ?: return "gallery_unavailable"
            resolver.openOutputStream(uri,"w")?.use { check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) } ?: error("gallery_unavailable")
            check(allowed())
            check(resolver.update(uri,ContentValues().apply {put(MediaStore.Images.Media.IS_PENDING,0)},null,null)==1)
            "saved"
        } catch(_:Exception) {uri?.let {runCatching {resolver.delete(it,null,null)}};"gallery_failed"}
    }
    private fun pixelCopy(a:Activity,done:(Bitmap?,String)->Unit) {
        if(a !is GoogleVoiceActivity && a !is WebChatActivity && a !is LaunchActivity) {done(null,"window_not_allowed");return}
        if(a.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE!=0) {done(null,"secure_window");return}
        val view=a.window.decorView
        val width=view.width;val height=view.height
        if(width<=0 || height<=0 || width.toLong()*height>MAX_PIXELS) {done(null,"invalid_size");return}
        val bitmap=try {Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)} catch(_:OutOfMemoryError) {done(null,"memory_unavailable");return}
        try {PixelCopy.request(a.window,bitmap,{ code ->
            if(code==PixelCopy.SUCCESS) done(bitmap,"captured")
            else {bitmap.recycle();done(null,"pixel_error_$code")}
        },main)} catch(_:Exception) {bitmap.recycle();done(null,"pixel_unavailable")}
    }
    private fun displayCopy(s:AccessibilityService,done:(Bitmap?,String)->Unit) {
        val target=runCatching {s.windows.firstOrNull {it.type==AccessibilityWindowInfo.TYPE_APPLICATION && it.isActive}?.root?.packageName?.toString()}.getOrNull()
        if(target !in setOf("it.jarviswake","com.openai.chatgpt","com.google.android.apps.maps")) {done(null,"target_unverified");return}
        s.takeScreenshot(Display.DEFAULT_DISPLAY,s.mainExecutor,object:AccessibilityService.TakeScreenshotCallback {
            override fun onSuccess(result:AccessibilityService.ScreenshotResult) {
                val buffer=result.hardwareBuffer
                try {
                    if(buffer.width.toLong()*buffer.height>MAX_PIXELS) {done(null,"image_too_large");return}
                    val hardware=Bitmap.wrapHardwareBuffer(buffer,result.colorSpace)
                    val bitmap=hardware?.copy(Bitmap.Config.ARGB_8888,false);hardware?.recycle()
                    done(bitmap,if(bitmap==null) "display_unavailable" else "captured")
                } catch(_:Exception) {done(null,"display_unavailable")} finally {buffer.close()}
            }
            override fun onFailure(errorCode:Int) {done(null,"display_error_$errorCode")}
        })
    }
}
