package it.jarviswake

import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

/** Request-owned, single MOVE_WINDOW. Never dismiss Maps or guess system/private action IDs. */
internal class BridgeMapsPip {
    private var attempted=false
    private var accepted=false
    private var original:Rect?=null
    private var windowId:Int?=null
    private var reported:String?=null
    fun prepare(service:VoiceAccessibilityService,token:String,windows:List<AccessibilityWindowInfo>):Boolean {
        fun report(code:String) { if(reported!=code) {reported=code;NativeRuntimeSnapshot.save(service,token,"bridge_pip","state=$code")} }
        val pips=windows.filter {it.isInPictureInPictureMode}
        if(pips.isEmpty()) return true
        if(windows.any {it.type==AccessibilityWindowInfo.TYPE_SYSTEM && (it.isActive || it.isFocused)}) return false
        val pip=pips.singleOrNull() ?: return false
        val root=runCatching {pip.root}.getOrNull() ?: return false
        if(root.packageName?.toString()!="com.google.android.apps.maps") return false
        val target=windows.filter {it.type==AccessibilityWindowInfo.TYPE_APPLICATION && !it.isInPictureInPictureMode}
            .maxByOrNull {it.layer} ?: return false
        if(target.root?.packageName?.toString()!=NativeChatSettings.PACKAGE) return false
        val bounds=Rect().also(pip::getBoundsInScreen)
        if(attempted) {
            val moved=accepted && windowId==pip.id && original?.let {it!=bounds}==true
            report(if(moved) "moved" else "move_unconfirmed")
            return moved
        }
        if(root.actionList.none {it.id==AccessibilityNodeInfo.AccessibilityAction.ACTION_MOVE_WINDOW.id}) {report("move_unavailable");return false}
        val area=Rect().also(target::getBoundsInScreen)
        val density=service.resources.displayMetrics.density
        val x=area.left+(16*density).toInt();val y=area.top+(64*density).toInt()
        if(bounds.isEmpty || area.isEmpty || x+bounds.width()>area.right || y+bounds.height()>area.bottom) {report("bounds_unavailable");return false}
        original=Rect(bounds);windowId=pip.id;attempted=true
        // Already at the intended safe corner: no action required, but stop overlap is still checked.
        if(kotlin.math.abs(bounds.left-x)<8*density && kotlin.math.abs(bounds.top-y)<8*density) {
            accepted=true;original=Rect();report("already_positioned");return true
        }
        accepted=runCatching {root.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_MOVE_WINDOW.id,Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_MOVE_WINDOW_X,x);putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_MOVE_WINDOW_Y,y)
        })}.getOrDefault(false)
        report(if(accepted) "move_dispatched" else "move_rejected")
        return false // Dispatch is not proof of new geometry; observe a subsequent window snapshot.
    }
    fun stopUncovered(windows:List<AccessibilityWindowInfo>,stop:AccessibilityNodeInfo):Boolean {
        val stopBounds=Rect().also(stop::getBoundsInScreen)
        if(stopBounds.isEmpty) return false
        return windows.filter {it.isInPictureInPictureMode}.none {
            Rect.intersects(stopBounds,Rect().also(it::getBoundsInScreen))
        }
    }
}
