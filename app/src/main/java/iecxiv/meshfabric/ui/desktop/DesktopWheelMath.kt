package iecxiv.meshfabric.ui.desktop

internal data class DesktopWheelPoint(val xFraction: Float, val yFraction: Float) {
    fun remote(width: Int, height: Int): Pair<Int, Int>? {
        if (width <= 0 || height <= 0) return null
        return (xFraction * width).toInt().coerceIn(0, width - 1) to
            (yFraction * height).toInt().coerceIn(0, height - 1)
    }
}

internal data class DesktopWheelGeometry(
    val viewportWidth: Float,
    val viewportHeight: Float,
    val imageWidth: Float,
    val imageHeight: Float,
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float
) {
    fun project(x: Float, y: Float): DesktopWheelPoint? {
        if (listOf(viewportWidth, viewportHeight, imageWidth, imageHeight, scale,
                offsetX, offsetY, x, y).any { !it.isFinite() }) return null
        if (viewportWidth <= 0f || viewportHeight <= 0f || imageWidth <= 0f ||
            imageHeight <= 0f || scale <= 0f) return null
        if (x < 0f || y < 0f || x >= viewportWidth || y >= viewportHeight) return null
        val localX = (x - viewportWidth / 2f - offsetX) / scale + imageWidth / 2f
        val localY = (y - viewportHeight / 2f - offsetY) / scale + imageHeight / 2f
        val fx = localX / imageWidth
        val fy = localY / imageHeight
        if (!fx.isFinite() || !fy.isFinite() || fx < 0f || fy < 0f || fx >= 1f || fy >= 1f) return null
        return DesktopWheelPoint(fx, fy)
    }
}

/** Compose Y is positive down; KVM wheel units are positive up. */
internal class DesktopVerticalWheelAccumulator(private val reverse: Boolean) {
    private var remainder = 0.0
    fun reset() { remainder = 0.0 }
    fun push(composeY: Float): Int {
        if (!composeY.isFinite()) { reset(); return 0 }
        val factor = if (reverse) -1.0 else 1.0
        val total = (remainder - composeY.toDouble() * 120.0 * factor).coerceIn(-32767.0, 32767.0)
        val units = total.toInt()
        remainder = total - units
        return units
    }
}
