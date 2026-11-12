package iecxiv.meshfabric.ui.management
import androidx.compose.runtime.*
internal class GroupDetailsLoadGate {
    private var requestedGeneration:Long?=null
    fun claim(generation:Long,ready:Boolean,loading:Boolean,busy:Boolean,readError:Boolean,available:Boolean):Boolean {
        if(!ready || loading || busy || readError || !available || requestedGeneration==generation) return false
        requestedGeneration=generation
        return true
    }
}
@Composable internal fun GroupDetailsAutoLoad(owner:Any,id:String,generation:Long,ready:Boolean,loading:Boolean,busy:Boolean,readError:Boolean,available:Boolean,onLoad:()->Unit) {
    val gate=remember(owner,id) {GroupDetailsLoadGate()}
    val callback by rememberUpdatedState(onLoad)
    LaunchedEffect(gate,generation,ready,loading,busy,readError,available) {
        if(gate.claim(generation,ready,loading,busy,readError,available)) callback()
    }
}
