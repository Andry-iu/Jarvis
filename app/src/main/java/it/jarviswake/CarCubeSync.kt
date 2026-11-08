package it.jarviswake

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import java.util.WeakHashMap

/** Only these two flags leave Jarvis. No IPC, waits or I/O in the cover's exit path. */
internal object CarCubeSync {
    data class State(val visible: Boolean, val muted: Boolean, val at: Long)
    private val main = Handler(Looper.getMainLooper())
    private val covers = WeakHashMap<View, Boolean>()
    @Volatile private var latest = State(false, true, 0)

    fun coverChanged(view: View, attached: Boolean) {
        main.post {
            if (attached) covers[view] = true else covers.remove(view)
            main.removeCallbacks(sample)
            sample.run()
        }
    }

    private val sample = object : Runnable {
        override fun run() {
            val cover = covers.keys.filterIsInstance<HolographicOverlayView>()
                .firstOrNull { it.isAttachedToWindow && it.isShown && it.windowVisibility == View.VISIBLE }
            latest = State(cover != null, cover?.probeMicrophoneMuted ?: true, SystemClock.elapsedRealtime())
            if (cover != null) main.postDelayed(this, 200L)
        }
    }

    fun current(now: Long = SystemClock.elapsedRealtime()): State {
        val value = latest
        if (!value.visible) return State(false, true, now)
        return if (now < value.at || now - value.at > 1500L) value.copy(visible = false, muted = true) else value
    }
}
