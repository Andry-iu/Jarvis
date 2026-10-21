package it.jarviswake

/** Stores the per-session Voice toggle location independently of display resolution. */
internal class VoiceToggleTarget private constructor(
    private val xFraction: Float,
    private val yFraction: Float
) {
    fun forSize(width: Int, height: Int): FloatArray? {
        if (width <= 0 || height <= 0) return null
        return floatArrayOf(xFraction * width, yFraction * height)
    }

    companion object {
        fun capture(x: Float, y: Float, width: Int, height: Int): VoiceToggleTarget? {
            if (width <= 0 || height <= 0 || x !in 0f..width.toFloat() || y !in 0f..height.toFloat()) return null
            return VoiceToggleTarget(x / width, y / height)
        }
    }
}
