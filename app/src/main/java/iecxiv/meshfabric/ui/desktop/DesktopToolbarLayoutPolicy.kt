package iecxiv.meshfabric.ui.desktop

import kotlin.math.roundToInt

internal fun desktopToolbarDiameter(width: Int, height: Int, preferred: Int, margin: Int, gap: Int): Int {
    val available = (width.toLong().coerceAtLeast(0L) - 2L * margin.coerceAtLeast(0) - gap.coerceAtLeast(0)).coerceAtLeast(0L)
    return minOf(preferred.coerceAtLeast(0), height.coerceAtLeast(0), (available / 2L).toInt())
}

internal fun desktopToolbarOrigin(
    width: Int, height: Int, barWidth: Int, barHeight: Int,
    relativeY: Float, margin: Int, collapsed: Boolean, rightToLeft: Boolean
): Pair<Int, Int> {
    val maxX = (width.coerceAtLeast(0) - barWidth.coerceAtLeast(0)).coerceAtLeast(0)
    val maxY = (height.coerceAtLeast(0) - barHeight.coerceAtLeast(0)).coerceAtLeast(0)
    val x = if (!collapsed) maxX / 2 else if (rightToLeft) maxX - margin.coerceAtLeast(0) else margin.coerceAtLeast(0)
    val fraction = if (relativeY.isFinite()) relativeY.coerceIn(0f, 1f) else 0f
    return x.coerceIn(0, maxX) to (maxY * (1f - fraction)).roundToInt().coerceIn(0, maxY)
}

internal fun desktopToolbarStartMouseMode(newSession: Boolean, current: MouseMode): MouseMode =
    if (newSession) MouseMode.LEFT_CLICK else current
