package it.jarviswake

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import java.util.concurrent.atomic.AtomicBoolean

/** Fresh explicit call command owns this temporary HUD; polling never creates or reopens it. */
internal object BridgeCallPreparation {
    private class Pending(val id:String,val service:VoiceAccessibilityService,val view:HolographicOverlayView,val cancelled:AtomicBoolean=AtomicBoolean(false))
    @Volatile private var pending:Pending?=null
    fun allowed(id:String)=pending?.let { it.id==id && !it.cancelled.get() }==true
    fun begin(c:Context,id:String) {
        check(pending==null)
        val service=VoiceAccessibilityService.bridgeService() ?: throw BridgeFailure("unavailable")
        val token=VoiceCycle.id
        VoiceAccessibilityService.releaseBridgeCover()
        val view=HolographicOverlayView(service)
        val p=Pending(id,service,view);pending=p
        fun cancel(home:Boolean) {
            if(!p.cancelled.compareAndSet(false,true)) return
            remove(p)
            if(token!=null) service.requestNativeExit(token,returnHome=home,emergency=!home)
            else if(home) service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
        }
        view.setCoverActions(singleTap={},belowTap={ CoverSettings.setOpaque(c,!CoverSettings.isOpaque(c)) },
            close={cancel(false)},hide={cancel(true)},revealChat={cancel(false)},export={
                p.cancelled.set(true);remove(p)
                DiagnosticExportActivity.open(service,token)
            })
        view.setStatus("PREPARAZIONE TELEFONATA")
        val params=WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT)
            .apply {gravity=Gravity.TOP or Gravity.START}
        try { service.getSystemService(WindowManager::class.java).addView(view,params) }
        catch(e:Exception) { p.cancelled.set(true);pending=null;throw BridgeFailure("unavailable") }
    }
    private fun remove(p:Pending) {
        p.view.visibility=android.view.View.GONE
        runCatching { p.service.getSystemService(WindowManager::class.java).removeViewImmediate(p.view) }
    }
    fun finish(id:String) {
        val p=pending?.takeIf {it.id==id} ?: return
        remove(p);pending=null
    }
}
