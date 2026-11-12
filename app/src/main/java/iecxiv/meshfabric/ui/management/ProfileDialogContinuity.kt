package iecxiv.meshfabric.ui.management
import androidx.compose.runtime.*
internal fun profileDialogSnapshotAllowed(ready:Boolean,loading:Boolean,readError:Boolean,actor:String,receivedActor:String)=ready && !loading && !readError && actor.isNotBlank() && actor==receivedActor
internal fun profileDialogPresentation(current:ProfileState,previous:ProfileState?,actor:String):ProfileState {
    if(profileDialogSnapshotAllowed(current.ready,current.loading,current.readError,actor,current.id)) return current
    if(actor.isBlank() || (current.id.isNotBlank() && current.id!=actor)) return ProfileState(notice=current.notice)
    val snapshot=previous?.takeIf {it.id==actor} ?: return current.copy(ready=false)
    return snapshot.copy(ready=false,loading=true,busy=current.busy,readError=true,notice=current.notice,detailsBusy=false,historyBusy=false)
}
@Composable internal fun rememberProfileDialogPresentation(current:ProfileState,identity:Any,actor:String):ProfileState {
    var previous by remember(identity,actor) {mutableStateOf<ProfileState?>(null)}
    SideEffect {
        if(profileDialogSnapshotAllowed(current.ready,current.loading,current.readError,actor,current.id)) previous=current
        else if(current.id.isNotBlank() && current.id!=actor) previous=null
    }
    return profileDialogPresentation(current,previous,actor)
}
