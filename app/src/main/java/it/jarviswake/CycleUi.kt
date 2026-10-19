package it.jarviswake

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.TextView

internal object CycleUi {
    fun closeButton(context: Context, close: () -> Unit) = TextView(context).apply {
        text = "×"
        textSize = 34f
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        contentDescription = "Chiudi Jarvis e ChatGPT"
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.argb(230, 8, 28, 40))
            setStroke((context.resources.displayMetrics.density).toInt().coerceAtLeast(1), Color.rgb(54, 222, 255))
        }
        isFocusable = true
        setOnClickListener { close() }
    }
}
