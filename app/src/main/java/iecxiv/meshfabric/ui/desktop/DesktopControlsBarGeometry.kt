package iecxiv.meshfabric.ui.desktop

internal fun desktopControlsBarLimit(viewportHeightPx:Float,barHeightPx:Int,bottomMarginPx:Float):Float {
    if(!viewportHeightPx.isFinite() || !bottomMarginPx.isFinite() || viewportHeightPx<=0f || barHeightPx<=0 || bottomMarginPx<0f) return 0f
    return -(viewportHeightPx-barHeightPx-bottomMarginPx).coerceAtLeast(0f)
}
