package it.jarviswake

/** Shared hit regions follow the animated rings in both cover windows. */
internal object CoverTapTarget {
    const val NONE = 0
    const val CENTER = 1
    const val EXPORT = 2
    const val BELOW = 3

    fun at(x: Float, y: Float, width: Int, height: Int, opacityBottom: Float = height.toFloat()): Int {
        if (width <= 0 || height <= 0 || x !in 0f..width.toFloat() || y !in 0f..height.toFloat()) return NONE
        if (x > width * 0.78f && y < height * 0.16f) return EXPORT
        val cx = width / 2f
        val cy = height * 0.43f
        val radius = minOf(width, height) * 0.34f
        if ((x - cx) * (x - cx) + (y - cy) * (y - cy) <= radius * radius) return CENTER
        val belowEnd = minOf(cy + minOf(width, height) * 0.60f, opacityBottom)
        return if (y > cy + radius && y < belowEnd) BELOW else NONE
    }
}
