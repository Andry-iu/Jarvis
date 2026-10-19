package it.jarviswake

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Original cinematic HUD animation shown while ChatGPT is being configured. */
class HolographicOverlayView(context: Context) : View(context) {
    private val startedAt = SystemClock.uptimeMillis()
    private val versionName = readVersionName()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cyan = Color.rgb(54, 222, 255)
    private val blue = Color.rgb(30, 116, 255)
    private var voiceActive = false
    private var modelConfirmed = false
    private var statusText = "PREPARAZIONE CHATGPT"
    private var errorText: String? = null
    private val coverPreferences = CoverSettings.preferences(context)
    private var backgroundAlpha = if (CoverSettings.isOpaque(context)) 255 else 128
    private val coverListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == CoverSettings.KEY_OPAQUE || key == null) updateCoverOpacity()
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        coverPreferences.registerOnSharedPreferenceChangeListener(coverListener)
        updateCoverOpacity()
    }

    override fun onDetachedFromWindow() {
        coverPreferences.unregisterOnSharedPreferenceChangeListener(coverListener)
        super.onDetachedFromWindow()
    }

    private fun updateCoverOpacity() {
        backgroundAlpha = if (CoverSettings.isOpaque(context)) 255 else 128
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val seconds = (SystemClock.uptimeMillis() - startedAt) / 1000f
        // Replace each frame's background so translucency never accumulates to opaque.
        canvas.drawColor(Color.argb(backgroundAlpha, 2, 8, 15), PorterDuff.Mode.SRC)

        val cx = width / 2f
        val cy = height * 0.43f
        val base = minOf(width, height) * 0.24f
        val pulse = if (voiceActive) {
            1f + abs(sin(seconds * 7.1f)) * 0.10f + abs(sin(seconds * 13.3f)) * 0.045f
        } else {
            1f + 0.025f * sin(seconds * 2.8f)
        }

        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.setShadowLayer(18f, 0f, 0f, cyan)

        drawRing(canvas, cx, cy, base * 1.18f, 2.2f, 35, seconds * 24f, 0.34f)
        drawRing(canvas, cx, cy, base, 4.5f, 12, -seconds * 42f, 0.78f)
        drawRing(canvas, cx, cy, base * 0.78f, 2.5f, 7, seconds * 58f, 0.58f)

        paint.color = withAlpha(cyan, 0.52f)
        paint.strokeWidth = 2f
        canvas.drawCircle(cx, cy, base * 0.60f * pulse, paint)
        canvas.drawCircle(cx, cy, base * 0.48f, paint)

        for (index in 0 until 48) {
            val angle = index * 2.0 * PI / 48.0 + seconds * 0.15
            val inner = base * if (index % 4 == 0) 1.25f else 1.29f
            val outer = base * if (index % 4 == 0) 1.39f else 1.35f
            paint.color = withAlpha(if (index % 3 == 0) cyan else blue, 0.52f)
            paint.strokeWidth = if (index % 4 == 0) 3f else 1.5f
            canvas.drawLine(
                cx + cos(angle).toFloat() * inner,
                cy + sin(angle).toFloat() * inner,
                cx + cos(angle).toFloat() * outer,
                cy + sin(angle).toFloat() * outer,
                paint
            )
        }

        paint.style = Paint.Style.FILL
        for (index in 0 until 10) {
            val angle = seconds * (0.42f + index * 0.018f) + index * 0.63f
            val radius = base * (0.68f + (index % 3) * 0.21f)
            paint.color = withAlpha(cyan, 0.35f + (index % 4) * 0.10f)
            canvas.drawCircle(
                cx + cos(angle.toDouble()).toFloat() * radius,
                cy + sin(angle.toDouble()).toFloat() * radius,
                3f + index % 3,
                paint
            )
        }

        val coreX = cx + if (voiceActive) sin(seconds * 29f) * 2.4f else 0f
        val coreY = cy + if (voiceActive) cos(seconds * 23f) * 2.0f else 0f
        if (voiceActive) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2.5f
            repeat(3) { index ->
                val progress = (seconds * 0.85f + index / 3f) % 1f
                paint.color = withAlpha(cyan, 0.26f * (1f - progress))
                canvas.drawCircle(coreX, coreY, base * (0.22f + progress * 0.36f), paint)
            }
            paint.style = Paint.Style.FILL
        }

        paint.setShadowLayer(28f, 0f, 0f, cyan)
        paint.color = withAlpha(cyan, 0.20f)
        canvas.drawCircle(coreX, coreY, base * 0.20f * pulse, paint)
        paint.color = withAlpha(cyan, 0.92f)
        canvas.drawCircle(coreX, coreY, base * 0.055f * pulse, paint)
        paint.clearShadowLayer()

        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        paint.textSize = width * 0.071f
        paint.letterSpacing = 0.12f
        paint.color = Color.WHITE
        canvas.drawText("J.A.R.V.I.S.", cx, cy + base * 1.78f, paint)

        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        paint.textSize = width * 0.030f
        paint.letterSpacing = 0.12f
        paint.color = withAlpha(cyan, 0.82f)
        canvas.drawText("VERSIONE $versionName", cx, cy + base * 2.02f, paint)

        // Status notes are useful only while setup is in progress. Keep the
        // HUD clean after Voice is assumed active, leaving its identity and version.
        if (!voiceActive) {
            paint.textSize = width * 0.030f
            paint.letterSpacing = 0.055f
            paint.color = when {
                errorText != null -> Color.rgb(255, 105, 105)
                modelConfirmed -> Color.rgb(132, 255, 205)
                else -> withAlpha(cyan, 0.70f)
            }
            canvas.drawText(
                errorText ?: if (modelConfirmed) "MODELLO WORK: 6 LUNA ✓" else statusText,
                cx,
                cy + base * 2.28f,
                paint
            )

            val activeDot = ((seconds * 3f).toInt() % 4)
            for (index in 0 until 4) {
                paint.color = withAlpha(cyan, if (index == activeDot) 0.95f else 0.22f)
                canvas.drawCircle(cx + (index - 1.5f) * 26f, cy + base * 2.50f, 5f, paint)
            }
        }

        postInvalidateOnAnimation()
    }

    fun setVoiceActive() {
        voiceActive = true
        invalidate()
    }

    fun setStatus(value: String) {
        statusText = value
        invalidate()
    }

    fun setModelConfirmed() {
        modelConfirmed = true
        invalidate()
    }

    fun setError(value: String) {
        errorText = value
        invalidate()
    }

    private fun readVersionName(): String = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0)
            ).versionName
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: ""

    private fun drawRing(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        stroke: Float,
        segments: Int,
        rotation: Float,
        alpha: Float
    ) {
        paint.strokeWidth = stroke
        paint.color = withAlpha(cyan, alpha)
        val bounds = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        val sweep = 360f / segments
        for (index in 0 until segments) {
            val length = sweep * if (index % 3 == 0) 0.70f else 0.38f
            canvas.drawArc(bounds, rotation + index * sweep, length, false, paint)
        }
    }

    private fun withAlpha(color: Int, alpha: Float): Int =
        Color.argb((alpha.coerceIn(0f, 1f) * 255).toInt(), Color.red(color), Color.green(color), Color.blue(color))
}
