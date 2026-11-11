package iecxiv.meshfabric.ui.management
internal fun retainEventDialog(phase:ServerEventsPhase,lifecyclePause:Boolean,enabled:Boolean,matches:Int):Boolean {
    if(!enabled) return false
    return when(phase) {
        ServerEventsPhase.READY->matches==1
        ServerEventsPhase.INVALIDATED,ServerEventsPhase.LOADING->lifecyclePause
        else->false
    }
}
