package it.jarviswake

import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.telephony.PhoneNumberUtils
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import org.json.JSONObject

/** One bounded UI attempt in the explicitly addressed WhatsApp chat. No resource IDs or coordinates. */
internal class WhatsAppFlow private constructor(private val service:VoiceAccessibilityService,
    private val contact:BridgeContact,private val text:String,private val deadline:Long,
    private val allowed:()->Boolean,private val completed:(JSONObject)->Unit) {
    companion object {
        private var current:WhatsAppFlow?=null
        fun start(s:VoiceAccessibilityService,c:BridgeContact,text:String,deadline:Long,allowed:()->Boolean,done:(JSONObject)->Unit) {
            if(current!=null) throw BridgeFailure("unavailable")
            current=WhatsAppFlow(s,c,text,minOf(deadline,SystemClock.elapsedRealtime()+11000),allowed,done).also { it.handler.post(it.poll) }
        }
        fun cancel() { current?.finish("cancelled") }
    }
    private val handler=Handler(Looper.getMainLooper())
    private var written=false
    private var sent=false
    private var sentAt=0L
    private var fieldWindow:Int?=null
    private var candidate:Pair<Int,Rect>?=null
    private var stableAt=0L
    private var reason="recipient_unverified"
    private val poll=object:Runnable {
        override fun run() {
            if(current!==this@WhatsAppFlow) return
            if(!allowed() || VoiceAccessibilityService.bridgeService()!==service) { finish("cancelled");return }
            if(SystemClock.elapsedRealtime()>=deadline) { finish(if(sent) "send_dispatched_unconfirmed" else reason);return }
            runCatching { inspect() }.onFailure { finish("outcome_uncertain") }
            if(current===this@WhatsAppFlow) handler.postDelayed(this,200)
        }
    }
    private fun finish(code:String) {
        if(current!==this) return
        current=null;handler.removeCallbacks(poll)
        runCatching { completed(result(code)) }
    }
    @Suppress("DEPRECATION")
    private fun inspect() {
        val windows=service.windows
        val roots=windows.associate { it.id to runCatching { it.root }.getOrNull() }
        val nodes=ArrayList<AccessibilityNodeInfo>()
        try {
            val descriptors=windows.map { w -> NativeWindowSelector.Window(w.id,when(w.type) {
                AccessibilityWindowInfo.TYPE_APPLICATION -> NativeWindowSelector.Kind.APP
                AccessibilityWindowInfo.TYPE_INPUT_METHOD -> NativeWindowSelector.Kind.IME
                AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY -> NativeWindowSelector.Kind.OVERLAY
                AccessibilityWindowInfo.TYPE_SYSTEM -> NativeWindowSelector.Kind.SYSTEM
                else -> NativeWindowSelector.Kind.OTHER
            },w.layer,w.isActive,w.isFocused,roots[w.id]?.packageName?.toString(),roots[w.id]?.isVisibleToUser ?: true) }
            val windowId=NativeWindowSelector.select(descriptors,"com.whatsapp") ?: return
            val root=roots[windowId] ?: return
            if(fieldWindow!=null && fieldWindow!=windowId) { finish("recipient_unverified");return }
            val bounds=Rect().also { windows.first { w -> w.id==windowId }.getBoundsInScreen(it) }
            var incomplete=false
            fun visit(node:AccessibilityNodeInfo,depth:Int) {
                if(nodes.size>=800 || depth>25) { incomplete=true;return }
                nodes.add(node)
                if(!node.isVisibleToUser || node.isEditable || node.isPassword) return
                for(i in 0 until node.childCount) node.getChild(i)?.let { visit(it,depth+1) }
            }
            visit(root,0)
            if(incomplete) { reason="composer_unavailable";return }
            val density=service.resources.displayMetrics.density
            val expected=NativeVoiceLabels.normalize(contact.name)
            val headerMatches=nodes.any { node ->
                if(node.isEditable || node.isPassword || !node.isVisibleToUser) false else {
                    val r=Rect().also(node::getBoundsInScreen)
                    val inHeader=r.top>=bounds.top && r.bottom<=bounds.top+96*density && r.width()<bounds.width()
                    if(!inHeader) false else listOfNotNull(node.text?.toString(),node.contentDescription?.toString()).any { label ->
                        (contact.uniqueName && expected.isNotEmpty() && NativeVoiceLabels.normalize(label)==expected) ||
                            (label.any(Char::isDigit) && label.all { it.isDigit() || it in "+ ()-" } &&
                                PhoneNumberUtils.normalizeNumber(label).let { n -> n==contact.international || n==contact.number })
                    }
                }
            }
            if(!headerMatches) { reason="recipient_unverified";return }
            val editors=nodes.filter { it.packageName?.toString()=="com.whatsapp" && it.isEditable && !it.isPassword && it.isVisibleToUser && it.isEnabled }
            if(editors.size!=1) { reason="composer_unavailable";return }
            val editor=editors.single()
            val value=if(editor.isShowingHintText) "" else editor.text?.toString() ?: return
            if(sent) {
                if(value.isEmpty()) finish("composer_cleared_after_send")
                else if(SystemClock.elapsedRealtime()-sentAt>2000) finish("send_dispatched_unconfirmed")
                return
            }
            if(!written) {
                if(value.isNotEmpty()) { finish("draft_exists");return }
                if(editor.actionList.none { it.id==AccessibilityNodeInfo.ACTION_SET_TEXT }) { reason="composer_unavailable";return }
                if(!allowed() || SystemClock.elapsedRealtime()>=deadline) { finish("cancelled");return }
                written=true;fieldWindow=windowId
                val accepted=editor.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,text)
                })
                if(!accepted) finish("write_rejected")
                return
            }
            if(value!=text) { if(value.isNotEmpty()) finish("outcome_uncertain");return }
            val sends=NativeControlResolver.targets(nodes,setOf("invia","send","invia messaggio","send message"),
                labels={ n -> listOfNotNull(n.contentDescription?.toString(),n.text?.toString()?.takeIf { n.className?.toString()?.contains("Button")==true })
                    .map(NativeVoiceLabels::normalize) },
                parent={ it.parent?.takeIf { n -> n.packageName?.toString()=="com.whatsapp" } },
                visible={it.isVisibleToUser},enabled={it.isEnabled},clickable={it.isClickable})
            val field=Rect().also(editor::getBoundsInScreen)
            val matching=sends.filter { n ->
                val r=Rect().also(n::getBoundsInScreen)
                r.centerX()>field.centerX() && kotlin.math.abs(r.centerY()-field.centerY())<80*density
            }
            if(matching.size!=1) { candidate=null;reason="send_unavailable";return }
            val target=matching.single();val r=Rect().also(target::getBoundsInScreen);val key=windowId to r
            if(candidate!=key) { candidate=key;stableAt=SystemClock.elapsedRealtime();return }
            if(!BridgeActionPolicy.maySend(headerMatches,editors.size,value==text,matching.size,SystemClock.elapsedRealtime()-stableAt>=250,sent)) return
            if(!allowed() || SystemClock.elapsedRealtime()>=deadline) { finish("cancelled");return }
            sent=true;sentAt=SystemClock.elapsedRealtime()
            if(!target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) finish("send_rejected")
        } finally {
            // Private transient nodes only: never exported, logged or retained across observations.
            nodes.distinctBy { System.identityHashCode(it) }.forEach { runCatching { it.recycle() } }
            roots.values.filterNotNull().filterNot { r -> nodes.any { it===r } }.forEach { runCatching { it.recycle() } }
        }
    }
}
