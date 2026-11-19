package iecxiv.meshfabric.ui.theme
internal fun appearanceTimeCanSave(hour:Int,minute:Int,otherMinutes:Int):Boolean =
    hour in 0..23 && minute in 0..59 && otherMinutes in 0..1439 && hour*60+minute!=otherMinutes
internal fun appearanceCityPopupWidth(contentWidth:Float,availableWidth:Float):Float {
    val available=when {
        availableWidth==Float.POSITIVE_INFINITY->480f
        !availableWidth.isFinite()->0f
        else->availableWidth.coerceIn(0f,480f)
    }
    val desired=if(contentWidth.isFinite()) contentWidth.coerceAtLeast(0f)+32f else available
    return minOf(desired,available)
}
