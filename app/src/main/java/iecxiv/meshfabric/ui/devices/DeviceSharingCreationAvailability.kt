package iecxiv.meshfabric.ui.devices
internal fun generalSharingCreationEnabled(connected:Boolean,ready:Boolean,rights:Long,revocationPhase:String?,reloaded:Boolean,reviewOpen:Boolean):Boolean=
    connected && ready && !reviewOpen && (revocationPhase==null || revocationPhase=="absent" && reloaded) && ShareAccess.entries.any {generalShareAccessAllowed(rights,it)}
