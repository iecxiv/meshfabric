package iecxiv.meshfabric.ui.devices
import java.util.Locale
internal enum class DeviceBatchVisualKind { PROGRESS, VERIFIED, WARNING, ERROR, NEUTRAL }
internal fun deviceBatchVisualKind(domain:String,phase:String,code:String=""):DeviceBatchVisualKind {
    val d=domain.lowercase(Locale.ROOT)
    val p=phase.uppercase(Locale.ROOT)
    if(d=="managed" && p=="ERROR") return if(code=="unconfirmed") DeviceBatchVisualKind.WARNING else DeviceBatchVisualKind.NEUTRAL
    if(p=="REJECTED") return DeviceBatchVisualKind.ERROR
    if(p in setOf("UNCONFIRMED","UNKNOWN","CONFLICT")) return DeviceBatchVisualKind.WARNING
    if(d in setOf("tags","move") && p=="VERIFIED" || d=="managed" && p=="REMOVED") return DeviceBatchVisualKind.VERIFIED
    if(p in setOf("CHECKING","LOADING","ELIGIBLE","READY","SENDING","SAVING","MOVING","RUNNING","ACCEPTED","QUEUED","LOCAL_QUEUED","OUTPUT","OBSERVED")) return DeviceBatchVisualKind.PROGRESS
    if(p in setOf("SKIPPED","NOT_SENT","UNCHANGED")) return DeviceBatchVisualKind.NEUTRAL
    return DeviceBatchVisualKind.WARNING
}
