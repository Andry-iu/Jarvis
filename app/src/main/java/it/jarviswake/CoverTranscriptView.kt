package it.jarviswake

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.MotionEvent
import android.view.View
import kotlin.math.max

/** Session-only text. Passive for single-finger cover gestures; two fingers scroll history. */
internal class CoverTranscriptView(context: Context) : View(context) {
    private val lines = ArrayDeque<String>()
    private val paint = TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(145, 218, 240, 248)
        textSize = 12f * resources.displayMetrics.scaledDensity
    }
    private var layout: StaticLayout? = null
    private var offset = 0f
    private var lastY = 0f
    private var dragging = false
    private var follow = true
    val textCount get() = lines.size
    var shown: Boolean
        get() = visibility == VISIBLE
        set(value) { visibility = if (value) VISIBLE else GONE; invalidate() }
    init {
        isClickable = false; isFocusable = false; isSaveEnabled = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        shown = false // A previous cycle's toggle must never reveal a new conversation automatically.
    }
    fun toggle(): Boolean {
        shown = !shown
        return shown
    }
    fun append(role: String, text: String) {
        require(role in setOf("Dettato", "Jarvis"))
        lines.addLast("$role\n${text.take(8000)}")
        while (lines.size > 20 || lines.sumOf { it.length } > 24000) lines.removeFirst()
        rebuild(); invalidate()
    }
    fun clearText() { lines.clear(); layout = null; offset = 0f; invalidate() }
    private fun dp(n: Int) = n * resources.displayMetrics.density
    private fun available() = (height - dp(160)).coerceAtLeast(1f)
    private fun maximum() = max(0f, (layout?.height ?: 0) - available())
    private fun rebuild() {
        if (width <= dp(32)) return
        val text=lines.joinToString("\n\n")
        layout = StaticLayout.Builder.obtain(text, 0, text.length,
            paint, (width - dp(32)).toInt()).setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).setLineSpacing(dp(2), 1f).build()
        offset = if (follow) maximum() else offset.coerceIn(0f, maximum())
    }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { super.onSizeChanged(w,h,oldw,oldh); rebuild() }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save(); canvas.clipRect(dp(16).toInt(), dp(28).toInt(), (width-dp(16)).toInt(), (height-dp(132)).toInt())
        canvas.translate(dp(16), dp(28)-offset); layout?.draw(canvas); canvas.restore()
    }
    /** Called by the cover container, leaving taps, double taps and one-finger exits unchanged. */
    fun scrollText(event: MotionEvent): Boolean {
        if (!shown || (event.pointerCount < 2 && !dragging)) return false
        if (event.pointerCount >= 2) {
            val y = (event.getY(0) + event.getY(1))/2
            if (!dragging) { dragging = true; lastY = y }
            if (event.actionMasked == MotionEvent.ACTION_MOVE) {
                offset = (offset + lastY-y).coerceIn(0f,maximum()); lastY = y
                follow = maximum()-offset < dp(20); invalidate()
            }
        }
        if (event.actionMasked in setOf(MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL)) dragging = false
        return true
    }
}
