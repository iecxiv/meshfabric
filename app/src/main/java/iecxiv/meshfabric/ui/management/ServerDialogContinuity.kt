package iecxiv.meshfabric.ui.management
import androidx.compose.runtime.*
internal data class ServerReadDialogResult(val raw:String,val action:String)
internal fun nextServerReadDialog(previous:ServerReadDialogResult?,raw:String,action:String):ServerReadDialogResult?=
    if(raw.isNotBlank() && action in setOf("serverversion","servererrors","serverconfig")) ServerReadDialogResult(raw,action) else previous
@Composable internal fun rememberServerReadDialog(raw:String,action:String,identity:Any):MutableState<ServerReadDialogResult?> {
    val result=remember(identity) {mutableStateOf<ServerReadDialogResult?>(null)}
    LaunchedEffect(identity,raw,action) {result.value=nextServerReadDialog(result.value,raw,action)}
    return result
}
