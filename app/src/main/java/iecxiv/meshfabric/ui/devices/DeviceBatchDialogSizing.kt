package iecxiv.meshfabric.ui.devices
internal fun deviceBatchDialogWidth(available:Float):Float = when {
    available==Float.POSITIVE_INFINITY -> 640f
    !available.isFinite() -> 0f
    else -> available.coerceIn(0f,640f)
}
