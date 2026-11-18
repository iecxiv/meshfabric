package iecxiv.meshfabric.ui.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal class DesktopRightClickArm(visible: Boolean = false) {
    var visible by mutableStateOf(visible)
        private set
    var armed by mutableStateOf(false)
        private set
    fun show(value: Boolean) { visible = value; armed = false }
    fun toggle() { if (visible) armed = !armed }
    fun cancel() { armed = false }
    fun consume(): Boolean {
        if (!visible || !armed) return false
        armed = false
        return true
    }
}

internal data class DesktopAssistBounds(val left: Int, val top: Int, val right: Int, val bottom: Int)

internal fun desktopAssistPosition(
    width: Int, height: Int, size: Int, bar: DesktopAssistBounds,
    desiredX: Float, desiredY: Float, margin: Int, gap: Int
): Pair<Int, Int>? {
    val maxX = width - margin - size
    val maxY = height - margin - size
    if (size <= 0 || maxX < margin || maxY < margin) return null
    val fx = desiredX.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 1f
    val fy = desiredY.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
    val dx = margin + ((maxX - margin) * fx).toInt()
    val dy = margin + ((maxY - margin) * fy).toInt()
    val xs = listOf(dx, margin, maxX, bar.left - gap - size, bar.right + gap).map { it.coerceIn(margin, maxX) }.distinct()
    val ys = listOf(dy, margin, maxY, bar.top - gap - size, bar.bottom + gap).map { it.coerceIn(margin, maxY) }.distinct()
    val free = mutableListOf<Pair<Int, Int>>()
    for (x in xs) for (y in ys) {
        if (x + size <= bar.left - gap || x >= bar.right + gap ||
            y + size <= bar.top - gap || y >= bar.bottom + gap) free += x to y
    }
    return free.minByOrNull { (x, y) ->
        val a = (x - dx).toDouble(); val b = (y - dy).toDouble(); a * a + b * b
    }
}
