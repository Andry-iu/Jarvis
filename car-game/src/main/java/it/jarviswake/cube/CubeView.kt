package it.jarviswake.cube

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import kotlin.math.min

internal class CubeView(c: Context, val game: CubeGame) : View(c) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var lastAt = 0L
    private var x = 0f
    private var y = 0f
    var changed: (() -> Unit)? = null
    var running = false
        set(value) { field = value; lastAt = 0; invalidate() }
    var link = CubeLink("missing")
        set(value) { field = value; lastAt = 0; invalidate() }
    private var following = false
    private var connectionLost = false
    val paused get() = connectionLost || (link.code == "linked" && link.visible && link.muted)

    fun updateLink(value: CubeLink) {
        if (value.code == "linked") {
            following = value.visible
            connectionLost = false
        } else if (following) connectionLost = true
        link = value
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.rgb(7, 12, 18))
        val now = SystemClock.elapsedRealtime()
        if (running && hasWindowFocus() && !paused && lastAt > 0) game.spin((now - lastAt) / 1000.0)
        lastAt = now
        val verts = CubeGame.vertices.map(game::transformed)
        val scale = min(width, height).coerceAtLeast(1) * 1.10
        val centerX = width / 2.0; val centerY = height / 2.0
        val projected = verts.map { Pair((centerX + it.x * scale / (4.2 - it.z)).toFloat(), (centerY + it.y * scale / (4.2 - it.z)).toFloat()) }
        val order = CubeGame.faces.indices.sortedBy { i -> CubeGame.faces[i].sumOf { verts[it].z } }
        for (i in order) {
            val normal = game.transformed(CubeGame.normals[i])
            if (normal.z <= 0.02) continue
            val face = CubeGame.faces[i]
            val path = Path()
            face.forEachIndexed { j, v -> val p = projected[v]; if (j == 0) path.moveTo(p.first, p.second) else path.lineTo(p.first, p.second) }
            path.close()
            val bright = (50 + 80 * normal.z).toInt().coerceIn(0, 255)
            paint.style = Paint.Style.FILL
            paint.color = when {
                paused -> Color.rgb(bright, bright, bright)
                link.code == "linked" && link.visible -> Color.rgb(8, bright, (bright + 60).coerceAtMost(255))
                else -> Color.rgb(bright, (bright * 0.65).toInt(), (bright + 70).coerceAtMost(255))
            }
            canvas.drawPath(path, paint)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = resources.displayMetrics.density * 2
            paint.color = when {
                paused -> Color.LTGRAY
                link.code == "linked" && link.visible -> Color.rgb(65, 223, 255)
                else -> Color.rgb(190, 150, 255)
            }
            canvas.drawPath(path, paint)
            paint.style = Paint.Style.FILL; paint.color = Color.WHITE
            paint.textSize = (min(width, height) * 0.105f).coerceAtLeast(1f)
            paint.textAlign = Paint.Align.CENTER
            val cx = face.map { projected[it].first }.average().toFloat()
            val cy = face.map { projected[it].second }.average().toFloat()
            canvas.drawText("${i + 1}", cx, cy - (paint.ascent() + paint.descent()) / 2, paint)
        }
        if (running && hasWindowFocus() && !paused && !game.manual) postInvalidateOnAnimation()
    }
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { x = e.x; y = e.y; parent?.requestDisallowInterceptTouchEvent(true); return true }
            MotionEvent.ACTION_MOVE -> {
                val divisor = min(width, height).coerceAtLeast(80)
                game.rotate((e.x - x) * 5.0 / divisor, (e.y - y) * -5.0 / divisor)
                x = e.x; y = e.y; invalidate(); changed?.invoke(); return true
            }
            MotionEvent.ACTION_UP -> { performClick(); return true }
            MotionEvent.ACTION_CANCEL -> return true
        }
        return false
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) { super.onWindowFocusChanged(hasWindowFocus); lastAt = 0; invalidate() }
}
